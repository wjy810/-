package com.jobproof.modules.aiconfig.infra;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_model")
public class AiModelEntity {
    @Id private String id;
    @Column(name = "model_code", nullable = false, length = 128) private String modelCode;
    @Column(name = "display_name", nullable = false, length = 128) private String displayName;
    @Column(name = "provider_code", nullable = false, length = 64) private String providerCode;
    @Column(name = "model_type", nullable = false, length = 32) private String modelType;
    @Column(name = "billing_mode", nullable = false, length = 16) private String billingMode;
    @Column(nullable = false, length = 32) private String unit;
    @Column(nullable = false, length = 32) private String status;
    @Version @Column(name = "version_no", nullable = false) private int versionNo;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected AiModelEntity() {}
    public String getId(){return id;} public void setId(String v){id=v;} public String getModelCode(){return modelCode;} public void setModelCode(String v){modelCode=v;} public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;} public String getProviderCode(){return providerCode;} public void setProviderCode(String v){providerCode=v;} public String getModelType(){return modelType;} public void setModelType(String v){modelType=v;} public String getBillingMode(){return billingMode;} public void setBillingMode(String v){billingMode=v;} public String getUnit(){return unit;} public void setUnit(String v){unit=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public int getVersionNo(){return versionNo;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;} public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}