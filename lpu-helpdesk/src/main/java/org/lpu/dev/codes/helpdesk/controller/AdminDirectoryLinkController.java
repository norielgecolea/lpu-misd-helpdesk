package org.lpu.dev.codes.helpdesk.controller;

import jakarta.validation.Valid;
import org.lpu.dev.codes.helpdesk.dto.EncodeLpuEmailResponse;
import org.lpu.dev.codes.helpdesk.dto.LinkTicketPersonRequest;
import org.lpu.dev.codes.helpdesk.service.DirectoryEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Extra directory admin endpoints kept separate from {@link AdminDirectoryController}
 * (that file may be owned by root in local checkouts).
 */
@RestController
@RequestMapping("/api/admin/directory")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminDirectoryLinkController {

    private final DirectoryEmailService directoryEmailService;

    public AdminDirectoryLinkController(DirectoryEmailService directoryEmailService) {
        this.directoryEmailService = directoryEmailService;
    }

    @PostMapping("/link-ticket-person")
    public ResponseEntity<EncodeLpuEmailResponse> linkTicketPerson(
            @Valid @RequestBody LinkTicketPersonRequest request
    ) {
        return ResponseEntity.ok(directoryEmailService.linkTicketPerson(request));
    }
}
