package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
class PasswordResetIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void unknownEmailRequestDoesNotRevealExistence() throws Exception {
        String unknown = "ghost+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(unknown)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/internal/dev/mailbox/{email}", unknown))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("MAIL_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"123456","newPassword":"Passw0rd9!"}
                                """.formatted(unknown)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESET_CODE_INVALID"));
    }

    @Test
    void registeredRequestLooksTheSameUntilMailbox() throws Exception {
        String email = "reset-enum+" + System.nanoTime() + "@example.com";
        register(email, "Passw0rd!");
        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(readMailboxCode(email)).hasSize(6);
    }

    @Test
    void newRequestInvalidatesPreviousCodeAndSuccessIsOneTime() throws Exception {
        String email = "reset-once+" + System.nanoTime() + "@example.com";
        register(email, "Passw0rd!");
        requestReset(email);
        String firstCode = readMailboxCode(email);
        requestReset(email);
        String secondCode = readMailboxCode(email);
        assertThat(secondCode).isNotEqualTo(firstCode);

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"Passw0rd2!"}
                                """.formatted(email, firstCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESET_CODE_INVALID"));

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"Passw0rd2!"}
                                """.formatted(email, secondCode)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"Passw0rd3!"}
                                """.formatted(email, secondCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESET_CODE_INVALID"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd2!"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void fiveWrongAttemptsLockTheCode() throws Exception {
        String email = "reset-lock+" + System.nanoTime() + "@example.com";
        register(email, "Passw0rd!");
        requestReset(email);
        String code = readMailboxCode(email);
        String wrong = "000000".equals(code) ? "000001" : "000000";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","code":"%s","newPassword":"Passw0rd2!"}
                                    """.formatted(email, wrong)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.reason").value("RESET_CODE_INVALID"));
        }

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"Passw0rd2!"}
                                """.formatted(email, code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESET_CODE_INVALID"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    void resetRevokesEverySessionAndWeakPasswordKeepsThem() throws Exception {
        String email = "reset-sess+" + System.nanoTime() + "@example.com";
        register(email, "Passw0rd!");
        Cookie first = login(email, "Passw0rd!");
        Cookie second = login(email, "Passw0rd!");
        requestReset(email);
        String code = readMailboxCode(email);

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"short"}
                                """.formatted(email, code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("PASSWORD_TOO_WEAK"));
        mockMvc.perform(get("/api/v1/me").cookie(first)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").cookie(second)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"Passw0rd9!"}
                                """.formatted(email, code)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").cookie(first)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").cookie(second)).andExpect(status().isUnauthorized());
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk());
    }

    private void requestReset(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    private String readMailboxCode(String email) throws Exception {
        MvcResult mail = mockMvc.perform(get("/internal/dev/mailbox/{email}", email))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode mailJson = objectMapper.readTree(mail.getResponse().getContentAsString());
        return mailJson.path("data").path("code").asText();
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("jobproof_session");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
