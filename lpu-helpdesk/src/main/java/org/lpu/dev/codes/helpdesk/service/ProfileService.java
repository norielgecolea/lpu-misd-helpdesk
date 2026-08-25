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
     * Outside-email users must declare student name + ID once. Saves on the user
     * and prefers the official directory name when the student number matches.
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

        String studentName = request.studentName().trim();
        String studentNo = request.studentNo().trim();
        if (studentName.isEmpty() || studentNo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student name and ID number are required");
        }

        DirectoryProfileResponse directory = directoryLookupService.resolveProfile(null, "STUDENT", studentNo);
        String displayName = directory.found() && directory.name() != null && !directory.name().isBlank()
                ? directory.name().trim()
                : studentName;

        user.setDeclaredStudentName(studentName);
        user.setDeclaredStudentNo(studentNo);
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
        return name == null || name.isBlank() || no == null || no.isBlank();
    }
}
