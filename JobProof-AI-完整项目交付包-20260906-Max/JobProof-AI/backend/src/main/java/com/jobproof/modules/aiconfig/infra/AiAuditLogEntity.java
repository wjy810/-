package com.jobproof.modules.aiconfig.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_audit_log")
public class AiAuditLogEntity {
    @Id
    private String id;
    @Column(name = "actor_account_id", length = 36)
    private String actorAccountId;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(name = "object_type", nullable = false, length = 64)
    private String objectType;
    @Column(name = "object_id", length = 64)
    private String objectId;
    @Column(nullable = false, length = 32)
    private String result;
    @Column(name = "trace_id", length = 128)
    private String traceId;
    @Column(nullable = false, length = 1024)
    private String summary;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiAuditLogEntity() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getActorAccountId() { return actorAccountId; }
    public void setActorAccountId(String actorAccountId) { this.actorAccountId = actorAccountId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
