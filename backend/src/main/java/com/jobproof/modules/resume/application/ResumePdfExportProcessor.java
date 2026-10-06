package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumePdfRenderer;
import com.jobproof.modules.resume.domain.ResumePdfExportMode;
import com.jobproof.modules.resume.domain.ResumeTaskTypes;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResumePdfExportProcessor {

    private static final Logger log = LoggerFactory.getLogger(ResumePdfExportProcessor.class);

    private final TaskService taskService;
    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;
    private final ResumeHtmlPdfExporter htmlExporter;

    public ResumePdfExportProcessor(TaskService taskService, ResumeService resumeService, ObjectMapper objectMapper,
            ResumeHtmlPdfExporter htmlExporter) {
        this.taskService = taskService;
        this.resumeService = resumeService;
        this.objectMapper = objectMapper;
        this.htmlExporter = htmlExporter;
    }

    public void processDue() {
        taskService.claimNext(ResumeTaskTypes.RESUME_PDF_EXPORT).ifPresent(this::run);
    }

    private void run(AsyncTaskEntity task) {
        try {
            JsonNode payload = objectMapper.readTree(task.getPayloadJson());
            String rendererVersion = payload.path("rendererVersion").asText();
            if (!ResumePdfRenderer.VERSION.equals(rendererVersion)) {
                taskService.markFailed(task.getId(), "PDF 渲染器版本已过期，请重新发起导出");
                return;
            }
            // Prepare and persist run in their own transactions; rendering holds none (docs/phase2/03 §5.4).
            resumeService.preparePdfExport(
                    task.getId(),
                    task.getAccountId(),
                    payload.path("resumeVersionId").asText(),
                    rendererVersion,
                    ResumePdfExportMode.parse(payload.path("exportMode").asText(null)))
                    .ifPresent(htmlExporter::export);
        } catch (Exception e) {
            log.warn("resume pdf export failed taskId={}", task.getId(), e);
            taskService.markFailed(task.getId(), "PDF 导出失败，可手动重试");
        }
    }
}
