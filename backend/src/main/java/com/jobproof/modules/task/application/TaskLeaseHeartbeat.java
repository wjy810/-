package com.jobproof.modules.task.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps this process's task leases alive while it runs them; a crashed process simply stops renewing. */
@Component
@ConditionalOnProperty(name = "jobproof.worker.in-process", havingValue = "true", matchIfMissing = true)
public class TaskLeaseHeartbeat {
    private static final Logger log = LoggerFactory.getLogger(TaskLeaseHeartbeat.class);

    private final TaskService tasks;

    public TaskLeaseHeartbeat(TaskService tasks) {
        this.tasks = tasks;
    }

    @Scheduled(fixedDelayString = "${jobproof.worker.lease-heartbeat-ms:30000}")
    public void renew() {
        try {
            tasks.renewLeases();
        } catch (RuntimeException exception) {
            log.warn("task lease heartbeat failed", exception);
        }
    }
}
