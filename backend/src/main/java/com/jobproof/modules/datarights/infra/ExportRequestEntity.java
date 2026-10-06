package com.jobproof.modules.datarights.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "export_requests")
public class ExportRequestEntity {

    @Id
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(nullable = false, length = 32)
    private String scope;

    @Column(name = "download_token_hash", length = 64)
    private String downloadTokenHash;

    @Column(name = "download_expires_at")
    private Instant downloadExpiresAt;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getDownloadTokenHash() {
        return downloadTokenHash;
    }

    public void setDownloadTokenHash(String downloadTokenHash) {
        this.downloadTokenHash = downloadTokenHash;
    }

    public Instant getDownloadExpiresAt() {
        return downloadExpiresAt;
    }

    public void setDownloadExpiresAt(Instant downloadExpiresAt) {
        this.downloadExpiresAt = downloadExpiresAt;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
