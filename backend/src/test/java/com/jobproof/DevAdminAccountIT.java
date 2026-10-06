package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "jobproof.dev.admin.enabled=true",
        "jobproof.dev.admin.alias=admin",
        "jobproof.dev.admin.email=admin@jobproof.local",
        "jobproof.dev.admin.password=admin"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevAdminAccountIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void localAliasLogsIntoAnAdminAccountWithoutWeakeningNormalRegistration() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin","password":"wrong"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("INVALID_CREDENTIALS"));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin","password":"admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@jobproof.local"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andReturn();
        Cookie session = login.getResponse().getCookie("jobproof_session");
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(get("/api/v1/admin/resume-templates/families").cookie(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new-user@example.com","password":"admin"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("PASSWORD_TOO_WEAK"));
    }

    @Test
    void onlyAdminCreatesSystemChannelsAndActivationRequiresAllCapabilities() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie admin = login.getResponse().getCookie("jobproof_session");
        MvcResult created = mockMvc.perform(post("/api/v1/admin/ai/channels").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"验收通道","providerCode":"QWEN","baseUrl":"https://api.example.com/","apiKey":"test-only-secret","protocol":"OPENAI_CHAT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.apiKeyMasked").value("****cret"))
                .andReturn();
        JsonNode channel = mapper.readTree(created.getResponse().getContentAsString()).path("data");
        mockMvc.perform(post("/api/v1/admin/ai/channels/{id}/activate", channel.path("id").asText())
                        .cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":%d}".formatted(channel.path("versionNo").asInt())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_CAPABILITY_GATE_FAILED"));

        String email = "system-channel-seeker@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Passw0rd!\"}".formatted(email)))
                .andExpect(status().isOk());
        Cookie seeker = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Passw0rd!\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("jobproof_session");
        mockMvc.perform(post("/api/v1/admin/ai/channels").cookie(seeker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"providerCode\":\"x\",\"baseUrl\":\"https://api.example.com\",\"apiKey\":\"secret\",\"protocol\":\"OPENAI_CHAT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.reason").value("ADMIN_REQUIRED"));
        mockMvc.perform(post("/api/v1/ai/channels/personal").cookie(seeker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"providerCode\":\"x\",\"baseUrl\":\"https://api.example.com\",\"apiKey\":\"secret\",\"protocol\":\"OPENAI_CHAT\",\"idempotencyKey\":\"one\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.reason").value("AI_PERSONAL_CHANNELS_DISABLED"));
    }
}
