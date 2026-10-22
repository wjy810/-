package com.jobproof.modules.audit.application;

import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.page.PageResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private final JdbcTemplate jdbc;
    public AuditQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String EVENTS = """
        (SELECT id,actor_id,action,object_type,object_id,summary,created_at,'APP' AS source,'RECORDED' AS result,NULL AS trace_id FROM audit_events
         UNION ALL SELECT id,actor_account_id,action,object_type,object_id,summary,created_at,'AI' AS source,result,trace_id FROM ai_audit_log) e
        LEFT JOIN accounts a ON a.id=e.actor_id
        """;

    @Transactional(readOnly = true)
    public PageResult<EventView> list(CurrentAccount current, String actor, String action, String objectType,
            String objectId, Instant from, Instant to, int page, int size) {
        if (current == null || !"ADMIN".equals(current.role())) throw AppException.forbidden("ADMIN_REQUIRED", "只有管理员可以查看审计日志");
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && !from.isBefore(to)))
            throw AppException.user("AUDIT_FILTER_INVALID", "请检查分页或时间范围");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (actor != null && !actor.isBlank()) { where.append(" AND (e.actor_id=? OR a.email LIKE ?)"); args.add(actor.trim()); args.add("%" + actor.trim() + "%"); }
        exact(where, args, "e.action", action);
        exact(where, args, "e.object_type", objectType);
        exact(where, args, "e.object_id", objectId);
        if (from != null) { where.append(" AND e.created_at>=?"); args.add(from); }
        if (to != null) { where.append(" AND e.created_at<?"); args.add(to); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + EVENTS + where, Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        List<EventView> items = jdbc.query("SELECT e.*,a.email AS actor_label FROM " + EVENTS + where
                + " ORDER BY e.created_at DESC,e.source,e.id DESC LIMIT ? OFFSET ?", (rs, n) -> new EventView(
                rs.getString("source") + ":" + rs.getString("id"), rs.getString("actor_id"),
                rs.getString("actor_label"), rs.getString("action"), rs.getString("object_type"),
                rs.getString("object_id"), AuditService.sanitize(rs.getString("summary")),
                rs.getString("source"), rs.getString("result"), rs.getString("trace_id"),
                rs.getTimestamp("created_at").toInstant()), args.toArray());
        return new PageResult<>(items, total == null ? 0 : total, page, size);
    }
    private static void exact(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) { where.append(" AND ").append(column).append("=?"); args.add(value.trim()); }
    }
    public record EventView(String id, String actorId, String actorLabel, String action, String objectType,
            String objectId, String summary, String source, String result, String traceId, Instant createdAt) {}
}
