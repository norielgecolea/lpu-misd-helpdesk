package org.lpu.dev.codes.helpdesk.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.lpu.dev.codes.helpdesk.dto.DirectoryIdentity;
import org.lpu.dev.codes.helpdesk.dto.DirectoryProfileResponse;
import org.lpu.dev.codes.helpdesk.repository.EmployeeRepository;
import org.lpu.dev.codes.helpdesk.repository.StudentRepository;
import org.springframework.stereotype.Service;

/** Resolves display names and full profiles from the gate attendance directory. */
@Service
public class DirectoryLookupService {

    private static final long CACHE_TTL_MS = 120_000;

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final ConcurrentHashMap<String, CacheEntry> profileCache = new ConcurrentHashMap<>();

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
     * Looks up identity columns only (never the ID photo blob).
     */
    public DirectoryProfileResponse resolveProfile(String email, String personType, String personNo) {
        String type = normalizeType(personType);
        String number = blankToNull(personNo);
        String normalizedEmail = normalizeEmail(email);
        String cacheKey = (type == null ? "" : type) + "|"
                + (number == null ? "" : number) + "|"
                + (normalizedEmail == null ? "" : normalizedEmail);
        long now = System.currentTimeMillis();
        CacheEntry cached = profileCache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.profile();
        }

        DirectoryProfileResponse profile = lookup(type, number, normalizedEmail);
        profileCache.put(cacheKey, new CacheEntry(profile, now + CACHE_TTL_MS));
        return profile;
    }

    private DirectoryProfileResponse lookup(String type, String number, String normalizedEmail) {
        if (number != null) {
            if ("STUDENT".equals(type)) {
                Optional<DirectoryProfileResponse> student = studentRepository.findIdentityByRfidOrStudentNo(number)
                        .map(DirectoryIdentity::toProfile);
                if (student.isPresent()) {
                    return student.get();
                }
            } else if ("EMPLOYEE".equals(type)) {
                Optional<DirectoryProfileResponse> employee = employeeRepository.findIdentityByRfidOrEmployeeNo(number)
                        .map(DirectoryIdentity::toProfile);
                if (employee.isPresent()) {
                    return employee.get();
                }
            } else {
                Optional<DirectoryProfileResponse> byNumber = studentRepository.findIdentityByRfidOrStudentNo(number)
                        .map(DirectoryIdentity::toProfile)
                        .or(() -> employeeRepository.findIdentityByRfidOrEmployeeNo(number)
                                .map(DirectoryIdentity::toProfile));
                if (byNumber.isPresent()) {
                    return byNumber.get();
                }
            }
        }

        if (normalizedEmail != null) {
            Optional<DirectoryProfileResponse> byEmail = studentRepository.findIdentityByLpuEmail(normalizedEmail)
                    .map(DirectoryIdentity::toProfile)
                    .or(() -> employeeRepository.findIdentityByLpuEmail(normalizedEmail)
                            .map(DirectoryIdentity::toProfile));
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }

        return DirectoryProfileResponse.notFound();
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

    private record CacheEntry(DirectoryProfileResponse profile, long expiresAtMillis) {}
}
