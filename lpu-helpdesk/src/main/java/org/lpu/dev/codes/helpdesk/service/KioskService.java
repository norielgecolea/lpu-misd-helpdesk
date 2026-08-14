package org.lpu.dev.codes.helpdesk.service;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.GateAttendanceProperties;
import org.lpu.dev.codes.helpdesk.dto.KioskPersonResponse;
import org.lpu.dev.codes.helpdesk.dto.KioskTicketRequest;
import org.lpu.dev.codes.helpdesk.dto.PendingCsmResponse;
import org.lpu.dev.codes.helpdesk.dto.TicketResponse;
import org.lpu.dev.codes.helpdesk.dto.WalkInTicketRequest;
import org.lpu.dev.codes.helpdesk.model.Employee;
import org.lpu.dev.codes.helpdesk.model.Student;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketCategoryDefinition;
import org.lpu.dev.codes.helpdesk.repository.EmployeeRepository;
import org.lpu.dev.codes.helpdesk.repository.StudentRepository;
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

    public KioskService(
            StudentRepository studentRepository,
            EmployeeRepository employeeRepository,
            QueueService queueService,
            TicketCategoryService ticketCategoryService,
            TicketCsmService ticketCsmService,
            GateAttendanceProperties gateAttendanceProperties
    ) {
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
        this.queueService = queueService;
        this.ticketCategoryService = ticketCategoryService;
        this.ticketCsmService = ticketCsmService;
        this.gateAttendanceProperties = gateAttendanceProperties;
    }

    @Transactional(transactionManager = "gateTransactionManager", readOnly = true)
    public KioskPersonResponse lookup(String rawIdentifier) {
        String identifier = normalizeIdentifier(rawIdentifier);
        return resolvePerson(identifier);
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
    public TicketResponse createTicket(KioskTicketRequest request) {
        String identifier = normalizeIdentifier(request.identifier());
        KioskPersonResponse person = resolvePerson(identifier);

        if (person.email() == null || person.email().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This record has no LPU email on file. Please ask the MISD counter for help."
            );
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

        Ticket ticket = queueService.createWalkInTicket(new WalkInTicketRequest(
                person.name(),
                person.email(),
                category.getCode(),
                subject,
                description,
                person.personType(),
                person.personNo()
        ));
        log.info(
                "Kiosk ticket created ticketNumber={} personType={} personNo={}",
                ticket.getTicketNumber(),
                person.personType(),
                person.personNo()
        );
        return TicketResponse.from(ticket);
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
