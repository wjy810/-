package com.jobproof.modules.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.shared.event.EventTypes;
import com.jobproof.shared.event.OutboxEventHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Turns task completion events into an in-app notification that says which task finished and links
 * to its result; deduplicated by {@code taskId:status}.
 */
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
        boolean failed = EventTypes.TASK_FAILED.equals(eventType);
        NotificationType type = failed ? NotificationType.TASK_FAILED : NotificationType.TASK_COMPLETED;
        Map<String, String> refs = refs(payload.path("refs"));
        // How the task ended when that changes what to say (e.g. a match that stopped to ask questions).
        refs.put("resultVersion", payload.path("resultVersion").asText(""));
        TaskNotificationCopy.of(payload.path("taskType").asText(), !failed, refs)
                .ifPresent(copy -> notifications.request(accountId, type, taskId + ":" + status,
                        copy.title(), copy.body(), copy.actionPath()));
    }

    private static Map<String, String> refs(JsonNode node) {
        Map<String, String> refs = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) refs.put(entry.getKey(), entry.getValue().asText());
            });
        }
        return refs;
    }
}
