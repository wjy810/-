package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.changelog.application.ChangelogDistributionService;
import com.jobproof.modules.changelog.application.ChangelogService;
import com.jobproof.modules.identity.infra.AccountEntity;
import com.jobproof.modules.identity.infra.AccountJpaRepository;
import com.jobproof.shared.event.EventTypes;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {"jobproof.worker.in-process=false", "jobproof.worker.poll-ms=600000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChangelogCenterIT {

    private static final AtomicInteger VERSIONS = new AtomicInteger(100);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountJpaRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChangelogService changelog;
    @Autowired ChangelogDistributionService distribution;

    @Test
    void publicationAudienceRevisionArchiveAndDangerousLinksRespectBoundaries() throws Exception {
        Cookie admin = account("changelog-admin", true);
        Cookie seeker = account("changelog-seeker", false);

        mockMvc.perform(get("/api/v1/admin/changelog").cookie(seeker))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.reason").value("ADMIN_ONLY"));

        String publicVersion = version();
        JsonNode draft = create(admin, command(publicVersion, "PUBLIC", "/updates", true, true, null));
        String publicId = draft.path("release").path("id").asText();
        int initialVersion = draft.path("release").path("versionNo").asInt();

        mockMvc.perform(get("/api/v1/updates"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(publicId))));
        mockMvc.perform(put("/api/v1/admin/changelog/{id}", publicId).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(command(publicVersion, "PUBLIC", "/updates", true, true, null)
                                .put("expectedVersion", initialVersion + 5).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("VERSION_CONFLICT"));

        ObjectNode dangerous = command(version(), "PUBLIC", "javascript:alert(1)", false, false, null);
        mockMvc.perform(post("/api/v1/admin/changelog").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(dangerous.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CHANGELOG_CTA_INVALID"));

        JsonNode published = publish(admin, publicId, initialVersion);
        int publishedVersion = published.path("release").path("versionNo").asInt();
        mockMvc.perform(get("/api/v1/updates"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(publicId)));
        mockMvc.perform(get("/api/v1/updates/{version}", publicVersion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.release.status").value("PUBLISHED"));

        ObjectNode revision = command(publicVersion, "PUBLIC", "/updates", true, true, null);
        revision.put("title", "修订后的真实更新标题").put("expectedVersion", publishedVersion);
        ObjectNode revisionRequest = mapper.createObjectNode().put("reason", "修正公开说明中的表述").set("release", revision);
        JsonNode revised = data(mockMvc.perform(post("/api/v1/admin/changelog/{id}/revise", publicId).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(revisionRequest.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.release.currentRevision").value(2)).andReturn());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM changelog_revisions WHERE release_id=?", Integer.class, publicId))
                .isEqualTo(2);

        int revisedVersion = revised.path("release").path("versionNo").asInt();
        mockMvc.perform(post("/api/v1/admin/changelog/{id}/archive", publicId).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + revisedVersion + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.release.status").value("ARCHIVED"));
        mockMvc.perform(get("/api/v1/updates"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(publicId))));
        mockMvc.perform(get("/api/v1/updates/{version}", publicVersion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.release.status").value("ARCHIVED"));

        String privateVersion = version();
        JsonNode privateDraft = create(admin, command(privateVersion, "AUTHENTICATED", null, false, false, null));
        String privateId = privateDraft.path("release").path("id").asText();
        publish(admin, privateId, privateDraft.path("release").path("versionNo").asInt());
        mockMvc.perform(get("/api/v1/updates"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(privateId))));
        mockMvc.perform(get("/api/v1/updates/{version}", privateVersion))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.reason").value("CHANGELOG_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/updates").cookie(seeker))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(privateId)));
        mockMvc.perform(get("/api/v1/updates/{version}", privateVersion).cookie(seeker))
                .andExpect(status().isOk());
    }

    @Test
    void assetsReceiptsAndNotificationsAreGatedAndIdempotent() throws Exception {
        Cookie admin = account("asset-admin", true);
        Cookie seeker = account("asset-seeker", false);
        String releaseVersion = version();
        JsonNode draft = create(admin, command(releaseVersion, "PUBLIC", "/career-library", true, true, null));
        String releaseId = draft.path("release").path("id").asText();

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
        MockMultipartFile image = new MockMultipartFile("file", "release.png", "image/png", png);
        JsonNode asset = data(mockMvc.perform(multipart("/api/v1/admin/changelog/{id}/assets", releaseId)
                        .file(image).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contentType").value("image/png")).andReturn());
        String assetId = asset.path("id").asText();
        mockMvc.perform(get("/api/v1/updates/assets/{id}", assetId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.reason").value("CHANGELOG_ASSET_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/admin/changelog/{releaseId}/assets/{assetId}", releaseId, assetId).cookie(seeker))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/changelog/{releaseId}/assets/{assetId}", releaseId, assetId).cookie(admin))
                .andExpect(status().isOk()).andExpect(content().bytes(png));

        ObjectNode withImage = command(releaseVersion, "PUBLIC", "/career-library", true, true, assetId)
                .put("expectedVersion", draft.path("release").path("versionNo").asInt());
        JsonNode saved = data(mockMvc.perform(put("/api/v1/admin/changelog/{id}", releaseId).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(withImage.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sections[0].imageAssetId").value(assetId)).andReturn());
        publish(admin, releaseId, saved.path("release").path("versionNo").asInt());
        mockMvc.perform(get("/api/v1/updates/assets/{id}", assetId))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("image/png")))
                .andExpect(content().bytes(png));

        distribution.enqueue(releaseId);
        distribution.enqueue(releaseId);
        for (int i = 0; i < 20 && distribution.processOneBatch(); i++) { /* drain resumable job */ }
        JsonNode notifications = data(mockMvc.perform(get("/api/v1/notifications").cookie(seeker))
                .andExpect(status().isOk()).andReturn());
        long matching = 0;
        String notificationId = null;
        for (JsonNode item : notifications.path("items")) {
            if (releaseId.equals(item.path("eventId").asText())) {
                matching++;
                notificationId = item.path("id").asText();
                assertThat(item.path("type").asText()).isEqualTo("PRODUCT_UPDATE");
                assertThat(item.path("actionPath").asText()).isEqualTo("/updates/" + releaseVersion);
            }
        }
        assertThat(matching).isEqualTo(1);
        assertThat(notificationId).isNotBlank();

        mockMvc.perform(get("/api/v1/updates/whats-new").cookie(seeker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.release.id").value(releaseId));
        mockMvc.perform(post("/api/v1/updates/{id}/remind-later", releaseId).cookie(seeker))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/updates/whats-new").cookie(seeker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(post("/api/v1/updates/{id}/acknowledge", releaseId).cookie(seeker))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/updates/{id}/acknowledge", releaseId).cookie(seeker))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM changelog_user_receipts WHERE release_id=?", Integer.class,
                releaseId)).isEqualTo(1);
    }

    @Test
    void concurrentScheduledPublishingCreatesOnePublishedEvent() throws Exception {
        Cookie admin = account("schedule-admin", true);
        String releaseVersion = version();
        JsonNode draft = create(admin, command(releaseVersion, "PUBLIC", null, false, false, null));
        String releaseId = draft.path("release").path("id").asText();
        mockMvc.perform(post("/api/v1/admin/changelog/{id}/schedule", releaseId).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + draft.path("release").path("versionNo").asInt()
                                + ",\"scheduledAt\":\"" + Instant.now().plusSeconds(3600) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.release.status").value("SCHEDULED"));
        jdbc.update("UPDATE changelog_releases SET scheduled_at=? WHERE id=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), releaseId);

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> first = executor.submit(() -> { start.await(); return changelog.publishDue(); });
        Future<Integer> second = executor.submit(() -> { start.await(); return changelog.publishDue(); });
        start.countDown();
        int published = first.get() + second.get();
        executor.shutdownNow();

        assertThat(published).isBetween(0, 1);
        assertThat(jdbc.queryForObject("SELECT status FROM changelog_releases WHERE id=?", String.class, releaseId))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE event_type=? AND payload_json LIKE ?",
                Integer.class, EventTypes.CHANGELOG_PUBLISHED, "%" + releaseId + "%")).isEqualTo(1);
    }

    @Test
    void historicalPublicationKeepsVerifiedDateAndMustRemainSilent() throws Exception {
        Cookie admin = account("history-admin", true);
        Instant publishedAt = Instant.parse("2026-08-18T12:00:00Z");
        JsonNode draft = create(admin, command(version(), "PUBLIC", "/updates", false, false, null));
        String releaseId = draft.path("release").path("id").asText();

        mockMvc.perform(post("/api/v1/admin/changelog/{id}/publish-history", releaseId).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + draft.path("release").path("versionNo").asInt()
                                + ",\"publishedAt\":\"" + publishedAt + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.release.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.release.publishedAt").value(publishedAt.toString()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='CHANGELOG_HISTORY_PUBLISHED' AND object_id=?",
                Integer.class, releaseId)).isEqualTo(1);
        distribution.enqueue(releaseId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM changelog_distribution_jobs WHERE release_id=?",
                Integer.class, releaseId)).isZero();

        JsonNode noisy = create(admin, command(version(), "PUBLIC", "/updates", true, true, null));
        mockMvc.perform(post("/api/v1/admin/changelog/{id}/publish-history", noisy.path("release").path("id").asText())
                        .cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"publishedAt\":\"" + publishedAt + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.reason").value("CHANGELOG_HISTORY_MUST_BE_SILENT"));
    }

    private JsonNode create(Cookie admin, ObjectNode command) throws Exception {
        return data(mockMvc.perform(post("/api/v1/admin/changelog").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(command.toString()))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode publish(Cookie admin, String id, int expectedVersion) throws Exception {
        return data(mockMvc.perform(post("/api/v1/admin/changelog/{id}/publish", id).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + expectedVersion + "}"))
                .andExpect(status().isOk()).andReturn());
    }

    private ObjectNode command(String version, String audience, String ctaPath, boolean show, boolean notify,
            String imageAssetId) {
        ObjectNode command = mapper.createObjectNode();
        command.put("versionLabel", version).put("title", "真实系统更新").put("summary", "这是一条用于验证完整发布闭环的真实合成更新说明。")
                .put("releaseType", "FEATURE").put("audience", audience).put("showWhatsNew", show)
                .put("sendNotification", notify).put("expectedVersion", 0);
        command.putArray("modules").add("PLATFORM");
        if (ctaPath != null) command.put("ctaLabel", "立即体验").put("ctaPath", ctaPath);
        ArrayNode sections = command.putArray("sections");
        ObjectNode section = sections.addObject().put("sectionType", "HIGHLIGHTS").put("title", "本次更新亮点")
                .put("body", "用户可以查看结构化更新内容，并通过站内通知进入详情。");
        section.putArray("items").add("发布、阅读和通知状态均可追溯");
        if (imageAssetId != null) section.put("imageAssetId", imageAssetId).put("imageAlt", "更新日志界面预览");
        return command;
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

    private static String version() {
        return "v9.51." + VERSIONS.incrementAndGet();
    }
}
