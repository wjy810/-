package com.jobproof;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.AiRecommendation;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.AiCanvasNode;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.AiResult;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.BatchValidationItem;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.BatchValidationPayload;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.InterviewPayload;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.AiProposalItem;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.CanvasPayload;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.ProposalPayload;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.RecommendationPayload;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.ValidationPayload;
import com.jobproof.modules.careerplanning.application.CareerPlanningAsyncService;
import com.jobproof.modules.careerplanning.application.CareerPlanningService;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.InterviewQuestion;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.shared.auth.CurrentAccount;
import java.util.List;
import java.util.UUID;
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

@SpringBootTest(properties = {"jobproof.worker.in-process=false", "jobproof.worker.poll-ms=600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareerPlanningIT {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CareerPlanningAsyncService careerPlanningAsync;
    @Autowired CareerPlanningService careerPlanning;
    @Autowired TaskService taskService;
    @MockBean CareerPlanningAiService ai;

    @Test
    void streamingInterviewRollsBackRoundMessagesAndEventsWhenTaskCompletionLosesToCancellation() throws Exception {
        MockCookie owner = registerAndLogin("career-interview-cancel+" + System.nanoTime() + "@example.com");
        MvcResult started = mockMvc.perform(post("/api/v1/career-planning/sessions").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryMode\":\"AI_DISCOVERY\",\"aiConsent\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = data(started).path("id").asText();
        mockMvc.perform(put("/api/v1/career-planning/sessions/{id}/profile", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"basics":{"identity":"应届生","education":"本科"},
                         "preferences":{},"constraints":{},
                         "items":[{"section":"SKILLS","claimType":"SELF_REPORTED","title":"Java",
                           "payload":{},"sourceRefs":[],"locked":false,"sortOrder":1}],
                         "expectedVersion":0}
                        """))
                .andExpect(status().isOk());
        String accountId = jdbc.queryForObject(
                "SELECT account_id FROM career_planning_sessions WHERE id=?", String.class, sessionId);
        String profileId = jdbc.queryForObject(
                "SELECT current_profile_id FROM career_planning_sessions WHERE id=?", String.class, sessionId);
        when(ai.generateQuestionsStreaming(anyString(), any(), any(), any(), any())).thenReturn(new AiResult<>(
                new InterviewPayload("为了保证建议可靠，我需要再确认一项信息。", List.of(
                        new InterviewQuestion("q-cancel", "你希望优先发展哪类后端能力？",
                                "确认学习重点", "SELF_REPORTED"))),
                "test-model", 12, 24, "cancelled-response-hash"));

        CurrentAccount current = new CurrentAccount(accountId, "", "SEEKER", "");
        assertThatThrownBy(() -> careerPlanning.startInterviewStreaming(
                current, sessionId, "cancel-at-commit", ignored -> {}, () -> false, ignored -> false))
                .hasMessageContaining("AI 访谈已取消");

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM career_planning_interview_rounds WHERE profile_id=?",
                Integer.class, profileId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM career_planning_messages WHERE session_id=? AND message_type='QUESTION_BATCH'",
                Integer.class, sessionId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM career_planning_stream_events WHERE session_id=? AND event_type='interview.started'",
                Integer.class, sessionId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT phase_code FROM career_planning_sessions WHERE id=?", String.class, sessionId)).isEqualTo("PROFILE");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT status FROM career_planning_profiles WHERE id=?", String.class, profileId)).isEqualTo("DRAFT");
    }

    @Test
    void confirmedGoalGeneratesEditableVersionedCanvasAndRejectsDependencyCycles() throws Exception {
        MockCookie owner = registerAndLogin("career-planning-owner+" + System.nanoTime() + "@example.com");
        MockCookie other = registerAndLogin("career-planning-other+" + System.nanoTime() + "@example.com");

        mockMvc.perform(get("/api/v1/career-planning").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.session").doesNotExist());

        MvcResult started = mockMvc.perform(post("/api/v1/career-planning/sessions").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryMode\":\"AI_DISCOVERY\",\"aiConsent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phase").value("PROFILE"))
                .andReturn();
        JsonNode startData = data(started);
        String sessionId = startData.path("id").asText();

        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}", sessionId).cookie(other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CP_SESSION_NOT_FOUND"));

        MvcResult updated = mockMvc.perform(put("/api/v1/career-planning/sessions/{id}/profile", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"basics":{"identity":"应届生","education":"本科","major":"软件工程",
                          "experienceYears":"1年以内","weeklyLearningHours":"10~15小时"},
                         "preferences":{"directionPreference":"软件研发","workMode":"混合办公"},
                         "constraints":{"weeklyLearningHours":"10~15小时"},
                         "items":[{"section":"SKILLS","claimType":"SELF_REPORTED","title":"Java",
                           "payload":{"origin":"PROFILE_FORM"},"sourceRefs":[],"locked":false,"sortOrder":1}],
                         "expectedVersion":0}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.version").value(1))
                .andReturn();
        String firstItemId = data(updated).path("profile").path("items").get(0).path("id").asText();

        MvcResult record = mockMvc.perform(post("/api/v1/career-library/records").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PROJECT\",\"title\":\"后端接口项目\",\"description\":\"实现结构化接口与测试\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String authorizedEvidenceId = data(record).path("id").asText();

        mockMvc.perform(put("/api/v1/career-planning/sessions/{id}/evidence", sessionId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selections\":[{\"sourceId\":\"%s\",\"scopes\":[\"PROFILE_INTERVIEW\",\"PLAN\"]}]}".formatted(authorizedEvidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0].sourceId").value(authorizedEvidenceId))
                .andExpect(jsonPath("$.data.permissions[0].scopes[0]").value("PROFILE_INTERVIEW"))
                .andExpect(jsonPath("$.data.permissions[0].scopes[1]").value("PLAN"));

        mockMvc.perform(put("/api/v1/career-planning/sessions/{id}/evidence", sessionId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selections\":[{\"sourceId\":\"%s\",\"scopes\":[]}] }".formatted(authorizedEvidenceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CP_EVIDENCE_SCOPE_REQUIRED"));

        mockMvc.perform(put("/api/v1/career-planning/sessions/{id}/profile", sessionId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"basics\":{},\"preferences\":{},\"constraints\":{},\"items\":[],\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("CP_PROFILE_VERSION_CONFLICT"));

        mockMvc.perform(put("/api/v1/career-planning/sessions/{id}/evidence", sessionId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"selections\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phase").value("EVIDENCE"));

        when(ai.generateQuestions(anyString(), any(), any())).thenReturn(new AiResult<>(
                List.of(new InterviewQuestion("q1", "你更希望专注后端服务还是完整产品交付？",
                        "确认方向偏好", "SELF_REPORTED")),
                "test-model", 10, 20, "question-hash"));
        when(ai.generateQuestionsStreaming(anyString(), any(), any(), any(), any())).thenReturn(new AiResult<>(
                new InterviewPayload("为了减少不可靠推断，我还需要确认一项信息。", List.of(
                        new InterviewQuestion("q1", "你更希望专注后端服务还是完整产品交付？",
                                "确认方向偏好", "SELF_REPORTED"))),
                "test-model", 10, 20, "question-hash"));
        MvcResult interviewTaskSubmitted = mockMvc.perform(post(
                        "/api/v1/career-planning/sessions/{id}/interviews/tasks", sessionId)
                        .cookie(owner).header("Idempotency-Key", "career-planning-test-interview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"career-planning-test-interview\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String interviewTaskId = data(interviewTaskSubmitted).path("id").asText();
        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/interviews/tasks", sessionId)
                        .cookie(owner).header("Idempotency-Key", "career-planning-test-interview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"career-planning-test-interview\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(interviewTaskId));
        var claimedInterviewTask = taskService.claimNext(TaskTypes.CAREER_PLANNING_INTERVIEW).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(claimedInterviewTask.getId()).isEqualTo(interviewTaskId);
        careerPlanningAsync.processClaimedTask(claimedInterviewTask);
        mockMvc.perform(get("/api/v1/tasks/{id}", interviewTaskId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        MvcResult interview = mockMvc.perform(get("/api/v1/career-planning/sessions/{id}", sessionId)
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phase").value("INTERVIEW"))
                .andExpect(jsonPath("$.data.interviewRounds[0].questions[0].id").value("q1"))
                .andExpect(jsonPath("$.data.messages[?(@.type == 'INTERVIEW_REQUEST')]").exists())
                .andExpect(jsonPath("$.data.messages[?(@.type == 'QUESTION_BATCH')]").exists())
                .andReturn();
        JsonNode interviewData = data(interview);
        String roundId = interviewData.path("interviewRounds").get(0).path("id").asText();
        int interviewProfileVersion = interviewData.path("profile").path("version").asInt();

        mockMvc.perform(put("/api/v1/career-planning/sessions/{id}/interviews/{roundId}/draft", sessionId, roundId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"answers":[{"questionId":"q1","question":"客户端不能覆盖服务端问题文本",
                          "answer":"优先后端服务，同时愿意补充基本前端协作能力"}],"expectedProfileVersion":%d}
                        """.formatted(interviewProfileVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phase").value("INTERVIEW"))
                .andExpect(jsonPath("$.data.profile.version").value(interviewProfileVersion))
                .andExpect(jsonPath("$.data.interviewRounds[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.interviewRounds[0].answers[0].question")
                        .value("你更希望专注后端服务还是完整产品交付？"))
                .andExpect(jsonPath("$.data.interviewRounds[0].answers[0].answer")
                        .value("优先后端服务，同时愿意补充基本前端协作能力"));

        MvcResult answered = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/interviews/{roundId}/answers", sessionId, roundId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"answers":[{"questionId":"q1","question":"你更希望专注后端服务还是完整产品交付？",
                          "answer":"优先后端服务，同时愿意补充基本前端协作能力"}],"expectedProfileVersion":%d}
                        """.formatted(interviewProfileVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phase").value("PROFILE_CONFIRMATION"))
                .andReturn();
        JsonNode answerData = data(answered);
        String interviewItemId = answerData.path("profile").path("items").get(1).path("id").asText();
        int answerVersion = answerData.path("profile").path("version").asInt();

        MvcResult confirmed = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/profile/confirm", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedItemIds\":[\"" + firstItemId + "\",\"" + interviewItemId
                                + "\"],\"expectedVersion\":" + answerVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.profile.snapshotHash").isNotEmpty())
                .andExpect(jsonPath("$.data.phase").value("RECOMMENDATIONS"))
                .andReturn();

        String javaId = taxonomyId("Java");
        String backendId = taxonomyId("后端开发");
        String testId = taxonomyId("软件测试");
        List<String> refs = List.of("PROFILE_ITEM:" + firstItemId);
        when(ai.generateRecommendations(anyString(), any(), any())).thenReturn(new AiResult<>(
                new RecommendationPayload("READY", List.of(
                        recommendation(javaId, "Java", "READY_NOW", refs),
                        recommendation(backendId, "后端开发", "AFTER_SMALL_GAP", refs),
                        recommendation(testId, "软件测试", "EXPLORATORY", refs)), List.of()),
                "test-model", 100, 180, "recommendation-hash"));

        MvcResult recommendationTaskSubmitted = mockMvc.perform(post(
                        "/api/v1/career-planning/sessions/{id}/recommendations/tasks", sessionId)
                        .cookie(owner).header("Idempotency-Key", "career-planning-test-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"career-planning-test-recommendations\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String recommendationTaskId = data(recommendationTaskSubmitted).path("id").asText();
        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/recommendations/tasks", sessionId)
                        .cookie(owner).header("Idempotency-Key", "career-planning-test-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"career-planning-test-recommendations\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(recommendationTaskId));
        var claimedRecommendationTask = taskService.claimNext(TaskTypes.CAREER_PLANNING_RECOMMENDATIONS).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(claimedRecommendationTask.getId()).isEqualTo(recommendationTaskId);
        careerPlanningAsync.processClaimedTask(claimedRecommendationTask);
        mockMvc.perform(get("/api/v1/tasks/{id}", recommendationTaskId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.progressPercent").value(100));
        MvcResult recommendations = mockMvc.perform(get("/api/v1/career-planning/sessions/{id}", sessionId)
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendationSet.status").value("READY"))
                .andExpect(jsonPath("$.data.recommendationSet.recommendations.length()").value(3))
                .andReturn();
        JsonNode recommendationData = data(recommendations).path("recommendationSet");
        String setId = recommendationData.path("id").asText();
        String recommendationId = recommendationData.path("recommendations").get(0).path("id").asText();

        mockMvc.perform(put("/api/v1/career-planning/sessions/{id}/recommendations/{recommendationId}/favorite", sessionId, recommendationId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations[0].favorite").value(true));

        MvcResult tokenResult = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/recommendation-sets/{setId}/goal-confirmation", sessionId, setId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendationId\":\"" + recommendationId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        String token = data(tokenResult).path("token").asText();
        int sessionVersion = data(confirmed).path("version").asInt();
        MvcResult goal = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/recommendation-sets/{setId}/goal", sessionId, setId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendationId\":\"" + recommendationId + "\",\"confirmationToken\":\""
                                + token + "\",\"expectedSessionVersion\":" + (sessionVersion + 1) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phase").value("CANVAS"))
                .andExpect(jsonPath("$.data.activeGoal.title").value("Java"))
                .andExpect(jsonPath("$.data.canvas.version").value(1))
                .andExpect(jsonPath("$.data.canvas.nodes.length()").value(1))
                .andExpect(jsonPath("$.data.canvas.nodes[0].type").value("CAREER"))
                .andExpect(jsonPath("$.data.canvas.nodes[0].locked").value(true))
                .andReturn();
        String goalId = data(goal).path("activeGoal").path("id").asText();
        Integer activeGoalCount = jdbc.queryForObject("SELECT COUNT(*) FROM career_goals WHERE account_id=(SELECT account_id FROM career_goals WHERE id=?) AND status='ACTIVE'", Integer.class, goalId);
        Integer canvasVersionCount = jdbc.queryForObject("SELECT COUNT(*) FROM canvas_versions WHERE goal_id=?", Integer.class, goalId);
        org.assertj.core.api.Assertions.assertThat(activeGoalCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(canvasVersionCount).isEqualTo(1);

        List<String> canvasRefs = List.of("GOAL:" + goalId);
        when(ai.generateCanvas(anyString(), any(), any(), any())).thenReturn(new AiResult<>(new CanvasPayload(List.of(
                canvasNode("backend", "DOMAIN", "后端开发", "ROOT", canvasRefs),
                canvasNode("data", "DOMAIN", "数据库", "ROOT", canvasRefs),
                canvasNode("engineering", "DOMAIN", "工程化", "ROOT", canvasRefs),
                canvasNode("delivery", "DOMAIN", "项目交付", "ROOT", canvasRefs),
                canvasNode("java", "SKILL", "Java 核心", "backend", canvasRefs),
                canvasNode("spring", "KNOWLEDGE", "Spring Boot", "backend", canvasRefs, "java"),
                canvasNode("api-task", "TASK", "实现订单 API", "spring", canvasRefs, "java"),
                canvasNode("api-evidence", "EVIDENCE", "接口测试报告", "api-task", canvasRefs),
                canvasNode("sql", "SKILL", "SQL", "data", canvasRefs))),
                "test-model", 260, 520, "canvas-response-hash"));

        MvcResult submitted = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/tasks", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"career-canvas-generate-test\",\"expectedVersion\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.progressPercent").value(0))
                .andExpect(jsonPath("$.data.checkpointCode").value("QUEUED"))
                .andReturn();
        String canvasTaskId = data(submitted).path("id").asText();

        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/tasks", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"career-canvas-generate-test\",\"expectedVersion\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(canvasTaskId));
        mockMvc.perform(get("/api/v1/tasks/{id}", canvasTaskId).cookie(other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.reason").value("OBJECT_FORBIDDEN"));

        var claimedCanvasTask = taskService.claimNext(TaskTypes.CAREER_PLANNING_CANVAS).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(claimedCanvasTask.getId()).isEqualTo(canvasTaskId);
        org.assertj.core.api.Assertions.assertThat(claimedCanvasTask.getProgressPercent()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(claimedCanvasTask.getCheckpointCode()).isEqualTo("STARTING");
        careerPlanningAsync.processClaimedTask(claimedCanvasTask);

        mockMvc.perform(get("/api/v1/tasks/{id}", canvasTaskId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.progressPercent").value(100))
                .andExpect(jsonPath("$.data.checkpointCode").value("COMPLETED"))
                .andExpect(jsonPath("$.data.resultVersion").value("career-canvas-v2"));

        MvcResult generated = mockMvc.perform(get("/api/v1/career-planning/sessions/{id}", sessionId)
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canvas.version").value(2))
                .andExpect(jsonPath("$.data.canvas.parentVersionId").isNotEmpty())
                .andExpect(jsonPath("$.data.canvas.nodes.length()").value(10))
                .andExpect(jsonPath("$.data.canvas.relations.length()").value(11))
                .andReturn();
        JsonNode generatedData = data(generated).path("canvas");
        long domainCount = countNodes(generatedData, "type", "DOMAIN");
        long taskCount = countNodes(generatedData, "type", "TASK");
        long evidenceCount = countNodes(generatedData, "type", "EVIDENCE");
        org.assertj.core.api.Assertions.assertThat(domainCount).isGreaterThanOrEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(taskCount).isGreaterThan(0);
        org.assertj.core.api.Assertions.assertThat(evidenceCount).isGreaterThan(0);

        String javaNodeId = nodeId(generatedData, "Java 核心");
        String springNodeId = nodeId(generatedData, "Spring Boot");
        String rootNodeId = nodeId(generatedData, "Java");
        String backendNodeId = nodeId(generatedData, "后端开发");
        String engineeringNodeId = nodeId(generatedData, "工程化");
        String sqlNodeId = nodeId(generatedData, "SQL");
        MvcResult updatedNode = mockMvc.perform(patch("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}",
                        sessionId, springNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spring Boot 企业应用\",\"status\":\"LEARNING\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.reason").value("USER_NODE_UPDATED"))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(nodeId(data(updatedNode), "Spring Boot 企业应用")).isEqualTo(springNodeId);

        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/canvas/versions/2", sessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.nodes[?(@.title == 'Spring Boot')]").exists());

        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/relations", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromNodeId\":\"" + springNodeId + "\",\"toNodeId\":\""
                                + javaNodeId + "\",\"type\":\"PREREQUISITE\",\"expectedVersion\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_DEPENDENCY_CYCLE"));

        mockMvc.perform(patch("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}",
                        sessionId, springNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"过期写入\",\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_VERSION_CONFLICT"));

        mockMvc.perform(patch("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}",
                        sessionId, springNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"MASTERED\",\"expectedVersion\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_MASTERY_REQUIRES_VALIDATION"));

        mockMvc.perform(patch("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}",
                        sessionId, rootNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentNodeId\":\"" + springNodeId + "\",\"expectedVersion\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_ROOT_MOVE_FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}",
                        sessionId, rootNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cascade\":true,\"expectedVersion\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_ROOT_DELETE_FORBIDDEN"));

        MvcResult movedNode = mockMvc.perform(patch("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}",
                        sessionId, sqlNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentNodeId\":\"" + engineeringNodeId + "\",\"expectedVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(4))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(parentId(data(movedNode), sqlNodeId))
                .isEqualTo(engineeringNodeId);
        org.assertj.core.api.Assertions.assertThat(parentRelationCount(data(movedNode), sqlNodeId)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}",
                        sessionId, backendNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cascade\":false,\"expectedVersion\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_NODE_HAS_CHILDREN"));

        mockMvc.perform(delete("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}",
                        sessionId, backendNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cascade\":true,\"expectedVersion\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(5))
                .andExpect(jsonPath("$.data.nodes[?(@.logicalNodeId == '" + backendNodeId + "')]").doesNotExist())
                .andExpect(jsonPath("$.data.nodes[?(@.logicalNodeId == '" + springNodeId + "')]").doesNotExist());

        Integer finalVersionCount = jdbc.queryForObject("SELECT COUNT(*) FROM canvas_versions WHERE goal_id=?", Integer.class, goalId);
        org.assertj.core.api.Assertions.assertThat(finalVersionCount).isEqualTo(5);

        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/canvas/versions", sessionId)
                        .cookie(owner).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].version").value(5));
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/canvas/versions", sessionId)
                        .cookie(owner).param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].version").value(1));
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/canvas/versions/compare", sessionId)
                        .cookie(owner).param("from", "2").param("to", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fromVersion").value(2))
                .andExpect(jsonPath("$.data.toVersion").value(5))
                .andExpect(jsonPath("$.data.removedNodes").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.nodes.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/canvas/versions", sessionId)
                        .cookie(owner).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_VERSION_PAGE_INVALID"));
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/canvas/versions", sessionId)
                        .cookie(owner).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_VERSION_SIZE_INVALID"));
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/canvas/versions/compare", sessionId)
                        .cookie(owner).param("from", "0").param("to", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_VERSION_INVALID"));
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/canvas/versions", sessionId).cookie(other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CP_SESSION_NOT_FOUND"));

        MvcResult restored = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/versions/2/restore", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(6))
                .andExpect(jsonPath("$.data.reason").value("USER_VERSION_RESTORED"))
                .andExpect(jsonPath("$.data.nodes.length()").value(10))
                .andReturn();

        MvcResult plan = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/plans", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"durationWeeks":8,"intensity":"STANDARD","weeklyHours":10,
                         "learningDays":[1,3,6],"startDate":"2026-08-31","expectedCanvasVersion":6}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationWeeks").value(8))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.currentRevision").value(1))
                .andExpect(jsonPath("$.data.currentRevisionId").isNotEmpty())
                .andExpect(jsonPath("$.data.tasks.length()").value(org.hamcrest.Matchers.greaterThan(4)))
                .andReturn();
        JsonNode planData = data(plan);
        String planId = planData.path("id").asText();
        JsonNode learningTask = null;
        for (JsonNode task : planData.path("tasks")) {
            if (!task.path("nodeId").isMissingNode() && !task.path("nodeId").asText().isBlank()) {
                learningTask = task;
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(learningTask).isNotNull();
        String taskId = learningTask.path("id").asText();
        String taskNodeId = learningTask.path("nodeId").asText();

        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/plans/{planId}/evidences", sessionId, planId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"taskId":"%s","nodeId":"%s","sourceType":"USER_NOTE",
                         "title":"本地测试证据","note":"完成了可复核练习","expectedTaskVersion":0}
                        """.formatted(taskId, taskNodeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentRevision").value(2))
                .andExpect(jsonPath("$.data.evidences.length()").value(1));
        String evidenceRevisionSnapshot = jdbc.queryForObject(
                "SELECT snapshot_json FROM career_learning_plan_revisions WHERE plan_id=? AND revision_no=2",
                String.class, planId);

        MvcResult completedTask = mockMvc.perform(patch("/api/v1/career-planning/sessions/{id}/plans/{planId}/tasks/{taskId}",
                        sessionId, planId, taskId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentRevision").value(3))
                .andExpect(jsonPath("$.data.tasks[?(@.id == '" + taskId + "')].status").value("DONE"))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT snapshot_json FROM career_learning_plan_revisions WHERE plan_id=? AND revision_no=2",
                String.class, planId)).isEqualTo(evidenceRevisionSnapshot);
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/plans/{planId}/revisions", sessionId, planId)
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].revision").value(3))
                .andExpect(jsonPath("$.data[2].revision").value(1));
        String evidenceId = data(completedTask).path("evidences").get(0).path("id").asText();

        var validationScore = mapper.createObjectNode().put("overall", 88);
        validationScore.set("dimensions", mapper.createArrayNode().add(mapper.createObjectNode()
                .put("name", "正确性").put("score", 88)));
        var validationFeedback = mapper.createObjectNode().put("summary", "达到当前节点掌握标准");
        validationFeedback.set("strengths", mapper.createArrayNode().add("结果可复核"));
        validationFeedback.set("gaps", mapper.createArrayNode());
        validationFeedback.set("nextActions", mapper.createArrayNode().add("继续在真实项目中巩固"));
        when(ai.evaluateAbility(anyString(), any(), any())).thenReturn(new AiResult<>(
                new ValidationPayload("PASSED", validationScore, validationFeedback),
                "test-model", 90, 120, "validation-hash"));
        int validationBaseVersion = jdbc.queryForObject("SELECT MAX(version_no) FROM canvas_versions WHERE goal_id=?",
                Integer.class, goalId);
        MvcResult validation = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/validations", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nodeId":"%s","method":"PROJECT_CHECK","evidenceIds":["%s"],
                         "submission":{"summary":"完成本地综合练习"},"expectedCanvasVersion":%d}
                        """.formatted(taskNodeId, evidenceId, validationBaseVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("PASSED"))
                .andExpect(jsonPath("$.data.status").value("EVALUATED"))
                .andReturn();
        String validationId = data(validation).path("id").asText();
        int confirmBaseVersion = jdbc.queryForObject("SELECT MAX(version_no) FROM canvas_versions WHERE goal_id=?",
                Integer.class, goalId);
        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/validations/{validationId}/confirm", sessionId, validationId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true,\"expectedCanvasVersion\":" + confirmBaseVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.userConfirmed").value(true));
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}", sessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canvas.nodes[?(@.logicalNodeId == '" + taskNodeId + "')].status")
                        .value("MASTERED"));

        var batchScore = mapper.createObjectNode().put("overall", 64);
        batchScore.set("dimensions", mapper.createArrayNode()
                .add(mapper.createObjectNode().put("name", "完整性").put("score", 64)));
        var springFeedback = mapper.createObjectNode().put("summary", "基础概念清晰，但缺少可复核证据");
        springFeedback.set("strengths", mapper.createArrayNode().add("能够说明核心概念"));
        springFeedback.set("gaps", mapper.createArrayNode().add("缺少实际项目产物"));
        springFeedback.set("nextActions", mapper.createArrayNode().add("补充一个可运行的接口示例"));
        var sqlFeedback = mapper.createObjectNode().put("summary", "查询思路基本正确，仍需覆盖性能分析");
        sqlFeedback.set("strengths", mapper.createArrayNode().add("能够拆解查询条件"));
        sqlFeedback.set("gaps", mapper.createArrayNode().add("未说明执行计划"));
        sqlFeedback.set("nextActions", mapper.createArrayNode().add("使用 EXPLAIN 验证索引效果"));
        when(ai.evaluateAbilities(anyString(), anyString(), any(), any())).thenReturn(new AiResult<>(
                new BatchValidationPayload(List.of(
                        new BatchValidationItem(springNodeId, "NEEDS_WORK", batchScore, springFeedback),
                        new BatchValidationItem(sqlNodeId, "NEEDS_WORK", batchScore, sqlFeedback))),
                "test-model", 180, 260, "batch-validation-hash"));
        int batchBaseVersion = jdbc.queryForObject(
                "SELECT MAX(version_no) FROM canvas_versions WHERE goal_id=?", Integer.class, goalId);
        String batchRequest = """
                {"requestId":"career-validation-batch-test","expectedCanvasVersion":%d,
                 "method":"PROJECT_CHECK","items":[
                   {"nodeId":"%s","evidenceIds":[],"submission":{"summary":"说明 Spring Boot 核心机制"}},
                   {"nodeId":"%s","evidenceIds":[],"submission":{"summary":"说明 SQL 查询优化过程"}}
                 ]}
                """.formatted(batchBaseVersion, springNodeId, sqlNodeId);
        MvcResult batchTask = mockMvc.perform(post(
                        "/api/v1/career-planning/sessions/{id}/validation-batches", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content(batchRequest))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskType").value(TaskTypes.CAREER_PLANNING_VALIDATION_BATCH))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String batchTaskId = data(batchTask).path("id").asText();
        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/validation-batches", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content(batchRequest))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(batchTaskId));
        var claimedBatchTask = taskService.claimNext(TaskTypes.CAREER_PLANNING_VALIDATION_BATCH).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(claimedBatchTask.getId()).isEqualTo(batchTaskId);
        careerPlanningAsync.processClaimedTask(claimedBatchTask);
        MvcResult completedBatch = mockMvc.perform(get("/api/v1/tasks/{id}", batchTaskId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.resultVersion").isNotEmpty())
                .andReturn();
        String batchId = data(completedBatch).path("resultVersion").asText();
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/validations", sessionId)
                        .cookie(owner).param("batchId", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].batchId").value(batchId))
                .andExpect(jsonPath("$.data[1].batchId").value(batchId))
                .andExpect(jsonPath("$.data[0].status").value("EVALUATED"))
                .andExpect(jsonPath("$.data[1].status").value("EVALUATED"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT MAX(version_no) FROM canvas_versions WHERE goal_id=?", Integer.class, goalId))
                .isEqualTo(batchBaseVersion + 1);

        int proposalBaseVersion = jdbc.queryForObject("SELECT MAX(version_no) FROM canvas_versions WHERE goal_id=?",
                Integer.class, goalId);
        when(ai.generateCanvasProposal(anyString(), any(), any(), any())).thenReturn(new AiResult<>(
                new ProposalPayload(List.of(new AiProposalItem("p1", "UPDATE", springNodeId, null,
                        null, "Spring Boot 云原生应用", "LEARNING",
                        mapper.createObjectNode().put("summary", "面向云原生服务的工程实践"),
                        "补齐目标岗位需要的工程化能力", canvasRefs, List.of()))),
                "test-model", 100, 150, "proposal-hash"));
        MvcResult proposal = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/proposals", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"requestId":"proposal-test","instruction":"补强云原生工程能力","expectedVersion":%d}
                        """.formatted(proposalBaseVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andReturn();
        String proposalId = data(proposal).path("id").asText();
        String proposalItemId = data(proposal).path("items").get(0).path("id").asText();
        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/proposals/{proposalId}/decide", sessionId, proposalId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"decisions":[{"itemId":"%s","decision":"ACCEPTED"}],"expectedVersion":%d}
                        """.formatted(proposalItemId, proposalBaseVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposal.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.canvas.nodes[?(@.logicalNodeId == '" + springNodeId + "')].title")
                        .value("Spring Boot 云原生应用"));

        int inferenceBaseVersion = jdbc.queryForObject(
                "SELECT MAX(version_no) FROM canvas_versions WHERE goal_id=?", Integer.class, goalId);
        when(ai.generateNodeInference(anyString(), any(), any(), any(), any())).thenReturn(new AiResult<>(
                new ProposalPayload(List.of(
                        new AiProposalItem("add-concurrency", "ADD", null, backendNodeId,
                                "KNOWLEDGE", "并发编程基础", "NOT_STARTED",
                                mapper.createObjectNode().put("summary", "理解线程安全、锁与同步原语"),
                                "补齐当前能力的前置知识", canvasRefs, List.of(springNodeId)),
                        new AiProposalItem("link-concurrency", "ADD_RELATION", null, null,
                                null, null, null, mapper.createObjectNode(),
                                "并发基础是云原生服务开发的前置能力", canvasRefs, List.of(springNodeId),
                                "PREREQUISITE", "PROPOSAL:add-concurrency", springNodeId))),
                "test-model", 120, 220, "inference-response-hash"))
                .thenReturn(new AiResult<>(new ProposalPayload(List.of(
                        new AiProposalItem("add-observability", "ADD", null, backendNodeId,
                                "SKILL", "可观测性基础", "NOT_STARTED",
                                mapper.createObjectNode().put("summary", "理解日志、指标与追踪"),
                                "横向补充相邻工程能力", canvasRefs, List.of(springNodeId)))),
                        "test-model", 90, 160, "rejected-inference-response-hash"));

        MvcResult inferenceTaskResult = mockMvc.perform(post(
                        "/api/v1/career-planning/sessions/{id}/canvas/proposals/tasks", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"requestId":"node-inference-prerequisite","targetNodeId":"%s",
                         "direction":"PREREQUISITES","depth":"ONE_LEVEL","expectedVersion":%d}
                        """.formatted(springNodeId, inferenceBaseVersion)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskType").value(TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String inferenceTaskId = data(inferenceTaskResult).path("id").asText();
        var claimedInferenceTask = taskService.claimNext(TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(claimedInferenceTask.getId()).isEqualTo(inferenceTaskId);
        careerPlanningAsync.processClaimedTask(claimedInferenceTask);

        MvcResult completedInferenceTask = mockMvc.perform(get("/api/v1/tasks/{id}", inferenceTaskId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.progressPercent").value(100))
                .andExpect(jsonPath("$.data.resultVersion").isNotEmpty())
                .andReturn();
        String inferenceProposalId = data(completedInferenceTask).path("resultVersion").asText();
        MvcResult inferenceProposal = mockMvc.perform(get(
                        "/api/v1/career-planning/sessions/{id}/canvas/proposals/{proposalId}",
                        sessionId, inferenceProposalId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposalType").value("NODE_INFERENCE"))
                .andExpect(jsonPath("$.data.targetNodeId").value(springNodeId))
                .andExpect(jsonPath("$.data.direction").value("PREREQUISITES"))
                .andExpect(jsonPath("$.data.depth").value("ONE_LEVEL"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andReturn();
        JsonNode inferenceItems = data(inferenceProposal).path("items");
        String addInferenceItemId = inferenceItems.get(0).path("id").asText();
        String relationInferenceItemId = inferenceItems.get(1).path("id").asText();
        mockMvc.perform(post(
                        "/api/v1/career-planning/sessions/{id}/canvas/proposals/{proposalId}/decide",
                        sessionId, inferenceProposalId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"decisions":[
                          {"itemId":"%s","decision":"ACCEPTED"},
                          {"itemId":"%s","decision":"ACCEPTED"}
                        ],"expectedVersion":%d}
                        """.formatted(addInferenceItemId, relationInferenceItemId, inferenceBaseVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposal.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.canvas.version").value(inferenceBaseVersion + 1))
                .andExpect(jsonPath("$.data.canvas.nodes[?(@.title == '并发编程基础')]").exists())
                .andExpect(jsonPath("$.data.canvas.relations[?(@.type == 'PREREQUISITE' && @.toNodeId == '"
                        + springNodeId + "')]").exists());

        int rejectedBaseVersion = inferenceBaseVersion + 1;
        MvcResult rejectedTaskResult = mockMvc.perform(post(
                        "/api/v1/career-planning/sessions/{id}/canvas/proposals/tasks", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"requestId":"node-inference-rejected","targetNodeId":"%s",
                         "direction":"SIBLINGS","depth":"ONE_LEVEL","expectedVersion":%d}
                        """.formatted(springNodeId, rejectedBaseVersion)))
                .andExpect(status().isAccepted()).andReturn();
        String rejectedTaskId = data(rejectedTaskResult).path("id").asText();
        var claimedRejectedTask = taskService.claimNext(TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL).orElseThrow();
        careerPlanningAsync.processClaimedTask(claimedRejectedTask);
        MvcResult completedRejectedTask = mockMvc.perform(get("/api/v1/tasks/{id}", rejectedTaskId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andReturn();
        String rejectedProposalId = data(completedRejectedTask).path("resultVersion").asText();
        MvcResult rejectedProposal = mockMvc.perform(get(
                        "/api/v1/career-planning/sessions/{id}/canvas/proposals/{proposalId}",
                        sessionId, rejectedProposalId).cookie(owner))
                .andExpect(status().isOk()).andReturn();
        String rejectedItemId = data(rejectedProposal).path("items").get(0).path("id").asText();
        mockMvc.perform(post(
                        "/api/v1/career-planning/sessions/{id}/canvas/proposals/{proposalId}/decide",
                        sessionId, rejectedProposalId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"decisions":[{"itemId":"%s","decision":"REJECTED","rejectionReason":"不纳入当前计划"}],
                         "expectedVersion":%d}
                        """.formatted(rejectedItemId, rejectedBaseVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proposal.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.canvas.version").value(rejectedBaseVersion));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT MAX(version_no) FROM canvas_versions WHERE goal_id=?", Integer.class, goalId))
                .isEqualTo(rejectedBaseVersion);

        int switchedVersion = jdbc.queryForObject("SELECT MAX(version_no) FROM canvas_versions WHERE goal_id=?",
                Integer.class, goalId);
        MvcResult split = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}/split",
                        sessionId, javaNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"items":[
                          {"type":"KNOWLEDGE","title":"Java 语言基础","status":"NOT_STARTED","detail":{"summary":"语法、集合与异常"},"locked":false},
                          {"type":"KNOWLEDGE","title":"Java 并发基础","status":"NOT_STARTED","detail":{"summary":"线程与并发安全"},"locked":false}
                        ],"expectedVersion":%d}
                        """.formatted(switchedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(switchedVersion + 1))
                .andExpect(jsonPath("$.data.reason").value("USER_NODE_SPLIT"))
                .andReturn();
        JsonNode splitCanvas = data(split);
        String languageNodeId = nodeId(splitCanvas, "Java 语言基础");
        String concurrencyNodeId = nodeId(splitCanvas, "Java 并发基础");
        org.assertj.core.api.Assertions.assertThat(parentId(splitCanvas, languageNodeId)).isEqualTo(javaNodeId);
        org.assertj.core.api.Assertions.assertThat(parentId(splitCanvas, concurrencyNodeId)).isEqualTo(javaNodeId);

        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/nodes/{nodeId}/split",
                        sessionId, javaNodeId).cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"items":[
                          {"type":"KNOWLEDGE","title":"重复名称","status":"NOT_STARTED","detail":{},"locked":false},
                          {"type":"TASK","title":"重复名称","status":"NOT_STARTED","detail":{},"locked":false}
                        ],"expectedVersion":%d}
                        """.formatted(switchedVersion + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("CP_CANVAS_SPLIT_TITLE_DUPLICATE"));

        MvcResult merged = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/nodes/merge", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nodeIds":["%s","%s"],"title":"Java 基础体系",
                         "detail":{"summary":"覆盖语言、集合、异常和并发基础"},"expectedVersion":%d}
                        """.formatted(languageNodeId, concurrencyNodeId, switchedVersion + 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(switchedVersion + 2))
                .andExpect(jsonPath("$.data.reason").value("USER_NODES_MERGED"))
                .andExpect(jsonPath("$.data.nodes[?(@.logicalNodeId == '" + languageNodeId + "')].title")
                        .value("Java 基础体系"))
                .andExpect(jsonPath("$.data.nodes[?(@.logicalNodeId == '" + concurrencyNodeId + "')]").doesNotExist())
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(parentId(data(merged), languageNodeId)).isEqualTo(javaNodeId);
        mockMvc.perform(patch("/api/v1/career-planning/sessions/{id}/canvas/nodes", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nodeIds":["%s","%s"],"locked":true,"expectedVersion":%d}
                        """.formatted(javaNodeId, languageNodeId, switchedVersion + 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(switchedVersion + 3))
                .andExpect(jsonPath("$.data.reason").value("USER_NODES_BATCH_UPDATED"))
                .andExpect(jsonPath("$.data.nodes[?(@.logicalNodeId == '" + javaNodeId + "')].locked").value(true))
                .andExpect(jsonPath("$.data.nodes[?(@.logicalNodeId == '" + languageNodeId + "')].locked").value(true));
        mockMvc.perform(patch("/api/v1/career-planning/sessions/{id}/plans/{planId}/tasks/{taskId}",
                        sessionId, planId, taskId).cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetWeek\":2,\"sortOrder\":0,\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[?(@.id == '" + taskId + "')].week").value(2))
                .andExpect(jsonPath("$.data.tasks[?(@.id == '" + taskId + "')].sortOrder").value(0));
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}/canvas/versions/{version}",
                        sessionId, switchedVersion + 1).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[?(@.logicalNodeId == '" + concurrencyNodeId + "')]").exists());
    }

    @Test
    void seekerCanOwnIndependentCanvasesAndGenerateEachTreeFromScratch() throws Exception {
        MockCookie owner = registerAndLogin("career-multi-canvas+" + System.nanoTime() + "@example.com");
        String taxonomySuffix = Long.toString(System.nanoTime());
        String categoryId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        List<String> taxonomyIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        jdbc.update("INSERT INTO job_taxonomy_nodes(id,parent_id,node_level,code,display_name,normalized_name,catalog_occupation_code,status,sort_order,created_at,updated_at) VALUES(?,NULL,'CATEGORY',?,?,?,'TEST-CATEGORY','PUBLISHED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                categoryId, "test-multi-category-" + taxonomySuffix, "测试职业大类", "测试职业大类");
        jdbc.update("INSERT INTO job_taxonomy_nodes(id,parent_id,node_level,code,display_name,normalized_name,catalog_occupation_code,status,sort_order,created_at,updated_at) VALUES(?,?,'GROUP',?,?,?,'TEST-GROUP','PUBLISHED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                groupId, categoryId, "test-multi-group-" + taxonomySuffix, "测试职业子类", "测试职业子类");
        jdbc.update("INSERT INTO job_taxonomy_nodes(id,parent_id,node_level,code,display_name,normalized_name,catalog_occupation_code,status,sort_order,created_at,updated_at) VALUES(?,?,'JOB',?,?,?,'TEST-A','PUBLISHED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                taxonomyIds.get(0), groupId, "test-multi-a-" + taxonomySuffix, "测试职业甲", "测试职业甲");
        jdbc.update("INSERT INTO job_taxonomy_nodes(id,parent_id,node_level,code,display_name,normalized_name,catalog_occupation_code,status,sort_order,created_at,updated_at) VALUES(?,?,'JOB',?,?,?,'TEST-B','PUBLISHED',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                taxonomyIds.get(1), groupId, "test-multi-b-" + taxonomySuffix, "测试职业乙", "测试职业乙");

        MvcResult first = mockMvc.perform(post("/api/v1/career-planning/canvases").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxonomyNodeId\":\"" + taxonomyIds.get(0) + "\",\"aiConsent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canvas.version").value(1))
                .andExpect(jsonPath("$.data.canvas.nodes.length()").value(1))
                .andExpect(jsonPath("$.data.canvas.nodes[0].type").value("CAREER"))
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/career-planning/canvases").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taxonomyNodeId\":\"" + taxonomyIds.get(1) + "\",\"aiConsent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canvas.version").value(1))
                .andExpect(jsonPath("$.data.canvas.nodes.length()").value(1))
                .andExpect(jsonPath("$.data.canvas.relations.length()").value(0))
                .andReturn();

        String firstSessionId = data(first).path("id").asText();
        String secondSessionId = data(second).path("id").asText();
        String firstGoalId = data(first).path("activeGoal").path("id").asText();
        String secondGoalId = data(second).path("activeGoal").path("id").asText();
        org.assertj.core.api.Assertions.assertThat(firstSessionId).isNotEqualTo(secondSessionId);
        org.assertj.core.api.Assertions.assertThat(firstGoalId).isNotEqualTo(secondGoalId);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM career_goals WHERE account_id=(SELECT account_id FROM career_goals WHERE id=?) AND status='ACTIVE'",
                Integer.class, firstGoalId)).isEqualTo(2);

        mockMvc.perform(get("/api/v1/career-planning/canvases").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.canvasCount").value(2))
                .andExpect(jsonPath("$.data.stats.primaryCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        List<String> refs = List.of("GOAL:" + secondGoalId);
        when(ai.generateCanvas(anyString(), any(), any(), any())).thenReturn(new AiResult<>(new CanvasPayload(List.of(
                canvasNode("platform", "DOMAIN", "平台基础", "ROOT", refs),
                canvasNode("language", "DOMAIN", "语言基础", "ROOT", refs),
                canvasNode("data", "DOMAIN", "数据能力", "ROOT", refs),
                canvasNode("delivery", "DOMAIN", "项目交付", "ROOT", refs),
                canvasNode("core-skill", "SKILL", "核心专业技能", "platform", refs),
                canvasNode("core-knowledge", "KNOWLEDGE", "核心知识体系", "language", refs),
                canvasNode("practice", "TASK", "完成综合实践", "delivery", refs),
                canvasNode("evidence", "EVIDENCE", "提交验证材料", "practice", refs),
                canvasNode("data-skill", "SKILL", "数据分析基础", "data", refs))),
                "test-model", 180, 360, "multi-canvas-response-hash"));

        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/canvas/generate", secondSessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"multi-canvas-generate\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.nodes.length()").value(10));
        mockMvc.perform(get("/api/v1/career-planning/canvases").param("q", "核心专业技能").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.canvasCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sessionId").value(secondSessionId));
        mockMvc.perform(get("/api/v1/career-planning/sessions/{id}", firstSessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canvas.version").value(1))
                .andExpect(jsonPath("$.data.canvas.nodes.length()").value(1));
        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/goal-switch", firstSessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void profileReviewNeverConfirmsFactsAndClosesAnOpenInterviewWhenSkipped() throws Exception {
        MockCookie owner = registerAndLogin("career-profile-review+" + System.nanoTime() + "@example.com");
        MvcResult started = mockMvc.perform(post("/api/v1/career-planning/sessions").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryMode\":\"AI_DISCOVERY\",\"aiConsent\":true}"))
                .andExpect(status().isOk()).andReturn();
        String sessionId = data(started).path("id").asText();

        MvcResult updated = mockMvc.perform(put("/api/v1/career-planning/sessions/{id}/profile", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("""
                        {"basics":{"identity":"应届生","education":"本科","experienceYears":"暂无正式经验","weeklyLearningHours":"5~10小时"},
                         "preferences":{},"constraints":{},
                         "items":[{"section":"SKILLS","claimType":"SELF_REPORTED","title":"Java",
                           "payload":{"origin":"PROFILE_FORM"},"sourceRefs":[],"locked":false,"sortOrder":1}],
                         "expectedVersion":0}
                        """))
                .andExpect(status().isOk()).andReturn();
        String itemId = data(updated).path("profile").path("items").get(0).path("id").asText();

        MvcResult reviewed = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/profile/review", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phase").value("PROFILE_CONFIRMATION"))
                .andExpect(jsonPath("$.data.profile.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.profile.items[0].confirmed").value(false))
                .andReturn();

        when(ai.generateQuestions(anyString(), any(), any())).thenReturn(new AiResult<>(
                List.of(new InterviewQuestion("q-review", "是否需要补充项目经历？", "确认经历范围", "SELF_REPORTED")),
                "test-model", 8, 12, "review-question-hash"));
        MvcResult interview = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/interviews", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"career-profile-review-interview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interviewRounds[0].status").value("OPEN"))
                .andReturn();
        int interviewVersion = data(interview).path("profile").path("version").asInt();

        MvcResult skipped = mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/profile/review", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + interviewVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phase").value("PROFILE_CONFIRMATION"))
                .andExpect(jsonPath("$.data.interviewRounds[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.profile.items[0].id").value(itemId))
                .andExpect(jsonPath("$.data.profile.items[0].confirmed").value(false))
                .andReturn();

        int reviewedVersion = data(skipped).path("profile").path("version").asInt();
        mockMvc.perform(post("/api/v1/career-planning/sessions/{id}/profile/confirm", sessionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedItemIds\":[\"" + itemId + "\"],\"expectedVersion\":" + reviewedVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.profile.items[0].confirmed").value(true));
    }

    private AiCanvasNode canvasNode(String key, String type, String title, String parent,
            List<String> refs, String... prerequisites) {
        return new AiCanvasNode(key, type, title, parent,
                mapper.createObjectNode().put("summary", title + " 的结构化学习内容"), refs,
                List.of(prerequisites));
    }

    private long countNodes(JsonNode canvas, String field, String value) {
        long count = 0;
        for (JsonNode node : canvas.path("nodes")) if (value.equals(node.path(field).asText())) count++;
        return count;
    }

    private String nodeId(JsonNode canvas, String title) {
        for (JsonNode node : canvas.path("nodes")) {
            if (title.equals(node.path("title").asText())) return node.path("logicalNodeId").asText();
        }
        throw new AssertionError("Missing canvas node " + title);
    }

    private String parentId(JsonNode canvas, String logicalNodeId) {
        for (JsonNode relation : canvas.path("relations")) {
            if ("TREE_PARENT".equals(relation.path("type").asText())
                    && logicalNodeId.equals(relation.path("fromNodeId").asText())) {
                return relation.path("toNodeId").asText();
            }
        }
        throw new AssertionError("Missing parent relation for " + logicalNodeId);
    }

    private long parentRelationCount(JsonNode canvas, String logicalNodeId) {
        long count = 0;
        for (JsonNode relation : canvas.path("relations")) {
            if ("TREE_PARENT".equals(relation.path("type").asText())
                    && logicalNodeId.equals(relation.path("fromNodeId").asText())) count++;
        }
        return count;
    }

    private AiRecommendation recommendation(String taxonomyId, String title, String tier, List<String> refs) {
        return new AiRecommendation(taxonomyId, title, tier, "基于已确认技能与方向偏好形成的测试推荐",
                List.of("已确认 Java 技能"), List.of("需要补充可验证项目"), refs);
    }

    private String taxonomyId(String title) {
        return jdbc.queryForObject("SELECT id FROM job_taxonomy_nodes WHERE display_name=? AND node_level='JOB'", String.class, title);
    }

    private MockCookie registerAndLogin(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}";
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
}
