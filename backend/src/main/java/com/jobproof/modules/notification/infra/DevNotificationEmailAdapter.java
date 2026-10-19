package com.jobproof.modules.notification.infra;

import com.jobproof.modules.notification.domain.NotificationEmailPort;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development and tests: messages are kept in memory and logged, nothing leaves the machine. */
@Component
@ConditionalOnProperty(name = "jobproof.notification.email.provider", havingValue = "dev")
public class DevNotificationEmailAdapter implements NotificationEmailPort {

    private static final Logger log = LoggerFactory.getLogger(DevNotificationEmailAdapter.class);
    private final List<NotificationEmail> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failNext;

    @Override public String providerCode() { return "dev"; }

    @Override public boolean available() { return true; }

    @Override
    public void send(NotificationEmail email) {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("simulated email failure");
        }
        sent.add(email);
        log.info("dev notification email subject={}", email.subject());
    }

    public List<NotificationEmail> sent() {
        return new ArrayList<>(sent);
    }

    public void failNext() {
        failNext = true;
    }
}
