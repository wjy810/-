package com.jobproof.modules.careerplanning.application;

import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.domain.TaskTypes;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jobproof.worker.in-process", havingValue = "true", matchIfMissing = true)
public class CareerPlanningAsyncProcessor {

    private final TaskService tasks;
    private final CareerPlanningAsyncService async;

    public CareerPlanningAsyncProcessor(TaskService tasks, CareerPlanningAsyncService async) {
        this.tasks = tasks;
        this.async = async;
    }

    @PostConstruct
    public void recoverInterrupted() {
        tasks.recoverRunning(TaskTypes.CAREER_PLANNING_CANVAS);
        tasks.recoverRunning(TaskTypes.CAREER_PLANNING_INTERVIEW);
        tasks.recoverRunning(TaskTypes.CAREER_PLANNING_RECOMMENDATIONS);
        tasks.recoverRunning(TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL);
        tasks.recoverRunning(TaskTypes.CAREER_PLANNING_VALIDATION_BATCH);
    }

    @Scheduled(fixedDelayString = "${jobproof.worker.poll-ms:1000}")
    public void poll() {
        tasks.claimNext(TaskTypes.CAREER_PLANNING_INTERVIEW).ifPresent(async::processClaimedTask);
        tasks.claimNext(TaskTypes.CAREER_PLANNING_RECOMMENDATIONS).ifPresent(async::processClaimedTask);
        tasks.claimNext(TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL).ifPresent(async::processClaimedTask);
        tasks.claimNext(TaskTypes.CAREER_PLANNING_CANVAS).ifPresent(async::processClaimedTask);
        tasks.claimNext(TaskTypes.CAREER_PLANNING_VALIDATION_BATCH).ifPresent(async::processClaimedTask);
    }
}
