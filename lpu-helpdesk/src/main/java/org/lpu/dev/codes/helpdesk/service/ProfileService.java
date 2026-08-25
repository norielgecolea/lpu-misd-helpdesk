package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.config.AuthProperties;
import org.lpu.dev.codes.helpdesk.dto.DirectoryProfileResponse;
import org.lpu.dev.codes.helpdesk.dto.StudentInfoRequest;
import org.lpu.dev.codes.helpdesk.dto.UserProfileResponse;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final DirectoryLookupService directoryLookupService;
    private final AuthProperties authProperties;

    public ProfileService(
            UserRepository userRepository,
            DirectoryLookupService directoryLookupService,
            AuthProperties authProperties
    ) {
        this.userRepository = userRepository;
        this.directoryLookupService = directoryLookupService;
        this.authProperties = authProperties;
    }

    @Transactional
    public UserProfileResponse getProfile(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Campus emails: keep syncing display name from the gate directory.
        if (authProperties.isAllowedEmail(user.getEmail())) {
            directoryLookupService.findNameByLpuEmail(user.getEmail()).ifPresent(directoryName -> {
                if (!directoryName.equals(user.getName())) {
                    user.setName(directoryName);
                    user.setUpdatedAt(Instant.now());
                    userRepository.save(user);
                }
            });
        }

        return UserProfileResponse.from(user, needsStudentInfo(user));
    }

    /**
     * Outside-email users declare person type, name, ID, and LPU email once.
     * Directory name is used only when ID + LPU email both match a campus record.
     */
    @Transactional
    public UserProfileResponse saveStudentInfo(AuthenticatedUser principal, StudentInfoRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getRole() != Role.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only helpdesk users can save student info");
        }
        if (authProperties.isAllowedEmail(user.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Campus accounts do not need to declare student info"
            );
        }

        String personType = normalizePersonType(request.personType());
        String displayInput = request.studentName().trim();
        String personNo = request.studentNo().trim();
        String lpuEmail = request.lpuEmail().trim().toLowerCase();
        if (displayInput.isEmpty() || personNo.isEmpty() || lpuEmail.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name, ID number, and LPU email are required"
            );
        }
        if (!authProperties.isAllowedEmail(lpuEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "LPU email must be a campus address (" + authProperties.allowedDomainsDisplay() + ")"
            );
        }

        DirectoryProfileResponse directory = directoryLookupService.resolveIfIdAndLpuEmailMatch(
                personType,
                personNo,
                lpuEmail
        );
        String displayName = directory.found() && directory.name() != null && !directory.name().isBlank()
                ? directory.name().trim()
                : displayInput;

        user.setDeclaredPersonType(personType);
        user.setDeclaredStudentName(displayInput);
        user.setDeclaredStudentNo(personNo);
        user.setDeclaredLpuEmail(lpuEmail);
        user.setName(displayName);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        return UserProfileResponse.from(user, needsStudentInfo(user));
    }

    public boolean needsStudentInfo(User user) {
        if (user.getRole() != Role.USER) {
            return false;
        }
        if (authProperties.isAllowedEmail(user.getEmail())) {
            return false;
        }
        String name = user.getDeclaredStudentName();
        String no = user.getDeclaredStudentNo();
        String type = user.getDeclaredPersonType();
        String lpuEmail = user.getDeclaredLpuEmail();
        return name == null || name.isBlank()
                || no == null || no.isBlank()
                || type == null || type.isBlank()
                || lpuEmail == null || lpuEmail.isBlank();
    }

    private static String normalizePersonType(String personType) {
        if (personType == null || personType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Person type is required");
        }
        String normalized = personType.trim().toUpperCase();
        if (!"STUDENT".equals(normalized) && !"EMPLOYEE".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Person type must be STUDENT or EMPLOYEE");
        }
        return normalized;
    }
}
