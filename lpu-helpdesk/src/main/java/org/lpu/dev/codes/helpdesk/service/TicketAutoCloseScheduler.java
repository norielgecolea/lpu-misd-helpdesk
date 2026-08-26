package org.lpu.dev.codes.helpdesk.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TicketAutoCloseScheduler {

    private static final Logger log = LogManager.getLogger(TicketAutoCloseScheduler.class);

    private final AdminTicketService adminTicketService;

    public TicketAutoCloseScheduler(AdminTicketService adminTicketService) {
        this.adminTicketService = adminTicketService;
    }

    /** Every 15 minutes: close tickets that have been Resolved for 2 days. */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void closeExpiredResolved() {
        try {
            int closed = adminTicketService.autoCloseExpiredResolved();
            if (closed > 0) {
                log.info("Auto-closed {} ticket(s) resolved for 2+ days", closed);
            }
        } catch (Exception ex) {
            log.error("Failed to auto-close resolved tickets", ex);
        }
    }
}
