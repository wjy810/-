package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.resume.application.ResumeAiCandidateService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 投递追踪与简历查看回执 (docs/phase2/01). Same context as AiResumeWorkbenchIT, which freezes versions. */
@SpringBootTest(properties = {
        "jobproof.ai.workbench.enabled=true",
        "jobproof.ai.workbench.monthly-quota=500"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobApplicationIT {
    private static final String WECHAT_PHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.50";
    private static final String DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockBean ResumeAiCandidateService aiCandidates;
    @MockBean AiGatewayService gateway;

    @BeforeEach
    void defaultAiAvailability() {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(false, "qwen-plus", "AI_GATEWAY_DISABLED"));
    }

    @Test
    void applicationLinksReportWhoOpenedTheResumeWithoutCountingTheOwnerOrRobots() throws Exception {
        Session owner = seeker();
        Session other = seeker();
        String versionId = frozenVersion(owner);

        JsonNode versions = data(mockMvc.perform(get("/api/v1/job-applications/resume-versions").cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        assertThat(versions.get(0).path("versionId").asText()).isEqualTo(versionId);
        assertThat(versions.get(0).path("ordinal").asInt()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/resume-links").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("resumeVersionId", versionId, "expiresInDays", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESUME_LINK_EXPIRY_INVALID"));

        JsonNode created = data(mockMvc.perform(post("/api/v1/job-applications").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("company", "合成科技", "positionTitle", "Java 后端工程师",
                                "channel", "REFERRAL", "resumeVersionId", versionId, "createLink", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("APPLIED"))
                .andExpect(jsonPath("$.data.link.status").value("ACTIVE"))
                .andReturn());
        String applicationId = created.path("id").asText();
        String token = created.path("link").path("token").asText();
        String linkId = created.path("link").path("id").asText();
        assertThat(created.path("link").path("path").asText()).isEqualTo("/r/" + token);
        assertThat(created.path("appliedOn").asText()).isNotBlank();

        mockMvc.perform(get("/api/v1/job-applications/{id}", applicationId).cookie(other.cookie()))
                .andExpect(status().isForbidden());

        // Anyone with the link reads the resume; the page is never indexed or cached.
        mockMvc.perform(get("/api/v1/public/resume-links/{token}", token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(jsonPath("$.data.templateId").isNotEmpty())
                .andExpect(jsonPath("$.data.ownerPreview").value(false));
        mockMvc.perform(get("/api/v1/public/resume-links/{token}", token).cookie(owner.cookie()))
                .andExpect(jsonPath("$.data.ownerPreview").value(true));

        assertThat(view(token, owner.cookie(), "198.51.100.1", DESKTOP).path("reason").asText()).isEqualTo("OWNER");
        assertThat(view(token, null, "198.51.100.2", "Mozilla/5.0 (compatible; Googlebot/2.1)").path("recorded").asBoolean())
                .isFalse();

        JsonNode phone = view(token, null, "203.0.113.5", WECHAT_PHONE);
        assertThat(phone.path("recorded").asBoolean()).isTrue();
        // A reload is the same visit, not a second reader.
        JsonNode reload = view(token, null, "203.0.113.5", WECHAT_PHONE);
        assertThat(reload.path("viewId").asText()).isEqualTo(phone.path("viewId").asText());

        heartbeat(token, phone.path("viewId").asText(), "not-the-key", 600);
        heartbeat(token, phone.path("viewId").asText(), reload.path("viewKey").asText(), 95);
        heartbeat(token, phone.path("viewId").asText(), reload.path("viewKey").asText(), 40);
        view(token, null, "192.0.2.77", DESKTOP);

        JsonNode overview = data(mockMvc.perform(get("/api/v1/job-applications").cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        JsonNode link = overview.path("items").get(0).path("link");
        assertThat(link.path("viewCount").asInt()).isEqualTo(2);
        assertThat(link.path("visitorCount").asInt()).isEqualTo(2);
        assertThat(link.path("readSeconds").asInt()).isEqualTo(95);
        assertThat(overview.path("stats").path("applied").asInt()).isEqualTo(1);
        assertThat(overview.path("stats").path("opened").asInt()).isEqualTo(1);

        List<String> titles = jdbc.queryForList("SELECT title FROM notifications WHERE account_id=? AND type='APPLICATION_ACTIVITY' ORDER BY created_at",
                String.class, owner.accountId());
        assertThat(titles).containsExactly("合成科技打开了你的简历", "合成科技的简历有了新的读者");
        String body = jdbc.queryForObject("SELECT body FROM notifications WHERE account_id=? AND type='APPLICATION_ACTIVITY' ORDER BY created_at LIMIT 1",
                String.class, owner.accountId());
        assertThat(body).contains("用手机在微信里");

        JsonNode detail = data(mockMvc.perform(get("/api/v1/job-applications/{id}", applicationId).cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        assertThat(detail.path("events").findValuesAsText("type"))
                .contains("CREATED", "LINK_CREATED", "FIRST_VIEWED", "NEW_VISITOR");
        assertThat(detail.path("views").size()).isEqualTo(2);
        assertThat(detail.path("views").findValuesAsText("client")).contains("WECHAT", "BROWSER");

        // The funnel keeps how far an application came even after it closes.
        int version = detail.path("application").path("version").asInt();
        JsonNode interview = stage(owner, applicationId, Map.of("stage", "INTERVIEW", "expectedVersion", version));
        assertThat(interview.path("furthestStage").asText()).isEqualTo("INTERVIEW");
        mockMvc.perform(post("/api/v1/job-applications/{id}/stage", applicationId).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("stage", "CLOSED", "expectedVersion", interview.path("version").asInt()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("APPLICATION_OUTCOME_REQUIRED"));
        JsonNode closed = stage(owner, applicationId, Map.of("stage", "CLOSED", "outcome", "REJECTED",
                "expectedVersion", interview.path("version").asInt()));
        assertThat(closed.path("furthestStage").asText()).isEqualTo("INTERVIEW");
        assertThat(closed.path("outcome").asText()).isEqualTo("REJECTED");

        mockMvc.perform(post("/api/v1/resume-links/{id}/revoke", linkId).cookie(other.cookie()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/resume-links/{id}/revoke", linkId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
        mockMvc.perform(get("/api/v1/public/resume-links/{token}", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESUME_LINK_REVOKED"));

        mockMvc.perform(delete("/api/v1/job-applications/{id}", applicationId).cookie(owner.cookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/resume-links/{token}", token))
                .andExpect(jsonPath("$.error.reason").value("RESUME_LINK_NOT_FOUND"));
        assertThat(data(mockMvc.perform(get("/api/v1/job-applications").cookie(owner.cookie())).andReturn())
                .path("items").size()).isZero();
    }

    @Test
    void aNewLinkForAnApplicationReplacesTheOldOneAndHiddenContactsNeverLeave() throws Exception {
        Session owner = seeker();
        String versionId = frozenVersion(owner);
        JsonNode application = data(mockMvc.perform(post("/api/v1/job-applications").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("company", "星图数据", "positionTitle", "数据分析师",
                                "stage", "WISHLIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("WISHLIST"))
                .andReturn());
        String applicationId = application.path("id").asText();

        JsonNode first = data(mockMvc.perform(post("/api/v1/resume-links").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("resumeVersionId", versionId,
                                "applicationId", applicationId))))
                .andExpect(status().isOk()).andReturn());
        assertThat(first.path("label").asText()).isEqualTo("星图数据 · 数据分析师");
        JsonNode second = data(mockMvc.perform(post("/api/v1/resume-links").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("resumeVersionId", versionId,
                                "applicationId", applicationId, "hideContact", true, "expiresInDays", 30))))
                .andExpect(status().isOk()).andReturn());
        assertThat(second.path("expiresAt").asText()).isNotBlank();

        JsonNode links = data(mockMvc.perform(get("/api/v1/resume-links").cookie(owner.cookie())).andReturn());
        assertThat(links.findValuesAsText("status")).containsExactlyInAnyOrder("ACTIVE", "REVOKED");
        mockMvc.perform(get("/api/v1/public/resume-links/{token}", first.path("token").asText()))
                .andExpect(jsonPath("$.error.reason").value("RESUME_LINK_REVOKED"));

        JsonNode shared = data(mockMvc.perform(get("/api/v1/public/resume-links/{token}", second.path("token").asText()))
                .andExpect(status().isOk()).andReturn());
        assertThat(shared.path("hideContact").asBoolean()).isTrue();
        JsonNode basics = shared.path("content").path("basics");
        assertThat(basics.has("phone") || basics.has("email")).isFalse();
        assertThat(shared.path("photo").isNull() || shared.path("photo").isMissingNode()).isTrue();
    }

    private String frozenVersion(Session owner) throws Exception {
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"投递用简历\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode target = card(created, "TARGET_JOB");
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, target.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"targetJob\":\"Java 后端工程师\"},\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        JsonNode started = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/export-pdf", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        AtomicReference<JsonNode> task = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode current = data(mockMvc.perform(get("/api/v1/tasks/{id}", started.path("id").asText())
                            .cookie(owner.cookie()))
                    .andExpect(jsonPath("$.data.status").value("SUCCEEDED")).andReturn());
            task.set(current);
        });
        return jdbc.queryForObject("SELECT id FROM resume_versions WHERE account_id=? AND status='FROZEN' ORDER BY created_at DESC LIMIT 1",
                String.class, owner.accountId());
    }

    private JsonNode view(String token, Cookie cookie, String address, String userAgent) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/public/resume-links/{token}/views", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referrer\":\"https://mail.example.com/inbox\"}")
                .header("User-Agent", userAgent)
                .with(servlet -> {
                    servlet.setRemoteAddr(address);
                    return servlet;
                });
        if (cookie != null) request.cookie(cookie);
        return data(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private void heartbeat(String token, String viewId, String key, int seconds) throws Exception {
        mockMvc.perform(post("/api/v1/public/resume-links/{token}/views/{view}/heartbeat", token, viewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("viewKey", key, "seconds", seconds))))
                .andExpect(status().isOk());
    }

    private JsonNode stage(Session owner, String applicationId, Map<String, Object> body) throws Exception {
        return data(mockMvc.perform(post("/api/v1/job-applications/{id}/stage", applicationId).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn());
    }

    private Session seeker() throws Exception {
        String email = "apply+" + System.nanoTime() + "@example.com";
        String password = "Passw0rd!";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = login.getResponse().getCookie("jobproof_session");
        JsonNode me = data(mockMvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isOk()).andReturn());
        return new Session(cookie, me.path("id").asText());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static JsonNode card(JsonNode conversation, String type) {
        for (JsonNode card : conversation.path("cards")) {
            if (type.equals(card.path("cardType").asText())) return card;
        }
        throw new AssertionError("card not found: " + type);
    }

    private record Session(Cookie cookie, String accountId) {}
}
