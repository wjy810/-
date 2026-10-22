package com.jobproof.modules.aiconfig.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aiconfig.domain.AiChannelScope;
import com.jobproof.modules.aiconfig.domain.AiChannelStatus;
import com.jobproof.modules.aiconfig.infra.*;
import com.jobproof.modules.aigateway.application.AiChannelInspector;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.CapabilityDetection;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel;
import com.jobproof.modules.aigateway.domain.AiProtocol;
import com.jobproof.modules.aigateway.security.ApiKeyCipher;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shared staged-configuration implementation for system and personal channels. */
@Service
public class AiAdminChannelService {
  private final AiChannelJpaRepository channels;
  private final AiChannelHealthJpaRepository health;
  private final AiChannelModelJpaRepository mappings;
  private final AiModelJpaRepository models;
  private final AiChannelInspector inspector;
  private final ApiKeyCipher cipher;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final ClockPort clock;
  private final AuditService audit;

  public AiAdminChannelService(
      AiChannelJpaRepository channels,
      AiChannelHealthJpaRepository health,
      AiChannelModelJpaRepository mappings,
      AiModelJpaRepository models,
      AiChannelInspector inspector,
      ApiKeyCipher cipher,
      JdbcTemplate jdbc,
      ObjectMapper mapper,
      ClockPort clock,
      AuditService audit) {
    this.channels = channels;
    this.health = health;
    this.mappings = mappings;
    this.models = models;
    this.inspector = inspector;
    this.cipher = cipher;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.clock = clock;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<SystemChannelView> list(CurrentAccount current) {
    assertAdmin(current);
    return channels.findAll().stream()
        .filter(c -> c.getScope() == AiChannelScope.SYSTEM)
        .map(this::view)
        .toList();
  }

  @Transactional
  public SystemChannelView create(CurrentAccount current, CreateSystemChannelCommand command) {
    assertAdmin(current);
    return view(createEntity(current, AiChannelScope.SYSTEM, command));
  }

  @Transactional
  public SystemChannelView update(CurrentAccount current, String id, UpdateChannelCommand command) {
    assertAdmin(current);
    return view(updateEntity(current, requireSystem(id), command));
  }

  @Transactional
  public CapabilityView capabilityTest(
      CurrentAccount current, String id, String providerModel, String platformModelCode) {
    assertAdmin(current);
    return inspectEntity(current, requireSystem(id), providerModel, platformModelCode);
  }

  @Transactional
  public SystemChannelView activate(
      CurrentAccount current, String id, int expectedVersion, String providerModel) {
    assertAdmin(current);
    return view(activateEntity(current, requireSystem(id), expectedVersion, providerModel));
  }

  @Transactional
  public SystemChannelView disable(CurrentAccount current, String id, int expectedVersion) {
    assertAdmin(current);
    return view(disableEntity(current, requireSystem(id), expectedVersion));
  }

  AiChannelEntity createEntity(
      CurrentAccount current, AiChannelScope scope, CreateSystemChannelCommand command) {
    String key = validatedKey(command.apiKey());
    AiChannelEntity entity = new AiChannelEntity();
    entity.setId(Ids.newId());
    entity.setScope(scope);
    entity.setOwnerAccountId(scope == AiChannelScope.PERSONAL ? current.accountId() : null);
    entity.setName(required(command.name(), "通道名称必填", 128));
    entity.setProviderCode(optional(command.providerCode(), 64));
    entity.setBaseUrl(normalizeBase(command.baseUrl()));
    entity.setProtocol(protocol(command.protocol()).name());
    entity.setApiKeyCiphertext(cipher.encrypt(key));
    entity.setApiKeyMasked(AiUserChannelService.mask(key));
    entity.setChannelRate(BigDecimal.ONE);
    entity.setRoutingWeight(weight(command.routingWeight()));
    entity.setStatus(AiChannelStatus.DRAFT);
    entity.setCreatedAt(clock.now());
    entity.setUpdatedAt(clock.now());
    channels.saveAndFlush(entity);
    audit.append(
        current.accountId(),
        "AI_CHANNEL_CREATE",
        "AI_CHANNEL",
        entity.getId(),
        "scope=" + scope + "; configuration created");
    return entity;
  }

  AiChannelEntity updateEntity(
      CurrentAccount current, AiChannelEntity entity, UpdateChannelCommand command) {
    assertEditable(entity);
    assertVersion(entity, command.expectedVersion());
    entity.setName(required(command.name(), "通道名称必填", 128));
    if (command.providerCode() != null)
      entity.setProviderCode(optional(command.providerCode(), 64));
    if (command.routingWeight() != null) entity.setRoutingWeight(weight(command.routingWeight()));
    String base = normalizeBase(command.baseUrl());
    String nextProtocol =
        command.protocol() == null ? draftProtocol(entity) : protocol(command.protocol()).name();
    String nextModel =
        command.selectedModel() == null
            ? draftModel(entity)
            : optional(command.selectedModel(), 128);
    boolean replaceKey = command.apiKey() != null && !command.apiKey().isBlank();
    boolean changed =
        !Objects.equals(base, draftBase(entity))
            || !Objects.equals(nextProtocol, draftProtocol(entity))
            || !Objects.equals(nextModel, draftModel(entity))
            || replaceKey;
    if (changed) {
      entity.setPendingBaseUrl(base);
      entity.setPendingProtocol(nextProtocol);
      entity.setPendingModel(nextModel);
      if (replaceKey) {
        String key = validatedKey(command.apiKey());
        entity.setPendingApiKeyCiphertext(cipher.encrypt(key));
        entity.setPendingApiKeyMasked(AiUserChannelService.mask(key));
      }
      entity.setConfigVersion(entity.getConfigVersion() + 1);
    }
    entity.setUpdatedAt(clock.now());
    channels.saveAndFlush(entity);
    audit.append(
        current.accountId(),
        "AI_CHANNEL_CONFIG_UPDATE",
        "AI_CHANNEL",
        entity.getId(),
        "draftVersion="
            + entity.getConfigVersion()
            + "; connectionChanged="
            + changed
            + "; keyReplaced="
            + replaceKey
            + "; routingWeight="
            + entity.getRoutingWeight());
    return entity;
  }

  CapabilityView inspectEntity(
      CurrentAccount current,
      AiChannelEntity entity,
      String requestedModel,
      String platformModelCode) {
    assertEditable(entity);
    String requested =
        requestedModel == null || requestedModel.isBlank()
            ? null
            : required(requestedModel, "模型必填", 128);
    // The caller locks the channel row, so a late test cannot certify another key or endpoint.
    CapabilityDetection result =
        inspector.inspectCapabilities(
            new Channel(
                entity.getId(),
                protocol(draftProtocol(entity)),
                URI.create(draftBase(entity)),
                draftKey(entity),
                0,
                entity.getRoutingWeight(),
                true),
            requested);
    String tested = result.testedModel();
    if (tested != null && !Objects.equals(tested, draftModel(entity))) {
      entity.setPendingModel(tested);
      entity.setConfigVersion(entity.getConfigVersion() + 1);
    }
    Instant now = clock.now();
    String id = Ids.newId();
    entity.setLastCapabilityId(id);
    entity.setUpdatedAt(now);
    channels.saveAndFlush(entity);
    // Candidate failures must preserve the previously activated configuration and its health.
    if (entity.getStatus() != AiChannelStatus.ACTIVE)
      recordHealth(entity.getId(), result.passed(), result.failureCode(), now);
    String alias =
        platformModelCode == null || platformModelCode.isBlank()
            ? null
            : required(platformModelCode, "模型别名过长", 128);
    jdbc.update(
        "INSERT INTO"
            + " ai_channel_capability_test(id,channel_id,normalized_base_url,models_json,tested_model,authenticated,model_discovery,plain_response,streaming_response,structured_response,status,failure_code,detail,checked_by,checked_at,platform_model_code,config_version)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        entity.getId(),
        result.normalizedBaseUrl(),
        json(result.models()),
        tested,
        result.authenticated(),
        result.modelDiscovery(),
        result.plainResponse(),
        result.streamingResponse(),
        result.structuredResponse(),
        result.passed() ? "PASSED" : "FAILED",
        result.failureCode(),
        result.detail(),
        current.accountId(),
        now,
        alias,
        entity.getConfigVersion());
    audit.append(
        current.accountId(),
        "AI_CHANNEL_TEST",
        "AI_CHANNEL",
        entity.getId(),
        "draftVersion="
            + entity.getConfigVersion()
            + "; result="
            + (result.passed() ? "PASSED" : "FAILED"));
    return new CapabilityView(
        id,
        entity.getId(),
        result.normalizedBaseUrl(),
        result.models(),
        tested,
        result.authenticated(),
        result.modelDiscovery(),
        result.plainResponse(),
        result.streamingResponse(),
        result.structuredResponse(),
        result.passed() ? "PASSED" : "FAILED",
        result.failureCode(),
        result.detail(),
        now,
        alias,
        entity.getConfigVersion());
  }

  AiChannelEntity activateEntity(
      CurrentAccount current, AiChannelEntity entity, int expectedVersion, String providerModel) {
    assertEditable(entity);
    assertVersion(entity, expectedVersion);
    CapabilityView latest = latestCapability(entity);
    String selected =
        providerModel == null || providerModel.isBlank()
            ? latest == null ? null : latest.testedModel()
            : providerModel.trim();
    if (latest == null
        || !"PASSED".equals(latest.status())
        || selected == null
        || selected.isBlank()
        || !selected.equals(latest.testedModel())
        || !selected.equals(draftModel(entity))
        || latest.configVersion() != entity.getConfigVersion())
      throw AppException.conflict("AI_CAPABILITY_GATE_FAILED", "当前待生效配置及所选模型尚未通过检测，请重新检测再启用");
    entity.setSelectedModel(selected);
    entity.setBaseUrl(latest.normalizedBaseUrl());
    entity.setProtocol(draftProtocol(entity));
    entity.setApiKeyCiphertext(draftKey(entity));
    entity.setApiKeyMasked(draftMasked(entity));
    entity.setPendingBaseUrl(null);
    entity.setPendingProtocol(null);
    entity.setPendingApiKeyCiphertext(null);
    entity.setPendingApiKeyMasked(null);
    entity.setPendingModel(null);
    entity.setAppliedConfigVersion(entity.getConfigVersion());
    entity.setStatus(AiChannelStatus.ACTIVE);
    entity.setUpdatedAt(clock.now());
    if (latest.platformModelCode() != null && entity.getScope() == AiChannelScope.SYSTEM)
      mapModel(entity.getId(), latest.platformModelCode(), selected, clock.now());
    recordHealth(entity.getId(), true, null, clock.now());
    channels.saveAndFlush(entity);
    audit.append(
        current.accountId(),
        "AI_CHANNEL_ACTIVATE",
        "AI_CHANNEL",
        entity.getId(),
        "appliedVersion=" + entity.getAppliedConfigVersion() + "; model applied");
    return entity;
  }

  AiChannelEntity disableEntity(
      CurrentAccount current, AiChannelEntity entity, int expectedVersion) {
    assertEditable(entity);
    assertVersion(entity, expectedVersion);
    entity.setStatus(AiChannelStatus.DISABLED);
    entity.setUpdatedAt(clock.now());
    channels.saveAndFlush(entity);
    audit.append(
        current.accountId(),
        "AI_CHANNEL_DISABLE",
        "AI_CHANNEL",
        entity.getId(),
        "channel disabled");
    return entity;
  }

  SystemChannelView view(AiChannelEntity entity) {
    var snapshot = health.findByChannelId(entity.getId()).orElse(null);
    boolean pending =
        entity.getConfigVersion() != entity.getAppliedConfigVersion()
            || entity.getStatus() == AiChannelStatus.DRAFT;
    return new SystemChannelView(
        entity.getId(),
        entity.getName(),
        entity.getProviderCode(),
        entity.getBaseUrl(),
        entity.getProtocol(),
        entity.getApiKeyMasked(),
        entity.getStatus().name(),
        snapshot == null ? "UNKNOWN" : snapshot.getStatus(),
        entity.getVersionNo(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getSelectedModel(),
        latestCapability(entity),
        entity.getRoutingWeight(),
        entity.getConfigVersion(),
        entity.getAppliedConfigVersion(),
        pending
            ? new PendingConfiguration(
                draftBase(entity),
                draftProtocol(entity),
                draftMasked(entity),
                draftModel(entity),
                entity.getConfigVersion())
            : null,
        entity.getScope().name(),
        entity.getOwnerAccountId(),
        entity.getChannelRate(),
        snapshot == null ? null : snapshot.getCheckedAt());
  }

  CapabilityView capabilityById(String id) {
    return jdbc
        .query("SELECT * FROM ai_channel_capability_test WHERE id=?", this::capability, id)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  private CapabilityView latestCapability(AiChannelEntity entity) {
    if (entity.getLastCapabilityId() != null) return capabilityById(entity.getLastCapabilityId());
    return jdbc
        .query(
            "SELECT * FROM ai_channel_capability_test WHERE channel_id=? ORDER BY checked_at"
                + " DESC,id DESC LIMIT 1",
            this::capability,
            entity.getId())
        .stream()
        .findFirst()
        .orElse(null);
  }

  private CapabilityView capability(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    return new CapabilityView(
        r.getString("id"),
        r.getString("channel_id"),
        r.getString("normalized_base_url"),
        modelList(r.getString("models_json")),
        r.getString("tested_model"),
        r.getBoolean("authenticated"),
        r.getBoolean("model_discovery"),
        r.getBoolean("plain_response"),
        r.getBoolean("streaming_response"),
        r.getBoolean("structured_response"),
        r.getString("status"),
        r.getString("failure_code"),
        r.getString("detail"),
        r.getTimestamp("checked_at").toInstant(),
        r.getString("platform_model_code"),
        r.getInt("config_version"));
  }

  private void recordHealth(String id, boolean passed, String code, Instant now) {
    var h = health.findByChannelId(id).orElseGet(AiChannelHealthEntity::new);
    if (h.getId() == null) {
      h.setId(Ids.newId());
      h.setChannelId(id);
      h.setConsecutiveFailures(0);
    }
    h.setStatus(passed ? "HEALTHY" : "UNHEALTHY");
    h.setLatencyMs(null);
    h.setConsecutiveFailures(passed ? 0 : h.getConsecutiveFailures() + 1);
    h.setFailureCode(code);
    h.setCheckedAt(now);
    health.save(h);
  }

  private void mapModel(String id, String alias, String modelCode, Instant now) {
    var model =
        models
            .findByModelCode(alias)
            .orElseThrow(() -> AppException.user("AI_MODEL_NOT_FOUND", "平台模型不存在"));
    if (!"ACTIVE".equals(model.getStatus()))
      throw AppException.conflict("AI_MODEL_DISABLED", "平台模型未启用");
    if (mappings.findByChannelIdAndModelId(id, model.getId()).isPresent())
      jdbc.update(
          "UPDATE ai_channel_model SET provider_model_code=?,status='ACTIVE',hidden=0,updated_at=?"
              + " WHERE channel_id=? AND model_id=?",
          modelCode,
          now,
          id,
          model.getId());
    else
      jdbc.update(
          "INSERT INTO"
              + " ai_channel_model(id,channel_id,model_id,provider_model_code,status,hidden,priority,version_no,created_at,updated_at)"
              + " VALUES(?,?,?,?, 'ACTIVE',0,100,0,?,?)",
          Ids.newId(),
          id,
          model.getId(),
          modelCode,
          now,
          now);
  }

  private AiChannelEntity requireSystem(String id) {
    var entity =
        channels
            .findLockedById(id)
            .orElseThrow(() -> AppException.user("AI_CHANNEL_NOT_FOUND", "系统通道不存在"));
    if (entity.getScope() != AiChannelScope.SYSTEM)
      throw AppException.forbidden("AI_CHANNEL_FORBIDDEN", "只能管理系统通道");
    return entity;
  }

  static void assertVersion(AiChannelEntity entity, int version) {
    if (entity.getVersionNo() != version)
      throw AppException.conflict("AI_VERSION_CONFLICT", "通道已更新，请刷新后重试");
  }

  static void assertEditable(AiChannelEntity entity) {
    if (entity.getStatus() == AiChannelStatus.ARCHIVED)
      throw AppException.conflict("AI_STATE_CONFLICT", "已归档通道仅供查看");
  }

  private static String draftBase(AiChannelEntity e) {
    return e.getPendingBaseUrl() == null ? e.getBaseUrl() : e.getPendingBaseUrl();
  }

  private static String draftProtocol(AiChannelEntity e) {
    return e.getPendingProtocol() == null ? e.getProtocol() : e.getPendingProtocol();
  }

  private static String draftKey(AiChannelEntity e) {
    return e.getPendingApiKeyCiphertext() == null
        ? e.getApiKeyCiphertext()
        : e.getPendingApiKeyCiphertext();
  }

  private static String draftMasked(AiChannelEntity e) {
    return e.getPendingApiKeyMasked() == null ? e.getApiKeyMasked() : e.getPendingApiKeyMasked();
  }

  private static String draftModel(AiChannelEntity e) {
    return e.getPendingModel() == null ? e.getSelectedModel() : e.getPendingModel();
  }

  private static int weight(Integer value) {
    int w = value == null ? 100 : value;
    if (w < 1 || w > 10000) throw AppException.user("AI_VALIDATION_FAILED", "路由权重须为 1 至 10000");
    return w;
  }

  private static String validatedKey(String value) {
    String key = required(value, "API Key 必填", 4096);
    if (key.contains("****")) throw AppException.user("AI_VALIDATION_FAILED", "掩码不能作为 API Key");
    return key;
  }

  private static String normalizeBase(String value) {
    String clean = required(value, "HTTPS Base URL 必填", 1024).replaceAll("/+$", "");
    URI uri;
    try {
      uri = URI.create(clean);
    } catch (RuntimeException e) {
      throw AppException.user("AI_VALIDATION_FAILED", "Base URL 格式无效");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null)
      throw AppException.user("AI_VALIDATION_FAILED", "Base URL 必须为无凭证及查询参数的 HTTPS 地址");
    return clean;
  }

  private static AiProtocol protocol(String value) {
    try {
      return AiProtocol.valueOf(required(value, "协议必填", 32).toUpperCase());
    } catch (RuntimeException e) {
      throw AppException.user("AI_VALIDATION_FAILED", "AI 协议不受支持");
    }
  }

  private static String optional(String value, int max) {
    String v = value == null ? "" : value.trim();
    if (v.length() > max) throw AppException.user("AI_VALIDATION_FAILED", "字段超过最大长度");
    return v;
  }

  private static String required(String value, String message, int max) {
    if (value == null || value.isBlank() || value.trim().length() > max)
      throw AppException.user("AI_VALIDATION_FAILED", message);
    return value.trim();
  }

  private List<String> modelList(String value) {
    try {
      return mapper.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Invalid saved model discovery", e);
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void assertAdmin(CurrentAccount c) {
    if (c == null || !"ADMIN".equals(c.role()))
      throw AppException.forbidden("ADMIN_REQUIRED", "仅管理员可以管理系统 AI 通道");
  }

  public record CreateSystemChannelCommand(
      String name,
      String providerCode,
      String baseUrl,
      String apiKey,
      String protocol,
      Integer routingWeight) {
    public CreateSystemChannelCommand(
        String name, String providerCode, String baseUrl, String apiKey, String protocol) {
      this(name, providerCode, baseUrl, apiKey, protocol, 100);
    }
  }

  public record UpdateChannelCommand(
      String name,
      String providerCode,
      String baseUrl,
      String apiKey,
      String protocol,
      Integer routingWeight,
      String selectedModel,
      int expectedVersion) {}

  public record PendingConfiguration(
      String baseUrl,
      String protocol,
      String apiKeyMasked,
      String selectedModel,
      int configVersion) {}

  public record SystemChannelView(
      String id,
      String name,
      String providerCode,
      String baseUrl,
      String protocol,
      String apiKeyMasked,
      String status,
      String healthStatus,
      int versionNo,
      Instant createdAt,
      Instant updatedAt,
      String selectedModel,
      CapabilityView capability,
      int routingWeight,
      int configVersion,
      int appliedConfigVersion,
      PendingConfiguration pending,
      String scope,
      String ownerAccountId,
      BigDecimal channelRate,
      Instant lastCheckedAt) {}

  public record CapabilityView(
      String id,
      String channelId,
      String normalizedBaseUrl,
      List<String> models,
      String testedModel,
      boolean authenticated,
      boolean modelDiscovery,
      boolean plainResponse,
      boolean streamingResponse,
      boolean structuredResponse,
      String status,
      String failureCode,
      String detail,
      Instant checkedAt,
      String platformModelCode,
      int configVersion) {}
}
