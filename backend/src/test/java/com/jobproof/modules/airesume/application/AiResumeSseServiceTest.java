package com.jobproof.modules.airesume.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AiResumeSseServiceTest {

    @Test
    void subscriptionStaysOpenUntilTheClientDisconnects() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:sse-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE ai_resume_conversations (id VARCHAR(64), account_id VARCHAR(64))");
        jdbc.execute("""
                CREATE TABLE ai_resume_stream_events (
                    account_id VARCHAR(64),
                    conversation_id VARCHAR(64),
                    sequence_no BIGINT,
                    event_type VARCHAR(64),
                    payload_json CLOB,
                    created_at TIMESTAMP WITH TIME ZONE,
                    expires_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbc.update("INSERT INTO ai_resume_conversations (id, account_id) VALUES (?, ?)",
                "conversation-1", "account-1");
        AiResumeSseService service = new AiResumeSseService(jdbc, new ObjectMapper());

        SseEmitter emitter = service.subscribe("account-1", "conversation-1", 0);

        assertEquals(0L, emitter.getTimeout());
        emitter.complete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void disconnectedHeartbeatCompletesNormallyWithoutErrorDispatch() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:sse-disconnect-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        AiResumeSseService service = new AiResumeSseService(jdbc, new ObjectMapper());
        DisconnectingEmitter emitter = new DisconnectingEmitter();
        CopyOnWriteArrayList<SseEmitter> list = new CopyOnWriteArrayList<>();
        list.add(emitter);
        Field field = AiResumeSseService.class.getDeclaredField("subscribers");
        field.setAccessible(true);
        Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers =
                (Map<String, CopyOnWriteArrayList<SseEmitter>>) field.get(service);
        subscribers.put("conversation-1", list);

        service.heartbeat();

        assertTrue(list.isEmpty());
        assertTrue(emitter.completed);
        assertFalse(emitter.completedWithError);
    }

    @Test
    @SuppressWarnings("unchecked")
    void disconnectedPublishDoesNotFailWhenEmitterCanNoLongerComplete() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:sse-publish-disconnect-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        AiResumeSseService service = new AiResumeSseService(jdbc, new ObjectMapper());
        CopyOnWriteArrayList<SseEmitter> list = new CopyOnWriteArrayList<>();
        list.add(new InvalidCompletionEmitter());
        ReceivingEmitter active = new ReceivingEmitter();
        list.add(active);
        Field field = AiResumeSseService.class.getDeclaredField("subscribers");
        field.setAccessible(true);
        Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers =
                (Map<String, CopyOnWriteArrayList<SseEmitter>>) field.get(service);
        subscribers.put("conversation-1", list);

        AiResumeSseService.Event event = new AiResumeSseService.Event(
                "conversation-1", 1, "pdf.export.started", Map.of("taskId", "task-1"),
                java.time.Instant.parse("2026-09-05T00:00:00Z"));

        assertDoesNotThrow(() -> service.publish(event));
        assertEquals(1, list.size());
        assertEquals(1, active.received);
        list.add(0, new InvalidCompletionEmitter());
        assertDoesNotThrow(service::heartbeat);
        assertEquals(1, list.size());
        assertEquals(2, active.received);
    }

    private static final class DisconnectingEmitter extends SseEmitter {
        private boolean completed;
        private boolean completedWithError;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("client disconnected");
        }

        @Override
        public synchronized void complete() {
            completed = true;
        }

        @Override
        public synchronized void completeWithError(Throwable exception) {
            completedWithError = true;
        }
    }

    private static final class InvalidCompletionEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("client disconnected");
        }

        @Override
        public synchronized void complete() {
            throw new IllegalStateException("async context already failed");
        }
    }

    private static final class ReceivingEmitter extends SseEmitter {
        private int received;

        @Override
        public void send(SseEventBuilder builder) {
            received++;
        }
    }
}
