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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "jobproof.verification.enabled=true",
        "jobproof.verification.email-provider=dev",
        "jobproof.verification.sms-provider=dev"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactVerificationFlowIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void emailAndSmsShareOneFailClosedVerificationProtocol() throws Exception {
        mockMvc.perform(get("/api/v1/auth/verifications/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailEnabled").value(true))
                .andExpect(jsonPath("$.data.smsEnabled").value(true))
                .andExpect(jsonPath("$.data.codeLength").value(6));

        verifyChannel("EMAIL", "verify+" + System.nanoTime() + "@example.com");
        verifyChannel("SMS", "13800138000");
    }

    private void verifyChannel(String channel, String destination) throws Exception {
        MvcResult requested = mockMvc.perform(post("/api/v1/auth/verifications/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"%s","destination":"%s","purpose":"REGISTER"}
                                """.formatted(channel, destination)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.resendAfterSeconds").value(60))
                .andReturn();
        String challengeId = data(requested).path("challengeId").asText();

        String mailboxDestination = "SMS".equals(channel) ? "+86" + destination : destination;
        MvcResult mailbox = mockMvc.perform(get("/internal/dev/mailbox/{destination}", mailboxDestination))
                .andExpect(status().isOk())
                .andReturn();
        String code = data(mailbox).path("code").asText();
        assertThat(code).hasSize(6);

        mockMvc.perform(post("/api/v1/auth/verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","destination":"%s","code":"%s"}
                                """.formatted(challengeId, destination, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationToken").isNotEmpty())
                .andExpect(jsonPath("$.data.channel").value(channel));

        mockMvc.perform(post("/api/v1/auth/verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","destination":"%s","code":"%s"}
                                """.formatted(challengeId, destination, code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_CODE_INVALID"));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
