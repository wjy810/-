package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeDesignV2;
import com.jobproof.modules.resume.domain.ResumeLayoutProtocol;
import com.jobproof.modules.resume.domain.ResumeTemplateManifest;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateJpaRepository;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionJpaRepository;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps the built-in templates in the template tables (docs/phase2/03 §5.1, TPL-06, TPL-08):
 * <ol>
 *   <li>upserts every manifest as a published resume-render-v4 template and version;</li>
 *   <li>retires the twelve resume-layout-v3 drafts;</li>
 *   <li>moves editable layouts and saved design preferences from a retired template to its mapped
 *       built-in template, converting design settings v1 → v2 (frozen versions keep their own
 *       snapshot and still export with the legacy renderer).</li>
 * </ol>
 * Idempotent; runs at startup on every instance. Operator decisions (retiring a built-in template,
 * catalog order, the recommended badge) are kept.
 */
@Component
@Order(10)
public class BuiltInTemplateSynchronizer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BuiltInTemplateSynchronizer.class);
    public static final String VERIFICATION_SOURCE = "BUILTIN_CI";
    public static final String DEFAULT_VARIANT = "DEFAULT";
    private static final int BATCH = 500;
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "steady", "稳健", "modern", "现代", "design", "设计感", "industry", "行业");

    private final BuiltInTemplateCatalog catalog;
    private final ResumeLayoutTemplateJpaRepository templates;
    private final ResumeLayoutTemplateVersionJpaRepository versions;
    private final ResumeSmartTemplateCatalogPublisher publisher;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final TransactionTemplate transactions;
    private final boolean enabled;
    private final boolean retireLegacy;

    public BuiltInTemplateSynchronizer(BuiltInTemplateCatalog catalog,
            ResumeLayoutTemplateJpaRepository templates,
            ResumeLayoutTemplateVersionJpaRepository versions,
            ResumeSmartTemplateCatalogPublisher publisher,
            JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock,
            PlatformTransactionManager transactionManager,
            @Value("${jobproof.templates.builtin.sync-enabled:true}") boolean enabled,
            @Value("${jobproof.templates.builtin.retire-legacy:true}") boolean retireLegacy) {
        this.catalog = catalog;
        this.templates = templates;
        this.versions = versions;
        this.publisher = publisher;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.retireLegacy = retireLegacy;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            transactions.executeWithoutResult(status -> synchronize());
        } catch (DataIntegrityViolationException race) {
            // Another instance inserted the same rows first; its result is what this run would write.
            log.info("built-in templates synchronized concurrently by another instance; re-checking");
            transactions.executeWithoutResult(status -> synchronize());
        }
    }

    public Result synchronize() {
        int upserted = 0;
        for (BuiltInTemplateCatalog.Entry entry : catalog.all()) {
            if (upsert(entry)) upserted++;
        }
        upserted += retireRemovedBuiltIns();
        int retired = 0;
        int layouts = 0;
        int preferences = 0;
        // Off only in tests that use the v3 drafts as fixtures for the admin publish pipeline.
        if (retireLegacy) {
            retired = retireLegacyTemplates();
            layouts = migrateLayouts();
            preferences = migratePreferences();
            BuiltInTemplateCatalog.LEGACY_TEMPLATES.keySet().forEach(publisher::sync);
        }
        catalog.all().forEach(entry -> publisher.sync(entry.manifest().id()));
        if (upserted + retired + layouts + preferences > 0) {
            log.info("built-in templates synchronized upserted={} retiredLegacy={} migratedLayouts={} migratedPreferences={}",
                    upserted, retired, layouts, preferences);
        }
        return new Result(upserted, retired, layouts, preferences);
    }

    private boolean upsert(BuiltInTemplateCatalog.Entry entry) {
        ResumeTemplateManifest manifest = entry.manifest();
        Instant now = clock.now();
        boolean changed = false;
        ResumeLayoutTemplateEntity template = templates.findById(manifest.id()).orElse(null);
        if (template == null) {
            template = new ResumeLayoutTemplateEntity();
            template.setId(manifest.id());
            template.setStatus("PUBLISHED");
            template.setSortOrder(entry.sortOrder());
            template.setCreatedAt(now);
            changed = true;
        }
        String pages = manifest.maxPages() == 1 ? "1" : "1-" + manifest.maxPages();
        String photoPolicy = manifest.photoAllowed() ? "OPTIONAL" : "DISABLED";
        String atsLevel = "strict".equals(manifest.atsLevel()) ? "HIGH" : "MEDIUM";
        String variants = json(List.of(DEFAULT_VARIANT));
        String tags = json(manifest.tags() == null ? List.of() : manifest.tags());
        String family = CATEGORY_LABELS.getOrDefault(manifest.category(), manifest.category());
        if (changed || !template.isBuiltin()
                || !Objects.equals(template.getDisplayName(), manifest.name())
                || !Objects.equals(template.getFamilyName(), family)
                || !Objects.equals(template.getLanguageCode(), manifest.locale())
                || !Objects.equals(template.getRecommendedPages(), pages)
                || !Objects.equals(template.getAtsCandidateLevel(), atsLevel)
                || !Objects.equals(template.getPhotoPolicy(), photoPolicy)
                || !Objects.equals(template.getVariantsJson(), variants)
                || !Objects.equals(template.getTagsJson(), tags)) {
            template.setBuiltin(true);
            template.setDisplayName(manifest.name());
            template.setFamilyName(family);
            template.setLanguageCode(manifest.locale());
            template.setRecommendedPages(pages);
            template.setAtsCandidateLevel(atsLevel);
            template.setPhotoPolicy(photoPolicy);
            template.setVariantsJson(variants);
            template.setTagsJson(tags);
            template.setUpdatedAt(now);
            templates.save(template);
            changed = true;
        }

        String definition = json(entry.json());
        ResumeLayoutTemplateVersionEntity version = versions.findByTemplateIdOrderByRevisionNoDesc(manifest.id())
                .stream().filter(value -> value.getRevisionNo() == manifest.revision()).findFirst().orElse(null);
        if (version == null) {
            version = new ResumeLayoutTemplateVersionEntity();
            version.setId(Ids.newId());
            version.setTemplateId(manifest.id());
            version.setRevisionNo(manifest.revision());
            version.setStatus("PUBLISHED");
            version.setRendererProtocol(ResumeLayoutProtocol.V4);
            version.setDefinitionJson(definition);
            version.setAuthorizationVerified(true);
            version.setSecurityVerified(true);
            version.setRenderVerified(true);
            version.setAtsVerified(true);
            version.setWordVerified(false);
            version.setWpsVerified(false);
            version.setVerificationSource(VERIFICATION_SOURCE);
            version.setTestReportJson(json(Map.of(
                    "source", VERIFICATION_SOURCE,
                    "checks", List.of("layout-regression", "pdf-text-order", "pdf-fonts-embedded"),
                    "docx", "GENERIC_ATS_LAYOUT")));
            version.setVersionNo(0);
            version.setPublishedAt(now);
            version.setCreatedAt(now);
            version.setUpdatedAt(now);
            versions.save(version);
            // Older revisions stay readable for frozen snapshots but are no longer offered; editable
            // layouts move to the new revision (their v2 design stays valid within a template).
            for (ResumeLayoutTemplateVersionEntity older : versions.findByTemplateIdOrderByRevisionNoDesc(manifest.id())) {
                if (older.getRevisionNo() < manifest.revision() && "PUBLISHED".equals(older.getStatus())) {
                    older.setStatus("RETIRED");
                    older.setRetiredAt(now);
                    older.setVersionNo(older.getVersionNo() + 1);
                    older.setUpdatedAt(now);
                    versions.save(older);
                }
                if (older.getRevisionNo() < manifest.revision()) {
                    jdbc.update("UPDATE resume_layout_instances SET template_version_id=?, version_no=version_no+1, "
                                    + "updated_at=? WHERE template_version_id=? AND status IN ('VALID','OVERFLOW')",
                            version.getId(), Timestamp.from(now), older.getId());
                }
            }
            return true;
        }
        if (!definition.equals(version.getDefinitionJson())) {
            // Same revision, edited manifest (names, palettes): the layout contract is unchanged, and
            // frozen versions carry their own copy of the definition.
            version.setDefinitionJson(definition);
            version.setVersionNo(version.getVersionNo() + 1);
            version.setUpdatedAt(now);
            versions.save(version);
            changed = true;
        }
        return changed;
    }

    /** A built-in template dropped from the manifest file is retired; frozen versions keep their own copy. */
    private int retireRemovedBuiltIns() {
        Instant now = clock.now();
        int count = 0;
        for (ResumeLayoutTemplateEntity template : templates.findAll()) {
            if (!template.isBuiltin() || catalog.contains(template.getId()) || "RETIRED".equals(template.getStatus())) {
                continue;
            }
            template.setStatus("RETIRED");
            template.setUpdatedAt(now);
            templates.save(template);
            for (ResumeLayoutTemplateVersionEntity version : versions.findByTemplateIdOrderByRevisionNoDesc(template.getId())) {
                if ("PUBLISHED".equals(version.getStatus())) {
                    version.setStatus("RETIRED");
                    version.setRetiredAt(now);
                    version.setVersionNo(version.getVersionNo() + 1);
                    version.setUpdatedAt(now);
                    versions.save(version);
                }
            }
            publisher.sync(template.getId());
            count++;
        }
        return count;
    }

    private int retireLegacyTemplates() {
        Instant now = clock.now();
        int count = 0;
        for (String legacyId : BuiltInTemplateCatalog.LEGACY_TEMPLATES.keySet()) {
            ResumeLayoutTemplateEntity template = templates.findById(legacyId).orElse(null);
            if (template == null) continue;
            if (!"RETIRED".equals(template.getStatus())) {
                template.setStatus("RETIRED");
                template.setUpdatedAt(now);
                templates.save(template);
                count++;
            }
            for (ResumeLayoutTemplateVersionEntity version : versions.findByTemplateIdOrderByRevisionNoDesc(legacyId)) {
                if (Set.of("DRAFT", "TESTING", "PUBLISHED").contains(version.getStatus())) {
                    version.setStatus("RETIRED");
                    version.setRetiredAt(now);
                    version.setVersionNo(version.getVersionNo() + 1);
                    version.setUpdatedAt(now);
                    versions.save(version);
                }
            }
        }
        return count;
    }

    private int migrateLayouts() {
        Map<String, String> targetVersions = currentBuiltInVersions();
        String legacyIds = placeholders(BuiltInTemplateCatalog.LEGACY_TEMPLATES.size());
        String sql = "SELECT li.id, li.design_json, v.template_id FROM resume_layout_instances li "
                + "JOIN resume_layout_template_versions v ON v.id = li.template_version_id "
                + "WHERE li.status IN ('VALID','OVERFLOW') AND v.template_id IN (" + legacyIds + ") "
                + "ORDER BY li.id LIMIT " + BATCH;
        Object[] params = BuiltInTemplateCatalog.LEGACY_TEMPLATES.keySet().toArray();
        String overflow = ResumeTemplateService.CLIENT_MEASURED_JSON;
        int total = 0;
        while (true) {
            List<Object[]> rows = jdbc.query(sql, (rs, n) -> new Object[] {
                    rs.getString("id"), rs.getString("design_json"), rs.getString("template_id")}, params);
            if (rows.isEmpty()) return total;
            Timestamp now = Timestamp.from(clock.now());
            List<Object[]> updates = new ArrayList<>();
            for (Object[] row : rows) {
                String targetId = BuiltInTemplateCatalog.LEGACY_TEMPLATES.get((String) row[2]);
                String versionId = targetVersions.get(targetId);
                if (versionId == null) {
                    throw new IllegalStateException("Built-in template has no published version: " + targetId);
                }
                ResumeTemplateManifest manifest = catalog.find(targetId).orElseThrow().manifest();
                ResumeDesignV2 design = ResumeDesignV2.carryOver(manifest, readTree((String) row[1]), null);
                updates.add(new Object[] {versionId, DEFAULT_VARIANT, ResumeDesignV2.SCHEMA, json(design),
                        overflow, now, row[0]});
            }
            jdbc.batchUpdate("UPDATE resume_layout_instances SET template_version_id=?, variant_code=?, "
                    + "design_schema_version=?, design_json=?, status='VALID', overflow_json=?, "
                    + "version_no=version_no+1, updated_at=? WHERE id=?", updates);
            total += rows.size();
        }
    }

    private int migratePreferences() {
        String legacyIds = placeholders(BuiltInTemplateCatalog.LEGACY_TEMPLATES.size());
        Object[] params = BuiltInTemplateCatalog.LEGACY_TEMPLATES.keySet().toArray();
        int total = 0;
        while (true) {
            List<Object[]> rows = jdbc.query("SELECT id, account_id, branch_id, template_id, settings_json "
                            + "FROM resume_layout_preferences WHERE template_id IN (" + legacyIds + ") "
                            + "ORDER BY updated_at DESC, id LIMIT " + BATCH,
                    (rs, n) -> new Object[] {rs.getString("id"), rs.getString("account_id"),
                            rs.getString("branch_id"), rs.getString("template_id"), rs.getString("settings_json")},
                    params);
            if (rows.isEmpty()) return total;
            Timestamp now = Timestamp.from(clock.now());
            for (Object[] row : rows) {
                String targetId = BuiltInTemplateCatalog.LEGACY_TEMPLATES.get((String) row[3]);
                Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM resume_layout_preferences "
                        + "WHERE account_id=? AND branch_id=? AND template_id=?", Integer.class,
                        row[1], row[2], targetId);
                if (existing != null && existing > 0) {
                    // A newer preference for the target template (or one migrated a moment ago) wins.
                    jdbc.update("DELETE FROM resume_layout_preferences WHERE id=?", row[0]);
                } else {
                    ResumeTemplateManifest manifest = catalog.find(targetId).orElseThrow().manifest();
                    ResumeDesignV2 design = ResumeDesignV2.carryOver(manifest, readTree((String) row[4]), null);
                    jdbc.update("UPDATE resume_layout_preferences SET template_id=?, variant_code=?, "
                                    + "design_schema_version=?, settings_json=?, version_no=version_no+1, updated_at=? "
                                    + "WHERE id=?",
                            targetId, DEFAULT_VARIANT, ResumeDesignV2.SCHEMA, json(design), now, row[0]);
                }
                total++;
            }
        }
    }

    private Map<String, String> currentBuiltInVersions() {
        Map<String, String> result = new java.util.HashMap<>();
        for (BuiltInTemplateCatalog.Entry entry : catalog.all()) {
            String id = entry.manifest().id();
            versions.findByTemplateIdOrderByRevisionNoDesc(id).stream()
                    .filter(version -> version.getRevisionNo() == entry.manifest().revision())
                    .findFirst()
                    .ifPresent(version -> result.put(id, version.getId()));
        }
        return result;
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Built-in template data cannot be serialized", exception);
        }
    }

    public record Result(int upserted, int retiredLegacy, int migratedLayouts, int migratedPreferences) {}
}
