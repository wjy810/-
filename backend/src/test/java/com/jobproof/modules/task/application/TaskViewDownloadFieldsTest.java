package com.jobproof.modules.task.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobproof.modules.task.infra.AsyncTaskEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaskViewDownloadFieldsTest {

    @Test
    void succeededPayloadExposesFileIdAndTimedDownloadPath() {
        AsyncTaskEntity entity = sample("SUCCEEDED", "{\"resumeVersionId\":\"ver-1\",\"fileId\":\"file-9\"}");
        TaskView view = TaskView.from(entity);
        assertEquals("file-9", view.fileId());
        assertTrue(view.downloadAvailable());
        assertEquals("/api/v1/files/file-9/download", view.downloadUrl());
    }

    @Test
    void pendingOrMissingFileIdDoesNotPretendDownloadable() {
        TaskView pending = TaskView.from(sample("PENDING", "{\"resumeVersionId\":\"ver-1\"}"));
        assertNull(pending.fileId());
        assertFalse(pending.downloadAvailable());
        assertNull(pending.downloadUrl());

        TaskView succeededNoFile = TaskView.from(sample("SUCCEEDED", "{\"resumeVersionId\":\"ver-1\"}"));
        assertNull(succeededNoFile.fileId());
        assertFalse(succeededNoFile.downloadAvailable());

        TaskView broken = TaskView.from(sample("SUCCEEDED", "{not-json"));
        assertNull(broken.fileId());
        assertFalse(broken.downloadAvailable());
    }

    private static AsyncTaskEntity sample(String status, String payloadJson) {
        Instant now = Instant.parse("2026-08-18T00:00:00Z");
        AsyncTaskEntity entity = new AsyncTaskEntity();
        entity.setId("task-1");
        entity.setAccountId("acc-1");
        entity.setTaskType("RESUME_PDF_EXPORT");
        entity.setStatus(status);
        entity.setInputVersion("v1");
        entity.setPayloadJson(payloadJson);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
