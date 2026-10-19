package com.jobproof.modules.notification.application;

import com.jobproof.shared.time.ClockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.notification.email.worker-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEmailWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationEmailWorker.class);
    private final NotificationEmailOutbox outbox;
    private final ClockPort clock;

    public NotificationEmailWorker(NotificationEmailOutbox outbox, ClockPort clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${jobproof.notification.email.initial-delay-ms:30000}", fixedDelayString = "${jobproof.notification.email.poll-ms:15000}")
    public void tick() {
        try {
            int sent = outbox.sendDue(clock.now());
            if (sent > 0) log.info("notification emails sent count={}", sent);
        } catch (RuntimeException exception) {
            log.warn("notification email sweep failed", exception);
        }
    }
}
