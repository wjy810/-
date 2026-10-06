package com.jobproof.infrastructure.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.infrastructure.persistence.OutboxEventEntity;
import com.jobproof.infrastructure.persistence.OutboxEventJpaRepository;
import com.jobproof.shared.event.OutboxEventHandler;
import com.jobproof.shared.time.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers outbox events to {@link OutboxEventHandler}s (docs/03 §5.6).
 *
 * <ul>
 *   <li>Each event is claimed by a conditional update with a lease, so several relays never deliver it twice;
 *       a relay that dies mid-delivery leaves an expired lease that the next pass picks up.</li>
 *   <li>Handler side effects and the PUBLISHED mark commit in one transaction; a failing handler rolls both
 *       back, and the failure is recorded in a separate transaction.</li>
 *   <li>Failures back off exponentially ({@code 30s × 2^(attempts-1)}, capped at 1h); after
 *       {@value #MAX_ATTEMPTS} attempts the event goes DEAD and is left for an operator.</li>
 * </ul>
 */
@Component
public class OutboxRelay {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String RETRY = "RETRY";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String DEAD = "DEAD";

    static final int MAX_ATTEMPTS = 8;
    static final int BATCH_SIZE = 20;
    static final Duration LEASE = Duration.ofMinutes(5);
    static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    static final Duration MAX_BACKOFF = Duration.ofHours(1);
    private static final List<String> DELIVERABLE = List.of(PENDING, RETRY, PROCESSING);
    private static final int ERROR_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventJpaRepository outbox;
    private final List<OutboxEventHandler> handlers;
    private final ClockPort clock;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public OutboxRelay(OutboxEventJpaRepository outbox, List<OutboxEventHandler> handlers, ClockPort clock,
            ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.outbox = outbox;
        this.handlers = List.copyOf(handlers);
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Delivers one batch of due events. Returns how many were published. */
    public int relayDue() {
        Instant now = clock.now();
        List<String> due = outbox.findDueIds(DELIVERABLE, now, PageRequest.of(0, BATCH_SIZE));
        int published = 0;
        for (String id : due) {
            if (relay(id)) published++;
        }
        return published;
    }

    private boolean relay(String id) {
        Instant now = clock.now();
        Integer claimed = transaction.execute(status -> outbox.claim(id, DELIVERABLE, now, now.plus(LEASE)));
        if (claimed == null || claimed == 0) return false;
        try {
            transaction.executeWithoutResult(status -> deliver(id));
            return true;
        } catch (RuntimeException ex) {
            recordFailure(id, ex);
            return false;
        }
    }

    private void deliver(String id) {
        OutboxEventEntity event = outbox.findById(id).orElseThrow();
        JsonNode payload = readPayload(event);
        for (OutboxEventHandler handler : handlers) {
            if (handler.supports(event.getEventType())) {
                handler.handle(event.getEventType(), payload);
            }
        }
        event.setStatus(PUBLISHED);
        event.setPublishedAt(clock.now());
        event.setNextAttemptAt(null);
        event.setLastError(null);
        outbox.save(event);
    }

    private JsonNode readPayload(OutboxEventEntity event) {
        try {
            return objectMapper.readTree(event.getPayloadJson());
        } catch (Exception ex) {
            throw new IllegalStateException("unreadable outbox payload", ex);
        }
    }

    private void recordFailure(String id, RuntimeException failure) {
        try {
            transaction.executeWithoutResult(status -> outbox.findById(id).ifPresent(event -> {
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(describe(failure));
                if (attempts >= MAX_ATTEMPTS) {
                    event.setStatus(DEAD);
                    event.setNextAttemptAt(null);
                    log.error("outbox event dead after {} attempts eventId={} type={}", attempts, id, event.getEventType(), failure);
                } else {
                    Instant next = clock.now().plus(backoff(attempts));
                    event.setStatus(RETRY);
                    event.setNextAttemptAt(next);
                    log.warn("outbox delivery failed, retrying at {} attempt={} eventId={} type={}: {}",
                            next, attempts, id, event.getEventType(), event.getLastError());
                }
                outbox.save(event);
            }));
        } catch (RuntimeException ex) {
            // The lease expires on its own; the next pass retries without counting this attempt.
            log.error("could not record outbox failure eventId={}", id, ex);
        }
    }

    /** {@code 30s × 2^(attempts-1)}, capped at one hour. */
    static Duration backoff(int attempts) {
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        Duration delay = BASE_BACKOFF.multipliedBy(1L << exponent);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String text = root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
        return text.length() > ERROR_LIMIT ? text.substring(0, ERROR_LIMIT) : text;
    }
}
