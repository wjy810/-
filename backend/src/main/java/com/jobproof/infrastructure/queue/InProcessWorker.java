package com.jobproof.infrastructure.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox inside the API process. Module workers (exports, deletions, resume jobs…) schedule
 * themselves in their own modules, so infrastructure never depends on a module (rule R3).
 */
@Component
@ConditionalOnProperty(name = "jobproof.worker.in-process", havingValue = "true", matchIfMissing = true)
public class InProcessWorker {

    private static final Logger log = LoggerFactory.getLogger(InProcessWorker.class);

    private final OutboxRelay relay;

    public InProcessWorker(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${jobproof.worker.poll-ms:1000}")
    public void tick() {
        try {
            relay.relayDue();
        } catch (RuntimeException e) {
            log.warn("outbox relay pass failed", e);
        }
    }
}
