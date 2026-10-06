package com.jobproof.modules.airesume.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.shared.error.AppException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiResumeSseService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public AiResumeSseService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public SseEmitter subscribe(String accountId, String conversationId, long afterSequence) {
        int owned = jdbc.queryForObject("SELECT COUNT(*) FROM ai_resume_conversations WHERE id=? AND account_id=?",
                Integer.class, conversationId, accountId);
        if (owned == 0) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能订阅他人的 AI 简历会话");
        }
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = subscribers.computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> {
            list.remove(emitter);
            if (list.isEmpty()) {
                subscribers.remove(conversationId, list);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            for (Event event : replay(accountId, conversationId, afterSequence)) {
                send(emitter, event);
            }
            emitter.send(SseEmitter.event().name("heartbeat").data(Map.of("at", Instant.now().toString())));
        } catch (IOException | IllegalStateException exception) {
            cleanup.run();
            completeQuietly(emitter);
        }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${jobproof.ai-resume.sse-heartbeat-ms:15000}")
    void heartbeat() {
        subscribers.forEach((conversationId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat")
                            .data(Map.of("at", Instant.now().toString())));
                } catch (IOException | IllegalStateException exception) {
                    list.remove(emitter);
                    completeQuietly(emitter);
                }
            }
            if (list.isEmpty()) {
                subscribers.remove(conversationId, list);
            }
        });
    }

    public void publish(Event event) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(event.conversationId());
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                send(emitter, event);
            } catch (IOException | IllegalStateException exception) {
                list.remove(emitter);
                completeQuietly(emitter);
            }
        }
        if (list.isEmpty()) {
            subscribers.remove(event.conversationId(), list);
        }
    }

    public List<Event> replay(String accountId, String conversationId, long afterSequence) {
        return jdbc.query("SELECT conversation_id,sequence_no,event_type,payload_json,created_at FROM ai_resume_stream_events WHERE account_id=? AND conversation_id=? AND sequence_no>? AND expires_at>? ORDER BY sequence_no ASC",
                (rs, n) -> new Event(rs.getString("conversation_id"), rs.getLong("sequence_no"),
                        rs.getString("event_type"), read(rs.getString("payload_json")),
                        rs.getTimestamp("created_at").toInstant()),
                accountId, conversationId, Math.max(0, afterSequence), Instant.now());
    }

    private void send(SseEmitter emitter, Event event) throws IOException {
        emitter.send(SseEmitter.event().id(String.valueOf(event.sequence())).name(event.type())
                .data(event.payload()));
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // The servlet container has already finalized this disconnected async request.
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String value) {
        try {
            return mapper.readValue(value, Map.class);
        } catch (Exception exception) {
            return Map.of("unavailable", true);
        }
    }

    public record Event(String conversationId, long sequence, String type, Map<String, Object> payload,
            Instant createdAt) {}
}
