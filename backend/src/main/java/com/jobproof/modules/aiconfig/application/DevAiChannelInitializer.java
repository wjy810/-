package com.jobproof.modules.aiconfig.application;

import com.jobproof.modules.aigateway.domain.AiProtocol;
import com.jobproof.modules.aigateway.security.ApiKeyCipher;
import com.jobproof.shared.id.Ids;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local development only: keeps one ACTIVE platform channel pointing at the configured OpenAI-compatible
 * endpoint, plus the platform model the features request, so AI features work right after startup. The
 * admin capability-test gate is skipped here (it still applies to channels created in the admin console),
 * and the channel can be disabled or re-tested from 后台 › AI 通道 like any other.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "jobproof.dev.ai-channel.enabled", havingValue = "true")
public class DevAiChannelInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevAiChannelInitializer.class);
    private static final String PROVIDER = "OPENAI_COMPAT";

    private final JdbcTemplate jdbc;
    private final ApiKeyCipher cipher;
    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String protocol;

    public DevAiChannelInitializer(JdbcTemplate jdbc, ApiKeyCipher cipher,
            @Value("${jobproof.dev.ai-channel.name:开发调试通道}") String name,
            @Value("${jobproof.dev.ai-channel.base-url}") String baseUrl,
            @Value("${jobproof.dev.ai-channel.api-key}") String apiKey,
            @Value("${jobproof.dev.ai-channel.model}") String model,
            @Value("${jobproof.dev.ai-channel.protocol:OPENAI_CHAT}") String protocol) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.name = name.trim();
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.protocol = AiProtocol.valueOf(protocol.trim().toUpperCase(Locale.ROOT)).name();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (apiKey.isBlank() || model.isBlank() || !baseUrl.startsWith("https://")) {
            log.warn("dev AI channel skipped: base-url must be https and api-key and model must be set");
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        String modelId = ensureModel(now);
        String channelId = ensureChannel(now);
        List<String> mapping = jdbc.queryForList("SELECT id FROM ai_channel_model WHERE channel_id=? AND model_id=?",
                String.class, channelId, modelId);
        if (mapping.isEmpty()) {
            jdbc.update("INSERT INTO ai_channel_model(id,channel_id,model_id,provider_model_code,status,hidden,priority,version_no,created_at,updated_at) VALUES(?,?,?,?,'ACTIVE',0,100,0,?,?)",
                    Ids.newId(), channelId, modelId, model, now, now);
        } else {
            jdbc.update("UPDATE ai_channel_model SET provider_model_code=?,status='ACTIVE',hidden=0,updated_at=? WHERE id=?",
                    model, now, mapping.get(0));
        }
        log.info("dev AI channel ready name={} model={} baseUrl={}", name, model, baseUrl);
    }

    private String ensureModel(Timestamp now) {
        List<String> ids = jdbc.queryForList("SELECT id FROM ai_model WHERE model_code=?", String.class, model);
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE ai_model SET status='ACTIVE',updated_at=? WHERE id=?", now, ids.get(0));
            return ids.get(0);
        }
        String id = Ids.newId();
        jdbc.update("INSERT INTO ai_model(id,model_code,display_name,provider_code,model_type,billing_mode,unit,status,version_no,created_at,updated_at) VALUES(?,?,?,?,'TEXT','TOKEN','MILLION_TOKENS','ACTIVE',0,?,?)",
                id, model, model, PROVIDER, now, now);
        jdbc.update("INSERT INTO ai_model_pricing(id,model_id,fixed_price,model_rate,cache_rate,completion_rate,currency,status,effective_from,effective_to,version_no,created_by,created_at) VALUES(?,?,0,0,0,0,'USD','ACTIVE',?,NULL,0,NULL,?)",
                Ids.newId(), id, now, now);
        return id;
    }

    private String ensureChannel(Timestamp now) {
        List<String> ids = jdbc.queryForList("SELECT id FROM ai_channel WHERE scope='SYSTEM' AND name=?", String.class, name);
        String ciphertext = cipher.encrypt(apiKey);
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE ai_channel SET provider_code=?,base_url=?,protocol=?,api_key_ciphertext=?,api_key_masked=?,status='ACTIVE',archived_at=NULL,updated_at=? WHERE id=?",
                    PROVIDER, baseUrl, protocol, ciphertext, AiUserChannelService.mask(apiKey), now, ids.get(0));
            return ids.get(0);
        }
        String id = Ids.newId();
        jdbc.update("INSERT INTO ai_channel(id,scope,owner_account_id,name,provider_code,base_url,protocol,api_key_ciphertext,api_key_masked,channel_rate,status,version_no,created_at,updated_at) VALUES(?,'SYSTEM',NULL,?,?,?,?,?,?,1,'ACTIVE',0,?,?)",
                id, name, PROVIDER, baseUrl, protocol, ciphertext, AiUserChannelService.mask(apiKey), now, now);
        return id;
    }
}
