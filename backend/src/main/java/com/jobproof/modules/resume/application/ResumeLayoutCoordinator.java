package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeDocumentModel;
import com.jobproof.modules.resume.domain.ResumeDesignSettings;
import com.jobproof.modules.resume.domain.ResumeDesignV2;
import com.jobproof.modules.resume.domain.ResumeGenericLayout;
import com.jobproof.modules.resume.domain.ResumeLayoutDefinition;
import com.jobproof.modules.resume.domain.ResumeLayoutProtocol;
import com.jobproof.modules.resume.domain.ResumeOverflowEngine;
import com.jobproof.modules.resume.domain.ResumePdfRenderer;
import com.jobproof.modules.resume.domain.ResumePdfPreflight;
import com.jobproof.modules.resume.domain.ResumeStructuredContent;
import com.jobproof.modules.resume.domain.ResumeTemplateManifest;
import com.jobproof.modules.resume.infra.ResumeLayoutInstanceEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutInstanceJpaRepository;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionJpaRepository;
import com.jobproof.modules.resume.infra.ResumeVersionEntity;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileEntity;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ResumeLayoutCoordinator {

    private static final Set<String> DOCX_TEMPLATE_IDS = ResumeTemplateService.SMART_TEMPLATE_IDS;
    private static final Set<String> EDITABLE_LAYOUT_STATUSES = Set.of("VALID", "OVERFLOW");

    private final ResumeLayoutInstanceJpaRepository layouts;
    private final ResumeLayoutTemplateVersionJpaRepository templateVersions;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final PrivateFileJpaRepository privateFiles;
    private final ObjectStoragePort storage;
    private final boolean demoEnabled;

    public ResumeLayoutCoordinator(
            ResumeLayoutInstanceJpaRepository layouts,
            ResumeLayoutTemplateVersionJpaRepository templateVersions,
            ObjectMapper mapper,
            ClockPort clock,
            PrivateFileJpaRepository privateFiles,
            ObjectStoragePort storage,
            @Value("${jobproof.templates.demo-enabled:false}") boolean demoEnabled) {
        this.layouts = layouts;
        this.templateVersions = templateVersions;
        this.mapper = mapper;
        this.clock = clock;
        this.privateFiles = privateFiles;
        this.storage = storage;
        this.demoEnabled = demoEnabled;
    }

    /**
     * Freezes the latest editable layout together with its exact content version.
     * Legacy resumes without an applied layout remain valid and return an empty result.
     */
    public Optional<String> freezeCurrentLayout(
            String accountId,
            String masterId,
            ResumeVersionEntity contentVersion) {
        ResumeLayoutInstanceEntity layout = layouts
                .findFirstByAccountIdAndMasterIdAndStatusInOrderByUpdatedAtDescCreatedAtDescIdDesc(
                        accountId, masterId, EDITABLE_LAYOUT_STATUSES)
                .orElse(null);
        if (layout == null) {
            return Optional.empty();
        }

        ResumeLayoutTemplateVersionEntity templateVersion = templateVersions.findById(layout.getTemplateVersionId())
                .orElseThrow(() -> AppException.conflict(
                        "RESUME_TEMPLATE_VERSION_NOT_FOUND", "当前版式绑定的模板版本不存在"));
        if (!isSelectable(templateVersion)) {
            throw AppException.conflict(
                    "RESUME_TEMPLATE_UNAVAILABLE", "当前模板版本已下架或尚未通过发布门禁，请重新选择模板");
        }
        if (ResumeLayoutProtocol.isRenderV4(templateVersion.getRendererProtocol())) {
            return Optional.of(freezeHtmlLayout(layout, templateVersion, contentVersion));
        }

        ResumeLayoutDefinition baseDefinition = definition(templateVersion);
        ResumeDesignSettings design = ResumeDesignSettings.fromJson(readTree(layout.getDesignJson()),
                baseDefinition, layout.getVariantCode(), "OPTIONAL", templateVersion.getTemplateId());
        ResumeLayoutDefinition definition = design.applyTo(baseDefinition);
        ResumeDocumentModel document = documentFromSnapshot(contentVersion.getSnapshotJson(), definition, accountId);
        ResumeOverflowEngine.Report report = ResumePdfPreflight.evaluate(
                document, definition, layout.getVariantCode(), templateVersion.getRendererProtocol());
        if (!report.valid()) {
            ResumeOverflowEngine.Item first = report.items().get(0);
            throw AppException.conflict(
                    "RESUME_TEMPLATE_OVERFLOW",
                    "第 " + first.page() + " 页的 " + first.slotKey()
                            + " 区域超出容量 " + first.excessUnits() + " 单位，请精简内容或更换模板");
        }
        try {
            ResumePdfRenderer.render("简历", document, definition, layout.getVariantCode(),
                    templateVersion.getRendererProtocol());
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("maxPages=")) {
                throw AppException.conflict(
                        "RESUME_TEMPLATE_OVERFLOW",
                        "当前内容在实际 A4 排版中超过模板的 " + definition.page().maxPages()
                                + " 页上限，请精简内容、调整设计或更换模板");
            }
            throw exception;
        }

        Instant now = clock.now();
        layout.setContentVersionId(contentVersion.getId());
        layout.setContentSnapshotJson(contentVersion.getSnapshotJson());
        layout.setTemplateSnapshotJson(json(Map.of(
                "id", templateVersion.getId(),
                "templateId", templateVersion.getTemplateId(),
                "revisionNo", templateVersion.getRevisionNo(),
                "rendererProtocol", templateVersion.getRendererProtocol(),
                "variantCode", layout.getVariantCode(),
                "designSchemaVersion", ResumeDesignSettings.SCHEMA,
                "design", mapper.valueToTree(design),
                "definition", readTree(templateVersion.getDefinitionJson()))));
        layout.setOverflowJson(json(report));
        layout.setStatus("FROZEN");
        layout.setFrozenAt(now);
        layout.setUpdatedAt(now);
        layout.setVersionNo(layout.getVersionNo() + 1);
        layouts.save(layout);
        return Optional.of(layout.getId());
    }

    /**
     * Built-in HTML templates are measured where they render (workbench preview, renderer service);
     * the page limit is enforced when the PDF is rendered, so freezing only snapshots the design.
     */
    private String freezeHtmlLayout(ResumeLayoutInstanceEntity layout,
            ResumeLayoutTemplateVersionEntity templateVersion, ResumeVersionEntity contentVersion) {
        ResumeTemplateManifest manifest = manifest(readTree(templateVersion.getDefinitionJson()),
                "RESUME_TEMPLATE_DEFINITION_INVALID", "模板定义无法读取");
        ResumeDesignV2 design = ResumeDesignV2.coerce(manifest, readTree(layout.getDesignJson()));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", templateVersion.getId());
        snapshot.put("templateId", templateVersion.getTemplateId());
        snapshot.put("revisionNo", templateVersion.getRevisionNo());
        snapshot.put("rendererProtocol", templateVersion.getRendererProtocol());
        snapshot.put("variantCode", layout.getVariantCode());
        snapshot.put("designSchemaVersion", ResumeDesignV2.SCHEMA);
        snapshot.put("design", mapper.valueToTree(design));
        snapshot.put("definition", readTree(templateVersion.getDefinitionJson()));
        Instant now = clock.now();
        layout.setContentVersionId(contentVersion.getId());
        layout.setContentSnapshotJson(contentVersion.getSnapshotJson());
        layout.setTemplateSnapshotJson(json(snapshot));
        layout.setDesignSchemaVersion(ResumeDesignV2.SCHEMA);
        layout.setDesignJson(json(design));
        layout.setOverflowJson(ResumeTemplateService.CLIENT_MEASURED_JSON);
        layout.setStatus("FROZEN");
        layout.setFrozenAt(now);
        layout.setUpdatedAt(now);
        layout.setVersionNo(layout.getVersionNo() + 1);
        layouts.save(layout);
        return layout.getId();
    }

    public Optional<RenderContext> renderContext(String accountId, ResumeVersionEntity contentVersion) {
        if (contentVersion.getLayoutInstanceId() == null || contentVersion.getLayoutInstanceId().isBlank()) {
            return Optional.empty();
        }
        ResumeLayoutInstanceEntity layout = layouts.findById(contentVersion.getLayoutInstanceId())
                .orElseThrow(() -> AppException.conflict(
                        "RESUME_LAYOUT_SNAPSHOT_MISSING", "冻结版本关联的版式快照不存在"));
        if (!accountId.equals(layout.getAccountId())
                || !contentVersion.getId().equals(layout.getContentVersionId())
                || !"FROZEN".equals(layout.getStatus())) {
            throw AppException.conflict(
                    "RESUME_LAYOUT_SNAPSHOT_INVALID", "冻结版本与版式快照不一致，已阻止导出");
        }
        JsonNode overflow = readTree(layout.getOverflowJson());
        if (!overflow.path("valid").asBoolean(false)) {
            throw AppException.conflict("RESUME_TEMPLATE_OVERFLOW", "冻结版式存在溢出记录，已阻止导出");
        }
        JsonNode templateSnapshot = readTree(layout.getTemplateSnapshotJson());
        String rendererProtocol = templateSnapshot.path("rendererProtocol")
                .asText(ResumeLayoutProtocol.V1);
        if (ResumeLayoutProtocol.isRenderV4(rendererProtocol)) {
            return Optional.of(htmlRenderContext(accountId, layout, templateSnapshot));
        }
        return Optional.of(new RenderContext(
                layout.getId(),
                templateSnapshot.path("id").asText(),
                templateSnapshot.path("templateId").asText(),
                documentFromSnapshot(layout.getContentSnapshotJson(),
                        designFromSnapshot(templateSnapshot, rendererProtocol), accountId),
                designFromSnapshot(templateSnapshot, rendererProtocol),
                rendererProtocol,
                layout.getVariantCode(),
                null));
    }

    /**
     * What a PDF export of the current editable layout would render, without freezing anything: the
     * export dialog shows its first page from the renderer service (EXP-02).
     */
    public Optional<RenderContext> previewContext(String accountId, String masterId, String contentSnapshotJson) {
        ResumeLayoutInstanceEntity layout = layouts
                .findFirstByAccountIdAndMasterIdAndStatusInOrderByUpdatedAtDescCreatedAtDescIdDesc(
                        accountId, masterId, EDITABLE_LAYOUT_STATUSES)
                .orElse(null);
        if (layout == null) return Optional.empty();
        ResumeLayoutTemplateVersionEntity version = templateVersions.findById(layout.getTemplateVersionId()).orElse(null);
        if (version == null || !ResumeLayoutProtocol.isRenderV4(version.getRendererProtocol())) return Optional.empty();
        com.fasterxml.jackson.databind.node.ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("id", version.getId());
        snapshot.put("templateId", version.getTemplateId());
        snapshot.set("definition", readTree(version.getDefinitionJson()));
        snapshot.set("design", readTree(layout.getDesignJson()));
        return Optional.of(htmlRenderContext(accountId, layout.getId(), layout.getVariantCode(), contentSnapshotJson,
                snapshot));
    }

    private RenderContext htmlRenderContext(String accountId, ResumeLayoutInstanceEntity layout,
            JsonNode templateSnapshot) {
        return htmlRenderContext(accountId, layout.getId(), layout.getVariantCode(), layout.getContentSnapshotJson(),
                templateSnapshot);
    }

    private RenderContext htmlRenderContext(String accountId, String layoutId, String variantCode,
            String contentSnapshotJson, JsonNode templateSnapshot) {
        ResumeTemplateManifest manifest = manifest(templateSnapshot.path("definition"),
                "RESUME_TEMPLATE_SNAPSHOT_INVALID", "冻结模板快照中的模板定义无法读取");
        ResumeDesignV2 design = ResumeDesignV2.coerce(manifest, templateSnapshot.path("design"));
        ResumeLayoutDefinition generic = ResumeGenericLayout.definition(manifest, design);
        ResumeDocumentModel document = documentFromSnapshot(contentSnapshotJson, generic, accountId);
        ResumeDocumentModel.Photo photo = document.header().photo();
        String photoDataUrl = photo == null || !manifest.photoAllowed() || "HIDE".equals(design.photo().mode())
                ? null
                : "data:" + photo.contentType() + ";base64," + Base64.getEncoder().encodeToString(photo.bytes());
        return new RenderContext(
                layoutId,
                templateSnapshot.path("id").asText(),
                templateSnapshot.path("templateId").asText(),
                document,
                generic,
                ResumeLayoutProtocol.V4,
                variantCode,
                new HtmlTemplate(manifest, design, renderContent(readTree(contentSnapshotJson)),
                        photoDataUrl, design.pageLimit(manifest)));
    }

    /** The structured content the HTML templates read; text-only legacy snapshots become plain entries. */
    private JsonNode renderContent(JsonNode root) {
        JsonNode canonical = root.path("content");
        if (canonical.isObject() && canonical.size() > 0) {
            com.fasterxml.jackson.databind.node.ObjectNode copy = ((com.fasterxml.jackson.databind.node.ObjectNode) canonical).deepCopy();
            copy.remove("photoFileId");
            return copy;
        }
        com.fasterxml.jackson.databind.node.ObjectNode content = mapper.createObjectNode();
        content.putObject("basics");
        content.put("summary", storedText(root.path("selfIntro")));
        for (Map.Entry<String, String> field : Map.of("experience", "experiences", "education", "education",
                "projects", "projects", "skills", "skills", "certificates", "certificates").entrySet()) {
            String text = storedText(root.path(field.getKey()));
            if (!text.isBlank()) content.putArray(field.getValue()).add(text);
        }
        return content;
    }

    private String storedText(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) return "";
        return value.isTextual() ? decodeStoredText(value.asText()) : value.toString();
    }

    private ResumeTemplateManifest manifest(JsonNode node, String code, String message) {
        try {
            return ResumeTemplateManifest.parse(mapper, node);
        } catch (IllegalArgumentException exception) {
            throw AppException.conflict(code, message);
        }
    }

    public void assertDocxAvailable(String templateVersionId) {
        ResumeLayoutTemplateVersionEntity version = templateVersions.findById(templateVersionId)
                .orElseThrow(() -> AppException.conflict(
                        "RESUME_TEMPLATE_VERSION_NOT_FOUND", "冻结版式绑定的模板版本不存在"));
        // Built-in templates export the generic ATS Word layout; a version frozen before its template was
        // retired still exports the content the user froze.
        if (ResumeLayoutProtocol.isRenderV4(version.getRendererProtocol())) return;
        if (!DOCX_TEMPLATE_IDS.contains(version.getTemplateId())) {
            throw AppException.conflict("RESUME_DOCX_TEMPLATE_NOT_ENABLED", "该智能模板尚未开放 DOCX 导出");
        }
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw AppException.conflict("RESUME_DOCX_TEMPLATE_NOT_PUBLISHED", "模板版本已下架或尚未发布");
        }
        if (!version.isAuthorizationVerified() || !version.isSecurityVerified()
                || !version.isRenderVerified() || !version.isWordVerified()
                || !version.isWpsVerified() || !version.isAtsVerified()) {
            throw AppException.conflict("RESUME_DOCX_EVIDENCE_INCOMPLETE",
                    "模板版本尚未完成授权、安全、渲染、Word、WPS 与 ATS 全部门禁");
        }
    }

    private boolean isSelectable(ResumeLayoutTemplateVersionEntity version) {
        return "PUBLISHED".equals(version.getStatus())
                || (demoEnabled
                        && DOCX_TEMPLATE_IDS.contains(version.getTemplateId())
                        && "DRAFT".equals(version.getStatus()));
    }

    private ResumeLayoutDefinition designFromSnapshot(JsonNode templateSnapshot, String rendererProtocol) {
        ResumeLayoutDefinition definition = definition(templateSnapshot.path("definition"), rendererProtocol);
        JsonNode designNode = templateSnapshot.path("design");
        if (designNode.isMissingNode() || designNode.isNull()) return definition;
        return ResumeDesignSettings.fromJson(designNode, definition,
                templateSnapshot.path("variantCode").asText("DEFAULT"), "OPTIONAL",
                templateSnapshot.path("templateId").asText(null)).applyTo(definition);
    }

    private ResumeDocumentModel documentFromSnapshot(
            String snapshotJson, ResumeLayoutDefinition definition, String accountId) {
        JsonNode root = readTree(snapshotJson);
        JsonNode canonical = root.path("content");
        if (canonical.isObject() && canonical.size() > 0) {
            String dateFormat = definition.tokens() == null ? "YYYY_DOT_MM"
                    : definition.tokens().getOrDefault("dateFormat", "YYYY_DOT_MM");
            ResumeDocumentModel.Header header = headerWithPhoto(
                    ResumeStructuredContent.header(canonical), canonical, definition, accountId);
            return new ResumeDocumentModel(header, List.of(
                    structuredSection(canonical, "summary", dateFormat), structuredSection(canonical, "education", dateFormat),
                    structuredSection(canonical, "experience", dateFormat), structuredSection(canonical, "projects", dateFormat),
                    structuredSection(canonical, "organizations", dateFormat), structuredSection(canonical, "skills", dateFormat),
                    structuredSection(canonical, "certificates", dateFormat), structuredSection(canonical, "honors", dateFormat),
                    structuredSection(canonical, "languages", dateFormat)));
        }
        return new ResumeDocumentModel(List.of(
                section("education", root.path("education")),
                section("experience", root.path("experience")),
                section("projects", root.path("projects")),
                section("skills", root.path("skills")),
                section("certificates", root.path("certificates")),
                section("summary", root.path("selfIntro"))));
    }

    private ResumeDocumentModel.Header headerWithPhoto(
            ResumeDocumentModel.Header header,
            JsonNode canonical,
            ResumeLayoutDefinition definition,
            String accountId) {
        String photoMode = definition.tokens() == null ? "AUTO"
                : definition.tokens().getOrDefault("photoMode", "AUTO");
        String fileId = canonical.path("photoFileId").asText("").trim();
        if (fileId.isEmpty() || "HIDE".equalsIgnoreCase(photoMode)) return header;
        PrivateFileEntity file = privateFiles.findById(fileId)
                .filter(value -> accountId.equals(value.getOwnerId()))
                .orElseThrow(() -> AppException.conflict(
                        "RESUME_PHOTO_UNAVAILABLE", "冻结简历引用的照片不存在或不属于当前账号"));
        try {
            return header.withPhoto(new ResumeDocumentModel.Photo(
                    file.getContentType(), storage.get(file.getObjectKey())));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw AppException.conflict("RESUME_PHOTO_UNAVAILABLE", "冻结简历引用的照片无法安全读取");
        }
    }

    private ResumeDocumentModel.Section structuredSection(JsonNode content, String key, String dateFormat) {
        String text = ResumeStructuredContent.text(content, key, dateFormat);
        return new ResumeDocumentModel.Section(key, text, ResumeStructuredContent.itemCount(content, key),
                ResumeStructuredContent.entries(content, key, dateFormat));
    }

    private ResumeDocumentModel.Section section(String key, JsonNode value) {
        String text;
        if (value == null || value.isMissingNode() || value.isNull()) {
            text = "";
        } else if (value.isTextual()) {
            text = decodeStoredText(value.asText());
        } else {
            text = value.toString();
        }
        int lines = text.isBlank() ? 0 : Math.max(1, text.split("\\R", -1).length);
        return new ResumeDocumentModel.Section(key, text, lines);
    }

    private String decodeStoredText(String value) {
        try {
            JsonNode nested = mapper.readTree(value);
            return nested.isTextual() ? nested.asText() : value;
        } catch (JsonProcessingException ignored) {
            return value;
        }
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

    private ResumeLayoutDefinition definition(JsonNode value, String rendererProtocol) {
        try {
            return ResumeLayoutProtocol.validate(
                    rendererProtocol,
                    mapper.treeToValue(value, ResumeLayoutDefinition.class));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw AppException.conflict("RESUME_TEMPLATE_SNAPSHOT_INVALID", "冻结模板快照中的版式定义无法读取");
        }
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json == null ? "null" : json);
        } catch (JsonProcessingException exception) {
            throw AppException.conflict("RESUME_TEMPLATE_SNAPSHOT_INVALID", "模板快照无法读取");
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw AppException.conflict("RESUME_TEMPLATE_SERIALIZATION_FAILED", "模板快照无法序列化");
        }
    }

    /**
     * @param definition the v1–v3 layout, or for HTML templates the generic layout used for DOCX
     * @param html set for built-in HTML templates (resume-render-v4), rendered by the renderer service
     */
    public record RenderContext(
            String layoutInstanceId,
            String templateVersionId,
            String templateId,
            ResumeDocumentModel document,
            ResumeLayoutDefinition definition,
            String rendererProtocol,
            String variantCode,
            HtmlTemplate html) {

        /** Protocol {@link #definition} conforms to. */
        public String definitionProtocol() {
            return html == null ? rendererProtocol : ResumeLayoutProtocol.V2;
        }
    }

    public record HtmlTemplate(
            ResumeTemplateManifest manifest,
            ResumeDesignV2 design,
            JsonNode content,
            String photoDataUrl,
            int pageLimit) {
    }
}
