package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.modules.identity.domain.VerificationPurpose;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.modules.identity.infra.ContactVerificationChallengeEntity;
import com.jobproof.modules.identity.infra.ContactVerificationChallengeJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
        "jobproof.verification.sms-provider=dev",
        "jobproof.verification.sms-ip-hourly-limit=100",
        "jobproof.policies.registration-enabled=true",
        "jobproof.policies.terms-version=terms-test-v1",
        "jobproof.policies.privacy-version=privacy-test-v1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DualChannelIdentityIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountJpaRepository accounts;

    @Autowired
    ContactVerificationChallengeJpaRepository challenges;

    @Test
    void emailRegistrationRecordsConsentCreatesSessionAndSupportsIdentifierLogin() throws Exception {
        String email = uniqueEmail("verified");
        Cookie registrationSession = register(
                VerificationChannel.EMAIL,
                email,
                verifiedToken(VerificationChannel.EMAIL, email, VerificationPurpose.REGISTER),
                "Passw0rd!",
                true,
                true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.primaryChannel").value("EMAIL"))
                .andReturn().getResponse().getCookie("jobproof_session");
        assertThat(registrationSession).isNotNull();

        AccountEntity account = accounts.findByEmail(email).orElseThrow();
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        assertThat(account.getTermsVersion()).isEqualTo("terms-test-v1");
        assertThat(account.getTermsAcceptedAt()).isNotNull();
        assertThat(account.getPrivacyVersion()).isEqualTo("privacy-test-v1");
        assertThat(account.getPrivacyAcceptedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/me").cookie(registrationSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayIdentifier").value(email));
        login(email, "Passw0rd!").andExpect(status().isOk());
    }

    @Test
    void phoneRegistrationStoresE164AndSupportsLocalOrE164Login() throws Exception {
        String localPhone = "13900010001";
        String token = verifiedToken(VerificationChannel.SMS, localPhone, VerificationPurpose.REGISTER);

        register(VerificationChannel.SMS, localPhone, token, "Passw0rd!", true, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phoneMasked").value("+86 139****0001"))
                .andExpect(jsonPath("$.data.displayIdentifier").value("+86 139****0001"))
                .andExpect(jsonPath("$.data.primaryChannel").value("SMS"));

        AccountEntity account = accounts.findByPhoneE164("+86" + localPhone).orElseThrow();
        assertThat(account.getPhoneVerifiedAt()).isNotNull();
        login(localPhone, "Passw0rd!").andExpect(status().isOk());
        login("+86 " + localPhone, "Passw0rd!").andExpect(status().isOk());
    }

    @Test
    void emailAndPhoneRecoveryUseOneTimeTokensAndRevokeOldSessions() throws Exception {
        assertRecoveryChangesPasswordAndRevokesSession(
                VerificationChannel.EMAIL,
                uniqueEmail("recover"));
        assertRecoveryChangesPasswordAndRevokesSession(
                VerificationChannel.SMS,
                "13700010002");
    }

    @Test
    void verificationTokenIsBoundToPurposeContactAndSingleConsumption() throws Exception {
        String first = uniqueEmail("bound");
        String other = uniqueEmail("other");
        String token = verifiedToken(VerificationChannel.EMAIL, first, VerificationPurpose.REGISTER);

        register(VerificationChannel.EMAIL, other, token, "Passw0rd!", true, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_TOKEN_INVALID"));
        register(VerificationChannel.EMAIL, first, token, "Passw0rd!", true, true)
                .andExpect(status().isOk());
        register(VerificationChannel.EMAIL, first, token, "Passw0rd!", true, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_TOKEN_INVALID"));

        String purposeContact = uniqueEmail("purpose");
        String purposeToken = verifiedToken(
                VerificationChannel.EMAIL, purposeContact, VerificationPurpose.REGISTER);
        resetPassword(VerificationChannel.EMAIL, purposeContact, purposeToken, "Passw0rd2!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    void agreementsAreMandatoryWithoutConsumingAnOtherwiseValidToken() throws Exception {
        String email = uniqueEmail("agreement");
        String token = verifiedToken(VerificationChannel.EMAIL, email, VerificationPurpose.REGISTER);

        register(VerificationChannel.EMAIL, email, token, "Passw0rd!", false, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("AGREEMENT_REQUIRED"));
        register(VerificationChannel.EMAIL, email, token, "Passw0rd!", true, true)
                .andExpect(status().isOk());
    }

    @Test
    void resendCooldownAndFiveWrongAttemptsCloseTheChallenge() throws Exception {
        String email = uniqueEmail("locked");
        MvcResult requested = requestVerification(
                VerificationChannel.EMAIL, email, VerificationPurpose.REGISTER)
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = data(requested).path("challengeId").asText();

        requestVerification(VerificationChannel.EMAIL, email, VerificationPurpose.REGISTER)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_RESEND_TOO_SOON"));

        for (int attempt = 0; attempt < 5; attempt++) {
            confirmVerification(challengeId, email, "000000")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.reason").value("VERIFICATION_CODE_INVALID"));
        }
        String realCode = mailboxCode(VerificationChannel.EMAIL, email);
        confirmVerification(challengeId, email, realCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_CODE_INVALID"));
        assertThat(challenges.findById(challengeId).orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test
    void expiredChallengeCannotBeConfirmed() throws Exception {
        String email = uniqueEmail("expired");
        MvcResult requested = requestVerification(
                VerificationChannel.EMAIL, email, VerificationPurpose.LOGIN_RECOVERY)
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = data(requested).path("challengeId").asText();
        ContactVerificationChallengeEntity challenge = challenges.findById(challengeId).orElseThrow();
        challenge.setExpiresAt(Instant.now().minusSeconds(1));
        challenges.saveAndFlush(challenge);

        confirmVerification(challengeId, email, mailboxCode(VerificationChannel.EMAIL, email))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_CODE_INVALID"));
    }

    private void assertRecoveryChangesPasswordAndRevokesSession(
            VerificationChannel channel,
            String destination) throws Exception {
        String registrationToken = verifiedToken(channel, destination, VerificationPurpose.REGISTER);
        Cookie oldSession = register(channel, destination, registrationToken, "Passw0rd!", true, true)
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("jobproof_session");
        assertThat(oldSession).isNotNull();

        String recoveryToken = verifiedToken(channel, destination, VerificationPurpose.LOGIN_RECOVERY);
        resetPassword(channel, destination, recoveryToken, "NewPassw0rd!")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        login(destination, "Passw0rd!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("INVALID_CREDENTIALS"));
        login(destination, "NewPassw0rd!").andExpect(status().isOk());
        resetPassword(channel, destination, recoveryToken, "AnotherPass1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("VERIFICATION_TOKEN_INVALID"));
    }

    private org.springframework.test.web.servlet.ResultActions register(
            VerificationChannel channel,
            String destination,
            String token,
            String password,
            boolean acceptedTerms,
            boolean acceptedPrivacy) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel.name());
        body.put("destination", destination);
        body.put("verificationToken", token);
        body.put("password", password);
        body.put("acceptedTerms", acceptedTerms);
        body.put("acceptedPrivacy", acceptedPrivacy);
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String identifier, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "identifier", identifier,
                        "password", password))));
    }

    private org.springframework.test.web.servlet.ResultActions resetPassword(
            VerificationChannel channel,
            String destination,
            String token,
            String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "channel", channel.name(),
                        "destination", destination,
                        "verificationToken", token,
                        "newPassword", password))));
    }

    private String verifiedToken(
            VerificationChannel channel,
            String destination,
            VerificationPurpose purpose) throws Exception {
        MvcResult requested = requestVerification(channel, destination, purpose)
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = data(requested).path("challengeId").asText();
        String code = mailboxCode(channel, destination);
        MvcResult confirmed = confirmVerification(challengeId, destination, code)
                .andExpect(status().isOk())
                .andReturn();
        return data(confirmed).path("verificationToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions requestVerification(
            VerificationChannel channel,
            String destination,
            VerificationPurpose purpose) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verifications/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "channel", channel.name(),
                        "destination", destination,
                        "purpose", purpose.name()))));
    }

    private org.springframework.test.web.servlet.ResultActions confirmVerification(
            String challengeId,
            String destination,
            String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "challengeId", challengeId,
                        "destination", destination,
                        "code", code))));
    }

    private String mailboxCode(VerificationChannel channel, String destination) throws Exception {
        String normalized = channel == VerificationChannel.SMS
                ? destination.startsWith("+86") ? destination.replace(" ", "") : "+86" + destination
                : destination.toLowerCase();
        MvcResult mailbox = mockMvc.perform(get("/internal/dev/mailbox/{destination}", normalized))
                .andExpect(status().isOk())
                .andReturn();
        return data(mailbox).path("code").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "+" + System.nanoTime() + "@example.com";
    }
}
