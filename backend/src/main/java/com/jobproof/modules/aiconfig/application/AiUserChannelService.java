package com.jobproof.modules.aiconfig.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aiconfig.application.AiAdminChannelService.*;
import com.jobproof.modules.aiconfig.domain.AiChannelScope;
import com.jobproof.modules.aiconfig.domain.AiChannelStatus;
import com.jobproof.modules.aiconfig.infra.AiChannelEntity;
import com.jobproof.modules.aiconfig.infra.AiChannelJpaRepository;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiUserChannelService {
  private final AiChannelJpaRepository channels;
  private final AiAdminChannelService configuration;
  private final JdbcTemplate jdbc;
  private final ClockPort clock;
  private final ObjectMapper mapper;
  private final AuditService audit;

  public AiUserChannelService(
      AiChannelJpaRepository channels,
      AiAdminChannelService configuration,
      JdbcTemplate jdbc,
      ClockPort clock,
      ObjectMapper mapper,
      AuditService audit) {
    this.channels = channels;
    this.configuration = configuration;
    this.jdbc = jdbc;
    this.clock = clock;
    this.mapper = mapper;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public Page<SystemChannelView> list(
      CurrentAccount current, String scope, String status, Pageable pageable) {
    assertAllowed(current);
    AiChannelScope parsed;
    try {
      parsed = AiChannelScope.valueOf(scope == null ? "SYSTEM" : scope);
    } catch (RuntimeException e) {
      throw AppException.user("AI_VALIDATION_FAILED", "scope 仅支持 SYSTEM 或 PERSONAL");
    }
    if (parsed == AiChannelScope.PERSONAL)
      return channels
          .findByScopeAndOwnerAccountId(parsed, current.accountId(), pageable)
          .map(configuration::view);
    // Public channel listings expose active system channels only, never their pending
    // configuration.
    return channels
        .findByScopeAndStatus(AiChannelScope.SYSTEM, AiChannelStatus.ACTIVE, pageable)
        .map(
            entity -> {
              var value = configuration.view(entity);
              return new SystemChannelView(
                  value.id(),
                  value.name(),
                  value.providerCode(),
                  value.baseUrl(),
                  value.protocol(),
                  "****",
                  value.status(),
                  value.healthStatus(),
                  value.versionNo(),
                  value.createdAt(),
                  value.updatedAt(),
                  value.selectedModel(),
                  null,
                  value.routingWeight(),
                  value.appliedConfigVersion(),
                  value.appliedConfigVersion(),
                  null,
                  "SYSTEM",
                  null,
                  value.channelRate(),
                  value.lastCheckedAt());
            });
  }

  @Transactional
  public SystemChannelView create(CurrentAccount current, CreateCommand command) {
    lockAccount(current);
    String replay = replay(current, "CREATE", command.idempotencyKey(), command);
    if (replay != null) return configuration.view(requireOwn(current, replay));
    requireSubscription(current.accountId());
    if (channels.countByScopeAndOwnerAccountIdAndStatusNot(
            AiChannelScope.PERSONAL, current.accountId(), AiChannelStatus.ARCHIVED)
        >= 5)
      throw AppException.conflict("AI_PERSONAL_CHANNEL_LIMIT", "最多保存 5 个未归档个人通道，请先归档不再使用的通道");
    var entity =
        configuration.createEntity(
            current,
            AiChannelScope.PERSONAL,
            new CreateSystemChannelCommand(
                command.name(),
                command.providerCode(),
                command.baseUrl(),
                command.apiKey(),
                command.protocol(),
                100));
    remember(current, "CREATE", command.idempotencyKey(), command, entity.getId());
    return configuration.view(entity);
  }

  @Transactional
  public SystemChannelView update(CurrentAccount current, String id, UpdateCommand command) {
    lockAccount(current);
    String operation = "UPDATE:" + id;
    String replay = replay(current, operation, command.idempotencyKey(), command);
    if (replay != null) return configuration.view(requireOwn(current, replay));
    var entity = requireOwn(current, id);
    if (command.status() != null && !command.status().equals(entity.getStatus().name()))
      throw AppException.conflict("AI_STATE_CONFLICT", "状态变更请使用检测启用、停用或归档操作");
    configuration.updateEntity(
        current,
        entity,
        new UpdateChannelCommand(
            command.name(),
            command.providerCode(),
            command.baseUrl(),
            command.apiKey(),
            command.protocol(),
            null,
            command.selectedModel(),
            command.versionNo()));
    remember(current, operation, command.idempotencyKey(), command, id);
    return configuration.view(entity);
  }

  @Transactional
  public CapabilityView test(CurrentAccount current, String id, String model, String key) {
    lockAccount(current);
    var entity = requireOwn(current, id);
    String operation = "TEST:" + id;
    var request = new TestCommand(model, key);
    String replay = replay(current, operation, key, request);
    if (replay != null) return configuration.capabilityById(replay);
    requireSubscription(current.accountId());
    var result = configuration.inspectEntity(current, entity, model, null);
    remember(current, operation, key, request, result.id());
    return result;
  }

  @Transactional
  public SystemChannelView activate(
      CurrentAccount current, String id, int version, String model, String key) {
    lockAccount(current);
    String operation = "ACTIVATE:" + id;
    var request = new StateCommand(version, model);
    String replay = replay(current, operation, key, request);
    if (replay != null) return configuration.view(requireOwn(current, replay));
    requireSubscription(current.accountId());
    var entity = configuration.activateEntity(current, requireOwn(current, id), version, model);
    remember(current, operation, key, request, id);
    return configuration.view(entity);
  }

  @Transactional
  public SystemChannelView disable(CurrentAccount current, String id, int version, String key) {
    lockAccount(current);
    String operation = "DISABLE:" + id;
    String replay = replay(current, operation, key, version);
    if (replay != null) return configuration.view(requireOwn(current, replay));
    var entity = configuration.disableEntity(current, requireOwn(current, id), version);
    remember(current, operation, key, version, id);
    return configuration.view(entity);
  }

  @Transactional
  public SystemChannelView archive(CurrentAccount current, String id, int version, String key) {
    lockAccount(current);
    String operation = "ARCHIVE:" + id;
    String replay = replay(current, operation, key, version);
    if (replay != null) return configuration.view(requireOwn(current, replay));
    var entity = requireOwn(current, id);
    AiAdminChannelService.assertVersion(entity, version);
    AiAdminChannelService.assertEditable(entity);
    entity.setStatus(AiChannelStatus.ARCHIVED);
    entity.setArchivedAt(clock.now());
    entity.setUpdatedAt(clock.now());
    channels.saveAndFlush(entity);
    remember(current, operation, key, version, id);
    // Keep selection explicit. Archiving a selected channel makes it unavailable until the owner
    // chooses another source.
    audit.append(
        current.accountId(),
        "AI_CHANNEL_ARCHIVE",
        "AI_CHANNEL",
        id,
        "channel archived; configuration retained");
    return configuration.view(entity);
  }

  @Transactional(readOnly = true)
  public PreferenceView preference(CurrentAccount current) {
    assertAllowed(current);
    var selected =
        jdbc
            .query(
                "SELECT personal_channel_id,version_no FROM ai_channel_preferences WHERE"
                    + " account_id=?",
                (r, n) -> new PreferenceSelection(r.getString(1), r.getInt(2)),
                current.accountId())
            .stream()
            .findFirst()
            .orElse(new PreferenceSelection(null, 0));
    var subscription = subscription(current.accountId());
    return new PreferenceView(
        selected.channelId() == null ? "PLATFORM" : "PERSONAL",
        selected.channelId(),
        selected.version(),
        subscription.active(),
        subscription.end());
  }

  @Transactional
  public PreferenceView select(CurrentAccount current, PreferenceCommand command) {
    lockAccount(current);
    String replay = replay(current, "PREFERENCE", command.idempotencyKey(), command);
    if (replay != null) return preference(current);
    var before = preference(current);
    if (before.versionNo() != command.versionNo())
      throw AppException.conflict("AI_VERSION_CONFLICT", "通道选择已更新，请刷新后重试");
    String id = command.personalChannelId();
    if (id != null && id.isBlank()) throw AppException.user("AI_VALIDATION_FAILED", "请选择有效的个人通道");
    if (id == null && before.personalChannelId() != null && !command.confirmPlatform())
      throw AppException.conflict("AI_PLATFORM_CONFIRMATION_REQUIRED", "切换平台通道后将使用平台额度或按次余额，请明确确认");
    if (id != null) {
      requireSubscription(current.accountId());
      var channel = requireOwn(current, id);
      if (channel.getStatus() != AiChannelStatus.ACTIVE || channel.getSelectedModel() == null)
        throw AppException.conflict("PERSONAL_CHANNEL_UNAVAILABLE", "请先检测并启用所选个人通道");
    }
    if (jdbc.update(
            "UPDATE ai_channel_preferences SET personal_channel_id=?,version_no=version_no+1 WHERE"
                + " account_id=? AND version_no=?",
            id,
            current.accountId(),
            command.versionNo())
        == 0)
      jdbc.update(
          "INSERT INTO ai_channel_preferences(account_id,personal_channel_id,version_no)"
              + " VALUES(?,?,1)",
          current.accountId(),
          id);
    remember(current, "PREFERENCE", command.idempotencyKey(), command, current.accountId());
    audit.append(
        current.accountId(),
        "AI_CHANNEL_SELECT",
        "AI_CHANNEL",
        id,
        "source=" + (id == null ? "PLATFORM" : "PERSONAL"));
    return preference(current);
  }

  private void lockAccount(CurrentAccount current) {
    assertAllowed(current);
    if (jdbc.queryForList(
            "SELECT id FROM accounts WHERE id=? FOR UPDATE", String.class, current.accountId())
        .isEmpty()) throw AppException.forbidden("AI_CHANNEL_FORBIDDEN", "账号不存在");
  }

  private AiChannelEntity requireOwn(CurrentAccount current, String id) {
    assertAllowed(current);
    var entity =
        channels
            .findLockedById(id)
            .orElseThrow(() -> AppException.user("AI_CHANNEL_NOT_FOUND", "通道不存在"));
    if (entity.getScope() != AiChannelScope.PERSONAL
        || !current.accountId().equals(entity.getOwnerAccountId()))
      throw AppException.forbidden("AI_CHANNEL_FORBIDDEN", "不能访问他人的个人通道");
    return entity;
  }

  private SubscriptionState subscription(String accountId) {
    return jdbc
        .query(
            "SELECT status,current_period_end FROM personal_channel_subscription WHERE"
                + " account_id=?",
            (r, n) -> {
              Instant end = r.getTimestamp(2).toInstant();
              return new SubscriptionState(
                  "ACTIVE".equals(r.getString(1)) && end.isAfter(clock.now()), end);
            },
            accountId)
        .stream()
        .findFirst()
        .orElse(new SubscriptionState(false, null));
  }

  private void requireSubscription(String accountId) {
    if (!subscription(accountId).active())
      throw AppException.conflict("PERSONAL_SUBSCRIPTION_REQUIRED", "个人通道订阅未开通或已到期，请先开通或续费");
  }

  private String replay(CurrentAccount current, String operation, String key, Object request) {
    requireKey(key);
    String hash = hash(request);
    return jdbc
        .query(
            "SELECT request_hash,result_id FROM ai_channel_commands WHERE account_id=? AND"
                + " operation=? AND idempotency_key=?",
            (r, n) -> {
              if (!Objects.equals(hash, r.getString(1)))
                throw AppException.conflict("AI_IDEMPOTENCY_CONFLICT", "幂等键已用于不同请求");
              return r.getString(2);
            },
            current.accountId(),
            operation,
            key)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private void remember(
      CurrentAccount current, String operation, String key, Object request, String resultId) {
    jdbc.update(
        "INSERT INTO"
            + " ai_channel_commands(account_id,operation,idempotency_key,request_hash,result_id,created_at)"
            + " VALUES(?,?,?,?,?,?)",
        current.accountId(),
        operation,
        key,
        hash(request),
        resultId,
        clock.now());
  }

  private String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void requireKey(String key) {
    if (key == null || key.isBlank() || key.length() > 128)
      throw AppException.user("AI_VALIDATION_FAILED", "idempotencyKey 必填且最长 128");
  }

  static String mask(String key) {
    return key.length() <= 4 ? "********" : "****" + key.substring(key.length() - 4);
  }

  private static void assertAllowed(CurrentAccount current) {
    if (current == null || (!"SEEKER".equals(current.role()) && !"ADMIN".equals(current.role())))
      throw AppException.forbidden("AI_CHANNEL_FORBIDDEN", "仅求职者或管理员可访问 AI 通道池");
  }

  public record CreateCommand(
      String name,
      String providerCode,
      String baseUrl,
      String apiKey,
      String protocol,
      String idempotencyKey) {}

  public record UpdateCommand(
      String name,
      String providerCode,
      String baseUrl,
      String apiKey,
      String protocol,
      String selectedModel,
      String status,
      int versionNo,
      String idempotencyKey) {}

  public record PreferenceCommand(
      String personalChannelId, boolean confirmPlatform, int versionNo, String idempotencyKey) {}

  public record PreferenceView(
      String source,
      String personalChannelId,
      int versionNo,
      boolean subscriptionActive,
      Instant subscriptionEnd) {}

  private record TestCommand(String providerModel, String key) {}

  private record StateCommand(int version, String model) {}

  private record SubscriptionState(boolean active, Instant end) {}

  private record PreferenceSelection(String channelId, int version) {}
}
