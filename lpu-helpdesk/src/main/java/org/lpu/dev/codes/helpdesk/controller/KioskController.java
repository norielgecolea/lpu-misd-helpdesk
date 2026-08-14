package org.lpu.dev.codes.helpdesk.controller;

import jakarta.validation.Valid;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.lpu.dev.codes.helpdesk.dto.KioskLookupRequest;
import org.lpu.dev.codes.helpdesk.dto.KioskPersonResponse;
import org.lpu.dev.codes.helpdesk.dto.KioskSubmitCsmRequest;
import org.lpu.dev.codes.helpdesk.dto.KioskTicketRequest;
import org.lpu.dev.codes.helpdesk.dto.PendingCsmResponse;
import org.lpu.dev.codes.helpdesk.dto.ServerTimeResponse;
import org.lpu.dev.codes.helpdesk.dto.TicketCategoryOption;
import org.lpu.dev.codes.helpdesk.dto.TicketResponse;
import org.lpu.dev.codes.helpdesk.service.KioskService;
import org.lpu.dev.codes.helpdesk.service.TicketCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public RFID kiosk endpoints — no login required. */
@RestController
@RequestMapping("/api/kiosk")
public class KioskController {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Manila");

    private final KioskService kioskService;
    private final TicketCategoryService ticketCategoryService;

    public KioskController(KioskService kioskService, TicketCategoryService ticketCategoryService) {
        this.kioskService = kioskService;
        this.ticketCategoryService = ticketCategoryService;
    }

    @GetMapping("/time")
    public ResponseEntity<ServerTimeResponse> serverTime() {
        ZonedDateTime now = ZonedDateTime.now(DISPLAY_ZONE);
        return ResponseEntity.ok(new ServerTimeResponse(
                now.toInstant().toEpochMilli(),
                now.toOffsetDateTime().toString(),
                DISPLAY_ZONE.getId()
        ));
    }

    @PostMapping("/lookup")
    public ResponseEntity<KioskPersonResponse> lookup(@Valid @RequestBody KioskLookupRequest request) {
        return ResponseEntity.ok(kioskService.lookup(request.identifier()));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<TicketCategoryOption>> categories() {
        return ResponseEntity.ok(ticketCategoryService.listForKiosk());
    }

    @PostMapping("/pending-csm")
    public ResponseEntity<PendingCsmResponse> pendingCsm(@Valid @RequestBody KioskLookupRequest request) {
        return kioskService.pendingCsm(request.identifier())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/csm")
    public ResponseEntity<PendingCsmResponse> submitCsm(@Valid @RequestBody KioskSubmitCsmRequest request) {
        return ResponseEntity.ok(kioskService.submitCsm(
                request.identifier(),
                request.ticketId(),
                request.rating(),
                request.comment()
        ));
    }

    @PostMapping("/tickets")
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody KioskTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kioskService.createTicket(request));
    }
}
