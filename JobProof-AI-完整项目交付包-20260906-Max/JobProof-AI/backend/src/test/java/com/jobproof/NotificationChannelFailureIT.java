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
import com.jobproof.modules.notification.domain.NotificationStatus;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
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
class NotificationChannelFailureIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NotificationService notificationService;

    @Test
    void exportSurvivesChannelFailureAndRecordsSendFailed() throws Exception {
        Cookie session = registerAndLogin("notify-fail+" + System.nanoTime() + "@example.com");
        notificationService.failNextWrite();

        MvcResult created = mockMvc.perform(post("/api/v1/data-rights/exports")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();
        String exportId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();

        MvcResult list = mockMvc.perform(get("/api/v1/notifications").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(list.getResponse().getContentAsString()).path("data").path("items");
        assertThat(items.toString()).contains(NotificationStatus.SEND_FAILED.name());

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/data-rights/exports/{id}", exportId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskStatus").value("SUCCEEDED")));

        String failedId = null;
        for (JsonNode item : items) {
            if (NotificationStatus.SEND_FAILED.name().equals(item.path("status").asText())) {
                failedId = item.path("id").asText();
                break;
            }
        }
        assertThat(failedId).isNotBlank();
        mockMvc.perform(post("/api/v1/notifications/{id}/read", failedId).cookie(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("NOTIFICATION_SEND_FAILED"));
        mockMvc.perform(post("/api/v1/notifications/read-batch")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + failedId + "\"]}"))
                .andExpect(status().isOk());
        MvcResult after = mockMvc.perform(get("/api/v1/notifications").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterItems = objectMapper.readTree(after.getResponse().getContentAsString()).path("data").path("items");
        boolean stillFailed = false;
        for (JsonNode item : afterItems) {
            if (failedId.equals(item.path("id").asText())) {
                assertThat(item.path("status").asText()).isEqualTo(NotificationStatus.SEND_FAILED.name());
                stillFailed = true;
            }
        }
        assertThat(stillFailed).isTrue();
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
