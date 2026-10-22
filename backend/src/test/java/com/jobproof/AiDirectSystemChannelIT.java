package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.*;
import com.jobproof.modules.aigateway.port.AiChannelPort;
import com.jobproof.modules.aigateway.security.SsrfGuard;
import com.jobproof.support.AiProviderStub;
import com.jobproof.modules.site.application.SiteCapabilitiesService;
import com.jobproof.modules.aiconfig.application.DevAiChannelInitializer;
import com.jobproof.modules.aigateway.security.ApiKeyCipher;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"jobproof.dev.admin.enabled=true", "jobproof.dev.admin.alias=admin",
        "jobproof.dev.admin.email=admin@jobproof.local", "jobproof.dev.admin.password=admin", "jobproof.ai.gateway.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AiDirectSystemChannelIT.ProviderConfiguration.class)
@Transactional
class AiDirectSystemChannelIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AiChannelPort channels;
    @Autowired AiGatewayService gateway;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiProviderStub provider;
    @Autowired SiteCapabilitiesService site;
    @Autowired EntityManager entities;
    @Autowired ApiKeyCipher cipher;

    @TestConfiguration
    static class ProviderConfiguration {
        @Bean @Primary AiProviderStub testProvider() { return new AiProviderStub(); }
        @Bean @Primary SsrfGuard testDns() {
            return new SsrfGuard(host -> new InetAddress[] { InetAddress.getByName("93.184.216.34") });
        }
    }

    @Test
    void createDiscoverActivateAndInvokeWithoutProviderCodeCatalogOrMapping() throws Exception {
        Cookie admin = login();
        String id = create(admin, "");
        assertThat(channels.findEligible("seeker", "auto")).isEmpty();
        JsonNode result = data(mvc.perform(post("/api/v1/admin/ai/channels/{id}/capability-test", id).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PASSED"))
                .andExpect(jsonPath("$.data.testedModel").value("working-model")).andReturn().getResponse().getContentAsString());
        JsonNode saved = channel(admin, id);
        assertThat(saved.path("selectedModel").asText("")).isEmpty();
        assertThat(saved.path("capability").path("status").asText()).isEqualTo("PASSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_channel_model WHERE channel_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_model WHERE model_code='working-model'", Integer.class)).isZero();
        assertThat(channels.findEligible("seeker", "auto")).isEmpty();
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/activate", id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + saved.path("versionNo").asInt() + "}"))
                .andExpect(status().isOk());

        assertThat(channels.findEligible("seeker", "auto")).extracting(Channel::providerModel).containsExactly("working-model");
        assertThat(site.capabilities().ai()).isEqualTo(new SiteCapabilitiesService.AiReadiness(true, true, true));
        assertThat(channels.findEligible("seeker", "working-model")).hasSize(1);
        assertThat(channels.findEligible("seeker", "different-explicit-model")).isEmpty();
        Response response = gateway.execute("seeker", new Request("auto", List.of(new Message("user", "hello")), false, Map.of()));
        assertThat(response.model()).isEqualTo("working-model");
        assertThat(response.text()).isEqualTo("OK");
        var deltas = new ArrayList<String>();
        Response streaming = gateway.executeStreaming("seeker", new Request("auto", List.of(new Message("user", "hello")), true, Map.of()), deltas::add);
        assertThat(streaming.model()).isEqualTo("working-model");
        assertThat(deltas).containsExactly("OK");

        saved = channel(admin, id);
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/disable", id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + saved.path("versionNo").asInt() + "}"))
                .andExpect(status().isOk());
        assertThat(channels.findEligible("seeker", "auto")).isEmpty();
    }

    @Test
    void blankProviderIsAcceptedAndFailedTestCannotActivate() throws Exception {
        Cookie admin = login();
        String id = create(admin, ",\"providerCode\":\"   \"");
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/capability-test", id).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"providerModel\":\"embedding-only\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FAILED"));
        JsonNode saved = channel(admin, id);
        assertThat(saved.path("selectedModel").asText("")).isEmpty();
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/activate", id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + saved.path("versionNo").asInt() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.reason").value("AI_CAPABILITY_GATE_FAILED"));
    }

    @Test
    void explicitLegacyAliasesKeepWorkingWithoutRoutingOtherModelsByProviderName() throws Exception {
        Cookie admin = login();
        String id = create(admin, ",\"providerCode\":\"QWEN\"");
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/capability-test", id).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"providerModel\":\"working-model\",\"platformModelCode\":\"qwen-plus\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PASSED"));
        JsonNode saved = channel(admin, id);
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/activate", id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + saved.path("versionNo").asInt() + "}"))
                .andExpect(status().isOk());
        assertThat(channels.findEligible("seeker", "qwen-plus")).extracting(Channel::providerModel).containsExactly("working-model");
        assertThat(channels.findEligible("seeker", "qwen-max")).isEmpty();
        entities.flush();
        jdbc.update("UPDATE ai_channel_model SET hidden=1 WHERE channel_id=?", id);
        entities.clear();
        assertThat(channels.findEligible("seeker", "qwen-plus")).isEmpty();
        assertThat(channels.findEligible("seeker", "auto")).hasSize(1);
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/capability-test", id).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"providerModel\":\"embedding-only\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FAILED"));
        saved = channel(admin, id);
        assertThat(saved.path("selectedModel").asText()).isEqualTo("working-model");
        assertThat(saved.path("capability").path("status").asText()).isEqualTo("FAILED");
    }

    @Test
    void testingAnotherModelKeepsTheAppliedModelUntilExplicitActivation() throws Exception {
        Cookie admin = login();
        String id = create(admin, "");
        testModel(admin, id, "working-model");
        activateModel(admin, id, "working-model", 200);
        testModel(admin, id, "unused-model");
        JsonNode saved = channel(admin, id);
        assertThat(saved.path("selectedModel").asText()).isEqualTo("working-model");
        assertThat(saved.path("capability").path("testedModel").asText()).isEqualTo("unused-model");
        assertThat(gateway.execute("seeker", new Request("auto", List.of(new Message("user", "hello")), false, Map.of())).model())
                .isEqualTo("working-model");
        activateModel(admin, id, "unused-model", 200);
        assertThat(gateway.execute("seeker", new Request("auto", List.of(new Message("user", "hello")), false, Map.of())).model())
                .isEqualTo("unused-model");
        assertThat(channel(admin, id).path("selectedModel").asText()).isEqualTo("unused-model");
    }

    @Test
    void activationRejectsUnverifiedModelFailedModelAndStaleVersion() throws Exception {
        Cookie admin = login();
        String id = create(admin, "");
        testModel(admin, id, "working-model");
        activateModel(admin, id, "unused-model", 409);
        activateModel(admin, id, "working-model", 200);
        testModel(admin, id, "embedding-only");
        activateModel(admin, id, "embedding-only", 409);
        assertThat(channel(admin, id).path("healthStatus").asText()).isEqualTo("HEALTHY");
        assertThat(channel(admin, id).path("selectedModel").asText()).isEqualTo("working-model");
        testModel(admin, id, "unused-model");
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/activate", id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerModel\":\"unused-model\",\"expectedVersion\":-0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.reason").value("AI_VERSION_CONFLICT"));
        assertThat(channel(admin, id).path("selectedModel").asText()).isEqualTo("working-model");
    }

    @Test
    void restartingDevInitializationPreservesDisabledChannelAndAppliedModel() throws Exception {
        var initializer = new DevAiChannelInitializer(jdbc, cipher, "Restart channel", "https://provider.example/v1",
                "test-only-key", "working-model", "OPENAI_CHAT");
        initializer.run(null);
        String id = jdbc.queryForObject("SELECT id FROM ai_channel WHERE name='Restart channel'", String.class);
        String appliedCipher=cipher.encrypt("synthetic-admin-replacement");
        jdbc.update("UPDATE ai_channel SET status='DISABLED', selected_model='unused-model',base_url='https://applied.example/v2',api_key_ciphertext=?,protocol='OPENAI_RESPONSES',config_version=5 WHERE id=?", appliedCipher,id);
        jdbc.update("UPDATE ai_channel_model SET provider_model_code='unused-model',status='DISABLED',hidden=1 WHERE channel_id=?",id);
        initializer.run(null);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_channel WHERE id=?", String.class, id)).isEqualTo("DISABLED");
        assertThat(jdbc.queryForObject("SELECT selected_model FROM ai_channel WHERE id=?", String.class, id)).isEqualTo("unused-model");
        assertThat(jdbc.queryForObject("SELECT base_url FROM ai_channel WHERE id=?",String.class,id)).isEqualTo("https://applied.example/v2");
        assertThat(jdbc.queryForObject("SELECT api_key_ciphertext FROM ai_channel WHERE id=?",String.class,id)).isEqualTo(appliedCipher);
        assertThat(jdbc.queryForObject("SELECT protocol FROM ai_channel WHERE id=?",String.class,id)).isEqualTo("OPENAI_RESPONSES");
        assertThat(jdbc.queryForObject("SELECT config_version FROM ai_channel WHERE id=?",Integer.class,id)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT provider_model_code FROM ai_channel_model WHERE channel_id=?",String.class,id)).isEqualTo("unused-model");
        assertThat(jdbc.queryForObject("SELECT hidden FROM ai_channel_model WHERE channel_id=?",Boolean.class,id)).isTrue();
    }

    @Test
    void testingLegacyAliasAlsoWaitsForActivationBeforeChangingRouting() throws Exception {
        Cookie admin = login();
        String id = create(admin, "");
        for (String model : List.of("working-model", "unused-model")) {
            mvc.perform(post("/api/v1/admin/ai/channels/{id}/capability-test", id).cookie(admin)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                            "providerModel", model, "platformModelCode", "qwen-plus"))))
                    .andExpect(status().isOk());
            entities.flush();
            entities.clear();
            if (model.equals("unused-model")) {
                assertThat(channels.findEligible("seeker", "qwen-plus")).extracting(Channel::providerModel).containsExactly("working-model");
            }
            activateModel(admin, id, model, 200);
            entities.flush();
            entities.clear();
            assertThat(channels.findEligible("seeker", "qwen-plus")).extracting(Channel::providerModel).containsExactly(model);
        }
    }

    @Test void oldAliasCannotRouteUntestedModelAfterAppliedModelChanges() throws Exception {
        Cookie admin=login(); String id=create(admin,"");
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/capability-test",id).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("providerModel","working-model","platformModelCode","qwen-plus")))).andExpect(status().isOk());
        activateModel(admin,id,"working-model",200);
        mvc.perform(put("/api/v1/admin/ai/channels/{id}",id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name","Direct channel","baseUrl","https://replacement.example/v1",
                        "apiKey","synthetic-replacement-key","protocol","OPENAI_CHAT","selectedModel","unused-model",
                        "expectedVersion",channel(admin,id).path("versionNo").asInt())))).andExpect(status().isOk());
        testModel(admin,id,"unused-model");activateModel(admin,id,"unused-model",200);
        entities.flush();entities.clear();
        assertThat(channels.findEligible("seeker","auto")).extracting(Channel::providerModel).containsExactly("unused-model");
        assertThat(channels.findEligible("seeker","qwen-plus")).isEmpty();
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/capability-test",id).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("providerModel","unused-model","platformModelCode","qwen-plus")))).andExpect(status().isOk());
        activateModel(admin,id,"unused-model",200);
        entities.flush();entities.clear();
        assertThat(channels.findEligible("seeker","qwen-plus")).extracting(Channel::providerModel).containsExactly("unused-model");
    }

    private void testModel(Cookie admin, String id, String model) throws Exception {
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/capability-test", id).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("providerModel", model))))
                .andExpect(status().isOk());
    }

    private void activateModel(Cookie admin, String id, String model, int expectedStatus) throws Exception {
        int version = channel(admin, id).path("versionNo").asInt();
        mvc.perform(post("/api/v1/admin/ai/channels/{id}/activate", id).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("providerModel", model, "expectedVersion", version))))
                .andExpect(status().is(expectedStatus));
    }

    private Cookie login() throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("jobproof_session");
    }

    private String create(Cookie admin, String extra) throws Exception {
        return data(mvc.perform(post("/api/v1/admin/ai/channels").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Direct channel\",\"baseUrl\":\"https://provider.example/v1\",\"apiKey\":\"test-only-key\",\"protocol\":\"OPENAI_CHAT\"" + extra + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
    }

    private JsonNode channel(Cookie admin, String id) throws Exception {
        JsonNode list = data(mvc.perform(get("/api/v1/admin/ai/channels").cookie(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode item : list) if (item.path("id").asText().equals(id)) return item;
        throw new AssertionError("Channel missing from list");
    }

    private JsonNode data(String body) throws Exception { return json.readTree(body).path("data"); }
}
