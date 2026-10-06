package com.jobproof;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.application.AiGatewayException;
import com.jobproof.modules.mockinterview.application.MockInterviewAiService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Answers submitted while the model was down are evaluated later; the report is rebuilt from them. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockInterviewEvaluationIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockBean MockInterviewAiService ai;

    @Test
    void pendingAnswersAreEvaluatedWhenTheModelIsBack() throws Exception {
        when(ai.generateQuestions(anyString(), anyString(), nullable(String.class), anyString(), anyString(), anyInt(), anyMap(), anyMap()))
                .thenThrow(new AiGatewayException("down", true, false));
        when(ai.evaluateAnswer(anyString(), anyString(), any(), anyString(), anyBoolean(), anyMap(), anyMap()))
                .thenThrow(new AiGatewayException("down", true, false));
        MockCookie owner = registerAndLogin("mock-eval+" + System.nanoTime() + "@example.com");
        String resumeId = data(mockMvc.perform(post("/api/v1/resumes").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"BLANK\",\"title\":\"评估验收\"}"))
                .andExpect(status().isOk()).andReturn()).path("id").asText();
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("resumeId", resumeId);
        command.put("careerRecordIds", List.of());
        command.put("careerFileIds", List.of());
        command.put("positionName", "数据分析师");
        command.put("mode", "TEXT");
        command.put("interviewType", "COMPREHENSIVE");
        command.put("difficulty", "STANDARD");
        command.put("durationMinutes", 20);
        command.put("questionCount", 5);
        command.put("languageCode", "zh-CN");
        command.put("feedbackMode", "AFTER_SESSION");
        command.put("followUpEnabled", true);
        command.put("consentConfirmed", true);
        JsonNode session = data(mockMvc.perform(post("/api/v1/mock-interviews/sessions").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(command)))
                .andExpect(status().isOk()).andReturn());
        String sessionId = session.path("session").path("id").asText();
        for (int index = 0; index < 5; index++) {
            String questionId = session.path("currentQuestion").path("id").asText();
            session = data(mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/answers/{q}", sessionId, questionId)
                            .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("answer", "我用 A/B 实验验证了留存提升，样本 3,600 人。"))))
                    .andExpect(status().isOk()).andReturn());
        }
        mockMvc.perform(get("/api/v1/mock-interviews/reports/{id}", sessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").doesNotExist())
                .andExpect(jsonPath("$.data.pendingCount").value(5));

        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("generationMode", "AI_MODEL");
        feedback.put("strengths", List.of("用实验数据支撑结论"));
        feedback.put("improvements", List.of("说明样本选择与对照组设置"));
        feedback.put("suggestedFollowUp", "如果实验结果不显著，你会怎么做？");
        doReturn(new MockInterviewAiService.Evaluation(
                        Map.of("structure", 80, "relevance", 70, "evidence", 90, "expression", 60, "professional", 70, "overall", 74),
                        feedback, "test-model", "channel", 10, 10))
                .when(ai).evaluateAnswer(anyString(), anyString(), any(), anyString(), anyBoolean(), anyMap(), anyMap());
        mockMvc.perform(post("/api/v1/mock-interviews/sessions/{id}/evaluate", sessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluated").value(5))
                .andExpect(jsonPath("$.data.pending").value(0))
                .andExpect(jsonPath("$.data.aiAvailable").value(true));
        mockMvc.perform(get("/api/v1/mock-interviews/reports/{id}", sessionId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallScore").value(74))
                .andExpect(jsonPath("$.data.dimensions.evidence").value(90))
                .andExpect(jsonPath("$.data.evaluatedCount").value(5))
                .andExpect(jsonPath("$.data.pendingCount").value(0))
                .andExpect(jsonPath("$.data.summary.strengths[0]").value("用实验数据支撑结论"))
                .andExpect(jsonPath("$.data.summary.risks[0]").value("说明样本选择与对照组设置"))
                .andExpect(jsonPath("$.data.recommendations[0]").value("如果实验结果不显著，你会怎么做？"))
                .andExpect(jsonPath("$.data.questions[0].scores.overall").value(74));
        mockMvc.perform(get("/api/v1/mock-interviews/dashboard").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageScore").value(74));
    }

    private MockCookie registerAndLogin(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return new MockCookie("jobproof_session", result.getResponse().getCookie("jobproof_session").getValue());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
