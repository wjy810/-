package com.jobproof.modules.careerplanning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.careerplanning.application.CareerPlanningSseService.Event;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CareerPlanningEventService {

    private static final Logger log = LoggerFactory.getLogger(CareerPlanningEventService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final CareerPlanningSseService sse;

    public CareerPlanningEventService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock,
            CareerPlanningSseService sse) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.sse = sse;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Event append(String accountId, String sessionId, String eventType,
            Map<String, Object> payload) {
        return appendInternal(accountId, sessionId, eventType, payload, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Event appendOnce(String accountId, String sessionId, String eventType,
            String dedupeKey, Map<String, Object> payload) {
        return appendInternal(accountId, sessionId, eventType, payload, requiredKey(dedupeKey));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Event appendAsync(String accountId, String sessionId, String eventType,
            String dedupeKey, Map<String, Object> payload) {
        return appendInternal(accountId, sessionId, eventType, payload, requiredKey(dedupeKey));
    }

    private Event appendInternal(String accountId, String sessionId, String eventType,
            Map<String, Object> payload, String dedupeKey) {
        jdbc.query("SELECT id FROM career_planning_sessions WHERE id=? AND account_id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), sessionId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.forbidden("OBJECT_FORBIDDEN", "职业规划会话不可访问"));
        if (dedupeKey != null) {
            Event existing = jdbc.query("""
                    SELECT session_id,sequence_no,event_type,payload_json,created_at
                    FROM career_planning_stream_events
                    WHERE session_id=? AND account_id=? AND dedupe_key=?
                    """, (rs, row) -> new Event(rs.getString("session_id"), rs.getLong("sequence_no"),
                            rs.getString("event_type"), read(rs.getString("payload_json")),
                            rs.getTimestamp("created_at").toInstant()), sessionId, accountId, dedupeKey)
                    .stream().findFirst().orElse(null);
            if (existing != null) return existing;
        }
        Long previous = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no),0) FROM career_planning_stream_events WHERE session_id=?",
                Long.class, sessionId);
        long sequence = (previous == null ? 0L : previous) + 1L;
        Instant now = clock.now();
        Event event = new Event(sessionId, sequence, eventType, Map.copyOf(payload), now);
        jdbc.update("""
                INSERT INTO career_planning_stream_events
                  (id,session_id,account_id,sequence_no,event_type,payload_json,created_at,expires_at,dedupe_key)
                VALUES(?,?,?,?,?,?,?,?,?)
                """, Ids.newId(), sessionId, accountId, sequence, eventType, json(payload),
                now, now.plus(7, ChronoUnit.DAYS), dedupeKey);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publishQuietly(event); }
            });
        } else {
            publishQuietly(event);
        }
        return event;
    }

    private void publishQuietly(Event event) {
        try {
            sse.publish(event);
        } catch (RuntimeException exception) {
            log.warn("Career planning event committed but live delivery failed sessionId={} sequence={}",
                    event.sessionId(), event.sequence());
        }
    }

    private static String requiredKey(String value) {
        if (value == null || value.isBlank() || value.length() > 160) {
            throw AppException.user("CP_EVENT_DEDUPE_KEY_INVALID", "职业规划事件幂等标识无效");
        }
        return value.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String value) {
        try {
            return mapper.readValue(value, Map.class);
        } catch (Exception exception) {
            return Map.of("unavailable", true);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("career planning event serialization failed", exception);
        }
    }
}
