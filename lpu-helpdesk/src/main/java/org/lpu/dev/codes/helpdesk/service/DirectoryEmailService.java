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
 * Encodes an LPU email onto a gate directory record and attaches all of that
 * person's tickets to the matching helpdesk user account.
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
        String email = requireAllowedLpuEmail(request.email());
        PersonRef person = resolvePerson(request);
        ensureEmailNotTakenBySomeoneElse(email, person);

        writeDirectoryEmail(person, email);
        int linked = linkTicketsForPerson(person.type(), person.number(), email);

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
        studentRepository.findByLpuEmail(normalized).ifPresent(student ->
                linkTicketsForPerson("STUDENT", student.getStudentNo(), normalized)
        );
        employeeRepository.findByLpuEmail(normalized).ifPresent(employee ->
                linkTicketsForPerson("EMPLOYEE", employee.getEmployeeNo(), normalized)
        );

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

    private PersonRef resolvePerson(EncodeLpuEmailRequest request) {
        String type = blankToNull(request.personType());
        String number = blankToNull(request.personNo());
        if ((type == null || number == null) && request.ticketId() != null) {
            Ticket ticket = ticketRepository.findById(request.ticketId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
            type = blankToNull(ticket.getRequesterPersonType());
            number = blankToNull(ticket.getRequesterPersonNo());
        }
        if (type == null || number == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide personType and personNo, or a ticket that has a directory identity"
            );
        }
        type = type.toUpperCase();
        if ("STUDENT".equals(type)) {
            Student student = studentRepository.findByRfidOrStudentNo(number)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found"));
            return new PersonRef("STUDENT", student.getId(), student.getStudentNo());
        }
        if ("EMPLOYEE".equals(type)) {
            Employee employee = employeeRepository.findByRfidOrEmployeeNo(number)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee record not found"));
            return new PersonRef("EMPLOYEE", employee.getId(), employee.getEmployeeNo());
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personType must be STUDENT or EMPLOYEE");
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

    private record PersonRef(String type, Long id, String number) {
    }
}
