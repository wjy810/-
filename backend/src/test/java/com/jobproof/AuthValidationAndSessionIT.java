package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class AuthValidationAndSessionIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void registerAndLoginReturnUserCorrectableErrorCodes() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"Passw0rd!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.category").value("USER_CORRECTABLE"))
                .andExpect(jsonPath("$.error.reason").value("EMAIL_INVALID"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"weak+%s@example.com","password":"short"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("PASSWORD_TOO_WEAK"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody-%s@example.com","password":"Passw0rd!"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.category").value("USER_CORRECTABLE"))
                .andExpect(jsonPath("$.error.reason").value("INVALID_CREDENTIALS"));
    }

    @Test
    void validationErrorsNameTheFieldsAndCarryTheRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-Id", "trace-validation-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"someone@example.com","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.password").value("必填"))
                .andExpect(jsonPath("$.error.requestId").value("trace-validation-01"))
                .andExpect(header().string("X-Request-Id", "trace-validation-01"));
    }

    @Test
    void duplicateRegisterReturnsConflictAlreadyRegisteredNotSilentAndNot500() throws Exception {
        String email = "dup+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd2!"}
                                """.formatted(email.toUpperCase(java.util.Locale.ROOT))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.category").value("CONFLICT"))
                .andExpect(jsonPath("$.error.reason").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.error.message").value("该邮箱已注册"));
    }

    @Test
    void concurrentDuplicateRegisterReturnsConflictAlreadyRegisteredNot500() throws Exception {
        String email = "race+" + System.nanoTime() + "@example.com";
        String payload = """
                {"email":"%s","password":"Passw0rd!"}
                """.formatted(email);
        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < racers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/v1/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn();
                }));
            }
            start.countDown();
            int ok = 0;
            int conflict = 0;
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(20, TimeUnit.SECONDS);
                int status = result.getResponse().getStatus();
                String body = result.getResponse().getContentAsString();
                assertThat(status).isNotEqualTo(500);
                assertThat(body).doesNotContain("SYSTEM_FAILURE");
                if (status == 200) {
                    ok++;
                    continue;
                }
                assertThat(status).isEqualTo(409);
                assertThat(body).contains("EMAIL_ALREADY_REGISTERED");
                assertThat(body).contains("该邮箱已注册");
                assertThat(body).contains("CONFLICT");
                conflict++;
            }
            assertThat(ok).isGreaterThanOrEqualTo(1);
            assertThat(ok + conflict).isEqualTo(racers);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void loginUnknownEmailAndWrongPasswordAreIndistinguishable() throws Exception {
        String known = "known+" + System.nanoTime() + "@example.com";
        String unknown = "unknown+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(known)))
                .andExpect(status().isOk());

        assertLoginCredentialsRejected(unknown, "Passw0rd!");
        assertLoginCredentialsRejected(known, "WrongPass1!");
    }

    private void assertLoginCredentialsRejected(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.category").value("USER_CORRECTABLE"))
                .andExpect(jsonPath("$.error.reason").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("邮箱、手机号或密码不正确"))
                .andExpect(result -> assertThat(result.getResponse().getCookie("jobproof_session")).isNull());
    }

    @Test
    void changeAndResetRejectWeakPasswordsWith400AndKeepSessions() throws Exception {
        String email = "pwdpolicy+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        Cookie session = login(email, "Passw0rd!");
        mockMvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Passw0rd!","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.category").value("USER_CORRECTABLE"))
                .andExpect(jsonPath("$.error.reason").value("PASSWORD_TOO_WEAK"));
        mockMvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"123456","newPassword":"short"}
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.category").value("USER_CORRECTABLE"))
                .andExpect(jsonPath("$.error.reason").value("PASSWORD_TOO_WEAK"));
        mockMvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"toolong+%s@example.com","password":"%s"}
                                """.formatted(System.nanoTime(), "x".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("PASSWORD_TOO_WEAK"));

        String sameAsEmail = "SameMail+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(sameAsEmail, sameAsEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("PASSWORD_TOO_WEAK"));
    }

    @Test
    void changePasswordRevokesEverySession() throws Exception {
        String email = "multi+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        Cookie first = login(email, "Passw0rd!");
        Cookie second = login(email, "Passw0rd!");
        assertThat(first.getValue()).isNotEqualTo(second.getValue());

        mockMvc.perform(get("/api/v1/me").cookie(first)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").cookie(second)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Passw0rd!","newPassword":"Passw0rd2!"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").cookie(first)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").cookie(second)).andExpect(status().isUnauthorized());
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
