package com.jobproof.modules.datarights.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "deletion_requests")
public class DeletionRequestEntity {

    @Id
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false, length = 32)
    private String scope;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "impact_summary", nullable = false)
    private String impactSummary;

    @Column(name = "legal_exception_note", nullable = false)
    private String legalExceptionNote;

    @Column(name = "confirmation_ack", nullable = false)
    private boolean confirmationAck;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImpactSummary() {
        return impactSummary;
    }

    public void setImpactSummary(String impactSummary) {
        this.impactSummary = impactSummary;
    }

    public String getLegalExceptionNote() {
        return legalExceptionNote;
    }

    public void setLegalExceptionNote(String legalExceptionNote) {
        this.legalExceptionNote = legalExceptionNote;
    }

    public boolean isConfirmationAck() {
        return confirmationAck;
    }

    public void setConfirmationAck(boolean confirmationAck) {
        this.confirmationAck = confirmationAck;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
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
}
