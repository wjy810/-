package com.jobproof.modules.resumeimport.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.worker.in-process", havingValue = "true", matchIfMissing = true)
public class ResumeImportProcessor {
    private final TaskService tasks;
    private final ResumeImportService imports;
    private final ObjectMapper mapper;

    public ResumeImportProcessor(TaskService tasks, ResumeImportService imports, ObjectMapper mapper) {
        this.tasks = tasks;
        this.imports = imports;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() { tasks.recoverRunning(TaskTypes.RESUME_IMPORT_PARSE); }

    @Scheduled(fixedDelayString = "${jobproof.worker.poll-ms:1000}")
    public void poll() { tasks.claimNext(TaskTypes.RESUME_IMPORT_PARSE).ifPresent(this::run); }

    private void run(AsyncTaskEntity task) {
        try {
            JsonNode payload = mapper.readTree(task.getPayloadJson());
            String id = payload.path("resumeImportId").asText("");
            if (id.isBlank()) throw new IllegalArgumentException("RESUME_IMPORT_TASK_PAYLOAD_INVALID");
            imports.process(task.getId(), task.getAccountId(), id);
        } catch (RuntimeException exception) {
            failIfRunning(task.getId(), "RESUME_IMPORT_PARSE_FAILED");
        } catch (Exception exception) {
            failIfRunning(task.getId(), "RESUME_IMPORT_TASK_PAYLOAD_INVALID");
        }
    }

    private void failIfRunning(String taskId, String reason) {
        if (tasks.stillRunning(taskId)) tasks.markFailed(taskId, reason);
    }
}
