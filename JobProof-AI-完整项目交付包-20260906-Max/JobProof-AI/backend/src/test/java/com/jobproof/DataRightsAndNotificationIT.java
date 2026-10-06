package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataRightsAndNotificationIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationService notificationService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void deletionStaysVisibleAndNeverPretendsCompletedOnSubmit() throws Exception {
        Cookie session = registerAndLogin("delete+" + System.nanoTime() + "@example.com");
        MvcResult me = mockMvc.perform(get("/api/v1/me").cookie(session)).andReturn();
        String accountId = objectMapper.readTree(me.getResponse().getContentAsString()).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/career-planning/sessions")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryMode\":\"AI_DISCOVERY\",\"aiConsent\":false}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM career_planning_sessions WHERE account_id=?", Integer.class, accountId))
                .isEqualTo(1);

        mockMvc.perform(post("/api/v1/data-rights/deletions")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"ACCOUNT","confirmationAck":false}
                                """))
                .andExpect(status().isBadRequest());

        MvcResult created = mockMvc.perform(post("/api/v1/data-rights/deletions")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"ACCOUNT","confirmationAck":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
        assertThat(id).isNotBlank();

        mockMvc.perform(get("/api/v1/data-rights/deletions/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/data-rights/deletions/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_RESTRICTED"))
                .andExpect(jsonPath("$.data.receipts").isArray()));

        MvcResult done = mockMvc.perform(get("/api/v1/data-rights/deletions/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode receipts = objectMapper.readTree(done.getResponse().getContentAsString()).path("data").path("receipts");
        assertThat(receiptStatus(receipts, "career-library")).isEqualTo("SUCCEEDED");
        assertThat(receiptStatus(receipts, "job")).isEqualTo("SUCCEEDED");
        assertThat(receiptStatus(receipts, "matching")).isEqualTo("SUCCEEDED");
        assertThat(receiptStatus(receipts, "resume")).isEqualTo("SUCCEEDED");
        assertThat(receiptStatus(receipts, "career-planning")).isEqualTo("SUCCEEDED");
        assertThat(receiptStatus(receipts, "audit")).isEqualTo("RESTRICTED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM career_planning_sessions WHERE account_id=?", Integer.class, accountId))
                .isZero();
    }

    private static String receiptStatus(JsonNode receipts, String moduleCode) {
        for (JsonNode row : receipts) {
            if (moduleCode.equals(row.path("moduleCode").asText())) {
                return row.path("status").asText();
            }
        }
        throw new AssertionError("missing deletion receipt for " + moduleCode);
    }

    @Test
    void exportTaskAndNotificationDedupAndFailureDoNotRollback() throws Exception {
        Cookie session = registerAndLogin("export+" + System.nanoTime() + "@example.com");
        notificationService.failNextWrite();
        MvcResult created = mockMvc.perform(post("/api/v1/data-rights/exports")
                        .cookie(session)
                        .header("Idempotency-Key", "export-once")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        String exportId = data.path("id").asText();
        String taskId = data.path("taskId").asText();
        assertThat(data.path("taskStatus").asText()).isIn("PENDING", "RUNNING", "SUCCEEDED");

        mockMvc.perform(post("/api/v1/data-rights/exports")
                        .cookie(session)
                        .header("Idempotency-Key", "export-once")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(exportId));

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/data-rights/exports/{id}", exportId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskStatus").value("SUCCEEDED")));

        mockMvc.perform(get("/api/v1/tasks/{id}", taskId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        String eventId = taskId + ":SUCCEEDED";
        var first = notificationService.request("ignore", NotificationType.TASK_COMPLETED, eventId, "a", "b");
        // 上面用了错误账号，下面用真实去重：同一 eventId + type + 接收人
        MvcResult me = mockMvc.perform(get("/api/v1/me").cookie(session)).andReturn();
        String accountId = objectMapper.readTree(me.getResponse().getContentAsString()).path("data").path("id").asText();
        var one = notificationService.request(accountId, NotificationType.TASK_COMPLETED, "same-event", "t", "b");
        var two = notificationService.request(accountId, NotificationType.TASK_COMPLETED, "same-event", "t2", "b2");
        assertThat(two.id()).isEqualTo(one.id());
        assertThat(first).isNotNull();
    }

    @Test
    void cannotReadOtherUsersExport() throws Exception {
        Cookie alice = registerAndLogin("alice+" + System.nanoTime() + "@example.com");
        Cookie bob = registerAndLogin("bob+" + System.nanoTime() + "@example.com");
        MvcResult created = mockMvc.perform(post("/api/v1/data-rights/exports")
                        .cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String exportId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
        mockMvc.perform(get("/api/v1/data-rights/exports/{id}", exportId).cookie(bob))
                .andExpect(status().isForbidden());
    }

    private Cookie registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("jobproof_session");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
