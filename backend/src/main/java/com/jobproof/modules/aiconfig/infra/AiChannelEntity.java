package com.jobproof.modules.aiconfig.infra;

import com.jobproof.modules.aiconfig.domain.AiChannelScope;
import com.jobproof.modules.aiconfig.domain.AiChannelStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_channel")
public class AiChannelEntity {
  @Id private String id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AiChannelScope scope;

  @Column(name = "owner_account_id", length = 36)
  private String ownerAccountId;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "provider_code", nullable = false, length = 64)
  private String providerCode;

  @Column(name = "selected_model", length = 128)
  private String selectedModel;

  @Column(name = "routing_weight", nullable = false)
  private int routingWeight = 100;

  @Column(name = "config_version", nullable = false)
  private int configVersion;

  @Column(name = "applied_config_version", nullable = false)
  private int appliedConfigVersion;

  @Column(name = "pending_base_url", length = 1024)
  private String pendingBaseUrl;

  @Column(name = "pending_protocol", length = 32)
  private String pendingProtocol;

  @Column(name = "pending_api_key_ciphertext", columnDefinition = "TEXT")
  private String pendingApiKeyCiphertext;

  @Column(name = "pending_api_key_masked", length = 32)
  private String pendingApiKeyMasked;

  @Column(name = "pending_model", length = 128)
  private String pendingModel;

  @Column(name = "last_capability_id", length = 36)
  private String lastCapabilityId;

  @Column(name = "base_url", nullable = false, length = 1024)
  private String baseUrl;

  @Column(nullable = false, length = 32)
  private String protocol;

  @Column(name = "api_key_ciphertext", nullable = false, columnDefinition = "TEXT")
  private String apiKeyCiphertext;

  @Column(name = "api_key_masked", nullable = false, length = 32)
  private String apiKeyMasked;

  @Column(name = "channel_rate", nullable = false, precision = 19, scale = 8)
  private BigDecimal channelRate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AiChannelStatus status;

  @Version
  @Column(name = "version_no", nullable = false)
  private int versionNo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  public AiChannelEntity() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public AiChannelScope getScope() {
    return scope;
  }

  public void setScope(AiChannelScope scope) {
    this.scope = scope;
  }

  public String getOwnerAccountId() {
    return ownerAccountId;
  }

  public void setOwnerAccountId(String ownerAccountId) {
    this.ownerAccountId = ownerAccountId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getProviderCode() {
    return providerCode;
  }

  public void setProviderCode(String providerCode) {
    this.providerCode = providerCode;
  }

  public String getSelectedModel() {
    return selectedModel;
  }

  public void setSelectedModel(String selectedModel) {
    this.selectedModel = selectedModel;
  }

  public int getRoutingWeight() {
    return routingWeight;
  }

  public void setRoutingWeight(int value) {
    routingWeight = value;
  }

  public int getConfigVersion() {
    return configVersion;
  }

  public void setConfigVersion(int value) {
    configVersion = value;
  }

  public int getAppliedConfigVersion() {
    return appliedConfigVersion;
  }

  public void setAppliedConfigVersion(int value) {
    appliedConfigVersion = value;
  }

  public String getPendingBaseUrl() {
    return pendingBaseUrl;
  }

  public void setPendingBaseUrl(String value) {
    pendingBaseUrl = value;
  }

  public String getPendingProtocol() {
    return pendingProtocol;
  }

  public void setPendingProtocol(String value) {
    pendingProtocol = value;
  }

  public String getPendingApiKeyCiphertext() {
    return pendingApiKeyCiphertext;
  }

  public void setPendingApiKeyCiphertext(String value) {
    pendingApiKeyCiphertext = value;
  }

  public String getPendingApiKeyMasked() {
    return pendingApiKeyMasked;
  }

  public void setPendingApiKeyMasked(String value) {
    pendingApiKeyMasked = value;
  }

  public String getPendingModel() {
    return pendingModel;
  }

  public void setPendingModel(String value) {
    pendingModel = value;
  }

  public String getLastCapabilityId() {
    return lastCapabilityId;
  }

  public void setLastCapabilityId(String value) {
    lastCapabilityId = value;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public String getApiKeyCiphertext() {
    return apiKeyCiphertext;
  }

  public void setApiKeyCiphertext(String apiKeyCiphertext) {
    this.apiKeyCiphertext = apiKeyCiphertext;
  }

  public String getApiKeyMasked() {
    return apiKeyMasked;
  }

  public void setApiKeyMasked(String apiKeyMasked) {
    this.apiKeyMasked = apiKeyMasked;
  }

  public BigDecimal getChannelRate() {
    return channelRate;
  }

  public void setChannelRate(BigDecimal channelRate) {
    this.channelRate = channelRate;
  }

  public AiChannelStatus getStatus() {
    return status;
  }

  public void setStatus(AiChannelStatus status) {
    this.status = status;
  }

  public int getVersionNo() {
    return versionNo;
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

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(Instant archivedAt) {
    this.archivedAt = archivedAt;
  }
}
