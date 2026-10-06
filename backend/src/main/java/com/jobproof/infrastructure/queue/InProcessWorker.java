package com.jobproof.infrastructure.queue;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.infrastructure.persistence.OutboxEventEntity;
import com.jobproof.infrastructure.persistence.OutboxEventJpaRepository;
import com.jobproof.modules.datarights.application.DeletionOrchestrator;
import com.jobproof.modules.datarights.application.ExportJobProcessor;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.shared.event.EventTypes;
import com.jobproof.shared.event.OutboxEventHandler;
import com.jobproof.shared.time.ClockPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "jobproof.worker.in-process", havingValue = "true", matchIfMissing = true)
public class InProcessWorker {

    private static final Logger log = LoggerFactory.getLogger(InProcessWorker.class);

    private final ExportJobProcessor exportJobProcessor;
    private final DeletionOrchestrator deletionOrchestrator;
    private final OutboxEventJpaRepository outbox;
    private final NotificationService notificationService;
    private final ClockPort clock;
    private final ObjectMapper objectMapper;
    private final List<OutboxEventHandler> outboxHandlers;

    public InProcessWorker(
            ExportJobProcessor exportJobProcessor,
            DeletionOrchestrator deletionOrchestrator,
            OutboxEventJpaRepository outbox,
            NotificationService notificationService,
            ClockPort clock,
            ObjectMapper objectMapper,
            List<OutboxEventHandler> outboxHandlers,
            JobProofProperties ignored) {
        this.exportJobProcessor = exportJobProcessor;
        this.deletionOrchestrator = deletionOrchestrator;
        this.outbox = outbox;
        this.notificationService = notificationService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.outboxHandlers = outboxHandlers;
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
        try {
            publishOutbox();
        } catch (RuntimeException e) {
            log.warn("outbox publish failed", e);
        }
    }

    @Transactional
    public void publishOutbox() {
        List<OutboxEventEntity> batch = outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 20));
        for (OutboxEventEntity event : batch) {
            consume(event);
            event.setPublishedAt(clock.now());
            outbox.save(event);
        }
    }

    private void consume(OutboxEventEntity event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayloadJson());
            if (EventTypes.TASK_COMPLETED.equals(event.getEventType()) || EventTypes.TASK_FAILED.equals(event.getEventType())) {
                String accountId = payload.path("accountId").asText();
                String taskId = payload.path("taskId").asText();
                String status = payload.path("status").asText();
                NotificationType type = EventTypes.TASK_FAILED.equals(event.getEventType())
                        ? NotificationType.TASK_FAILED
                        : NotificationType.TASK_COMPLETED;
                notificationService.request(accountId, type, taskId + ":" + status, "任务状态更新", "任务状态已更新，不含敏感正文。");
            }
            for (OutboxEventHandler handler : outboxHandlers) {
                if (handler.supports(event.getEventType())) {
                    handler.handle(event.getEventType(), payload);
                }
            }
        } catch (Exception e) {
            log.warn("outbox consumer failed, business unchanged eventId={}", event.getId(), e);
        }
    }
}
