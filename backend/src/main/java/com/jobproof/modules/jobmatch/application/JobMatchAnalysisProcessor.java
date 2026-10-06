package com.jobproof.modules.jobmatch.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.domain.TaskTypes;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobMatchAnalysisProcessor {
    private final TaskService tasks;
    private final JobMatchAnalysisService analysis;
    private final ObjectMapper mapper;

    public JobMatchAnalysisProcessor(TaskService tasks, JobMatchAnalysisService analysis, ObjectMapper mapper) {
        this.tasks = tasks;
        this.analysis = analysis;
        this.mapper = mapper;
    }

    @PostConstruct
    public void recoverInterrupted() { tasks.recoverRunning(TaskTypes.JOB_MATCH); }

    @Scheduled(fixedDelayString = "${jobproof.worker.poll-ms:1000}")
    public void poll() { tasks.claimNext(TaskTypes.JOB_MATCH).ifPresent(task -> run(task.getId(), task.getAccountId(), task.getPayloadJson())); }

    private void run(String taskId, String accountId, String payloadJson) {
        try {
            Map<String, Object> payload = mapper.readValue(payloadJson, new TypeReference<>() {});
            analysis.process(taskId, accountId, String.valueOf(payload.get("matchId")));
        } catch (RuntimeException exception) {
            tasks.markFailed(taskId, "JOB_MATCH_TASK_PAYLOAD_INVALID");
        } catch (Exception exception) {
            tasks.markFailed(taskId, "JOB_MATCH_TASK_PAYLOAD_INVALID");
        }
    }
}
