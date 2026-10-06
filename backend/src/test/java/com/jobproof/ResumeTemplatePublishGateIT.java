package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.modules.audit.infra.AuditEventJpaRepository;
import com.jobproof.modules.resume.application.BuiltInTemplateCatalog;
import com.jobproof.modules.resume.application.ResumeSmartTemplateCatalogPublisher;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogFacetJpaRepository;
import com.jobproof.modules.resume.infra.ResumeRenderArtifactJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateSlotJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateTestRunJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "jobproof.templates.builtin.retire-legacy=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResumeTemplatePublishGateIT {

    private static final String TEMPLATE_ID = "rlt-b-tech-single-v1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountJpaRepository accounts;
    @Autowired ResumeTemplateTestRunJpaRepository testRuns;
    @Autowired ResumeTemplateSlotJpaRepository slots;
    @Autowired ResumeRenderArtifactJpaRepository renderArtifacts;
    @Autowired AuditEventJpaRepository auditEvents;
    @Autowired ResumeSmartTemplateCatalogPublisher smartCatalogPublisher;
    @Autowired ResumeTemplateCatalogFacetJpaRepository catalogFacets;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired BuiltInTemplateCatalog builtInTemplates;

    @Test
    void publishReadsImmutableServerEvidenceAndNotClientBooleans() throws Exception {
        String email = "template-admin+" + System.nanoTime() + "@example.com";
        Cookie admin = registerAndLogin(email);
        AccountEntity account = accounts.findByEmail(email).orElseThrow();
        account.setRole("ADMIN");
        accounts.saveAndFlush(account);
        mockMvc.perform(get("/api/v1/admin/resume-templates/families").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12 + builtInTemplates.all().size()));
        String independentEvidenceId = uploadEvidence(admin, "INDEPENDENT_DESIGN", "independent-source");
        String securityEvidenceId = uploadEvidence(admin, "SECURITY_SCAN", "security-pass");

        JsonNode seeded = seededVersion(admin, TEMPLATE_ID);
        MvcResult editedResult = mockMvc.perform(put("/api/v1/admin/resume-templates/versions/{id}",
                        seeded.path("id").asText())
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rendererProtocol", seeded.path("rendererProtocol").asText(),
                                "definitionJson", seeded.path("definitionJson").asText(),
                                "thumbnailUri", seeded.path("thumbnailUri").asText(),
                                "independentDesignEvidenceId", independentEvidenceId,
                                "expectedVersion", seeded.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        JsonNode version = objectMapper.readTree(editedResult.getResponse().getContentAsString()).path("data");
        String versionId = version.path("id").asText();
        assertThat(slots.countByTemplateVersionId(versionId)).isEqualTo(9);
        int expected = version.path("version").asInt();

        mockMvc.perform(post("/api/v1/admin/resume-templates/versions/{id}/publish", versionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + expected + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_NOT_TESTED"));

        mockMvc.perform(post("/api/v1/admin/resume-templates/versions/{id}/test", versionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testBody("AUTHORIZATION", "PASSED", securityEvidenceId, expected)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_TEST_EVIDENCE_TYPE_INVALID"));

        version = recordGate(admin, versionId, "AUTHORIZATION", "PASSED", independentEvidenceId, expected);
        expected = version.path("version").asInt();
        assertThat(version.path("status").asText()).isEqualTo("TESTING");
        assertThat(version.path("authorizationVerified").asBoolean()).isTrue();
        assertThat(version.path("securityVerified").asBoolean()).isFalse();

        mockMvc.perform(post("/api/v1/admin/resume-templates/versions/{id}/publish", versionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + expected + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_PUBLISH_GATE_FAILED"));

        for (String gate : new String[] {"SECURITY", "RENDER", "WORD"}) {
            String evidenceId = "SECURITY".equals(gate) ? securityEvidenceId
                    : uploadEvidence(admin, gate + "_TEST", gate.toLowerCase() + "-pass");
            version = recordGate(admin, versionId, gate, "PASSED", evidenceId, expected);
            expected = version.path("version").asInt();
        }
        version = recordGate(admin, versionId, "WPS", "FAILED",
                uploadEvidence(admin, "WPS_TEST", "wps-failed"), expected);
        expected = version.path("version").asInt();
        assertThat(version.path("status").asText()).isEqualTo("DRAFT");
        version = recordGate(admin, versionId, "ATS", "PASSED",
                uploadEvidence(admin, "ATS_TEST", "ats-pass"), expected);
        expected = version.path("version").asInt();
        assertThat(version.path("status").asText()).isEqualTo("DRAFT");
        version = recordGate(admin, versionId, "WPS", "PASSED",
                uploadEvidence(admin, "WPS_TEST", "wps-pass"), expected);
        expected = version.path("version").asInt();
        assertThat(version.path("status").asText()).isEqualTo("TESTING");
        assertThat(version.path("wpsVerified").asBoolean()).isTrue();
        assertThat(testRuns.findByTemplateVersionIdOrderByTemplateVersionNoDesc(versionId)).hasSize(7);

        MvcResult publishedResult = mockMvc.perform(post("/api/v1/admin/resume-templates/versions/{id}/publish", versionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + expected + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.authorizationVerified").value(true))
                .andExpect(jsonPath("$.data.securityVerified").value(true))
                .andExpect(jsonPath("$.data.renderVerified").value(true))
                .andExpect(jsonPath("$.data.wordVerified").value(true))
                .andExpect(jsonPath("$.data.wpsVerified").value(true))
                .andExpect(jsonPath("$.data.atsVerified").value(true))
                .andReturn();
        version = objectMapper.readTree(publishedResult.getResponse().getContentAsString()).path("data");
        expected = version.path("version").asInt();

        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            smartCatalogPublisher.sync(TEMPLATE_ID);
            smartCatalogPublisher.sync(TEMPLATE_ID);
        });
        String catalogEntryId = objectMapper.readTree(mockMvc.perform(get("/api/v1/template-catalog")
                        .queryParam("keyword", "技术项目单页"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("items").get(0).path("id").asText();
        assertThat(catalogFacets.findByCatalogEntryId(catalogEntryId)).hasSize(1);

        mockMvc.perform(post("/api/v1/admin/resume-templates/versions/{id}/test", versionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testBody("ATS", "PASSED",
                                uploadEvidence(admin, "ATS_TEST", "ats-after-publish"), expected)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_VERSION_IMMUTABLE"));

        mockMvc.perform(get("/api/v1/resume-templates").cookie(admin).queryParam("keyword", "技术项目单页"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PUBLISHED"));

        Cookie seeker = registerAndLogin("retired-template-seeker+" + System.nanoTime() + "@example.com");
        FrozenTemplateUse frozen = freezeWithPublishedTemplate(seeker);

        MvcResult retiredResult = mockMvc.perform(post("/api/v1/admin/resume-templates/versions/{id}/retire", versionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + expected + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETIRED"))
                .andReturn();
        version = objectMapper.readTree(retiredResult.getResponse().getContentAsString()).path("data");

        mockMvc.perform(get("/api/v1/resume-templates").cookie(seeker).queryParam("keyword", "技术项目单页"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(post("/api/v1/resume-templates/{id}/apply", TEMPLATE_ID)
                        .cookie(seeker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"masterId":"%s","variantCode":"BLUE"}
                                """.formatted(frozen.masterId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_UNAVAILABLE"));

        MvcResult replayResult = mockMvc.perform(post("/api/v1/resumes/versions/{versionId}/export-pdf",
                        frozen.resumeVersionId()).cookie(seeker))
                .andExpect(status().isOk())
                .andReturn();
        String replayTaskId = objectMapper.readTree(replayResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/tasks/{id}", replayTaskId).cookie(seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileId").isNotEmpty()));
        JsonNode replayTask = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/tasks/{id}", replayTaskId).cookie(seeker))
                .andReturn().getResponse().getContentAsString()).path("data");
        byte[] replayPdf = mockMvc.perform(get(replayTask.path("downloadUrl").asText()).cookie(seeker))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument pdf = Loader.loadPDF(replayPdf)) {
            assertThat(new PDFTextStripper().getText(pdf))
                    .contains("退休模板快照回放", "冻结后仍从快照稳定导出", "Java / Spring Boot");
        }
        assertThat(renderArtifacts.findById(replayTaskId)).hasValueSatisfying(artifact -> {
            assertThat(artifact.getLayoutInstanceId()).isEqualTo(frozen.layoutId());
            assertThat(artifact.getTemplateVersionId()).isEqualTo(versionId);
            assertThat(artifact.getStatus()).isEqualTo("SUCCEEDED");
        });

        MvcResult nextDraftResult = mockMvc.perform(post(
                        "/api/v1/admin/resume-templates/{id}/versions", TEMPLATE_ID)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rendererProtocol", version.path("rendererProtocol").asText(),
                                "definitionJson", version.path("definitionJson").asText(),
                                "independentDesignEvidenceId", independentEvidenceId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionNo").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        String nextVersionId = objectMapper.readTree(nextDraftResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        assertThat(slots.countByTemplateVersionId(nextVersionId)).isEqualTo(9);

        mockMvc.perform(get("/api/v1/admin/resume-templates/evidence-artifacts/{id}/download",
                        independentEvidenceId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(evidenceContent("independent-source")));
        assertThat(auditEvents.findAll()).anyMatch(event -> "TEMPLATE_VERSION_DRAFT_CREATED".equals(event.getAction())
                && nextVersionId.equals(event.getObjectId()));
        assertThat(auditEvents.findAll()).anyMatch(event -> "TEMPLATE_VERSION_PUBLISHED".equals(event.getAction())
                && versionId.equals(event.getObjectId()));
        assertThat(auditEvents.findAll()).anyMatch(event -> "TEMPLATE_VERSION_RETIRED".equals(event.getAction())
                && versionId.equals(event.getObjectId()));
    }

    @Test
    void seededDraftCanBindProvenanceOnceButCannotReplaceIt() throws Exception {
        String email = "legacy-template-admin+" + System.nanoTime() + "@example.com";
        Cookie admin = registerAndLogin(email);
        AccountEntity account = accounts.findByEmail(email).orElseThrow();
        account.setRole("ADMIN");
        accounts.saveAndFlush(account);
        String evidenceId = uploadEvidence(admin, "INDEPENDENT_DESIGN", "legacy-independent-source");

        JsonNode seeded = seededVersion(admin, "rlt-b-ats-minimal-v1");
        String versionId = seeded.path("id").asText();
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("rendererProtocol", seeded.path("rendererProtocol").asText());
        update.put("definitionJson", seeded.path("definitionJson").asText());
        update.put("thumbnailUri", seeded.path("thumbnailUri").asText());
        update.put("independentDesignEvidenceId", evidenceId);
        update.put("expectedVersion", seeded.path("version").asInt());
        MvcResult updatedResult = mockMvc.perform(put("/api/v1/admin/resume-templates/versions/{id}", versionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.independentDesignEvidenceId").value(evidenceId))
                .andReturn();
        JsonNode updated = objectMapper.readTree(updatedResult.getResponse().getContentAsString()).path("data");

        update.put("independentDesignEvidenceId",
                uploadEvidence(admin, "INDEPENDENT_DESIGN", "replacement-independent-source"));
        update.put("expectedVersion", updated.path("version").asInt());
        mockMvc.perform(put("/api/v1/admin/resume-templates/versions/{id}", versionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_PROVENANCE_IMMUTABLE"));
    }

    private JsonNode seededVersion(Cookie admin, String templateId) throws Exception {
        MvcResult versionsResult = mockMvc.perform(get(
                        "/api/v1/admin/resume-templates/{id}/versions", templateId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        return objectMapper.readTree(versionsResult.getResponse().getContentAsString()).path("data").get(0);
    }

    private FrozenTemplateUse freezeWithPublishedTemplate(Cookie seeker) throws Exception {
        JsonNode master = objectMapper.readTree(mockMvc.perform(post("/api/v1/resumes")
                        .cookie(seeker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "mode", "BLANK",
                                "title", "退休模板快照回放"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("expectedVersion", master.path("version").asInt());
        update.put("education", "华中科技大学 软件工程 本科");
        update.put("experience", "JobProof 后端开发\n冻结后仍从快照稳定导出");
        update.put("projects", "结构化简历模板中心\n完成退休模板回放验证");
        update.put("skills", "Java / Spring Boot / PDF");
        update.put("selfIntro", "关注历史投递与冻结版本的长期可读性");
        mockMvc.perform(put("/api/v1/resumes/{id}", master.path("id").asText())
                        .cookie(seeker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        JsonNode layout = objectMapper.readTree(mockMvc.perform(post(
                        "/api/v1/resume-templates/{id}/apply", TEMPLATE_ID)
                        .cookie(seeker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"masterId":"%s","variantCode":"BLUE"}
                                """.formatted(master.path("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andReturn().getResponse().getContentAsString()).path("data");

        JsonNode current = getResume(seeker, master.path("id").asText());
        mockMvc.perform(post("/api/v1/resumes/{id}/ready", master.path("id").asText())
                        .cookie(seeker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + current.path("version").asInt() + "}"))
                .andExpect(status().isOk());
        current = getResume(seeker, master.path("id").asText());
        JsonNode frozen = objectMapper.readTree(mockMvc.perform(post(
                        "/api/v1/resumes/{id}/freeze", master.path("id").asText())
                        .cookie(seeker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + current.path("version").asInt() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"))
                .andReturn().getResponse().getContentAsString()).path("data");
        return new FrozenTemplateUse(master.path("id").asText(), frozen.path("id").asText(), layout.path("id").asText());
    }

    private JsonNode getResume(Cookie seeker, String id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/resumes/{id}", id).cookie(seeker))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    private JsonNode recordGate(Cookie admin, String versionId, String gate, String outcome,
            String evidenceId, int expectedVersion)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/resume-templates/versions/{id}/test", versionId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(testBody(gate, outcome, evidenceId, expectedVersion)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String testBody(String gate, String outcome, String evidenceId, int expectedVersion) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gateCode", gate);
        body.put("outcome", outcome);
        body.put("evidenceId", evidenceId);
        body.put("environmentJson", "{\"runner\":\"manual-controlled\",\"os\":\"Windows 11\"}");
        body.put("summary", gate + " controlled evidence result " + outcome);
        body.put("expectedVersion", expectedVersion);
        return objectMapper.writeValueAsString(body);
    }

    private String uploadEvidence(Cookie admin, String evidenceType, String label) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", label + ".txt", "text/plain", evidenceContent(label));
        MvcResult result = mockMvc.perform(multipart("/api/v1/admin/resume-templates/evidence-artifacts")
                        .file(file)
                        .param("evidenceType", evidenceType)
                        .param("description", "controlled evidence " + label)
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evidenceType").value(evidenceType))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private static byte[] evidenceContent(String label) {
        return ("immutable template evidence: " + label).getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

    private record FrozenTemplateUse(String masterId, String resumeVersionId, String layoutId) {}
}
