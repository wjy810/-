package com.jobproof.modules.aiconfig.infra;

import com.jobproof.modules.aiconfig.domain.AiChannelScope;
import com.jobproof.modules.aiconfig.domain.AiChannelStatus;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel;
import com.jobproof.modules.aigateway.domain.AiProtocol;
import com.jobproof.modules.aigateway.port.AiChannelPort;
import com.jobproof.shared.ai.AiExecutionContext;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAiChannelAdapter implements AiChannelPort {
  private final AiChannelJpaRepository channels;
  private final AiChannelModelJpaRepository mappings;
  private final AiModelJpaRepository models;
  private final AiChannelHealthJpaRepository health;
  private final JdbcTemplate jdbc;
  private final ClockPort clock;

  public JpaAiChannelAdapter(
      AiChannelJpaRepository channels,
      AiChannelModelJpaRepository mappings,
      AiModelJpaRepository models,
      AiChannelHealthJpaRepository health,
      JdbcTemplate jdbc,
      ClockPort clock) {
    this.channels = channels;
    this.mappings = mappings;
    this.models = models;
    this.health = health;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  @Transactional
  public List<Channel> findEligible(String accountId, String model) {
    lockAccount(accountId);
    var context = AiExecutionContext.current();
    if (context != null && context.reservationId() != null) {
      if (!accountId.equals(context.accountId()))
        throw AppException.forbidden("AI_CHANNEL_FORBIDDEN", "AI 执行账号不匹配");
      if (context.personalChannelId() != null)
        return personal(accountId, context.personalChannelId());
      return system(model);
    }
    if (preference(accountId) != null)
      throw AppException.conflict("AI_QUOTE_REQUIRED", "请重新确认个人通道使用方式");
    return system(model);
  }

  @Override
  @Transactional
  public List<Channel> findAvailable(String accountId, String model) {
    lockAccount(accountId);
    var context = AiExecutionContext.current();
    if (context != null && context.reservationId() != null) return findEligible(accountId, model);
    String selected = preference(accountId);
    return selected == null ? system(model) : personal(accountId, selected);
  }

  private void lockAccount(String accountId) {
    jdbc.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE", String.class, accountId);
  }

  private String preference(String accountId) {
    return jdbc
        .query(
            "SELECT personal_channel_id FROM ai_channel_preferences WHERE account_id=? FOR UPDATE",
            (r, n) -> r.getString(1),
            accountId)
        .stream()
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private List<Channel> personal(String accountId, String id) {
    var subscription =
        jdbc.query(
            "SELECT status,current_period_end FROM personal_channel_subscription WHERE account_id=?"
                + " FOR UPDATE",
            (r, n) ->
                "ACTIVE".equals(r.getString(1))
                    && r.getTimestamp(2).toInstant().isAfter(clock.now()),
            accountId);
    if (subscription.isEmpty() || !subscription.getFirst())
      throw AppException.conflict("PERSONAL_SUBSCRIPTION_REQUIRED", "个人通道订阅已到期，请续费或明确切换平台通道");
    // Read under a row lock rather than reusing an older persistence-context entity.
    var result =
        jdbc.query(
            "SELECT * FROM ai_channel WHERE id=? AND owner_account_id=? AND scope='PERSONAL' AND"
                + " status='ACTIVE' FOR UPDATE",
            (r, n) -> {
              String selected = r.getString("selected_model");
              if (selected == null || selected.isBlank())
                throw AppException.conflict("PERSONAL_CHANNEL_UNAVAILABLE", "个人通道尚未启用具体模型");
              return new Channel(
                  r.getString("id"),
                  AiProtocol.valueOf(r.getString("protocol")),
                  URI.create(r.getString("base_url")),
                  r.getString("api_key_ciphertext"),
                  selected,
                  0,
                  r.getInt("routing_weight"),
                  true);
            },
            id,
            accountId);
    if (result.isEmpty())
      throw AppException.conflict("PERSONAL_CHANNEL_UNAVAILABLE", "所选个人通道不可用，请检测启用或重新选择");
    return List.copyOf(result);
  }

  private List<Channel> system(String model) {
    boolean automatic = "auto".equals(model);
    var requested = automatic ? null : models.findByModelCode(model).orElse(null);
    if (requested != null && !"ACTIVE".equals(requested.getStatus())) return List.of();
    List<Channel> result = new ArrayList<>();
    for (AiChannelEntity entity : channels.findAll()) {
      if (entity.getStatus() != AiChannelStatus.ACTIVE
          || entity.getScope() != AiChannelScope.SYSTEM) continue;
      String providerModel;
      if (entity.getSelectedModel() != null
          && (automatic || entity.getSelectedModel().equals(model))) {
        providerModel = entity.getSelectedModel();
        var catalog = models.findByModelCode(providerModel).orElse(null);
        if (catalog != null) {
          if (!"ACTIVE".equals(catalog.getStatus())) continue;
          var binding =
              mappings.findByChannelIdAndModelId(entity.getId(), catalog.getId()).orElse(null);
          if (binding != null && (!"ACTIVE".equals(binding.getStatus()) || binding.isHidden()))
            continue;
        }
      } else {
        if (automatic || requested == null) continue;
        var explicit =
            mappings.findByChannelIdAndModelId(entity.getId(), requested.getId()).orElse(null);
        if (explicit != null && (!"ACTIVE".equals(explicit.getStatus()) || explicit.isHidden()))
          continue;
        if (explicit == null
            && (entity.getSelectedModel() != null
                || entity.getProviderCode().isBlank()
                || !entity.getProviderCode().equalsIgnoreCase(requested.getProviderCode())))
          continue;
        providerModel = explicit == null ? model : explicit.getProviderModelCode();
        if (entity.getSelectedModel() != null && !entity.getSelectedModel().equals(providerModel)) continue;
      }
      var snapshot = health.findByChannelId(entity.getId()).orElse(null);
      int priority =
          snapshot == null || "UNKNOWN".equals(snapshot.getStatus())
              ? 1
              : "HEALTHY".equals(snapshot.getStatus())
                  ? 0
                  : "DEGRADED".equals(snapshot.getStatus()) ? 2 : 3;
      result.add(
          new Channel(
              entity.getId(),
              AiProtocol.valueOf(entity.getProtocol().trim().toUpperCase()),
              URI.create(entity.getBaseUrl()),
              entity.getApiKeyCiphertext(),
              providerModel,
              priority,
              entity.getRoutingWeight(),
              true));
    }
    return List.copyOf(result);
  }

  @Override
  @Transactional
  public void recordSuccess(String id, long latency) {
    var v = value(id);
    v.setStatus("HEALTHY");
    v.setLatencyMs(latency);
    v.setConsecutiveFailures(0);
    v.setFailureCode(null);
    v.setCheckedAt(clock.now());
    health.save(v);
  }

  @Override
  @Transactional
  public void recordFailure(String id, String failure) {
    var v = value(id);
    v.setStatus(v.getConsecutiveFailures() >= 2 ? "UNHEALTHY" : "DEGRADED");
    v.setConsecutiveFailures(v.getConsecutiveFailures() + 1);
    v.setFailureCode(failure);
    v.setCheckedAt(clock.now());
    health.save(v);
  }

  private AiChannelHealthEntity value(String id) {
    return health
        .findByChannelId(id)
        .orElseGet(
            () -> {
              var v = new AiChannelHealthEntity();
              v.setId(Ids.newId());
              v.setChannelId(id);
              v.setStatus("UNKNOWN");
              v.setConsecutiveFailures(0);
              v.setCheckedAt(clock.now());
              return v;
            });
  }
}
