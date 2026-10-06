package com.jobproof;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "jobproof.verification.enabled=false",
        "jobproof.verification.email-provider=disabled",
        "jobproof.verification.sms-provider=disabled"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContactVerificationFailClosedIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void unavailableProvidersAreNotAdvertisedAndCannotSendCodes() throws Exception {
        mockMvc.perform(get("/api/v1/auth/verifications/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailEnabled").value(false))
                .andExpect(jsonPath("$.data.smsEnabled").value(false));

        mockMvc.perform(post("/api/v1/auth/verifications/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","destination":"person@example.com","purpose":"REGISTER"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_PROVIDER_UNAVAILABLE"));
    }
}
