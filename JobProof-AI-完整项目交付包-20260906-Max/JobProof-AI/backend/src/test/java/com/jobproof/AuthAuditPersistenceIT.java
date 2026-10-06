package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.audit.infra.AuditEventEntity;
import com.jobproof.modules.audit.infra.AuditEventJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAuditPersistenceIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuditEventJpaRepository audits;

    @Test
    void frozenAuthAndDataRightsAuditsPersistWithoutSecrets() throws Exception {
        String email = "audit-min+" + System.nanoTime() + "@example.com";
        String password1 = "Passw0rd!";
        String password2 = "Passw0rd2!";
        String password3 = "Passw0rd3!";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"WrongPass1!"}
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("INVALID_CREDENTIALS"));

        Cookie session = login(email, password1);
        String accountId = meId(session);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session))
                .andExpect(status().isOk());
        session = login(email, password1);

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(password1, password2)))
                .andExpect(status().isOk());
        session = login(email, password2);

        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk());
        MvcResult mail = mockMvc.perform(get("/internal/dev/mailbox/{email}", email))
                .andExpect(status().isOk())
                .andReturn();
        String code = objectMapper.readTree(mail.getResponse().getContentAsString()).path("data").path("code").asText();
        assertThat(code).hasSize(6);

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"000000","newPassword":"%s"}
                                """.formatted(email, password3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESET_CODE_INVALID"));

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"%s"}
                                """.formatted(email, code, password3)))
                .andExpect(status().isOk());
        Cookie afterReset = login(email, password3);

        mockMvc.perform(post("/api/v1/data-rights/exports")
                        .cookie(afterReset)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        MvcResult deletion = mockMvc.perform(post("/api/v1/data-rights/deletions")
                        .cookie(afterReset)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"ACCOUNT","confirmationAck":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn();
        String deletionId = objectMapper.readTree(deletion.getResponse().getContentAsString())
                .path("data").path("id").asText();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/data-rights/deletions/{id}", deletionId).cookie(afterReset))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_RESTRICTED")));

        List<String> actions = audits.findAll().stream()
                .filter(event -> accountId.equals(event.getActorId()))
                .map(AuditEventEntity::getAction)
                .toList();
        assertThat(actions).contains(
                "ACCOUNT_REGISTERED",
                "ACCOUNT_LOGIN",
                "ACCOUNT_LOGOUT",
                "PASSWORD_CHANGED",
                "PASSWORD_RESET_REQUESTED",
                "PASSWORD_RESET_CONFIRMED",
                "EXPORT_REQUESTED",
                "DELETION_REQUESTED",
                "DELETION_PROGRESS");
        assertThat(actions.stream().filter("ACCOUNT_LOGIN"::equals).count()).isEqualTo(4);
        assertThat(actions.stream().filter("PASSWORD_RESET_CONFIRMED"::equals).count()).isEqualTo(1);

        for (AuditEventEntity event : audits.findAll()) {
            if (!accountId.equals(event.getActorId())) {
                continue;
            }
            assertThat(event.getSummary())
                    .doesNotContain(password1, password2, password3, code, "WrongPass1!", "000000");
        }
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = login.getResponse().getCookie("jobproof_session");
        assertThat(session).isNotNull();
        return session;
    }

    private String meId(Cookie session) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(me.getResponse().getContentAsString()).path("data");
        String id = data.path("id").asText();
        assertThat(id).isNotBlank();
        return id;
    }
}
