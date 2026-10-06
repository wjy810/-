package com.jobproof.modules.aiconfig.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aiconfig.domain.AiChannelScope;
import com.jobproof.modules.aiconfig.domain.AiChannelStatus;
import com.jobproof.modules.aiconfig.infra.AiChannelEntity;
import com.jobproof.modules.aiconfig.infra.AiChannelHealthEntity;
import com.jobproof.modules.aiconfig.infra.AiChannelHealthJpaRepository;
import com.jobproof.modules.aiconfig.infra.AiChannelJpaRepository;
import com.jobproof.modules.aiconfig.infra.AiChannelModelJpaRepository;
import com.jobproof.modules.aiconfig.infra.AiModelJpaRepository;
import com.jobproof.modules.aigateway.application.AiChannelInspector;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.CapabilityDetection;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Channel;
import com.jobproof.modules.aigateway.domain.AiProtocol;
import com.jobproof.modules.aigateway.security.ApiKeyCipher;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AiAdminChannelService(AiChannelJpaRepository channels, AiChannelHealthJpaRepository health,
            AiChannelModelJpaRepository mappings, AiModelJpaRepository models, AiChannelInspector inspector,
            ApiKeyCipher cipher, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.channels = channels;
        this.health = health;
        this.mappings = mappings;
        this.models = models;
        this.inspector = inspector;
        this.cipher = cipher;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<SystemChannelView> list(CurrentAccount current) {
        assertAdmin(current);
        return channels.findAll().stream().filter(value -> value.getScope() == AiChannelScope.SYSTEM)
                .map(this::view).toList();
    }

    @Transactional
    public SystemChannelView create(CurrentAccount current, CreateSystemChannelCommand command) {
        assertAdmin(current);
        String name = required(command.name(), "通道名称必填");
        String provider = required(command.providerCode(), "供应商代码必填");
        String baseUrl = normalizeBase(command.baseUrl());
        AiProtocol protocol = protocol(command.protocol());
        String key = required(command.apiKey(), "API Key 必填");
        if (key.contains("****")) throw AppException.user("AI_VALIDATION_FAILED", "掩码不能作为 API Key");
        Instant now = Instant.now();
        AiChannelEntity entity = new AiChannelEntity();
        entity.setId(Ids.newId());
        entity.setScope(AiChannelScope.SYSTEM);
        entity.setOwnerAccountId(null);
        entity.setName(name);
        entity.setProviderCode(provider);
        entity.setBaseUrl(baseUrl);
        entity.setProtocol(protocol.name());
        entity.setApiKeyCiphertext(cipher.encrypt(key));
        entity.setApiKeyMasked(AiUserChannelService.mask(key));
        entity.setChannelRate(BigDecimal.ONE);
        entity.setStatus(AiChannelStatus.DRAFT);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return view(channels.save(entity));
    }

    @Transactional
    public CapabilityView capabilityTest(CurrentAccount current, String channelId, String providerModel,
            String platformModelCode) {
        assertAdmin(current);
        AiChannelEntity entity = requireSystem(channelId);
        CapabilityDetection result = inspector.inspectCapabilities(new Channel(entity.getId(),
                protocol(entity.getProtocol()), URI.create(entity.getBaseUrl()), entity.getApiKeyCiphertext(),
                0, 100, true), providerModel);
        Instant now = Instant.now();
        entity.setBaseUrl(result.normalizedBaseUrl());
        entity.setUpdatedAt(now);
        channels.save(entity);
        AiChannelHealthEntity snapshot = health.findByChannelId(channelId).orElseGet(AiChannelHealthEntity::new);
        if (snapshot.getId() == null) {
            snapshot.setId(Ids.newId());
            snapshot.setChannelId(channelId);
            snapshot.setConsecutiveFailures(0);
        }
        snapshot.setStatus(result.passed() ? "HEALTHY" : "UNHEALTHY");
        snapshot.setLatencyMs(null);
        snapshot.setConsecutiveFailures(result.passed() ? 0 : snapshot.getConsecutiveFailures() + 1);
        snapshot.setFailureCode(result.failureCode());
        snapshot.setCheckedAt(now);
        health.save(snapshot);
        String id = Ids.newId();
        jdbc.update("INSERT INTO ai_channel_capability_test(id,channel_id,normalized_base_url,models_json,tested_model,authenticated,model_discovery,plain_response,streaming_response,structured_response,status,failure_code,detail,checked_by,checked_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, channelId, result.normalizedBaseUrl(), json(result.models()), result.testedModel(),
                result.authenticated(), result.modelDiscovery(), result.plainResponse(), result.streamingResponse(),
                result.structuredResponse(), result.passed() ? "PASSED" : "FAILED", result.failureCode(),
                result.detail(), current.accountId(), now);
        if (result.passed()) mapModel(channelId, required(platformModelCode, "平台模型代码必填"), result.testedModel(), now);
        return new CapabilityView(id, channelId, result.normalizedBaseUrl(), result.models(), result.testedModel(),
                result.authenticated(), result.modelDiscovery(), result.plainResponse(), result.streamingResponse(),
                result.structuredResponse(), result.passed() ? "PASSED" : "FAILED", result.failureCode(),
                result.detail(), now);
    }

    @Transactional
    public SystemChannelView activate(CurrentAccount current, String channelId, int expectedVersion) {
        assertAdmin(current);
        AiChannelEntity entity = requireSystem(channelId);
        if (entity.getVersionNo() != expectedVersion) {
            throw AppException.conflict("AI_VERSION_CONFLICT", "通道已更新，请刷新后重试");
        }
        String latest = jdbc.query("SELECT status FROM ai_channel_capability_test WHERE channel_id=? ORDER BY checked_at DESC",
                (rs, n) -> rs.getString("status"), channelId).stream().findFirst().orElse("MISSING");
        if (!"PASSED".equals(latest)) {
            throw AppException.conflict("AI_CAPABILITY_GATE_FAILED", "五项能力测试全部通过后才能启用系统通道");
        }
        entity.setStatus(AiChannelStatus.ACTIVE);
        entity.setUpdatedAt(Instant.now());
        return view(channels.save(entity));
    }

    @Transactional
    public SystemChannelView disable(CurrentAccount current, String channelId, int expectedVersion) {
        assertAdmin(current);
        AiChannelEntity entity = requireSystem(channelId);
        if (entity.getVersionNo() != expectedVersion) {
            throw AppException.conflict("AI_VERSION_CONFLICT", "通道已更新，请刷新后重试");
        }
        entity.setStatus(AiChannelStatus.DISABLED);
        entity.setUpdatedAt(Instant.now());
        return view(channels.save(entity));
    }

    private void mapModel(String channelId, String platformModelCode, String providerModel, Instant now) {
        var model = models.findByModelCode(platformModelCode)
                .orElseThrow(() -> AppException.user("AI_MODEL_NOT_FOUND", "平台模型不存在"));
        if (!"ACTIVE".equals(model.getStatus())) throw AppException.conflict("AI_MODEL_DISABLED", "平台模型未启用");
        if (mappings.findByChannelIdAndModelId(channelId, model.getId()).isPresent()) {
            jdbc.update("UPDATE ai_channel_model SET provider_model_code=?,status='ACTIVE',hidden=0,updated_at=? WHERE channel_id=? AND model_id=?",
                    providerModel, now, channelId, model.getId());
        } else {
            jdbc.update("INSERT INTO ai_channel_model(id,channel_id,model_id,provider_model_code,status,hidden,priority,version_no,created_at,updated_at) VALUES(?,?,?,?, 'ACTIVE',0,100,0,?,?)",
                    Ids.newId(), channelId, model.getId(), providerModel, now, now);
        }
    }

    private SystemChannelView view(AiChannelEntity entity) {
        AiChannelHealthEntity snapshot = health.findByChannelId(entity.getId()).orElse(null);
        return new SystemChannelView(entity.getId(), entity.getName(), entity.getProviderCode(), entity.getBaseUrl(),
                entity.getProtocol(), entity.getApiKeyMasked(), entity.getStatus().name(),
                snapshot == null ? "UNKNOWN" : snapshot.getStatus(), entity.getVersionNo(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AiChannelEntity requireSystem(String id) {
        AiChannelEntity entity = channels.findById(id)
                .orElseThrow(() -> AppException.user("AI_CHANNEL_NOT_FOUND", "系统通道不存在"));
        if (entity.getScope() != AiChannelScope.SYSTEM) {
            throw AppException.forbidden("AI_CHANNEL_FORBIDDEN", "只能管理系统通道");
        }
        return entity;
    }

    private static String normalizeBase(String value) {
        String clean = required(value, "HTTPS Base URL 必填").replaceAll("/+$", "");
        URI uri;
        try { uri = URI.create(clean); }
        catch (RuntimeException exception) { throw AppException.user("AI_VALIDATION_FAILED", "Base URL 格式无效"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw AppException.user("AI_VALIDATION_FAILED", "Base URL 必须是无用户信息的 HTTPS 地址");
        }
        return clean;
    }

    private static AiProtocol protocol(String value) {
        try { return AiProtocol.valueOf(required(value, "协议必填").toUpperCase()); }
        catch (RuntimeException exception) { throw AppException.user("AI_VALIDATION_FAILED", "AI 协议不受支持"); }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw AppException.user("AI_VALIDATION_FAILED", message);
        return value.trim();
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static void assertAdmin(CurrentAccount current) {
        if (current == null || !"ADMIN".equals(current.role())) {
            throw AppException.forbidden("ADMIN_REQUIRED", "仅管理员可以管理系统 AI 通道");
        }
    }

    public record CreateSystemChannelCommand(String name, String providerCode, String baseUrl, String apiKey,
            String protocol) {}
    public record SystemChannelView(String id, String name, String providerCode, String baseUrl, String protocol,
            String apiKeyMasked, String status, String healthStatus, int versionNo, Instant createdAt,
            Instant updatedAt) {}
    public record CapabilityView(String id, String channelId, String normalizedBaseUrl, List<String> models,
            String testedModel, boolean authenticated, boolean modelDiscovery, boolean plainResponse,
            boolean streamingResponse, boolean structuredResponse, String status, String failureCode,
            String detail, Instant checkedAt) {}
}
