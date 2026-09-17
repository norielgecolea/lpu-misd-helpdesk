package org.lpu.dev.codes.helpdesk.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.lpu.dev.codes.helpdesk.dto.TicketListQuery;
import org.lpu.dev.codes.helpdesk.model.PendingRequesterEmail;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketChannel;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class TicketRepository {

    private final SessionFactory sessionFactory;

    public TicketRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public List<Ticket> findByRequesterUserIdOrderByCreatedAtDesc(Long requesterUserId) {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.requesterUserId = :userId ORDER BY t.createdAt DESC",
                        Ticket.class
                )
                .setParameter("userId", requesterUserId)
                .getResultList();
    }

    /**
     * Student dashboard: tickets owned by this user id, sender email, or linked LPU email
     * (outside-email tickets also appear under the declared campus account).
     */
    @Transactional(readOnly = true)
    public List<Ticket> findMineByUserIdOrEmailOrderByCreatedAtDesc(Long requesterUserId, String email) {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.requesterUserId = :userId "
                                + "OR lower(t.requesterEmail) = lower(:email) "
                                + "OR (t.requesterLpuEmail IS NOT NULL AND lower(t.requesterLpuEmail) = lower(:email)) "
                                + "ORDER BY t.createdAt DESC",
                        Ticket.class
                )
                .setParameter("userId", requesterUserId)
                .setParameter("email", email)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<Ticket> findById(Long id) {
        return currentSession()
                .createQuery("FROM Ticket t WHERE t.id = :id", Ticket.class)
                .setParameter("id", id)
                .uniqueResultOptional();
    }

    /** Oldest CLOSED ticket for this user that has no CSM rating yet. */
    @Transactional(readOnly = true)
    public Optional<Ticket> findOldestUnratedClosedByUserIdOrEmail(Long requesterUserId, String email) {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.status = :status "
                                + "AND t.category <> :emailLinkCategory "
                                + "AND (t.requesterUserId = :userId OR lower(t.requesterEmail) = lower(:email) "
                                + "OR (t.requesterLpuEmail IS NOT NULL AND lower(t.requesterLpuEmail) = lower(:email))) "
                                + "AND t.id NOT IN (SELECT c.ticketId FROM TicketCsm c) "
                                + "ORDER BY t.createdAt ASC, t.id ASC",
                        Ticket.class
                )
                .setParameter("status", TicketStatus.CLOSED)
                .setParameter("emailLinkCategory", PendingRequesterEmail.LINK_LPU_EMAIL_CATEGORY)
                .setParameter("userId", requesterUserId)
                .setParameter("email", email)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    /** Oldest CLOSED ticket for this person (email and/or person identity) without CSM. */
    @Transactional(readOnly = true)
    public Optional<Ticket> findOldestUnratedClosedForPerson(
            String email,
            String personType,
            String personNo
    ) {
        StringBuilder hql = new StringBuilder(
                "FROM Ticket t WHERE t.status = :status "
                        + "AND t.category <> :emailLinkCategory "
                        + "AND t.id NOT IN (SELECT c.ticketId FROM TicketCsm c) "
                        + "AND ("
        );
        boolean hasEmail = email != null && !email.isBlank() && !PendingRequesterEmail.isPending(email);
        boolean hasPerson = personType != null && !personType.isBlank()
                && personNo != null && !personNo.isBlank();
        if (!hasEmail && !hasPerson) {
            return Optional.empty();
        }
        if (hasEmail) {
            hql.append("(lower(t.requesterEmail) = lower(:email) "
                    + "OR (t.requesterLpuEmail IS NOT NULL AND lower(t.requesterLpuEmail) = lower(:email)))");
        }
        if (hasEmail && hasPerson) {
            hql.append(" OR ");
        }
        if (hasPerson) {
            hql.append("(t.requesterPersonType = :personType AND t.requesterPersonNo = :personNo)");
        }
        hql.append(") ORDER BY t.createdAt ASC, t.id ASC");

        var query = currentSession().createQuery(hql.toString(), Ticket.class)
                .setParameter("status", TicketStatus.CLOSED)
                .setParameter("emailLinkCategory", PendingRequesterEmail.LINK_LPU_EMAIL_CATEGORY)
                .setMaxResults(1);
        if (hasEmail) {
            query.setParameter("email", email);
        }
        if (hasPerson) {
            query.setParameter("personType", personType.trim().toUpperCase());
            query.setParameter("personNo", personNo.trim());
        }
        return query.uniqueResultOptional();
    }

    @Transactional(readOnly = true)
    public boolean hasUnratedClosedByUserIdOrEmail(Long requesterUserId, String email) {
        Long count = currentSession()
                .createQuery(
                        "SELECT count(t.id) FROM Ticket t WHERE t.status = :status "
                                + "AND t.category <> :emailLinkCategory "
                                + "AND (t.requesterUserId = :userId OR lower(t.requesterEmail) = lower(:email) "
                                + "OR (t.requesterLpuEmail IS NOT NULL AND lower(t.requesterLpuEmail) = lower(:email))) "
                                + "AND t.id NOT IN (SELECT c.ticketId FROM TicketCsm c)",
                        Long.class
                )
                .setParameter("status", TicketStatus.CLOSED)
                .setParameter("emailLinkCategory", PendingRequesterEmail.LINK_LPU_EMAIL_CATEGORY)
                .setParameter("userId", requesterUserId)
                .setParameter("email", email)
                .uniqueResult();
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public boolean hasUnratedClosedForPerson(String email, String personType, String personNo) {
        return findOldestUnratedClosedForPerson(email, personType, personNo).isPresent();
    }

    /**
     * All tickets for a requester matched by email and/or person type + person number
     * (same identity rules as CSM), newest first.
     */
    @Transactional(readOnly = true)
    public List<Ticket> findHistoryForPerson(String email, String personType, String personNo) {
        boolean hasEmail = email != null && !email.isBlank() && !PendingRequesterEmail.isPending(email);
        boolean hasPerson = personType != null && !personType.isBlank()
                && personNo != null && !personNo.isBlank();
        if (!hasEmail && !hasPerson) {
            return List.of();
        }

        StringBuilder hql = new StringBuilder("FROM Ticket t WHERE (");
        if (hasEmail) {
            hql.append("(lower(t.requesterEmail) = lower(:email) "
                    + "OR (t.requesterLpuEmail IS NOT NULL AND lower(t.requesterLpuEmail) = lower(:email)))");
        }
        if (hasEmail && hasPerson) {
            hql.append(" OR ");
        }
        if (hasPerson) {
            hql.append("(t.requesterPersonType = :personType AND t.requesterPersonNo = :personNo)");
        }
        hql.append(") ORDER BY t.createdAt DESC, t.id DESC");

        var query = currentSession().createQuery(hql.toString(), Ticket.class);
        if (hasEmail) {
            query.setParameter("email", email.trim());
        }
        if (hasPerson) {
            query.setParameter("personType", personType.trim().toUpperCase());
            query.setParameter("personNo", personNo.trim());
        }
        return query.getResultList();
    }

    /** Stamp declared LPU email onto all tickets owned by this outside-email user. */
    @Transactional
    public int stampRequesterLpuEmailForUser(Long userId, String lpuEmail) {
        if (userId == null || lpuEmail == null || lpuEmail.isBlank()) {
            return 0;
        }
        return currentSession()
                .createMutationQuery(
                        "UPDATE Ticket t SET t.requesterLpuEmail = :lpu, t.updatedAt = :now "
                                + "WHERE t.requesterUserId = :userId"
                )
                .setParameter("lpu", lpuEmail.trim().toLowerCase())
                .setParameter("now", Instant.now())
                .setParameter("userId", userId)
                .executeUpdate();
    }

    /** Admin ticket table: all channels (online + onsite), optionally filtered by status. */
    @Transactional(readOnly = true)
    public List<Ticket> findAllOrderByCreatedAtDesc(TicketStatus status) {
        String hql = "FROM Ticket t"
                + (status != null ? " WHERE t.status = :status" : "")
                + " ORDER BY t.createdAt DESC";
        var query = currentSession().createQuery(hql, Ticket.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public TicketPage page(TicketListQuery query, Long unreadUserId) {
        Object[] stats = loadPageStats(query);
        long total = ((Number) stats[0]).longValue();
        long openCount = ((Number) stats[1]).longValue();
        long inProgressCount = ((Number) stats[2]).longValue();
        List<Ticket> items = total == 0 ? List.of() : loadPageItems(query);
        long unreadTotal = unreadUserId == null || total == 0 ? 0 : countUnread(query, unreadUserId);
        return new TicketPage(items, total, unreadTotal, openCount, inProgressCount);
    }

    private Object[] loadPageStats(TicketListQuery query) {
        String sql = """
                SELECT count(*)::bigint,
                       count(*) FILTER (WHERE t.status = 'OPEN')::bigint,
                       count(*) FILTER (WHERE t.status = 'IN_PROGRESS')::bigint
                FROM tickets t
                """
                + ticketWhereSql(query);
        Object raw = bindTicketFilters(currentSession().createNativeQuery(sql), query).getSingleResult();
        if (raw instanceof Object[] row) {
            return row;
        }
        return new Object[]{raw, 0L, 0L};
    }

    private List<Ticket> loadPageItems(TicketListQuery query) {
        String sql = """
                SELECT t.id
                FROM tickets t
                LEFT JOIN users u ON u.id = t.assigned_admin_id
                LEFT JOIN ticket_categories cat ON cat.code = t.category
                LEFT JOIN ticket_categories sub ON sub.code = t.subcategory
                """
                + ticketWhereSql(query)
                + " ORDER BY "
                + orderBySql(query.sort(), query.ascending());
        List<?> idRows = bindTicketFilters(currentSession().createNativeQuery(sql), query)
                .setFirstResult(query.offset())
                .setMaxResults(query.limit())
                .getResultList();
        if (idRows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(idRows.size());
        for (Object row : idRows) {
            if (row instanceof Number number) {
                ids.add(number.longValue());
            } else if (row instanceof Object[] cells && cells.length > 0 && cells[0] instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        List<Ticket> loaded = currentSession()
                .createQuery("FROM Ticket t WHERE t.id IN :ids", Ticket.class)
                .setParameter("ids", ids)
                .getResultList();
        Map<Long, Ticket> byId = new LinkedHashMap<>();
        for (Ticket ticket : loaded) {
            byId.put(ticket.getId(), ticket);
        }
        List<Ticket> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Ticket ticket = byId.get(id);
            if (ticket != null) {
                ordered.add(ticket);
            }
        }
        return ordered;
    }

    private long countUnread(TicketListQuery query, Long userId) {
        String sql = """
                SELECT count(*)::bigint
                FROM ticket_messages m
                INNER JOIN tickets t ON t.id = m.ticket_id
                LEFT JOIN ticket_message_reads r
                    ON r.ticket_id = m.ticket_id AND r.user_id = :unreadUserId
                """
                + ticketWhereSql(query)
                + """
                 AND m.id > COALESCE(r.last_read_message_id, 0)
                 AND (m.author_user_id IS NULL OR m.author_user_id <> :unreadUserId)
                """;
        Number count = (Number) bindTicketFilters(currentSession().createNativeQuery(sql), query)
                .setParameter("unreadUserId", userId)
                .getSingleResult();
        return count == null ? 0 : count.longValue();
    }

    private static String ticketWhereSql(TicketListQuery query) {
        List<String> clauses = new ArrayList<>();
        if (query.status() != null) {
            clauses.add("t.status = :status");
        }
        if (query.channel() != null) {
            clauses.add("t.channel = :channel");
        }
        if (query.unassignedOnly()) {
            clauses.add("t.assigned_admin_id IS NULL");
        } else if (query.assignedAdminId() != null) {
            clauses.add("t.assigned_admin_id = :assignedAdminId");
        }
        if (query.category() != null) {
            clauses.add("t.category = :category");
        }
        if (query.subcategory() != null) {
            clauses.add("t.subcategory = :subcategory");
        }
        if (query.requesterUserId() != null && query.requesterEmail() != null) {
            clauses.add("""
                    (t.requester_user_id = :requesterUserId
                     OR lower(t.requester_email) = lower(:requesterEmail)
                     OR (t.requester_lpu_email IS NOT NULL
                         AND lower(t.requester_lpu_email) = lower(:requesterEmail)))
                    """.trim());
        } else if (query.requesterUserId() != null) {
            clauses.add("t.requester_user_id = :requesterUserId");
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", clauses) + " ";
    }

    private static NativeQuery<?> bindTicketFilters(NativeQuery<?> nativeQuery, TicketListQuery query) {
        if (query.status() != null) {
            nativeQuery.setParameter("status", query.status().name());
        }
        if (query.channel() != null) {
            nativeQuery.setParameter("channel", query.channel().name());
        }
        if (query.assignedAdminId() != null && !query.unassignedOnly()) {
            nativeQuery.setParameter("assignedAdminId", query.assignedAdminId());
        }
        if (query.category() != null) {
            nativeQuery.setParameter("category", query.category());
        }
        if (query.subcategory() != null) {
            nativeQuery.setParameter("subcategory", query.subcategory());
        }
        if (query.requesterUserId() != null) {
            nativeQuery.setParameter("requesterUserId", query.requesterUserId());
            if (query.requesterEmail() != null) {
                nativeQuery.setParameter("requesterEmail", query.requesterEmail());
            }
        }
        return nativeQuery;
    }

    private static String orderBySql(String sort, boolean ascending) {
        String dir = ascending ? "ASC" : "DESC";
        String order = switch (sort) {
            case "ticketNumber" -> "lower(t.ticket_number) " + dir;
            case "subject" -> "lower(t.subject) " + dir;
            case "categoryLabel" -> "lower(coalesce(cat.label, t.category)) " + dir
                    + ", lower(coalesce(sub.label, t.subcategory)) " + dir;
            case "requesterName" -> "lower(t.requester_name) " + dir;
            case "requesterEmail" -> "lower(t.requester_email) " + dir;
            case "assignedAdminName" -> "lower(u.name) " + dir + " NULLS LAST";
            case "status" -> "CASE t.status WHEN 'OPEN' THEN 0 WHEN 'IN_PROGRESS' THEN 1 "
                    + "WHEN 'RESOLVED' THEN 2 WHEN 'CLOSED' THEN 3 ELSE 4 END " + dir;
            case "channel" -> "t.channel " + dir;
            case "resolveHours" -> "(CASE WHEN t.resolved_at IS NULL THEN -1 "
                    + "ELSE EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) END) " + dir;
            case "createdAt" -> "t.created_at " + dir;
            default -> "t.updated_at " + dir;
        };
        return order + ", t.id " + dir;
    }

    /** Open onsite tickets waiting on the line, oldest first. */
    @Transactional(readOnly = true)
    public List<Ticket> findWaitingOnsiteOrderByQueueNumber() {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.channel = :channel AND t.status = :status ORDER BY t.createdAt ASC, t.id ASC",
                        Ticket.class
                )
                .setParameter("channel", TicketChannel.ONSITE_RFID)
                .setParameter("status", TicketStatus.OPEN)
                .getResultList();
    }

    /** Onsite tickets currently in progress and assigned to an admin. */
    @Transactional(readOnly = true)
    public List<Ticket> findServingOnsite() {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.channel = :channel AND t.status = :status AND t.assignedAdminId IS NOT NULL"
                                + " ORDER BY t.updatedAt ASC",
                        Ticket.class
                )
                .setParameter("channel", TicketChannel.ONSITE_RFID)
                .setParameter("status", TicketStatus.IN_PROGRESS)
                .getResultList();
    }

    /**
     * Locks and returns the oldest waiting onsite ticket so two admins calling
     * "next" at the same moment can never claim the same person.
     */
    @Transactional
    public Optional<Ticket> lockOldestWaitingOnsite() {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.channel = :channel AND t.status = :status ORDER BY t.queueNumber ASC",
                        Ticket.class
                )
                .setParameter("channel", TicketChannel.ONSITE_RFID)
                .setParameter("status", TicketStatus.OPEN)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    /**
     * Locks a specific waiting onsite ticket so two admins cannot claim it at once.
     */
    @Transactional
    public Optional<Ticket> lockWaitingOnsiteById(Long ticketId) {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.id = :id AND t.channel = :channel AND t.status = :status",
                        Ticket.class
                )
                .setParameter("id", ticketId)
                .setParameter("channel", TicketChannel.ONSITE_RFID)
                .setParameter("status", TicketStatus.OPEN)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .uniqueResultOptional();
    }

    /** Most recently created tickets across all channels (for the monitoring board). */
    @Transactional(readOnly = true)
    public List<Ticket> findRecentOrderByCreatedAtDesc(int limit) {
        return currentSession()
                .createQuery("FROM Ticket t ORDER BY t.createdAt DESC", Ticket.class)
                .setMaxResults(Math.min(Math.max(limit, 1), 100))
                .getResultList();
    }

    /** Recent online tickets only — lighter payload for the TV monitoring board. */
    @Transactional(readOnly = true)
    public List<Ticket> findRecentOnlineOrderByCreatedAtDesc(int limit) {
        return currentSession()
                .createQuery(
                        """
                        FROM Ticket t
                        WHERE t.channel = :channel
                          AND t.status NOT IN (:excluded)
                        ORDER BY t.createdAt DESC
                        """,
                        Ticket.class
                )
                .setParameter("channel", TicketChannel.ONLINE)
                .setParameterList("excluded", List.of(TicketStatus.CLOSED, TicketStatus.RESOLVED))
                .setMaxResults(Math.min(Math.max(limit, 1), 100))
                .getResultList();
    }

    @Transactional(readOnly = true)
    public long countCreatedBetween(Instant from, Instant to) {
        Long count = currentSession()
                .createQuery(
                        "SELECT count(t.id) FROM Ticket t WHERE t.createdAt >= :from AND t.createdAt < :to",
                        Long.class
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .uniqueResult();
        return count != null ? count : 0L;
    }

    @Transactional(readOnly = true)
    public long countClosedBetween(Instant from, Instant to) {
        Long count = currentSession()
                .createQuery(
                        "SELECT count(t.id) FROM Ticket t WHERE t.status = :status "
                                + "AND t.resolvedAt IS NOT NULL AND t.resolvedAt >= :from AND t.resolvedAt < :to",
                        Long.class
                )
                .setParameter("status", TicketStatus.CLOSED)
                .setParameter("from", from)
                .setParameter("to", to)
                .uniqueResult();
        return count != null ? count : 0L;
    }

    @Transactional(readOnly = true)
    public long countByStatus(TicketStatus status) {
        Long count = currentSession()
                .createQuery("SELECT count(t.id) FROM Ticket t WHERE t.status = :status", Long.class)
                .setParameter("status", status)
                .uniqueResult();
        return count != null ? count : 0L;
    }

    @Transactional(readOnly = true)
    public long countUnassignedOpen() {
        Long count = currentSession()
                .createQuery(
                        "SELECT count(t.id) FROM Ticket t WHERE t.status = :status AND t.assignedAdminId IS NULL",
                        Long.class
                )
                .setParameter("status", TicketStatus.OPEN)
                .uniqueResult();
        return count != null ? count : 0L;
    }

    /** Average resolve hours for tickets closed in range with resolvedAt set. */
    @Transactional(readOnly = true)
    public Double avgResolveHoursBetween(Instant from, Instant to) {
        Object result = currentSession()
                .createNativeQuery(
                        """
                        SELECT AVG(EXTRACT(EPOCH FROM (resolved_at - created_at)) / 3600.0)
                        FROM tickets
                        WHERE status = 'CLOSED'
                          AND resolved_at IS NOT NULL
                          AND resolved_at >= :from
                          AND resolved_at < :to
                        """
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .uniqueResult();
        if (result == null) {
            return null;
        }
        return ((Number) result).doubleValue();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> countGroupByStatus() {
        return currentSession()
                .createQuery("SELECT t.status, count(t.id) FROM Ticket t GROUP BY t.status", Object[].class)
                .getResultList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> countCreatedGroupByChannel(Instant from, Instant to) {
        return currentSession()
                .createQuery(
                        "SELECT t.channel, count(t.id) FROM Ticket t "
                                + "WHERE t.createdAt >= :from AND t.createdAt < :to GROUP BY t.channel",
                        Object[].class
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> countCreatedGroupByCategory(Instant from, Instant to) {
        return currentSession()
                .createQuery(
                        "SELECT t.category, count(t.id) FROM Ticket t "
                                + "WHERE t.createdAt >= :from AND t.createdAt < :to GROUP BY t.category "
                                + "ORDER BY count(t.id) DESC",
                        Object[].class
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** Tickets created in range grouped by category + concern (subcategory). */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> countCreatedGroupByConcern(Instant from, Instant to) {
        return currentSession()
                .createQuery(
                        "SELECT t.category, t.subcategory, count(t.id) FROM Ticket t "
                                + "WHERE t.createdAt >= :from AND t.createdAt < :to "
                                + "GROUP BY t.category, t.subcategory "
                                + "ORDER BY count(t.id) DESC",
                        Object[].class
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> countCreatedByDay(Instant from, Instant to) {
        return currentSession()
                .createNativeQuery(
                        """
                        SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day, COUNT(*)
                        FROM tickets
                        WHERE created_at >= :from AND created_at < :to
                        GROUP BY day
                        ORDER BY day
                        """
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> countClosedByDay(Instant from, Instant to) {
        return currentSession()
                .createNativeQuery(
                        """
                        SELECT to_char(resolved_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day, COUNT(*)
                        FROM tickets
                        WHERE status = 'CLOSED'
                          AND resolved_at IS NOT NULL
                          AND resolved_at >= :from
                          AND resolved_at < :to
                        GROUP BY day
                        ORDER BY day
                        """
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /**
     * Rows: adminId, status, count — for open/in_progress (current) and closed in range.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Object[]> assigneeLoad(Instant from, Instant to) {
        return currentSession()
                .createQuery(
                        "SELECT t.assignedAdminId, t.status, count(t.id) FROM Ticket t "
                                + "WHERE t.assignedAdminId IS NOT NULL AND ("
                                + "t.status IN (:open, :inProgress) "
                                + "OR (t.status = :closed AND t.resolvedAt IS NOT NULL "
                                + "AND t.resolvedAt >= :from AND t.resolvedAt < :to)"
                                + ") GROUP BY t.assignedAdminId, t.status",
                        Object[].class
                )
                .setParameter("open", TicketStatus.OPEN)
                .setParameter("inProgress", TicketStatus.IN_PROGRESS)
                .setParameter("closed", TicketStatus.CLOSED)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /**
     * Tickets for one assignee matching {@link #assigneeLoad} rules, newest activity first.
     */
    @Transactional(readOnly = true)
    public List<Ticket> findAssigneeTickets(Long adminId, Instant from, Instant to, int limit) {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.assignedAdminId = :adminId AND ("
                                + "t.status IN (:open, :inProgress) "
                                + "OR (t.status = :closed AND t.resolvedAt IS NOT NULL "
                                + "AND t.resolvedAt >= :from AND t.resolvedAt < :to)"
                                + ") ORDER BY t.updatedAt DESC",
                        Ticket.class
                )
                .setParameter("adminId", adminId)
                .setParameter("open", TicketStatus.OPEN)
                .setParameter("inProgress", TicketStatus.IN_PROGRESS)
                .setParameter("closed", TicketStatus.CLOSED)
                .setParameter("from", from)
                .setParameter("to", to)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    /** Tickets created in range, newest first. */
    @Transactional(readOnly = true)
    public List<Ticket> findCreatedBetween(Instant from, Instant to, int limit) {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.createdAt >= :from AND t.createdAt < :to "
                                + "ORDER BY t.createdAt DESC, t.id DESC",
                        Ticket.class
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    /** Tickets resolved (or closed) in range, newest first. */
    @Transactional(readOnly = true)
    public List<Ticket> findResolvedBetween(Instant from, Instant to, int limit) {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.resolvedAt IS NOT NULL "
                                + "AND t.resolvedAt >= :from AND t.resolvedAt < :to "
                                + "AND t.status IN (:resolved, :closed) "
                                + "ORDER BY t.resolvedAt DESC",
                        Ticket.class
                )
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("resolved", TicketStatus.RESOLVED)
                .setParameter("closed", TicketStatus.CLOSED)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    /** Resolved tickets whose resolvedAt is on or before the cutoff. */
    @Transactional(readOnly = true)
    public List<Ticket> findResolvedBefore(Instant cutoff, int limit) {
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.status = :status "
                                + "AND t.resolvedAt IS NOT NULL AND t.resolvedAt <= :cutoff "
                                + "ORDER BY t.resolvedAt ASC",
                        Ticket.class
                )
                .setParameter("status", TicketStatus.RESOLVED)
                .setParameter("cutoff", cutoff)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<Ticket> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return currentSession()
                .createQuery("FROM Ticket t WHERE t.id IN :ids", Ticket.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<Ticket> findByPerson(String personType, String personNo) {
        if (personType == null || personType.isBlank() || personNo == null || personNo.isBlank()) {
            return List.of();
        }
        return currentSession()
                .createQuery(
                        "FROM Ticket t WHERE t.requesterPersonType = :personType "
                                + "AND t.requesterPersonNo = :personNo "
                                + "ORDER BY t.createdAt DESC, t.id DESC",
                        Ticket.class
                )
                .setParameter("personType", personType.trim().toUpperCase())
                .setParameter("personNo", personNo.trim())
                .getResultList();
    }

    @Transactional(readOnly = true)
    public boolean existsOpenEmailLinkForPerson(String personType, String personNo) {
        if (personType == null || personType.isBlank() || personNo == null || personNo.isBlank()) {
            return false;
        }
        Long count = currentSession()
                .createQuery(
                        "SELECT count(t.id) FROM Ticket t WHERE t.category = :category "
                                + "AND t.status IN (:open, :inProgress) "
                                + "AND t.requesterPersonType = :personType "
                                + "AND t.requesterPersonNo = :personNo",
                        Long.class
                )
                .setParameter("category", PendingRequesterEmail.LINK_LPU_EMAIL_CATEGORY)
                .setParameter("open", TicketStatus.OPEN)
                .setParameter("inProgress", TicketStatus.IN_PROGRESS)
                .setParameter("personType", personType.trim().toUpperCase())
                .setParameter("personNo", personNo.trim())
                .uniqueResult();
        return count != null && count > 0;
    }

    @Transactional
    public Ticket persist(Ticket ticket) {
        Session session = currentSession();
        session.persist(ticket);
        session.flush();
        return ticket;
    }

    @Transactional
    public Ticket save(Ticket ticket) {
        Session session = currentSession();
        Ticket merged = session.merge(ticket);
        session.flush();
        return merged;
    }
}
