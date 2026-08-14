package org.lpu.dev.codes.helpdesk.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.lpu.dev.codes.helpdesk.dto.AdminCategoryResponse;
import org.lpu.dev.codes.helpdesk.dto.CreateCategoryRequest;
import org.lpu.dev.codes.helpdesk.dto.UpdateCategoryRequest;
import org.lpu.dev.codes.helpdesk.service.TicketCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminCategoryController {

    private final TicketCategoryService ticketCategoryService;

    public AdminCategoryController(TicketCategoryService ticketCategoryService) {
        this.ticketCategoryService = ticketCategoryService;
    }

    @GetMapping
    public ResponseEntity<List<AdminCategoryResponse>> list() {
        return ResponseEntity.ok(ticketCategoryService.listAll());
    }

    @PostMapping
    public ResponseEntity<AdminCategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketCategoryService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminCategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ResponseEntity.ok(ticketCategoryService.update(id, request));
    }
}
