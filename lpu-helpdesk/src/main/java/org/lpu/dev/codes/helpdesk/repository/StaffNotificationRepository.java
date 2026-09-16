package org.lpu.dev.codes.helpdesk.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.lpu.dev.codes.helpdesk.model.StaffNotification;
import org.lpu.dev.codes.helpdesk.model.StaffNotificationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StaffNotificationRepository {

    private static final int MAX_LIST = 50;

    private static final RowMapper<StaffNotification> ROW_MAPPER = (rs, rowNum) -> {
        StaffNotification notification = new StaffNotification();
        notification.setId(rs.getLong("id"));
        notification.setRecipientId(rs.getLong("recipient_id"));
        notification.setType(StaffNotificationType.valueOf(rs.getString("type")));
        notification.setTitle(rs.getString("title"));
        notification.setBody(rs.getString("body"));
        long ticketId = rs.getLong("ticket_id");
        notification.setTicketId(rs.wasNull() ? null : ticketId);
        notification.setTicketChannel(rs.getString("ticket_channel"));
        Timestamp readAt = rs.getTimestamp("read_at");
        notification.setReadAt(readAt == null ? null : readAt.toInstant());
        Timestamp createdAt = rs.getTimestamp("created_at");
        notification.setCreatedAt(createdAt == null ? Instant.now() : createdAt.toInstant());
        return notification;
    };

    private final JdbcTemplate jdbc;

    public StaffNotificationRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Transactional(readOnly = true)
    public Optional<StaffNotification> findById(Long id) {
        List<StaffNotification> rows = jdbc.query(
                """
                SELECT id, recipient_id, type, title, body, ticket_id, ticket_channel, read_at, created_at
                FROM staff_notifications
                WHERE id = ?
                """,
                ROW_MAPPER,
                id
        );
        return rows.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<StaffNotification> findRecentForRecipient(Long recipientId) {
        return jdbc.query(
                """
                SELECT id, recipient_id, type, title, body, ticket_id, ticket_channel, read_at, created_at
                FROM staff_notifications
                WHERE recipient_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """,
                ROW_MAPPER,
                recipientId,
                MAX_LIST
        );
    }

    @Transactional(readOnly = true)
    public long countUnread(Long recipientId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM staff_notifications WHERE recipient_id = ? AND read_at IS NULL",
                Long.class,
                recipientId
        );
        return count == null ? 0 : count;
    }

    @Transactional
    public StaffNotification persist(StaffNotification notification) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                    """
                    INSERT INTO staff_notifications
                        (recipient_id, type, title, body, ticket_id, ticket_channel, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"}
            );
            ps.setLong(1, notification.getRecipientId());
            ps.setString(2, notification.getType().name());
            ps.setString(3, notification.getTitle());
            ps.setString(4, notification.getBody());
            if (notification.getTicketId() == null) {
                ps.setObject(5, null);
            } else {
                ps.setLong(5, notification.getTicketId());
            }
            ps.setString(6, notification.getTicketChannel());
            Instant created = notification.getCreatedAt() != null ? notification.getCreatedAt() : Instant.now();
            ps.setTimestamp(7, Timestamp.from(created));
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id != null) {
            notification.setId(id.longValue());
        }
        return notification;
    }

    @Transactional
    public StaffNotification save(StaffNotification notification) {
        jdbc.update(
                "UPDATE staff_notifications SET read_at = ? WHERE id = ?",
                notification.getReadAt() == null ? null : Timestamp.from(notification.getReadAt()),
                notification.getId()
        );
        return notification;
    }

    @Transactional
    public int markAllRead(Long recipientId, Instant readAt) {
        return jdbc.update(
                "UPDATE staff_notifications SET read_at = ? WHERE recipient_id = ? AND read_at IS NULL",
                Timestamp.from(readAt),
                recipientId
        );
    }

    @Transactional
    public int deleteAllForRecipient(Long recipientId) {
        return jdbc.update("DELETE FROM staff_notifications WHERE recipient_id = ?", recipientId);
    }
}
