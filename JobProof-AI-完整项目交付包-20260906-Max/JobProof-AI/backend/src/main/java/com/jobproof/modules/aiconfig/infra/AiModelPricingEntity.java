package com.jobproof.modules.aiconfig.infra;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_model_pricing")
public class AiModelPricingEntity {
    @Id private String id;
    @Column(name = "model_id", nullable = false) private String modelId;
    @Column(name = "fixed_price", nullable = false, precision = 19, scale = 8) private BigDecimal fixedPrice;
    @Column(name = "model_rate", nullable = false, precision = 19, scale = 8) private BigDecimal modelRate;
    @Column(name = "cache_rate", nullable = false, precision = 19, scale = 8) private BigDecimal cacheRate;
    @Column(name = "completion_rate", nullable = false, precision = 19, scale = 8) private BigDecimal completionRate;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "effective_from", nullable = false) private Instant effectiveFrom;
    @Column(name = "effective_to") private Instant effectiveTo;
    @Version @Column(name = "version_no", nullable = false) private int versionNo;
    @Column(name = "created_by") private String createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected AiModelPricingEntity() {}
    public String getId(){return id;} public void setId(String v){id=v;} public String getModelId(){return modelId;} public void setModelId(String v){modelId=v;} public BigDecimal getFixedPrice(){return fixedPrice;} public void setFixedPrice(BigDecimal v){fixedPrice=v;} public BigDecimal getModelRate(){return modelRate;} public void setModelRate(BigDecimal v){modelRate=v;} public BigDecimal getCacheRate(){return cacheRate;} public void setCacheRate(BigDecimal v){cacheRate=v;} public BigDecimal getCompletionRate(){return completionRate;} public void setCompletionRate(BigDecimal v){completionRate=v;} public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public Instant getEffectiveFrom(){return effectiveFrom;} public void setEffectiveFrom(Instant v){effectiveFrom=v;} public Instant getEffectiveTo(){return effectiveTo;} public void setEffectiveTo(Instant v){effectiveTo=v;} public int getVersionNo(){return versionNo;} public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
}