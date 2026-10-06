package com.jobproof.modules.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.shared.event.EventTypes;
import com.jobproof.shared.event.OutboxEventHandler;
import org.springframework.stereotype.Component;

/** Turns task completion events into an in-app notification; deduplicated by {@code taskId:status}. */
@Component
public class TaskStatusNotificationHandler implements OutboxEventHandler {

    private final NotificationService notifications;

    public TaskStatusNotificationHandler(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public boolean supports(String eventType) {
        return EventTypes.TASK_COMPLETED.equals(eventType) || EventTypes.TASK_FAILED.equals(eventType);
    }

    @Override
    public void handle(String eventType, JsonNode payload) {
        String accountId = payload.path("accountId").asText();
        String taskId = payload.path("taskId").asText();
        String status = payload.path("status").asText();
        if (accountId.isBlank() || taskId.isBlank()) {
            throw new IllegalArgumentException("task event without accountId/taskId");
        }
        NotificationType type = EventTypes.TASK_FAILED.equals(eventType) ? NotificationType.TASK_FAILED : NotificationType.TASK_COMPLETED;
        notifications.request(accountId, type, taskId + ":" + status, "任务状态更新", "任务状态已更新，不含敏感正文。");
    }
}
