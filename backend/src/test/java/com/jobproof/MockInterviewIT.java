package com.jobproof;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockInterviewIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void textInterviewFreezesMaterialsAdvancesQuestionsAndGeneratesReport() throws Exception {
        MockCookie owner = registerAndLogin("mock-owner+" + System.nanoTime() + "@example.com");
        MockCookie other = registerAndLogin("mock-other+" + System.nanoTime() + "@example.com");
        String resumeId = data(mockMvc.perform(post("/api/v1/resumes").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BLANK\",\"title\":\"Java 后端求职简历\"}"))
                .andExpect(status().isOk()).andReturn()).path("id").asText();

        MvcResult draftResult = mockMvc.perform(post("/api/v1/mock-interviews/drafts").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.step").value(1))
                .andReturn();
        String draftId = data(draftResult).path("id").asText();
        mockMvc.perform(patch("/api/v1/mock-interviews/drafts/{id}", draftId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"step\":3,\"payload\":{\"mode\":\"TEXT\"},\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.step").value(3))
                .andExpect(jsonPath("$.data.version").value(1));

        Map<String, Object> command = new LinkedHashMap<>();
        command.put("draftId", draftId);
        command.put("resumeId", resumeId);
        command.put("taxonomyNodeId", "job-java");
        command.put("taxonomyCategoryId", "category-technology");
        command.put("taxonomyGroupId", "group-software");
        command.put("careerRecordIds", List.of());
        command.put("careerFileIds", List.of());
        command.put("positionName", "Java 后端工程师");
        command.put("companyName", "合成测试公司");
        command.put("mode", "TEXT");
        command.put("interviewType", "COMPREHENSIVE");
        command.put("difficulty", "STANDARD");
        command.put("durationMinutes", 30);
        command.put("questionCount", 5);
        command.put("languageCode", "zh-CN");
        command.put("feedbackMode", "AFTER_EACH");
        command.put("followUpEnabled", true);
        command.put("consentConfirmed", true);
        MvcResult created = mockMvc.perform(post("/api/v1/mock-interviews/sessions").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.mode").value("TEXT"))
                .andExpect(jsonPath("$.data.questions.length()").value(5))
                .andExpect(jsonPath("$.data.resumeSnapshot.title").value("Java 后端求职简历"))
                .andExpect(jsonPath("$.data.settingsSnapshot.taxonomyNodeId").value("job-java"))
                .andExpect(jsonPath("$.data.settingsSnapshot.questionGenerationMode").value("BASIC_RULES"))
                .andReturn();
        JsonNode session = data(created);
        String sessionId = session.path("session").path("id").asText();

        mockMvc.perform(get("/api/v1/mock-interviews/sessions/{id}", sessionId).cookie(other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("MOCK_INTERVIEW_NOT_FOUND"));

        for (int index = 0; index < 5; index++) {
            JsonNode current = index == 0 ? session.path("currentQuestion") : data(mockMvc.perform(
                            get("/api/v1/mock-interviews/sessions/{id}", sessionId).cookie(owner))
                    .andExpect(status().isOk()).andReturn()).path("currentQuestion");
            String questionId = current.path("id").asText();
            String answer = "在合成项目中，我先明确目标与约束，然后拆解任务、验证方案并与团队同步。"
                    + "最终完成核心服务交付，接口错误率降低 18%，相关数据仅用于自动化测试。";
            MvcResult submitted = mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/answers/{questionId}", sessionId, questionId)
                            .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("answer", answer))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.answers[" + index + "].status").value("SUBMITTED"))
                    // Without the model there is no score, only a pending evaluation (BE-1).
                    .andExpect(jsonPath("$.data.answers[" + index + "].feedback.generationMode").value("PENDING"))
                    .andExpect(jsonPath("$.data.answers[" + index + "].scores").isEmpty())
                    .andReturn();
            session = data(submitted);
            if (index == 0) {
                session = data(mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/answers/{questionId}", sessionId, questionId)
                                .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("answer", answer + " 我补充了可追溯的验证步骤。"))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.session.answeredCount").value(1))
                        .andExpect(jsonPath("$.data.currentQuestion.orderNo").value(2))
                        .andReturn());
                mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/pause", sessionId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.session.status").value("PAUSED"))
                        .andExpect(jsonPath("$.data.session.elapsedSeconds").isNumber())
                        .andExpect(jsonPath("$.data.session.durationMinutes").value(30));
                mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/resume", sessionId).cookie(owner))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.session.status").value("IN_PROGRESS"));
            }
        }

        mockMvc.perform(get("/api/v1/mock-interviews/reports/{id}", sessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.status").value("COMPLETED"))
                // Nothing evaluated: no score, no dimensions and no written-in strengths (H-5).
                .andExpect(jsonPath("$.data.overallScore").doesNotExist())
                .andExpect(jsonPath("$.data.dimensions").isEmpty())
                .andExpect(jsonPath("$.data.evaluatedCount").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(5))
                .andExpect(jsonPath("$.data.questions.length()").value(5))
                .andExpect(jsonPath("$.data.summary.strengths").isEmpty())
                .andExpect(jsonPath("$.data.recommendations").isEmpty());
        mockMvc.perform(get("/api/v1/mock-interviews/dashboard").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.completed").value(1))
                .andExpect(jsonPath("$.data.averageScore").doesNotExist());
        // The model is still unavailable, so nothing changes.
        mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/evaluate", sessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluated").value(0))
                .andExpect(jsonPath("$.data.pending").value(5))
                .andExpect(jsonPath("$.data.aiAvailable").value(false));
    }

    @Test
    void voiceInterviewPersistsTranscriptAndFallsBackToTextWithoutLosingProgress() throws Exception {
        MockCookie owner = registerAndLogin("mock-voice+" + System.nanoTime() + "@example.com");
        String resumeId = data(mockMvc.perform(post("/api/v1/resumes").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BLANK\",\"title\":\"语音面试合成简历\"}"))
                .andExpect(status().isOk()).andReturn()).path("id").asText();

        Map<String, Object> command = new LinkedHashMap<>();
        command.put("resumeId", resumeId);
        command.put("careerRecordIds", List.of());
        command.put("careerFileIds", List.of());
        command.put("positionName", "Java 后端工程师");
        command.put("mode", "VOICE");
        command.put("interviewType", "PROFESSIONAL");
        command.put("difficulty", "STANDARD");
        command.put("durationMinutes", 30);
        command.put("questionCount", 5);
        command.put("languageCode", "zh-CN");
        command.put("feedbackMode", "AFTER_SESSION");
        command.put("followUpEnabled", true);
        command.put("consentConfirmed", true);
        JsonNode created = data(mockMvc.perform(post("/api/v1/mock-interviews/sessions").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.mode").value("VOICE"))
                .andReturn());
        String sessionId = created.path("session").path("id").asText();
        String questionId = created.path("currentQuestion").path("id").asText();

        mockMvc.perform(patch("/api/v1/mock-interviews/sessions/{id}/transcript/{questionId}", sessionId, questionId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"这是可以校正的语音转写草稿\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("VOICE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/pause", sessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.status").value("PAUSED"));
        mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/resume", sessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.status").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/switch-mode", sessionId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"TEXT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.mode").value("TEXT"))
                .andExpect(jsonPath("$.data.answers[0].answer").value("这是可以校正的语音转写草稿"));
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
