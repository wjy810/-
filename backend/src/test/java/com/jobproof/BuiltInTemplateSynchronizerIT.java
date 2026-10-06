package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.application.BuiltInTemplateCatalog;
import com.jobproof.modules.resume.application.BuiltInTemplateSynchronizer;
import com.jobproof.modules.resume.application.ResumeSmartTemplateCatalogPublisher;
import com.jobproof.modules.resume.domain.ResumeDesignV2;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateJpaRepository;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryJpaRepository;
import com.jobproof.shared.time.ClockPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Retiring the v3 drafts moves editable work to the mapped built-in template (TPL-08, DSN-07). */
@SpringBootTest(properties = {
        "jobproof.templates.builtin.retire-legacy=false",
        "jobproof.templates.builtin.it=synchronizer"
})
@ActiveProfiles("test")
@DirtiesContext
class BuiltInTemplateSynchronizerIT {

    @Autowired BuiltInTemplateCatalog catalog;
    @Autowired ResumeLayoutTemplateJpaRepository templates;
    @Autowired ResumeLayoutTemplateVersionJpaRepository versions;
    @Autowired ResumeTemplateCatalogEntryJpaRepository catalogEntries;
    @Autowired ResumeSmartTemplateCatalogPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ClockPort clock;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void retiresLegacyDraftsAndMigratesEditableLayoutsAndPreferences() throws Exception {
        String legacyVersion = versions.findByTemplateIdOrderByRevisionNoDesc("rlt-b-tech-double-v1").get(0).getId();
        String account = UUID.randomUUID().toString();
        String master = UUID.randomUUID().toString();
        String branch = UUID.randomUUID().toString();
        String v1 = """
                {"schemaVersion":"resume-design-v1","fontScale":"LARGE","dateFormat":"YYYY_CN_MM",
                 "accentColor":"#123456","hiddenSections":["honors"]}
                """;
        String editable = insertLayout(account, master, legacyVersion, "VALID", v1);
        String frozen = insertLayout(account, master, legacyVersion, "FROZEN", v1);
        Instant now = Instant.now();
        String older = insertPreference(account, master, branch, "rlt-b-tech-single-v1", v1, now.minusSeconds(60));
        String newer = insertPreference(account, master, branch, "rlt-b-tech-double-v1", v1, now);

        BuiltInTemplateSynchronizer.Result first = sync();
        assertThat(first.retiredLegacy()).isEqualTo(BuiltInTemplateCatalog.LEGACY_TEMPLATES.size());
        assertThat(first.migratedLayouts()).isEqualTo(1);
        assertThat(first.migratedPreferences()).isEqualTo(2);

        ResumeLayoutTemplateVersionEntity engineer = versions.findFirstByTemplateIdAndStatusOrderByRevisionNoDesc(
                "engineer", "PUBLISHED").orElseThrow();
        Map<String, Object> migrated = jdbc.queryForMap(
                "SELECT template_version_id, variant_code, status, design_schema_version, design_json, overflow_json "
                        + "FROM resume_layout_instances WHERE id=?", editable);
        assertThat(migrated.get("template_version_id")).isEqualTo(engineer.getId());
        assertThat(migrated.get("variant_code")).isEqualTo("DEFAULT");
        assertThat(migrated.get("status")).isEqualTo("VALID");
        assertThat(migrated.get("design_schema_version")).isEqualTo(ResumeDesignV2.SCHEMA);
        JsonNode design = mapper.readTree((String) migrated.get("design_json"));
        ResumeDesignV2 engineerDefaults = ResumeDesignV2.defaults(catalog.find("engineer").orElseThrow().manifest());
        assertThat(design.path("dateFormat").asText()).isEqualTo("YYYY年MM月");
        assertThat(design.path("hiddenSections").get(0).asText()).isEqualTo("honors");
        assertThat(design.path("customAccent").asText()).isEqualTo("#123456");
        // v1 type scale came from the retired template's preset, not from the user.
        assertThat(design.path("fontSize").asText()).isEqualTo(engineerDefaults.fontSize());
        assertThat(mapper.readTree((String) migrated.get("overflow_json")).path("source").asText())
                .isEqualTo("CLIENT_MEASURED");

        // Frozen versions keep their binding and export with the renderer they were frozen with.
        assertThat(jdbc.queryForObject("SELECT template_version_id FROM resume_layout_instances WHERE id=?",
                String.class, frozen)).isEqualTo(legacyVersion);

        List<Map<String, Object>> preferences = jdbc.queryForList(
                "SELECT id, template_id, variant_code, design_schema_version FROM resume_layout_preferences "
                        + "WHERE account_id=?", account);
        assertThat(preferences).hasSize(1);
        assertThat(preferences.get(0)).containsEntry("id", newer).containsEntry("template_id", "engineer")
                .containsEntry("variant_code", "DEFAULT").containsEntry("design_schema_version", ResumeDesignV2.SCHEMA);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resume_layout_preferences WHERE id=?",
                Integer.class, older)).isZero();

        for (String legacy : BuiltInTemplateCatalog.LEGACY_TEMPLATES.keySet()) {
            assertThat(templates.findById(legacy).orElseThrow().getStatus()).isEqualTo("RETIRED");
            assertThat(versions.findByTemplateIdOrderByRevisionNoDesc(legacy))
                    .allSatisfy(version -> assertThat(version.getStatus()).isEqualTo("RETIRED"));
            catalogEntries.findByEntryTypeAndReferenceId("SMART_TEMPLATE", legacy).ifPresent(entry ->
                    assertThat(entry.getPublicationStatus()).isIn("HIDDEN", "RETIRED"));
        }
        for (BuiltInTemplateCatalog.Entry entry : catalog.all()) {
            String id = entry.manifest().id();
            assertThat(templates.findById(id).orElseThrow().isBuiltin()).as(id).isTrue();
            ResumeLayoutTemplateVersionEntity version = versions
                    .findFirstByTemplateIdAndStatusOrderByRevisionNoDesc(id, "PUBLISHED").orElseThrow();
            assertThat(version.getRendererProtocol()).isEqualTo("resume-render-v4");
            assertThat(version.getVerificationSource()).isEqualTo("BUILTIN_CI");
            assertThat(catalogEntries.findByEntryTypeAndReferenceId("SMART_TEMPLATE", id).orElseThrow()
                    .getPublicationStatus()).as(id).isEqualTo("PUBLISHED");
        }

        BuiltInTemplateSynchronizer.Result second = sync();
        assertThat(second).isEqualTo(new BuiltInTemplateSynchronizer.Result(0, 0, 0, 0));
    }

    @Test
    void operatorRetirementOfABuiltInTemplateSurvivesRestarts() {
        var template = templates.findById("aurora").orElseThrow();
        template.setStatus("RETIRED");
        templates.save(template);
        new TransactionTemplate(transactions).execute(status -> synchronizer(false).synchronize());
        assertThat(templates.findById("aurora").orElseThrow().getStatus()).isEqualTo("RETIRED");
        template = templates.findById("aurora").orElseThrow();
        template.setStatus("PUBLISHED");
        templates.save(template);
    }

    @Test
    void builtInTemplateDroppedFromTheManifestIsRetired() {
        var dropped = new com.jobproof.modules.resume.infra.ResumeLayoutTemplateEntity();
        dropped.setId("dropped-template");
        dropped.setDisplayName("已移除");
        dropped.setFamilyName("现代");
        dropped.setLanguageCode("zh-CN");
        dropped.setRecommendedPages("1");
        dropped.setAtsCandidateLevel("HIGH");
        dropped.setPhotoPolicy("DISABLED");
        dropped.setVariantsJson("[\"DEFAULT\"]");
        dropped.setTagsJson("[]");
        dropped.setStatus("PUBLISHED");
        dropped.setBuiltin(true);
        dropped.setCreatedAt(Instant.now());
        dropped.setUpdatedAt(Instant.now());
        templates.save(dropped);
        new TransactionTemplate(transactions).execute(status -> synchronizer(false).synchronize());
        assertThat(templates.findById("dropped-template").orElseThrow().getStatus()).isEqualTo("RETIRED");
        assertThat(templates.findById("classic").orElseThrow().getStatus()).isEqualTo("PUBLISHED");
    }

    private BuiltInTemplateSynchronizer.Result sync() {
        return new TransactionTemplate(transactions).execute(status -> synchronizer(true).synchronize());
    }

    private BuiltInTemplateSynchronizer synchronizer(boolean retireLegacy) {
        return new BuiltInTemplateSynchronizer(catalog, templates, versions, publisher, jdbc, mapper, clock,
                transactions, true, retireLegacy);
    }

    private String insertLayout(String account, String master, String versionId, String status, String design) {
        String id = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO resume_layout_instances(id,account_id,master_id,template_version_id,variant_code,status,"
                        + "overflow_json,version_no,created_at,updated_at,design_schema_version,design_json) "
                        + "VALUES(?,?,?,?,?,?,?,0,?,?,?,?)",
                id, account, master, versionId, "BLUE", status, "{\"valid\":true,\"items\":[]}", now, now,
                "resume-design-v1", design);
        return id;
    }

    private String insertPreference(String account, String master, String branch, String templateId, String settings,
            Instant updatedAt) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO resume_layout_preferences(id,account_id,master_id,branch_id,template_id,variant_code,"
                        + "design_schema_version,settings_json,version_no,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,0,?,?)",
                id, account, master, branch, templateId, "BLUE", "resume-design-v1", settings,
                Timestamp.from(updatedAt), Timestamp.from(updatedAt));
        return id;
    }
}
