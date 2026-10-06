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
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumePdfExportIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void onlyFrozenOrBoundVersionCanExportAndTaskReturnsDownloadableFile() throws Exception {
        Cookie owner = registerAndLogin("pdf-owner+" + System.nanoTime() + "@example.com");
        Cookie stranger = registerAndLogin("pdf-stranger+" + System.nanoTime() + "@example.com");

        mockMvc.perform(post("/api/v1/resumes/versions/{versionId}/export-pdf", UUID.randomUUID())
                        .cookie(owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("RESUME_VERSION_NOT_FOUND"));

        FrozenResume frozen = freezeReadyResume(owner, "可导出简历");
        mockMvc.perform(post("/api/v1/resumes/versions/{versionId}/archive", frozen.resumeVersionId())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(post("/api/v1/resumes/versions/{versionId}/export-pdf", frozen.resumeVersionId())
                        .cookie(owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_VERSION_NOT_EXPORTABLE"));

        FrozenResume exportable = freezeReadyResume(owner, "第二份可导出");
        MvcResult started = mockMvc.perform(post("/api/v1/resumes/versions/{versionId}/export-pdf",
                        exportable.resumeVersionId())
                        .cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskType").value("RESUME_PDF_EXPORT"))
                .andReturn();
        String taskId = objectMapper.readTree(started.getResponse().getContentAsString()).path("data").path("id").asText();

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/tasks/{id}", taskId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.downloadAvailable").value(true))
                .andExpect(jsonPath("$.data.fileId").isNotEmpty())
                .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty()));

        JsonNode task = objectMapper.readTree(mockMvc.perform(get("/api/v1/tasks/{id}", taskId).cookie(owner))
                .andReturn().getResponse().getContentAsString()).path("data");
        String fileId = task.path("fileId").asText();
        String downloadUrl = task.path("downloadUrl").asText();
        assertThat(downloadUrl).isEqualTo("/api/v1/files/" + fileId + "/download");

        mockMvc.perform(get("/api/v1/files/{id}/download", fileId).cookie(stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.reason").value("OBJECT_FORBIDDEN"));

        MvcResult downloaded = mockMvc.perform(get(downloadUrl).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();
        byte[] body = downloaded.getResponse().getContentAsByteArray();
        assertThat(body).hasSizeGreaterThan(8);
        assertThat(new String(body, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    private FrozenResume freezeReadyResume(Cookie session, String title) throws Exception {
        String evidenceId = createEvidence(session);
        JsonNode master = createResume(session, """
                {"mode":"TEMPLATE","templateCode":"SOFTWARE_DEV","title":"%s"}
                """.formatted(title));
        mockMvc.perform(put("/api/v1/resumes/{id}", master.path("id").asText())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%d,"keyOutcomes":[{"text":"完成招聘平台后端","evidenceId":"%s"}]}
                                """.formatted(master.path("version").asInt(), evidenceId)))
                .andExpect(status().isOk());
        JsonNode prepared = getResume(session, master.path("id").asText());
        mockMvc.perform(post("/api/v1/resumes/{id}/ready", master.path("id").asText())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%d}
                                """.formatted(prepared.path("version").asInt())))
                .andExpect(status().isOk());
        JsonNode ready = getResume(session, master.path("id").asText());
        mockMvc.perform(post("/api/v1/resumes/{id}/freeze", master.path("id").asText())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%d}
                                """.formatted(ready.path("version").asInt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"));
        JsonNode frozen = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/resumes/{id}/versions", master.path("id").asText()).cookie(session))
                .andReturn().getResponse().getContentAsString()).path("data").get(0);
        return new FrozenResume(master.path("id").asText(), frozen.path("id").asText());
    }

    private String createEvidence(Cookie session) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/career-library/records")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PROJECT","title":"招聘平台","description":"Java Spring Boot 招聘平台后端","strength":"STRONG"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private JsonNode createResume(Cookie session, String body) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/resumes")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
    }

    private JsonNode getResume(Cookie session, String id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/resumes/{id}", id).cookie(session))
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    private Cookie registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Passw0rd!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("jobproof_session");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private record FrozenResume(String masterId, String resumeVersionId) {
    }
}
