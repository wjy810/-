package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.jobapply.application.NextStepReminders;
import com.jobproof.modules.jobapply.application.WeeklyDigest;
import com.jobproof.modules.resume.application.ResumeAiCandidateService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
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
    @Autowired NextStepReminders reminders;
    @Autowired WeeklyDigest digest;
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

    @Test
    void theFreePlanKeepsThreeActiveLinksAndProRaisesTheLimit() throws Exception {
        Session owner = seeker();
        String versionId = frozenVersion(owner);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/resume-links").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("resumeVersionId", versionId, "label", "链接 " + i))))
                    .andExpect(status().isOk());
        }
        String fourth = mapper.writeValueAsString(Map.of("resumeVersionId", versionId, "label", "第四条"));
        mockMvc.perform(post("/api/v1/resume-links").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON).content(fourth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_LINK_LIMIT"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("升级专业版")));

        jdbc.update("INSERT INTO account_memberships(account_id,plan,expires_at,source,version_no,created_at,updated_at) VALUES(?,'PRO',?,'TEST',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                owner.accountId(), java.sql.Timestamp.from(java.time.Instant.now().plus(Duration.ofDays(30))));
        mockMvc.perform(post("/api/v1/resume-links").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON).content(fourth))
                .andExpect(status().isOk());
    }

    @Test
    void receiptsSurviveANewLinkTheOwnersOwnOpeningsCanBeTakenOutAndClosingFreesTheSlot() throws Exception {
        Session owner = seeker();
        String versionId = frozenVersion(owner);
        JsonNode created = data(mockMvc.perform(post("/api/v1/job-applications").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("company", "星图数据", "positionTitle", "数据分析师",
                                "resumeVersionId", versionId, "createLink", true))))
                .andExpect(status().isOk()).andReturn());
        String applicationId = created.path("id").asText();
        String firstToken = created.path("link").path("token").asText();
        String firstLink = created.path("link").path("id").asText();
        // Links made with an application last 60 days by default instead of forever.
        assertThat(java.time.Instant.parse(created.path("link").path("expiresAt").asText()))
                .isBetween(java.time.Instant.now().plus(Duration.ofDays(59)), java.time.Instant.now().plus(Duration.ofDays(61)));

        JsonNode hr = view(firstToken, null, "hr-desktop", DESKTOP);
        view(firstToken, null, "me-on-phone", WECHAT_PHONE);
        // Opening 投递追踪 registers this browser as the owner's; its later openings are not receipts.
        MvcResult overviewCall = mockMvc.perform(get("/api/v1/job-applications").cookie(owner.cookie())).andReturn();
        Cookie device = overviewCall.getResponse().getCookie("jp_owner_device");
        assertThat(device).isNotNull();
        assertThat(device.isHttpOnly()).isTrue();
        assertThat(view(firstToken, null, "my-laptop", DESKTOP, device).path("reason").asText()).isEqualTo("OWNER");

        // 这是我自己打开的: the phone visit was the owner checking the link.
        JsonNode views = data(mockMvc.perform(get("/api/v1/resume-links/{id}/views", firstLink).cookie(owner.cookie())).andReturn());
        String phoneView = null;
        for (JsonNode item : views) if ("WECHAT".equals(item.path("client").asText())) phoneView = item.path("id").asText();
        mockMvc.perform(post("/api/v1/resume-links/{id}/views/{view}/own", firstLink, phoneView).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(1))
                .andExpect(jsonPath("$.data.visitorCount").value(1));
        assertThat(view(firstToken, null, "me-on-phone", WECHAT_PHONE).path("reason").asText()).isEqualTo("OWNER");

        // A new link for the same application: the card still shows the HR visit from the old one.
        JsonNode second = data(mockMvc.perform(post("/api/v1/resume-links").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("resumeVersionId", versionId, "applicationId", applicationId, "expiresInDays", 30))))
                .andExpect(status().isOk()).andReturn());
        view(second.path("token").asText(), null, "hr-desktop", DESKTOP);
        JsonNode card = data(mockMvc.perform(get("/api/v1/job-applications").cookie(owner.cookie())).andReturn()).path("items").get(0);
        assertThat(card.path("link").path("viewCount").asInt()).as("the current link alone").isEqualTo(1);
        assertThat(card.path("receipts").path("views").asInt()).isEqualTo(2);
        assertThat(card.path("receipts").path("linkCount").asInt()).isEqualTo(2);
        assertThat(hr.path("recorded").asBoolean()).isTrue();

        // Expiry and contact visibility change in place; the address stays.
        String secondId = second.path("id").asText();
        mockMvc.perform(patch("/api/v1/resume-links/{id}", secondId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"neverExpires\":true,\"hideContact\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(second.path("token").asText()))
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.hideContact").value(true));

        // Accepting an offer needs an offer; closing frees the link slot two weeks later.
        int version = card.path("version").asInt();
        mockMvc.perform(post("/api/v1/job-applications/{id}/stage", applicationId).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("stage", "CLOSED", "outcome", "ACCEPTED", "expectedVersion", version))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("APPLICATION_OUTCOME_NEEDS_OFFER"));
        stage(owner, applicationId, Map.of("stage", "CLOSED", "outcome", "NO_RESPONSE", "expectedVersion", version));
        String expires = jdbc.queryForObject("SELECT expires_at FROM resume_share_links WHERE id=?", String.class, secondId);
        assertThat(java.sql.Timestamp.valueOf(expires).toInstant())
                .isBetween(java.time.Instant.now().plus(Duration.ofDays(13)), java.time.Instant.now().plus(Duration.ofDays(15)));
    }

    @Test
    void aDoneNextStepStopsBeingOverdueAndDeletingArchivesUntilRestored() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/job-applications").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("company", "拾光电商", "positionTitle", "数据分析师",
                                "nextStep", "一面", "nextStepAt", java.time.Instant.now().minus(Duration.ofDays(2)).toString()))))
                .andExpect(status().isOk()).andReturn());
        String id = created.path("id").asText();
        mockMvc.perform(post("/api/v1/job-applications").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("company", "合成科技", "positionTitle", "后端",
                                "nextStep", "笔试", "nextStepAt", java.time.Instant.now().plus(Duration.ofDays(2)).toString()))))
                .andExpect(status().isOk());

        // The dashboard lists what is coming separately from what is overdue.
        JsonNode summary = data(mockMvc.perform(get("/api/v1/workspace/overview").cookie(owner.cookie())).andReturn()).path("applications");
        assertThat(summary.path("nextSteps").findValuesAsText("company")).containsExactly("合成科技");
        assertThat(summary.path("overdueSteps").findValuesAsText("company")).containsExactly("拾光电商");

        JsonNode done = data(mockMvc.perform(post("/api/v1/job-applications/{id}/next-step/done", id).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("note", "进入二面", "expectedVersion", created.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
        assertThat(done.path("nextStepAt").isMissingNode() || done.path("nextStepAt").isNull()).isTrue();
        JsonNode overview = data(mockMvc.perform(get("/api/v1/job-applications").cookie(owner.cookie())).andReturn());
        assertThat(overview.path("stats").path("overdue").asInt()).isZero();
        JsonNode detail = data(mockMvc.perform(get("/api/v1/job-applications/{id}", id).cookie(owner.cookie())).andReturn());
        assertThat(detail.path("events").findValuesAsText("detail")).contains("完成：一面。进入二面");

        mockMvc.perform(post("/api/v1/job-applications/{id}/archive", id).cookie(owner.cookie())).andExpect(status().isOk());
        overview = data(mockMvc.perform(get("/api/v1/job-applications").cookie(owner.cookie())).andReturn());
        assertThat(overview.path("items").findValuesAsText("company")).containsExactly("合成科技");
        assertThat(overview.path("archivedCount").asInt()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/job-applications/archived").cookie(owner.cookie()))
                .andExpect(jsonPath("$.data[0].company").value("拾光电商"));
        mockMvc.perform(post("/api/v1/job-applications/{id}/restore", id).cookie(owner.cookie())).andExpect(status().isOk());
        assertThat(data(mockMvc.perform(get("/api/v1/job-applications").cookie(owner.cookie())).andReturn())
                .path("items").size()).isEqualTo(2);
    }

    @Test
    void aNextStepWithinADayIsRemindedOnceAndAgainWhenItMoves() throws Exception {
        Session owner = seeker();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant interview = now.plus(Duration.ofHours(20));
        JsonNode created = data(mockMvc.perform(post("/api/v1/job-applications").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("company", "星图数据", "positionTitle", "数据分析师",
                                "channel", "CAMPUS", "stage", "INTERVIEW", "nextStep", "一面", "nextStepAt", interview.toString()))))
                .andExpect(status().isOk()).andReturn());
        String id = created.path("id").asText();
        JsonNode ended = data(mockMvc.perform(post("/api/v1/job-applications").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("company", "已结束科技", "positionTitle", "测试工程师",
                                "nextStep", "终面", "nextStepAt", now.plus(Duration.ofHours(3)).toString()))))
                .andExpect(status().isOk()).andReturn());
        stage(owner, ended.path("id").asText(), Map.of("stage", "CLOSED", "outcome", "WITHDRAWN",
                "expectedVersion", ended.path("version").asInt()));

        // Just set: not announced back to the user straight away.
        reminders.sendDue(now);
        assertThat(reminderTitles(owner)).isEmpty();

        Instant later = now.plus(Duration.ofMinutes(15));
        reminders.sendDue(later);
        reminders.sendDue(later.plus(Duration.ofMinutes(5)));
        assertThat(reminderTitles(owner)).singleElement().asString().contains("星图数据 一面");
        assertThat(jdbc.queryForObject("SELECT action_path FROM notifications WHERE account_id=? AND type='REMINDER'",
                String.class, owner.accountId())).isEqualTo("/applications?focus=" + id);
        // Bookkeeping never bumps the version the user edits against.
        JsonNode current = data(mockMvc.perform(get("/api/v1/job-applications/{id}", id).cookie(owner.cookie())).andReturn());
        assertThat(current.path("application").path("version").asInt()).isEqualTo(created.path("version").asInt());

        // Moving the interview re-arms the reminder.
        mockMvc.perform(put("/api/v1/job-applications/{id}", id).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("company", "星图数据", "positionTitle", "数据分析师",
                                "channel", "CAMPUS", "nextStep", "二面", "nextStepAt", now.plus(Duration.ofHours(23)).toString(),
                                "expectedVersion", created.path("version").asInt()))))
                .andExpect(status().isOk());
        reminders.sendDue(later);
        assertThat(reminderTitles(owner)).hasSize(2).anySatisfy(title -> assertThat(title).contains("星图数据 二面"));
    }

    @Test
    void mondayBringsOneDigestOfLastWeekAndTheWeekAhead() throws Exception {
        Session owner = seeker();
        String versionId = frozenVersion(owner);
        ZoneId china = ZoneId.of("Asia/Shanghai");
        Instant monday = Instant.now().atZone(china).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(10, 0).atZone(china).toInstant();
        JsonNode created = data(mockMvc.perform(post("/api/v1/job-applications").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("company", "周报科技", "positionTitle", "运营专员", "channel", "CAMPUS",
                                "resumeVersionId", versionId, "createLink", true, "stage", "INTERVIEW", "nextStep", "终面",
                                "nextStepAt", monday.plus(Duration.ofDays(2)).toString()))))
                .andExpect(status().isOk()).andReturn());
        assertThat(view(created.path("link").path("token").asText(), null, "203.0.113.77", WECHAT_PHONE).path("recorded").asBoolean()).isTrue();

        assertThat(digest.sendDue(monday.minus(Duration.ofHours(2)))).isZero();
        while (digest.sendDue(monday) > 0) {
            // other tests' accounts share the database; drain the week
        }
        digest.sendDue(monday.plus(Duration.ofHours(1)));
        List<String> titles = jdbc.queryForList("SELECT title FROM notifications WHERE account_id=? AND type='WEEKLY_DIGEST'", String.class, owner.accountId());
        assertThat(titles).singleElement().asString().contains("上周投递 1 家").contains("简历被打开 1 次");
        String body = jdbc.queryForObject("SELECT body FROM notifications WHERE account_id=? AND type='WEEKLY_DIGEST'", String.class, owner.accountId());
        assertThat(body).contains("周报科技打开了你的简历").contains("本周安排").contains("周报科技 终面");
    }

    private List<String> reminderTitles(Session owner) {
        return jdbc.queryForList("SELECT title FROM notifications WHERE account_id=? AND type='REMINDER' ORDER BY created_at",
                String.class, owner.accountId());
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

    /**
     * One browser opening the link. {@code reader} names the browser: it becomes that browser's jp_reader cookie,
     * which is how repeat visits are told from new readers (the address is not used).
     */
    private JsonNode view(String token, Cookie cookie, String reader, String userAgent, Cookie... extra) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/public/resume-links/{token}/views", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referrer\":\"https://mail.example.com/inbox\"}")
                .header("User-Agent", userAgent)
                .cookie(new Cookie("jp_reader", "reader-" + reader.replace('.', '-') + "-0000000000"));
        if (cookie != null) request.cookie(cookie);
        for (Cookie value : extra) request.cookie(value);
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
