package com.jobproof.modules.membership.application;

import com.jobproof.shared.time.ClockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class MembershipReminderWorker {
    private static final Logger log = LoggerFactory.getLogger(MembershipReminderWorker.class);
    private final MembershipReminders reminders;
    private final ClockPort clock;

    public MembershipReminderWorker(MembershipReminders reminders, ClockPort clock) {
        this.reminders = reminders;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${jobproof.reminders.initial-delay-ms:60000}", fixedDelayString = "${jobproof.reminders.poll-ms:300000}")
    public void tick() {
        try {
            int sent = reminders.sendDue(clock.now());
            if (sent > 0) log.info("membership expiry notices sent count={}", sent);
        } catch (RuntimeException exception) {
            log.warn("membership expiry sweep failed", exception);
        }
    }
}
