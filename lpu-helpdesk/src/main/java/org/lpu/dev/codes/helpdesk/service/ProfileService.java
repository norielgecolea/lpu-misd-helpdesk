package org.lpu.dev.codes.helpdesk.service;

import java.time.Instant;
import org.lpu.dev.codes.helpdesk.dto.UserProfileResponse;
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

    public ProfileService(UserRepository userRepository, DirectoryLookupService directoryLookupService) {
        this.userRepository = userRepository;
        this.directoryLookupService = directoryLookupService;
    }

    @Transactional
    public UserProfileResponse getProfile(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        directoryLookupService.findNameByLpuEmail(user.getEmail()).ifPresent(directoryName -> {
            if (!directoryName.equals(user.getName())) {
                user.setName(directoryName);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
            }
        });

        return UserProfileResponse.from(user);
    }
}
