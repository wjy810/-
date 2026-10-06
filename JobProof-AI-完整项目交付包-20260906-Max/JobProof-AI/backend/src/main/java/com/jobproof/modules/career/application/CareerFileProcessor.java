package com.jobproof.modules.career.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.worker.in-process", havingValue = "true", matchIfMissing = true)
public class CareerFileProcessor {
    private final TaskService tasks;
    private final CareerFileService files;
    private final ObjectMapper mapper;

    public CareerFileProcessor(TaskService tasks, CareerFileService files, ObjectMapper mapper) {
        this.tasks = tasks;
        this.files = files;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        tasks.recoverRunning(TaskTypes.CAREER_FILE_PROCESS);
    }

    @Scheduled(fixedDelayString = "${jobproof.worker.poll-ms:1000}")
    public void poll() {
        tasks.claimNext(TaskTypes.CAREER_FILE_PROCESS).ifPresent(this::run);
    }

    private void run(AsyncTaskEntity task) {
        String fileId;
        try {
            JsonNode payload = mapper.readTree(task.getPayloadJson());
            fileId = payload.path("careerFileId").asText();
            if (fileId.isBlank()) throw new IllegalArgumentException("careerFileId missing");
        } catch (Exception exception) {
            tasks.markFailed(task.getId(), "CAREER_FILE_TASK_PAYLOAD_INVALID");
            return;
        }
        try {
            CareerFileService.FileView file = files.processTask(task.getAccountId(), fileId);
            tasks.markSucceeded(task.getId(), file.sha256(), json(fileId));
        } catch (CareerFileService.ProcessingException exception) {
            if (exception.retryable() && task.getAttemptCount() < 2) tasks.requeuePending(task.getId());
            else tasks.markFailed(task.getId(), exception.getMessage());
        } catch (RuntimeException exception) {
            if (task.getAttemptCount() < 2) tasks.requeuePending(task.getId());
            else tasks.markFailed(task.getId(), "CAREER_FILE_PROCESSING_FAILED");
        }
    }

    private String json(String fileId) {
        try { return mapper.writeValueAsString(java.util.Map.of("careerFileId", fileId)); }
        catch (Exception exception) { return "{}"; }
    }
}
