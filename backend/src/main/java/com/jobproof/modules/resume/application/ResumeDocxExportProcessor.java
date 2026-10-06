package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeDocxRenderer;
import com.jobproof.modules.resume.domain.ResumeTaskTypes;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResumeDocxExportProcessor {

    private static final Logger log = LoggerFactory.getLogger(ResumeDocxExportProcessor.class);

    private final TaskService taskService;
    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;

    public ResumeDocxExportProcessor(TaskService taskService, ResumeService resumeService, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.resumeService = resumeService;
        this.objectMapper = objectMapper;
    }

    public void processDue() {
        taskService.claimNext(ResumeTaskTypes.RESUME_DOCX_EXPORT).ifPresent(this::run);
    }

    private void run(AsyncTaskEntity task) {
        try {
            JsonNode payload = objectMapper.readTree(task.getPayloadJson());
            String rendererVersion = payload.path("rendererVersion").asText();
            if (!ResumeDocxRenderer.VERSION.equals(rendererVersion)) {
                taskService.markFailed(task.getId(), "DOCX 渲染器版本已过期，请重新发起导出");
                return;
            }
            resumeService.completeDocxExport(
                    task.getId(), task.getAccountId(), payload.path("resumeVersionId").asText(), rendererVersion);
        } catch (Exception exception) {
            log.warn("resume docx export failed taskId={}", task.getId(), exception);
            taskService.markFailed(task.getId(), "DOCX 导出失败，未生成可下载文件");
        }
    }
}
