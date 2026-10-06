package com.jobproof.modules.careerplanning.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class CareerPlanningSseServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final ClockPort CLOCK = () -> NOW;

    @Test
    void persistedEventsUseMonotonicSequencesAndReplayAfterLastEventId() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = schema(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        CareerPlanningSseService sse = new CareerPlanningSseService(jdbc, mapper, CLOCK);
        CareerPlanningEventService events = new CareerPlanningEventService(
                jdbc, mapper, CLOCK, sse);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        tx.executeWithoutResult(status -> {
            events.append("account-1", "session-1", "profile.updated", Map.of("version", 1));
            events.append("account-1", "session-1", "canvas.updated", Map.of("version", 2));
            events.append("account-1", "session-1", "plan.updated", Map.of("version", 3));
            events.appendOnce("account-1", "session-1", "task.queued", "task-1:queued", Map.of("taskId", "task-1"));
            events.appendOnce("account-1", "session-1", "task.queued", "task-1:queued", Map.of("taskId", "task-1"));
        });

        List<CareerPlanningSseService.Event> replay = sse.replay("account-1", "session-1", 1);
        assertEquals(List.of(2L, 3L, 4L), replay.stream().map(CareerPlanningSseService.Event::sequence).toList());
        assertEquals(List.of("canvas.updated", "plan.updated", "task.queued"),
                replay.stream().map(CareerPlanningSseService.Event::type).toList());
        assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(*) FROM career_planning_stream_events WHERE session_id='session-1'",
                Integer.class));
    }

    @Test
    void subscriptionRejectsAnotherAccount() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = schema(dataSource);
        CareerPlanningSseService sse = new CareerPlanningSseService(jdbc, new ObjectMapper(), CLOCK);

        AppException exception = assertThrows(AppException.class,
                () -> sse.subscribe("account-2", "session-1", 0));

        assertEquals("OBJECT_FORBIDDEN", exception.reason());
    }

    @Test
    void liveEventsAreBufferedDuringReplayAndDeliveredOnceInSequenceOrder() throws Exception {
        List<Long> delivered = new ArrayList<>();
        CareerPlanningSseService.Subscription subscription = new CareerPlanningSseService.Subscription(
                new SseEmitter(), 0, event -> delivered.add(event.sequence()));
        Instant now = Instant.parse("2026-08-28T00:00:00Z");
        CareerPlanningSseService.Event first = event(1, now);
        CareerPlanningSseService.Event second = event(2, now);
        CareerPlanningSseService.Event third = event(3, now);

        subscription.publish(third);
        subscription.finishReplay(List.of(first, second, third));
        subscription.publish(second);
        subscription.publish(event(4, now));

        assertEquals(List.of(1L, 2L, 3L, 4L), delivered);
    }

    @Test
    @SuppressWarnings("unchecked")
    void disconnectedSubscriberCannotTurnCommittedBusinessRequestIntoFailure() throws Exception {
        CareerPlanningSseService sse = new CareerPlanningSseService(
                new JdbcTemplate(dataSource()), new ObjectMapper(), CLOCK);
        TrackingCompletionEmitter emitter = new TrackingCompletionEmitter();
        CareerPlanningSseService.Subscription subscription = new CareerPlanningSseService.Subscription(
                emitter, 0, event -> { throw new IOException("client disconnected"); });
        subscription.finishReplay(List.of());
        CopyOnWriteArrayList<CareerPlanningSseService.Subscription> list = new CopyOnWriteArrayList<>();
        list.add(subscription);
        Field field = CareerPlanningSseService.class.getDeclaredField("subscribers");
        field.setAccessible(true);
        Map<String, CopyOnWriteArrayList<CareerPlanningSseService.Subscription>> subscribers =
                (Map<String, CopyOnWriteArrayList<CareerPlanningSseService.Subscription>>) field.get(sse);
        subscribers.put("session-1", list);

        assertDoesNotThrow(() -> sse.publish(event(1, Instant.parse("2026-08-28T00:00:00Z"))));
        assertEquals(0, list.size());
        assertEquals(0, emitter.completionCalls);
    }

    @Test
    @SuppressWarnings("unchecked")
    void heartbeatDropsDisconnectedSubscriberWithoutCompletingFailedResponse() throws Exception {
        CareerPlanningSseService sse = new CareerPlanningSseService(
                new JdbcTemplate(dataSource()), new ObjectMapper(), CLOCK);
        TrackingCompletionEmitter emitter = new TrackingCompletionEmitter();
        CareerPlanningSseService.Subscription subscription = new CareerPlanningSseService.Subscription(
                emitter, 0, event -> { });
        subscription.finishReplay(List.of());
        CopyOnWriteArrayList<CareerPlanningSseService.Subscription> list = new CopyOnWriteArrayList<>();
        list.add(subscription);
        Field field = CareerPlanningSseService.class.getDeclaredField("subscribers");
        field.setAccessible(true);
        Map<String, CopyOnWriteArrayList<CareerPlanningSseService.Subscription>> subscribers =
                (Map<String, CopyOnWriteArrayList<CareerPlanningSseService.Subscription>>) field.get(sse);
        subscribers.put("session-1", list);

        assertDoesNotThrow(sse::heartbeat);
        assertEquals(0, list.size());
        assertEquals(0, emitter.completionCalls);
    }

    @Test
    void replayAndCleanupRespectTheSameInjectedExpiryClock() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = schema(dataSource);
        AtomicReference<Instant> now = new AtomicReference<>(NOW);
        ObjectMapper mapper = new ObjectMapper();
        CareerPlanningSseService sse = new CareerPlanningSseService(jdbc, mapper, now::get);
        CareerPlanningEventService events = new CareerPlanningEventService(jdbc, mapper, now::get, sse);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.executeWithoutResult(status -> events.append("account-1", "session-1", "profile.updated", Map.of("version", 1)));
        now.set(NOW.plusSeconds(7 * 24 * 60 * 60 - 1));
        assertEquals(1, sse.replay("account-1", "session-1", 0).size());
        now.set(NOW.plusSeconds(7 * 24 * 60 * 60));
        assertEquals(0, sse.replay("account-1", "session-1", 0).size());
        now.set(now.get().plusSeconds(1));
        sse.cleanupExpired();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM career_planning_stream_events", Integer.class));
    }

    private static CareerPlanningSseService.Event event(long sequence, Instant now) {
        return new CareerPlanningSseService.Event("session-1", sequence, "assistant.delta",
                Map.of("textDelta", "chunk-" + sequence), now);
    }

    private static final class TrackingCompletionEmitter extends SseEmitter {
        private int completionCalls;

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            throw new IOException("client disconnected");
        }

        @Override
        public synchronized void complete() {
            completionCalls++;
        }
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:career-sse-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static JdbcTemplate schema(DriverManagerDataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE career_planning_sessions (id VARCHAR(64) PRIMARY KEY, account_id VARCHAR(64))");
        jdbc.execute("""
                CREATE TABLE career_planning_stream_events (
                    id VARCHAR(64) PRIMARY KEY,
                    session_id VARCHAR(64),
                    account_id VARCHAR(64),
                    sequence_no BIGINT,
                    event_type VARCHAR(64),
                    payload_json CLOB,
                    created_at TIMESTAMP WITH TIME ZONE,
                    expires_at TIMESTAMP WITH TIME ZONE,
                    dedupe_key VARCHAR(160),
                    CONSTRAINT uk_career_event UNIQUE(session_id, sequence_no),
                    CONSTRAINT uk_career_event_dedupe UNIQUE(session_id, dedupe_key)
                )
                """);
        jdbc.update("INSERT INTO career_planning_sessions(id,account_id) VALUES('session-1','account-1')");
        return jdbc;
    }
}
