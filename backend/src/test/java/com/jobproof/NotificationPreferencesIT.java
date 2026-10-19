package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.notification.application.NotificationEmailOutbox;
import com.jobproof.modules.notification.application.NotificationPreferences;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationEmailPort.NotificationEmail;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.modules.notification.infra.DevNotificationEmailAdapter;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 通知偏好与邮件提醒 (docs/phase2/03). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationPreferencesIT {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;
    @Autowired NotificationEmailOutbox outbox;
    @Autowired NotificationPreferences preferences;
    @Autowired DevNotificationEmailAdapter mailer;

    @Test
    void noticesFollowThePreferencesAndReachAVerifiedAddressOnce() throws Exception {
        Account account = account("prefs", true);
        JsonNode view = data(mockMvc.perform(get("/api/v1/notification-preferences").cookie(account.cookie()))
                .andExpect(status().isOk()).andReturn());
        assertThat(view.path("emailAvailable").asBoolean()).isTrue();
        assertThat(view.path("emailAddress").asText()).startsWith("pr***@");
        assertThat(view.path("items").findValuesAsText("type"))
                .containsExactly("APPLICATION_ACTIVITY", "REMINDER", "MEMBERSHIP", "WEEKLY_DIGEST", "PRODUCT_UPDATE");
        assertThat(view.path("items").get(4).path("email").asBoolean()).isFalse();

        mockMvc.perform(put("/api/v1/notification-preferences").cookie(account.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("items", List.of(Map.of("type", "SECURITY", "inApp", false, "email", false))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("NOTIFICATION_TYPE_LOCKED"));

        // A reminder shows in the center and is emailed, once, with user text escaped.
        notifications.request(account.id(), NotificationType.REMINDER, "next-step:a1:1", "明天 14:00 · <合成科技> 一面",
                "岗位：Java 后端工程师。", "/applications?focus=a1");
        notifications.request(account.id(), NotificationType.REMINDER, "next-step:a1:1", "明天 14:00 · <合成科技> 一面",
                "岗位：Java 后端工程师。", "/applications?focus=a1");
        Instant now = Instant.now();
        outbox.sendDue(now);
        outbox.sendDue(now.plus(Duration.ofMinutes(10)));
        List<NotificationEmail> mine = sentTo(account.email());
        assertThat(mine).singleElement().satisfies(email -> {
            assertThat(email.subject()).isEqualTo("【JobProof AI】明天 14:00 · <合成科技> 一面");
            assertThat(email.html()).contains("&lt;合成科技&gt;").doesNotContain("<合成科技>")
                    .contains("/applications?focus=a1").contains("/account/notifications");
            assertThat(email.text()).contains("岗位：Java 后端工程师。");
        });
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE account_id=? AND type='REMINDER'", account.id())).isEqualTo(1);

        // Product updates stay out of the inbox by default.
        notifications.request(account.id(), NotificationType.PRODUCT_UPDATE, "release-1", "v2.0 新版本", "更新内容", "/updates/v2");
        assertThat(count("SELECT COUNT(*) FROM notification_emails WHERE account_id=? AND type='PRODUCT_UPDATE'", account.id())).isZero();

        // Turned off entirely: neither the center nor the inbox.
        data(mockMvc.perform(put("/api/v1/notification-preferences").cookie(account.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("items", List.of(Map.of("type", "REMINDER", "inApp", false, "email", false))))))
                .andExpect(status().isOk()).andReturn());
        notifications.request(account.id(), NotificationType.REMINDER, "next-step:a2:1", "今天 18:00 · 星图数据 笔试", "岗位：数据分析师。", null);
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE account_id=? AND type='REMINDER'", account.id())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification_emails WHERE account_id=? AND type='REMINDER'", account.id())).isEqualTo(1);
        // Security notices cannot be turned off.
        assertThat(preferences.of(account.id(), NotificationType.SECURITY).inApp()).isTrue();

        assertThat(preferences.onAccountDeletion(account.id()).status()).isEqualTo("SUCCEEDED");
        assertThat(count("SELECT COUNT(*) FROM notification_emails WHERE account_id=?", account.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM notification_preferences WHERE account_id=?", account.id())).isZero();
    }

    @Test
    void aFailedSendIsRetriedLaterAndAnUnverifiedAddressGetsNothing() throws Exception {
        Account verified = account("retry", true);
        Account unverified = account("unverified", false);
        notifications.request(unverified.id(), NotificationType.APPLICATION_ACTIVITY, "first-open:l1", "合成科技打开了你的简历", "微信 · 手机", null);
        assertThat(count("SELECT COUNT(*) FROM notification_emails WHERE account_id=?", unverified.id())).isZero();

        notifications.request(verified.id(), NotificationType.APPLICATION_ACTIVITY, "first-open:l2", "星图数据打开了你的简历", "飞书 · 电脑", null);
        Instant now = Instant.now();
        mailer.failNext();
        outbox.sendDue(now);
        Map<String, Object> row = jdbc.queryForMap("SELECT status,attempts,last_error FROM notification_emails WHERE account_id=?", verified.id());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(1);
        assertThat((String) row.get("last_error")).contains("simulated");
        outbox.sendDue(now.plusSeconds(10));
        assertThat(sentTo(verified.email())).isEmpty();
        outbox.sendDue(now.plus(Duration.ofMinutes(2)));
        assertThat(sentTo(verified.email())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM notification_emails WHERE account_id=?", String.class, verified.id())).isEqualTo("SENT");
    }

    private List<NotificationEmail> sentTo(String address) {
        return mailer.sent().stream().filter(email -> email.to().equalsIgnoreCase(address)).toList();
    }

    private int count(String sql, String accountId) {
        Integer value = jdbc.queryForObject(sql, Integer.class, accountId);
        return value == null ? 0 : value;
    }

    private Account account(String prefix, boolean verifiedEmail) throws Exception {
        String email = prefix + "+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk());
        if (verifiedEmail) jdbc.update("UPDATE accounts SET email_verified_at=? WHERE email=?", Instant.now(), email);
        else jdbc.update("UPDATE accounts SET email_verified_at=NULL WHERE email=?", email);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = login.getResponse().getCookie("jobproof_session");
        String id = data(mockMvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isOk()).andReturn()).path("id").asText();
        return new Account(cookie, id, email);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private record Account(Cookie cookie, String id, String email) {}
}
