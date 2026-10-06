package com.jobproof.modules.aiconfig.infra;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_channel_health")
public class AiChannelHealthEntity {
    @Id private String id;
    @Column(name = "channel_id", nullable = false) private String channelId;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "latency_ms") private Long latencyMs;
    @Column(name = "consecutive_failures", nullable = false) private int consecutiveFailures;
    @Column(name = "failure_code", length = 64) private String failureCode;
    @Column(name = "checked_at", nullable = false) private Instant checkedAt;
    @Version @Column(name = "version_no", nullable = false) private int versionNo;
    public AiChannelHealthEntity() {}
    public String getId(){return id;} public void setId(String v){id=v;} public String getChannelId(){return channelId;} public void setChannelId(String v){channelId=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public Long getLatencyMs(){return latencyMs;} public void setLatencyMs(Long v){latencyMs=v;} public int getConsecutiveFailures(){return consecutiveFailures;} public void setConsecutiveFailures(int v){consecutiveFailures=v;} public String getFailureCode(){return failureCode;} public void setFailureCode(String v){failureCode=v;} public Instant getCheckedAt(){return checkedAt;} public void setCheckedAt(Instant v){checkedAt=v;} public int getVersionNo(){return versionNo;}
}