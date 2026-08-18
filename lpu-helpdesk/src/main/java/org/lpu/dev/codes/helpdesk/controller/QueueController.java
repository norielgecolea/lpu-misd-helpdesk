package org.lpu.dev.codes.helpdesk.controller;

import jakarta.validation.Valid;
import org.lpu.dev.codes.helpdesk.dto.QueueSnapshotResponse;
import org.lpu.dev.codes.helpdesk.dto.QueueTransferResponse;
import org.lpu.dev.codes.helpdesk.dto.TicketResponse;
import org.lpu.dev.codes.helpdesk.dto.TransferQueueTicketRequest;
import org.lpu.dev.codes.helpdesk.dto.WalkInTicketRequest;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.security.AuthenticatedUser;
import org.lpu.dev.codes.helpdesk.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/queue")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping
    public ResponseEntity<QueueSnapshotResponse> snapshot() {
        return ResponseEntity.ok(queueService.getSnapshot());
    }

    @PostMapping("/walk-in")
    public ResponseEntity<TicketResponse> walkIn(@Valid @RequestBody WalkInTicketRequest request) {
        Ticket ticket = queueService.createWalkInTicket(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketResponse.from(ticket));
    }

    @PostMapping("/call-next")
    public ResponseEntity<TicketResponse> callNext(@AuthenticationPrincipal AuthenticatedUser actingAdmin) {
        Ticket ticket = queueService.callNext(actingAdmin);
        return ResponseEntity.ok(TicketResponse.from(ticket, actingAdmin.getName()));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<TicketResponse> claim(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @PathVariable Long id
    ) {
        Ticket ticket = queueService.claim(actingAdmin, id);
        return ResponseEntity.ok(TicketResponse.from(ticket, actingAdmin.getName()));
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<QueueTransferResponse> transfer(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @PathVariable Long id,
            @Valid @RequestBody TransferQueueTicketRequest request
    ) {
        QueueTransferResponse transfer = queueService.requestTransfer(actingAdmin, id, request.adminId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(transfer);
    }

    @PostMapping("/transfers/{transferId}/approve")
    public ResponseEntity<TicketResponse> approveTransfer(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @PathVariable Long transferId
    ) {
        Ticket ticket = queueService.approveTransfer(actingAdmin, transferId);
        return ResponseEntity.ok(TicketResponse.from(ticket, actingAdmin.getName()));
    }

    @PostMapping("/transfers/{transferId}/reject")
    public ResponseEntity<QueueTransferResponse> rejectTransfer(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @PathVariable Long transferId
    ) {
        return ResponseEntity.ok(queueService.rejectTransfer(actingAdmin, transferId));
    }

    @PostMapping("/transfers/{transferId}/cancel")
    public ResponseEntity<QueueTransferResponse> cancelTransfer(
            @AuthenticationPrincipal AuthenticatedUser actingAdmin,
            @PathVariable Long transferId
    ) {
        return ResponseEntity.ok(queueService.cancelTransferRequest(actingAdmin, transferId));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<TicketResponse> complete(@PathVariable Long id) {
        Ticket ticket = queueService.completeServing(id);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    @PostMapping("/{id}/requeue")
    public ResponseEntity<TicketResponse> requeue(@PathVariable Long id) {
        Ticket ticket = queueService.requeue(id);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    @PostMapping("/{id}/hold")
    public ResponseEntity<TicketResponse> hold(@PathVariable Long id) {
        Ticket ticket = queueService.holdServing(id);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }
}
