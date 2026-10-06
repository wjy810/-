package com.jobproof.modules.resume.application;

import com.jobproof.modules.resume.domain.ResumeTemplatePreviewRenderer;
import com.jobproof.modules.resume.domain.ResumeTemplatePreviewRenderer.RenderRequest;
import com.jobproof.modules.resume.domain.ResumeTemplatePreviewRenderer.RenderResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.templates.preview.enabled", havingValue = "true")
public class ResumeTemplatePreviewWorker {
    private static final Logger log = LoggerFactory.getLogger(ResumeTemplatePreviewWorker.class);
    private final ResumeTemplatePreviewService service;
    private final ResumeTemplatePreviewRenderer renderer;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean();

    public ResumeTemplatePreviewWorker(ResumeTemplatePreviewService service, ResumeTemplatePreviewRenderer renderer,
            @Value("${jobproof.templates.preview.batch-size:12}") int batchSize) {
        this.service = service;
        this.renderer = renderer;
        this.batchSize = Math.max(1, Math.min(batchSize, 32));
    }

    @Scheduled(initialDelayString = "${jobproof.templates.preview.initial-delay-ms:3000}",
            fixedDelayString = "${jobproof.templates.preview.poll-ms:1000}")
    public void processDue() {
        if (!running.compareAndSet(false, true)) return;
        try {
            List<RenderRequest> requests = service.claimNext(batchSize);
            if (requests.isEmpty()) return;
            Map<String, RenderResult> resultById = new HashMap<>();
            for (RenderResult result : renderer.render(requests)) resultById.put(result.catalogEntryId(), result);
            for (RenderRequest request : requests) service.complete(request, resultById.get(request.catalogEntryId()));
        } catch (RuntimeException exception) {
            log.error("Template preview batch failed", exception);
        } finally {
            running.set(false);
        }
    }
}
