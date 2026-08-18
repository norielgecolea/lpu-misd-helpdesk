package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.AuthProperties;
import org.lpu.dev.codes.helpdesk.dto.EncodeLpuEmailRequest;
import org.lpu.dev.codes.helpdesk.dto.EncodeLpuEmailResponse;
import org.lpu.dev.codes.helpdesk.model.Employee;
import org.lpu.dev.codes.helpdesk.model.PendingRequesterEmail;
import org.lpu.dev.codes.helpdesk.model.Student;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.EmployeeRepository;
import org.lpu.dev.codes.helpdesk.repository.StudentRepository;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Encodes an LPU email onto a gate directory record and attaches tickets in
 * both directions: kiosk tickets that already have a person identity, and
 * online tickets that already have the email but no student/employee number.
 */
@Service
public class DirectoryEmailService {

    private static final Logger log = LogManager.getLogger(DirectoryEmailService.class);

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuthProperties authProperties;

    public DirectoryEmailService(
            StudentRepository studentRepository,
            EmployeeRepository employeeRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            AuthProperties authProperties
    ) {
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.authProperties = authProperties;
    }

    @Transactional
    public EncodeLpuEmailResponse encode(EncodeLpuEmailRequest request) {
        String email = resolveEmail(request);
        PersonRef person = resolvePerson(request);
        ensureEmailNotTakenBySomeoneElse(email, person);
        ensurePersonEmailCompatible(person, email);

        writeDirectoryEmail(person, email);
        int linked = stampTicket(request.ticketId(), person, email);
        linked += linkTicketsForPerson(person.type(), person.number(), email);
        linked += attachPersonToTicketsForEmail(email, person);

        log.info(
                "Encoded LPU email on {} {} ticketsLinked={}",
                person.type(),
                person.number(),
                linked
        );
        return new EncodeLpuEmailResponse(email, person.type(), person.number(), linked);
    }

    /** Attach tickets for this person once their directory email is known. */
    @Transactional
    public int linkTicketsForPerson(String personType, String personNo, String email) {
        if (personType == null || personType.isBlank() || personNo == null || personNo.isBlank()) {
            return 0;
        }
        if (email == null || email.isBlank() || PendingRequesterEmail.isPending(email)) {
            return 0;
        }
        String normalized = email.trim().toLowerCase();
        Long userId = userRepository.findUserByEmail(normalized).map(User::getId).orElse(null);
        Instant now = Instant.now();
        int updated = 0;
        List<Ticket> tickets = ticketRepository.findByPerson(personType, personNo);
        for (Ticket ticket : tickets) {
            boolean changed = false;
            if (!normalized.equalsIgnoreCase(ticket.getRequesterEmail())) {
                ticket.setRequesterEmail(normalized);
                changed = true;
            }
            if (userId != null && !userId.equals(ticket.getRequesterUserId())) {
                ticket.setRequesterUserId(userId);
                changed = true;
            }
            if (changed) {
                ticket.setUpdatedAt(now);
                ticketRepository.save(ticket);
                updated++;
            }
        }
        return updated;
    }

    /** After login: attach any tickets for the directory person who owns this email. */
    @Transactional
    public void linkTicketsForLpuEmail(String email, Long userId) {
        if (email == null || email.isBlank() || PendingRequesterEmail.isPending(email) || userId == null) {
            return;
        }
        String normalized = email.trim().toLowerCase();
        studentRepository.findByLpuEmail(normalized).ifPresent(student -> {
            PersonRef person = fromStudent(student);
            linkTicketsForPerson(person.type(), person.number(), normalized);
            attachPersonToTicketsForEmail(normalized, person);
        });
        employeeRepository.findByLpuEmail(normalized).ifPresent(employee -> {
            PersonRef person = fromEmployee(employee);
            linkTicketsForPerson(person.type(), person.number(), normalized);
            attachPersonToTicketsForEmail(normalized, person);
        });

        Instant now = Instant.now();
        for (Ticket ticket : ticketRepository.findHistoryForPerson(normalized, null, null)) {
            if (userId.equals(ticket.getRequesterUserId())) {
                continue;
            }
            ticket.setRequesterUserId(userId);
            ticket.setUpdatedAt(now);
            ticketRepository.save(ticket);
        }
    }

    /**
     * Stamp student/employee identity onto tickets that already have this LPU email
     * (online tickets filed before the address was encoded on the directory record).
     */
    private int attachPersonToTicketsForEmail(String email, PersonRef person) {
        if (person == null || email == null || email.isBlank() || PendingRequesterEmail.isPending(email)) {
            return 0;
        }
        int updated = 0;
        for (Ticket ticket : ticketRepository.findHistoryForPerson(email.trim().toLowerCase(), null, null)) {
            if (applyPerson(ticket, person, email)) {
                updated++;
            }
        }
        return updated;
    }

    /** Persist directory identity onto a specific ticket (the one the admin just linked). */
    private int stampTicket(Long ticketId, PersonRef person, String email) {
        if (ticketId == null) {
            return 0;
        }
        return ticketRepository.findById(ticketId)
                .map(ticket -> applyPerson(ticket, person, email) ? 1 : 0)
                .orElse(0);
    }

    /**
     * If an online ticket's LPU email is already on a directory record but the
     * ticket was never stamped with student/employee number, attach it now.
     */
    @Transactional
    public int healMissingPersonIdentity(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return 0;
        }
        List<String> emails = tickets.stream()
                .filter(this::missingPersonIdentity)
                .map(Ticket::getRequesterEmail)
                .filter(email -> email != null && !PendingRequesterEmail.isPending(email))
                .map(email -> email.trim().toLowerCase())
                .distinct()
                .toList();
        if (emails.isEmpty()) {
            return 0;
        }

        java.util.Map<String, PersonRef> byEmail = new java.util.HashMap<>();
        for (Student student : studentRepository.findByLpuEmails(emails)) {
            if (student.getLpuEmail() != null) {
                byEmail.put(student.getLpuEmail().trim().toLowerCase(), fromStudent(student));
            }
        }
        for (Employee employee : employeeRepository.findByLpuEmails(emails)) {
            if (employee.getLpuEmail() != null) {
                byEmail.putIfAbsent(employee.getLpuEmail().trim().toLowerCase(), fromEmployee(employee));
            }
        }
        if (byEmail.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (Ticket ticket : tickets) {
            if (!missingPersonIdentity(ticket) || PendingRequesterEmail.isPending(ticket.getRequesterEmail())) {
                continue;
            }
            PersonRef person = byEmail.get(ticket.getRequesterEmail().trim().toLowerCase());
            if (person == null) {
                continue;
            }
            if (applyPerson(ticket, person, ticket.getRequesterEmail())) {
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Healed person identity on {} ticket(s) whose LPU email is already in the directory", updated);
        }
        return updated;
    }

    private boolean missingPersonIdentity(Ticket ticket) {
        return ticket.getRequesterPersonType() == null || ticket.getRequesterPersonType().isBlank()
                || ticket.getRequesterPersonNo() == null || ticket.getRequesterPersonNo().isBlank();
    }

    private boolean applyPerson(Ticket ticket, PersonRef person, String email) {
        boolean changed = false;
        if (!person.type().equalsIgnoreCase(nullToEmpty(ticket.getRequesterPersonType()))
                || !person.number().equalsIgnoreCase(nullToEmpty(ticket.getRequesterPersonNo()))) {
            ticket.setRequesterPersonType(person.type());
            ticket.setRequesterPersonNo(person.number());
            changed = true;
        }
        String normalized = email == null ? null : email.trim().toLowerCase();
        if (normalized != null && !normalized.isBlank() && !PendingRequesterEmail.isPending(normalized)
                && !normalized.equalsIgnoreCase(ticket.getRequesterEmail())) {
            ticket.setRequesterEmail(normalized);
            changed = true;
        }
        if (person.name() != null && !person.name().isBlank()
                && !person.name().equals(ticket.getRequesterName())) {
            ticket.setRequesterName(person.name());
            changed = true;
        }
        if (changed) {
            ticket.setUpdatedAt(Instant.now());
            ticketRepository.save(ticket);
        }
        return changed;
    }

    private String resolveEmail(EncodeLpuEmailRequest request) {
        String email = blankToNull(request.email());
        if (email == null && request.ticketId() != null) {
            Ticket ticket = ticketRepository.findById(request.ticketId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
            email = blankToNull(ticket.getRequesterEmail());
        }
        return requireAllowedLpuEmail(email);
    }

    private PersonRef resolvePerson(EncodeLpuEmailRequest request) {
        String type = blankToNull(request.personType());
        String number = blankToNull(request.personNo());
        if ((type == null || number == null) && request.ticketId() != null) {
            Ticket ticket = ticketRepository.findById(request.ticketId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
            if (type == null) {
                type = blankToNull(ticket.getRequesterPersonType());
            }
            if (number == null) {
                number = blankToNull(ticket.getRequesterPersonNo());
            }
        }
        if (number == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide a student or employee number"
            );
        }
        if (type == null) {
            return resolvePersonByNumber(number);
        }
        type = type.toUpperCase();
        if ("STUDENT".equals(type)) {
            Student student = studentRepository.findByRfidOrStudentNo(number)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found"));
            return fromStudent(student);
        }
        if ("EMPLOYEE".equals(type)) {
            Employee employee = employeeRepository.findByRfidOrEmployeeNo(number)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee record not found"));
            return fromEmployee(employee);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personType must be STUDENT or EMPLOYEE");
    }

    private PersonRef resolvePersonByNumber(String number) {
        var student = studentRepository.findByRfidOrStudentNo(number);
        var employee = employeeRepository.findByRfidOrEmployeeNo(number);
        if (student.isPresent() && employee.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "That number matches both a student and an employee. Choose Student or Employee."
            );
        }
        if (student.isPresent()) {
            return fromStudent(student.get());
        }
        if (employee.isPresent()) {
            return fromEmployee(employee.get());
        }
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No student or employee record matches that number"
        );
    }

    private static PersonRef fromStudent(Student student) {
        return new PersonRef(
                "STUDENT",
                student.getId(),
                student.getStudentNo(),
                student.getName(),
                student.getLpuEmail()
        );
    }

    private static PersonRef fromEmployee(Employee employee) {
        return new PersonRef(
                "EMPLOYEE",
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                employee.getLpuEmail()
        );
    }

    private void ensurePersonEmailCompatible(PersonRef person, String email) {
        String existing = blankToNull(person.existingEmail());
        if (existing != null && !existing.equalsIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This " + person.type().toLowerCase()
                            + " already has a different LPU email on file (" + existing + ")"
            );
        }
    }

    private void ensureEmailNotTakenBySomeoneElse(String email, PersonRef person) {
        studentRepository.findByLpuEmail(email).ifPresent(student -> {
            if (!"STUDENT".equals(person.type()) || !person.number().equalsIgnoreCase(student.getStudentNo())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "That LPU email is already encoded on another student record"
                );
            }
        });
        employeeRepository.findByLpuEmail(email).ifPresent(employee -> {
            if (!"EMPLOYEE".equals(person.type()) || !person.number().equalsIgnoreCase(employee.getEmployeeNo())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "That LPU email is already encoded on another employee record"
                );
            }
        });
    }

    private void writeDirectoryEmail(PersonRef person, String email) {
        try {
            int updated;
            if ("STUDENT".equals(person.type())) {
                updated = studentRepository.updateLpuEmail(person.id(), email);
            } else {
                updated = employeeRepository.updateLpuEmail(person.id(), email);
            }
            if (updated < 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Directory record was not updated");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to encode LPU email on {} {}", person.type(), person.number(), ex);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to write LPU email to the directory record. Check gate database permissions."
            );
        }
    }

    private String requireAllowedLpuEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase();
        String suffix = "@" + authProperties.getAllowedEmailDomain().toLowerCase();
        if (email.isBlank() || !email.endsWith(suffix)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use an @" + authProperties.getAllowedEmailDomain() + " address"
            );
        }
        return email;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record PersonRef(String type, Long id, String number, String name, String existingEmail) {
    }
}
