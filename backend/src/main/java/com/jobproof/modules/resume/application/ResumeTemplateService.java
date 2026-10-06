package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeDocumentModel;
import com.jobproof.modules.resume.domain.ResumeDesignSettings;
import com.jobproof.modules.resume.domain.ResumeLayoutDefinition;
import com.jobproof.modules.resume.domain.ResumeLayoutProtocol;
import com.jobproof.modules.resume.domain.ResumeOverflowEngine;
import com.jobproof.modules.resume.domain.ResumePdfPreflight;
import com.jobproof.modules.resume.domain.ResumeStructuredContent;
import com.jobproof.modules.resume.infra.ResumeLayoutInstanceEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutInstanceJpaRepository;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateJpaRepository;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionJpaRepository;
import com.jobproof.modules.resume.infra.ResumeMasterEntity;
import com.jobproof.modules.resume.infra.ResumeMasterJpaRepository;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeTemplateService {
    private static final Set<String> EDITABLE_LAYOUT_STATUSES = Set.of("VALID", "OVERFLOW");

    public static final Set<String> SMART_TEMPLATE_IDS = Set.of(
            "rlt-b-ats-minimal-v1",
            "rlt-b-tech-single-v1",
            "rlt-b-tech-double-v1",
            "rlt-b-campus-v1",
            "rlt-b-career-pro-v1",
            "rlt-b-consulting-v1",
            "rlt-b-finance-v1",
            "rlt-b-product-ops-v1",
            "rlt-b-education-research-v1",
            "rlt-b-english-single-v1",
            "rlt-b-cn-table-v1",
            "rlt-b-qa-data-v1");
    private static final Set<String> DOCX_TEMPLATE_IDS = SMART_TEMPLATE_IDS;
    private final ResumeLayoutTemplateJpaRepository templates;
    private final ResumeLayoutTemplateVersionJpaRepository templateVersions;
    private final ResumeLayoutInstanceJpaRepository layouts;
    private final ResumeMasterJpaRepository masters;
    private final ResumeService resumeService;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final boolean demoEnabled;

    public ResumeTemplateService(ResumeLayoutTemplateJpaRepository templates,
            ResumeLayoutTemplateVersionJpaRepository templateVersions,
            ResumeLayoutInstanceJpaRepository layouts,
            ResumeMasterJpaRepository masters,
            ResumeService resumeService,
            ObjectMapper mapper,
            ClockPort clock,
            @Value("${jobproof.templates.demo-enabled:false}") boolean demoEnabled) {
        this.templates = templates;
        this.templateVersions = templateVersions;
        this.layouts = layouts;
        this.masters = masters;
        this.resumeService = resumeService;
        this.mapper = mapper;
        this.clock = clock;
        this.demoEnabled = demoEnabled;
    }

    @Transactional(readOnly = true)
    public PageResult<TemplateSummaryView> catalog(CurrentAccount current, TemplateCatalogQuery query) {
        assertUser(current);
        List<ResumeLayoutTemplateEntity> matched = templates.findAll().stream()
                .filter(this::isCatalogVisible)
                .filter(value -> matches(value, query))
                .sorted(Comparator.comparing(ResumeLayoutTemplateEntity::getDisplayName))
                .toList();
        int from = Math.min(query.page() * query.size(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        return new PageResult<>(matched.subList(from, to).stream().map(this::summary).toList(),
                matched.size(), query.page(), query.size());
    }

    @Transactional(readOnly = true)
    public TemplateDetailView detail(CurrentAccount current, String templateId) {
        assertUser(current);
        ResumeLayoutTemplateEntity template = requireSelectableTemplate(templateId);
        ResumeLayoutTemplateVersionEntity version = requireSelectableVersion(templateId);
        boolean docxAvailable = docxAvailable(version);
        return new TemplateDetailView(summary(template), version.getId(), version.getRevisionNo(),
                version.getRendererProtocol(), json(definition(version)), readStrings(template.getVariantsJson()),
                version.getThumbnailUri(),
                "ATS_CANDIDATE_LEVEL_UNVERIFIED", docxAvailable,
                docxAvailable ? null : docxUnavailableReason(version));
    }

    @Transactional(readOnly = true)
    public PreviewView preview(CurrentAccount current, String templateId, PreviewCommand command) {
        assertUser(current);
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), command.masterId());
        ResumeLayoutTemplateVersionEntity version = requireSelectableVersion(templateId);
        ResumeDocumentModel document = documentOf(master);
        ResumeLayoutDefinition definition = definition(version);
        ResumeOverflowEngine.Report overflow = ResumePdfPreflight.evaluate(
                document, definition, command.variantCode(), version.getRendererProtocol());
        boolean docxAvailable = docxAvailable(version);
        return new PreviewView(templateId, version.getId(), command.variantCode(), overflow,
                readStrings(templates.getReferenceById(templateId).getVariantsJson()).contains(command.variantCode()),
                docxAvailable, docxAvailable ? null : docxUnavailableReason(version));
    }

    @Transactional
    public LayoutView apply(CurrentAccount current, String templateId, ApplyCommand command) {
        assertUser(current);
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), command.masterId());
        ResumeLayoutTemplateVersionEntity version = requireSelectableVersion(templateId);
        assertVariant(templateId, command.variantCode());
        ResumeLayoutDefinition resolvedDefinition = definition(version);
        ResumeLayoutInstanceEntity layout = editableLayout(current.accountId(), master.getId()).orElse(null);
        String priorTemplateId = layout == null ? null : templateIdOf(layout);
        Instant now = clock.now();
        if (layout == null || "FROZEN".equals(layout.getStatus())) {
            layout = new ResumeLayoutInstanceEntity();
            layout.setId(Ids.newId()); layout.setAccountId(current.accountId()); layout.setMasterId(master.getId());
            layout.setVersionNo(0); layout.setCreatedAt(now);
        } else {
            Versions.assertExpected(command.expectedVersion(), layout.getVersionNo());
            layout.setVersionNo(layout.getVersionNo() + 1);
        }
        layout.setTemplateVersionId(version.getId()); layout.setVariantCode(command.variantCode());
        if (layout.getDesignJson() == null || !templateId.equals(priorTemplateId)) {
            ResumeLayoutTemplateEntity template = templates.getReferenceById(templateId);
            layout.setDesignSchemaVersion(ResumeDesignSettings.SCHEMA);
            layout.setDesignJson(json(ResumeDesignSettings.defaults(
                    resolvedDefinition, command.variantCode(), template.getPhotoPolicy(), templateId)));
        }
        ResumeLayoutTemplateEntity selectedTemplate = templates.getReferenceById(templateId);
        ResumeDesignSettings selectedDesign = ResumeDesignSettings.fromJson(
                readTree(layout.getDesignJson()), resolvedDefinition, command.variantCode(),
                selectedTemplate.getPhotoPolicy(), templateId);
        ResumeOverflowEngine.Report report = ResumePdfPreflight.evaluate(
                documentOf(master), selectedDesign.applyTo(resolvedDefinition),
                command.variantCode(), version.getRendererProtocol());
        layout.setStatus(report.valid() ? "VALID" : "OVERFLOW"); layout.setOverflowJson(json(report));
        layout.setContentVersionId(null); layout.setContentSnapshotJson(null); layout.setTemplateSnapshotJson(null);
        layout.setUpdatedAt(now); layouts.save(layout);
        return view(layout);
    }

    @Transactional
    public LayoutView configureDesign(CurrentAccount current, String layoutId, String branchId,
            JsonNode settings, boolean bumpVersion) {
        assertUser(current);
        ResumeLayoutInstanceEntity layout = requireOwnLayout(current.accountId(), layoutId);
        if (!EDITABLE_LAYOUT_STATUSES.contains(layout.getStatus())) {
            throw AppException.conflict("RESUME_LAYOUT_NOT_EDITABLE", "已冻结版式不能修改设计");
        }
        ResumeLayoutTemplateVersionEntity version = templateVersions.findById(layout.getTemplateVersionId())
                .orElseThrow(() -> AppException.conflict(
                        "RESUME_TEMPLATE_VERSION_NOT_FOUND", "当前版式绑定的模板版本不存在"));
        ResumeLayoutTemplateEntity template = templates.findById(version.getTemplateId())
                .orElseThrow(() -> AppException.conflict(
                        "RESUME_TEMPLATE_NOT_FOUND", "当前版式绑定的模板不存在"));
        ResumeLayoutDefinition base = definition(version);
        ResumeDesignSettings design;
        try {
            design = ResumeDesignSettings.fromJson(settings, base, layout.getVariantCode(),
                    template.getPhotoPolicy(), template.getId());
        } catch (IllegalArgumentException exception) {
            throw AppException.user("RESUME_DESIGN_INVALID", exception.getMessage());
        }
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), layout.getMasterId());
        ResumeOverflowEngine.Report report = ResumePdfPreflight.evaluate(
                documentOf(master), design.applyTo(base), layout.getVariantCode(), version.getRendererProtocol());
        layout.setBranchId(branchId);
        layout.setDesignSchemaVersion(ResumeDesignSettings.SCHEMA);
        layout.setDesignJson(json(design));
        layout.setStatus(report.valid() ? "VALID" : "OVERFLOW");
        layout.setOverflowJson(json(report));
        if (bumpVersion) layout.setVersionNo(layout.getVersionNo() + 1);
        layout.setUpdatedAt(clock.now());
        layouts.save(layout);
        return view(layout);
    }

    private String templateIdOf(ResumeLayoutInstanceEntity layout) {
        if (layout.getTemplateVersionId() == null) return null;
        ResumeLayoutTemplateVersionEntity prior = templateVersions.findById(layout.getTemplateVersionId()).orElse(null);
        return prior == null ? null : prior.getTemplateId();
    }

    @Transactional(readOnly = true)
    public LayoutView layout(CurrentAccount current, String layoutId) {
        assertUser(current);
        return view(requireOwnLayout(current.accountId(), layoutId));
    }

    @Transactional(readOnly = true)
    public CurrentLayoutView currentLayout(CurrentAccount current, String masterId) {
        assertUser(current);
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), masterId);
        return currentLayoutEntity(current.accountId(), masterId)
                .map(value -> new CurrentLayoutView(true, currentView(value, master)))
                .orElseGet(() -> new CurrentLayoutView(false, null));
    }

    @Transactional
    public TaskView export(CurrentAccount current, String layoutId, String requestedFormat) {
        assertUser(current);
        ResumeLayoutInstanceEntity layout = requireOwnLayout(current.accountId(), layoutId);
        String format = requestedFormat.trim().toUpperCase();
        if (!Set.of("PDF", "DOCX").contains(format))
            throw AppException.user("RESUME_EXPORT_FORMAT_UNSUPPORTED", "导出格式仅支持 PDF 或 DOCX");
        if (!"FROZEN".equals(layout.getStatus()) || !isOverflowFree(layout.getOverflowJson())) {
            throw AppException.conflict("RESUME_LAYOUT_NOT_EXPORTABLE", "仅允许从已冻结且无溢出的版式实例导出");
        }
        if (layout.getContentVersionId() == null || layout.getContentVersionId().isBlank()) {
            throw AppException.conflict("RESUME_LAYOUT_NOT_EXPORTABLE", "冻结版式缺少内容版本关联，已阻止导出");
        }
        return "DOCX".equals(format)
                ? resumeService.startDocxExport(current, layout.getContentVersionId())
                : resumeService.startPdfExport(current, layout.getContentVersionId());
    }

    private static boolean docxAvailable(ResumeLayoutTemplateVersionEntity version) {
        return version != null
                && DOCX_TEMPLATE_IDS.contains(version.getTemplateId())
                && "PUBLISHED".equals(version.getStatus())
                && version.isAuthorizationVerified()
                && version.isSecurityVerified()
                && version.isRenderVerified()
                && version.isWordVerified()
                && version.isWpsVerified()
                && version.isAtsVerified();
    }

    private static String docxUnavailableReason(ResumeLayoutTemplateVersionEntity version) {
        if (version == null || !DOCX_TEMPLATE_IDS.contains(version.getTemplateId()))
            return "DOCX_TEMPLATE_NOT_ENABLED";
        if (!"PUBLISHED".equals(version.getStatus())) return "DOCX_TEMPLATE_NOT_PUBLISHED";
        return "DOCX_EVIDENCE_INCOMPLETE";
    }

    private TemplateSummaryView summary(ResumeLayoutTemplateEntity value) {
        ResumeLayoutTemplateVersionEntity previewVersion = previewVersion(value).orElse(null);
        return new TemplateSummaryView(value.getId(), value.getDisplayName(), value.getFamilyName(),
                value.getLanguageCode(), value.getRecommendedPages(), value.getAtsCandidateLevel(),
                value.getPhotoPolicy(), readStrings(value.getVariantsJson()), readStrings(value.getTagsJson()),
                isDemoTemplate(value) ? "DEMO" : value.getStatus(),
                previewVersion == null ? null : previewVersion.getRendererProtocol(),
                previewVersion == null ? null : json(definition(previewVersion)),
                previewVersion == null ? null : previewVersion.getThumbnailUri());
    }

    private Optional<ResumeLayoutTemplateVersionEntity> previewVersion(ResumeLayoutTemplateEntity template) {
        Optional<ResumeLayoutTemplateVersionEntity> published = templateVersions
                .findFirstByTemplateIdAndStatusOrderByRevisionNoDesc(template.getId(), "PUBLISHED");
        return published.isPresent() || !isDemoTemplate(template)
                ? published
                : templateVersions.findFirstByTemplateIdAndStatusOrderByRevisionNoDesc(template.getId(), "DRAFT");
    }

    private LayoutView view(ResumeLayoutInstanceEntity value) {
        return view(value, value.getStatus(), readTree(value.getOverflowJson()));
    }

    private LayoutView currentView(ResumeLayoutInstanceEntity value, ResumeMasterEntity master) {
        if (!"VALID".equals(value.getStatus()) && !"OVERFLOW".equals(value.getStatus())) {
            return view(value);
        }
        ResumeLayoutTemplateVersionEntity version = templateVersions.findById(value.getTemplateVersionId())
                .orElseThrow(() -> AppException.conflict(
                        "RESUME_TEMPLATE_VERSION_NOT_FOUND", "当前版式绑定的模板版本不存在"));
        ResumeLayoutDefinition base = definition(version);
        ResumeLayoutTemplateEntity template = templates.findById(version.getTemplateId()).orElse(null);
        ResumeDesignSettings design = ResumeDesignSettings.fromJson(readTree(value.getDesignJson()), base,
                value.getVariantCode(), template == null ? "OPTIONAL" : template.getPhotoPolicy(),
                version.getTemplateId());
        ResumeOverflowEngine.Report report = ResumePdfPreflight.evaluate(
                documentOf(master), design.applyTo(base), value.getVariantCode(), version.getRendererProtocol());
        return view(value, report.valid() ? "VALID" : "OVERFLOW", mapper.valueToTree(report));
    }

    private LayoutView view(ResumeLayoutInstanceEntity value, String status, JsonNode overflow) {
        ResumeLayoutTemplateVersionEntity version = templateVersions.findById(value.getTemplateVersionId()).orElse(null);
        ResumeLayoutTemplateEntity template = version == null ? null : templates.findById(version.getTemplateId()).orElse(null);
        return new LayoutView(
                value.getId(),
                value.getMasterId(),
                value.getTemplateVersionId(),
                version == null ? null : version.getTemplateId(),
                template == null ? null : template.getDisplayName(),
                value.getVariantCode(),
                version == null ? null : version.getRendererProtocol(),
                version == null ? null : json(definition(version)),
                readTree(value.getDesignJson()),
                status,
                overflow,
                value.getVersionNo(),
                value.getFrozenAt());
    }

    private ResumeMasterEntity requireOwnMaster(String accountId, String id) {
        ResumeMasterEntity value = masters.findById(id).orElseThrow(() -> AppException.user("RESUME_NOT_FOUND", "简历不存在"));
        if (!accountId.equals(value.getAccountId())) throw AppException.forbidden("RESUME_FORBIDDEN", "不能访问他人简历");
        return value;
    }

    private ResumeLayoutInstanceEntity requireOwnLayout(String accountId, String id) {
        ResumeLayoutInstanceEntity value = layouts.findById(id)
                .orElseThrow(() -> AppException.user("RESUME_LAYOUT_NOT_FOUND", "版式实例不存在"));
        if (!accountId.equals(value.getAccountId())) throw AppException.forbidden("RESUME_LAYOUT_FORBIDDEN", "不能访问他人版式实例");
        return value;
    }

    private ResumeLayoutTemplateEntity requireSelectableTemplate(String id) {
        ResumeLayoutTemplateEntity value = templates.findById(id)
                .orElseThrow(() -> AppException.user("RESUME_TEMPLATE_NOT_FOUND", "模板不存在"));
        if (!isCatalogVisible(value)) {
            throw AppException.conflict("RESUME_TEMPLATE_UNAVAILABLE", "模板尚未发布或已下架");
        }
        return value;
    }

    private ResumeLayoutTemplateVersionEntity requireSelectableVersion(String templateId) {
        ResumeLayoutTemplateEntity template = requireSelectableTemplate(templateId);
        return templateVersions.findFirstByTemplateIdAndStatusOrderByRevisionNoDesc(templateId, "PUBLISHED")
                .or(() -> isDemoTemplate(template)
                        ? templateVersions.findFirstByTemplateIdAndStatusOrderByRevisionNoDesc(templateId, "DRAFT")
                        : java.util.Optional.empty())
                .orElseThrow(() -> AppException.conflict("RESUME_TEMPLATE_UNAVAILABLE", "模板没有可用的已发布版本"));
    }

    private void assertVariant(String templateId, String variant) {
        if (!readStrings(requireSelectableTemplate(templateId).getVariantsJson()).contains(variant))
            throw AppException.user("RESUME_TEMPLATE_VARIANT_UNKNOWN", "模板变体不存在");
    }

    private boolean isCatalogVisible(ResumeLayoutTemplateEntity value) {
        return "PUBLISHED".equals(value.getStatus()) || isDemoTemplate(value);
    }

    private boolean isDemoTemplate(ResumeLayoutTemplateEntity value) {
        return demoEnabled
                && DOCX_TEMPLATE_IDS.contains(value.getId())
                && "DRAFT".equals(value.getStatus());
    }

    private boolean matches(ResumeLayoutTemplateEntity value, TemplateCatalogQuery query) {
        String keyword = normalize(query.getKeyword());
        if (!keyword.isEmpty()) {
            String haystack = normalize(value.getId() + " " + value.getDisplayName() + " "
                    + value.getFamilyName() + " " + String.join(" ", readStrings(value.getTagsJson())));
            if (!haystack.contains(keyword)) return false;
        }
        if (!contains(value.getFamilyName(), query.getFamily())) return false;
        if (!containsAny(readStrings(value.getTagsJson()), query.getTag())) return false;
        if (!equalsIgnoreCase(value.getLanguageCode(), query.getLanguage())) return false;
        if (!equalsIgnoreCase(value.getRecommendedPages(), query.getPages())) return false;
        if (!equalsIgnoreCase(value.getPhotoPolicy(), query.getPhotoPolicy())) return false;
        return equalsIgnoreCase(value.getAtsCandidateLevel(), query.getAtsLevel());
    }

    private static boolean contains(String value, String expected) {
        return expected == null || expected.isBlank() || normalize(value).contains(normalize(expected));
    }

    private static boolean containsAny(List<String> values, String expected) {
        return expected == null || expected.isBlank()
                || values.stream().anyMatch(value -> normalize(value).contains(normalize(expected)));
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return expected == null || expected.isBlank() || normalize(value).equals(normalize(expected));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private ResumeDocumentModel documentOf(ResumeMasterEntity value) {
        JsonNode canonical = readTree(value.getContentJson());
        if (canonical != null && canonical.isObject()) {
            return new ResumeDocumentModel(ResumeStructuredContent.header(canonical), List.of(
                    structuredSection(canonical, "summary"), structuredSection(canonical, "education"),
                    structuredSection(canonical, "experience"), structuredSection(canonical, "projects"),
                    structuredSection(canonical, "organizations"), structuredSection(canonical, "skills"),
                    structuredSection(canonical, "certificates"), structuredSection(canonical, "honors"),
                    structuredSection(canonical, "languages")));
        }
        return new ResumeDocumentModel(List.of(
                section("education", value.getEducationJson()), section("experience", value.getExperienceJson()),
                section("projects", value.getProjectsJson()), section("skills", value.getSkillsJson()),
                section("certificates", value.getCertificatesJson()), section("summary", value.getSelfIntro())));
    }

    private ResumeDocumentModel.Section structuredSection(JsonNode content, String key) {
        String text = ResumeStructuredContent.text(content, key);
        return new ResumeDocumentModel.Section(key, text, ResumeStructuredContent.itemCount(content, key),
                ResumeStructuredContent.entries(content, key, "YYYY_DOT_MM"));
    }

    private ResumeDocumentModel.Section section(String key, String content) {
        String text = content == null ? "" : content;
        return new ResumeDocumentModel.Section(key, text, text.isBlank() ? 0 : Math.max(1, text.split("\\n").length));
    }

    private ResumeLayoutDefinition definition(ResumeLayoutTemplateVersionEntity version) {
        try {
            return ResumeLayoutProtocol.validate(
                    version.getRendererProtocol(),
                    mapper.readValue(version.getDefinitionJson(), ResumeLayoutDefinition.class));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw AppException.conflict("RESUME_TEMPLATE_DEFINITION_INVALID", "模板版式定义无法读取");
        }
    }

    private List<String> readStrings(String json) {
        try { return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, String.class)); }
        catch (JsonProcessingException exception) { return List.of(); }
    }

    private JsonNode readTree(String json) {
        try { return mapper.readTree(json == null ? "null" : json); }
        catch (JsonProcessingException exception) { return mapper.nullNode(); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw AppException.conflict("RESUME_TEMPLATE_SERIALIZATION_FAILED", "模板快照无法序列化"); }
    }

    private boolean isOverflowFree(String json) { return readTree(json).path("valid").asBoolean(false); }

    private Optional<ResumeLayoutInstanceEntity> editableLayout(String accountId, String masterId) {
        return layouts.findFirstByAccountIdAndMasterIdAndStatusInOrderByUpdatedAtDescCreatedAtDescIdDesc(
                accountId, masterId, EDITABLE_LAYOUT_STATUSES);
    }

    private Optional<ResumeLayoutInstanceEntity> currentLayoutEntity(String accountId, String masterId) {
        Optional<ResumeLayoutInstanceEntity> editable = editableLayout(accountId, masterId);
        return editable.isPresent() ? editable
                : layouts.findFirstByAccountIdAndMasterIdOrderByUpdatedAtDescCreatedAtDescIdDesc(
                        accountId, masterId);
    }

    private static void assertUser(CurrentAccount current) {
        if (current.operator()) throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能查看或编辑用户简历原文");
    }

    public static final class TemplateCatalogQuery extends PageQuery {
        private String keyword;
        private String family;
        private String tag;
        private String language;
        private String pages;
        private String photoPolicy;
        private String atsLevel;

        public String getKeyword() { return keyword; }
        public void setKeyword(String value) { keyword = value; }
        public String getFamily() { return family; }
        public void setFamily(String value) { family = value; }
        public String getTag() { return tag; }
        public void setTag(String value) { tag = value; }
        public String getLanguage() { return language; }
        public void setLanguage(String value) { language = value; }
        public String getPages() { return pages; }
        public void setPages(String value) { pages = value; }
        public String getPhotoPolicy() { return photoPolicy; }
        public void setPhotoPolicy(String value) { photoPolicy = value; }
        public String getAtsLevel() { return atsLevel; }
        public void setAtsLevel(String value) { atsLevel = value; }
    }

    public record PreviewCommand(String masterId, String variantCode) {}
    public record ApplyCommand(String masterId, String variantCode, Integer expectedVersion) {}
    public record TemplateSummaryView(String id, String displayName, String familyName, String languageCode,
            String recommendedPages, String atsCandidateLevel, String photoPolicy, List<String> variants,
            List<String> tags, String status, String rendererProtocol, String layoutDefinitionJson,
            String thumbnailUri) {}
    public record TemplateDetailView(TemplateSummaryView template, String templateVersionId, int revisionNo,
            String rendererProtocol, String layoutDefinitionJson, List<String> variants, String thumbnailUri,
            String atsNotice, boolean docxAvailable, String docxUnavailableReason) {}
    public record PreviewView(String templateId, String templateVersionId, String variantCode,
            ResumeOverflowEngine.Report overflow, boolean variantValid, boolean docxAvailable,
            String docxUnavailableReason) {}
    public record LayoutView(String id, String masterId, String templateVersionId, String templateId,
            String templateName, String variantCode, String rendererProtocol, String layoutDefinitionJson,
            JsonNode design, String status, JsonNode overflow, int version, Instant frozenAt) {}
    public record CurrentLayoutView(boolean selected, LayoutView layout) {}
}
