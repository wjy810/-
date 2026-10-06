package com.jobproof.modules.datarights.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs due account exports and deletions. Each step is isolated so one failure does not starve the other. */
@Component
@ConditionalOnProperty(name = "jobproof.worker.in-process", havingValue = "true", matchIfMissing = true)
public class DataRightsWorker {

    private static final Logger log = LoggerFactory.getLogger(DataRightsWorker.class);

    private final ExportJobProcessor exportJobProcessor;
    private final DeletionOrchestrator deletionOrchestrator;

    public DataRightsWorker(ExportJobProcessor exportJobProcessor, DeletionOrchestrator deletionOrchestrator) {
        this.exportJobProcessor = exportJobProcessor;
        this.deletionOrchestrator = deletionOrchestrator;
    }

    @Scheduled(fixedDelayString = "${jobproof.worker.poll-ms:1000}")
    public void tick() {
        try {
            exportJobProcessor.processDue();
        } catch (RuntimeException e) {
            log.warn("export worker tick failed", e);
        }
        try {
            deletionOrchestrator.processDue();
        } catch (RuntimeException e) {
            log.warn("deletion worker tick failed", e);
        }
    }
}
