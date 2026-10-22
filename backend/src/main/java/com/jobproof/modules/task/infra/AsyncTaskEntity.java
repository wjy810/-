package com.jobproof.modules.task.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "async_tasks")
public class AsyncTaskEntity {

    @Column(name = "ai_quote_id") private String aiQuoteId;
    @Column(name = "ai_request_path") private String aiRequestPath;
    @Column(name = "ai_payload_hash") private String aiPayloadHash;
    @Column(name = "ai_payment_confirmed") private boolean aiPaymentConfirmed;
    @Column(name = "ai_reservation_id") private String aiReservationId;
    @Column(name = "ai_personal_channel_id") private String aiPersonalChannelId;

    public com.jobproof.shared.ai.AiExecutionContext.State aiContext() {
        return new com.jobproof.shared.ai.AiExecutionContext.State(aiQuoteId, aiRequestPath, aiPayloadHash,
                aiPaymentConfirmed, accountId, aiReservationId, aiPersonalChannelId, id);
    }
    public void captureAiContext(com.jobproof.shared.ai.AiExecutionContext.State context) {
        if (context == null) return;
        aiQuoteId = context.quoteId(); aiRequestPath = context.path(); aiPayloadHash = context.payloadHash();
        aiPaymentConfirmed = context.paymentConfirmed(); aiReservationId = context.reservationId();
        aiPersonalChannelId = context.personalChannelId();
    }

    @Id
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "task_type", nullable = false, length = 64)
    private String taskType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "input_version", nullable = false, length = 64)
    private String inputVersion;

    @Column(name = "result_version", length = 64)
    private String resultVersion;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Column(name = "checkpoint_code", length = 64)
    private String checkpointCode;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Worker instance currently running the task; null while queued or after it finished. */
    @Column(name = "lease_owner", length = 96)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

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

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getInputVersion() {
        return inputVersion;
    }

    public void setInputVersion(String inputVersion) {
        this.inputVersion = inputVersion;
    }

    public String getResultVersion() {
        return resultVersion;
    }

    public void setResultVersion(String resultVersion) {
        this.resultVersion = resultVersion;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public String getCheckpointCode() {
        return checkpointCode;
    }

    public void setCheckpointCode(String checkpointCode) {
        this.checkpointCode = checkpointCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
}
