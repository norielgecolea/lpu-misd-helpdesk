package org.lpu.dev.codes.helpdesk.controller;

import org.lpu.dev.codes.helpdesk.dto.MonitorSnapshotResponse;
import org.lpu.dev.codes.helpdesk.service.MonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
@PreAuthorize("hasAnyRole('MONITORING', 'ADMIN', 'SUPER_ADMIN')")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/snapshot")
    public ResponseEntity<MonitorSnapshotResponse> snapshot(
            @RequestParam(defaultValue = "20") int recentLimit
    ) {
        return ResponseEntity.ok(monitorService.getSnapshot(recentLimit));
    }
}
