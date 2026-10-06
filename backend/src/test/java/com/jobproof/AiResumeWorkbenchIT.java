package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.airesume.application.AiResumeSseService;
import com.jobproof.modules.airesume.application.AiQuotaService;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Request;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Response;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Usage;
import com.jobproof.modules.resume.application.ResumeAiCandidateService;
import com.jobproof.modules.audit.infra.AuditEventJpaRepository;
import com.jobproof.modules.task.infra.AsyncTaskJpaRepository;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

@SpringBootTest(properties = {
        "jobproof.ai.workbench.enabled=true",
        "jobproof.ai.workbench.monthly-quota=500"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiResumeWorkbenchIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired AiResumeSseService events;
    @Autowired AiQuotaService quota;
    @Autowired AsyncTaskJpaRepository asyncTasks;
    @Autowired AuditEventJpaRepository auditEvents;
    @MockBean ResumeAiCandidateService aiCandidates;
    @MockBean AiGatewayService gateway;

    @BeforeEach
    void defaultAiAvailability() {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(false, "qwen-plus", "AI_GATEWAY_DISABLED"));
    }

    @Test
    void guidedCardsCreateImmutableRevisionsWithoutLeakingDrafts() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identityType":"GRADUATE","title":"Java 后端求职简历"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityType").value("GRADUATE"))
                .andExpect(jsonPath("$.data.cards.length()").value(12))
                .andExpect(jsonPath("$.data.aiAvailable").value(false))
                .andExpect(jsonPath("$.data.content.schemaVersion").value("resume-content-v3"))
                .andExpect(jsonPath("$.data.content.education").isArray())
                .andExpect(jsonPath("$.data.content.experiences").isArray())
                .andExpect(jsonPath("$.data.content.projects").isArray())
                .andExpect(jsonPath("$.data.content.organizations").isArray())
                .andExpect(jsonPath("$.data.content.skills").isArray())
                .andExpect(jsonPath("$.data.content.certificates").isArray())
                .andExpect(jsonPath("$.data.content.honors").isArray())
                .andExpect(jsonPath("$.data.content.languages").isArray())
                .andReturn());

        String conversationId = created.path("id").asText();
        String masterId = created.path("masterId").asText();
        JsonNode targetCard = card(created, "TARGET_JOB");
        JsonNode javaJob = taxonomyJob("JAVA后端");
        String targetPayload = mapper.writeValueAsString(Map.of(
                "targetJob", javaJob.path("displayName").asText(),
                "taxonomyNodeId", javaJob.path("id").asText(),
                "taxonomyGroupId", javaJob.path("groupId").asText(),
                "taxonomyCategoryId", javaJob.path("categoryId").asText()));

        JsonNode drafted = data(mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/cards/{card}/draft",
                        conversationId, targetCard.path("id").asText())
                        .cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":%s,\"expectedVersion\":0}".formatted(targetPayload)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(drafted.path("resume").path("version").asInt()).isEqualTo(created.path("resume").path("version").asInt());

        JsonNode targetSubmitted = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, targetCard.path("id").asText())
                        .cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":%s,\"expectedVersion\":1}".formatted(targetPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStage").value("COLLECTING_FACTS"))
                .andReturn());

        JsonNode educationCard = card(targetSubmitted, "EDUCATION");
        JsonNode ready = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, educationCard.path("id").asText())
                        .cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload":{"text":"2022-2026 示例大学 软件工程 本科"},"expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStage").value("READY_FOR_PREVIEW"))
                .andExpect(jsonPath("$.data.resume.education").value("2022-2026 示例大学 软件工程 本科"))
                .andExpect(jsonPath("$.data.content.education").isArray())
                .andExpect(jsonPath("$.data.content.education[0].description")
                        .value("2022-2026 示例大学 软件工程 本科"))
                .andReturn());

        int revisions = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn()).path("resume").path("version").asInt();
        assertThat(revisions).isEqualTo(ready.path("resume").path("version").asInt());
        assertThat(masterId).isEqualTo(ready.path("resume").path("id").asText());
        assertThat(events.replay(owner.accountId(), conversationId, 0)).extracting(AiResumeSseService.Event::type)
                .contains("conversation.created", "card.draft", "card.confirmed");

        MvcResult history = mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}/revisions", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andReturn();
        JsonNode prior = data(history).get(1);
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/revisions/{revision}/restore",
                        conversationId, prior.path("id").asText()).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("VERSION_RESTORED"));
    }

    @Test
    void structuredEducationKeepsMultipleRecordsAndProjectsOneCanonicalContentIntoPreviewText() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode target = card(created, "TARGET_JOB");
        JsonNode withTarget = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, target.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"targetJob\":\"Java 后端工程师\"},\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode education = card(withTarget, "EDUCATION");

        MvcResult result = mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, education.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload":{"items":[
                                  {"school":"示例大学","major":"软件工程","degree":"本科","startDate":"2022-09","endDate":"2026-06","description":"主修后端开发。"},
                                  {"school":"交换大学","major":"计算机科学","degree":"交换生","startDate":"2024-02","endDate":"2024-07","description":"完成数据库课程。"}
                                ]},"expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStage").value("READY_FOR_PREVIEW"))
                .andExpect(jsonPath("$.data.resume.education").value(org.hamcrest.Matchers.containsString("示例大学")))
                .andExpect(jsonPath("$.data.resume.education").value(org.hamcrest.Matchers.containsString("交换大学")))
                .andReturn();
        JsonNode completed = data(result);
        JsonNode items = card(completed, "EDUCATION").path("payload").path("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("school").asText()).isEqualTo("示例大学");
        assertThat(items.get(1).path("school").asText()).isEqualTo("交换大学");
        assertThat(completed.path("content").path("education").get(0).path("school").asText())
                .isEqualTo("示例大学");
        assertThat(completed.path("content").path("education").get(1).path("school").asText())
                .isEqualTo("交换大学");
    }

    @Test
    void smartTemplateSelectionPersistsWithoutCreatingContentRevisions() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"模板绑定验收\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.templateId").value("rlt-b-campus-v1"))
                .andExpect(jsonPath("$.data.layout.variantCode").value("NO_PHOTO"))
                .andReturn());
        String conversationId = created.path("id").asText();
        String masterId = created.path("masterId").asText();
        JsonNode initialRevisions = data(mockMvc.perform(
                        get("/api/v1/ai-resume/conversations/{id}/revisions", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        String initialHash = initialRevisions.get(0).path("contentHash").asText();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"TECH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.templateId").value("rlt-b-tech-double-v1"))
                .andExpect(jsonPath("$.data.layout.variantCode").value("BLUE"));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"TABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.templateId").value("rlt-b-cn-table-v1"))
                .andExpect(jsonPath("$.data.layout.variantCode").value("STANDARD"));

        mockMvc.perform(get("/api/v1/resume-templates/layouts/current").param("masterId", masterId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selected").value(true))
                .andExpect(jsonPath("$.data.layout.templateId").value("rlt-b-cn-table-v1"));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}/revisions", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].source").value("WORKBENCH_CREATED"))
                .andExpect(jsonPath("$.data[0].contentHash").value(initialHash));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"FREEFORM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_RESUME_TEMPLATE_UNSUPPORTED"));
    }

    @Test
    void allIdentityTypesStartWithTheCampusTemplate() throws Exception {
        Session owner = seeker();
        for (String identityType : List.of("STUDENT", "GRADUATE", "PROFESSIONAL")) {
            mockMvc.perform(post("/api/v1/ai-resume/conversations")
                            .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identityType\":\"%s\",\"title\":\"默认模板-%s\"}"
                                    .formatted(identityType, identityType)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.layout.templateId").value("rlt-b-campus-v1"))
                    .andExpect(jsonPath("$.data.layout.variantCode").value("NO_PHOTO"));
        }
    }

    @Test
    void listsTwelveServerTemplatesAndRemembersValidatedDesignWithOptimisticLocking() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"设计偏好验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();

        JsonNode templates = data(mockMvc.perform(get(
                        "/api/v1/ai-resume/conversations/{id}/smart-templates", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[0].rendererProtocol").value("resume-layout-v3"))
                .andReturn());
        assertThat(templates).allSatisfy(template -> {
            assertThat(template.path("layoutDefinitionJson").asText()).contains("organizations", "languages");
            assertThat(template.path("design").path("settings").path("schemaVersion").asText())
                    .isEqualTo("resume-design-v1");
            assertThat(template.path("presets")).hasSize(2);
            assertThat(template.path("presets").get(0).path("settings"))
                    .isNotEqualTo(template.path("presets").get(1).path("settings"));
        });

        // Campus is the product default; activate ATS before exercising its persisted design version.
        JsonNode active = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("templateId", "rlt-b-ats-minimal-v1",
                                "expectedLayoutVersion", created.path("layout").path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
        ObjectNode settings = (ObjectNode) active.path("activeDesign").path("settings").deepCopy();
        settings.put("fontScale", "LARGE");
        settings.put("dateFormat", "YYYY_CN_MM");
        settings.put("accentColor", "#175CD3");
        settings.withArray("hiddenSections").add("certificates");
        String designBody = mapper.writeValueAsString(Map.of("settings", settings, "expectedVersion", 0));
        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/design/{templateId}", conversationId,
                        "rlt-b-ats-minimal-v1").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(designBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.settings.fontScale").value("LARGE"))
                .andExpect(jsonPath("$.data.settings.hiddenSections[0]").value("certificates"));

        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/design/{templateId}", conversationId,
                        "rlt-b-ats-minimal-v1").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(designBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_DESIGN_VERSION_CONFLICT"));

        ObjectNode invalid = settings.deepCopy();
        invalid.put("accentColor", "#FF00FF");
        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/design/{templateId}", conversationId,
                        "rlt-b-ats-minimal-v1").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("settings", invalid, "expectedVersion", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESUME_DESIGN_INVALID"));

        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        JsonNode selected = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("templateId", "rlt-b-tech-single-v1",
                                "expectedLayoutVersion", refreshed.path("layout").path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"templateId\":\"rlt-b-ats-minimal-v1\",\"expectedLayoutVersion\":%d}")
                                .formatted(selected.path("layout").path("version").asInt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeDesign.settings.fontScale").value("LARGE"))
                .andExpect(jsonPath("$.data.activeDesign.settings.dateFormat").value("YYYY_CN_MM"));

        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}/revisions", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void switchesARealDesignPresetTogetherWithTheActivePdfVariant() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"双预设验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("templateId", "rlt-b-ats-minimal-v1",
                                "expectedLayoutVersion", created.path("layout").path("version").asInt()))))
                .andExpect(status().isOk());
        JsonNode templates = data(mockMvc.perform(get(
                        "/api/v1/ai-resume/conversations/{id}/smart-templates", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        JsonNode ats = java.util.stream.StreamSupport.stream(templates.spliterator(), false)
                .filter(template -> template.path("templateId").asText().equals("rlt-b-ats-minimal-v1"))
                .findFirst().orElseThrow();
        JsonNode alternate = ats.path("presets").get(1);

        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/design/{templateId}", conversationId,
                        "rlt-b-ats-minimal-v1").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "variantCode", alternate.path("variantCode").asText(),
                                "settings", alternate.path("settings"),
                                "expectedVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variantCode").value("BLUE"))
                .andExpect(jsonPath("$.data.settings.headerLayout").value("SPLIT"))
                .andExpect(jsonPath("$.data.settings.headingStyle").value("SIDELINE"));

        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.variantCode").value("BLUE"))
                .andExpect(jsonPath("$.data.activeDesign.variantCode").value("BLUE"));

        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/design/{templateId}", conversationId,
                        "rlt-b-ats-minimal-v1").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "variantCode", "UNKNOWN", "settings", alternate.path("settings"),
                                "expectedVersion", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESUME_DESIGN_PRESET_INVALID"));
    }

    @Test
    void workbenchPdfExportsConfirmedFactsAndKeepsAnEditableLayoutForRepeatedExports() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"PDF 直接导出验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String masterId = created.path("masterId").asText();

        JsonNode target = card(created, "TARGET_JOB");
        JsonNode withTarget = data(mockMvc.perform(post(
                        "/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, target.path("id").asText()).cookie(owner.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"targetJob\":\"Java 后端工程师\"},\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode education = card(withTarget, "EDUCATION");
        data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, education.path("id").asText()).cookie(owner.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"payload":{"items":[{"school":"PDF 验收大学","major":"软件工程","degree":"本科","startDate":"2022-09","endDate":"2026-06"}]},"expectedVersion":0}
                        """))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(post("/api/v1/resumes/{id}/candidates", masterId).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldKey\":\"SELF_INTRO\",\"proposedValue\":\"这段待确认文字不能进入 PDF\"}"))
                .andExpect(status().isOk());

        JsonNode firstTask = startAndAwaitWorkbenchPdf(owner, conversationId);
        byte[] pdf = mockMvc.perform(get(firstTask.path("downloadUrl").asText()).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .startsWith("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Java 后端工程师", "PDF 验收大学")
                    .doesNotContain("这段待确认文字不能进入 PDF");
        }

        mockMvc.perform(get("/api/v1/resume-templates/layouts/current").param("masterId", masterId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.status").value("VALID"))
                .andExpect(jsonPath("$.data.layout.templateId").value("rlt-b-campus-v1"));

        JsonNode secondTask = startAndAwaitWorkbenchPdf(owner, conversationId);
        assertThat(secondTask.path("id").asText()).isNotEqualTo(firstTask.path("id").asText());
        mockMvc.perform(get("/api/v1/resume-templates/layouts/current").param("masterId", masterId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.status").value("VALID"))
                .andExpect(jsonPath("$.data.layout.templateId").value("rlt-b-campus-v1"));
    }

    @Test
    void allTwentyFourSmartTemplatePresetsProduceCompleteDistinctPdfFromTheSameCanonicalContent() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"十二模板 PDF 矩阵\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode target = card(created, "TARGET_JOB");
        JsonNode withTarget = data(mockMvc.perform(post(
                        "/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, target.path("id").asText()).cookie(owner.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"targetJob\":\"Platform Engineer\"},\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode current = submitCard(owner, conversationId, withTarget, "CONTACT", """
                {"name":"林知远","email":"lin.zhiyuan@example.com","phone":"13800001111","location":"杭州","links":["https://portfolio.example.com/lin"]}
                """);
        current = submitCard(owner, conversationId, current, "SUMMARY", """
                {"text":"平台工程师，重视可观测性、稳定交付与事实可追溯的技术决策。"}
                """);
        current = submitCard(owner, conversationId, current, "EDUCATION", """
                {"items":[{"school":"统一内容大学","major":"Computer Science","degree":"本科","startDate":"2018-09","endDate":"2022-06","description":"主修分布式系统与数据库。"}]}
                """);
        current = submitCard(owner, conversationId, current, "EXPERIENCE", """
                {"items":[{"company":"星云基础设施科技有限公司","role":"Platform Engineer","startDate":"2022-07","current":true,"location":"杭州","description":"负责开发者平台与持续交付链路。","highlights":["统一发布流程并保留完整审计记录","Improved observability for production services"]}]}
                """);
        current = submitCard(owner, conversationId, current, "PROJECTS", """
                {"items":[{"name":"JobProof Resume Platform","role":"Backend Owner","startDate":"2024-03","endDate":"2025-02","description":"构建结构化简历冻结与异步导出流程。","highlights":["保持网页预览与 PDF 内容一致","Verified immutable snapshot hashes"]}]}
                """);
        current = submitCard(owner, conversationId, current, "SKILLS", """
                {"items":[{"category":"Backend & Platform","items":["Java","Spring Boot","PostgreSQL","Docker","OpenTelemetry"]}]}
                """);
        submitCard(owner, conversationId, current, "CERTIFICATES", """
                {"items":[{"name":"Cloud Native Associate","issuer":"CNCF Training","date":"2024-06",
                  "description":"已确认取得该云原生资质，补充说明记录了对应证书名称、颁发机构、取得时间与学习范围，用于呈现可核实的技术学习经历，所有信息均来自用户确认事实，不增加未经确认的成绩或能力结论。"}]}
                """);

        List<String> templateIds = List.of(
                "rlt-b-ats-minimal-v1", "rlt-b-tech-single-v1", "rlt-b-tech-double-v1",
                "rlt-b-campus-v1", "rlt-b-career-pro-v1", "rlt-b-consulting-v1",
                "rlt-b-finance-v1", "rlt-b-product-ops-v1", "rlt-b-education-research-v1",
                "rlt-b-english-single-v1", "rlt-b-cn-table-v1", "rlt-b-qa-data-v1");
        JsonNode templates = data(mockMvc.perform(get(
                        "/api/v1/ai-resume/conversations/{id}/smart-templates", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        for (String templateId : templateIds) {
            JsonNode selected = data(mockMvc.perform(post(
                            "/api/v1/ai-resume/conversations/{id}/template", conversationId)
                            .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"templateId\":\"%s\"}".formatted(templateId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.layout.templateId").value(templateId))
                    .andReturn());
            JsonNode template = smartTemplate(templates, templateId);
            assertThat(template.path("presets")).as(templateId).hasSize(2);
            BufferedImage firstPresetPage = null;
            for (int presetIndex = 0; presetIndex < 2; presetIndex++) {
                JsonNode preset = template.path("presets").get(presetIndex);
                String variant = preset.path("variantCode").asText();
                if (presetIndex == 0) {
                    assertThat(selected.path("layout").path("variantCode").asText()).isEqualTo(variant);
                } else {
                    mockMvc.perform(put(
                                    "/api/v1/ai-resume/conversations/{id}/design/{templateId}",
                                    conversationId, templateId)
                                    .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsString(Map.of(
                                            "variantCode", variant,
                                            "settings", preset.path("settings"),
                                            "expectedVersion", 0))))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.data.variantCode").value(variant));
                    mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                                    .cookie(owner.cookie()))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.data.layout.variantCode").value(variant))
                            .andExpect(jsonPath("$.data.activeDesign.variantCode").value(variant));
                }
                JsonNode task = startAndAwaitWorkbenchPdf(owner, conversationId);
                byte[] pdf = mockMvc.perform(get(task.path("downloadUrl").asText()).cookie(owner.cookie()))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
                writePdfQaArtifact(templateId, variant, pdf);
                try (PDDocument document = Loader.loadPDF(pdf)) {
                    writePdfQaImages(templateId, variant, document);
                    String caseName = templateId + ":" + variant;
                    assertThat(document.getNumberOfPages()).as(caseName).isBetween(1, 2);
                    String normalizedText = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
                    assertThat(normalizedText).as(caseName)
                            .contains(
                                    "林知远", "Platform Engineer", "lin.zhiyuan@example.com", "杭州",
                                    "统一内容大学", "Computer Science", "星云基础设施科技有限公司",
                                    "Improved observability for production services", "JobProof Resume Platform",
                                    "Verified immutable snapshot hashes", "OpenTelemetry", "Cloud Native Associate");
                    for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                        int currentPage = pageIndex;
                        assertThat(document.getPage(pageIndex).getMediaBox().getWidth())
                                .as(caseName).isEqualTo(595.27563f);
                        assertThat(document.getPage(pageIndex).getMediaBox().getHeight())
                                .as(caseName).isEqualTo(841.8898f);
                        assertThat(document.getPage(pageIndex).getResources().getFontNames())
                                .as(caseName)
                                .anySatisfy(name -> assertThat(document.getPage(currentPage).getResources()
                                        .getFont(name).isEmbedded()).isTrue());
                        assertThat(inkPixels(new PDFRenderer(document).renderImageWithDPI(pageIndex, 72)))
                                .as(caseName + " page " + (pageIndex + 1)).isGreaterThan(1_000);
                    }
                    BufferedImage rendered = new PDFRenderer(document).renderImageWithDPI(0, 72);
                    if (presetIndex == 0) {
                        firstPresetPage = rendered;
                    } else {
                        assertThat(differentPixels(firstPresetPage, rendered)).as(caseName)
                                .isGreaterThan(1_000);
                    }
                }
            }
        }
    }

    @Test
    void longCanonicalContentPaginatesOrFailsBeforeTaskCreationWithoutSilentClipping() throws Exception {
        Session owner = seeker();
        JsonNode current = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"长内容 PDF 分页验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = current.path("id").asText();
        current = submitCard(owner, conversationId, current, "TARGET_JOB", """
                {"targetJob":"Senior Platform Engineer"}
                """);
        current = submitCard(owner, conversationId, current, "CONTACT", """
                {"name":"周明远","email":"zhou@example.com","phone":"13800002222","location":"上海"}
                """);
        current = submitCard(owner, conversationId, current, "SUMMARY", """
                {"text":"专注平台稳定性、工程效率与可追溯交付。"}
                """);
        String experience = "负责平台稳定性建设、发布治理与问题复盘，确保结构化事实完整可追溯。 ".repeat(45)
                + "EXPERIENCE-LONG-END";
        current = submitCard(owner, conversationId, current, "EXPERIENCE",
                mapper.writeValueAsString(Map.of("items", List.of(Map.of(
                        "company", "超长企业技术服务有限公司",
                        "role", "Senior Platform Engineer",
                        "startDate", "2020-01",
                        "current", true,
                        "location", "上海",
                        "description", experience)))));
        String project = "围绕异步任务、冻结快照与 PDF 渲染建立可复核的端到端验收。 ".repeat(22)
                + "PROJECT-LONG-END";
        submitCard(owner, conversationId, current, "PROJECTS",
                mapper.writeValueAsString(Map.of("items", List.of(Map.of(
                        "name", "Enterprise Resume Delivery Platform",
                        "role", "Technical Lead",
                        "startDate", "2023-03",
                        "endDate", "2025-06",
                        "description", project)))));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"rlt-b-tech-double-v1\"}"))
                .andExpect(status().isOk());
        JsonNode task = startAndAwaitWorkbenchPdf(owner, conversationId);
        byte[] pdf = mockMvc.perform(get(task.path("downloadUrl").asText()).cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("EXPERIENCE-LONG-END", "PROJECT-LONG-END", "第 2 / 2 页");
            assertThat(inkPixels(new PDFRenderer(document).renderImageWithDPI(1, 72))).isGreaterThan(1_000);
        }

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"rlt-b-career-pro-v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.status").value("VALID"));
        task = startAndAwaitWorkbenchPdf(owner, conversationId);
        pdf = mockMvc.perform(get(task.path("downloadUrl").asText()).cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("EXPERIENCE-LONG-END", "PROJECT-LONG-END", "第 2 / 2 页");
            assertThat(inkPixels(new PDFRenderer(document).renderImageWithDPI(1, 72))).isGreaterThan(1_000);
        }

        current = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        String compactExperience = "负责平台稳定性建设、发布治理与问题复盘，确保结构化事实完整可追溯。 ".repeat(28)
                + "EXPERIENCE-COMPACT-END";
        current = submitCard(owner, conversationId, current, "EXPERIENCE",
                mapper.writeValueAsString(Map.of("items", List.of(Map.of(
                        "company", "超长企业技术服务有限公司",
                        "role", "Senior Platform Engineer",
                        "startDate", "2020-01",
                        "current", true,
                        "location", "上海",
                        "description", compactExperience)))));
        String compactProject = "围绕异步任务、冻结快照与 PDF 渲染建立可复核的端到端验收。 ".repeat(12)
                + "PROJECT-COMPACT-END";
        submitCard(owner, conversationId, current, "PROJECTS",
                mapper.writeValueAsString(Map.of("items", List.of(Map.of(
                        "name", "Enterprise Resume Delivery Platform",
                        "role", "Technical Lead",
                        "startDate", "2023-03",
                        "endDate", "2025-06",
                        "description", compactProject)))));

        task = startAndAwaitWorkbenchPdf(owner, conversationId);
        pdf = mockMvc.perform(get(task.path("downloadUrl").asText()).cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isBetween(1, 2);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("EXPERIENCE-COMPACT-END", "PROJECT-COMPACT-END");
        }

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"rlt-b-ats-minimal-v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.status").value("VALID"));
        task = startAndAwaitWorkbenchPdf(owner, conversationId);
        pdf = mockMvc.perform(get(task.path("downloadUrl").asText()).cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isBetween(1, 2);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("EXPERIENCE-COMPACT-END", "PROJECT-COMPACT-END");
        }
    }

    @Test
    void rejectsSensitiveFieldsAndReplaysIdempotentMessages() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode contact = card(created, "CONTACT");

        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/cards/{card}/draft",
                        conversationId, contact.path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"name\":\"测试用户\",\"gender\":\"女\"},\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_SENSITIVE_ATTRIBUTE_FORBIDDEN"));

        JsonNode education = card(created, "EDUCATION");
        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/cards/{card}/draft",
                        conversationId, education.path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"items\":[{\"school\":\"示例大学\",\"profile\":{\"年龄\":22}}]},\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_SENSITIVE_ATTRIBUTE_FORBIDDEN"));

        String clientMessageId = UUID.randomUUID().toString();
        MvcResult first = mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"%s\",\"text\":\"我想准备 Java 实习简历\"}".formatted(clientMessageId)))
                .andExpect(status().isOk()).andReturn();
        MvcResult replay = mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"%s\",\"text\":\"我想准备 Java 实习简历\"}".formatted(clientMessageId)))
                .andExpect(status().isOk()).andReturn();
        assertThat(data(first).path("id").asText()).isEqualTo(data(replay).path("id").asText());
    }

    @Test
    void contactCardRequiresStructuredNameAndReachableContactMethod() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode contact = card(created, "CONTACT");
        String cardId = contact.path("id").asText();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit", conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"email\":\"seeker@example.com\"},\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_CONTACT_NAME_REQUIRED"));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit", conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"name\":\"林知远\"},\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_CONTACT_METHOD_REQUIRED"));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit", conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"name\":\"林知远\",\"email\":\"bad-email\"},\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_CONTACT_EMAIL_INVALID"));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit", conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"name\":\"林知远\",\"phone\":\"+86 138-0000-1111\",\"location\":\"杭州\"},\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.basics.name").value("林知远"))
                .andExpect(jsonPath("$.data.content.basics.phone").value("+86 138-0000-1111"))
                .andExpect(jsonPath("$.data.content.basics.location").value("杭州"));
    }

    @Test
    void taxonomyIsAnonymousAndSupportsAliases() throws Exception {
        JsonNode javaJob = data(mockMvc.perform(get("/api/v1/job-taxonomy").param("keyword", "JAVA后端"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("Java"))
                .andExpect(jsonPath("$.data[0].categoryId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].groupId").isNotEmpty())
                .andReturn()).get(0);
        assertThat(javaJob.path("parentId").asText()).isEqualTo(javaJob.path("groupId").asText());

        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\"}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode target = card(created, "TARGET_JOB");
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        created.path("id").asText(), target.path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"targetJob\":\"Java\",\"taxonomyNodeId\":\"missing-job\"},\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_TARGET_JOB_TAXONOMY_INVALID"));
    }

    @Test
    void careerLibraryEvidenceIsOffByDefaultPersistsPerConversationAndInterviewRoutesAreGone() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careerLibraryEvidence.enabled").value(false))
                .andReturn());
        String conversationId = created.path("id").asText();

        mockMvc.perform(post("/api/v1/career-library/records").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PROJECT\",\"title\":\"招聘平台\",\"description\":\"Java 后端开发\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/preferences/career-library-evidence", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.snapshotVersion").value(1));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careerLibraryEvidence.enabled").value(true));
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/interviews", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PRACTICE\",\"questionLimit\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void languageBranchesSwitchAndRequireExplicitParentSync() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode target = card(created, "TARGET_JOB");
        JsonNode withTarget = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, target.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"targetJob\":\"Java 后端工程师\"},\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn());

        JsonNode language = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/language",
                        conversationId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"languageCode\":\"en-US\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andReturn());
        String languageId = language.path("id").asText();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/{branch}/switch",
                        conversationId, languageId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
        String baseId = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}/branches", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn()).findValuesAsText("branchType")
                .contains("BASE") ? created.path("activeBranchId").asText() : null;
        assertThat(baseId).isNotNull();
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/{branch}/switch",
                        conversationId, baseId).cookie(owner.cookie())).andExpect(status().isOk());

        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        JsonNode summary = card(refreshed, "SUMMARY");
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, summary.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"text\":\"只包含本人确认事实的职业简介\"},\"expectedVersion\":%d}"
                                .formatted(summary.path("versionNo").asInt())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}/branches/{branch}/diff",
                        conversationId, languageId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncRequired").value(true))
                .andExpect(jsonPath("$.data.changes[0].path").isNotEmpty());
        JsonNode branches = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}/branches", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        JsonNode languageView = null;
        for (JsonNode branch : branches) if (languageId.equals(branch.path("id").asText())) languageView = branch;
        assertThat(languageView).isNotNull();
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/{branch}/sync",
                        conversationId, languageId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":%d}".formatted(languageView.path("versionNo").asInt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncRequired").value(false))
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.reviewMetadata.translationStatus").value("NOT_STARTED"));
    }

    @Test
    void translationRequiresReviewBeforeItCanReplaceTheActiveBranch() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("translation-1", "channel-1", "qwen-plus",
                "{\"translated\":{\"summary\":\"Backend engineer focused on verified delivery facts.\"},\"unconfirmedProperNames\":[]}",
                new Usage(120, 45, 165), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"翻译验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode summary = card(created, "SUMMARY");
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, summary.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"text\":\"专注可验证交付事实的后端工程师。\"},\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        JsonNode branch = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/language",
                        conversationId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"languageCode\":\"en-US\"}"))
                .andExpect(status().isOk()).andReturn());
        String branchId = branch.path("id").asText();
        int quotaBefore = quota.current(owner.accountId()).remainingUnits();

        JsonNode translated = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/{branch}/translate",
                        conversationId, branchId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"translate-one\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.reviewMetadata.translationStatus").value("AWAITING_CONFIRMATION"))
                .andReturn());
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(quotaBefore - 1);

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/{branch}/switch",
                        conversationId, branchId).cookie(owner.cookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resume.selfIntro").value("专注可验证交付事实的后端工程师。"));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/{branch}/translation/confirm",
                        conversationId, branchId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":%d}".formatted(translated.path("versionNo").asInt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.reviewMetadata.translationStatus").value("CONFIRMED"));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resume.selfIntro")
                        .value("Backend engineer focused on verified delivery facts."));
    }

    @Test
    void writingPreferencesAreControlledAndHistoryDeletionKeepsConfirmedResume() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"隐私清理验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String masterId = created.path("masterId").asText();
        JsonNode summary = card(created, "SUMMARY");
        JsonNode confirmed = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, summary.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"text\":\"只保留本人确认的正式简介\"},\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}/preferences/writing-style", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("SYSTEM_RECOMMENDED"));
        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/preferences/writing-style", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"styleCode\":\"TECHNICAL_RIGOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label").value("技术严谨"));
        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/preferences/writing-style", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"styleCode\":\"FREEFORM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_WRITING_STYLE_INVALID"));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"privacy-message\",\"text\":\"这段正文应当可删除\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/resumes/{id}/candidates", masterId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldKey\":\"SELF_INTRO\",\"proposedValue\":\"尚未确认的候选\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/ai-resume/conversations/{id}/history", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageBodiesDeleted").value(1))
                .andExpect(jsonPath("$.data.pendingCandidatesDeleted").value(1))
                .andExpect(jsonPath("$.data.auditMarker").value("AI_RESUME_HISTORY_REDACTED"));

        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.resume.pendingCandidateIds.length()").value(0))
                .andExpect(jsonPath("$.data.resume.selfIntro").value("只保留本人确认的正式简介"));
        assertThat(confirmed.path("resume").path("selfIntro").asText())
                .isEqualTo("只保留本人确认的正式简介");
    }

    @Test
    void retiredTextImportDirectsPastedResumeTextToChatWithoutCreatingLegacyCandidates() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"文本导入验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/text-import", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"个人简介\\n专注 Java 后端开发\\n性别：女\\n教育经历\\n示例大学 软件工程 本科\\n工作经历\\n示例公司 Java 实习生"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_TEXT_IMPORT_MOVED_TO_CHAT"));

        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resume.education").doesNotExist())
                .andExpect(jsonPath("$.data.resume.pendingCandidateIds.length()").value(0))
                .andExpect(jsonPath("$.data.changeSets.length()").value(0));
    }

    @Test
    void resumePhotoFlowsThroughPrivateUploadFreezeAndPdfExport() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"照片验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        MockMultipartFile photo = new MockMultipartFile("file", "portrait.png", "image/png", portraitPng());

        JsonNode uploaded = data(mockMvc.perform(multipart("/api/v1/ai-resume/conversations/{id}/photo",
                        conversationId).file(photo).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photo.fileId").isNotEmpty())
                .andReturn());
        String fileId = uploaded.path("photo").path("fileId").asText();
        mockMvc.perform(get("/api/v1/files/{id}/content", fileId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).startsWith("image/png"));
        Session stranger = seeker();
        mockMvc.perform(get("/api/v1/files/{id}/content", fileId).cookie(stranger.cookie()))
                .andExpect(status().isForbidden());

        JsonNode current = submitCard(owner, conversationId, uploaded, "TARGET_JOB", """
                {"targetJob":"产品运营经理"}
                """);
        current = submitCard(owner, conversationId, current, "CONTACT", """
                {"name":"照片链路验收","email":"photo@example.com","phone":"13800003333","location":"深圳","links":["https://portfolio.example.com/photo"]}
                """);
        current = submitCard(owner, conversationId, current, "SUMMARY", """
                {"text":"验证私有照片只进入冻结后的受控 PDF。"}
                """);
        current = submitCard(owner, conversationId, current, "EDUCATION", """
                {"items":[{"school":"匿名验收大学","major":"信息管理","degree":"本科","startDate":"2017-09","endDate":"2021-06"}]}
                """);
        current = submitCard(owner, conversationId, current, "EXPERIENCE", """
                {"items":[{"company":"星海零售科技","role":"产品运营","startDate":"2021-07","current":true,"description":"负责增长实验与业务流程优化。"}]}
                """);
        current = submitCard(owner, conversationId, current, "PROJECTS", """
                {"items":[{"name":"会员增长平台","role":"项目负责人","startDate":"2023-03","endDate":"2024-02","description":"建立实验复盘与指标审计流程。"}]}
                """);
        submitCard(owner, conversationId, current, "SKILLS", """
                {"items":[{"category":"产品运营","items":["数据分析","用户研究","增长实验"]}]}
                """);
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/template", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"rlt-b-campus-v1\"}"))
                .andExpect(status().isOk());
        JsonNode templates = data(mockMvc.perform(get(
                        "/api/v1/ai-resume/conversations/{id}/smart-templates", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        JsonNode photoPreset = smartTemplate(templates, "rlt-b-campus-v1").path("presets").get(1);
        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/design/{templateId}", conversationId,
                        "rlt-b-campus-v1").cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "variantCode", photoPreset.path("variantCode").asText(),
                                "settings", photoPreset.path("settings"),
                                "expectedVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.photoMode").value("SHOW"));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.photoFileId").value(fileId))
                .andExpect(jsonPath("$.data.layout.variantCode").value("PHOTO"))
                .andExpect(jsonPath("$.data.activeDesign.settings.photoMode").value("SHOW"));

        JsonNode task = startAndAwaitWorkbenchPdf(owner, conversationId);
        byte[] pdf = mockMvc.perform(get(task.path("downloadUrl").asText()).cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        writePdfQaArtifact("rlt-b-campus-v1", "PHOTO-WITH-PORTRAIT", pdf);
        JsonNode afterExport = data(mockMvc.perform(get(
                        "/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        String frozenLayoutId = afterExport.path("resume").path("versions").get(0)
                .path("layoutInstanceId").asText();
        mockMvc.perform(get("/api/v1/resume-templates/layouts/{id}", frozenLayoutId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"))
                .andExpect(jsonPath("$.data.variantCode").value("PHOTO"))
                .andExpect(jsonPath("$.data.design.photoMode").value("SHOW"));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            writePdfQaImages("rlt-b-campus-v1", "PHOTO-WITH-PORTRAIT", document);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("照片链路验收", "photo@example.com", "13800003333", "深圳",
                            "portfolio.example.com/", "产品运营经理", "私有照片",
                            "匿名验收大学", "星海零售科技", "会员增长平台", "数据分析");
            assertThat(document.getPage(0).getResources().getXObjectNames()).anySatisfy(name ->
                    assertThat(document.getPage(0).getResources().getXObject(name))
                            .isInstanceOf(PDImageXObject.class));
        }

        JsonNode anonymousTask = startAndAwaitWorkbenchPdf(owner, conversationId, "ANONYMOUS");
        byte[] anonymousPdf = mockMvc.perform(get(anonymousTask.path("downloadUrl").asText()).cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        writePdfQaArtifact("rlt-b-campus-v1", "PHOTO-ANONYMOUS", anonymousPdf);
        try (PDDocument document = Loader.loadPDF(anonymousPdf)) {
            writePdfQaImages("rlt-b-campus-v1", "PHOTO-ANONYMOUS", document);
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("产品运营经理", "私有照片", "匿名验收大学", "星海零售科技",
                            "会员增长平台", "数据分析")
                    .doesNotContain("照片链路验收", "photo@example.com", "13800003333", "深圳",
                            "portfolio.example.com/");
            assertThat(document.getPage(0).getResources().getXObjectNames()).noneSatisfy(name ->
                    assertThat(document.getPage(0).getResources().getXObject(name))
                            .isInstanceOf(PDImageXObject.class));
        }

        JsonNode afterAnonymous = data(mockMvc.perform(get(
                        "/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.basics.name").value("照片链路验收"))
                .andExpect(jsonPath("$.data.content.basics.email").value("photo@example.com"))
                .andExpect(jsonPath("$.data.content.photoFileId").value(fileId))
                .andReturn());
        JsonNode anonymousVersion = versionBySource(afterAnonymous, "AI_WORKBENCH_PDF_ANONYMOUS");
        assertThat(anonymousVersion.path("snapshot").path("exportMode").asText()).isEqualTo("ANONYMOUS");
        assertThat(anonymousVersion.path("snapshot").path("title").asText()).isEqualTo("匿名简历");
        assertThat(anonymousVersion.path("snapshot").path("content").path("basics").has("name")).isFalse();
        assertThat(anonymousVersion.path("snapshot").path("content").path("basics").has("email")).isFalse();
        assertThat(anonymousVersion.path("snapshot").path("content").hasNonNull("photoFileId")).isFalse();
        JsonNode anonymousTaskMetadata = mapper.readTree(asyncTasks.findById(
                anonymousTask.path("id").asText()).orElseThrow().getPayloadJson());
        assertThat(anonymousTaskMetadata.path("exportMode").asText()).isEqualTo("ANONYMOUS");
        assertThat(events.replay(owner.accountId(), conversationId, 0)).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("pdf.export.started");
            assertThat(event.payload()).containsEntry("exportMode", "ANONYMOUS");
        });
        assertThat(auditEvents.findAll()).anySatisfy(event -> {
            assertThat(event.getAction()).isEqualTo("AI_RESUME_PDF_EXPORT_STARTED");
            assertThat(event.getObjectId()).isEqualTo(conversationId);
            assertThat(event.getSummary()).contains("mode=ANONYMOUS");
        });

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/export-pdf", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exportMode\":\"UNSUPPORTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESUME_PDF_EXPORT_MODE_INVALID"));

        mockMvc.perform(delete("/api/v1/ai-resume/conversations/{id}/photo", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photo").doesNotExist());
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}/revisions", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].source").value("PHOTO_REMOVED"))
                .andExpect(result -> assertThat(data(result).findValuesAsText("source"))
                        .contains("PHOTO_CONFIRMED"));
    }

    @Test
    void chatCreatesItemizedChangeThatCanBeAppliedAndUndone() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"逐条修改验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode summaryCard = card(created, "SUMMARY");
        String before = "具备后端功能开发、接口联调和问题排查经验，能够根据需求完成任务拆分并配合团队交付。";
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{cardId}/submit",
                        conversationId, summaryCard.path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("payload", Map.of("text", before),
                                "expectedVersion", summaryCard.path("versionNo").asInt()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        String proposed = "能够围绕已确认需求梳理后端功能边界，通过任务拆分、接口联调和异常排查推进开发工作，并结合回归验证解决交付过程中的具体问题。重视模块职责和协作反馈，能够在不改变事实边界的前提下整理实现过程，形成结构清晰、便于复核和持续维护的交付内容，同时支持团队按计划完成后续联调与稳定交付。";
        when(gateway.execute(any(), any())).thenReturn(new Response("change-1", "channel-1", "qwen-plus",
                mapper.writeValueAsString(Map.of(
                        "assistantText", "我已将个人简介整理为一条可直接核对的修改。",
                        "intentCode", "RESUME_CHANGE",
                        "clarificationQuestions", List.of(),
                        "changes", List.of(Map.of(
                                "module", "SUMMARY", "targetPath", "/summary", "operation", "REPLACE_TEXT",
                                "beforeValue", before, "proposedValue", proposed, "reason", "重组已有事实并补足行动和验证结构",
                                "completeGeneration", false,
                                "sourceFacts", List.of(Map.of("source", "resume/summary", "quote", before)))))),
                new Usage(120, 90, 210), null));

        JsonNode responded = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages/respond",
                        conversationId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"inline-change-1\",\"text\":\"请优化个人简介\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changeSets.length()").value(1))
                .andExpect(jsonPath("$.data.changeSets[0].items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.content.summary").value(before))
                .andReturn());
        String setId = responded.path("changeSets").path(0).path("id").asText();
        String itemId = responded.path("changeSets").path(0).path("items").path(0).path("id").asText();

        JsonNode applied = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/change-sets/{setId}/items/{itemId}/decision",
                        conversationId, setId, itemId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPLY\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("APPLIED"))
                .andReturn());
        assertThat(applied.path("items").path(0).path("appliedRevisionId").asText()).isNotBlank();
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.summary").value(proposed));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/change-sets/{setId}/items/{itemId}/undo",
                        conversationId, setId, itemId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("UNDONE"));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.summary").value(before));
    }

    @Test
    void itemizedChangesSupportCorrectionRejectionAndStaleProtection() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"逐条决策验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode summaryCard = card(created, "SUMMARY");
        String before = "具备后端功能开发、接口联调和问题排查经验，能够根据需求完成任务拆分并配合团队交付。";
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{cardId}/submit",
                        conversationId, summaryCard.path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("payload", Map.of("text", before),
                                "expectedVersion", summaryCard.path("versionNo").asInt()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        String proposedOne = "能够围绕已确认需求梳理后端功能边界，通过任务拆分、接口联调和异常排查推进开发工作，并结合回归验证处理交付过程中的具体问题。重视模块职责与协作反馈，能够在既有事实范围内整理实现过程，形成结构清晰、便于复核和持续维护的交付内容，同时支持团队按计划完成后续联调和稳定交付。";
        String corrected = "能够围绕已确认需求梳理后端功能边界，通过任务拆分、接口联调和异常排查推进开发工作，并结合回归验证解决交付过程中的具体问题。重视模块职责与协作反馈，能够持续整理实现过程和处理记录，形成结构清晰、便于复核和维护的交付内容，同时配合团队按计划完成后续联调、问题修正与稳定交付。";
        String proposedTwo = "能够根据已确认需求推进后端功能开发，通过拆分任务、联调接口和排查异常处理交付问题，并结合测试反馈完成问题修正与结果验证。注重梳理模块边界、协作事项和处理记录，能够把已有实践整理为结构完整、事实清晰且便于复核的简历表达，支持团队持续推进联调工作与版本交付，并通过处理记录核对各阶段任务是否完成。";
        String proposedThree = "能够围绕后端功能开发梳理需求和模块边界，通过任务拆分、接口联调、异常排查与回归验证推进问题处理，并根据协作反馈整理交付事项。重视事实边界与表达准确性，能够将已有实践组织为行动、方法和验证结果清晰的内容，支持团队持续完成后续联调、问题修正和稳定交付，并结合交付记录复核各阶段处理结果。";
        when(gateway.execute(any(), any())).thenReturn(
                inlineSummaryChange("change-correct", before, proposedOne),
                inlineSummaryChange("change-reject", corrected, proposedTwo),
                inlineSummaryChange("change-stale", corrected, proposedThree));

        JsonNode first = inlineChange(owner, conversationId, "change-correct-request");
        String firstSet = first.path("changeSets").path(0).path("id").asText();
        String firstItem = first.path("changeSets").path(0).path("items").path(0).path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/change-sets/{setId}/items/{itemId}/decision",
                        conversationId, firstSet, firstItem).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("decision", "APPLY", "editedValue", corrected,
                                "expectedVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.items[0].correctedValue").value(corrected));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}/revisions", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(data(result).findValuesAsText("source"))
                        .contains("USER_CORRECTED_AI_CHANGE"));

        JsonNode second = inlineChange(owner, conversationId, "change-reject-request");
        JsonNode secondSet = second.path("changeSets").path(1);
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/change-sets/{setId}/items/{itemId}/decision",
                        conversationId, secondSet.path("id").asText(), secondSet.path("items").path(0).path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.items[0].status").value("REJECTED"));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.summary").value(corrected));

        JsonNode third = inlineChange(owner, conversationId, "change-stale-request");
        JsonNode thirdSet = third.path("changeSets").path(2);
        JsonNode refreshedSummaryCard = card(third, "SUMMARY");
        String manual = "能够根据新的人工确认内容重新组织后端开发实践，通过需求梳理、任务拆分和接口联调推进实现，并结合异常排查与回归验证记录问题处理结果。持续维护模块边界、协作事项和交付记录，确保当前简历内容以用户最新修改为准，同时支持团队完成后续问题修正、系统测试和稳定交付，并通过人工复核确认各阶段事项已经准确记录。";
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{cardId}/submit",
                        conversationId, refreshedSummaryCard.path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("payload", Map.of("text", manual),
                                "expectedVersion", refreshedSummaryCard.path("versionNo").asInt()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/change-sets/{setId}/items/{itemId}/decision",
                        conversationId, thirdSet.path("id").asText(), thirdSet.path("items").path(0).path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPLY\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("STALE"))
                .andExpect(jsonPath("$.data.items[0].status").value("STALE"));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.summary").value(manual));
    }

    @Test
    void pendingChangeCanOnlyBeAppliedOnItsOriginalBranch() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"修改分支隔离\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String baseBranchId = created.path("activeBranchId").asText();
        JsonNode summaryCard = card(created, "SUMMARY");
        String before = "具备后端功能开发、接口联调和问题排查经验，能够根据需求完成任务拆分并配合团队交付。";
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{cardId}/submit",
                        conversationId, summaryCard.path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("payload", Map.of("text", before),
                                "expectedVersion", summaryCard.path("versionNo").asInt()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        String proposed = "能够围绕已确认需求梳理后端功能边界，通过任务拆分、接口联调和异常排查推进开发工作，并结合回归验证解决交付过程中的具体问题。重视模块职责和协作反馈，能够在不改变事实边界的前提下整理实现过程，形成结构清晰、便于复核和持续维护的交付内容，同时支持团队按计划完成后续联调与稳定交付。";
        when(gateway.execute(any(), any())).thenReturn(inlineSummaryChange("change-branch", before, proposed));
        JsonNode pending = inlineChange(owner, conversationId, "change-branch-request");
        JsonNode set = pending.path("changeSets").path(0);

        JsonNode language = data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/language",
                        conversationId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"languageCode\":\"en-US\"}"))
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/{branch}/switch",
                        conversationId, language.path("id").asText()).cookie(owner.cookie()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/change-sets/{setId}/items/{itemId}/decision",
                        conversationId, set.path("id").asText(), set.path("items").path(0).path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPLY\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_CHANGE_BRANCH_MISMATCH"));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/branches/{branch}/switch",
                        conversationId, baseBranchId).cookie(owner.cookie()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/change-sets/{setId}/items/{itemId}/decision",
                        conversationId, set.path("id").asText(), set.path("items").path(0).path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPLY\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("APPLIED"));
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.summary").value(proposed));
    }

    @Test
    void chatStreamingEmitsAcceptanceLoadingDeltasAndPersistedCompletion() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.executeStreaming(any(), any(), any())).thenAnswer(invocation -> {
            Consumer<String> deltas = invocation.getArgument(2);
            deltas.accept("请提供");
            deltas.accept("教育经历。");
            return new Response("chat-stream-1", "channel-1", "qwen-plus", "请提供教育经历。",
                    new Usage(24, 7, 31), null);
        });
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"流式验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        MvcResult started = mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages/stream", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"clientMessageId\":\"stream-one\",\"text\":\"帮我继续完善\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();
        String stream = completed.getResponse().getContentAsString();

        assertThat(stream).contains("event:user.accepted", "event:assistant.started",
                "event:assistant.delta", "请提供", "教育经历。", "event:assistant.completed");
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.messages[1].content").value("请提供教育经历。"));
    }

    @Test
    void duplicateStreamingRequestDoesNotPersistFailureForTheActiveRequest() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        CountDownLatch modelStarted = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        when(gateway.executeStreaming(any(), any(), any())).thenAnswer(invocation -> {
            modelStarted.countDown();
            if (!releaseModel.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test model timed out");
            return new Response("chat-duplicate", "channel-1", "qwen-plus", "请提供教育经历。",
                    new Usage(24, 7, 31), null);
        });
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"重复流式请求验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie())).andExpect(status().isOk());
        int before = quota.current(owner.accountId()).remainingUnits();
        String body = "{\"clientMessageId\":\"duplicate-stream\",\"text\":\"帮我继续完善\"}";
        MvcResult original = mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages/stream", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM).content(body))
                .andExpect(request().asyncStarted()).andReturn();
        try {
            assertThat(modelStarted.await(5, TimeUnit.SECONDS)).isTrue();
            MvcResult duplicate = mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages/stream", conversationId)
                            .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM).content(body))
                    .andExpect(request().asyncStarted()).andReturn();
            String rejected = mockMvc.perform(asyncDispatch(duplicate)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(rejected).contains("event:request.failed", "AI_FOREGROUND_TASK_LIMIT")
                    .doesNotContain("event:assistant.failed");
            mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.messages.length()").value(1));
        } finally {
            releaseModel.countDown();
        }
        assertThat(mockMvc.perform(asyncDispatch(original)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).contains("event:assistant.completed");
        mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId).cookie(owner.cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[1].status").value("COMPLETED"));
        MvcResult replay = mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages/stream", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM).content(body))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(mockMvc.perform(asyncDispatch(replay)).andReturn().getResponse().getContentAsString())
                .contains("event:assistant.completed").doesNotContain("event:assistant.failed");
        verify(gateway, times(1)).executeStreaming(any(), any(), any());
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(before - 1);
    }

    @Test
    void cancellingAnInFlightResponseDiscardsLateOutputAndRefundsQuota() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        CountDownLatch modelStarted = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        when(gateway.execute(any(), any())).thenAnswer(invocation -> {
            modelStarted.countDown();
            if (!releaseModel.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test model timed out");
            return new Response("chat-1", "channel-1", "qwen-plus", "这段迟到回复必须被丢弃。",
                    new Usage(30, 12, 42), null);
        });
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"取消验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int before = quota.current(owner.accountId()).remainingUnits();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var response = executor.submit(() -> mockMvc.perform(
                            post("/api/v1/ai-resume/conversations/{id}/messages/respond", conversationId)
                                    .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"clientMessageId\":\"cancel-me\",\"text\":\"请检查我的简历\"}"))
                    .andExpect(status().isOk()).andReturn());
            assertThat(modelStarted.await(5, TimeUnit.SECONDS)).isTrue();
            mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages/{request}/cancel",
                            conversationId, "cancel-me").cookie(owner.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accepted").value(true))
                    .andExpect(jsonPath("$.data.status").value("CANCEL_REQUESTED"));
            releaseModel.countDown();
            JsonNode completed = data(response.get(5, TimeUnit.SECONDS));
            assertThat(completed.path("messages").findValuesAsText("status")).contains("CANCELLED");
            assertThat(completed.path("messages").findValuesAsText("content"))
                    .doesNotContain("这段迟到回复必须被丢弃。");
            assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(before);
        } finally {
            releaseModel.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void skillSuggestionUsesTwoStageConfirmationWithoutMutatingTheCard() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any()))
                .thenReturn(new Response("skill-names", "channel-1", "qwen-plus", """
                        {"candidates":[
                          {"name":"Java","category":"编程语言","reason":"当前技能草稿中已填写","sourceFields":["currentSkills"]},
                          {"name":"Spring Boot","category":"后端框架","reason":"可与现有 Java 技能组合使用","sourceFields":["currentSkills"]},
                          {"name":"C","category":"编程语言","reason":"可作为补充语言候选","sourceFields":["currentSkills"]}
                        ]}
                        """, new Usage(30, 20, 50), null))
                .thenReturn(new Response("skill-details", "channel-1", "qwen-plus", """
                        {"groups":[{"category":"后端框架","items":["Java","Spring Boot"],
                          "description":"• 使用 Java 组织后端业务逻辑、异常处理与数据访问代码，保持模块边界清晰，并便于后续调试、协作和持续维护。\\n• 结合 Spring Boot 配置应用组件、接口层与基础依赖，支持常见服务功能的开发、联调和工程化交付。",
                          "sourceFields":["currentSkills"]}]}
                        """, new Usage(45, 30, 75), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"技能建议验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "SKILLS").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        String currentSkills = "[{\"category\":\"编程语言\",\"items\":[\"Java\",\"React\"]}]";
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/skill-suggestions",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"skill-names","phase":"NAMES","mode":"EXPAND",
                                 "currentSkills":%s,"selectedNames":[],"confirmedNames":[]}
                                """.formatted(currentSkills)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(3))
                .andExpect(jsonPath("$.data.candidates[0].evidenceStatus").value("SUPPORTED"))
                .andExpect(jsonPath("$.data.candidates[1].evidenceStatus").value("NEEDS_CONFIRMATION"))
                .andExpect(jsonPath("$.data.candidates[1].sourceRefs.length()").value(0))
                .andExpect(jsonPath("$.data.candidates[2].evidenceStatus").value("NEEDS_CONFIRMATION"))
                .andExpect(jsonPath("$.data.candidates[2].sourceRefs.length()").value(0))
                .andExpect(jsonPath("$.data.candidates[0].sourceRefs[0].label").value("当前技能草稿"))
                .andExpect(jsonPath("$.data.remainingQuota").value(beforeQuota - 1))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-skills-v2"));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/skill-suggestions",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"skill-details-rejected","phase":"DETAILS","mode":"EXPAND",
                                 "currentSkills":%s,"selectedNames":["Java","Spring Boot"],"confirmedNames":[]}
                                """.formatted(currentSkills)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_SKILL_CONFIRMATION_REQUIRED"));

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/skill-suggestions",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"skill-details","phase":"DETAILS","mode":"EXPAND",
                                 "currentSkills":%s,"selectedNames":["Java","Spring Boot"],
                                 "confirmedNames":["Spring Boot"]}
                                """.formatted(currentSkills)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].category").value("后端开发"))
                .andExpect(jsonPath("$.data.groups[0].items.length()").value(2))
                .andExpect(jsonPath("$.data.groups[0].verificationRequired").value(true))
                .andExpect(jsonPath("$.data.groups[0].verificationItems.length()").value(2))
                .andExpect(jsonPath("$.data.remainingQuota").value(beforeQuota - 2));

        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        assertThat(card(refreshed, "SKILLS").path("payload").toString()).doesNotContain("Spring Boot");
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void skillSuggestionRepairsShortDescriptionsAndKeepsEverySelectedSkill() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any()))
                .thenReturn(new Response("skill-details-short", "channel-1", "qwen-plus", """
                        {"groups":[
                          {"category":"工程工具","items":["Maven","Git"],
                           "description":"• Maven 与 Git","sourceFields":["currentSkills"]},
                          {"category":"数据库与存储","items":["MySQL","MyBatis"],
                           "description":"• MySQL 与 MyBatis","sourceFields":["currentSkills"]}
                        ]}
                        """, new Usage(40, 18, 58), null))
                .thenReturn(new Response("skill-details-repaired", "channel-1", "qwen-plus", """
                        {"groups":[
                          {"category":"构建与版本协作","items":["Maven","Git"],
                           "description":"• 使用 Maven 管理项目依赖、构建生命周期和多环境配置，配合统一目录与插件约定支持后端应用的编译、测试、打包和持续交付准备。\\n• 使用 Git 完成分支协作、变更审查、冲突处理和版本追踪，使代码调整具备清晰记录并便于团队协同与问题回溯。",
                           "sourceFields":["currentSkills"]},
                          {"category":"关系型数据库与持久层","items":["MySQL","MyBatis"],
                           "description":"• 使用 MySQL 设计关系数据表、约束、索引与常见查询，结合事务边界和执行计划检查支持业务数据的可靠存储、检索与维护。\\n• 使用 MyBatis 组织映射配置、参数绑定和结果转换，衔接应用服务与数据库访问层，并关注查询可读性、复用性和后续调试。",
                           "sourceFields":["currentSkills"]}
                        ]}
                        """, new Usage(70, 110, 180), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"多技术栈描述验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "SKILLS").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        JsonNode result = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/skill-suggestions",
                                conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"skill-details-quality-repair","phase":"DETAILS","mode":"EXTRACT",
                                 "currentSkills":[{"category":"已有技能","items":["Maven","Git","MySQL","MyBatis"]}],
                                 "selectedNames":["Maven","Git","MySQL","MyBatis"],"confirmedNames":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(2))
                .andExpect(jsonPath("$.data.groups[0].category").value("工程工具"))
                .andExpect(jsonPath("$.data.groups[1].category").value("数据库与存储"))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-skills-v2"))
                .andReturn());

        List<String> returnedItems = new ArrayList<>();
        result.path("groups").forEach(group ->
                group.path("items").forEach(item -> returnedItems.add(item.asText())));
        assertThat(returnedItems).containsExactlyInAnyOrder("Maven", "Git", "MySQL", "MyBatis");
        for (JsonNode description : result.path("groups").findValues("description")) {
            long visibleCharacters = description.asText().codePoints()
                    .filter(codePoint -> !Character.isWhitespace(codePoint)).count();
            assertThat(visibleCharacters).isBetween(80L, 140L);
            assertThat(description.asText().lines()).hasSizeBetween(2, 3);
        }
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void certificateSuggestionExtractsAllLibraryNamesThenBuildsOnlySelectedEntries() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any()))
                .thenReturn(new Response("certificate-details", "channel-1", "qwen-plus", """
                        {"certificates":[
                          {"name":"软件设计师","issuer":"工业和信息化部","date":"2025-05",
                           "description":"• 已取得计算机技术与软件专业技术资格中级资格，资料记录明确标注为已取得，并保留对应发证机构与取得时间。\\n• 补充说明围绕该中级资格的已确认名称、资格层级和资料状态整理，未增加证书编号、成绩或有效期等未记录信息。",
                           "sourceFields":["draft.1"]},
                          {"name":"PMP","issuer":"Project Management Institute","date":"2024-08",
                           "description":"• 已取得项目管理专业人士资格，求职资料记录明确标注为已取得，并保留对应发证机构与取得时间。\\n• 补充说明围绕该专业人士资格的已确认名称和资料状态整理，未增加证书编号、考试成绩或有效期等未记录信息。",
                           "sourceFields":["draft.2"]}
                        ]}
                        """, new Usage(60, 70, 130), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"证书提取验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "CERTIFICATES").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/career-library/records").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type":"CERTIFICATE","title":"软件设计师","organization":"工业和信息化部",
                                 "startDate":"2025-05","description":"计算机技术与软件专业技术资格中级资格，资料状态为已取得"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/career-library/records").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type":"CERTIFICATE","title":"PMP","organization":"Project Management Institute",
                                 "startDate":"2024-08","description":"项目管理专业人士资格，资料记录状态为已取得"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/preferences/career-library-evidence",
                        conversationId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        String currentCertificates = """
                [{"name":"CET-6","issuer":"教育部教育考试院","date":"2023-06"},
                 {"name":"软件设计师","issuer":"工业和信息化部","date":"2025-05",
                  "description":"计算机技术与软件专业技术资格中级资格，资料状态为已取得，并记录了资格层级与取得信息"},
                 {"name":"PMP","issuer":"Project Management Institute","date":"2024-08",
                  "description":"项目管理专业人士资格，资料记录状态为已取得，并记录了资格名称与对应取得信息"}]
                """;
        JsonNode names = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/certificate-suggestions",
                                conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON).content("""
                                {"clientRequestId":"certificate-names-all","phase":"NAMES",
                                 "currentCertificates":%s,"selectedNames":[]}
                                """.formatted(currentCertificates)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(3))
                .andExpect(jsonPath("$.data.careerLibraryEvidenceEnabled").value(true))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-certificates-v4"))
                .andExpect(jsonPath("$.data.model").value("system-deterministic"))
                .andExpect(jsonPath("$.data.inputTokens").value(0))
                .andExpect(jsonPath("$.data.outputTokens").value(0))
                .andExpect(jsonPath("$.data.remainingQuota").value(500))
                .andReturn());
        assertThat(names.path("candidates").findValuesAsText("name"))
                .containsExactlyInAnyOrder("CET-6", "软件设计师", "PMP");
        assertThat(names.path("candidates").findValuesAsText("label"))
                .anyMatch(label -> label.startsWith("求职资料库："));

        JsonNode details = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/certificate-suggestions",
                                conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON).content("""
                                {"clientRequestId":"certificate-details-selected","phase":"DETAILS",
                                 "currentCertificates":%s,"selectedNames":["软件设计师","PMP"]}
                                """.formatted(currentCertificates)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificates.length()").value(2))
                .andExpect(jsonPath("$.data.certificates[0].issuer").value("工业和信息化部"))
                .andExpect(jsonPath("$.data.certificates[0].date").value("2025-05"))
                .andExpect(jsonPath("$.data.certificates[1].issuer").value("Project Management Institute"))
                .andExpect(jsonPath("$.data.certificates[1].date").value("2024-08"))
                .andReturn());
        assertThat(details.path("certificates").findValuesAsText("name"))
                .containsExactly("软件设计师", "PMP");
        details.path("certificates").findValues("description").forEach(description -> {
            long count = description.asText().codePoints().filter(Character::isLetterOrDigit).count();
            assertThat(count).isBetween(80L, 160L);
        });

        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        assertThat(card(refreshed, "CERTIFICATES").path("payload").toString())
                .doesNotContain("软件设计师", "PMP");
        verify(gateway, times(1)).execute(any(), any());
    }

    @Test
    void certificateNamesRejectBlankPlaceholdersWithoutCallingTheModelOrUsingQuota() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"空证书验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "CERTIFICATES").path("id").asText();
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/certificate-suggestions",
                                conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON).content("""
                                {"clientRequestId":"certificate-empty","phase":"NAMES",
                                 "currentCertificates":[{"name":"","issuer":"","date":"","description":""}],
                                 "selectedNames":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_CERTIFICATE_FACTS_INSUFFICIENT"));

        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
        verify(gateway, times(0)).execute(any(), any());
    }

    @Test
    void certificateDetailsRejectFieldsBorrowedFromAnotherCertificate() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        Response invalid = new Response("certificate-cross-source", "channel-1", "qwen-plus", """
                {"certificates":[
                  {"name":"软件设计师","issuer":"Project Management Institute","date":"2024-08",
                   "description":"• 计算机技术与软件专业技术资格中级资格，资料状态为已取得。",
                   "sourceFields":["draft.0"]}
                ]}
                """, new Usage(40, 30, 70), null);
        when(gateway.execute(any(), any())).thenReturn(invalid, invalid);
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"证书串用验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "CERTIFICATES").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/certificate-suggestions",
                                conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON).content("""
                                {"clientRequestId":"certificate-cross-source","phase":"DETAILS",
                                 "currentCertificates":[
                                   {"name":"软件设计师","issuer":"工业和信息化部","date":"2025-05",
                                    "description":"计算机技术与软件专业技术资格中级资格，资料状态为已取得。"},
                                   {"name":"PMP","issuer":"Project Management Institute","date":"2024-08",
                                    "description":"项目管理专业人士资格，资料状态为已取得。"}
                                 ],"selectedNames":["软件设计师"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_CERTIFICATE_UNSUPPORTED_FIELD"));

        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void certificateDetailsRejectUnsupportedNonNumericDescriptions() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        Response invalid = new Response("certificate-invented-description", "channel-1", "qwen-plus", """
                {"certificates":[
                  {"name":"软件设计师","issuer":"工业和信息化部","date":"2025-05",
                   "description":"• 具备大型分布式系统架构设计、容量规划、性能治理与复杂故障分析能力，能够独立承担关键技术方案决策。\\n• 具有跨团队管理、项目统筹、资源协调和业务增长经验，能够持续推动大型平台建设并形成显著经营成果。",
                   "sourceFields":["draft.0"]}
                ]}
                """, new Usage(40, 30, 70), null);
        when(gateway.execute(any(), any())).thenReturn(invalid, invalid);
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"证书描述门禁验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "CERTIFICATES").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/certificate-suggestions",
                                conversationId, cardId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON).content("""
                                {"clientRequestId":"certificate-invented-description","phase":"DETAILS",
                                 "currentCertificates":[
                                   {"name":"软件设计师","issuer":"工业和信息化部","date":"2025-05",
                                    "description":"计算机技术与软件专业技术资格中级资格，资料状态为已取得。"}
                                 ],"selectedNames":["软件设计师"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_CERTIFICATE_UNSUPPORTED_FIELD"));

        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void certificateRecommendationsReturnMultipleCatalogOptionsAndExcludeContactDetails() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("credential-recommendations", "channel-1",
                "qwen-plus", """
                        {"recommendations":[
                          {"catalogId":"cert-software-designer","reason":"与 Java 后端目标岗位和软件开发方向相关","sourceFields":["resume.intentions.targetJob"]},
                          {"catalogId":"cert-system-architect","reason":"与后端工程师后续架构能力发展方向相关","sourceFields":["resume.intentions.targetJob"]},
                          {"catalogId":"cert-cka","reason":"与后端服务部署和云原生技术方向相关","sourceFields":["resume.intentions.targetJob"]},
                          {"catalogId":"cert-rhce","reason":"与后端运行环境和 Linux 技术方向相关","sourceFields":["resume.intentions.targetJob"]},
                          {"catalogId":"cert-aws-saa","reason":"与后端系统云计算和架构方向相关","sourceFields":["resume.intentions.targetJob"]},
                          {"catalogId":"cert-cet6","reason":"与技术资料阅读和通用英语能力方向相关","sourceFields":["resume.intentions.targetJob"]}
                        ]}
                        """, new Usage(120, 80, 200), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"证书多选推荐验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode javaJob = taxonomyJob("JAVA后端");
        JsonNode targetCard = card(created, "TARGET_JOB");
        String targetPayload = mapper.writeValueAsString(Map.of(
                "targetJob", javaJob.path("displayName").asText(),
                "taxonomyNodeId", javaJob.path("id").asText(),
                "taxonomyGroupId", javaJob.path("groupId").asText(),
                "taxonomyCategoryId", javaJob.path("categoryId").asText()));
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/submit",
                        conversationId, targetCard.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":%s,\"expectedVersion\":0}".formatted(targetPayload)))
                .andExpect(status().isOk());
        JsonNode contactCard = card(created, "CONTACT");
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/submit",
                        conversationId, contactCard.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"payload":{"name":"隐私测试姓名","email":"private@example.com",
                                  "phone":"13800138000","location":"隐私城市","links":["https://private.invalid"]},
                                 "expectedVersion":0}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        String cardId = card(created, "CERTIFICATES").path("id").asText();
        JsonNode response = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/credential-recommendations",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"certificate-multiple-recommendations\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CERTIFICATE"))
                .andExpect(jsonPath("$.data.candidates.length()").value(6))
                .andExpect(jsonPath("$.data.candidates[0].candidateType").value("RECOMMENDED"))
                .andExpect(jsonPath("$.data.candidates[0].sourceRefs[0].key")
                        .value("resume.intentions.targetJob"))
                .andReturn());
        assertThat(response.path("candidates").findValuesAsText("name"))
                .containsExactly("软件设计师资格", "系统架构设计师资格",
                        "Certified Kubernetes Administrator（CKA）", "Red Hat Certified Engineer（RHCE）",
                        "AWS Certified Solutions Architect - Associate", "大学英语六级（CET-6）");

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(gateway, times(1)).execute(any(), request.capture());
        String prompt = request.getValue().messages().get(1).content();
        assertThat(prompt).contains(javaJob.path("displayName").asText(), "resume.intentions.targetJob")
                .doesNotContain("隐私测试姓名", "private@example.com", "13800138000", "隐私城市",
                        "https://private.invalid", "basics");
    }

    @Test
    void blankCertificateCardCanBuildCompleteDetailsAfterUserConfirmsARecommendation() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(
                new Response("credential-recommendations", "channel-1", "qwen-plus", """
                        {"recommendations":[
                          {"catalogId":"cert-ncre2","reason":"与计算机专业和软件开发目标方向相关","sourceFields":["resume.intentions.targetJob"]},
                          {"catalogId":"cert-software-designer","reason":"与软件开发和后端技术方向相关","sourceFields":["resume.intentions.targetJob"]},
                          {"catalogId":"cert-system-architect","reason":"与后端架构发展方向相关","sourceFields":["resume.intentions.targetJob"]},
                          {"catalogId":"cert-cka","reason":"与后端部署和云原生方向相关","sourceFields":["resume.intentions.targetJob"]},
                          {"catalogId":"cert-rhce","reason":"与后端运行环境和 Linux 方向相关","sourceFields":["resume.intentions.targetJob"]}
                        ]}
                        """, new Usage(90, 70, 160), null),
                new Response("certificate-details", "channel-1", "qwen-plus", """
                        {"certificates":[{
                          "name":"全国计算机等级考试二级","issuer":"教育部教育考试院","date":"",
                          "description":"• 用户已确认取得全国计算机等级考试二级，证书由教育部教育考试院组织，考核计算机基础知识、程序设计与办公软件应用等方向。\\n• 当前没有提供取得日期、考试成绩、具体等级或证书编号，相关字段保持空白并等待用户核对补充。",
                          "sourceFields":["confirmedRecommendation.0"]
                        }]}
                        """, new Usage(70, 80, 150), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"空证书 AI 完善验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode targetCard = card(created, "TARGET_JOB");
        JsonNode javaJob = taxonomyJob("JAVA后端");
        String targetPayload = mapper.writeValueAsString(Map.of(
                "targetJob", javaJob.path("displayName").asText(),
                "taxonomyNodeId", javaJob.path("id").asText(),
                "taxonomyGroupId", javaJob.path("groupId").asText(),
                "taxonomyCategoryId", javaJob.path("categoryId").asText()));
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/submit",
                        conversationId, targetCard.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":%s,\"expectedVersion\":0}".formatted(targetPayload)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        String cardId = card(created, "CERTIFICATES").path("id").asText();
        mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/credential-recommendations",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"blank-certificate-recommendations\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates[0].name").value("全国计算机等级考试二级"));

        JsonNode details = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/certificate-suggestions",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"blank-certificate-details","phase":"DETAILS",
                                 "currentCertificates":[{"name":"","issuer":"","date":"","description":""}],
                                 "selectedNames":["全国计算机等级考试二级"],
                                 "confirmedNames":["全国计算机等级考试二级"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificates[0].name").value("全国计算机等级考试二级"))
                .andExpect(jsonPath("$.data.certificates[0].issuer").value("教育部教育考试院"))
                .andExpect(jsonPath("$.data.certificates[0].date").value(""))
                .andReturn());
        int descriptionCharacters = details.path("certificates").get(0).path("description").asText()
                .codePoints().filter(Character::isLetterOrDigit).toArray().length;
        assertThat(descriptionCharacters).isBetween(80, 160);
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void honorRecommendationsReturnFiveToEightOptionsAndRemainUnconfirmed() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("honor-recommendations", "channel-1",
                "qwen-plus", """
                        {"recommendations":[
                          {"catalogId":"honor-national-scholarship","reason":"与学生阶段学业和综合表现方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-academic-scholarship","reason":"与在校学习经历和专业表现方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-outstanding-graduate","reason":"与应届毕业和在校综合表现方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-lanqiao","reason":"与软件工程专业和程序设计方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-acm","reason":"与软件工程专业和算法竞赛方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-innovation-competition","reason":"与软件工程专业和创新项目方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-student-cadre","reason":"与学生阶段组织和综合发展方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-service","reason":"与学生阶段社会实践和志愿服务方向相关","sourceFields":["resume.education.0"]}
                        ]}
                        """, new Usage(110, 90, 200), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"荣誉多选推荐验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode educationCard = card(created, "EDUCATION");
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/submit",
                        conversationId, educationCard.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"payload":{"items":[{"school":"示例大学","major":"软件工程",
                                  "degree":"本科","description":"学习软件工程专业课程并完成课程项目"}]},
                                 "expectedVersion":0}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        String cardId = card(created, "HONORS").path("id").asText();
        JsonNode response = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/credential-recommendations",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"honor-multiple-recommendations\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("HONOR"))
                .andExpect(jsonPath("$.data.candidates.length()").value(8))
                .andExpect(jsonPath("$.data.candidates[7].candidateType").value("RECOMMENDED"))
                .andReturn());
        assertThat(response.path("candidates").findValuesAsText("name"))
                .contains("国家奖学金", "校级学业奖学金", "优秀毕业生", "优秀志愿者");
        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        assertThat(card(refreshed, "HONORS").path("payload").path("items").toString())
                .doesNotContain("国家奖学金", "优秀毕业生", "优秀志愿者");
    }

    @Test
    void blankHonorCardCanBuildCompleteDetailsAfterUserConfirmsARecommendation() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        String honorName = "“挑战杯”全国大学生系列科技学术竞赛奖项";
        String honorIssuer = "共青团中央、中国科协、教育部、中国社会科学院、全国学联";
        when(gateway.execute(any(), any())).thenReturn(
                new Response("honor-recommendations", "channel-1", "qwen-plus", """
                        {"recommendations":[
                          {"catalogId":"honor-challenge-cup","reason":"与软件工程学习和科技创新项目方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-lanqiao","reason":"与软件工程和程序设计方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-acm","reason":"与软件工程和算法竞赛方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-innovation-competition","reason":"与学生创新项目方向相关","sourceFields":["resume.education.0"]},
                          {"catalogId":"honor-mcm","reason":"与计算机专业的数据建模方向相关","sourceFields":["resume.education.0"]}
                        ]}
                        """, new Usage(90, 70, 160), null),
                new Response("honor-details", "channel-1", "qwen-plus", """
                        {"honors":[{
                          "name":"%s","issuer":"%s","date":"",
                          "description":"• 用户确认获得“挑战杯”全国大学生系列科技学术竞赛奖项；目录信息显示该荣誉由共青团中央、中国科协、教育部、中国社会科学院、全国学联共同组织。\\n• 当前未提供具体届次、赛道、奖项级别、团队角色、取得日期和证明编号，相关字段保持空白，等待用户核对补充。",
                          "sourceFields":["confirmedRecommendation.0"]
                        }]}
                        """.formatted(honorName, honorIssuer), new Usage(70, 80, 150), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"空荣誉 AI 完善验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode educationCard = card(created, "EDUCATION");
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/submit",
                        conversationId, educationCard.path("id").asText()).cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"payload":{"items":[{"school":"示例大学","major":"软件工程",
                                  "degree":"本科","description":"学习软件工程专业课程并完成科技创新项目"}]},
                                 "expectedVersion":0}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        String cardId = card(created, "HONORS").path("id").asText();
        mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/credential-recommendations",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"blank-honor-recommendations\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates[0].name").value(honorName));

        JsonNode details = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/honor-suggestions",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "clientRequestId", "blank-honor-details",
                                "phase", "DETAILS",
                                "currentHonors", List.of(Map.of(
                                        "name", "", "issuer", "", "date", "", "description", "")),
                                "selectedNames", List.of(honorName),
                                "confirmedNames", List.of(honorName)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.honors[0].name").value(honorName))
                .andExpect(jsonPath("$.data.honors[0].issuer").value(honorIssuer))
                .andExpect(jsonPath("$.data.honors[0].date").value(""))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-honors-v2"))
                .andReturn());
        int descriptionCharacters = details.path("honors").get(0).path("description").asText()
                .codePoints().filter(Character::isLetterOrDigit).toArray().length;
        assertThat(descriptionCharacters).isBetween(80, 160);
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void credentialRecommendationRequiresConfirmedResumeContextWithoutCallingTheModel() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"空推荐上下文\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "CERTIFICATES").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/credential-recommendations",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"empty-credential-recommendations\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason")
                        .value("AI_CREDENTIAL_RECOMMENDATION_CONTEXT_REQUIRED"));
        verify(gateway, times(0)).execute(any(), any());
    }

    @Test
    void certificateAndHonorCardsRequireSixtyMeaningfulDescriptionCharacters() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"资质字数门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode certificateWithoutIssuer = card(created, "CERTIFICATES");
        String longDescription = "已确认该记录真实存在，补充说明围绕取得过程、评审范围、个人参与内容与可核实结果展开，所有内容均需要由用户核对，不增加未经确认的成绩、编号或日期信息。";
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/submit",
                        conversationId, certificateWithoutIssuer.path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload":{"items":[{"name":"测试证书","issuer":"",
                                  "date":"","description":%s}]},"expectedVersion":0}
                                """.formatted(mapper.writeValueAsString(longDescription))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_CREDENTIAL_ISSUER_REQUIRED"));
        for (String type : List.of("CERTIFICATES", "HONORS")) {
            JsonNode target = card(created, type);
            mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/submit",
                            conversationId, target.path("id").asText())
                            .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"payload":{"items":[{"name":"测试名称","issuer":"测试机构",
                                      "date":"2025-06","description":"内容过短，无法充分说明事实。"}]},
                                     "expectedVersion":0}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.reason").value("AI_CREDENTIAL_DESCRIPTION_TOO_SHORT"));
        }

        String valid = "已确认该记录真实存在，补充说明围绕取得过程、评审范围、个人参与内容与可核实结果展开，所有机构、日期和成果均来自用户确认事实，不增加未经确认的信息。";
        assertThat(valid.codePoints().filter(Character::isLetterOrDigit).count()).isGreaterThanOrEqualTo(60);
        JsonNode certificate = card(created, "CERTIFICATES");
        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/submit",
                        conversationId, certificate.path("id").asText())
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payload":{"items":[{"name":"测试证书","issuer":"测试机构",
                                  "date":"2025-06","description":%s}]},"expectedVersion":0}
                                """.formatted(mapper.writeValueAsString(valid))))
                .andExpect(status().isOk());
    }

    @Test
    void honorSuggestionUsesLibraryOnlyWhenEnabledAndDoesNotMutateTheCard() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"荣誉提取验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "HONORS").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        JsonNode libraryHonor = data(mockMvc.perform(post("/api/v1/career-library/records").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type":"HONOR","title":"全国大学生软件创新一等奖","organization":"全国软件创新组委会",
                                 "startDate":"2025-08","description":"参加全国大学生软件创新竞赛，负责服务端方案设计、接口实现与系统联调，参赛作品获得一等奖",
                                 "coreOutcome":"参赛作品完成现场评审并获得一等奖，个人负责内容和获奖结果均已确认"}
                                """))
                .andExpect(status().isOk()).andReturn());
        String currentHonors = """
                [{"name":"国家奖学金","issuer":"教育部","date":"2024-10",
                  "description":"国家奖学金记录已经确认获得，评选依据覆盖课程学习表现、专业成绩与在校综合表现",
                  "coreOutcome":"荣誉状态、授予机构和取得时间均已由用户确认"}]
                """;
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        JsonNode localOnly = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/honor-suggestions",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"honor-names-local","phase":"NAMES",
                                 "currentHonors":%s,"selectedNames":[]}
                                """.formatted(currentHonors)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(1))
                .andExpect(jsonPath("$.data.careerLibraryEvidenceEnabled").value(false))
                .andExpect(jsonPath("$.data.inputTokens").value(0))
                .andExpect(jsonPath("$.data.outputTokens").value(0))
                .andReturn());
        assertThat(localOnly.path("candidates").findValuesAsText("name")).containsExactly("国家奖学金");

        mockMvc.perform(put("/api/v1/ai-resume/conversations/{id}/preferences/career-library-evidence",
                        conversationId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        JsonNode names = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/honor-suggestions",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"honor-names-library","phase":"NAMES",
                                 "currentHonors":%s,"selectedNames":[]}
                                """.formatted(currentHonors)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(2))
                .andExpect(jsonPath("$.data.model").value("system-deterministic"))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-honors-v2"))
                .andReturn());
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
        verify(gateway, times(0)).execute(any(), any());

        String careerKey = "career." + libraryHonor.path("id").asText();
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("honor-details", "channel-1", "qwen-plus", """
                {"honors":[
                  {"name":"国家奖学金","issuer":"教育部","date":"2024-10",
                   "description":"• 国家奖学金记录确认该荣誉已经获得，现有资料说明评选依据覆盖课程学习表现、专业成绩与在校综合表现，并保留授予机构及取得时间。\\n• 相关说明仅整理已确认的评选依据、学习表现和荣誉状态，用于呈现可核实的在校成果，不增加名次、比例或金额信息。",
                   "sourceFields":["draft.0"]},
                  {"name":"全国大学生软件创新一等奖","issuer":"全国软件创新组委会","date":"2025-08",
                   "description":"• 参加全国大学生软件创新竞赛，负责服务端方案设计、接口实现与系统联调，参赛作品完成现场评审并获得一等奖。\\n• 获奖记录确认个人负责内容涵盖服务端方案、接口和联调工作，作品获一等奖的结果已经确认，未增加名次比例或参赛人数。",
                   "sourceFields":["%s"]}
                ]}
                """.formatted(careerKey), new Usage(80, 110, 190), null));

        JsonNode details = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/honor-suggestions",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"honor-details","phase":"DETAILS",
                                 "currentHonors":%s,
                                 "selectedNames":["国家奖学金","全国大学生软件创新一等奖"]}
                                """.formatted(currentHonors)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.honors.length()").value(2))
                .andReturn());
        details.path("honors").findValues("description").forEach(description -> {
            long count = description.asText().codePoints().filter(Character::isLetterOrDigit).count();
            assertThat(count).isBetween(80L, 160L);
        });
        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        assertThat(card(refreshed, "HONORS").path("payload").toString())
                .doesNotContain("国家奖学金", "全国大学生软件创新一等奖");
        verify(gateway, times(1)).execute(any(), any());
    }

    @Test
    void honorDetailsRejectFactsBorrowedFromAnotherHonor() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        Response invalid = new Response("honor-cross-source", "channel-1", "qwen-plus", """
                {"honors":[{"name":"优秀学生干部","issuer":"竞赛组委会","date":"2025-08",
                  "description":"• 优秀学生干部记录确认该荣誉已经获得，现有资料说明评选依据覆盖学生工作组织、活动协调和服务同学等表现。\\n• 相关说明整理已确认的学生工作内容和荣誉状态，用于呈现可核实的在校贡献，不增加名次、比例、金额或人数信息。",
                  "sourceFields":["draft.1"]}]}
                """, new Usage(40, 70, 110), null);
        when(gateway.execute(any(), any())).thenReturn(invalid, invalid);
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"荣誉串用门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "HONORS").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie())).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/honor-suggestions",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"honor-cross-source","phase":"DETAILS","currentHonors":[
                                  {"name":"优秀学生干部","issuer":"示例大学","date":"2024-10",
                                   "description":"优秀学生干部记录确认已经获得，评选依据覆盖学生工作组织、活动协调与服务同学表现"},
                                  {"name":"创新竞赛一等奖","issuer":"竞赛组委会","date":"2025-08",
                                   "description":"参加创新竞赛并完成作品评审，获奖结果与授予机构已经确认"}],
                                 "selectedNames":["优秀学生干部"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_HONOR_UNSUPPORTED_FIELD"));
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void languageCardRequiresASelectionAndCanBeExplicitlySkipped() throws Exception {
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"语言跳过验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        JsonNode languageCard = card(created, "LANGUAGES");
        String cardId = languageCard.path("id").asText();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/submit",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"items\":[]},\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_LANGUAGE_REQUIRED"));

        JsonNode skipped = data(mockMvc.perform(post(
                                "/api/v1/ai-resume/conversations/{conversation}/cards/{card}/skip",
                                conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(card(skipped, "LANGUAGES").path("status").asText()).isEqualTo("SKIPPED");
        assertThat(card(skipped, "LANGUAGES").path("payload").path("items").isEmpty()).isTrue();

        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        assertThat(card(refreshed, "LANGUAGES").path("status").asText()).isEqualTo("SKIPPED");
    }

    @Test
    void summarySuggestionsReturnThreeGroundedDraftOptionsWithoutMutatingTheCard() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("summary-options", "channel-1", "qwen-plus",
                """
                        {"candidates":[
                          {"style":"专业简洁","text":"毕业于示例科技大学，面向Java后端开发岗位，具备软件工程专业学习背景，已形成围绕Java、Spring Boot与MySQL的技术知识结构。能够结合课程实践梳理需求、拆分任务并完成基础功能实现，重视代码可读性、数据处理逻辑与团队协作，持续提升从问题分析到交付验证的完整工程能力。","reason":"综合已确认的目标岗位、专业背景和技能事实，突出技术基础与工程流程。","sourceFields":["card.TARGET_JOB","card.EDUCATION.1","card.SKILLS.1"]},
                          {"style":"成果导向","text":"以Java后端开发为求职方向，将软件工程课程基础与Java、Spring Boot、MySQL技能组合用于项目实践。能够围绕需求理解推进功能实现和数据处理，关注问题定位、联调验证与交付质量，并通过持续复盘完善技术方案和协作方式，形成稳定的任务推进与工程实践能力。","reason":"基于已确认事实强调任务推进、验证与交付过程，没有添加量化成果。","sourceFields":["card.TARGET_JOB","card.EDUCATION.1","card.SKILLS.1"]},
                          {"style":"稳健正式","text":"具备软件工程专业背景，求职方向聚焦Java后端开发，掌握Java、Spring Boot与MySQL相关基础。重视需求理解、功能拆分、代码实现和验证反馈之间的衔接，能够在课程与项目实践中保持清晰的问题分析思路、规范的执行过程和可靠的团队协作习惯，持续积累后端工程能力。","reason":"依据已确认教育和技能内容形成稳健表述，保持事实边界。","sourceFields":["card.TARGET_JOB","card.EDUCATION.1","card.SKILLS.1"]}
                        ]}
                        """, new Usage(120, 240, 360), null));
        Session owner = seeker();
        JsonNode current = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"GRADUATE\",\"title\":\"个人简介帮写验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = current.path("id").asText();
        JsonNode target = taxonomyJob("JAVA后端");
        current = submitCard(owner, conversationId, current, "TARGET_JOB", mapper.writeValueAsString(Map.of(
                "targetJob", target.path("displayName").asText(),
                "taxonomyNodeId", target.path("id").asText(),
                "taxonomyGroupId", target.path("groupId").asText(),
                "taxonomyCategoryId", target.path("categoryId").asText())));
        current = submitCard(owner, conversationId, current, "EDUCATION", """
                {"items":[{"school":"示例科技大学","major":"软件工程","degree":"本科",
                "description":"完成后端课程实践，参与需求梳理、功能实现和联调验证"}]}
                """);
        current = submitCard(owner, conversationId, current, "SKILLS", """
                {"items":[{"category":"后端开发","items":["Java","Spring Boot","MySQL"],
                "description":"围绕后端功能实现、数据处理和测试验证开展课程与项目实践"}]}
                """);
        current = submitCard(owner, conversationId, current, "CONTACT", """
                {"name":"候选人甲","email":"private-summary@example.com","phone":"13800001234","location":"杭州"}
                """);
        String summaryCardId = card(current, "SUMMARY").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie())).andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/summary-suggestions",
                        conversationId, summaryCardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"summary-options\",\"currentSummary\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.length()").value(3))
                .andExpect(jsonPath("$.data.candidates[0].style").value("专业简洁"))
                .andExpect(jsonPath("$.data.candidates[1].style").value("成果导向"))
                .andExpect(jsonPath("$.data.candidates[2].style").value("稳健正式"))
                .andExpect(jsonPath("$.data.candidates[0].sourceRefs.length()").value(3))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-summary-v1"))
                .andExpect(jsonPath("$.data.remainingQuota").value(beforeQuota - 1));

        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        assertThat(card(refreshed, "SUMMARY").path("payload").path("text").asText()).isBlank();
        assertThat(refreshed.path("content").path("summary").asText()).isBlank();
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(gateway).execute(any(), request.capture());
        String prompt = request.getValue().messages().get(1).content();
        assertThat(prompt).doesNotContain("private-summary@example.com", "13800001234", "候选人甲");
    }

    @Test
    void summarySuggestionsRejectUnsupportedNumbersAndRefundQuota() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        String unsafe = "围绕专业课程持续整理知识结构与实践方法，能够结合课堂任务完成资料分析、方案梳理和成果表达。通过主动推进学习任务、复盘执行过程并优化协作方式，使整体效率提升30%，同时保持清晰的问题拆解思路、规范的交付习惯和稳定的团队沟通能力。";
        String response = """
                {"candidates":[
                  {"style":"专业简洁","text":"%s","reason":"整理教育事实并突出实践过程。","sourceFields":["card.EDUCATION.1"]},
                  {"style":"成果导向","text":"%s","reason":"整理教育事实并突出实践过程。","sourceFields":["card.EDUCATION.1"]},
                  {"style":"稳健正式","text":"%s","reason":"整理教育事实并突出实践过程。","sourceFields":["card.EDUCATION.1"]}
                ]}
                """.formatted(unsafe, unsafe, unsafe);
        when(gateway.execute(any(), any())).thenReturn(
                new Response("summary-unsafe", "channel-1", "qwen-plus", response, new Usage(50, 80, 130), null),
                new Response("summary-unsafe-repair", "channel-1", "qwen-plus", response, new Usage(50, 80, 130), null));
        Session owner = seeker();
        JsonNode current = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"简介数字事实门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = current.path("id").asText();
        current = submitCard(owner, conversationId, current, "EDUCATION", """
                {"items":[{"school":"示例科技大学","major":"软件工程","degree":"本科",
                "description":"完成课程任务、资料分析、方案梳理和成果表达"}]}
                """);
        String summaryCardId = card(current, "SUMMARY").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie())).andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/summary-suggestions",
                        conversationId, summaryCardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"summary-unsafe\",\"currentSummary\":\"\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_SUMMARY_UNSUPPORTED_FACT"));
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void summarySuggestionsCannotUseNamedFactsFromUncitedRecords() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        String uncited = "具备示例财经大学形成的课程学习基础，能够围绕资料整理、任务分析和方案表达推进实践。重视需求理解、执行过程与成果验证之间的衔接，保持清晰的问题拆解方式、规范的交付习惯和稳定的协作意识，并持续完善专业知识结构与实践方法。";
        String response = """
                {"candidates":[
                  {"style":"专业简洁","text":"%s","reason":"依据第一段教育记录组织专业简介。","sourceFields":["card.EDUCATION.1"]},
                  {"style":"成果导向","text":"%s","reason":"依据第一段教育记录组织成果简介。","sourceFields":["card.EDUCATION.1"]},
                  {"style":"稳健正式","text":"%s","reason":"依据第一段教育记录组织稳健简介。","sourceFields":["card.EDUCATION.1"]}
                ]}
                """.formatted(uncited, uncited, uncited);
        when(gateway.execute(any(), any())).thenReturn(
                new Response("summary-cross-source", "channel-1", "qwen-plus", response,
                        new Usage(50, 80, 130), null),
                new Response("summary-cross-source-repair", "channel-1", "qwen-plus", response,
                        new Usage(50, 80, 130), null));
        Session owner = seeker();
        JsonNode current = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"简介来源隔离门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = current.path("id").asText();
        current = submitCard(owner, conversationId, current, "EDUCATION", """
                {"items":[
                  {"school":"示例科技大学","major":"软件工程","degree":"本科","description":"完成课程任务和方案表达"},
                  {"school":"示例财经大学","major":"金融学","degree":"本科","description":"完成数据分析课程实践"}
                ]}
                """);
        String summaryCardId = card(current, "SUMMARY").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie())).andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/summary-suggestions",
                        conversationId, summaryCardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"summary-cross-source\",\"currentSummary\":\"\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_SUMMARY_UNSUPPORTED_FACT"));
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void summarySuggestionsRequireConfirmedSubstantiveFactsBeforeCallingTheModel() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"简介事实不足门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String summaryCardId = card(created, "SUMMARY").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie())).andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/summary-suggestions",
                        conversationId, summaryCardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"summary-empty\",\"currentSummary\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_SUMMARY_FACTS_INSUFFICIENT"));
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
        verify(gateway, times(0)).execute(any(), any());
    }

    @Test
    void languageDescriptionSuggestionUsesSelectedFactsAndRemainsUnconfirmed() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("language-description", "channel-1",
                "qwen-plus", """
                        {"suggestion":"• 能够阅读与目标岗位相关的常见资料，提取关键信息并整理为可执行要点，协助完成资料检索、信息核对和任务准备。\\n• 能够在日常协作中处理常见信息往来，根据沟通对象整理表达重点，并保持书面内容清晰、准确且便于后续跟进。\\n• 可在实际任务中支持文档查阅、需求理解和跨团队信息同步，并根据工作场景持续积累专业词汇与表达方式。","reason":"根据所选语言、水平和成绩证明生成三条使用场景参考，具体能力边界需要用户逐项确认。","sourceFields":["language","level","score"],"verificationRequired":true,"verificationItems":["确认能够阅读资料、提取信息并整理任务要点","确认能够处理日常信息往来并完成清晰书面表达","确认能够支持文档查阅、需求理解和跨团队信息同步"]}
                        """, new Usage(48, 86, 134), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"语言帮写验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "LANGUAGES").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"language-description","recordFacts":{"language":"英语","level":"熟练","score":"CET-6 520"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationRequired").value(true))
                .andExpect(jsonPath("$.data.verificationItems.length()").value(3))
                .andExpect(jsonPath("$.data.sourceFields.length()").value(3))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-description-v8"));

        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie())).andExpect(status().isOk()).andReturn());
        assertThat(card(refreshed, "LANGUAGES").path("payload").toString())
                .doesNotContain("资料检索", "跨团队信息同步");
        verify(gateway, times(1)).execute(any(), any());
    }

    @Test
    void descriptionSuggestionUsesCurrentRecordFactsWithoutMutatingTheCard() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("description-1", "channel-1", "qwen-plus",
                """
                        {"suggestion":"• 系统学习数据结构、数据库原理、机器学习与数据挖掘等专业常见核心课程，持续构建数学、统计与数据处理知识基础。\\n• 围绕数据采集、清洗、分析与可视化开展课程实践，熟悉从原始数据整理到分析结果表达的完整工作流程。\\n• 面向数据开发相关岗位持续提升专业能力，注重将算法模型应用于实际问题，并培养逻辑思维、沟通表达与团队协作能力。","reason":"根据专业方向生成三条教育经历参考，课程、实践和岗位能力需要用户逐项确认。","sourceFields":["major"],"verificationRequired":true,"verificationItems":["确认已学习数据结构、数据库原理、机器学习与数据挖掘课程","确认参与过数据采集、清洗、分析与可视化课程实践","确认求职方向和逻辑思维、沟通表达、团队协作能力符合实际"]}
                        """,
                new Usage(42, 24, 66), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"描述帮写验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EDUCATION").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-success","recordFacts":{"school":"示例科技大学","major":"数据科学与大数据技术","degree":"本科","startDate":"2021-09","endDate":"2025-06","location":"杭州"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestion").value("• 系统学习数据结构、数据库原理、机器学习与数据挖掘等专业常见核心课程，持续构建数学、统计与数据处理知识基础。\n• 围绕数据采集、清洗、分析与可视化开展课程实践，熟悉从原始数据整理到分析结果表达的完整工作流程。\n• 面向数据开发相关岗位持续提升专业能力，注重将算法模型应用于实际问题，并培养逻辑思维、沟通表达与团队协作能力。"))
                .andExpect(jsonPath("$.data.sourceFields.length()").value(1))
                .andExpect(jsonPath("$.data.verificationRequired").value(true))
                .andExpect(jsonPath("$.data.verificationItems.length()").value(3))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-description-v8"))
                .andExpect(jsonPath("$.data.remainingQuota").value(beforeQuota - 1));

        JsonNode refreshed = data(mockMvc.perform(get("/api/v1/ai-resume/conversations/{id}", conversationId)
                        .cookie(owner.cookie()))
                .andExpect(status().isOk()).andReturn());
        assertThat(refreshed.toString()).doesNotContain("机器学习");
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota - 1);
    }

    @Test
    void descriptionSuggestionRepairsFragmentedVerificationItemsAndChargesOneQuotaUnit() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        String suggestion = "• 系统学习数据结构、数据库原理、机器学习与数据挖掘等专业常见核心课程，持续构建数学、统计与数据处理知识基础。\\n"
                + "• 围绕数据采集、清洗、分析与可视化开展课程实践，熟悉从原始数据整理到分析结果表达的完整工作流程。\\n"
                + "• 面向数据开发相关岗位持续提升专业能力，注重将算法模型应用于实际问题，并培养逻辑思维、沟通表达与团队协作能力。";
        when(gateway.execute(any(), any()))
                .thenReturn(new Response("description-fragmented", "channel-1", "qwen-plus",
                        """
                                {"suggestion":"%s","reason":"按专业常识补充。","sourceFields":["major"],"verificationRequired":true,"verificationItems":["确认数据结构课程","确认数据库原理课程","确认机器学习课程","确认数据挖掘课程","确认数学基础","确认统计基础","确认数据采集实践","确认数据清洗实践","确认数据分析实践","确认数据可视化实践","确认逻辑思维能力","确认沟通表达能力","确认团队协作能力"]}
                                """.formatted(suggestion),
                        new Usage(40, 80, 120), null))
                .thenReturn(new Response("description-repaired", "channel-1", "qwen-plus",
                        """
                                {"suggestion":"%s","reason":"根据专业方向生成三条参考，课程工具、实践流程和能力方向均需用户确认。","sourceFields":["major"],"verificationRequired":true,"verificationItems":["确认已学习数据结构、数据库原理、机器学习、数据挖掘等课程并具备数学和统计基础","确认参与过数据采集、清洗、分析与可视化课程实践并了解完整流程","确认数据开发方向、算法模型应用、逻辑思维、沟通表达和团队协作能力符合实际"]}
                                """.formatted(suggestion),
                        new Usage(55, 70, 125), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"教育确认项修复验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EDUCATION").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-repair","recordFacts":{"school":"示例科技大学","major":"数据科学与大数据技术","degree":"本科"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationItems.length()").value(3))
                .andExpect(jsonPath("$.data.inputTokens").value(95))
                .andExpect(jsonPath("$.data.outputTokens").value(150))
                .andExpect(jsonPath("$.data.remainingQuota").value(beforeQuota - 1));
        verify(gateway, times(2)).execute(any(), any());
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota - 1);
    }

    @Test
    void descriptionSuggestionPromptSeparatesMajorContentFromTargetJobAlignment() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("description-prompt", "channel-1", "qwen-plus",
                """
                        {"suggestion":"• 系统学习数据结构、数据库原理、机器学习与数据挖掘等专业常见核心课程，持续构建数学、统计与数据处理知识基础。\\n• 围绕数据采集、清洗、分析与可视化开展课程实践，熟悉从原始数据整理到分析结果表达的完整工作流程。\\n• 面向数据开发相关岗位持续提升专业能力，注重将算法模型应用于实际问题，并培养逻辑思维、沟通表达与团队协作能力。","reason":"根据专业方向生成三条参考，待用户核实。","sourceFields":["major"],"verificationRequired":true,"verificationItems":["确认已学习数据结构、数据库原理、机器学习、数据挖掘等课程并具备数学和统计基础","确认参与过数据采集、清洗、分析与可视化课程实践并了解完整流程","确认数据开发方向、算法模型应用、逻辑思维、沟通表达和团队协作能力符合实际"]}
                        """,
                new Usage(42, 70, 112), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"教育提示词边界验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EDUCATION").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-prompt","recordFacts":{"school":"示例科技大学","major":"数据科学与大数据技术","degree":"本科"}}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(gateway).execute(any(), request.capture());
        String systemPrompt = request.getValue().messages().get(0).content();
        assertThat(systemPrompt)
                .contains("第一、二条只由 major 和 description 决定")
                .contains("targetJob 只能影响第三条")
                .contains("严禁“建议、可重点呈现、可梳理、可突出、可以写”等指导用户如何写的措辞")
                .contains("verificationItems 必须对应三条正文各提供一项")
                .contains("可以根据岗位、项目名称、组织角色、专业和目标岗位生成合理的常见职责");
    }

    @Test
    void descriptionSuggestionRepairsAdvisoryWordingBeforeReturningCandidate() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any()))
                .thenReturn(new Response("description-advisory", "channel-1", "qwen-plus",
                        """
                                {"suggestion":"• 课程与工具方面，可重点呈现数据结构、数据库原理、概率论与数理统计等专业课程，以及运用 Python、SQL 进行数据处理与分析的学习内容。\\n• 课程项目与实践方面，可梳理数据采集、数据清洗、探索性分析、数据建模与结果可视化等环节，呈现数据分析实践的基本流程与方法。\\n• 面向数据开发相关岗位，可突出专业学习所培养的逻辑分析、数据处理、问题拆解与协作表达能力，体现相关能力向目标岗位迁移的潜力。","reason":"根据专业方向生成参考。","sourceFields":["major"],"verificationRequired":true,"verificationItems":["确认课程与工具：数据结构、数据库原理、概率论与数理统计、Python、SQL","确认实践流程：数据采集、数据清洗、探索性分析、数据建模、结果可视化","确认能力方向：逻辑分析、数据处理、问题拆解、协作表达"]}
                                """,
                        new Usage(40, 80, 120), null))
                .thenReturn(new Response("description-direct", "channel-1", "qwen-plus",
                        """
                                {"suggestion":"• 系统学习数据结构、数据库原理、概率论与数理统计等专业课程，持续运用 Python、SQL 完成数据处理与分析练习，构建扎实的专业知识基础。\\n• 围绕数据采集、数据清洗、探索性分析、数据建模与结果可视化开展课程实践，熟悉从原始数据整理到分析结果表达的完整工作流程。\\n• 面向数据开发相关岗位持续提升专业能力，注重将数据方法用于实际问题，并培养逻辑分析、问题拆解、沟通表达与团队协作能力。","reason":"根据专业方向生成三条可直接使用的陈述，所列课程、工具、实践和能力需用户确认。","sourceFields":["major"],"verificationRequired":true,"verificationItems":["确认课程与工具：数据结构、数据库原理、概率论与数理统计、Python、SQL","确认实践流程：数据采集、数据清洗、探索性分析、数据建模、结果可视化","确认能力方向：逻辑分析、数据处理、问题拆解、沟通表达、团队协作"]}
                                """,
                        new Usage(55, 75, 130), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"教育陈述句修复验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EDUCATION").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-advisory","recordFacts":{"school":"示例科技大学","major":"数据科学与大数据技术","degree":"本科"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestion").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("可重点呈现"))))
                .andExpect(jsonPath("$.data.suggestion").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("可梳理"))))
                .andExpect(jsonPath("$.data.suggestion").value(org.hamcrest.Matchers.startsWith("• 系统学习")));
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void descriptionSuggestionRejectsEducationHeaderFieldsDatesAndDiagnosticText() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("description-header", "channel-1", "qwen-plus",
                """
                        {"suggestion":"• 2027年1月至2026年12月就读于河南科技学院，本科学历，专业为数据科学与大数据技术，就读地点为新乡。\\n• 围绕专业理论开展课程学习，持续梳理知识结构并形成较为完整的问题分析与解决思路。\\n• 教育经历的开始时间晚于结束时间，请核对起止日期是否准确后再用于正式简历内容。","reason":"整理结构化教育信息。","sourceFields":["school","major","degree","startDate","endDate","location"]}
                        """,
                new Usage(42, 24, 66), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"教育字段重复门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EDUCATION").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-header","recordFacts":{"school":"河南科技学院","major":"数据科学与大数据技术","degree":"本科","startDate":"2027-01","endDate":"2026-12","location":"新乡"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_DESCRIPTION_QUALITY_LOW"));
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
    }

    @Test
    void descriptionSuggestionUsesSchoolOnlyToCreateAConfirmedEducationDraft() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("description-school-only", "channel-1",
                "qwen-plus", """
                        {"suggestion":"• 围绕在校学习持续梳理课程知识与学习方法，通过课堂任务和课后练习建立较为完整的基础知识结构。\\n• 参与课程作业或实践任务的资料整理、方案讨论与成果表达，在协作过程中提升问题分析和沟通能力。\\n• 结合目标方向持续总结可迁移的学习能力、任务执行能力和团队协作经验，为后续岗位实践做好准备。","reason":"根据学校信息生成教育经历参考，全部内容需要用户确认。","sourceFields":["school"],"verificationRequired":true,"verificationItems":["确认课程学习、课堂任务和课后练习经历符合实际","确认参与过课程作业或实践任务并承担资料整理、方案讨论或成果表达","确认学习能力、任务执行能力和团队协作经验符合实际"]}
                        """, new Usage(45, 90, 135), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"事实门禁验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EDUCATION").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-insufficient","recordFacts":{"school":"示例科技大学"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationRequired").value(true))
                .andExpect(jsonPath("$.data.verificationItems.length()").value(3))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-description-v8"));
        verify(gateway, times(1)).execute(any(), any());
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota - 1);
    }

    @Test
    void descriptionSuggestionRejectsDateOnlyRecordWithoutCallingTheModel() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"无语义字段门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EXPERIENCE").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-date-only","recordFacts":{"startDate":"2026-04","endDate":"2026-12","location":"新乡"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AI_DESCRIPTION_FACTS_INSUFFICIENT"));
        verify(gateway, times(0)).execute(any(), any());
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
    }

    @Test
    void descriptionSuggestionUsesHeaderOnlyExperienceToCreateAConfirmedDraft() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("description-header-only", "channel-1",
                "qwen-plus", """
                        {"suggestion":"• 围绕常见业务需求参与功能梳理与实现，主动拆分任务、明确交付边界，并持续跟进开发进度与问题处理。\\n• 结合岗位常见协作方式推进模块开发与联调，及时整理异常现象、定位影响范围，并配合相关成员完成修正。\\n• 配合产品与测试完成需求确认、功能验证和交付检查，沉淀必要文档与复盘记录，保障工作按计划推进。","reason":"根据岗位生成工作经历参考，职责、协作方式和交付内容均需用户确认。","sourceFields":["role"],"verificationRequired":true,"verificationItems":["确认实际参与需求梳理、任务拆分、功能实现和进度跟进","确认实际参与模块开发、联调、异常定位和问题修正","确认实际参与需求确认、功能验证、交付检查、文档或复盘整理"]}
                        """, new Usage(45, 90, 135), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"工作经历事实门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EXPERIENCE").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-header-only","recordFacts":{"company":"星火小组","role":"全栈开发工程师","startDate":"2026-04","endDate":"2026-12","location":"新乡"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestion", org.hamcrest.Matchers.containsString("• 配合产品与测试")))
                .andExpect(jsonPath("$.data.verificationRequired").value(true))
                .andExpect(jsonPath("$.data.verificationItems.length()").value(3))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-description-v8"));
        verify(gateway, times(1)).execute(any(), any());
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota - 1);
    }

    @Test
    void descriptionSuggestionRepairsHeaderOnlyExperienceUntilAThirdDetailedResponse() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        String thinResponse = """
                {"suggestion":"• 参与日常功能开发与问题处理。\\n• 配合团队推进模块联调与测试。\\n• 整理交付内容并跟进后续改进。","reason":"根据岗位生成待确认参考。","sourceFields":["role"],"verificationRequired":true,"verificationItems":["确认是否参与日常功能开发与问题处理","确认是否参与模块联调与测试","确认是否整理交付内容并跟进改进"]}
                """;
        String detailedResponse = """
                {"suggestion":"• 围绕常见业务需求参与功能梳理与实现，主动拆分任务、明确交付边界，并持续跟进开发进度、问题状态与后续处理事项。\\n• 结合岗位常见协作方式推进模块开发与联调，及时整理异常现象、定位影响范围，并配合相关成员完成修正与验证记录。\\n• 配合产品与测试完成需求确认、功能验证和交付检查，整理必要文档与复盘记录，并持续跟进交付后的反馈与改进事项。","reason":"未填写原始描述，三条内容均为依据岗位生成的 AI 推测，需要用户逐条确认。","sourceFields":["role"],"verificationRequired":true,"verificationItems":["确认实际参与需求梳理、任务拆分、功能实现和进度跟进","确认实际参与模块开发、联调、异常定位、问题修正和验证记录","确认实际参与需求确认、功能验证、交付检查、文档整理和反馈改进"]}
                """;
        when(gateway.execute(any(), any()))
                .thenReturn(new Response("description-thin-first", "channel-1", "qwen-plus", thinResponse,
                        new Usage(40, 30, 70), null))
                .thenReturn(new Response("description-thin-second", "channel-1", "qwen-plus", thinResponse,
                        new Usage(55, 30, 85), null))
                .thenReturn(new Response("description-detailed-third", "channel-1", "qwen-plus", detailedResponse,
                        new Usage(65, 95, 160), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"空描述多轮修复验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EXPERIENCE").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-header-repair-three","recordFacts":{"company":"星火小组","role":"全栈开发工程师","startDate":"2024-09","current":true,"location":"新乡市","description":""}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestion", org.hamcrest.Matchers.containsString("• 配合产品与测试")))
                .andExpect(jsonPath("$.data.verificationRequired").value(true))
                .andExpect(jsonPath("$.data.verificationItems.length()").value(3))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-description-v8"));
        verify(gateway, times(3)).execute(any(), any());
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota - 1);
    }

    @Test
    void descriptionSuggestionUsesHeaderOnlyProjectAndOrganizationToCreateConfirmedDrafts() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any()))
                .thenReturn(new Response("description-project-header", "channel-1", "qwen-plus", """
                        {"suggestion":"• 围绕项目目标参与需求梳理与方案拆分，结合负责模块明确实现范围、接口边界、协作方式和阶段性交付要求，并通过评审记录核对任务理解是否一致。\\n• 按照项目开发流程推进功能实现与模块联调，针对联调中出现的异常持续定位调用链路、整理问题原因并配合完成修正，通过测试记录验证处理结果。\\n• 协同团队开展测试验证、文档整理和交付复盘，根据反馈调整实现细节与后续计划，并通过验收清单确认关键功能按阶段要求完成。","reason":"根据项目基础信息生成参考，所有职责、行动和交付内容均需用户确认。","sourceFields":["name","role"],"verificationRequired":true,"verificationItems":["确认实际参与需求梳理、方案拆分和模块范围确认","确认实际参与功能实现、模块联调、异常排查和问题修正","确认实际参与测试验证、文档整理、交付复盘和反馈调整"]}
                        """, new Usage(42, 88, 130), null))
                .thenReturn(new Response("description-organization-header", "channel-1", "qwen-plus", """
                        {"suggestion":"• 围绕组织日常事务参与活动策划与任务安排，协助梳理成员分工、时间节点、协作方式和现场执行要求，并通过筹备清单核对任务进度。\\n• 在筹备过程中持续跟进沟通与资源协调，及时整理阶段进度、待办事项和风险问题，配合成员处理执行阶段出现的异常情况。\\n• 协同成员完成活动通知、现场支持和复盘整理，根据参与反馈完善后续流程与工作安排，并通过复盘记录沉淀可复用的执行方法。","reason":"根据组织和角色信息生成参考，所有活动职责、协作过程和结果均需用户确认。","sourceFields":["name","role"],"verificationRequired":true,"verificationItems":["确认实际参与活动策划、任务安排、成员分工和现场执行","确认实际参与沟通、资源协调、进度跟进和问题处理","确认实际参与活动通知、现场支持、复盘整理和流程完善"]}
                        """, new Usage(43, 89, 132), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"项目组织事实门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        List<Map.Entry<String, Map<String, String>>> cases = List.of(
                Map.entry("PROJECTS", Map.of("name", "招聘系统", "role", "后端开发", "department", "平台组")),
                Map.entry("ORGANIZATIONS", Map.of("name", "学生会", "role", "活动负责人", "department", "技术部")));
        for (var entry : cases) {
            String cardId = card(created, entry.getKey()).path("id").asText();
            String body = mapper.writeValueAsString(Map.of(
                    "clientRequestId", "description-header-only-" + entry.getKey().toLowerCase(),
                    "recordFacts", entry.getValue()));
            mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                            conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.verificationRequired").value(true))
                    .andExpect(jsonPath("$.data.verificationItems.length()").value(3))
                    .andExpect(jsonPath("$.data.promptVersion").value("resume-description-v8"));
        }
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void descriptionSuggestionRepairsTwoBulletExperienceIntoThreeDetailedBullets() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        String twoBullets = "• 负责业务需求分析、功能开发和模块联调，围绕实际需求拆分任务并推进业务模块实现。\\n"
                + "• 排查接口响应问题并优化查询逻辑，协助完成系统测试、问题跟踪和版本交付。";
        String threeBullets = "• 参与业务需求分析与功能开发，围绕实际需求梳理实现范围、拆分功能任务，并推进业务模块开发与联调，通过联调记录核对功能衔接和任务完成情况。\\n"
                + "• 针对接口响应问题排查调用链路和查询逻辑，根据排查结果完成查询优化，并协助开展系统测试、问题跟踪与修正验证，持续记录问题处理状态。\\n"
                + "• 持续跟进功能开发、模块联调和系统测试进度，协助团队处理版本交付过程中的问题，整理交付事项并按计划完成版本发布前检查。";
        when(gateway.execute(any(), any()))
                .thenReturn(new Response("description-two-bullets", "channel-1", "qwen-plus", """
                        {"suggestion":"%s","reason":"整理已确认的职责和交付事实。","sourceFields":["description"]}
                        """.formatted(twoBullets), new Usage(50, 45, 95), null))
                .thenReturn(new Response("description-three-bullets", "channel-1", "qwen-plus", """
                        {"suggestion":"%s","reason":"将已确认的职责、问题处理和交付事实整理为三条可直接使用的经历要点。","sourceFields":["description"]}
                        """.formatted(threeBullets), new Usage(70, 80, 150), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"工作经历质量门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EXPERIENCE").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-three-bullets","recordFacts":{"company":"星火小组","role":"全栈","description":"负责业务需求分析、功能开发和模块联调；排查接口响应问题并优化查询逻辑，协助系统测试和版本交付"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestion").value(threeBullets.replace("\\n", "\n")))
                .andExpect(jsonPath("$.data.promptVersion").value("resume-description-v8"));
        verify(gateway, times(2)).execute(any(), any());
    }

    @Test
    void descriptionSuggestionRejectsUnsupportedNumbersAndRefundsQuota() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("description-unsafe", "channel-1", "qwen-plus",
                """
                        {"suggestion":"在示例科技公司担任后端工程师，使系统性能提升30%。","reason":"突出成果。","sourceFields":["company","role"]}
                        """,
                new Usage(36, 18, 54), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"PROFESSIONAL\",\"title\":\"幻觉门禁验收\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EXPERIENCE").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-unsafe","recordFacts":{"company":"示例科技公司","role":"后端工程师","description":"负责后端接口开发和数据处理，参与需求分析、功能联调、系统测试以及版本交付工作"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_UNSUPPORTED_FACT"));
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
    }

    @Test
    void descriptionSuggestionRejectsUnderDetailedOutputAndRefundsQuota() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("description-thin", "channel-1", "qwen-plus",
                """
                        {"suggestion":"主修数据结构、操作系统与数据库课程。","reason":"整理课程信息。","sourceFields":["school","major","degree","startDate","endDate","location","description"]}
                        """,
                new Usage(42, 18, 60), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"描述质量门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EDUCATION").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-thin","recordFacts":{"school":"示例科技大学","major":"软件工程","degree":"本科","startDate":"2021-09","endDate":"2025-06","location":"杭州","description":"主修数据结构、操作系统与数据库课程"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_DESCRIPTION_QUALITY_LOW"));
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
    }

    @Test
    void descriptionSuggestionRejectsInventedTechnicalTermsAndRefundsQuota() throws Exception {
        when(aiCandidates.availability(any(), nullable(String.class)))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
        when(gateway.execute(any(), any())).thenReturn(new Response("description-tech", "channel-1", "qwen-plus",
                """
                        {"suggestion":"• 就读于示例科技大学软件工程本科。\\n• 熟练使用 Python 完成数据分析。","reason":"整理教育背景和专业能力。","sourceFields":["school","major","degree","description"]}
                        """,
                new Usage(42, 24, 66), null));
        Session owner = seeker();
        JsonNode created = data(mockMvc.perform(post("/api/v1/ai-resume/conversations")
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"STUDENT\",\"title\":\"技能幻觉门禁\"}"))
                .andExpect(status().isOk()).andReturn());
        String conversationId = created.path("id").asText();
        String cardId = card(created, "EDUCATION").path("id").asText();
        mockMvc.perform(post("/api/v1/ai-resume/consent").cookie(owner.cookie()))
                .andExpect(status().isOk());
        int beforeQuota = quota.current(owner.accountId()).remainingUnits();

        mockMvc.perform(post("/api/v1/ai-resume/conversations/{conversation}/cards/{card}/records/0/description-suggestion",
                        conversationId, cardId).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId":"description-tech","recordFacts":{"school":"示例科技大学","major":"软件工程","degree":"本科","description":"主修数据结构、操作系统与数据库课程"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("AI_UNSUPPORTED_FACT"));
        assertThat(quota.current(owner.accountId()).remainingUnits()).isEqualTo(beforeQuota);
    }

    private JsonNode startAndAwaitWorkbenchPdf(Session owner, String conversationId) throws Exception {
        return startAndAwaitWorkbenchPdf(owner, conversationId, null);
    }

    private JsonNode startAndAwaitWorkbenchPdf(
            Session owner, String conversationId, String exportMode) throws Exception {
        var request = post("/api/v1/ai-resume/conversations/{id}/export-pdf", conversationId)
                .cookie(owner.cookie());
        if (exportMode != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("exportMode", exportMode)));
        }
        JsonNode started = data(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskType").value("RESUME_PDF_EXPORT"))
                .andReturn());
        AtomicReference<JsonNode> completed = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode task = data(mockMvc.perform(get("/api/v1/tasks/{id}", started.path("id").asText())
                            .cookie(owner.cookie()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.data.fileId").isNotEmpty())
                    .andReturn());
            completed.set(task);
        });
        return completed.get();
    }

    private static JsonNode versionBySource(JsonNode conversation, String source) {
        for (JsonNode version : conversation.path("resume").path("versions")) {
            if (source.equals(version.path("source").asText())) return version;
        }
        throw new AssertionError("resume version not found for source: " + source);
    }

    private JsonNode submitCard(
            Session owner,
            String conversationId,
            JsonNode conversation,
            String cardType,
            String payloadJson) throws Exception {
        JsonNode selected = card(conversation, cardType);
        return data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/cards/{card}/submit",
                        conversationId, selected.path("id").asText()).cookie(owner.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":%s,\"expectedVersion\":%d}".formatted(
                        payloadJson, selected.path("versionNo").asInt())))
                .andExpect(status().isOk()).andReturn());
    }

    private Response inlineSummaryChange(String responseId, String before, String proposed) throws Exception {
        return new Response(responseId, "channel-1", "qwen-plus",
                mapper.writeValueAsString(Map.of(
                        "assistantText", "我已将个人简介整理为一条可直接核对的修改。",
                        "intentCode", "RESUME_CHANGE",
                        "clarificationQuestions", List.of(),
                        "changes", List.of(Map.of(
                                "module", "SUMMARY", "targetPath", "/summary", "operation", "REPLACE_TEXT",
                                "beforeValue", before, "proposedValue", proposed,
                                "reason", "重组已有事实并补足行动、方法和验证结构",
                                "completeGeneration", false,
                                "sourceFacts", List.of(Map.of("source", "resume/summary", "quote", before)))))),
                new Usage(120, 90, 210), null);
    }

    private JsonNode inlineChange(Session owner, String conversationId, String requestId) throws Exception {
        return data(mockMvc.perform(post("/api/v1/ai-resume/conversations/{id}/messages/respond", conversationId)
                        .cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "clientMessageId", requestId, "text", "请优化个人简介"))))
                .andExpect(status().isOk()).andReturn());
    }

    private Session seeker() throws Exception {
        String email = "ai-resume+" + System.nanoTime() + "@example.com";
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

    private JsonNode taxonomyJob(String keyword) throws Exception {
        JsonNode matches = data(mockMvc.perform(get("/api/v1/job-taxonomy").param("keyword", keyword))
                .andExpect(status().isOk()).andReturn());
        assertThat(matches).isNotEmpty();
        return matches.get(0);
    }

    private static byte[] portraitPng() throws Exception {
        BufferedImage image = new BufferedImage(100, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(232, 238, 247));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(75, 103, 139));
        graphics.fillOval(15, 72, 70, 62);
        graphics.setColor(new Color(238, 197, 165));
        graphics.fillOval(28, 18, 44, 54);
        graphics.setColor(new Color(55, 68, 86));
        graphics.fillArc(26, 13, 48, 37, 0, 180);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static long inkPixels(BufferedImage image) {
        long result = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                if (red < 245 || green < 245 || blue < 245) result++;
            }
        }
        return result;
    }

    private static long differentPixels(BufferedImage first, BufferedImage second) {
        assertThat(first).isNotNull();
        assertThat(second.getWidth()).isEqualTo(first.getWidth());
        assertThat(second.getHeight()).isEqualTo(first.getHeight());
        long result = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) result++;
            }
        }
        return result;
    }

    private static JsonNode smartTemplate(JsonNode templates, String templateId) {
        for (JsonNode template : templates) {
            if (templateId.equals(template.path("templateId").asText())) return template;
        }
        throw new AssertionError("smart template not found: " + templateId);
    }

    private static void writePdfQaArtifact(String templateId, String variant, byte[] pdf) throws Exception {
        String output = System.getProperty("jobproof.pdf.qa-output", "").trim();
        if (output.isEmpty()) return;
        Path directory = Path.of(output).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String family = templateId.replace("rlt-b-", "").replace("-v1", "");
        Files.write(directory.resolve(family + "-" + variant.toLowerCase() + ".pdf"), pdf);
    }

    private static void writePdfQaImages(String templateId, String variant, PDDocument document) throws Exception {
        String output = System.getProperty("jobproof.pdf.qa-images", "").trim();
        if (output.isEmpty()) return;
        Path directory = Path.of(output).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String family = templateId.replace("rlt-b-", "").replace("-v1", "");
        PDFRenderer renderer = new PDFRenderer(document);
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            Path target = directory.resolve(family + "-" + variant.toLowerCase()
                    + "-page-" + (pageIndex + 1) + ".png");
            ImageIO.write(renderer.renderImageWithDPI(pageIndex, 144), "png", target.toFile());
        }
    }

    private static JsonNode card(JsonNode conversation, String type) {
        for (JsonNode card : conversation.path("cards")) {
            if (type.equals(card.path("cardType").asText())) return card;
        }
        throw new AssertionError("card not found: " + type);
    }

    private record Session(Cookie cookie, String accountId) {}
}
