package com.jobproof.modules.changelog.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.shared.event.EventTypes;
import com.jobproof.shared.event.OutboxEventHandler;
import org.springframework.stereotype.Component;

@Component
public class ChangelogPublishedHandler implements OutboxEventHandler {
    private final ChangelogDistributionService distribution;

    public ChangelogPublishedHandler(ChangelogDistributionService distribution) {
        this.distribution = distribution;
    }

    @Override
    public boolean supports(String eventType) {
        return EventTypes.CHANGELOG_PUBLISHED.equals(eventType);
    }

    @Override
    public void handle(String eventType, JsonNode payload) {
        distribution.enqueue(payload.path("releaseId").asText());
    }
}
