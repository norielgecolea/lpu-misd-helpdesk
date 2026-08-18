package org.lpu.dev.codes.helpdesk.controller;

import jakarta.validation.Valid;
import org.lpu.dev.codes.helpdesk.dto.DirectoryProfileResponse;
import org.lpu.dev.codes.helpdesk.dto.EncodeLpuEmailRequest;
import org.lpu.dev.codes.helpdesk.dto.EncodeLpuEmailResponse;
import org.lpu.dev.codes.helpdesk.service.DirectoryEmailService;
import org.lpu.dev.codes.helpdesk.service.DirectoryLookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/directory")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminDirectoryController {

    private final DirectoryLookupService directoryLookupService;
    private final DirectoryEmailService directoryEmailService;

    public AdminDirectoryController(
            DirectoryLookupService directoryLookupService,
            DirectoryEmailService directoryEmailService
    ) {
        this.directoryLookupService = directoryLookupService;
        this.directoryEmailService = directoryEmailService;
    }

    @GetMapping("/profile")
    public ResponseEntity<DirectoryProfileResponse> profile(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) String personNo
    ) {
        return ResponseEntity.ok(directoryLookupService.resolveProfile(email, personType, personNo));
    }

    @PostMapping("/encode-email")
    public ResponseEntity<EncodeLpuEmailResponse> encodeEmail(@Valid @RequestBody EncodeLpuEmailRequest request) {
        return ResponseEntity.ok(directoryEmailService.encode(request));
    }
}
