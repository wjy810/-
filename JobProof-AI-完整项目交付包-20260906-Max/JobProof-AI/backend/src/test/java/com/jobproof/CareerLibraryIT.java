package com.jobproof;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
import com.jobproof.modules.career.application.CareerFileMalwareScanner;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Response;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Usage;
import com.jobproof.modules.resume.application.ResumeAiCandidateService;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareerLibraryIT {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockBean CareerFileMalwareScanner malwareScanner;
    @MockBean ResumeAiCandidateService resumeAiCandidateService;
    @MockBean AiGatewayService aiGatewayService;

    @BeforeEach
    void cleanScanner() {
        when(malwareScanner.scan(any())).thenReturn(
                CareerFileMalwareScanner.ScanResult.clean("TEST_SCANNER", "1.0"));
        when(resumeAiCandidateService.availability(any(), anyString()))
                .thenReturn(new ResumeAiCandidateService.Availability(true, "qwen-plus", null));
    }

    @Test
    void profileRecordsFilesAndArchiveLifecycleStayAccountScoped() throws Exception {
        MockCookie owner = registerAndLogin("career-owner+" + System.nanoTime() + "@example.com");
        MockCookie other = registerAndLogin("career-other+" + System.nanoTime() + "@example.com");

        mockMvc.perform(get("/api/v1/career-library/profile").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotVersion").value(0));
        mockMvc.perform(put("/api/v1/career-library/profile").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"basics":{"name":"张三","email":"zhang@example.com"},
                                 "intentions":{"targetJob":"Java 后端工程师"},
                                 "preferences":{"targetCity":"上海"},"summary":"专注后端开发","expectedVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotVersion").value(1))
                .andExpect(jsonPath("$.data.intentions.targetJob").value("Java 后端工程师"));

        MvcResult created = mockMvc.perform(post("/api/v1/career-library/records").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PROJECT","title":"招聘平台","role":"后端开发",
                                 "description":"使用 Java 和 Spring Boot 完成核心服务","coreOutcome":"完成可复核的服务交付"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmed").value(true))
                .andReturn();
        String recordId = data(created).path("id").asText();
        mockMvc.perform(get("/api/v1/career-library/records").cookie(owner).param("keyword", "Spring"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(post("/api/v1/career-library/records/{id}/archive", recordId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(post("/api/v1/career-library/records/{id}/restore", recordId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(get("/api/v1/career-library/records/{id}", recordId).cookie(other))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.reason").value("CAREER_RECORD_NOT_FOUND"));

        MockMultipartFile image = new MockMultipartFile("file", "proof.png", "image/png", PNG);
        MvcResult upload = mockMvc.perform(multipart("/api/v1/career-library/files").file(image)
                        .param("category", "PROOF").param("displayName", "项目证明").cookie(owner))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.file.processingStatus").value("SCANNING"))
                .andExpect(jsonPath("$.data.task.taskType").value("CAREER_FILE_PROCESS"))
                .andReturn();
        String fileId = data(upload).path("file").path("id").asText();
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/career-library/files/{id}", fileId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanStatus").value("CLEAN"))
                .andExpect(jsonPath("$.data.processingStatus").value("READY"))
                .andExpect(jsonPath("$.data.previewPageCount").value(1)));
        mockMvc.perform(get("/api/v1/career-library/files/{id}/preview-pages/1", fileId).cookie(owner))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
        mockMvc.perform(get("/api/v1/career-library/files/{id}/download", fileId).cookie(owner))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG));
        mockMvc.perform(get("/api/v1/career-library/files/{id}/download", fileId).cookie(other))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.reason").value("CAREER_FILE_NOT_FOUND"));
    }

    @Test
    void scannerFailureClosesUploadAndRetiredBusinessRoutesReturn404() throws Exception {
        MockCookie owner = registerAndLogin("career-gates+" + System.nanoTime() + "@example.com");
        when(malwareScanner.scan(any())).thenReturn(
                CareerFileMalwareScanner.ScanResult.unavailable("CLAMAV", "SCANNER_TIMEOUT"));
        MockMultipartFile image = new MockMultipartFile("file", "blocked.png", "image/png", PNG);
        mockMvc.perform(multipart("/api/v1/career-library/files").file(image).param("category", "OTHER").cookie(owner))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.file.processingStatus").value("SCANNING"))
                .andReturn();
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/career-library/files").cookie(owner).param("status", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].processingStatus").value("SCAN_FAILED"))
                .andExpect(jsonPath("$.data.items[0].status").value("QUARANTINED")));

        for (String path : new String[] {"/api/v1/profile", "/api/v1/evidences", "/api/v1/applications",
                "/api/v1/interviews", "/api/v1/reviews"}) {
            mockMvc.perform(get(path).cookie(owner)).andExpect(status().isNotFound());
        }
    }

    @Test
    void historySummaryAndExportsAreReadOnly() throws Exception {
        MockCookie owner = registerAndLogin("career-history+" + System.nanoTime() + "@example.com");
        mockMvc.perform(get("/api/v1/career-library/history").cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/career-library/history/export.json").cookie(owner))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        mockMvc.perform(get("/api/v1/career-library/history/export.md").cookie(owner))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/markdown"));
        mockMvc.perform(post("/api/v1/career-library/history").cookie(owner))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void pendingCandidateWithCareerSourcesExpiresWhenLibrarySnapshotChanges() throws Exception {
        MockCookie owner = registerAndLogin("career-candidate+" + System.nanoTime() + "@example.com");
        mockMvc.perform(get("/api/v1/career-library/profile").cookie(owner)).andExpect(status().isOk());
        String masterId = data(mockMvc.perform(post("/api/v1/resumes").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BLANK\",\"title\":\"资料引用验收简历\"}"))
                .andExpect(status().isOk()).andReturn()).path("id").asText();
        String candidateId = data(mockMvc.perform(post("/api/v1/resumes/{id}/candidates", masterId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldKey\":\"SELF_INTRO\",\"proposedValue\":\"引用资料形成的候选\"}"))
                .andExpect(status().isOk()).andReturn()).path("id").asText();
        jdbc.update("UPDATE resume_candidates SET career_library_snapshot_version=0,career_library_sources_json=? WHERE id=?",
                "[{\"id\":\"record-1\",\"type\":\"PROJECT\",\"title\":\"招聘平台\",\"excerpt\":\"Java 后端开发\"}]",
                candidateId);

        mockMvc.perform(put("/api/v1/career-library/profile").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"basics\":{},\"intentions\":{\"targetJob\":\"后端开发\"},\"preferences\":{},\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.snapshotVersion").value(1));

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/resumes/{id}/candidates", masterId).cookie(owner).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sourceStale").value(true))
                .andExpect(jsonPath("$.data.items[0].careerLibrarySnapshotVersion").value(0))
                .andExpect(jsonPath("$.data.items[0].sourceRefs[0].title").value("招聘平台")));

        mockMvc.perform(post("/api/v1/resumes/{id}/candidates/{candidateId}/confirm", masterId, candidateId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("CANDIDATE_SOURCE_STALE"));
        mockMvc.perform(post("/api/v1/resumes/{id}/candidates/{candidateId}/reject", masterId, candidateId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void realDocxRunsAsynchronouslyAndProducesPrivatePagePreview() throws Exception {
        MockCookie owner = registerAndLogin("career-docx+" + System.nanoTime() + "@example.com");
        MockMultipartFile docx = new MockMultipartFile("file", "synthetic-resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx());
        MvcResult upload = mockMvc.perform(multipart("/api/v1/career-library/files").file(docx)
                        .param("category", "RESUME").cookie(owner))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.file.processingStatus").value("SCANNING"))
                .andReturn();
        String fileId = data(upload).path("file").path("id").asText();
        await().atMost(Duration.ofSeconds(90)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/career-library/files/{id}", fileId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processingStatus").value("READY"))
                .andExpect(jsonPath("$.data.previewPageCount").value(1)));
        mockMvc.perform(get("/api/v1/career-library/files/{id}/preview-pages/1", fileId).cookie(owner))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
    }

    @Test
    void avatarBecomesVisibleOnlyAfterScanAndExplicitCommit() throws Exception {
        MockCookie owner = registerAndLogin("career-avatar+" + System.nanoTime() + "@example.com");
        MockMultipartFile image = new MockMultipartFile("file", "avatar.png", "image/png", PNG);
        MvcResult upload = mockMvc.perform(multipart("/api/v1/career-library/profile/avatar").file(image).cookie(owner))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.profile.avatarFileId").doesNotExist())
                .andExpect(jsonPath("$.data.file.processingStatus").value("SCANNING"))
                .andReturn();
        String fileId = data(upload).path("file").path("id").asText();
        mockMvc.perform(put("/api/v1/career-library/profile/avatar/{fileId}", fileId).cookie(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("CAREER_AVATAR_NOT_READY"));
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/career-library/files/{id}", fileId).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.processingStatus").value("READY")));
        mockMvc.perform(put("/api/v1/career-library/profile/avatar/{fileId}", fileId).cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarFileId").value(fileId));
        mockMvc.perform(get("/api/v1/career-library/profile/avatar/content").cookie(owner))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/career-library/profile/avatar").cookie(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarFileId").doesNotExist());
    }

    @Test
    void aiRecordCandidateDoesNotApplyUntilAccepted() throws Exception {
        String email = "career-ai+" + System.nanoTime() + "@example.com";
        MockCookie owner = registerAndLogin(email);
        String accountId = jdbc.queryForObject("SELECT id FROM accounts WHERE email=?", String.class, email);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO ai_user_consents(id,account_id,consent_type,policy_version,status,granted_at,revoked_at,updated_at) VALUES(?,?,'AI_RESUME_WORKBENCH','v1','GRANTED',?,NULL,?)",
                UUID.randomUUID().toString(), accountId, now, now);
        when(aiGatewayService.execute(anyString(), any())).thenReturn(new Response("response-1", "channel-1",
                "qwen-plus", """
                {"description":"• 清洗用户行为数据并维护分析报表，形成稳定的数据分析交付。\\n• 基于既有报表持续整理用户行为信息，支持团队复核。","coreOutcome":"完成周报交付","reason":"重组已有事实","sourceQuotes":["负责清洗用户行为数据并维护分析报表","完成周报交付"]}
                """, new Usage(40, 80, 120), null));
        MvcResult recordResult = mockMvc.perform(post("/api/v1/career-library/records").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"EXPERIENCE","title":"数据分析实习","organization":"示例科技公司",
                         "description":"负责清洗用户行为数据并维护分析报表","coreOutcome":"完成周报交付"}
                        """))
                .andExpect(status().isOk()).andReturn();
        String recordId = data(recordResult).path("id").asText();
        MvcResult candidateResult = mockMvc.perform(post("/api/v1/career-library/records/{id}/ai-candidates", recordId)
                        .cookie(owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"candidate-test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.sourceRefs.length()").value(2))
                .andReturn();
        String candidateId = data(candidateResult).path("id").asText();
        mockMvc.perform(get("/api/v1/career-library/records/{id}", recordId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("负责清洗用户行为数据并维护分析报表"));
        mockMvc.perform(post("/api/v1/career-library/records/ai-candidates/{id}/accept", candidateId).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCandidateVersion\":0,\"expectedRecordVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        mockMvc.perform(get("/api/v1/career-library/records/{id}", recordId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value(org.hamcrest.Matchers.containsString("稳定的数据分析交付")));
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

    private byte[] docx() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("合成简历 - 数据分析师");
            document.createParagraph().createRun().setText("教育经历与项目成果均为自动化测试数据");
            document.write(output);
            return output.toByteArray();
        }
    }
}
