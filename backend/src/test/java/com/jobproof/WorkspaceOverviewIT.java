package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class WorkspaceOverviewIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void newSeekerGetsAnEmptyButCompleteOverview() throws Exception {
        MockCookie seeker = registerAndLogin("workspace-new+" + System.nanoTime() + "@example.com");
        MvcResult result = mockMvc.perform(get("/api/v1/workspace/overview").cookie(seeker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.resumes.total").value(0))
                .andExpect(jsonPath("$.data.resumes.exportable").value(0))
                .andExpect(jsonPath("$.data.mockInterviews.total").value(0))
                .andExpect(jsonPath("$.data.unreadNotifications").isNumber())
                .andExpect(jsonPath("$.data.profile.completeness").isNumber())
                .andReturn();
        JsonNode data = data(result);
        assertThat(data.has("degraded")).isTrue();
        assertThat(data.path("degraded").isArray()).isTrue();
    }

    @Test
    void recentResumesAreListedForTheirOwnerOnly() throws Exception {
        MockCookie owner = registerAndLogin("workspace-owner+" + System.nanoTime() + "@example.com");
        MockCookie other = registerAndLogin("workspace-other+" + System.nanoTime() + "@example.com");
        mockMvc.perform(post("/api/v1/resumes").cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BLANK\",\"title\":\"工作台测试简历\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workspace/overview").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumes.total").value(1))
                .andExpect(jsonPath("$.data.resumes.recent[0].title").value("工作台测试简历"))
                .andExpect(jsonPath("$.data.resumes.recent[0].confirmedModules").isNumber());

        mockMvc.perform(get("/api/v1/workspace/overview").cookie(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumes.total").value(0));
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/workspace/overview")).andExpect(status().isUnauthorized());
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
