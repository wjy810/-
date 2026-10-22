package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 运营概览 (docs/phase2/04). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationsOverviewIT {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountJpaRepository accounts;

    @Test
    void administratorsSeeHowFarSeekersGetAndSeekersCannotLook() throws Exception {
        Cookie admin = account("ops-admin", true);
        JsonNode before = overview(admin);

        Cookie seeker = account("ops-seeker", false);
        mockMvc.perform(get("/api/v1/admin/overview").cookie(seeker)).andExpect(status().isForbidden());
        String seekerId = data(mockMvc.perform(get("/api/v1/me").cookie(seeker)).andReturn()).path("id").asText();
        mockMvc.perform(post("/api/v1/job-applications").cookie(seeker).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"运营科技\",\"positionTitle\":\"产品经理\"}"))
                .andExpect(status().isOk());
        Instant now = Instant.now();
        jdbc.update("INSERT INTO resume_masters(id,account_id,title,status,source,key_outcomes_json,version_no,created_at,updated_at) VALUES(?,?,?,'DRAFT','MANUAL','[]',0,?,?)",
                UUID.randomUUID().toString(), seekerId, "运营测试简历", now, now);

        JsonNode after = overview(admin);
        assertThat(after.path("users").path("total").asLong()).isEqualTo(before.path("users").path("total").asLong() + 1);
        assertThat(after.path("users").path("new7").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(module(after, "applications").path("last7").asLong()).isEqualTo(module(before, "applications").path("last7").asLong() + 1);
        assertThat(after.path("funnel").get(0).path("key").asText()).isEqualTo("registered");
        assertThat(after.path("funnel").get(1).path("accounts").asLong()).isEqualTo(before.path("funnel").get(1).path("accounts").asLong() + 1);
        assertThat(after.path("funnel").get(2).path("accounts").asLong()).isEqualTo(before.path("funnel").get(2).path("accounts").asLong() + 1);
        assertThat(after.path("days")).hasSize(14);
        JsonNode today = after.path("days").get(13);
        assertThat(today.path("signups").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(today.path("applications").asLong()).isGreaterThanOrEqualTo(1);
        // Administrators are not counted as users.
        assertThat(after.path("users").path("total").asLong())
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE role='SEEKER'", Long.class));
    }

    @Test void balanceRedemptionNeverCountsAsProActivation() throws Exception {
        Cookie admin=account("balance-admin",true); Cookie seeker=account("balance-seeker",false);
        String id=data(mockMvc.perform(get("/api/v1/me").cookie(seeker)).andReturn()).path("id").asText();
        JsonNode before=overview(admin);
        jdbc.update("INSERT INTO membership_events(id,account_id,event_type,plan,days,created_at) VALUES(?,?,'REDEEMED','BALANCE',0,?)",UUID.randomUUID().toString(),id,Instant.now());
        JsonNode after=overview(admin);
        assertThat(after.path("membership").path("redeemed30")).isEqualTo(before.path("membership").path("redeemed30"));
        assertThat(after.path("funnel").get(4).path("accounts")).isEqualTo(before.path("funnel").get(4).path("accounts"));
    }

    private JsonNode overview(Cookie admin) throws Exception {
        return data(mockMvc.perform(get("/api/v1/admin/overview").cookie(admin)).andExpect(status().isOk()).andReturn());
    }

    @Test
    void memberOpenAndDailyStatisticsExcludeAdminOwnedRecords() throws Exception {
        Cookie admin = account("ops-filter-admin", true);
        Cookie seeker = account("ops-filter-seeker", false);
        String adminId = data(mockMvc.perform(get("/api/v1/me").cookie(admin)).andReturn()).path("id").asText();
        String seekerId = data(mockMvc.perform(get("/api/v1/me").cookie(seeker)).andReturn()).path("id").asText();
        JsonNode before = overview(admin);
        Instant now = Instant.now();
        for (String id : java.util.List.of(adminId, seekerId)) {
            jdbc.update("INSERT INTO account_memberships(account_id,plan,expires_at,source,created_at,updated_at) VALUES(?,'PRO',?,'TEST',?,?)", id, now.plusSeconds(86400), now, now);
            jdbc.update("INSERT INTO membership_events(id,account_id,event_type,plan,days,created_at) VALUES(?,?,'REDEEMED','PRO',30,?)", UUID.randomUUID().toString(), id, now);
            jdbc.update("INSERT INTO job_applications(id,account_id,company,position_title,channel,stage,furthest_stage,created_at,updated_at) VALUES(?,?,'test','test','OTHER','SAVED','SAVED',?,?)", UUID.randomUUID().toString(), id, now, now);
            String link = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO resume_share_links(id,account_id,token,resume_version_id,label,created_at,updated_at) VALUES(?,?,?,?, 'test',?,?)", link, id, link.replace("-", ""), UUID.randomUUID().toString(), now, now);
            jdbc.update("INSERT INTO resume_share_views(id,link_id,view_key,visitor_hash,device,client,started_at,last_seen_at) VALUES(?,?,'anonymous','hash','DESKTOP','BROWSER',?,?)", UUID.randomUUID().toString(), link, now, now);
        }
        JsonNode after = overview(admin);
        assertThat(after.path("membership").path("pro").asLong()).isEqualTo(before.path("membership").path("pro").asLong() + 1);
        assertThat(after.path("membership").path("redeemed30").asLong()).isEqualTo(before.path("membership").path("redeemed30").asLong() + 1);
        assertThat(module(after, "opens").path("total").asLong()).isEqualTo(module(before, "opens").path("total").asLong() + 1);
        assertThat(module(after, "opens").path("last7").asLong()).isEqualTo(module(before, "opens").path("last7").asLong() + 1);
        for (String field : java.util.List.of("applications", "opens"))
            assertThat(after.path("days").get(13).path(field).asLong()).isEqualTo(before.path("days").get(13).path(field).asLong() + 1);
    }

    private static JsonNode module(JsonNode overview, String key) {
        for (JsonNode module : overview.path("modules")) {
            if (key.equals(module.path("key").asText())) return module;
        }
        throw new AssertionError("no module " + key);
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
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk()).andReturn();
        return login.getResponse().getCookie("jobproof_session");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
