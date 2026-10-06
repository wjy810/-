package com.jobproof.modules.task.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.task.domain.TaskStatus;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import com.jobproof.shared.error.AppException;
import java.time.Instant;

public record TaskView(
        String id,
        String accountId,
        String taskType,
        String status,
        String inputVersion,
        String resultVersion,
        String failureReason,
        int progressPercent,
        @JsonInclude(JsonInclude.Include.NON_NULL) String checkpointCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String fileId,
        boolean downloadAvailable,
        @JsonInclude(JsonInclude.Include.NON_NULL) String downloadUrl,
        /** Export outcome a user may see: page count and the ATS text check. Never the raw payload. */
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode result) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FILES_DOWNLOAD_PREFIX = "/api/v1/files/";

    public static TaskView from(AsyncTaskEntity entity) {
        String fileId = extractSucceededFileId(entity);
        boolean downloadAvailable = fileId != null;
        String downloadUrl = downloadAvailable ? FILES_DOWNLOAD_PREFIX + fileId + "/download" : null;
        return new TaskView(
                entity.getId(),
                entity.getAccountId(),
                entity.getTaskType(),
                entity.getStatus(),
                entity.getInputVersion(),
                entity.getResultVersion(),
                entity.getFailureReason(),
                entity.getProgressPercent(),
                entity.getCheckpointCode(),
                entity.getErrorCode(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                fileId,
                downloadAvailable,
                downloadUrl,
                extractResult(entity));
    }

    static JsonNode extractResult(AsyncTaskEntity entity) {
        if (entity == null || !TaskStatus.SUCCEEDED.name().equals(entity.getStatus())) return null;
        String payload = entity.getPayloadJson();
        if (payload == null || payload.isBlank()) return null;
        try {
            JsonNode node = JSON.readTree(payload);
            com.fasterxml.jackson.databind.node.ObjectNode result = JSON.createObjectNode();
            if (node.path("pageCount").isInt()) result.put("pageCount", node.path("pageCount").asInt());
            if (node.path("atsCheck").isObject()) result.set("atsCheck", node.path("atsCheck"));
            return result.isEmpty() ? null : result;
        } catch (Exception ignored) {
            return null;
        }
    }

    public TaskStatus statusEnum() {
        return TaskStatus.valueOf(status);
    }

    public void assertOwner(String accountId) {
        if (!this.accountId.equals(accountId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能访问他人的任务");
        }
    }

    static String extractSucceededFileId(AsyncTaskEntity entity) {
        if (entity == null || !TaskStatus.SUCCEEDED.name().equals(entity.getStatus())) {
            return null;
        }
        String payload = entity.getPayloadJson();
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(payload);
            JsonNode fileId = node.path("fileId");
            if (fileId.isMissingNode() || fileId.isNull()) {
                return null;
            }
            String value = fileId.asText();
            return value == null || value.isBlank() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }
}
