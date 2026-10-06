package com.jobproof.modules.changelog.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChangelogWorker {
    private static final Logger log = LoggerFactory.getLogger(ChangelogWorker.class);
    private final ChangelogService changelog;
    private final ChangelogDistributionService distribution;

    public ChangelogWorker(ChangelogService changelog, ChangelogDistributionService distribution) {
        this.changelog = changelog;
        this.distribution = distribution;
    }

    @Scheduled(fixedDelayString = "${jobproof.worker.poll-ms:1000}")
    public void tick() {
        try {
            changelog.publishDue();
        } catch (RuntimeException exception) {
            log.warn("changelog schedule tick failed", exception);
        }
        try {
            distribution.processOneBatch();
        } catch (RuntimeException exception) {
            log.warn("changelog distribution tick failed", exception);
        }
    }
}
