package org.lpu.dev.codes.helpdesk.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.lpu.dev.codes.helpdesk.dto.AssignTicketRequest;
import org.lpu.dev.codes.helpdesk.dto.TicketResponse;
import org.lpu.dev.codes.helpdesk.dto.UpdateTicketStatusRequest;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.lpu.dev.codes.helpdesk.service.AdminTicketService;
import org.lpu.dev.codes.helpdesk.service.TicketUnreadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tickets")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminTicketController {

    private final AdminTicketService adminTicketService;
    private final UserRepository userRepository;
    private final TicketUnreadService ticketUnreadService;

    public AdminTicketController(
            AdminTicketService adminTicketService,
            UserRepository userRepository,
            TicketUnreadService ticketUnreadService
    ) {
        this.adminTicketService = adminTicketService;
        this.userRepository = userRepository;
        this.ticketUnreadService = ticketUnreadService;
    }

    @GetMapping
    public ResponseEntity<List<TicketResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @RequestParam(required = false) String status
    ) {
        TicketStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            statusFilter = TicketStatus.valueOf(status.trim().toUpperCase());
        }
        List<Ticket> tickets = adminTicketService.listTickets(statusFilter);
        return ResponseEntity.ok(enrich(tickets, actingAdmin));
    }

    @GetMapping("/history")
    public ResponseEntity<List<TicketResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) String personNo
    ) {
        List<Ticket> tickets = adminTicketService.listHistoryForPerson(email, personType, personNo);
        return ResponseEntity.ok(enrich(tickets, actingAdmin));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<TicketResponse> assign(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @PathVariable Long id,
            @RequestBody AssignTicketRequest request
    ) {
        Ticket ticket = adminTicketService.assignTicket(actingAdmin, id, request.adminId());
        return ResponseEntity.ok(enrich(List.of(ticket), actingAdmin).get(0));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketStatusRequest request
    ) {
        Ticket ticket = adminTicketService.updateStatus(actingAdmin, id, request.status());
        return ResponseEntity.ok(enrich(List.of(ticket), actingAdmin).get(0));
    }

    /** MISD staff list for the "assign to" dropdown — available to any admin, not just Super Admin. */
    @GetMapping("/assignees")
    public ResponseEntity<List<AdminSummary>> assignees() {
        List<AdminSummary> admins = userRepository.findByRoleIn(List.of(Role.ADMIN, Role.SUPER_ADMIN)).stream()
                .filter(User::isActive)
                .map(u -> new AdminSummary(u.getId(), u.getName(), u.getEmail(), u.getRole().name()))
                .toList();
        return ResponseEntity.ok(admins);
    }

    public record AdminSummary(Long id, String name, String email, String role) {
    }

    private List<TicketResponse> enrich(List<Ticket> tickets, AuthenticatedUser user) {
        List<Long> adminIds = tickets.stream()
                .map(Ticket::getAssignedAdminId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> adminsById = userRepository.findByIdIn(adminIds);
        List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
        Map<Long, Integer> unread = ticketUnreadService.unreadCounts(user.getId(), ticketIds);
        return tickets.stream()
                .map(ticket -> {
                    User admin = ticket.getAssignedAdminId() != null
                            ? adminsById.get(ticket.getAssignedAdminId())
                            : null;
                    return TicketResponse.from(
                            ticket,
                            admin != null ? admin.getName() : null,
                            unread.getOrDefault(ticket.getId(), 0)
                    );
                })
                .toList();
    }
}
