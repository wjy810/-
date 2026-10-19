package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Request;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Response;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Usage;
import com.jobproof.modules.resume.application.ResumeAiCandidateService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "jobproof.job-match.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobMatchIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockBean AiGatewayService gateway;
    @MockBean ResumeAiCandidateService resumeAiCandidates;

    @BeforeEach
    void configureAi() {
        when(resumeAiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(false, "qwen-plus", "AI_GATEWAY_DISABLED"));
        when(gateway.execute(anyString(), any())).thenAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            JsonNode input = mapper.readTree(request.messages().get(request.messages().size() - 1).content());
            String requirementId = input.path("requirements").path(0).path("id").asText("");
            return new Response("job-match-ai", "system-channel", "qwen-plus",
                    validAiReport(input), new Usage(420, 380, 800), null);
        });
    }

    @Test
    void completeWorkflowIsIdempotentAccountScopedAndExportsAllFormats() throws Exception {
        MockCookie owner = registerAndLogin("match-owner+" + System.nanoTime() + "@example.com");
        MockCookie other = registerAndLogin("match-other+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);

        String createRequest = "create-" + UUID.randomUUID();
        JsonNode created = createMatch(owner, createRequest);
        JsonNode replay = createMatch(owner, createRequest);
        assertThat(replay.path("id").asText()).isEqualTo(created.path("id").asText());
        String matchId = created.path("id").asText();

        mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.reason").value("OBJECT_FORBIDDEN"));

        JsonNode corrected = confirmJd(owner, created);
        JsonNode options = data(mockMvc.perform(get("/api/v1/job-matches/{id}/resume-options", matchId).cookie(owner))
                .andExpect(status().isOk()).andReturn());
        assertThat(options).isNotEmpty();
        String revisionId = options.get(0).path("revisionId").asText();

        JsonNode resumeConfirmed = data(mockMvc.perform(put("/api/v1/job-matches/{id}/resume-selection", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "resumeRevisionId", revisionId,
                                "expectedVersion", corrected.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESUME_CONFIRMED"))
                .andReturn());

        JsonNode firstAuthorization = data(mockMvc.perform(post("/api/v1/job-matches/{id}/authorization", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "mode", "NONE", "recordIds", List.of(), "fileIds", List.of(),
                                "scope", "CURRENT_MATCH", "rememberPreference", false,
                                "expectedVersion", resumeConfirmed.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EVIDENCE_AUTHORIZED"))
                .andReturn());

        JsonNode preview = data(mockMvc.perform(get("/api/v1/job-matches/{id}/redaction-preview", matchId).cookie(owner))
                .andExpect(status().isOk()).andReturn());
        assertThat(preview.path("excludedFields").toString()).contains("phone", "email", "identityNumber");
        assertThat(preview.path("includedSources")).isEmpty();
        mockMvc.perform(get("/api/v1/job-matches/{id}/redaction-preview", matchId).cookie(other))
                .andExpect(status().isForbidden());

        // Revoking before analysis rewinds the match to the evidence step; it can then be authorised again.
        JsonNode revoked = data(mockMvc.perform(delete("/api/v1/job-matches/{id}/authorization", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "requestId", "revoke-" + UUID.randomUUID(),
                                "expectedVersion", firstAuthorization.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESUME_CONFIRMED"))
                .andReturn());
        assertThat(revoked.path("authorizationId").asText(null)).isNull();

        JsonNode authorized = data(mockMvc.perform(post("/api/v1/job-matches/{id}/authorization", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "mode", "NONE", "recordIds", List.of(), "fileIds", List.of(),
                                "scope", "CURRENT_MATCH", "rememberPreference", false,
                                "expectedVersion", revoked.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EVIDENCE_AUTHORIZED"))
                .andReturn());

        String analysisRequest = "analysis-" + UUID.randomUUID();
        JsonNode started = analyze(owner, matchId, analysisRequest, authorized.path("version").asInt());
        String taskId = started.path("task").path("id").asText();

        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.data.reportId").isNotEmpty()));

        JsonNode completed = data(mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                .andExpect(status().isOk()).andReturn());
        JsonNode repeatedAnalysis = analyze(owner, matchId, analysisRequest, authorized.path("version").asInt());
        assertThat(repeatedAnalysis.path("task").path("id").asText()).isEqualTo(taskId);
        assertThat(repeatedAnalysis.path("match").path("status").asText()).isEqualTo("COMPLETED");

        JsonNode report = data(mockMvc.perform(get("/api/v1/job-matches/{id}/report", matchId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.score").isNumber())
                .andExpect(jsonPath("$.data.report.scoring.method").value("AI_VERDICT"))
                .andExpect(jsonPath("$.data.report.confidence").value("HIGH"))
                .andExpect(jsonPath("$.data.report.requirements[0].verdict").value("MET"))
                // Every requirement met: the score allows 推荐投递, the model's more careful CONDITIONAL stands.
                .andExpect(jsonPath("$.data.report.recommendation.code").value("CONDITIONAL"))
                .andExpect(jsonPath("$.data.report.recommendation.capped").value(false))
                .andExpect(jsonPath("$.data.report.preview").value(false))
                .andExpect(jsonPath("$.data.report.ai.summary").exists())
                .andExpect(jsonPath("$.data.evidenceSources").isArray())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn());

        // The report was built from the authorised material: the authorisation can no longer be withdrawn.
        mockMvc.perform(delete("/api/v1/job-matches/{id}/authorization", matchId).cookie(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("JOB_MATCH_AUTHORIZATION_LOCKED"));

        // The stubbed model names no strengths or gaps, so give the report one conclusion to judge.
        String claimId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO job_match_claims(id,match_id,report_id,requirement_id,conclusion_type,score,confidence,evidence_ids_json,reasoning_summary,feedback_status,created_at,updated_at) VALUES(?,?,?,?,'STRENGTH',NULL,70,'[]','项目经历直接对应',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                claimId, matchId, report.path("id").asText(), completed.path("requirements").path(0).path("id").asText());
        JsonNode claim = data(mockMvc.perform(get("/api/v1/job-matches/{id}/report/claims/{claimId}", matchId, claimId)
                        .cookie(owner))
                .andExpect(status().isOk()).andReturn());
        assertThat(claim.path("feedback").asText(null)).isNull();
        String feedbackRequest = "feedback-" + UUID.randomUUID();
        Map<String, Object> feedback = Map.of("feedback", "INACCURATE", "requestId", feedbackRequest,
                "expectedVersion", claim.path("version").asInt());
        JsonNode marked = data(mockMvc.perform(post("/api/v1/job-matches/{id}/report/claims/{claimId}/feedback",
                        matchId, claim.path("id").asText()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(feedback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback").value("INACCURATE"))
                .andReturn());
        mockMvc.perform(post("/api/v1/job-matches/{id}/report/claims/{claimId}/feedback",
                        matchId, claim.path("id").asText()).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(feedback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(marked.path("version").asInt()));
        mockMvc.perform(post("/api/v1/job-matches/{id}/report/claims/{claimId}/feedback",
                        matchId, claim.path("id").asText()).cookie(other).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("feedback", "ACCURATE"))))
                .andExpect(status().isForbidden());

        JsonNode optimization = data(mockMvc.perform(post("/api/v1/job-matches/{id}/actions/resume-optimization", matchId)
                        .cookie(owner))
                .andExpect(status().isOk()).andReturn());
        String conversationId = optimization.path("conversationId").asText();
        mockMvc.perform(post("/api/v1/job-matches/{id}/actions/resume-optimization", matchId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changeSets.length()").value(1))
                .andExpect(jsonPath("$.data.changeSets[0].items.length()").value(1))
                .andExpect(jsonPath("$.data.changeSets[0].items[0].targetPath").value("/summary"));

        Map<String, Object> interview = new LinkedHashMap<>();
        interview.put("resumeId", options.get(0).path("masterId").asText());
        interview.put("careerRecordIds", List.of());
        interview.put("careerFileIds", List.of());
        interview.put("positionName", completed.path("title").asText());
        interview.put("companyName", completed.path("company").asText());
        interview.put("mode", "TEXT");
        interview.put("interviewType", "COMPREHENSIVE");
        interview.put("difficulty", "STANDARD");
        interview.put("durationMinutes", 30);
        interview.put("questionCount", 5);
        interview.put("languageCode", "zh-CN");
        interview.put("feedbackMode", "AFTER_SESSION");
        interview.put("followUpEnabled", true);
        interview.put("consentConfirmed", true);
        interview.put("jobMatchId", matchId);
        mockMvc.perform(post("/api/v1/mock-interviews/sessions").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(interview)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jdSnapshot.jobMatchId").value(matchId))
                .andExpect(jsonPath("$.data.jdSnapshot.reportId").value(completed.path("reportId").asText()))
                .andExpect(jsonPath("$.data.jdSnapshot.requirements.length()").value(9))
                .andExpect(jsonPath("$.data.jdSnapshot.recommendation.code").value("CONDITIONAL"));

        assertExport(owner, matchId, "PDF", "%PDF-".getBytes(StandardCharsets.ISO_8859_1));
        assertExport(owner, matchId, "DOCX", new byte[] { 'P', 'K' });
        assertExport(owner, matchId, "JSON", new byte[] { '{' });

        JsonNode quota = data(mockMvc.perform(get("/api/v1/job-matches/capabilities").cookie(owner))
                .andExpect(status().isOk()).andReturn()).path("quota");
        assertThat(quota.path("usedUnits").asInt()).isEqualTo(1);
        assertThat(completed.path("progress").asInt()).isEqualTo(100);

        mockMvc.perform(get("/api/v1/job-matches/dashboard").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(1))
                .andExpect(jsonPath("$.data.pending").value(0))
                .andExpect(jsonPath("$.data.averageScore").isNumber());

        mockMvc.perform(get("/api/v1/job-matches/history").cookie(owner)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(matchId))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(delete("/api/v1/job-matches/{id}", matchId).cookie(other))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/job-matches/{id}", matchId).cookie(owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/job-matches/history").cookie(owner)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void similarDirectionsListOnlyDirectionsThatShareResumeSkills() throws Exception {
        MockCookie owner = registerAndLogin("match-similar+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);
        JsonNode created = createMatch(owner, "create-similar-" + UUID.randomUUID());
        String matchId = created.path("id").asText();
        JsonNode corrected = confirmJd(owner, created);
        JsonNode options = data(mockMvc.perform(get("/api/v1/job-matches/{id}/resume-options", matchId)
                        .cookie(owner)).andExpect(status().isOk()).andReturn());
        String revisionId = options.get(0).path("revisionId").asText();
        jdbc.update("UPDATE resume_revisions SET content_json=? WHERE id=?", """
                {"schemaVersion":"resume-content-v3","basics":{"name":"测试用户"},
                "summary":"","education":[],"experiences":[],"projects":[],"organizations":[],
                "skills":[{"category":"语言与框架","items":["Java","Python","Spring Boot"]},
                          {"category":"内容与数据","items":["视频剪辑、数据挖掘与分析"]}],
                "certificates":[],"honors":[],"languages":[]}
                """, revisionId);
        mockMvc.perform(put("/api/v1/job-matches/{id}/resume-selection", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "resumeRevisionId", revisionId,
                                "expectedVersion", corrected.path("version").asInt()))))
                .andExpect(status().isOk());

        // No report yet: the dashboard has no average to show rather than a zero.
        mockMvc.perform(get("/api/v1/job-matches/dashboard").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(1))
                .andExpect(jsonPath("$.data.averageScore").doesNotExist());

        JsonNode similar = data(mockMvc.perform(get("/api/v1/job-matches/{id}/similar-jobs", matchId).cookie(owner))
                .andExpect(status().isOk()).andReturn());
        assertThat(similar.path("resumeSkills").toString())
                .contains("Java", "Python", "Spring Boot", "视频剪辑", "数据挖掘与分析");
        Map<String, List<String>> shared = new LinkedHashMap<>();
        for (JsonNode direction : similar.path("directions")) {
            assertThat(direction.has("matchScore")).isFalse();
            assertThat(direction.path("category").asText()).isNotBlank();
            List<String> skills = new java.util.ArrayList<>();
            direction.path("sharedSkills").forEach(skill -> skills.add(skill.asText()));
            assertThat(skills).isNotEmpty();
            shared.put(direction.path("title").asText(), skills);
        }
        assertThat(shared).containsEntry("Python", List.of("Python"))
                .containsEntry("视频剪辑", List.of("视频剪辑"))
                .containsEntry("数据挖掘", List.of("数据挖掘与分析"))
                // The target role itself and directions without a shared skill are not listed.
                .doesNotContainKeys("Java", "会计", "护士/护理", "JavaScript");

        jdbc.update("UPDATE resume_revisions SET content_json=? WHERE id=?",
                "{\"schemaVersion\":\"resume-content-v3\",\"skills\":[{\"category\":\"通用\",\"items\":[\"沟通\"]}]}",
                revisionId);
        mockMvc.perform(get("/api/v1/job-matches/{id}/similar-jobs", matchId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeSkills[0]").value("沟通"))
                .andExpect(jsonPath("$.data.directions.length()").value(0));
    }

    @Test
    void urlImportRejectsPrivateTargets() throws Exception {
        MockCookie owner = registerAndLogin("match-url+" + System.nanoTime() + "@example.com");
        mockMvc.perform(post("/api/v1/job-matches/jd/fetch-url").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1:8080/actuator\",\"requestId\":\"ssrf-test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("JD_URL_FORBIDDEN"));
        mockMvc.perform(post("/api/v1/job-matches/jd/fetch-url").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://[::1]/internal\",\"requestId\":\"ssrf-ipv6\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("JD_URL_FORBIDDEN"));
        mockMvc.perform(post("/api/v1/job-matches/jd/fetch-url").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://169.254.169.254/latest/meta-data\",\"requestId\":\"ssrf-metadata\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("JD_URL_FORBIDDEN"));
        mockMvc.perform(post("/api/v1/job-matches/jd/fetch-url").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://user:password@example.com/job\",\"requestId\":\"ssrf-userinfo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("JD_URL_INVALID"));
    }

    @Test
    void failedAiAnalysisPausesSafelyAndReleasesQuota() throws Exception {
        doThrow(new IllegalStateException("provider unavailable"))
                .when(gateway).execute(anyString(), any());
        MockCookie owner = registerAndLogin("match-failure+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);

        JsonNode created = createMatch(owner, "create-failure-" + UUID.randomUUID());
        JsonNode corrected = confirmJd(owner, created);
        JsonNode options = data(mockMvc.perform(get("/api/v1/job-matches/{id}/resume-options", created.path("id").asText())
                        .cookie(owner)).andExpect(status().isOk()).andReturn());
        JsonNode resumeConfirmed = data(mockMvc.perform(put("/api/v1/job-matches/{id}/resume-selection", created.path("id").asText())
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "resumeRevisionId", options.get(0).path("revisionId").asText(),
                                "expectedVersion", corrected.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
        JsonNode authorized = data(mockMvc.perform(post("/api/v1/job-matches/{id}/authorization", created.path("id").asText())
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "mode", "NONE", "recordIds", List.of(), "fileIds", List.of(),
                                "scope", "CURRENT_MATCH", "rememberPreference", false,
                                "expectedVersion", resumeConfirmed.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());

        analyze(owner, created.path("id").asText(), "analysis-failure-" + UUID.randomUUID(),
                authorized.path("version").asInt());
        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", created.path("id").asText()).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ANALYSIS_PAUSED"))
                        .andExpect(jsonPath("$.data.errorCode").value("JOB_MATCH_AI_FAILED")));

        mockMvc.perform(get("/api/v1/job-matches/{id}/report", created.path("id").asText()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.preview").value(true))
                .andExpect(jsonPath("$.data.report.scoring.method").value("RULE_PREVIEW"))
                .andExpect(jsonPath("$.data.report.requirements.length()").value(9))
                .andExpect(jsonPath("$.data.report.ai").doesNotExist());

        JsonNode quota = data(mockMvc.perform(get("/api/v1/job-matches/capabilities").cookie(owner))
                .andExpect(status().isOk()).andReturn()).path("quota");
        assertThat(quota.path("usedUnits").asInt()).isZero();
        assertThat(quota.path("heldUnits").asInt()).isZero();
        assertThat(quota.path("remainingUnits").asInt()).isEqualTo(quota.path("grantedUnits").asInt());

        mockMvc.perform(post("/api/v1/job-matches/{id}/resume-analysis", created.path("id").asText())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANALYZING"));
        JsonNode resumedQuota = data(mockMvc.perform(get("/api/v1/job-matches/capabilities").cookie(owner))
                .andExpect(status().isOk()).andReturn()).path("quota");
        assertThat(resumedQuota.path("heldUnits").asInt()).isEqualTo(1);
        assertThat(resumedQuota.path("remainingUnits").asInt())
                .isEqualTo(resumedQuota.path("grantedUnits").asInt() - 1);

        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", created.path("id").asText()).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("ANALYSIS_PAUSED")));
        JsonNode releasedAgain = data(mockMvc.perform(get("/api/v1/job-matches/capabilities").cookie(owner))
                .andExpect(status().isOk()).andReturn()).path("quota");
        assertThat(releasedAgain.path("heldUnits").asInt()).isZero();
        assertThat(releasedAgain.path("remainingUnits").asInt())
                .isEqualTo(releasedAgain.path("grantedUnits").asInt());
    }

    @Test
    void schemaRepairReceivesTheExactValidationFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> repairInstruction = new AtomicReference<>();
        AtomicReference<JsonNode> repairPayload = new AtomicReference<>();
        doAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            JsonNode input = mapper.readTree(request.messages().get(request.messages().size() - 1).content());
            String requirementId = input.path("requirements").get(0).path("id").asText();
            if (attempts.getAndIncrement() == 0) {
                return new Response("job-match-ai-invalid", "system-channel", "qwen-plus",
                        "{\"summary\":{\"headline\":\"类型错误\"},\"hardGates\":{},\"strengths\":[],"
                                + "\"gaps\":[],\"evidenceMatrix\":[],\"clarifications\":[],\"learningPlan\":[],"
                                + "\"resumeSuggestions\":[],\"interviewTopics\":[],"
                                + "\"recommendation\":{\"code\":\"CONDITIONAL\",\"rationale\":\"待核实\"}}",
                        new Usage(120, 30, 150), null);
            }
            repairInstruction.set(input.path("repairInstruction").asText());
            repairPayload.set(input);
            return new Response("job-match-ai-repaired", "system-channel", "qwen-plus",
                    validAiReport(input), new Usage(420, 380, 800), null);
        }).when(gateway).execute(anyString(), any());

        MockCookie owner = registerAndLogin("match-repair+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);
        JsonNode created = createMatch(owner, "create-repair-" + UUID.randomUUID());
        String matchId = created.path("id").asText();
        JsonNode corrected = confirmJd(owner, created);
        JsonNode options = data(mockMvc.perform(get("/api/v1/job-matches/{id}/resume-options", matchId)
                        .cookie(owner)).andExpect(status().isOk()).andReturn());
        String revisionId = options.get(0).path("revisionId").asText();
        jdbc.update("UPDATE resume_revisions SET content_json=? WHERE id=?", """
                {"schemaVersion":"resume-content-v3","basics":{"name":"测试用户","phone":"13800138000",
                "email":"private@example.com","fullAddress":"上海市测试路 1 号","photoRef":"private/photo.png",
                "wechat":"wx_private_id","qq":"80008000","github":"https://github.com/private-user",
                "links":[{"label":"主页","url":"https://private.example.com"}]},
                "summary":"","education":[],"experiences":[],"projects":[],"organizations":[],
                "skills":[],"certificates":[],"honors":[],"languages":[]}
                """, revisionId);
        JsonNode resumeConfirmed = data(mockMvc.perform(put("/api/v1/job-matches/{id}/resume-selection", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "resumeRevisionId", revisionId,
                                "expectedVersion", corrected.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
        JsonNode authorized = data(mockMvc.perform(post("/api/v1/job-matches/{id}/authorization", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "mode", "NONE", "recordIds", List.of(), "fileIds", List.of(),
                                "scope", "CURRENT_MATCH", "rememberPreference", false,
                                "expectedVersion", resumeConfirmed.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());

        analyze(owner, matchId, "analysis-repair-" + UUID.randomUUID(), authorized.path("version").asInt());
        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("COMPLETED")));

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(repairInstruction.get())
                .contains("FIELD_NOT_ARRAY:hardGates")
                .contains("summary 和 recommendation 必须是 JSON 对象");
        assertThat(repairPayload.get().path("editableResumeFields").isArray()).isTrue();
        assertThat(repairPayload.get().path("editableResumeFields").get(0).path("path").asText())
                .isEqualTo("/summary");
        assertThat(repairPayload.get().path("allowedSourceRefs").isArray()).isTrue();
        assertThat(repairPayload.get().path("allowedSourceRefs").toString()).contains("jd:");
        String providerPayload = repairPayload.get().toString();
        assertThat(providerPayload)
                .doesNotContain("13800138000", "private@example.com", "上海市测试路 1 号", "private/photo.png")
                .doesNotContain("wx_private_id", "80008000", "github.com/private-user", "private.example.com", "测试用户")
                .doesNotContain("resume:/basics/phone", "resume:/basics/email", "resume:/basics/fullAddress",
                        "resume:/basics/photoRef");
    }

    @Test
    void aSuggestionCitingAnUnknownSourceIsDroppedWithoutFailingTheReport() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> repairInstruction = new AtomicReference<>();
        doAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            JsonNode input = mapper.readTree(request.messages().get(request.messages().size() - 1).content());
            String requirementId = input.path("requirements").get(0).path("id").asText();
            if (attempts.getAndIncrement() == 0) {
                return new Response("job-match-ai-invalid-source", "system-channel", "qwen-plus",
                        validAiReport(input).replace("jd:" + requirementId, "resume:/basics/email"),
                        new Usage(420, 380, 800), null);
            }
            repairInstruction.set(input.path("repairInstruction").asText());
            return new Response("job-match-ai-repaired", "system-channel", "qwen-plus",
                    validAiReport(input), new Usage(420, 380, 800), null);
        }).when(gateway).execute(anyString(), any());

        MockCookie owner = registerAndLogin("match-source-ref+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);
        JsonNode created = createMatch(owner, "create-source-ref-" + UUID.randomUUID());
        String matchId = created.path("id").asText();
        JsonNode corrected = confirmJd(owner, created);
        JsonNode options = data(mockMvc.perform(get("/api/v1/job-matches/{id}/resume-options", matchId)
                        .cookie(owner)).andExpect(status().isOk()).andReturn());
        JsonNode resumeConfirmed = data(mockMvc.perform(put("/api/v1/job-matches/{id}/resume-selection", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "resumeRevisionId", options.get(0).path("revisionId").asText(),
                                "expectedVersion", corrected.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
        JsonNode authorized = data(mockMvc.perform(post("/api/v1/job-matches/{id}/authorization", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "mode", "NONE", "recordIds", List.of(), "fileIds", List.of(),
                                "scope", "CURRENT_MATCH", "rememberPreference", false,
                                "expectedVersion", resumeConfirmed.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());

        analyze(owner, matchId, "analysis-source-ref-" + UUID.randomUUID(), authorized.path("version").asInt());
        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("COMPLETED")));

        // One weak suggestion no longer costs the user the whole report or a second model run.
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(repairInstruction.get()).isNull();
        JsonNode report = data(mockMvc.perform(get("/api/v1/job-matches/{id}/report", matchId).cookie(owner))
                .andExpect(status().isOk()).andReturn()).path("report");
        assertThat(report.path("ai").path("resumeSuggestions").size()).isZero();
        assertThat(report.toString()).doesNotContain("resume:/basics/email");
    }

    @Test
    void deferredClarificationIsSentToAiAndCannotLoop() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<JsonNode> resumedPayload = new AtomicReference<>();
        doAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            JsonNode input = mapper.readTree(request.messages().get(request.messages().size() - 1).content());
            String requirementId = input.path("requirements").get(0).path("id").asText();
            if (attempts.getAndIncrement() == 0) {
                return new Response("job-match-ai", "system-channel", "qwen-plus",
                        """
                        {"summary":{"headline":"需要确认一项已有事实。"},
                         "hardGates":[],"strengths":[],"gaps":[],"evidenceMatrix":MATRIX,
                         "clarifications":[{"requirementId":"%s","question":"能否确认该项事实？",
                         "options":["可以确认"],"evidenceContext":{}}],"learningPlan":[],
                         "resumeSuggestions":[],"interviewTopics":[],
                         "recommendation":{"code":"CONDITIONAL","rationale":"待确认"}}
                        """.formatted(requirementId).replace("MATRIX", matrix(input)), new Usage(420, 380, 800), null);
            }
            resumedPayload.set(input);
            return new Response("job-match-ai", "system-channel", "qwen-plus",
                    validAiReport(input), new Usage(420, 380, 800), null);
        }).when(gateway).execute(anyString(), any());

        MockCookie owner = registerAndLogin("match-deferred+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);
        JsonNode created = createMatch(owner, "create-deferred-" + UUID.randomUUID());
        String matchId = created.path("id").asText();
        JsonNode corrected = confirmJd(owner, created);
        JsonNode options = data(mockMvc.perform(get("/api/v1/job-matches/{id}/resume-options", matchId)
                        .cookie(owner)).andExpect(status().isOk()).andReturn());
        JsonNode resumeConfirmed = data(mockMvc.perform(put("/api/v1/job-matches/{id}/resume-selection", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "resumeRevisionId", options.get(0).path("revisionId").asText(),
                                "expectedVersion", corrected.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
        JsonNode authorized = data(mockMvc.perform(post("/api/v1/job-matches/{id}/authorization", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "mode", "NONE", "recordIds", List.of(), "fileIds", List.of(),
                                "scope", "CURRENT_MATCH", "rememberPreference", false,
                                "expectedVersion", resumeConfirmed.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());

        analyze(owner, matchId, "analysis-deferred-" + UUID.randomUUID(), authorized.path("version").asInt());
        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("NEEDS_CLARIFICATION"))
                        .andExpect(jsonPath("$.data.clarifications[0].options[1]").value("暂不确认")));

        JsonNode needsClarification = data(mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(post("/api/v1/job-matches/{id}/clarifications", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "answers", List.of(),
                                "continueWithPending", true,
                                "expectedVersion", needsClarification.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANALYZING"))
                .andExpect(jsonPath("$.data.clarifications[0].status").value("DEFERRED"));

        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.data.clarifications.length()").value(1))
                        .andExpect(jsonPath("$.data.clarifications[0].status").value("DEFERRED")));
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(resumedPayload.get().path("clarifications").get(0).path("status").asText())
                .isEqualTo("DEFERRED");
    }

    @Test
    void answeredClarificationIsNotInsertedAgainWhenAnalysisResumes() throws Exception {
        doAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            JsonNode input = mapper.readTree(request.messages().get(request.messages().size() - 1).content());
            String requirementId = input.path("requirements").get(0).path("id").asText();
            return new Response("job-match-ai", "system-channel", "qwen-plus",
                    aiReportWithClarification(input), new Usage(420, 380, 800), null);
        }).when(gateway).execute(anyString(), any());
        MockCookie owner = registerAndLogin("match-clarification+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);

        JsonNode created = createMatch(owner, "create-clarification-" + UUID.randomUUID());
        String matchId = created.path("id").asText();
        JsonNode corrected = confirmJd(owner, created);
        JsonNode options = data(mockMvc.perform(get("/api/v1/job-matches/{id}/resume-options", matchId)
                        .cookie(owner)).andExpect(status().isOk()).andReturn());
        JsonNode resumeConfirmed = data(mockMvc.perform(put("/api/v1/job-matches/{id}/resume-selection", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "resumeRevisionId", options.get(0).path("revisionId").asText(),
                                "expectedVersion", corrected.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
        JsonNode authorized = data(mockMvc.perform(post("/api/v1/job-matches/{id}/authorization", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "mode", "NONE", "recordIds", List.of(), "fileIds", List.of(),
                                "scope", "CURRENT_MATCH", "rememberPreference", false,
                                "expectedVersion", resumeConfirmed.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());

        analyze(owner, matchId, "analysis-clarification-" + UUID.randomUUID(), authorized.path("version").asInt());
        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("NEEDS_CLARIFICATION"))
                        .andExpect(jsonPath("$.data.clarifications.length()").value(1)));

        JsonNode needsClarification = data(mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                .andExpect(status().isOk()).andReturn());
        JsonNode clarification = needsClarification.path("clarifications").get(0);
        mockMvc.perform(post("/api/v1/job-matches/{id}/clarifications", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "answers", List.of(Map.of(
                                        "id", clarification.path("id").asText(),
                                        "answerCode", clarification.path("options").get(0).asText(),
                                        "note", "")),
                                "continueWithPending", false,
                                "expectedVersion", needsClarification.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANALYZING"));

        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.data.clarifications.length()").value(1))
                        .andExpect(jsonPath("$.data.clarifications[0].status").value("ANSWERED")));
        assertThat(clarification.path("options").get(0).asText()).isEqualTo("只在课程项目里做过");
        // The model calls it MET from the resume, but the user's own answer says only partly.
        String firstRequirement = needsClarification.path("clarifications").get(0).path("requirementId").asText();
        JsonNode answered = data(mockMvc.perform(get("/api/v1/job-matches/{id}/report", matchId).cookie(owner))
                .andExpect(status().isOk()).andReturn()).path("report");
        assertThat(answered.path("requirements")).filteredOn(item -> firstRequirement.equals(item.path("requirementId").asText()))
                .singleElement().satisfies(item -> assertThat(item.path("verdict").asText()).isEqualTo("PARTIAL"));

        mockMvc.perform(get("/api/v1/job-matches/{id}/report/versions", matchId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].version").value(2));
        mockMvc.perform(get("/api/v1/job-matches/{id}/report/compare", matchId).cookie(owner)
                        .param("fromVersion", "1").param("toVersion", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fromVersion").value(1))
                .andExpect(jsonPath("$.data.toVersion").value(2))
                .andExpect(jsonPath("$.data.changes.length()").value(5));
    }

    @Test
    void aModelThatKeepsAskingGetsOneFollowUpRoundAndThenAFinalReport() throws Exception {
        // Each run asks about the next requirement nobody was asked about yet: without a cap this never ends.
        doAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            JsonNode input = mapper.readTree(request.messages().get(request.messages().size() - 1).content());
            int asked = input.path("clarifications").size();
            String requirementId = input.path("requirements").path(asked).path("id").asText("");
            String report = """
                    {"summary":{"headline":"还需确认一项事实。"},"hardGates":[],"strengths":[],"gaps":[],
                     "evidenceMatrix":MATRIX,
                     "clarifications":[{"requirementId":"%s","question":"第 %d 轮：能否确认该项？",
                       "options":[{"label":"可以确认","verdict":"MET"},{"label":"暂不确认职责范围","verdict":"UNCLEAR"}],"evidenceContext":{}}],
                     "learningPlan":[],"resumeSuggestions":[],"interviewTopics":[],
                     "recommendation":{"code":"CONDITIONAL","rationale":"确认后再下结论。"}}
                    """.formatted(requirementId, asked + 1).replace("MATRIX", matrix(input));
            return new Response("job-match-ai", "system-channel", "qwen-plus", report, new Usage(420, 380, 800), null);
        }).when(gateway).execute(anyString(), any());
        MockCookie owner = registerAndLogin("match-rounds+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);
        JsonNode authorized = authorizedMatch(owner);
        String matchId = authorized.path("id").asText();
        analyze(owner, matchId, "analysis-rounds-" + UUID.randomUUID(), authorized.path("version").asInt());

        for (int round = 1; round <= MAX_ROUNDS_IN_TEST; round++) {
            int expectedQuestions = round;
            await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                    mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                            .andExpect(jsonPath("$.data.status").value("NEEDS_CLARIFICATION"))
                            .andExpect(jsonPath("$.data.clarifications.length()").value(expectedQuestions)));
            JsonNode waiting = data(mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner)).andReturn());
            JsonNode pending = waiting.path("clarifications").get(round - 1);
            assertThat(pending.path("options")).extracting(JsonNode::asText)
                    .as("the model's own 暂不确认… is folded into the standard one").containsExactly("可以确认", "暂不确认");
            mockMvc.perform(post("/api/v1/job-matches/{id}/clarifications", matchId)
                            .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "answers", List.of(Map.of("id", pending.path("id").asText(), "answerCode", "可以确认", "note", "")),
                                    "continueWithPending", false,
                                    "expectedVersion", waiting.path("version").asInt()))))
                    .andExpect(status().isOk());
            // Two submissions within one millisecond would share answered_at and read as one round.
            Thread.sleep(5);
        }

        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.data.clarifications.length()").value(MAX_ROUNDS_IN_TEST)));
    }

    private static final int MAX_ROUNDS_IN_TEST = 2;

    private JsonNode createMatch(Cookie owner, String requestId) throws Exception {
        return data(mockMvc.perform(post("/api/v1/job-matches").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "sourceType", "TEXT", "text", jd(), "requestId", requestId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("JD_PARSED"))
                .andReturn());
    }

    private JsonNode confirmJd(Cookie owner, JsonNode match) throws Exception {
        List<Map<String, Object>> requirements = new java.util.ArrayList<>();
        for (JsonNode item : match.path("requirements")) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", item.path("id").asText());
            value.put("category", item.path("category").asText());
            value.put("text", item.path("text").asText());
            value.put("priority", item.path("priority").asText());
            value.put("hardGate", item.path("hardGate").asBoolean());
            value.put("sourceLocator", item.path("sourceLocator").asText());
            value.put("sourceQuote", item.path("sourceQuote").asText());
            requirements.add(value);
        }
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("title", match.path("title").asText());
        command.put("company", match.path("company").asText());
        command.put("location", "上海");
        command.put("workMode", "ONSITE");
        command.put("requirements", requirements);
        command.put("expectedVersion", match.path("version").asInt());
        return data(mockMvc.perform(patch("/api/v1/job-matches/{id}/jd-structure", match.path("id").asText())
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkpoint").value("JD_CONFIRMED"))
                .andReturn());
    }

    private JsonNode analyze(Cookie owner, String matchId, String requestId, int expectedVersion) throws Exception {
        return data(mockMvc.perform(post("/api/v1/job-matches/{id}/analyze", matchId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "requestId", requestId, "expectedVersion", expectedVersion,
                                "outputOptions", Map.of("redacted", true, "language", "zh-CN")))))
                .andExpect(status().isAccepted()).andReturn());
    }

    @Test
    void analysisCancelledWhileTheModelAnswersKeepsNoReport() throws Exception {
        AtomicReference<String> cancelledMatch = new AtomicReference<>();
        doAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            JsonNode input = mapper.readTree(request.messages().get(request.messages().size() - 1).content());
            // The user presses 取消 while the model is still writing.
            jdbc.update("UPDATE job_match_tasks SET status='CANCELLED',checkpoint_code='CANCELLED' WHERE id=?",
                    input.path("matchId").asText());
            cancelledMatch.set(input.path("matchId").asText());
            return new Response("job-match-ai", "system-channel", "qwen-plus",
                    validAiReport(input), new Usage(420, 380, 800), null);
        }).when(gateway).execute(anyString(), any());
        MockCookie owner = registerAndLogin("match-cancel+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);
        JsonNode authorized = authorizedMatch(owner);
        String matchId = authorized.path("id").asText();

        analyze(owner, matchId, "analysis-cancel-" + UUID.randomUUID(), authorized.path("version").asInt());
        await().atMost(Duration.ofSeconds(12)).until(() -> cancelledMatch.get() != null);
        await().atMost(Duration.ofSeconds(12)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM match_ai_advice_runs r JOIN match_reports m ON m.id=r.report_id WHERE m.match_id=? AND r.status='CANCELLED'",
                Integer.class, matchId)).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT status FROM job_match_tasks WHERE id=?", String.class, matchId))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_match_report_versions WHERE match_id=?",
                Integer.class, matchId)).isZero();
        // A cancelled match can be analysed again.
        int version = jdbc.queryForObject("SELECT version_no FROM job_match_tasks WHERE id=?", Integer.class, matchId);
        doAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            JsonNode input = mapper.readTree(request.messages().get(request.messages().size() - 1).content());
            return new Response("job-match-ai", "system-channel", "qwen-plus",
                    validAiReport(input), new Usage(420, 380, 800), null);
        }).when(gateway).execute(anyString(), any());
        analyze(owner, matchId, "analysis-again-" + UUID.randomUUID(), version);
        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("COMPLETED")));
    }

    @Test
    void unbackedVerdictsBecomeUnclearAndAMissingHardRequirementCapsTheAdvice() throws Exception {
        doAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            JsonNode input = mapper.readTree(request.messages().get(request.messages().size() - 1).content());
            java.util.List<String> items = new java.util.ArrayList<>();
            boolean missingMarked = false;
            for (JsonNode requirement : input.path("requirements")) {
                String id = requirement.path("id").asText();
                // Claims every requirement is met but cites nothing, and calls one hard requirement missing.
                boolean missing = !missingMarked && requirement.path("hard_gate").asBoolean();
                missingMarked |= missing;
                String status = missing ? "NOT_FOUND" : "MET";
                items.add("{\"requirementId\":\"" + id + "\",\"status\":\"" + status + "\",\"resumeRefs\":[],\"evidenceIds\":[]}");
            }
            String body = validAiReport(input).replaceFirst("\"evidenceMatrix\":\\[.*?\\],\"clarifications\"",
                    java.util.regex.Matcher.quoteReplacement("\"evidenceMatrix\":[" + String.join(",", items) + "],\"clarifications\""))
                    .replace("\"code\":\"CONDITIONAL\"", "\"code\":\"RECOMMENDED\"");
            return new Response("job-match-ai", "system-channel", "qwen-plus", body, new Usage(420, 380, 800), null);
        }).when(gateway).execute(anyString(), any());
        MockCookie owner = registerAndLogin("match-verdicts+" + System.nanoTime() + "@example.com");
        createStructuredResume(owner);
        JsonNode authorized = authorizedMatch(owner);
        String matchId = authorized.path("id").asText();
        analyze(owner, matchId, "analysis-verdicts-" + UUID.randomUUID(), authorized.path("version").asInt());
        await().atMost(Duration.ofSeconds(12)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/job-matches/{id}", matchId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("COMPLETED")));
        JsonNode report = data(mockMvc.perform(get("/api/v1/job-matches/{id}/report", matchId).cookie(owner))
                .andExpect(status().isOk()).andReturn()).path("report");
        assertThat(report.path("requirements").findValuesAsText("verdict")).doesNotContain("MET");
        assertThat(report.path("hardGatePassed").asBoolean()).isFalse();
        assertThat(report.path("recommendation").path("code").asText()).isEqualTo("CAUTION");
        assertThat(report.path("recommendation").path("capped").asBoolean()).isTrue();
        assertThat(report.path("recommendation").path("rationale").asText()).contains("硬性要求");
        assertThat(report.path("confidence").asText()).isEqualTo("LOW");
        assertThat(report.path("strengths").size()).isZero();
    }

    /** A match with JD confirmed, the first resume chosen and no extra evidence authorized. */
    private JsonNode authorizedMatch(Cookie owner) throws Exception {
        JsonNode created = createMatch(owner, "create-" + UUID.randomUUID());
        String matchId = created.path("id").asText();
        JsonNode corrected = confirmJd(owner, created);
        JsonNode options = data(mockMvc.perform(get("/api/v1/job-matches/{id}/resume-options", matchId)
                        .cookie(owner)).andExpect(status().isOk()).andReturn());
        JsonNode resumeConfirmed = data(mockMvc.perform(put("/api/v1/job-matches/{id}/resume-selection", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "resumeRevisionId", options.get(0).path("revisionId").asText(),
                                "expectedVersion", corrected.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
        return data(mockMvc.perform(post("/api/v1/job-matches/{id}/authorization", matchId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "mode", "NONE", "recordIds", List.of(), "fileIds", List.of(),
                                "scope", "CURRENT_MATCH", "rememberPreference", false,
                                "expectedVersion", resumeConfirmed.path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
    }

    private void assertExport(Cookie owner, String matchId, String format, byte[] prefix) throws Exception {
        JsonNode created = data(mockMvc.perform(post("/api/v1/job-matches/{id}/exports", matchId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "format", format, "sections", List.of("ALL"), "redacted", true,
                                "language", "zh-CN", "requestId", "export-" + format + "-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn());
        byte[] body = mockMvc.perform(get("/api/v1/job-matches/exports/{id}/download", created.path("id").asText())
                        .cookie(owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(body).startsWith(prefix);
    }

    private void createStructuredResume(Cookie owner) throws Exception {
        mockMvc.perform(post("/api/v1/ai-resume/conversations").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"Java 后端求职简历\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.schemaVersion").value("resume-content-v3"));
    }

    private MockCookie registerAndLogin(String email) throws Exception {
        String body = mapper.writeValueAsString(Map.of("email", email, "password", "Passw0rd!"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return new MockCookie("jobproof_session", result.getResponse().getCookie("jobproof_session").getValue());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    /** The model's verdict table: every requirement met, each citing the first resume field it may cite. */
    private static String matrix(JsonNode input) {
        String ref = null;
        for (JsonNode allowed : input.path("allowedSourceRefs")) {
            if (allowed.asText().startsWith("resume:")) { ref = allowed.asText(); break; }
        }
        java.util.List<String> items = new java.util.ArrayList<>();
        for (JsonNode requirement : input.path("requirements")) {
            items.add(ref == null
                    ? "{\"requirementId\":\"%s\",\"status\":\"UNCLEAR\",\"resumeRefs\":[],\"evidenceIds\":[],\"reason\":\"信息不足\"}"
                            .formatted(requirement.path("id").asText())
                    : "{\"requirementId\":\"%s\",\"status\":\"MET\",\"resumeRefs\":[\"%s\"],\"evidenceIds\":[],\"reason\":\"简历中有对应经历\"}"
                            .formatted(requirement.path("id").asText(), ref));
        }
        return "[" + String.join(",", items) + "]";
    }

    private static String validAiReport(JsonNode input) {
        String requirementId = input.path("requirements").path(0).path("id").asText("");
        return """
                {"summary":{"headline":"简历具备基础后端能力，仍需补强可验证的性能与微服务实践。"},
                 "hardGates":[],"strengths":[],"gaps":[],"evidenceMatrix":MATRIX,"clarifications":[],
                 "learningPlan":[],"resumeSuggestions":[{"requirementId":"%s","targetPath":"/summary","beforeValue":"","proposedValue":"具备扎实的 Java 与 Spring Boot 后端开发基础，能够围绕真实业务需求完成接口设计、数据建模、自动化测试和交付验证；在项目实践中重视问题定位、代码可维护性与跨团队协作，通过需求拆解、方案评审和复盘沉淀持续完善工程方法，并能够根据监控指标与测试结果验证实现质量，为后续参与微服务治理、数据库优化和稳定性建设奠定可靠基础。","reason":"突出与岗位职责直接相关的工程能力，并保持所有表述来自当前简历事实。","sourceRefs":["jd:%s"]}],"interviewTopics":[],
                 "recommendation":{"code":"CONDITIONAL","rationale":"先补充关键项目证据后再决定投递优先级。"}}
                """.formatted(requirementId, requirementId).replace("MATRIX", matrix(input));
    }

    private static String aiReportWithClarification(JsonNode input) {
        String requirementId = input.path("requirements").path(0).path("id").asText("");
        return """
                {"summary":{"headline":"需要确认一项已有事实。"},
                 "hardGates":[],"strengths":[],"gaps":[],"evidenceMatrix":MATRIX,
                 "clarifications":[{"requirementId":"%s","question":"能否确认该项事实？","options":[{"label":"只在课程项目里做过","verdict":"PARTIAL"},{"label":"暂不确认","verdict":"UNCLEAR"}],"evidenceContext":{}}],
                 "learningPlan":[],"resumeSuggestions":[],"interviewTopics":[],
                 "recommendation":{"code":"CONDITIONAL","rationale":"完成事实确认后再生成最终结论。"}}
                """.formatted(requirementId).replace("MATRIX", matrix(input));
    }

    private static String jd() {
        return """
                岗位名称: Java 后端工程师
                公司名称: 合成科技有限公司
                工作地点: 上海

                岗位职责:
                1. 负责订单与履约服务的需求分析、领域建模、接口设计和稳定交付，并通过可观测指标持续验证系统质量。
                2. 参与微服务治理、数据库性能优化和线上故障复盘，与产品、测试和平台团队协作推进方案落地。
                3. 建设自动化测试、持续集成和发布检查机制，保证核心交易链路在高并发场景下保持可靠。

                任职要求:
                1. 必须具备本科及以上学历，计算机、软件工程或相关专业，能够独立阅读技术文档并进行方案说明。
                2. 至少具备两年 Java 开发经验，熟悉 Spring Boot、MySQL、Redis 及常见消息队列的工程实践。
                3. 具备数据结构、网络、操作系统和关系数据库基础，能够定位接口延迟、资源竞争与一致性问题。
                4. 具备清晰沟通和跨团队协作能力，能够把业务目标拆解为可验收的技术任务与迭代计划。

                加分项:
                1. 有 Kubernetes、云原生可观测平台或大规模交易系统实践经验者优先。
                2. 有开源贡献、技术文章或可复核的性能优化案例者优先。
                """;
    }
}
