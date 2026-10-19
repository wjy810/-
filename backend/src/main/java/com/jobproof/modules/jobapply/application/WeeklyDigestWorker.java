package com.jobproof.modules.jobapply.application;

import com.jobproof.shared.time.ClockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class WeeklyDigestWorker {
    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestWorker.class);
    private final WeeklyDigest digest;
    private final ClockPort clock;

    public WeeklyDigestWorker(WeeklyDigest digest, ClockPort clock) {
        this.digest = digest;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${jobproof.reminders.initial-delay-ms:60000}", fixedDelayString = "${jobproof.reminders.poll-ms:300000}")
    public void tick() {
        try {
            // Each call settles a batch; keep going while accounts are left for this week.
            int settled;
            do {
                settled = digest.sendDue(clock.now());
                if (settled > 0) log.info("weekly digest accounts settled count={}", settled);
            } while (settled > 0);
        } catch (RuntimeException exception) {
            log.warn("weekly digest sweep failed", exception);
        }
    }
}
