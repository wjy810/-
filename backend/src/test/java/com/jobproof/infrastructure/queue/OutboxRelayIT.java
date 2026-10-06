package com.jobproof.infrastructure.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.infrastructure.persistence.OutboxEventEntity;
import com.jobproof.infrastructure.persistence.OutboxEventJpaRepository;
import com.jobproof.shared.event.OutboxEventHandler;
import com.jobproof.shared.time.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "jobproof.worker.in-process=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxRelayIT {

    static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.parse("2026-10-01T08:00:00Z"));

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ClockPort testClock() {
            return NOW::get;
        }

        @Bean
        ScriptedHandler scriptedHandler(JdbcTemplate jdbc) {
            return new ScriptedHandler(jdbc);
        }
    }

    /** Handles {@code Test*} events; fails the first N deliveries of an event type after writing a marker row. */
    static class ScriptedHandler implements OutboxEventHandler {
        final Map<String, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        private final JdbcTemplate jdbc;

        ScriptedHandler(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public boolean supports(String eventType) {
            return eventType.startsWith("Test");
        }

        @Override
        public void handle(String eventType, JsonNode payload) {
            deliveries.computeIfAbsent(eventType, key -> new AtomicInteger()).incrementAndGet();
            // A side effect in the delivery transaction; it must roll back when the handler fails.
            jdbc.update("INSERT INTO outbox_relay_marks (event_type) VALUES (?)", eventType);
            AtomicInteger left = failuresLeft.get(eventType);
            if (left != null && left.getAndDecrement() > 0) {
                throw new IllegalStateException("scripted failure for " + eventType);
            }
        }

        int deliveries(String type) {
            AtomicInteger count = deliveries.get(type);
            return count == null ? 0 : count.get();
        }
    }

    @Autowired OutboxService outboxService;
    @Autowired OutboxRelay relay;
    @Autowired OutboxEventJpaRepository outbox;
    @Autowired ScriptedHandler handler;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS outbox_relay_marks (event_type VARCHAR(64) NOT NULL)");
        jdbc.update("UPDATE outbox_events SET status='PUBLISHED', published_at=CURRENT_TIMESTAMP WHERE published_at IS NULL");
    }

    @Test
    void deliversOnceAndMarksPublished() {
        String type = type();
        outboxService.enqueue(type, Map.of("k", "v"));

        assertThat(relay.relayDue()).isEqualTo(1);
        assertThat(relay.relayDue()).isZero();

        OutboxEventEntity event = only(type);
        assertThat(event.getStatus()).isEqualTo(OutboxRelay.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(NOW.get());
        assertThat(handler.deliveries(type)).isEqualTo(1);
        assertThat(marks(type)).isEqualTo(1);
    }

    @Test
    void failureRollsBackSideEffectsAndRetriesWithBackoff() {
        String type = type();
        handler.failuresLeft.put(type, new AtomicInteger(1));
        outboxService.enqueue(type, Map.of());

        assertThat(relay.relayDue()).isZero();
        OutboxEventEntity failed = only(type);
        assertThat(failed.getStatus()).isEqualTo(OutboxRelay.RETRY);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getNextAttemptAt()).isEqualTo(NOW.get().plusSeconds(30));
        assertThat(failed.getLastError()).contains("IllegalStateException").contains("scripted failure");
        assertThat(marks(type)).as("handler side effect rolled back").isZero();

        assertThat(relay.relayDue()).as("not due before the backoff elapses").isZero();
        assertThat(handler.deliveries(type)).isEqualTo(1);

        advance(Duration.ofSeconds(31));
        assertThat(relay.relayDue()).isEqualTo(1);
        OutboxEventEntity published = only(type);
        assertThat(published.getStatus()).isEqualTo(OutboxRelay.PUBLISHED);
        assertThat(published.getLastError()).isNull();
        assertThat(marks(type)).isEqualTo(1);
    }

    @Test
    void oneFailingEventDoesNotBlockTheRestOfTheBatch() {
        String bad = type();
        String good = type();
        handler.failuresLeft.put(bad, new AtomicInteger(Integer.MAX_VALUE));
        outboxService.enqueue(bad, Map.of());
        outboxService.enqueue(good, Map.of());

        assertThat(relay.relayDue()).isEqualTo(1);
        assertThat(only(bad).getStatus()).isEqualTo(OutboxRelay.RETRY);
        assertThat(only(good).getStatus()).isEqualTo(OutboxRelay.PUBLISHED);
    }

    @Test
    void goesDeadAfterMaxAttempts() {
        String type = type();
        handler.failuresLeft.put(type, new AtomicInteger(Integer.MAX_VALUE));
        outboxService.enqueue(type, Map.of());

        for (int attempt = 1; attempt <= OutboxRelay.MAX_ATTEMPTS; attempt++) {
            relay.relayDue();
            advance(Duration.ofHours(2));
        }
        OutboxEventEntity dead = only(type);
        assertThat(dead.getStatus()).isEqualTo(OutboxRelay.DEAD);
        assertThat(dead.getAttempts()).isEqualTo(OutboxRelay.MAX_ATTEMPTS);
        assertThat(dead.getPublishedAt()).isNull();

        relay.relayDue();
        assertThat(handler.deliveries(type)).as("dead events are never retried").isEqualTo(OutboxRelay.MAX_ATTEMPTS);
    }

    @Test
    void claimIsExclusiveAndAnExpiredLeaseIsRedelivered() {
        String type = type();
        outboxService.enqueue(type, Map.of());
        String id = only(type).getId();
        List<String> statuses = List.of(OutboxRelay.PENDING, OutboxRelay.RETRY, OutboxRelay.PROCESSING);
        Instant now = NOW.get();

        // Another relay instance claims the event and then dies without finishing.
        assertThat(claim(id, statuses, now)).isEqualTo(1);
        assertThat(claim(id, statuses, now)).as("a second relay cannot claim a leased event").isZero();
        assertThat(relay.relayDue()).isZero();
        assertThat(handler.deliveries(type)).isZero();

        advance(OutboxRelay.LEASE.plusSeconds(1));
        assertThat(relay.relayDue()).isEqualTo(1);
        assertThat(only(type).getStatus()).isEqualTo(OutboxRelay.PUBLISHED);
    }

    @Test
    void backoffDoublesAndIsCapped() {
        assertThat(OutboxRelay.backoff(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(OutboxRelay.backoff(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(OutboxRelay.backoff(4)).isEqualTo(Duration.ofSeconds(240));
        assertThat(OutboxRelay.backoff(7)).isEqualTo(Duration.ofMinutes(32));
        assertThat(OutboxRelay.backoff(8)).isEqualTo(Duration.ofHours(1));
        assertThat(OutboxRelay.backoff(60)).isEqualTo(Duration.ofHours(1));
    }

    private int claim(String id, List<String> statuses, Instant now) {
        Integer claimed = new TransactionTemplate(transactionManager)
                .execute(status -> outbox.claim(id, statuses, now, now.plus(OutboxRelay.LEASE)));
        return claimed == null ? 0 : claimed;
    }

    private static String type() {
        return "Test" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private static void advance(Duration duration) {
        NOW.updateAndGet(now -> now.plus(duration));
    }

    private OutboxEventEntity only(String type) {
        List<OutboxEventEntity> events = outbox.findAll().stream().filter(event -> event.getEventType().equals(type)).toList();
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private int marks(String type) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_relay_marks WHERE event_type=?", Integer.class, type);
        return count == null ? 0 : count;
    }
}
