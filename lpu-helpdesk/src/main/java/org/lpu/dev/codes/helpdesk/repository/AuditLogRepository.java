package org.lpu.dev.codes.helpdesk.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.lpu.dev.codes.helpdesk.dto.AuditLogQuery;
import org.lpu.dev.codes.helpdesk.model.AuditAction;
import org.lpu.dev.codes.helpdesk.model.AuditLog;
import org.lpu.dev.codes.helpdesk.model.AuditResourceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuditLogRepository {

    public record Page(List<AuditLog> items, long total) {
    }

    private static final String SELECT_COLUMNS = """
            SELECT id, actor_id, actor_email, actor_name, actor_role, action, resource_type,
                   resource_id, resource_label, summary, details, created_at
            FROM audit_logs
            """;

    private static final RowMapper<AuditLog> ROW_MAPPER = (rs, rowNum) -> {
        AuditLog log = new AuditLog();
        log.setId(rs.getLong("id"));
        long actorId = rs.getLong("actor_id");
        log.setActorId(rs.wasNull() ? null : actorId);
        log.setActorEmail(rs.getString("actor_email"));
        log.setActorName(rs.getString("actor_name"));
        log.setActorRole(rs.getString("actor_role"));
        String action = rs.getString("action");
        log.setAction(action == null ? null : AuditAction.valueOf(action));
        String resourceType = rs.getString("resource_type");
        log.setResourceType(resourceType == null ? null : AuditResourceType.valueOf(resourceType));
        log.setResourceId(rs.getString("resource_id"));
        log.setResourceLabel(rs.getString("resource_label"));
        log.setSummary(rs.getString("summary"));
        log.setDetails(rs.getString("details"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        log.setCreatedAt(createdAt == null ? Instant.now() : createdAt.toInstant());
        return log;
    };

    private final JdbcTemplate jdbc;

    public AuditLogRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Transactional
    public AuditLog persist(AuditLog log) {
        KeyHolder keys = new GeneratedKeyHolder();
        Instant created = log.getCreatedAt() != null ? log.getCreatedAt() : Instant.now();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                    """
                    INSERT INTO audit_logs
                        (actor_id, actor_email, actor_name, actor_role, action, resource_type,
                         resource_id, resource_label, summary, details, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"}
            );
            if (log.getActorId() == null) {
                ps.setObject(1, null);
            } else {
                ps.setLong(1, log.getActorId());
            }
            ps.setString(2, log.getActorEmail());
            ps.setString(3, log.getActorName());
            ps.setString(4, log.getActorRole());
            ps.setString(5, log.getAction().name());
            ps.setString(6, log.getResourceType().name());
            ps.setString(7, log.getResourceId());
            ps.setString(8, log.getResourceLabel());
            ps.setString(9, log.getSummary());
            ps.setString(10, log.getDetails());
            ps.setTimestamp(11, Timestamp.from(created));
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id != null) {
            log.setId(id.longValue());
        }
        log.setCreatedAt(created);
        return log;
    }

    @Transactional(readOnly = true)
    public Page page(AuditLogQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendFilters(query, where, args);

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs" + where,
                Long.class,
                args.toArray()
        );
        long count = total != null ? total : 0L;
        if (count == 0) {
            return new Page(List.of(), 0);
        }

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(Math.max(1, query.limit()));
        pageArgs.add(Math.max(0, query.offset()));
        List<AuditLog> items = jdbc.query(
                SELECT_COLUMNS + where + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                pageArgs.toArray()
        );
        return new Page(items, count);
    }

    private static void appendFilters(AuditLogQuery query, StringBuilder where, List<Object> args) {
        if (query.search() != null && !query.search().isBlank()) {
            String like = "%" + query.search().trim().toLowerCase() + "%";
            where.append("""
                     AND (
                        lower(summary) LIKE ?
                        OR lower(coalesce(actor_name, '')) LIKE ?
                        OR lower(coalesce(actor_email, '')) LIKE ?
                        OR lower(coalesce(resource_label, '')) LIKE ?
                        OR lower(coalesce(resource_id, '')) LIKE ?
                        OR lower(coalesce(details, '')) LIKE ?
                        OR lower(action) LIKE ?
                     )
                    """);
            for (int i = 0; i < 7; i++) {
                args.add(like);
            }
        }
        if (query.action() != null) {
            where.append(" AND action = ?");
            args.add(query.action().name());
        }
        if (query.resourceType() != null) {
            where.append(" AND resource_type = ?");
            args.add(query.resourceType().name());
        }
        if (query.from() != null) {
            where.append(" AND created_at >= ?");
            args.add(Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            where.append(" AND created_at < ?");
            args.add(Timestamp.from(query.to()));
        }
    }
}
