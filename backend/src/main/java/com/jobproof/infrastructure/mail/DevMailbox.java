package com.jobproof.infrastructure.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DevMailbox {

    private static final Logger log = LoggerFactory.getLogger(DevMailbox.class);
    private final List<DevMailMessage> messages = new CopyOnWriteArrayList<>();

    public void deliver(DevMailMessage message) {
        messages.add(message);
        log.info("dev mailbox accepted message purpose={}", message.purpose());
    }

    public Optional<DevMailMessage> lastTo(String email) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            DevMailMessage message = messages.get(i);
            if (message.to().equalsIgnoreCase(email)) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }

    public List<DevMailMessage> all() {
        return new ArrayList<>(messages);
    }
}
