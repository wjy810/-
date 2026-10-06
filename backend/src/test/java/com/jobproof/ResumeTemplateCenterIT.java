package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeTaskTypes;
import com.jobproof.modules.resume.domain.ResumeDocxRenderer;
import com.jobproof.modules.resume.domain.ResumePdfRenderer;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionJpaRepository;
import com.jobproof.modules.resume.infra.ResumeRenderArtifactEntity;
import com.jobproof.modules.resume.infra.ResumeRenderArtifactJpaRepository;
import com.jobproof.modules.resume.infra.ResumeVersionEntity;
import com.jobproof.modules.resume.infra.ResumeVersionJpaRepository;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "jobproof.templates.builtin.retire-legacy=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeTemplateCenterIT {

    private static final String TEMPLATE_ID = "rlt-b-ats-minimal-v1";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ResumeVersionJpaRepository resumeVersions;

    @Autowired
    ResumeRenderArtifactJpaRepository renderArtifacts;

    @Autowired
    TaskService taskService;

    @Autowired
    ResumeLayoutTemplateVersionJpaRepository templateVersions;

    @Test
    void demoCatalogFiltersAndAtomicLayoutFreezeUseTheExistingPdfChain() throws Exception {
        Cookie owner = registerAndLogin("template-owner+" + System.nanoTime() + "@example.com");

        mockMvc.perform(get("/api/v1/resume-templates")
                        .cookie(owner)
                        .queryParam("keyword", "ATS")
                        .queryParam("language", "zh-CN")
                        .queryParam("pages", "1")
                        .queryParam("photoPolicy", "DISABLED")
                        .queryParam("atsLevel", "HIGH_UNVERIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(TEMPLATE_ID))
                .andExpect(jsonPath("$.data.items[0].status").value("DEMO"));
        mockMvc.perform(get("/api/v1/resume-templates").cookie(owner).queryParam("language", "en")
                        .queryParam("atsLevel", "HIGH_UNVERIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("rlt-b-english-single-v1"));
        // The built-in English template is published, not a demo draft.
        mockMvc.perform(get("/api/v1/resume-templates").cookie(owner).queryParam("language", "en")
                        .queryParam("atsLevel", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("harvard"))
                .andExpect(jsonPath("$.data.items[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.items[0].rendererProtocol").value("resume-render-v4"));

        JsonNode master = createBlankResume(owner, "模板冻结链路");
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("expectedVersion", master.path("version").asInt());
        update.put("education", "华中科技大学 软件工程 本科");
        update.put("experience", "JobProof 后端开发\n负责简历版本与证据引用的一致性");
        update.put("projects", "结构化简历模板中心\n实现内容与版式分离");
        update.put("skills", "Java / Spring Boot / Vue / TypeScript");
        update.put("selfIntro", "关注事实可追溯和稳定交付");
        mockMvc.perform(put("/api/v1/resumes/{id}", master.path("id").asText())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
        JsonNode beforeApply = getResume(owner, master.path("id").asText());

        MvcResult appliedResult = mockMvc.perform(post("/api/v1/resume-templates/{id}/apply", TEMPLATE_ID)
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"masterId":"%s","variantCode":"BLUE"}
                                """.formatted(master.path("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andReturn();
        JsonNode layout = objectMapper.readTree(appliedResult.getResponse().getContentAsString()).path("data");
        JsonNode afterApply = getResume(owner, master.path("id").asText());
        assertThat(afterApply.path("education")).isEqualTo(beforeApply.path("education"));
        assertThat(afterApply.path("experience")).isEqualTo(beforeApply.path("experience"));
        assertThat(afterApply.path("projects")).isEqualTo(beforeApply.path("projects"));

        mockMvc.perform(get("/api/v1/resume-templates/layouts/current")
                        .cookie(owner)
                        .queryParam("masterId", master.path("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selected").value(true))
                .andExpect(jsonPath("$.data.layout.id").value(layout.path("id").asText()));

        markReady(owner, master.path("id").asText());
        JsonNode ready = getResume(owner, master.path("id").asText());
        MvcResult frozenResult = mockMvc.perform(post("/api/v1/resumes/{id}/freeze", master.path("id").asText())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + ready.path("version").asInt() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"))
                .andExpect(jsonPath("$.data.layoutInstanceId").value(layout.path("id").asText()))
                .andReturn();
        JsonNode frozen = objectMapper.readTree(frozenResult.getResponse().getContentAsString()).path("data");

        mockMvc.perform(get("/api/v1/resume-templates/layouts/current")
                        .cookie(owner)
                        .queryParam("masterId", master.path("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.status").value("FROZEN"));

        MvcResult exportResult = mockMvc.perform(post("/api/v1/resume-templates/layouts/{id}/export", layout.path("id").asText())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"PDF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskType").value("RESUME_PDF_EXPORT"))
                .andReturn();
        String taskId = objectMapper.readTree(exportResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/tasks/{id}", taskId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileId").isNotEmpty()));
        JsonNode task = objectMapper.readTree(mockMvc.perform(get("/api/v1/tasks/{id}", taskId).cookie(owner))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(task.path("resultVersion").asText()).isEqualTo(ResumePdfRenderer.VERSION);
        MvcResult downloaded = mockMvc.perform(get(task.path("downloadUrl").asText()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();
        byte[] pdfBytes = downloaded.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).hasSizeGreaterThan(5_000);
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(pdf.getPage(0).getMediaBox().getWidth()).isEqualTo(595.27563f);
            assertThat(new PDFTextStripper().getText(pdf))
                    .contains("模板冻结链路", "结构化简历 · ATS 单栏", "华中科技大学", "Java / Spring Boot")
                    .doesNotContain("resume-");
            assertThat(pdf.getPage(0).getResources().getFontNames())
                    .anySatisfy(name -> assertThat(pdf.getPage(0).getResources().getFont(name).isEmbedded()).isTrue());
        }

        ResumeVersionEntity frozenVersion = resumeVersions.findById(frozen.path("id").asText()).orElseThrow();
        List<ResumeRenderArtifactEntity> artifacts = renderArtifacts.findByAccountId(frozenVersion.getAccountId()).stream()
                .filter(artifact -> frozenVersion.getId().equals(artifact.getContentVersionId()))
                .toList();
        assertThat(artifacts).singleElement().satisfies(artifact -> {
            assertThat(artifact.getLayoutInstanceId()).isEqualTo(layout.path("id").asText());
            assertThat(artifact.getTemplateVersionId()).isNotBlank();
            assertThat(artifact.getFormat()).isEqualTo("PDF");
            assertThat(artifact.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(artifact.getRendererVersion()).isEqualTo(ResumePdfRenderer.VERSION);
            assertThat(artifact.getFileId()).isEqualTo(task.path("fileId").asText());
            assertThat(artifact.getFileHash()).isEqualTo(sha256(pdfBytes));
            assertThat(artifact.getValidationJson()).contains("\"pdfMagic\":true");
        });

        MvcResult duplicate = mockMvc.perform(post("/api/v1/resumes/versions/{versionId}/export-pdf",
                        frozenVersion.getId()).cookie(owner))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(duplicate.getResponse().getContentAsString()).path("data").path("id").asText())
                .isEqualTo(taskId);
        assertThat(frozen.path("snapshot").path("experience")).isEqualTo(beforeApply.path("experience"));

        mockMvc.perform(post("/api/v1/resume-templates/layouts/{id}/export", layout.path("id").asText())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"DOCX\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_DOCX_TEMPLATE_NOT_PUBLISHED"));

        ResumeLayoutTemplateVersionEntity templateVersion = templateVersions
                .findById(artifacts.get(0).getTemplateVersionId()).orElseThrow();
        String previousStatus = templateVersion.getStatus();
        boolean previousAuthorization = templateVersion.isAuthorizationVerified();
        boolean previousSecurity = templateVersion.isSecurityVerified();
        boolean previousRender = templateVersion.isRenderVerified();
        boolean previousWord = templateVersion.isWordVerified();
        boolean previousWps = templateVersion.isWpsVerified();
        boolean previousAts = templateVersion.isAtsVerified();
        try {
            templateVersion.setStatus("PUBLISHED");
            templateVersion.setAuthorizationVerified(true);
            templateVersion.setSecurityVerified(true);
            templateVersion.setRenderVerified(true);
            templateVersion.setWordVerified(true);
            templateVersion.setWpsVerified(true);
            templateVersion.setAtsVerified(true);
            templateVersions.saveAndFlush(templateVersion);

            mockMvc.perform(get("/api/v1/resume-templates/{id}", TEMPLATE_ID).cookie(owner))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.docxAvailable").value(true))
                    .andExpect(jsonPath("$.data.docxUnavailableReason").doesNotExist());

            MvcResult docxStarted = mockMvc.perform(post(
                            "/api/v1/resume-templates/layouts/{id}/export", layout.path("id").asText())
                            .cookie(owner)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"format\":\"DOCX\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.taskType").value("RESUME_DOCX_EXPORT"))
                    .andReturn();
            String docxTaskId = objectMapper.readTree(docxStarted.getResponse().getContentAsString())
                    .path("data").path("id").asText();
            await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                            get("/api/v1/tasks/{id}", docxTaskId).cookie(owner))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.data.resultVersion").value(ResumeDocxRenderer.VERSION))
                    .andExpect(jsonPath("$.data.fileId").isNotEmpty()));
            JsonNode docxTask = objectMapper.readTree(mockMvc.perform(
                            get("/api/v1/tasks/{id}", docxTaskId).cookie(owner))
                    .andReturn().getResponse().getContentAsString()).path("data");
            MvcResult docxDownload = mockMvc.perform(get(docxTask.path("downloadUrl").asText()).cookie(owner))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType(
                            ResumeDocxRenderer.CONTENT_TYPE)))
                    .andReturn();
            byte[] docxBytes = docxDownload.getResponse().getContentAsByteArray();
            assertThat(docxBytes).hasSizeGreaterThan(3_000).startsWith((byte) 'P', (byte) 'K');
            try (XWPFDocument document = new XWPFDocument(new java.io.ByteArrayInputStream(docxBytes));
                    XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                assertThat(extractor.getText()).contains(
                        "模板冻结链路", "华中科技大学", "负责简历版本与证据引用的一致性", "Java / Spring Boot");
            }
            List<ResumeRenderArtifactEntity> allArtifacts = renderArtifacts
                    .findByAccountId(frozenVersion.getAccountId()).stream()
                    .filter(artifact -> frozenVersion.getId().equals(artifact.getContentVersionId()))
                    .toList();
            assertThat(allArtifacts).hasSize(2);
            assertThat(allArtifacts).anySatisfy(artifact -> {
                assertThat(artifact.getFormat()).isEqualTo("DOCX");
                assertThat(artifact.getRendererVersion()).isEqualTo(ResumeDocxRenderer.VERSION);
                assertThat(artifact.getFileHash()).isEqualTo(sha256(docxBytes));
                assertThat(artifact.getValidationJson())
                        .contains("\"valid\":true", "\"textOrder\":true", "\"macroFree\":true");
            });

            MvcResult duplicateDocx = mockMvc.perform(post(
                            "/api/v1/resumes/versions/{versionId}/export-docx", frozenVersion.getId())
                            .cookie(owner))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(objectMapper.readTree(duplicateDocx.getResponse().getContentAsString())
                    .path("data").path("id").asText()).isEqualTo(docxTaskId);
        } finally {
            templateVersion.setStatus(previousStatus);
            templateVersion.setAuthorizationVerified(previousAuthorization);
            templateVersion.setSecurityVerified(previousSecurity);
            templateVersion.setRenderVerified(previousRender);
            templateVersion.setWordVerified(previousWord);
            templateVersion.setWpsVerified(previousWps);
            templateVersion.setAtsVerified(previousAts);
            templateVersions.saveAndFlush(templateVersion);
        }
    }

    @Test
    void rendererUpgradeDoesNotReuseACompletedTaskFromThePreviousRenderer() throws Exception {
        Cookie owner = registerAndLogin("template-renderer-upgrade+" + System.nanoTime() + "@example.com");
        JsonNode master = createBlankResume(owner, "渲染器升级重导出");
        mockMvc.perform(post("/api/v1/resume-templates/{id}/apply", TEMPLATE_ID)
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"masterId":"%s","variantCode":"BLUE"}
                                """.formatted(master.path("id").asText())))
                .andExpect(status().isOk());
        markReady(owner, master.path("id").asText());
        JsonNode ready = getResume(owner, master.path("id").asText());
        JsonNode frozen = objectMapper.readTree(mockMvc.perform(post("/api/v1/resumes/{id}/freeze", master.path("id").asText())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + ready.path("version").asInt() + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        ResumeVersionEntity frozenVersion = resumeVersions.findById(frozen.path("id").asText()).orElseThrow();

        String oldKey = "RESUME_PDF:" + frozenVersion.getId() + ":" + frozenVersion.getVersionNo();
        TaskView oldTask = taskService.create(
                frozenVersion.getAccountId(),
                ResumeTaskTypes.RESUME_PDF_EXPORT,
                oldKey,
                objectMapper.writeValueAsString(Map.of("resumeVersionId", frozenVersion.getId())));
        taskService.markSucceeded(oldTask.id(), "resume-pdf-v1", objectMapper.writeValueAsString(Map.of(
                "resumeVersionId", frozenVersion.getId(),
                "fileId", "previous-renderer-file")));

        MvcResult started = mockMvc.perform(post("/api/v1/resumes/versions/{versionId}/export-pdf", frozenVersion.getId())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andReturn();
        String taskId = objectMapper.readTree(started.getResponse().getContentAsString()).path("data").path("id").asText();
        assertThat(taskId).isNotEqualTo(oldTask.id());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/tasks/{id}", taskId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.resultVersion").value(ResumePdfRenderer.VERSION))
                .andExpect(jsonPath("$.data.fileId").isNotEmpty()));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void overflowNamesThePageAndSlotAndRollsBackTheWholeFreeze() throws Exception {
        Cookie owner = registerAndLogin("template-overflow+" + System.nanoTime() + "@example.com");
        JsonNode master = createBlankResume(owner, "溢出阻断");
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("expectedVersion", master.path("version").asInt());
        update.put("experience", "超长工作经历与项目事实".repeat(160));
        update.put("projects", "超长项目经历与成果".repeat(650));
        mockMvc.perform(put("/api/v1/resumes/{id}", master.path("id").asText())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/resume-templates/{id}/apply", TEMPLATE_ID)
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"masterId":"%s","variantCode":"MONO"}
                                """.formatted(master.path("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OVERFLOW"))
                .andExpect(jsonPath("$.data.overflow.items[0].slotKey").value("experience"));

        markReady(owner, master.path("id").asText());
        JsonNode ready = getResume(owner, master.path("id").asText());
        mockMvc.perform(post("/api/v1/resumes/{id}/freeze", master.path("id").asText())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + ready.path("version").asInt() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_OVERFLOW"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("experience")))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("第 1 页")));

        mockMvc.perform(get("/api/v1/resumes/{id}/versions", master.path("id").asText()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private JsonNode createBlankResume(Cookie session, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/resumes")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mode", "BLANK", "title", title))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void markReady(Cookie session, String masterId) throws Exception {
        JsonNode current = getResume(session, masterId);
        mockMvc.perform(post("/api/v1/resumes/{id}/ready", masterId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + current.path("version").asInt() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_TO_EXPORT"));
    }

    private JsonNode getResume(Cookie session, String id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/resumes/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    private Cookie registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Passw0rd!"))))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("jobproof_session");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
