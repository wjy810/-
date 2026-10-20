package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.modules.identity.domain.VerificationPurpose;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 绑定联系方式与账号安全通知 (docs/phase2/11). */
@SpringBootTest(properties = {
        "jobproof.verification.enabled=true",
        "jobproof.verification.email-provider=dev",
        "jobproof.verification.sms-provider=dev",
        "jobproof.verification.sms-ip-hourly-limit=100",
        "jobproof.verification.email-ip-hourly-limit=100",
        "jobproof.policies.registration-enabled=true",
        "jobproof.notification.email.provider=dev"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSecurityIT {
    private static final String WINDOWS_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36";
    private static final String IPHONE_SAFARI = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aPhoneAccountBindsAnEmailThenChangesItAndHearsAboutBothAtTheRightAddress() throws Exception {
        String phone = "139" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Cookie session = register(VerificationChannel.SMS, phone, IPHONE_SAFARI);
        String accountId = data(mockMvc.perform(get("/api/v1/me").cookie(session)).andReturn()).path("id").asText();
        mockMvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(jsonPath("$.data.phoneVerified").value(true))
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        // The new address proves itself with a code; the user proves themselves with the password.
        String email = "bind+" + System.nanoTime() + "@example.com";
        String token = verifiedToken(VerificationChannel.EMAIL, email, VerificationPurpose.CHANGE_CONTACT);
        bind(session, VerificationChannel.EMAIL, email, token, "WrongPass1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("INVALID_CREDENTIALS"));
        bind(session, VerificationChannel.EMAIL, email, token, "Passw0rd!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.emailVerified").value(true));
        assertThat(securityTitles(accountId)).contains("已绑定邮箱");
        login(email, IPHONE_SAFARI).andExpect(status().isOk());
        // Notices may go out by email now that there is a verified address.
        mockMvc.perform(get("/api/v1/notification-preferences").cookie(session))
                .andExpect(jsonPath("$.data.emailAddress").value(org.hamcrest.Matchers.endsWith("@example.com")));

        // Changing it warns the old address, where the owner would see a change they did not make.
        String changed = "bind-new+" + System.nanoTime() + "@example.com";
        bind(session, VerificationChannel.EMAIL, changed,
                verifiedToken(VerificationChannel.EMAIL, changed, VerificationPurpose.CHANGE_CONTACT), "Passw0rd!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(changed));
        assertThat(securityTitles(accountId)).contains("邮箱已更换");
        assertThat(jdbc.queryForList("SELECT to_address FROM notification_emails WHERE account_id=? AND type='SECURITY' AND subject LIKE '%邮箱已更换%'",
                String.class, accountId)).containsExactly(email);
        login(email, IPHONE_SAFARI).andExpect(status().isBadRequest());
        login(changed, IPHONE_SAFARI).andExpect(status().isOk());

        // Someone else's address cannot be taken over.
        String other = "taken+" + System.nanoTime() + "@example.com";
        register(VerificationChannel.EMAIL, other, IPHONE_SAFARI);
        bind(session, VerificationChannel.EMAIL, other,
                verifiedToken(VerificationChannel.EMAIL, other, VerificationPurpose.CHANGE_CONTACT), "Passw0rd!")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("CONTACT_IN_USE"));
        // A code for another purpose does not bind.
        String recovery = "recovery+" + System.nanoTime() + "@example.com";
        bind(session, VerificationChannel.EMAIL, recovery,
                verifiedToken(VerificationChannel.EMAIL, recovery, VerificationPurpose.REGISTER), "Passw0rd!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    void signInsFromANewDevicePasswordChangesAndSignedOutDevicesAreAllReported() throws Exception {
        String email = "security+" + System.nanoTime() + "@example.com";
        Cookie phoneSession = register(VerificationChannel.EMAIL, email, IPHONE_SAFARI);
        String accountId = data(mockMvc.perform(get("/api/v1/me").cookie(phoneSession)).andReturn()).path("id").asText();
        assertThat(securityTitles(accountId)).isEmpty();

        // The same kind of device again is not news; a new one is.
        login(email, IPHONE_SAFARI).andExpect(status().isOk());
        assertThat(securityTitles(accountId)).isEmpty();
        Cookie laptop = login(email, WINDOWS_CHROME).andExpect(status().isOk()).andReturn().getResponse().getCookie("jobproof_session");
        assertThat(securityTitles(accountId)).containsExactly("新设备登录了你的账号");
        assertThat(securityBodies(accountId).get(0)).contains("Windows · Chrome");
        login(email, WINDOWS_CHROME).andExpect(status().isOk());
        assertThat(securityTitles(accountId)).hasSize(1);
        // The device list names the devices.
        JsonNode sessions = data(mockMvc.perform(get("/api/v1/auth/sessions").cookie(laptop)).andReturn());
        assertThat(sessions.findValuesAsText("clientLabel")).contains("Windows · Chrome", "iPhone · Safari");

        mockMvc.perform(post("/api/v1/auth/sessions/revoke-others").cookie(laptop)).andExpect(status().isOk());
        assertThat(securityTitles(accountId)).anyMatch(title -> title.startsWith("已退出其他") && title.endsWith("台登录设备"));

        mockMvc.perform(post("/api/v1/auth/password/change").cookie(laptop).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Passw0rd!\",\"newPassword\":\"N3wPassw0rd!\"}"))
                .andExpect(status().isOk());
        assertThat(securityTitles(accountId)).contains("账号密码已修改");
        // Security notices cannot be turned off and are emailed to the verified address.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_emails WHERE account_id=? AND type='SECURITY' AND to_address=?",
                Integer.class, accountId, email)).isGreaterThanOrEqualTo(3);

        String token = verifiedToken(VerificationChannel.EMAIL, email, VerificationPurpose.LOGIN_RECOVERY);
        mockMvc.perform(post("/api/v1/auth/password/reset/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("channel", "EMAIL", "destination", email,
                                "verificationToken", token, "newPassword", "Th1rdPassw0rd!"))))
                .andExpect(status().isOk());
        assertThat(securityTitles(accountId)).contains("账号密码已通过找回重置");
    }

    private List<String> securityTitles(String accountId) {
        return jdbc.queryForList("SELECT title FROM notifications WHERE account_id=? AND type='SECURITY' ORDER BY created_at",
                String.class, accountId);
    }

    private List<String> securityBodies(String accountId) {
        return jdbc.queryForList("SELECT body FROM notifications WHERE account_id=? AND type='SECURITY' ORDER BY created_at",
                String.class, accountId);
    }

    private Cookie register(VerificationChannel channel, String destination, String userAgent) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel.name());
        body.put("destination", destination);
        body.put("verificationToken", verifiedToken(channel, destination, VerificationPurpose.REGISTER));
        body.put("password", "Passw0rd!");
        body.put("acceptedTerms", true);
        body.put("acceptedPrivacy", true);
        return mockMvc.perform(post("/api/v1/auth/register").header("User-Agent", userAgent)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("jobproof_session");
    }

    private ResultActions login(String identifier, String userAgent) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").header("User-Agent", userAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("identifier", identifier, "password", "Passw0rd!"))));
    }

    private ResultActions bind(Cookie session, VerificationChannel channel, String destination, String token,
            String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/contacts").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("channel", channel.name(), "destination", destination,
                        "verificationToken", token, "currentPassword", password))));
    }

    private String verifiedToken(VerificationChannel channel, String destination, VerificationPurpose purpose) throws Exception {
        MvcResult requested = mockMvc.perform(post("/api/v1/auth/verifications/request").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("channel", channel.name(), "destination", destination,
                                "purpose", purpose.name()))))
                .andExpect(status().isOk()).andReturn();
        String normalized = channel == VerificationChannel.SMS ? "+86" + destination : destination.toLowerCase();
        String code = data(mockMvc.perform(get("/internal/dev/mailbox/{destination}", normalized))
                .andExpect(status().isOk()).andReturn()).path("code").asText();
        MvcResult confirmed = mockMvc.perform(post("/api/v1/auth/verifications/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("challengeId", data(requested).path("challengeId").asText(),
                                "destination", destination, "code", code))))
                .andExpect(status().isOk()).andReturn();
        return data(confirmed).path("verificationToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
