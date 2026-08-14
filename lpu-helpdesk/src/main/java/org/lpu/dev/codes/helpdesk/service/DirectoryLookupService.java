package org.lpu.dev.codes.helpdesk.service;

import java.util.Optional;
import org.lpu.dev.codes.helpdesk.dto.DirectoryProfileResponse;
import org.lpu.dev.codes.helpdesk.model.Employee;
import org.lpu.dev.codes.helpdesk.model.Student;
import org.lpu.dev.codes.helpdesk.repository.EmployeeRepository;
import org.lpu.dev.codes.helpdesk.repository.StudentRepository;
import org.springframework.stereotype.Service;

/** Resolves display names and full profiles from the gate attendance directory. */
@Service
public class DirectoryLookupService {

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    public DirectoryLookupService(StudentRepository studentRepository, EmployeeRepository employeeRepository) {
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
    }

    public Optional<String> findNameByLpuEmail(String email) {
        DirectoryProfileResponse profile = resolveProfile(email, null, null);
        if (!profile.found() || profile.name() == null || profile.name().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(profile.name().trim());
    }

    /**
     * Resolve a student/employee profile for ticket summaries.
     * Preference: personType + personNo → personNo alone → email.
     */
    public DirectoryProfileResponse resolveProfile(String email, String personType, String personNo) {
        String type = normalizeType(personType);
        String number = blankToNull(personNo);
        String normalizedEmail = normalizeEmail(email);

        if (number != null) {
            if ("STUDENT".equals(type)) {
                Optional<DirectoryProfileResponse> student = studentRepository.findByRfidOrStudentNo(number)
                        .map(this::fromStudent);
                if (student.isPresent()) {
                    return student.get();
                }
            } else if ("EMPLOYEE".equals(type)) {
                Optional<DirectoryProfileResponse> employee = employeeRepository.findByRfidOrEmployeeNo(number)
                        .map(this::fromEmployee);
                if (employee.isPresent()) {
                    return employee.get();
                }
            } else {
                Optional<DirectoryProfileResponse> byNumber = studentRepository.findByRfidOrStudentNo(number)
                        .map(this::fromStudent)
                        .or(() -> employeeRepository.findByRfidOrEmployeeNo(number).map(this::fromEmployee));
                if (byNumber.isPresent()) {
                    return byNumber.get();
                }
            }
        }

        if (normalizedEmail != null) {
            Optional<DirectoryProfileResponse> byEmail = studentRepository.findByLpuEmail(normalizedEmail)
                    .map(this::fromStudent)
                    .or(() -> employeeRepository.findByLpuEmail(normalizedEmail).map(this::fromEmployee));
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }

        return DirectoryProfileResponse.notFound();
    }

    private DirectoryProfileResponse fromStudent(Student student) {
        return new DirectoryProfileResponse(
                true,
                "STUDENT",
                blankToNull(student.getName()),
                blankToNull(student.getLpuEmail()),
                blankToNull(student.getStudentNo()),
                blankToNull(student.getDepartment()),
                blankToNull(student.getCourse()),
                null
        );
    }

    private DirectoryProfileResponse fromEmployee(Employee employee) {
        return new DirectoryProfileResponse(
                true,
                "EMPLOYEE",
                blankToNull(employee.getName()),
                blankToNull(employee.getLpuEmail()),
                blankToNull(employee.getEmployeeNo()),
                blankToNull(employee.getDepartment()),
                null,
                blankToNull(employee.getPosition())
        );
    }

    private static String normalizeType(String personType) {
        if (personType == null || personType.isBlank()) {
            return null;
        }
        return personType.trim().toUpperCase();
    }

    private static String normalizeEmail(String email) {
        String value = blankToNull(email);
        return value == null ? null : value.toLowerCase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
