package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.shared.mail.MailTransport;
import jakarta.servlet.http.Cookie;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 后台邮件设置 (docs/phase2/05). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MailSettingsIT {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountJpaRepository accounts;
    @Autowired MailTransport transport;

    @Test
    void anAdministratorSavesTestsAndClearsTheMailAccount() throws Exception {
        Cookie admin = account("mail-admin", true);
        Cookie seeker = account("mail-seeker", false);
        mockMvc.perform(get("/api/v1/admin/mail-settings").cookie(seeker)).andExpect(status().isForbidden());

        JsonNode initial = data(mockMvc.perform(get("/api/v1/admin/mail-settings").cookie(admin)).andExpect(status().isOk()).andReturn());
        assertThat(initial.path("source").asText()).isEqualTo("NONE");
        assertThat(initial.path("canStoreSecrets").asBoolean()).isTrue();
        assertThat(initial.has("admin")).isFalse();

        rejected(admin, settings("smtp example.com", "ops@example.com", "secret", null), "MAIL_HOST_INVALID");
        Map<String, Object> qq = settings("smtp.qq.com", "other@qq.com", "secret", null);
        qq.put("username", "me@qq.com");
        rejected(admin, qq, "MAIL_FROM_MISMATCH");
        rejected(admin, settings("smtp.example.com", "ops@example.com", "", null), "MAIL_PASSWORD_REQUIRED");

        JsonNode saved = save(admin, settings("SMTP.Example.com", "ops@example.com", "first-code", null));
        assertThat(saved.path("source").asText()).isEqualTo("ADMIN");
        assertThat(saved.path("notifications").asBoolean()).isTrue();
        assertThat(saved.path("admin").path("host").asText()).isEqualTo("smtp.example.com");
        assertThat(saved.path("admin").path("version").asInt()).isEqualTo(1);
        assertThat(saved.toString()).doesNotContain("first-code");
        assertThat(jdbc.queryForObject("SELECT password_ciphertext FROM mail_settings", String.class)).doesNotContain("first-code");

        MailTransport.Account account = transport.current().orElseThrow();
        assertThat(account.source()).isEqualTo("ADMIN");
        assertThat(((JavaMailSenderImpl) account.sender()).getPassword()).isEqualTo("first-code");

        // A blank password keeps the saved one; a stale version is refused.
        Map<String, Object> rename = settings("smtp.example.com", "ops@example.com", "", 1);
        rename.put("fromName", "求职证据官");
        assertThat(save(admin, rename).path("admin").path("fromName").asText()).isEqualTo("求职证据官");
        assertThat(((JavaMailSenderImpl) transport.current().orElseThrow().sender()).getPassword()).isEqualTo("first-code");
        rejected(admin, settings("smtp.example.com", "ops@example.com", "", 1), "VERSION_CONFLICT");

        // A server that is not listening: the test reports why, quickly, and the result is kept.
        int closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }
        Map<String, Object> local = settings("127.0.0.1", "ops@example.com", "", 2);
        local.put("port", closed);
        local.put("security", "NONE");
        save(admin, local);
        JsonNode test = data(mockMvc.perform(post("/api/v1/admin/mail-settings/test").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"me@example.com\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(test.path("ok").asBoolean()).isFalse();
        assertThat(test.path("message").asText()).contains("连接不上");
        mockMvc.perform(post("/api/v1/admin/mail-settings/test").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"me@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.reason").value("MAIL_TEST_TOO_OFTEN"));
        JsonNode reread = data(mockMvc.perform(get("/api/v1/admin/mail-settings").cookie(admin)).andReturn());
        assertThat(reread.path("lastTest").path("ok").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action LIKE 'MAIL_SETTINGS_%'", Integer.class)).isGreaterThanOrEqualTo(4);

        JsonNode cleared = data(mockMvc.perform(delete("/api/v1/admin/mail-settings").cookie(admin)).andExpect(status().isOk()).andReturn());
        assertThat(cleared.path("source").asText()).isEqualTo("NONE");
        assertThat(transport.current()).isEmpty();
    }

    private Map<String, Object> settings(String host, String from, String password, Integer expectedVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("host", host);
        body.put("port", 465);
        body.put("security", "SSL");
        body.put("username", from);
        body.put("password", password);
        body.put("fromAddress", from);
        body.put("fromName", "JobProof AI");
        body.put("notifications", true);
        body.put("expectedVersion", expectedVersion);
        return body;
    }

    private JsonNode save(Cookie admin, Map<String, Object> body) throws Exception {
        return data(mockMvc.perform(put("/api/v1/admin/mail-settings").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isOk()).andReturn());
    }

    private void rejected(Cookie admin, Map<String, Object> body, String reason) throws Exception {
        mockMvc.perform(put("/api/v1/admin/mail-settings").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.error.reason").value(reason));
    }

    private Cookie account(String prefix, boolean admin) throws Exception {
        String email = prefix + "+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk());
        if (admin) {
            AccountEntity entity = accounts.findByEmail(email).orElseThrow();
            entity.setRole("ADMIN");
            accounts.saveAndFlush(entity);
        }
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk()).andReturn();
        return login.getResponse().getCookie("jobproof_session");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
