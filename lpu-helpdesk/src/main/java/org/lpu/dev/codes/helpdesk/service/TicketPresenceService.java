package org.lpu.dev.codes.helpdesk.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.springframework.stereotype.Service;

/** In-memory last-seen times for who is currently viewing a ticket thread. */
@Service
public class TicketPresenceService {

    private static final Duration ONLINE_FOR = Duration.ofSeconds(12);

    private static final class Presence {
        volatile Instant requesterSeen;
        volatile Instant staffSeen;
    }

    private final ConcurrentHashMap<Long, Presence> byTicket = new ConcurrentHashMap<>();

    public void heartbeat(Long ticketId, Role role) {
        if (ticketId == null || role == null) {
            return;
        }
        Presence presence = byTicket.computeIfAbsent(ticketId, id -> new Presence());
        Instant now = Instant.now();
        if (role == Role.USER) {
            presence.requesterSeen = now;
        } else {
            presence.staffSeen = now;
        }
    }

    public boolean requesterOnline(Long ticketId) {
        Presence presence = byTicket.get(ticketId);
        return presence != null && isFresh(presence.requesterSeen);
    }

    public boolean staffOnline(Long ticketId) {
        Presence presence = byTicket.get(ticketId);
        return presence != null && isFresh(presence.staffSeen);
    }

    private static boolean isFresh(Instant seen) {
        return seen != null && seen.isAfter(Instant.now().minus(ONLINE_FOR));
    }
}
