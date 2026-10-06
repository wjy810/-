package com.jobproof.modules.resume.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.worker.in-process", havingValue = "true", matchIfMissing = true)
public class ResumeWorker {

    private static final Logger log = LoggerFactory.getLogger(ResumeWorker.class);

    private final ResumePdfExportProcessor pdfExportProcessor;
    private final ResumeDocxExportProcessor docxExportProcessor;
    private final ResumeTemplateBatchImportService templateBatchImportService;

    public ResumeWorker(ResumePdfExportProcessor pdfExportProcessor,
            ResumeDocxExportProcessor docxExportProcessor,
            ResumeTemplateBatchImportService templateBatchImportService) {
        this.pdfExportProcessor = pdfExportProcessor;
        this.docxExportProcessor = docxExportProcessor;
        this.templateBatchImportService = templateBatchImportService;
    }

    @Scheduled(fixedDelayString = "${jobproof.worker.poll-ms:1000}")
    public void tick() {
        try {
            pdfExportProcessor.processDue();
            docxExportProcessor.processDue();
            templateBatchImportService.processDue();
        } catch (RuntimeException e) {
            log.warn("resume export or template import worker tick failed", e);
        }
    }
}
