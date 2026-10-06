package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.audit.infra.AuditEventJpaRepository;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.modules.resume.domain.ResumeTemplateDocxInspector;
import com.jobproof.modules.resume.application.TemplateAssetMalwareScanner;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetJpaRepository;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "jobproof.templates.builtin.retire-legacy=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResumeTemplateAssetUploadIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountJpaRepository accounts;
    @Autowired ResumeTemplateAssetJpaRepository assets;
    @Autowired ObjectStoragePort storage;
    @Autowired PrivateFileJpaRepository privateFiles;
    @Autowired AuditEventJpaRepository auditEvents;
    @MockBean TemplateAssetMalwareScanner malwareScanner;

    @BeforeEach
    void malwareScannerIsCleanUnlessTestOverridesIt() {
        when(malwareScanner.scan(any())).thenReturn(
                TemplateAssetMalwareScanner.ScanResult.clean("TEST_SCANNER", "1.0"));
    }

    @Test
    void serverHashesScansAndStoresSafeDocxInTemplateNamespace() throws Exception {
        Cookie admin = adminSession();
        byte[] docx = docx(false);
        long privateFileCountBefore = privateFiles.count();

        MvcResult result = upload(admin, "safe-template.docx", docx, "UNCONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileHash").value(ResumeTemplateDocxInspector.sha256(docx)))
                .andExpect(jsonPath("$.data.scanStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.stored").value(true))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        ResumeTemplateAssetEntity asset = assets.findById(data.path("id").asText()).orElseThrow();

        assertThat(asset.getStorageKey()).startsWith("template-assets/" + asset.getId() + "/");
        assertThat(storage.get(asset.getStorageKey())).isEqualTo(docx);
        assertThat(privateFiles.count()).isEqualTo(privateFileCountBefore);
        assertThat(auditEvents.findAll()).anyMatch(event -> "TEMPLATE_ASSET_IMPORTED".equals(event.getAction())
                && asset.getId().equals(event.getObjectId()));

        String licenseEvidenceId = uploadEvidence(admin, "LICENSE", "commercial-license");
        mockMvc.perform(post("/api/v1/admin/resume-templates/assets/{id}/review", asset.getId())
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVED",
                                "licenseStatus", "APPROVED",
                                "licenseEvidenceId", licenseEvidenceId,
                                "expectedVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.licenseEvidenceId").value(licenseEvidenceId))
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(auditEvents.findAll()).anyMatch(event -> "TEMPLATE_ASSET_APPROVED".equals(event.getAction())
                && asset.getId().equals(event.getObjectId()));

        MvcResult versionsResult = mockMvc.perform(get(
                        "/api/v1/admin/resume-templates/{id}/versions", "rlt-b-career-pro-v1")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();
        JsonNode seeded = objectMapper.readTree(versionsResult.getResponse().getContentAsString())
                .path("data").get(0);
        mockMvc.perform(put("/api/v1/admin/resume-templates/versions/{id}", seeded.path("id").asText())
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rendererProtocol", seeded.path("rendererProtocol").asText(),
                                "definitionJson", seeded.path("definitionJson").asText(),
                                "thumbnailUri", seeded.path("thumbnailUri").asText(),
                                "sourceAssetId", asset.getId(),
                                "expectedVersion", seeded.path("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceAssetId").value(asset.getId()))
                .andExpect(jsonPath("$.data.independentDesignEvidenceId").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/resume-templates/assets/{id}/review", asset.getId())
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "REJECTED", "reason", "cannot overwrite", "expectedVersion", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_ASSET_REVIEW_FINAL"));

        upload(admin, "renamed.docx", docx, "UNCONFIRMED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_ASSET_DUPLICATE"));
    }

    @Test
    void adminCanReadExternalScannerReadinessWithoutCreatingAnAsset() throws Exception {
        Cookie admin = adminSession();

        mockMvc.perform(get("/api/v1/admin/resume-templates/malware-scanner/status").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.outcome").value("CLEAN"))
                .andExpect(jsonPath("$.data.engine").value("TEST_SCANNER"))
                .andExpect(jsonPath("$.data.engineVersion").value("1.0"));
    }

    @Test
    void externalRelationshipIsRejectedAndOriginalIsNotStored() throws Exception {
        Cookie admin = adminSession();
        byte[] docx = docx(true);

        MvcResult result = upload(admin, "external-link.docx", docx, "UNCONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanStatus").value("STATIC_REJECTED"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.stored").value(false))
                .andExpect(jsonPath("$.data.rejectionReason").value("EXTERNAL_RELATIONSHIP"))
                .andReturn();
        ResumeTemplateAssetEntity asset = assets.findById(objectMapper.readTree(
                result.getResponse().getContentAsString()).path("data").path("id").asText()).orElseThrow();

        assertThat(asset.getStorageKey()).isNull();
        assertThat(asset.getScanReportJson()).contains("EXTERNAL_RELATIONSHIP");
        assertThat(auditEvents.findAll()).anyMatch(event -> "TEMPLATE_ASSET_REJECTED".equals(event.getAction())
                && asset.getId().equals(event.getObjectId()));
    }

    @Test
    void reviewRejectionDeletesStoredBytesButRetainsDecisionMetadata() throws Exception {
        Cookie admin = adminSession();
        byte[] docx = docx(false, "review-rejection");
        MvcResult result = upload(admin, "review-rejection.docx", docx, "UNCONFIRMED")
                .andExpect(status().isOk())
                .andReturn();
        String assetId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
        String storageKey = assets.findById(assetId).orElseThrow().getStorageKey();

        mockMvc.perform(post("/api/v1/admin/resume-templates/assets/{id}/review", assetId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "REJECTED",
                                "reason", "commercial scope not authorized",
                                "expectedVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.stored").value(false))
                .andExpect(jsonPath("$.data.rejectionReason").value("commercial scope not authorized"));

        ResumeTemplateAssetEntity rejected = assets.findById(assetId).orElseThrow();
        assertThat(rejected.getStorageKey()).isNull();
        assertThat(rejected.getFileHash()).isEqualTo(ResumeTemplateDocxInspector.sha256(docx));
        assertThatThrownBy(() -> storage.get(storageKey)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unavailableScannerQuarantinesAndBlocksApprovalUntilCleanRescan() throws Exception {
        Cookie admin = adminSession();
        byte[] docx = docx(false, "scanner-unavailable-then-clean");
        when(malwareScanner.scan(any())).thenReturn(
                TemplateAssetMalwareScanner.ScanResult.unavailable("CLAMAV", "SCANNER_TIMEOUT"));

        MvcResult result = upload(admin, "scanner-unavailable.docx", docx, "UNCONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanStatus").value("MALWARE_SCANNER_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.rejectionReason").value("SCANNER_TIMEOUT"))
                .andExpect(jsonPath("$.data.stored").value(true))
                .andReturn();
        String assetId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
        ResumeTemplateAssetEntity quarantined = assets.findById(assetId).orElseThrow();
        String quarantineKey = quarantined.getStorageKey();
        assertThat(quarantineKey).startsWith("template-quarantine/" + assetId + "/");
        assertThat(auditEvents.findAll()).anyMatch(event -> "TEMPLATE_ASSET_QUARANTINED".equals(event.getAction())
                && assetId.equals(event.getObjectId()));

        String evidenceId = uploadEvidence(admin, "LICENSE", "scanner-gate-license");
        mockMvc.perform(post("/api/v1/admin/resume-templates/assets/{id}/review", assetId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVED", "licenseStatus", "APPROVED",
                                "licenseEvidenceId", evidenceId, "expectedVersion", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TEMPLATE_ASSET_SECURITY_GATE_FAILED"));

        when(malwareScanner.scan(any())).thenReturn(
                TemplateAssetMalwareScanner.ScanResult.clean("CLAMAV", "1.4.3/27800"));
        mockMvc.perform(post("/api/v1/admin/resume-templates/assets/{id}/rescan", assetId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expectedVersion", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.version").value(1));

        ResumeTemplateAssetEntity rescanned = assets.findById(assetId).orElseThrow();
        assertThat(rescanned.getStorageKey()).startsWith("template-assets/" + assetId + "/");
        assertThat(storage.get(rescanned.getStorageKey())).isEqualTo(docx);
        assertThatThrownBy(() -> storage.get(quarantineKey)).isInstanceOf(IllegalStateException.class);

        mockMvc.perform(post("/api/v1/admin/resume-templates/assets/{id}/review", assetId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVED", "licenseStatus", "APPROVED",
                                "licenseEvidenceId", evidenceId, "expectedVersion", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void malwareDetectionRejectsWithoutRetainingUploadedBytes() throws Exception {
        Cookie admin = adminSession();
        byte[] docx = docx(false, "malware-detected");
        when(malwareScanner.scan(any())).thenReturn(
                TemplateAssetMalwareScanner.ScanResult.infected(
                        "CLAMAV", "1.4.3/27800", "Win.Test.EICAR_HDB-1"));

        MvcResult result = upload(admin, "infected.docx", docx, "UNCONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanStatus").value("MALWARE_DETECTED"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("MALWARE_DETECTED"))
                .andExpect(jsonPath("$.data.stored").value(false))
                .andReturn();
        ResumeTemplateAssetEntity asset = assets.findById(objectMapper.readTree(
                result.getResponse().getContentAsString()).path("data").path("id").asText()).orElseThrow();

        assertThat(asset.getStorageKey()).isNull();
        assertThat(asset.getScanReportJson()).contains("Win.Test.EICAR_HDB-1", "staticInspection");
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            Cookie admin, String filename, byte[] content, String licenseStatus) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);
        return mockMvc.perform(multipart("/api/v1/admin/resume-templates/assets/import")
                .file(file)
                .param("sourceUri", "hicv-index://templates/controlled-candidate/" + filename)
                .param("licenseStatus", licenseStatus)
                .cookie(admin));
    }

    private String uploadEvidence(Cookie admin, String evidenceType, String label) throws Exception {
        byte[] content = ("immutable template evidence: " + label)
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", label + ".txt", "text/plain", content);
        MvcResult result = mockMvc.perform(multipart("/api/v1/admin/resume-templates/evidence-artifacts")
                        .file(file)
                        .param("evidenceType", evidenceType)
                        .param("description", "controlled evidence " + label)
                        .cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private Cookie adminSession() throws Exception {
        String email = "asset-admin+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Passw0rd!"))))
                .andExpect(status().isOk());
        AccountEntity account = accounts.findByEmail(email).orElseThrow();
        account.setRole("ADMIN");
        accounts.saveAndFlush(account);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("jobproof_session");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static byte[] docx(boolean externalRelationship) throws Exception {
        return docx(externalRelationship, "");
    }

    private static byte[] docx(boolean externalRelationship, String marker) throws Exception {
        String relationships = externalRelationship
                ? """
                  <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                      Target="https://example.invalid/payload" TargetMode="External"/>
                  </Relationships>
                  """
                : """
                  <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                  """;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            add(zip, "[Content_Types].xml", """
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml"
                        ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            add(zip, "_rels/.rels", """
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                        Target="word/document.xml"/>
                    </Relationships>
                    """);
            add(zip, "word/document.xml",
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                            + "<w:body><w:p><w:r><w:t>" + marker + "</w:t></w:r></w:p></w:body></w:document>");
            add(zip, "word/_rels/document.xml.rels", relationships);
            zip.finish();
            return output.toByteArray();
        }
    }

    private static void add(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
