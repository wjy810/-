package com.jobproof.modules.aiconfig.application;

import com.jobproof.modules.aiconfig.domain.AiChannelScope;
import com.jobproof.modules.aiconfig.domain.AiChannelStatus;
import com.jobproof.modules.aiconfig.infra.AiChannelEntity;
import com.jobproof.modules.aiconfig.infra.AiChannelHealthEntity;
import com.jobproof.modules.aiconfig.infra.AiChannelHealthJpaRepository;
import com.jobproof.modules.aiconfig.infra.AiChannelJpaRepository;
import com.jobproof.modules.aigateway.security.ApiKeyCipher;
import com.jobproof.modules.aigateway.application.AiChannelInspector;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel;
import com.jobproof.modules.aigateway.domain.AiProtocol;
import java.net.URI;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiUserChannelService {
    private final AiChannelJpaRepository channels;
    private final AiChannelHealthJpaRepository health;
    private final ApiKeyCipher cipher;
    private final AiChannelInspector inspector;
    private final Map<String, Idempotency> idempotency = new HashMap<>();

    public AiUserChannelService(AiChannelJpaRepository channels, AiChannelHealthJpaRepository health,
            ApiKeyCipher cipher, AiChannelInspector inspector) {
        this.channels = channels;
        this.health = health;
        this.cipher = cipher;
        this.inspector = inspector;
    }

    @Transactional(readOnly = true)
    public Page<ChannelView> list(CurrentAccount current, String scope, String status, Pageable pageable) {
        assertAllowed(current);
        AiChannelScope parsed = parseScope(scope);
        if (parsed == AiChannelScope.PERSONAL) personalChannelsDisabled();
        Page<AiChannelEntity> result = parsed == AiChannelScope.PERSONAL
                ? channels.findByScopeAndOwnerAccountIdAndStatusNot(parsed, current.accountId(), AiChannelStatus.ARCHIVED, pageable)
                : channels.findByScopeAndStatus(parsed, status == null ? AiChannelStatus.ACTIVE : parseStatus(status), pageable);
        return result.map(this::view);
    }

    @Transactional
    public ChannelView create(CurrentAccount current, CreateCommand command) {
        personalChannelsDisabled();
        assertAllowed(current);
        validate(command.name(), command.baseUrl(), command.apiKey(), command.idempotencyKey());
        String fingerprint = command.toString();
        String replay = replay(current.accountId(), "CREATE", command.idempotencyKey(), fingerprint);
        if (replay != null) return view(requireOwn(current, replay));
        Instant now = Instant.now();
        AiChannelEntity entity = new AiChannelEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setScope(AiChannelScope.PERSONAL);
        entity.setOwnerAccountId(current.accountId());
        entity.setName(command.name().trim());
        entity.setProviderCode(command.providerCode().trim());
        entity.setBaseUrl(command.baseUrl().trim());
        entity.setProtocol(command.protocol().trim());
        entity.setApiKeyCiphertext(cipher.encrypt(command.apiKey()));
        entity.setApiKeyMasked(mask(command.apiKey()));
        entity.setChannelRate(BigDecimal.ONE);
        entity.setStatus(AiChannelStatus.DRAFT);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        channels.save(entity);
        remember(current.accountId(), "CREATE", command.idempotencyKey(), fingerprint, entity.getId());
        return view(entity);
    }

    @Transactional
    public ChannelView update(CurrentAccount current, String id, UpdateCommand command) {
        personalChannelsDisabled();
        validate(command.name(), command.baseUrl(), command.apiKey() == null ? "unchanged" : command.apiKey(), command.idempotencyKey());
        AiChannelEntity entity = requireOwn(current, id);
        assertVersion(entity, command.versionNo());
        String fingerprint = command.toString();
        String replay = replay(current.accountId(), "UPDATE", command.idempotencyKey(), fingerprint);
        if (replay != null) return view(requireOwn(current, replay));
        entity.setName(command.name().trim());
        entity.setBaseUrl(command.baseUrl().trim());
        if (command.apiKey() != null && !command.apiKey().isBlank()) {
            if (command.apiKey().contains("****")) throw AppException.user("AI_VALIDATION_FAILED", "掩码不能作为新密钥");
            entity.setApiKeyCiphertext(cipher.encrypt(command.apiKey()));
            entity.setApiKeyMasked(mask(command.apiKey()));
        }
        AiChannelStatus next = parseStatus(command.status());
        if (next == AiChannelStatus.ARCHIVED) throw AppException.conflict("AI_STATE_CONFLICT", "请使用归档端点");
        entity.setStatus(next);
        entity.setUpdatedAt(Instant.now());
        channels.save(entity);
        remember(current.accountId(), "UPDATE", command.idempotencyKey(), fingerprint, id);
        return view(entity);
    }

    @Transactional
    public ChannelView archive(CurrentAccount current, String id, int versionNo, String key) {
        personalChannelsDisabled();
        AiChannelEntity entity = requireOwn(current, id);
        assertVersion(entity, versionNo);
        String fingerprint = id + ":" + versionNo;
        String replay = replay(current.accountId(), "ARCHIVE", key, fingerprint);
        if (replay != null) return view(requireOwn(current, replay));
        entity.setStatus(AiChannelStatus.ARCHIVED);
        entity.setArchivedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        channels.save(entity);
        remember(current.accountId(), "ARCHIVE", key, fingerprint, id);
        return view(entity);
    }

    @Transactional
    public TestView test(CurrentAccount current, String id, String key) {
        personalChannelsDisabled();
        requireKey(key);
        AiChannelEntity channel=requireOwn(current, id);
        var detection=inspector.inspect(new Channel(channel.getId(),AiProtocol.valueOf(channel.getProtocol().trim().toUpperCase()),
                URI.create(channel.getBaseUrl()),channel.getApiKeyCiphertext(),0,100,true));
        AiChannelHealthEntity snapshot = health.findByChannelId(id).orElseGet(AiChannelHealthEntity::new);
        if (snapshot.getId() == null) {
            snapshot.setId(UUID.randomUUID().toString());
            snapshot.setChannelId(id);
        }
        snapshot.setStatus(detection.reachable()&&detection.authenticated()?"HEALTHY":"UNHEALTHY");
        snapshot.setLatencyMs(null);
        snapshot.setConsecutiveFailures(detection.reachable()&&detection.authenticated()?0:snapshot.getConsecutiveFailures()+1);
        snapshot.setFailureCode(detection.reachable()&&detection.authenticated()?null:detection.authenticated()?"UPSTREAM_UNREACHABLE":"AUTHENTICATION_REJECTED");
        snapshot.setCheckedAt(Instant.now());
        health.save(snapshot);
        return new TestView(id, snapshot.getStatus(), snapshot.getLatencyMs(), snapshot.getCheckedAt(), snapshot.getFailureCode(),
                detection.detail()+"；models="+String.join(",",detection.models()));
    }

    private AiChannelEntity requireOwn(CurrentAccount current, String id) {
        assertAllowed(current);
        AiChannelEntity entity = channels.findById(id)
                .orElseThrow(() -> AppException.user("AI_CHANNEL_NOT_FOUND", "通道不存在"));
        if (entity.getScope() != AiChannelScope.PERSONAL || !current.accountId().equals(entity.getOwnerAccountId())) {
            throw AppException.forbidden("AI_CHANNEL_FORBIDDEN", "不能访问他人的个人通道");
        }
        return entity;
    }

    private ChannelView view(AiChannelEntity entity) {
        AiChannelHealthEntity h = health.findByChannelId(entity.getId()).orElse(null);
        return new ChannelView(entity.getId(), entity.getScope().name(),
                entity.getScope() == AiChannelScope.PERSONAL ? entity.getOwnerAccountId() : null,
                entity.getName(), entity.getProviderCode(), entity.getBaseUrl(), entity.getProtocol(),
                entity.getApiKeyMasked(), entity.getStatus().name(), entity.getChannelRate(),
                h == null ? "UNKNOWN" : h.getStatus(), h == null ? null : h.getCheckedAt(),
                entity.getVersionNo(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private synchronized String replay(String account, String operation, String key, String fingerprint) {
        requireKey(key);
        Idempotency existing = idempotency.get(account + "|" + operation + "|" + key);
        if (existing == null) return null;
        if (!existing.fingerprint().equals(fingerprint)) throw AppException.conflict("AI_IDEMPOTENCY_CONFLICT", "幂等键已用于不同请求");
        return existing.resultId();
    }

    private synchronized void remember(String account, String operation, String key, String fingerprint, String result) {
        idempotency.put(account + "|" + operation + "|" + key, new Idempotency(fingerprint, result));
    }

    private static void assertVersion(AiChannelEntity entity, int version) {
        if (entity.getVersionNo() != version) throw AppException.conflict("AI_VERSION_CONFLICT", "版本已更新，请刷新后重试");
    }

    private static void validate(String name, String baseUrl, String apiKey, String key) {
        if (name == null || name.isBlank() || baseUrl == null || !baseUrl.startsWith("https://") || apiKey == null || apiKey.isBlank()) {
            throw AppException.user("AI_VALIDATION_FAILED", "名称、HTTPS 地址和 API Key 必填");
        }
        requireKey(key);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) throw AppException.user("AI_VALIDATION_FAILED", "idempotencyKey 必填且最长 128");
    }

    static String mask(String key) {
        return "****" + (key.length() <= 4 ? key : key.substring(key.length() - 4));
    }

    private static AiChannelScope parseScope(String scope) {
        try {
            return AiChannelScope.valueOf(scope == null ? "SYSTEM" : scope);
        } catch (RuntimeException ex) {
            throw AppException.user("AI_VALIDATION_FAILED", "scope 仅支持 SYSTEM 或 PERSONAL");
        }
    }

    private static AiChannelStatus parseStatus(String status) {
        try {
            return AiChannelStatus.valueOf(status);
        } catch (RuntimeException ex) {
            throw AppException.user("AI_VALIDATION_FAILED", "通道状态无效");
        }
    }

    private static void assertAllowed(CurrentAccount current) {
        if (!"SEEKER".equals(current.role()) && !"ADMIN".equals(current.role())) {
            throw AppException.forbidden("AI_CHANNEL_FORBIDDEN", "仅求职者或管理员可访问 AI 通道池");
        }
    }

    private static void personalChannelsDisabled() {
        throw AppException.forbidden("AI_PERSONAL_CHANNELS_DISABLED", "普通用户不配置供应商 Key，请使用平台 SYSTEM 通道");
    }

    private record Idempotency(String fingerprint, String resultId) {}
    public record CreateCommand(String name, String providerCode, String baseUrl, String apiKey, String protocol, String idempotencyKey) {}
    public record UpdateCommand(String name, String baseUrl, String apiKey, String status, int versionNo, String idempotencyKey) {}
    public record ChannelView(String id, String scope, String ownerAccountId, String name, String providerCode,
            String baseUrl, String protocol, String apiKeyMasked, String status, BigDecimal channelRate,
            String healthStatus, Instant lastCheckedAt, int versionNo, Instant createdAt, Instant updatedAt) {}
    public record TestView(String channelId, String status, Long latencyMs, Instant checkedAt, String failureCode, String message) {}
}
