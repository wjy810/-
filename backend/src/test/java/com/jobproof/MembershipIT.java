package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 会员与兑换码 (docs/phase2/02): batches, redemption, stacking, abuse limits and what PRO unlocks. */
@SpringBootTest(properties = {
        "jobproof.ai.workbench.enabled=true",
        "jobproof.ai.workbench.monthly-quota=50",
        "jobproof.membership.pro.ai-monthly-units=400"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MembershipIT {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountJpaRepository accounts;

    @Test
    void codesFromABatchUpgradeOnceStackAndTopUpTheMonth() throws Exception {
        Cookie admin = account("membership-admin", true);
        Cookie seeker = account("membership-seeker", false);
        Cookie other = account("membership-other", false);

        mockMvc.perform(get("/api/v1/membership/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.free.aiMonthlyUnits").value(50))
                .andExpect(jsonPath("$.data.pro.aiMonthlyUnits").value(400))
                .andExpect(jsonPath("$.data.proPrices.length()").value(3));

        mockMvc.perform(post("/api/v1/admin/redeem-codes").cookie(seeker).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "x", "durationDays", 30, "quantity", 1))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/redeem-codes").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "x", "durationDays", 31, "quantity", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("REDEEM_BATCH_DURATION"));

        JsonNode created = data(mockMvc.perform(post("/api/v1/admin/redeem-codes").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "示例大学就业中心 · 秋招", "channel", "高校合作",
                                "durationDays", 30, "quantity", 4))))
                .andExpect(status().isOk()).andReturn());
        List<String> codes = new ArrayList<>();
        created.path("codes").forEach(code -> codes.add(code.asText()));
        assertThat(codes).hasSize(4).allMatch(code -> code.matches("JP-(\\w{4}-){3}\\w{4}"));
        String batchId = created.path("batch").path("id").asText();
        // Only hashes are stored.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM redeem_codes WHERE batch_id=? AND code_hash LIKE 'JP%'",
                Integer.class, batchId)).isZero();

        assertThat(data(mockMvc.perform(get("/api/v1/membership").cookie(seeker)).andReturn()).path("plan").asText())
                .isEqualTo("FREE");
        int grantedBefore = data(mockMvc.perform(get("/api/v1/ai-resume/quota").cookie(seeker)).andReturn())
                .path("grantedUnits").asInt();
        assertThat(grantedBefore).isEqualTo(50);

        Instant before = Instant.now();
        JsonNode upgraded = redeem(seeker, codes.get(0).toLowerCase().replace("-", " "));
        assertThat(upgraded.path("plan").asText()).isEqualTo("PRO");
        assertThat(upgraded.path("entitlements").path("activeLinks").asInt()).isEqualTo(20);
        Instant firstExpiry = Instant.parse(upgraded.path("expiresAt").asText());
        assertThat(firstExpiry).isBetween(before.plus(Duration.ofDays(30)).minusSeconds(5), before.plus(Duration.ofDays(30)).plusSeconds(60));
        assertThat(data(mockMvc.perform(get("/api/v1/ai-resume/quota").cookie(seeker)).andReturn())
                .path("grantedUnits").asInt()).isEqualTo(400);

        // A second code adds its days on top of the running plan.
        JsonNode stacked = redeem(seeker, codes.get(1));
        assertThat(Instant.parse(stacked.path("expiresAt").asText())).isEqualTo(firstExpiry.plus(Duration.ofDays(30)));
        assertThat(stacked.path("events").size()).isEqualTo(2);

        mockMvc.perform(post("/api/v1/membership/redeem").cookie(seeker).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", codes.get(1)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("REDEEM_CODE_ALREADY_YOURS"));
        mockMvc.perform(post("/api/v1/membership/redeem").cookie(other).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", codes.get(0)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("REDEEM_CODE_USED"));

        mockMvc.perform(post("/api/v1/admin/redeem-codes/{id}/revoke", batchId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.redeemed").value(2));
        mockMvc.perform(post("/api/v1/membership/redeem").cookie(other).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", codes.get(2)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("REDEEM_CODE_REVOKED"));
    }

    @Test
    void guessingCodesStopsAfterTenFailuresInAnHour() throws Exception {
        Cookie seeker = account("membership-guess", false);
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/membership/redeem").cookie(seeker).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("code", "JP-AAAA-BBBB-CCCC-" + "DEFG".substring(0, 3) + (char) ('2' + i)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.reason").value("REDEEM_CODE_INVALID"));
        }
        mockMvc.perform(post("/api/v1/membership/redeem").cookie(seeker).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", "JP-AAAA-BBBB-CCCC-DDDD"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.reason").value("REDEEM_TOO_MANY_ATTEMPTS"));
    }

    private JsonNode redeem(Cookie cookie, String code) throws Exception {
        return data(mockMvc.perform(post("/api/v1/membership/redeem").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk()).andReturn());
    }

    private Cookie account(String prefix, boolean admin) throws Exception {
        String email = prefix + "+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk());
        if (admin) {
            AccountEntity account = accounts.findByEmail(email).orElseThrow();
            account.setRole("ADMIN");
            accounts.saveAndFlush(account);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("jobproof_session");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
