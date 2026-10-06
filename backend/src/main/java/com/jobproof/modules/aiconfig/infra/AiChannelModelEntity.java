package com.jobproof.modules.aiconfig.infra;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_channel_model")
public class AiChannelModelEntity {
    @Id private String id;
    @Column(name = "channel_id", nullable = false) private String channelId;
    @Column(name = "model_id", nullable = false) private String modelId;
    @Column(name = "provider_model_code", nullable = false, length = 128) private String providerModelCode;
    @Column(nullable = false, length = 32) private String status;
    @Column(nullable = false) private boolean hidden;
    @Column(nullable = false) private int priority;
    @Version @Column(name = "version_no", nullable = false) private int versionNo;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected AiChannelModelEntity() {}
    public String getId(){return id;} public void setId(String v){id=v;} public String getChannelId(){return channelId;} public void setChannelId(String v){channelId=v;} public String getModelId(){return modelId;} public void setModelId(String v){modelId=v;} public String getProviderModelCode(){return providerModelCode;} public void setProviderModelCode(String v){providerModelCode=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public boolean isHidden(){return hidden;} public void setHidden(boolean v){hidden=v;} public int getPriority(){return priority;} public void setPriority(int v){priority=v;} public int getVersionNo(){return versionNo;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;} public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}