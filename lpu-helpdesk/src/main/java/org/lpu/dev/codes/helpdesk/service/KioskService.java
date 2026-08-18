package org.lpu.dev.codes.helpdesk.service;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.GateAttendanceProperties;
import org.lpu.dev.codes.helpdesk.dto.KioskPersonResponse;
import org.lpu.dev.codes.helpdesk.dto.KioskTicketCreateResponse;
import org.lpu.dev.codes.helpdesk.dto.KioskTicketRequest;
import org.lpu.dev.codes.helpdesk.dto.PendingCsmResponse;
import org.lpu.dev.codes.helpdesk.dto.TicketResponse;
import org.lpu.dev.codes.helpdesk.dto.WalkInTicketRequest;
import org.lpu.dev.codes.helpdesk.model.Employee;
import org.lpu.dev.codes.helpdesk.model.PendingRequesterEmail;
import org.lpu.dev.codes.helpdesk.model.Student;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketCategoryDefinition;
import org.lpu.dev.codes.helpdesk.repository.EmployeeRepository;
import org.lpu.dev.codes.helpdesk.repository.StudentRepository;
import org.lpu.dev.codes.helpdesk.repository.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KioskService {

    private static final Logger log = LogManager.getLogger(KioskService.class);

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final QueueService queueService;
    private final TicketCategoryService ticketCategoryService;
    private final TicketCsmService ticketCsmService;
    private final GateAttendanceProperties gateAttendanceProperties;
    private final DirectoryEmailService directoryEmailService;
    private final TicketRepository ticketRepository;

    public KioskService(
            StudentRepository studentRepository,
            EmployeeRepository employeeRepository,
            QueueService queueService,
            TicketCategoryService ticketCategoryService,
            TicketCsmService ticketCsmService,
            GateAttendanceProperties gateAttendanceProperties,
            DirectoryEmailService directoryEmailService,
            TicketRepository ticketRepository
    ) {
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
        this.queueService = queueService;
        this.ticketCategoryService = ticketCategoryService;
        this.ticketCsmService = ticketCsmService;
        this.gateAttendanceProperties = gateAttendanceProperties;
        this.directoryEmailService = directoryEmailService;
        this.ticketRepository = ticketRepository;
    }

    public KioskPersonResponse lookup(String rawIdentifier) {
        String identifier = normalizeIdentifier(rawIdentifier);
        KioskPersonResponse person = resolvePerson(identifier);
        if (person.email() != null && !person.email().isBlank()) {
            directoryEmailService.linkTicketsForPerson(person.personType(), person.personNo(), person.email());
        }
        return person;
    }

    @Transactional(readOnly = true)
    public Optional<PendingCsmResponse> pendingCsm(String rawIdentifier) {
        String identifier = normalizeIdentifier(rawIdentifier);
        KioskPersonResponse person = resolvePerson(identifier);
        return ticketCsmService.pendingForPerson(person.email(), person.personType(), person.personNo());
    }

    @Transactional
    public PendingCsmResponse submitCsm(String rawIdentifier, Long ticketId, String rating, String comment) {
        String identifier = normalizeIdentifier(rawIdentifier);
        KioskPersonResponse person = resolvePerson(identifier);
        return ticketCsmService.submitForPerson(
                ticketId,
                person.email(),
                person.personType(),
                person.personNo(),
                rating,
                comment
        );
    }

    @Transactional
    public KioskTicketCreateResponse createTicket(KioskTicketRequest request) {
        String identifier = normalizeIdentifier(request.identifier());
        KioskPersonResponse person = resolvePerson(identifier);

        if (person.email() != null && !person.email().isBlank()) {
            directoryEmailService.linkTicketsForPerson(person.personType(), person.personNo(), person.email());
        }

        ticketCsmService.requireNoPendingForPerson(person.email(), person.personType(), person.personNo());

        TicketCategoryDefinition category = ticketCategoryService.requireActiveForKiosk(request.category());
        String subject;
        String description;

        if (category.isRequiresDetail()) {
            String concern = request.concern() != null ? request.concern().trim() : "";
            if (concern.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please describe your concern");
            }
            subject = concern.length() > 200 ? concern.substring(0, 200) : concern;
            description = concern;
        } else {
            subject = category.getLabel();
            description = "Onsite RFID kiosk request: " + category.getLabel();
        }

        String requesterEmail = person.email() != null && !person.email().isBlank()
                ? person.email()
                : PendingRequesterEmail.forPerson(person.personType(), person.personNo());

        Ticket ticket = queueService.createWalkInTicket(new WalkInTicketRequest(
                person.name(),
                requesterEmail,
                category.getCode(),
                subject,
                description,
                person.personType(),
                person.personNo()
        ));

        Ticket emailLinkTicket = null;
        boolean missingEmail = person.email() == null || person.email().isBlank();
        if (missingEmail
                && !PendingRequesterEmail.LINK_LPU_EMAIL_CATEGORY.equals(category.getCode())
                && !ticketRepository.existsOpenEmailLinkForPerson(person.personType(), person.personNo())) {
            emailLinkTicket = queueService.createWalkInTicket(new WalkInTicketRequest(
                    person.name(),
                    requesterEmail,
                    PendingRequesterEmail.LINK_LPU_EMAIL_CATEGORY,
                    "Link LPU email to local record",
                    "No LPU email is encoded on this "
                            + person.personType().toLowerCase()
                            + " record ("
                            + person.personNo()
                            + "). Encode the official LPU email so existing and future tickets can be linked to their account.",
                    person.personType(),
                    person.personNo()
            ));
        }

        log.info(
                "Kiosk ticket created ticketNumber={} personType={} personNo={} emailLinkTicket={}",
                ticket.getTicketNumber(),
                person.personType(),
                person.personNo(),
                emailLinkTicket != null ? emailLinkTicket.getTicketNumber() : "none"
        );
        return new KioskTicketCreateResponse(
                TicketResponse.from(ticket),
                emailLinkTicket != null ? TicketResponse.from(emailLinkTicket) : null
        );
    }

    private KioskPersonResponse resolvePerson(String identifier) {
        return studentRepository.findByRfidOrStudentNo(identifier)
                .<KioskPersonResponse>map(this::fromStudent)
                .or(() -> employeeRepository.findByRfidOrEmployeeNo(identifier).map(this::fromEmployee))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No student or employee found for that RFID / ID number"
                ));
    }

    private KioskPersonResponse fromStudent(Student student) {
        return new KioskPersonResponse(
                "STUDENT",
                student.getId(),
                student.getName(),
                student.getStudentNo(),
                blankToNull(student.getLpuEmail()),
                gateAttendanceProperties.resolvePhotoUrl(student.getPhoto()),
                student.getDepartment(),
                student.getCourse(),
                student.getSchool(),
                null,
                student.getRfid()
        );
    }

    private KioskPersonResponse fromEmployee(Employee employee) {
        return new KioskPersonResponse(
                "EMPLOYEE",
                employee.getId(),
                employee.getName(),
                employee.getEmployeeNo(),
                blankToNull(employee.getLpuEmail()),
                gateAttendanceProperties.resolvePhotoUrl(employee.getPhoto()),
                employee.getDepartment(),
                null,
                null,
                employee.getPosition(),
                employee.getRfid()
        );
    }

    private static String normalizeIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RFID or ID number is required");
        }
        return raw.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }
}
