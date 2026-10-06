package com.jobproof.modules.careerplanning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class CareerPlanningSseService {

    private static final int MAX_REPLAY_EVENTS = 500;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    // Event retention must use the same clock as CareerPlanningEventService writes.
    private final ClockPort clock;
    private final Map<String, CopyOnWriteArrayList<Subscription>> subscribers = new ConcurrentHashMap<>();

    public CareerPlanningSseService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    public SseEmitter subscribe(String accountId, String sessionId, long afterSequence) {
        Integer owned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM career_planning_sessions WHERE id=? AND account_id=?",
                Integer.class, sessionId, accountId);
        if (owned == null || owned == 0) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能订阅他人的职业规划会话");
        }
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(emitter, afterSequence,
                event -> send(emitter, event));
        CopyOnWriteArrayList<Subscription> list = subscribers.computeIfAbsent(
                sessionId, ignored -> new CopyOnWriteArrayList<>());
        list.add(subscription);
        Runnable cleanup = () -> {
            list.remove(subscription);
            if (list.isEmpty()) subscribers.remove(sessionId, list);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            subscription.finishReplay(replay(accountId, sessionId, afterSequence));
            emitter.send(SseEmitter.event().name("heartbeat").data(Map.of("at", clock.now().toString())));
        } catch (IOException | RuntimeException exception) {
            cleanup.run();
        }
        return emitter;
    }

    public List<Event> replay(String accountId, String sessionId, long afterSequence) {
        return jdbc.query("""
                SELECT session_id,sequence_no,event_type,payload_json,created_at
                FROM career_planning_stream_events
                WHERE account_id=? AND session_id=? AND sequence_no>? AND expires_at>?
                ORDER BY sequence_no ASC LIMIT ?
                """, (rs, row) -> new Event(rs.getString("session_id"), rs.getLong("sequence_no"),
                        rs.getString("event_type"), read(rs.getString("payload_json")),
                        rs.getTimestamp("created_at").toInstant()),
                accountId, sessionId, Math.max(0, afterSequence), clock.now(), MAX_REPLAY_EVENTS);
    }

    public void publish(Event event) {
        CopyOnWriteArrayList<Subscription> list = subscribers.get(event.sessionId());
        if (list == null) return;
        for (Subscription subscription : list) {
            try {
                subscription.publish(event);
            } catch (IOException | RuntimeException exception) {
                list.remove(subscription);
            }
        }
        if (list.isEmpty()) subscribers.remove(event.sessionId(), list);
    }

    @Scheduled(fixedDelayString = "${jobproof.career-planning.sse-heartbeat-ms:15000}")
    void heartbeat() {
        subscribers.forEach((sessionId, list) -> {
            for (Subscription subscription : list) {
                try {
                    subscription.emitter().send(SseEmitter.event().name("heartbeat")
                            .data(Map.of("at", clock.now().toString())));
                } catch (IOException | RuntimeException exception) {
                    list.remove(subscription);
                }
            }
            if (list.isEmpty()) subscribers.remove(sessionId, list);
        });
    }

    @Scheduled(cron = "${jobproof.career-planning.sse-cleanup-cron:0 17 3 * * *}")
    void cleanupExpired() {
        jdbc.update("DELETE FROM career_planning_stream_events WHERE expires_at<?", clock.now());
    }

    private void send(SseEmitter emitter, Event event) throws IOException {
        emitter.send(SseEmitter.event().id(String.valueOf(event.sequence()))
                .name(event.type()).data(event.payload()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String value) {
        try {
            return mapper.readValue(value, Map.class);
        } catch (Exception exception) {
            return Map.of("unavailable", true);
        }
    }

    public record Event(String sessionId, long sequence, String type,
            Map<String, Object> payload, Instant createdAt) {}

    @FunctionalInterface
    interface EventSender {
        void send(Event event) throws IOException;
    }

    /** Buffers live events until replay is complete, then de-duplicates by sequence. */
    static final class Subscription {
        private final SseEmitter emitter;
        private final EventSender sender;
        private final TreeMap<Long, Event> pending = new TreeMap<>();
        private long lastSequence;
        private boolean replaying = true;

        Subscription(SseEmitter emitter, long afterSequence, EventSender sender) {
            this.emitter = emitter;
            this.lastSequence = Math.max(0, afterSequence);
            this.sender = sender;
        }

        synchronized void publish(Event event) throws IOException {
            if (replaying) {
                if (event.sequence() > lastSequence) pending.put(event.sequence(), event);
                return;
            }
            deliver(event);
        }

        synchronized void finishReplay(List<Event> replay) throws IOException {
            for (Event event : replay) deliver(event);
            replaying = false;
            for (Event event : pending.values()) deliver(event);
            pending.clear();
        }

        private void deliver(Event event) throws IOException {
            if (event.sequence() <= lastSequence) return;
            sender.send(event);
            lastSequence = event.sequence();
        }

        SseEmitter emitter() {
            return emitter;
        }
    }
}
