package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileUploadGateIT {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    };

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void emptyOversizedDisallowedAndSpoofedUploadsAreUserCorrectable() throws Exception {
        Cookie session = registerAndLogin("file-gate+" + System.nanoTime() + "@example.com");

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("FILE_INVALID"));

        byte[] tooLarge = new byte[1024 * 1024 + 1];
        System.arraycopy(PNG, 0, tooLarge, 0, PNG.length);
        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "big.png", "image/png", tooLarge))
                        .cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("FILE_TOO_LARGE"));

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "note.html", "text/html", "<html>x</html>".getBytes()))
                        .cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("FILE_TYPE_NOT_ALLOWED"));

        mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile(
                                "file", "cert.pdf", "application/pdf", "<html>not-pdf</html>".getBytes()))
                        .cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("FILE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void traversalNameIsAccountIsolatedAndStrangerCannotRead() throws Exception {
        Cookie alice = registerAndLogin("file-alice+" + System.nanoTime() + "@example.com");
        Cookie bob = registerAndLogin("file-bob+" + System.nanoTime() + "@example.com");
        MvcResult me = mockMvc.perform(get("/api/v1/me").cookie(alice)).andReturn();
        String accountId = objectMapper.readTree(me.getResponse().getContentAsString()).path("data").path("id").asText();

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "..\\..\\etc\\passwd.png", "image/png", PNG))
                        .cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("passwd.png"))
                .andReturn();
        String fileId = objectMapper.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asText();
        assertThat(fileId).isNotBlank();

        MvcResult signed = mockMvc.perform(get("/api/v1/files/{id}/download-url", fileId).cookie(alice))
                .andExpect(status().isOk())
                .andReturn();
        String url = objectMapper.readTree(signed.getResponse().getContentAsString()).path("data").path("url").asText();
        assertThat(url).startsWith("local://private/" + accountId + "/");
        assertThat(url).contains(fileId + "-passwd.png");
        assertThat(url).doesNotContain("..");

        mockMvc.perform(get("/api/v1/files/{id}/download", fileId).cookie(bob))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.reason").value("OBJECT_FORBIDDEN"));
        mockMvc.perform(get("/api/v1/files/{id}/download-url", fileId).cookie(bob))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.reason").value("OBJECT_FORBIDDEN"));
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
}
