package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.resume.domain.ResumeLayoutDefinition;
import com.jobproof.modules.resume.domain.ResumeLayoutProtocol;
import com.jobproof.modules.resume.domain.ResumeTemplateDocxInspector;
import com.jobproof.modules.resume.domain.ResumeTemplateEvidenceInspector;
import com.jobproof.modules.resume.application.TemplateAssetMalwareScanner.ScanResult;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateJpaRepository;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateEvidenceEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateEvidenceJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateSlotEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateSlotJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateTestRunEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateTestRunJpaRepository;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ResumeTemplateAdminService {
    private static final Logger log = LoggerFactory.getLogger(ResumeTemplateAdminService.class);
    private static final Set<String> REQUIRED_GATES = Set.of(
            "AUTHORIZATION", "SECURITY", "RENDER", "WORD", "WPS", "ATS");
    private static final Set<String> LICENSE_STATUSES = Set.of(
            "UNCONFIRMED", "APPROVED", "REJECTED", "INDEPENDENT_DESIGN");
    private static final Set<String> EVIDENCE_TYPES = Set.of(
            "LICENSE", "INDEPENDENT_DESIGN", "SECURITY_SCAN", "RENDER_TEST",
            "WORD_TEST", "WPS_TEST", "ATS_TEST");
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final byte[] MALWARE_SCANNER_PROBE =
            "jobproof-template-malware-scanner-readiness-v1".getBytes(StandardCharsets.US_ASCII);

    private final ResumeTemplateAssetJpaRepository assets;
    private final ResumeTemplateEvidenceJpaRepository evidenceArtifacts;
    private final ResumeLayoutTemplateJpaRepository templates;
    private final ResumeLayoutTemplateVersionJpaRepository versions;
    private final ResumeTemplateTestRunJpaRepository testRuns;
    private final ResumeTemplateSlotJpaRepository slots;
    private final ObjectStoragePort storage;
    private final TemplateAssetMalwareScanner malwareScanner;
    private final AuditService auditService;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final ResumeSmartTemplateCatalogPublisher smartCatalogPublisher;

    public ResumeTemplateAdminService(ResumeTemplateAssetJpaRepository assets,
            ResumeTemplateEvidenceJpaRepository evidenceArtifacts,
            ResumeLayoutTemplateJpaRepository templates,
            ResumeLayoutTemplateVersionJpaRepository versions,
            ResumeTemplateTestRunJpaRepository testRuns,
            ResumeTemplateSlotJpaRepository slots,
            ObjectStoragePort storage,
            TemplateAssetMalwareScanner malwareScanner,
            AuditService auditService,
            ObjectMapper mapper, ClockPort clock,
            ResumeSmartTemplateCatalogPublisher smartCatalogPublisher) {
        this.assets = assets;
        this.evidenceArtifacts = evidenceArtifacts;
        this.templates = templates;
        this.versions = versions;
        this.testRuns = testRuns;
        this.slots = slots;
        this.storage = storage;
        this.malwareScanner = malwareScanner;
        this.auditService = auditService;
        this.mapper = mapper;
        this.clock = clock;
        this.smartCatalogPublisher = smartCatalogPublisher;
    }

    @Transactional(readOnly = true)
    public PageResult<AssetView> assets(CurrentAccount current, PageQuery query) {
        assertAdmin(current);
        Page<ResumeTemplateAssetEntity> page = assets.findAllByOrderByCreatedAtDesc(
                PageRequest.of(query.page(), query.size()));
        return new PageResult<>(page.getContent().stream().map(this::assetView).toList(),
                page.getTotalElements(), query.page(), query.size());
    }

    @Transactional(readOnly = true)
    public List<TemplateAdminView> templates(CurrentAccount current) {
        assertAdmin(current);
        return templates.findAllByOrderByDisplayNameAsc().stream().map(value -> new TemplateAdminView(
                value.getId(), value.getDisplayName(), value.getFamilyName(), value.getLanguageCode(),
                value.getRecommendedPages(), value.getAtsCandidateLevel(), value.getPhotoPolicy(),
                value.getStatus(), value.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public ScannerStatusView malwareScannerStatus(CurrentAccount current) {
        assertAdmin(current);
        ScanResult result;
        try {
            result = malwareScanner.scan(MALWARE_SCANNER_PROBE);
            if (result == null || result.outcome() == null) {
                result = ScanResult.error("UNKNOWN", null, "SCANNER_INTERNAL_ERROR");
            }
        } catch (RuntimeException exception) {
            log.warn("template asset malware scanner readiness check failed", exception);
            result = ScanResult.error("UNKNOWN", null, "SCANNER_INTERNAL_ERROR");
        }
        return new ScannerStatusView(
                result.outcome() == TemplateAssetMalwareScanner.Outcome.CLEAN,
                result.outcome().name(), result.engine(), result.engineVersion(), result.detailCode(), clock.now());
    }

    @Transactional(readOnly = true)
    public PageResult<EvidenceView> evidence(CurrentAccount current, PageQuery query) {
        assertAdmin(current);
        Page<ResumeTemplateEvidenceEntity> page = evidenceArtifacts.findAllByOrderByCreatedAtDesc(
                PageRequest.of(query.page(), query.size()));
        return new PageResult<>(page.getContent().stream().map(this::evidenceView).toList(),
                page.getTotalElements(), query.page(), query.size());
    }

    @Transactional
    public EvidenceView uploadEvidence(CurrentAccount current, UploadEvidenceCommand command) {
        assertAdmin(current);
        String evidenceType = normalizedEvidenceType(command.evidenceType());
        String description = blankToNull(command.description());
        if (description == null || description.length() > 512)
            throw AppException.user("RESUME_TEMPLATE_EVIDENCE_DESCRIPTION_INVALID", "证据说明必填且不能超过 512 字符");
        ResumeTemplateEvidenceInspector.Inspection inspection =
                ResumeTemplateEvidenceInspector.inspect(command.content());
        if (!inspection.accepted())
            throw AppException.user("RESUME_TEMPLATE_EVIDENCE_FILE_REJECTED",
                    "证据文件仅支持 PDF、PNG、JPEG 或 UTF-8 文本，原因=" + inspection.rejectionCode());
        String fileHash = ResumeTemplateDocxInspector.sha256(command.content());
        if (evidenceArtifacts.findByFileHash(fileHash).isPresent())
            throw AppException.conflict("RESUME_TEMPLATE_EVIDENCE_DUPLICATE", "相同指纹的不可变证据已存在");

        ResumeTemplateEvidenceEntity evidence = new ResumeTemplateEvidenceEntity();
        Instant now = clock.now();
        evidence.setId(Ids.newId());
        evidence.setEvidenceType(evidenceType);
        evidence.setOriginalFilename(safeFilename(command.originalFilename(), "evidence." + inspection.extension()));
        evidence.setContentType(inspection.contentType());
        evidence.setSizeBytes(command.content().length);
        evidence.setFileHash(fileHash);
        evidence.setDescription(description);
        evidence.setUploadedBy(current.accountId());
        evidence.setCreatedAt(now);
        String objectKey = "template-evidence/" + evidence.getId() + "/" + fileHash + "." + inspection.extension();
        storage.put(objectKey, command.content());
        registerRollbackCleanup(objectKey);
        evidence.setStorageKey(objectKey);
        evidenceArtifacts.save(evidence);
        auditService.append(current.accountId(), "TEMPLATE_EVIDENCE_UPLOADED",
                "RESUME_TEMPLATE_EVIDENCE", evidence.getId(),
                "type=" + evidenceType + " hash=" + fileHash.substring(0, 12)
                        + " bytes=" + command.content().length);
        return evidenceView(evidence);
    }

    @Transactional(readOnly = true)
    public EvidenceDownload downloadEvidence(CurrentAccount current, String evidenceId) {
        assertAdmin(current);
        ResumeTemplateEvidenceEntity evidence = requireEvidence(evidenceId);
        return new EvidenceDownload(evidence.getOriginalFilename(), evidence.getContentType(),
                storage.get(evidence.getStorageKey()));
    }

    @Transactional
    public AssetView importCandidate(CurrentAccount current, ImportAssetCommand command) {
        assertAdmin(current);
        byte[] content = command.content();
        String originalFilename = command.originalFilename() == null ? "" : command.originalFilename().trim();
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".docx"))
            throw AppException.user("RESUME_TEMPLATE_ASSET_TYPE_UNSUPPORTED", "候选资产必须是 DOCX");
        if (command.sourceUri() == null || command.sourceUri().isBlank() || command.sourceUri().length() > 1024)
            throw AppException.user("RESUME_TEMPLATE_ASSET_SOURCE_REQUIRED", "候选资产必须记录有效来源 URI");
        String licenseStatus = normalizedLicenseStatus(command.licenseStatus());
        ResumeTemplateEvidenceEntity licenseEvidence = validateLicenseEvidence(
                licenseStatus, blankToNull(command.licenseEvidenceId()));
        String fileHash = ResumeTemplateDocxInspector.sha256(content);
        if (assets.findByFileHash(fileHash).isPresent())
            throw AppException.conflict("RESUME_TEMPLATE_ASSET_DUPLICATE", "相同文件指纹的候选资产已存在");
        ResumeTemplateDocxInspector.Inspection inspection = ResumeTemplateDocxInspector.inspect(content);
        ScanResult malwareInspection = scanMalware(inspection, content, fileHash);
        Instant now = clock.now();
        ResumeTemplateAssetEntity asset = new ResumeTemplateAssetEntity();
        asset.setId(Ids.newId());
        asset.setSourceName(sourceName(command.sourceName(), originalFilename));
        asset.setSourceUri(command.sourceUri().trim());
        asset.setLicenseStatus(licenseStatus);
        asset.setLicenseEvidence(null);
        asset.setLicenseEvidenceId(licenseEvidence == null ? null : licenseEvidence.getId());
        asset.setFileHash(fileHash);
        asset.setContentType(DOCX_CONTENT_TYPE);
        asset.setSizeBytes((long) content.length);
        asset.setScanStatus(scanStatus(inspection, malwareInspection));
        asset.setScanReportJson(scanReport(inspection, malwareInspection));
        asset.setScannedAt(now);
        asset.setUploadedBy(current.accountId());
        boolean rejected = !inspection.accepted()
                || malwareInspection.outcome() == TemplateAssetMalwareScanner.Outcome.INFECTED
                || "REJECTED".equals(licenseStatus);
        asset.setStatus(rejected ? "REJECTED" : "REVIEWING");
        asset.setRejectionReason(rejectionReason(inspection, malwareInspection, licenseStatus));
        asset.setVersionNo(0);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);

        if (!rejected && inspection.accepted()) {
            String prefix = malwareInspection.outcome() == TemplateAssetMalwareScanner.Outcome.CLEAN
                    ? "template-assets/" : "template-quarantine/";
            String objectKey = prefix + asset.getId() + "/" + fileHash + ".docx";
            storage.put(objectKey, content);
            registerRollbackCleanup(objectKey);
            asset.setStorageKey(objectKey);
        }
        assets.save(asset);
        String auditAction = rejected ? "TEMPLATE_ASSET_REJECTED"
                : "PASSED".equals(asset.getScanStatus())
                        ? "TEMPLATE_ASSET_IMPORTED" : "TEMPLATE_ASSET_QUARANTINED";
        auditService.append(current.accountId(), auditAction,
                "RESUME_TEMPLATE_ASSET", asset.getId(),
                "candidate hash=" + fileHash.substring(0, 12) + " scan=" + asset.getScanStatus()
                        + " license=" + licenseStatus + " bytes=" + content.length);
        return assetView(asset);
    }

    @Transactional
    public AssetView rescanAsset(CurrentAccount current, String assetId, Integer expectedVersion) {
        assertAdmin(current);
        ResumeTemplateAssetEntity asset = requireAsset(assetId);
        Versions.assertExpected(expectedVersion, asset.getVersionNo());
        if (asset.getStorageKey() == null)
            throw AppException.conflict("RESUME_TEMPLATE_ASSET_RESCAN_UNAVAILABLE",
                    "候选原始文件未保留，不能复扫；请重新导入文件");

        String previousKey = asset.getStorageKey();
        byte[] content = storage.get(previousKey);
        String actualHash = ResumeTemplateDocxInspector.sha256(content);
        Instant now = clock.now();
        if (!asset.getFileHash().equals(actualHash)) {
            asset.setScanStatus("STORAGE_INTEGRITY_FAILED");
            asset.setScanReportJson(json(Map.of(
                    "expectedHash", asset.getFileHash(), "actualHash", actualHash,
                    "detailCode", "STORAGE_HASH_MISMATCH")));
            asset.setStatus("REJECTED");
            asset.setRejectionReason("STORAGE_HASH_MISMATCH");
            asset.setStorageKey(null);
            registerCommitCleanup(previousKey);
        } else {
            ResumeTemplateDocxInspector.Inspection inspection = ResumeTemplateDocxInspector.inspect(content);
            ScanResult malwareInspection = scanMalware(inspection, content, actualHash);
            asset.setScanStatus(scanStatus(inspection, malwareInspection));
            asset.setScanReportJson(scanReport(inspection, malwareInspection));
            if (!inspection.accepted()
                    || malwareInspection.outcome() == TemplateAssetMalwareScanner.Outcome.INFECTED) {
                asset.setStatus("REJECTED");
                asset.setRejectionReason(rejectionReason(inspection, malwareInspection, asset.getLicenseStatus()));
                asset.setStorageKey(null);
                registerCommitCleanup(previousKey);
            } else if (malwareInspection.outcome() == TemplateAssetMalwareScanner.Outcome.CLEAN) {
                String trustedKey = "template-assets/" + asset.getId() + "/" + asset.getFileHash() + ".docx";
                moveStoredObject(previousKey, trustedKey, content);
                asset.setStorageKey(trustedKey);
                asset.setRejectionReason(null);
            } else {
                String quarantineKey = "template-quarantine/" + asset.getId() + "/"
                        + asset.getFileHash() + ".docx";
                moveStoredObject(previousKey, quarantineKey, content);
                asset.setStorageKey(quarantineKey);
                if ("APPROVED".equals(asset.getStatus())) asset.setStatus("REVIEWING");
                asset.setRejectionReason(rejectionReason(inspection, malwareInspection, asset.getLicenseStatus()));
            }
        }
        asset.setScannedAt(now);
        asset.setVersionNo(asset.getVersionNo() + 1);
        asset.setUpdatedAt(now);
        assets.save(asset);
        auditService.append(current.accountId(), "TEMPLATE_ASSET_RESCANNED",
                "RESUME_TEMPLATE_ASSET", asset.getId(),
                "candidate hash=" + asset.getFileHash().substring(0, 12) + " scan=" + asset.getScanStatus());
        return assetView(asset);
    }

    @Transactional
    public AssetView reviewAsset(CurrentAccount current, String assetId,
            ReviewAssetCommand command, Integer expectedVersion) {
        assertAdmin(current);
        ResumeTemplateAssetEntity asset = requireAsset(assetId);
        if (!Set.of("DISCOVERED", "REVIEWING").contains(asset.getStatus()))
            throw AppException.conflict("RESUME_TEMPLATE_ASSET_REVIEW_FINAL", "候选资产已完成审批，不能覆盖既有结论");
        Versions.assertExpected(expectedVersion, asset.getVersionNo());
        String decision = command.decision() == null ? "" : command.decision().trim().toUpperCase(Locale.ROOT);
        Instant now = clock.now();
        if ("APPROVED".equals(decision)) {
            if (!"PASSED".equals(asset.getScanStatus()) || asset.getStorageKey() == null
                    || !asset.getStorageKey().startsWith("template-assets/"))
                throw AppException.conflict("RESUME_TEMPLATE_ASSET_SECURITY_GATE_FAILED",
                        "静态 OOXML 与独立恶意文件扫描必须全部通过后才能批准候选");
            String licenseStatus = normalizedLicenseStatus(command.licenseStatus());
            if (!Set.of("APPROVED", "INDEPENDENT_DESIGN").contains(licenseStatus))
                throw AppException.user("RESUME_TEMPLATE_LICENSE_NOT_APPROVED", "批准候选必须确认商业授权或独立设计权利");
            ResumeTemplateEvidenceEntity licenseEvidence = validateLicenseEvidence(
                    licenseStatus, blankToNull(command.licenseEvidenceId()));
            asset.setLicenseStatus(licenseStatus);
            asset.setLicenseEvidenceId(licenseEvidence.getId());
            asset.setStatus("APPROVED");
            asset.setRejectionReason(null);
            auditService.append(current.accountId(), "TEMPLATE_ASSET_APPROVED",
                    "RESUME_TEMPLATE_ASSET", asset.getId(),
                    "license=" + licenseStatus + " evidence=" + licenseEvidence.getId()
                            + " hash=" + asset.getFileHash().substring(0, 12));
        } else if ("REJECTED".equals(decision)) {
            String reason = blankToNull(command.reason());
            if (reason == null || reason.length() > 1024)
                throw AppException.user("RESUME_TEMPLATE_ASSET_REJECTION_REASON_REQUIRED",
                        "驳回原因必填且不能超过 1024 字符");
            asset.setStatus("REJECTED");
            asset.setLicenseStatus("REJECTED");
            asset.setRejectionReason(reason);
            String storedObjectKey = asset.getStorageKey();
            asset.setStorageKey(null);
            if (storedObjectKey != null) registerCommitCleanup(storedObjectKey);
            auditService.append(current.accountId(), "TEMPLATE_ASSET_REJECTED",
                    "RESUME_TEMPLATE_ASSET", asset.getId(),
                    "reason=" + reason + " hash=" + asset.getFileHash().substring(0, 12));
        } else {
            throw AppException.user("RESUME_TEMPLATE_ASSET_REVIEW_DECISION_UNKNOWN",
                    "decision 仅支持 APPROVED 或 REJECTED");
        }
        asset.setReviewedBy(current.accountId());
        asset.setReviewedAt(now);
        asset.setVersionNo(asset.getVersionNo() + 1);
        asset.setUpdatedAt(now);
        assets.save(asset);
        return assetView(asset);
    }

    @Transactional(readOnly = true)
    public List<VersionView> versions(CurrentAccount current, String templateId) {
        assertAdmin(current); requireTemplate(templateId);
        return versions.findByTemplateIdOrderByRevisionNoDesc(templateId).stream().map(this::versionView).toList();
    }

    @Transactional
    public VersionView createDraft(CurrentAccount current, String templateId, DraftCommand command) {
        assertAdmin(current); ResumeLayoutTemplateEntity template = requireTemplate(templateId);
        if (versions.findByTemplateIdOrderByRevisionNoDesc(templateId).stream()
                .anyMatch(value -> "DRAFT".equals(value.getStatus()) || "TESTING".equals(value.getStatus())))
            throw AppException.conflict("RESUME_TEMPLATE_DRAFT_EXISTS", "模板已有草稿或测试中版本");
        int revision = versions.findByTemplateIdOrderByRevisionNoDesc(templateId).stream()
                .mapToInt(ResumeLayoutTemplateVersionEntity::getRevisionNo).max().orElse(0) + 1;
        Provenance provenance = validateProvenance(command.sourceAssetId(), command.independentDesignEvidenceId());
        ResumeLayoutTemplateVersionEntity value = new ResumeLayoutTemplateVersionEntity(); Instant now = clock.now();
        value.setId(Ids.newId()); value.setTemplateId(templateId); value.setRevisionNo(revision); value.setStatus("DRAFT");
        value.setRendererProtocol(command.rendererProtocol()); value.setDefinitionJson(command.definitionJson());
        value.setThumbnailUri(command.thumbnailUri()); value.setSourceAssetId(provenance.sourceAssetId());
        value.setIndependentDesignEvidenceId(provenance.independentDesignEvidenceId());
        value.setVersionNo(0); value.setCreatedAt(now); value.setUpdatedAt(now);
        versions.save(value); syncSlots(value.getId(), command.rendererProtocol(), command.definitionJson());
        template.setUpdatedAt(now); templates.save(template);
        auditService.append(current.accountId(), "TEMPLATE_VERSION_DRAFT_CREATED",
                "RESUME_LAYOUT_TEMPLATE_VERSION", value.getId(),
                "template=" + templateId + " revision=" + revision + " provenance=" + provenance.summary());
        return versionView(value);
    }

    @Transactional
    public VersionView editDraft(CurrentAccount current, String versionId, DraftCommand command, Integer expectedVersion) {
        assertAdmin(current); ResumeLayoutTemplateVersionEntity value = requireVersion(versionId);
        if (!"DRAFT".equals(value.getStatus()))
            throw AppException.conflict("RESUME_TEMPLATE_VERSION_IMMUTABLE", "仅草稿模板版本可编辑；已发布版本不可原地修改");
        Versions.assertExpected(expectedVersion, value.getVersionNo());
        bindOrAssertProvenance(value, command);
        value.setRendererProtocol(command.rendererProtocol()); value.setDefinitionJson(command.definitionJson());
        value.setThumbnailUri(command.thumbnailUri()); value.setVersionNo(value.getVersionNo() + 1); value.setUpdatedAt(clock.now());
        versions.save(value); syncSlots(value.getId(), command.rendererProtocol(), command.definitionJson());
        auditService.append(current.accountId(), "TEMPLATE_VERSION_DRAFT_UPDATED",
                "RESUME_LAYOUT_TEMPLATE_VERSION", value.getId(), "version=" + value.getVersionNo());
        return versionView(value);
    }

    @Transactional
    public VersionView recordTest(CurrentAccount current, String versionId, TestCommand command, Integer expectedVersion) {
        assertAdmin(current); ResumeLayoutTemplateVersionEntity value = requireVersion(versionId);
        if (!"DRAFT".equals(value.getStatus()) && !"TESTING".equals(value.getStatus()))
            throw AppException.conflict("RESUME_TEMPLATE_VERSION_IMMUTABLE", "仅草稿或测试中版本可记录测试结果");
        Versions.assertExpected(expectedVersion, value.getVersionNo());
        validatePersistedProvenance(value);
        String gateCode = normalizedGate(command.gateCode());
        String outcome = normalizedOutcome(command.outcome());
        ResumeTemplateEvidenceEntity evidence = validateGateEvidence(value, gateCode, command.evidenceId());
        if (command.summary() == null || command.summary().isBlank() || command.summary().length() > 2048)
            throw AppException.user("RESUME_TEMPLATE_TEST_REPORT_REQUIRED", "每项门禁必须记录不超过 2048 字符的测试结论摘要");
        String environmentJson = normalizedJson(command.environmentJson(), "测试环境必须是 JSON 对象");
        Instant now = clock.now();
        ResumeTemplateTestRunEntity run = new ResumeTemplateTestRunEntity();
        run.setId(Ids.newId()); run.setTemplateVersionId(versionId); run.setGateCode(gateCode);
        run.setOutcome(outcome); run.setEvidenceArtifactId(evidence.getId());
        run.setEvidenceRef("template-evidence://" + evidence.getId());
        run.setTemplateVersionNo(value.getVersionNo() + 1); run.setExecutedBy(current.accountId());
        run.setEnvironmentJson(environmentJson);
        run.setReportJson(json(Map.of("summary", command.summary().trim())));
        run.setCreatedAt(now); testRuns.save(run);

        GateSnapshot snapshot = gateSnapshot(versionId);
        value.setStatus(snapshot.anyFailed() ? "DRAFT" : "TESTING");
        applyGateSnapshot(value, snapshot);
        value.setTestReportJson(json(Map.of("allPassed", snapshot.allPassed(), "gates", snapshot.gates())));
        value.setVersionNo(value.getVersionNo() + 1); value.setUpdatedAt(clock.now()); versions.save(value);
        auditService.append(current.accountId(), "TEMPLATE_VERSION_TEST_RECORDED",
                "RESUME_LAYOUT_TEMPLATE_VERSION", value.getId(),
                "gate=" + gateCode + " outcome=" + outcome + " evidence=" + evidence.getId());
        return versionView(value);
    }

    @Transactional
    public VersionView publish(CurrentAccount current, String versionId, Integer expectedVersion) {
        assertAdmin(current); ResumeLayoutTemplateVersionEntity value = requireVersion(versionId);
        Versions.assertExpected(expectedVersion, value.getVersionNo());
        if (!"TESTING".equals(value.getStatus()))
            throw AppException.conflict("RESUME_TEMPLATE_NOT_TESTED", "仅完成测试的模板版本可发布");
        validatePersistedProvenance(value);
        GateSnapshot snapshot = gateSnapshot(versionId);
        if (!snapshot.allPassed())
            throw AppException.conflict("RESUME_TEMPLATE_PUBLISH_GATE_FAILED",
                    "授权、安全、渲染、Word、WPS 与 ATS 必须各有一条最新的服务端通过证据");
        if (slots.countByTemplateVersionId(versionId) == 0)
            throw AppException.conflict("RESUME_TEMPLATE_SLOTS_REQUIRED", "模板版本没有受控槽位定义，不能发布");
        applyGateSnapshot(value, snapshot);
        value.setTestReportJson(json(Map.of("allPassed", true, "gates", snapshot.gates())));
        Instant now = clock.now(); value.setStatus("PUBLISHED"); value.setPublishedAt(now);
        value.setVersionNo(value.getVersionNo() + 1); value.setUpdatedAt(now); versions.save(value);
        ResumeLayoutTemplateEntity template = requireTemplate(value.getTemplateId()); template.setStatus("PUBLISHED");
        template.setUpdatedAt(now); templates.save(template);
        auditService.append(current.accountId(), "TEMPLATE_VERSION_PUBLISHED",
                "RESUME_LAYOUT_TEMPLATE_VERSION", value.getId(),
                "template=" + value.getTemplateId() + " revision=" + value.getRevisionNo());
        smartCatalogPublisher.sync(value.getTemplateId());
        return versionView(value);
    }

    @Transactional
    public VersionView retire(CurrentAccount current, String versionId, Integer expectedVersion) {
        assertAdmin(current); ResumeLayoutTemplateVersionEntity value = requireVersion(versionId);
        Versions.assertExpected(expectedVersion, value.getVersionNo());
        if (!"PUBLISHED".equals(value.getStatus()))
            throw AppException.conflict("RESUME_TEMPLATE_NOT_PUBLISHED", "仅已发布版本可以下架");
        Instant now = clock.now(); value.setStatus("RETIRED"); value.setRetiredAt(now);
        value.setVersionNo(value.getVersionNo() + 1); value.setUpdatedAt(now); versions.save(value);
        if (versions.findFirstByTemplateIdAndStatusOrderByRevisionNoDesc(value.getTemplateId(), "PUBLISHED").isEmpty()) {
            ResumeLayoutTemplateEntity template = requireTemplate(value.getTemplateId()); template.setStatus("RETIRED");
            template.setUpdatedAt(now); templates.save(template);
        }
        auditService.append(current.accountId(), "TEMPLATE_VERSION_RETIRED",
                "RESUME_LAYOUT_TEMPLATE_VERSION", value.getId(),
                "template=" + value.getTemplateId() + " revision=" + value.getRevisionNo());
        smartCatalogPublisher.sync(value.getTemplateId());
        return versionView(value);
    }

    private ResumeLayoutTemplateEntity requireTemplate(String id) {
        return templates.findById(id).orElseThrow(() -> AppException.user("RESUME_TEMPLATE_NOT_FOUND", "模板不存在"));
    }
    private ResumeLayoutTemplateVersionEntity requireVersion(String id) {
        return versions.findById(id).orElseThrow(() -> AppException.user("RESUME_TEMPLATE_VERSION_NOT_FOUND", "模板版本不存在"));
    }
    private ResumeTemplateAssetEntity requireAsset(String id) {
        return assets.findById(id).orElseThrow(() -> AppException.user("RESUME_TEMPLATE_ASSET_NOT_FOUND", "候选资产不存在"));
    }
    private ResumeTemplateEvidenceEntity requireEvidence(String id) {
        if (id == null || id.isBlank())
            throw AppException.user("RESUME_TEMPLATE_EVIDENCE_REQUIRED", "必须引用已上传的不可变证据");
        return evidenceArtifacts.findById(id.trim())
                .orElseThrow(() -> AppException.user("RESUME_TEMPLATE_EVIDENCE_NOT_FOUND", "不可变证据不存在"));
    }
    private AssetView assetView(ResumeTemplateAssetEntity value) {
        return new AssetView(value.getId(), value.getSourceName(), value.getSourceUri(), value.getLicenseStatus(),
                value.getLicenseEvidenceId(), value.getFileHash(), value.getContentType(), value.getSizeBytes(),
                value.getScanStatus(), value.getScanReportJson(), value.getStorageKey() != null,
                value.getStatus(), value.getRejectionReason(), value.getUploadedBy(), value.getVersionNo(),
                value.getReviewedBy(), value.getScannedAt(), value.getReviewedAt(), value.getCreatedAt());
    }
    private EvidenceView evidenceView(ResumeTemplateEvidenceEntity value) {
        return new EvidenceView(value.getId(), value.getEvidenceType(), value.getOriginalFilename(),
                value.getContentType(), value.getSizeBytes(), value.getFileHash(), value.getDescription(),
                value.getUploadedBy(), value.getCreatedAt());
    }
    private VersionView versionView(ResumeLayoutTemplateVersionEntity value) {
        return new VersionView(value.getId(), value.getTemplateId(), value.getRevisionNo(), value.getStatus(),
                value.getRendererProtocol(), value.getDefinitionJson(), value.getThumbnailUri(),
                value.getSourceAssetId(), value.getIndependentDesignEvidenceId(), value.getTestReportJson(),
                value.isAuthorizationVerified(), value.isSecurityVerified(), value.isRenderVerified(),
                value.isWordVerified(), value.isWpsVerified(), value.isAtsVerified(), value.getVersionNo(),
                value.getPublishedAt(), value.getRetiredAt());
    }

    private void syncSlots(String templateVersionId, String rendererProtocol, String definitionJson) {
        ResumeLayoutDefinition definition;
        try {
            definition = ResumeLayoutProtocol.validate(
                    rendererProtocol,
                    mapper.readValue(definitionJson, ResumeLayoutDefinition.class));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw AppException.user("RESUME_TEMPLATE_DEFINITION_INVALID", "版式定义必须是受支持的 JSON 文档模型");
        }
        List<ResumeTemplateSlotEntity> next = new java.util.ArrayList<>();
        for (ResumeLayoutDefinition.Slot slot : definition.slots()) {
            ResumeTemplateSlotEntity entity = new ResumeTemplateSlotEntity();
            entity.setId(Ids.newId()); entity.setTemplateVersionId(templateVersionId); entity.setSlotKey(slot.key());
            entity.setDisplayOrder(slot.order()); entity.setCapacityUnits(slot.capacityUnits());
            entity.setRepeatable(slot.repeatable()); entity.setHideWhenEmpty(slot.hideWhenEmpty());
            entity.setOverflowStrategy("BLOCK");
            Map<String, String> slotTokens = new LinkedHashMap<>();
            slotTokens.put("label", slot.effectiveLabel());
            if (slot.headingStyle() != null && !slot.headingStyle().isBlank()) {
                slotTokens.put("headingStyle", slot.headingStyle());
            }
            entity.setTokenJson(json(slotTokens)); next.add(entity);
        }
        slots.deleteByTemplateVersionId(templateVersionId);
        slots.flush();
        slots.saveAll(next);
    }

    private GateSnapshot gateSnapshot(String versionId) {
        Map<String, GateResult> latest = new LinkedHashMap<>();
        for (ResumeTemplateTestRunEntity run :
                testRuns.findByTemplateVersionIdOrderByTemplateVersionNoDesc(versionId)) {
            if (latest.containsKey(run.getGateCode()) || run.getEvidenceArtifactId() == null) continue;
            ResumeTemplateEvidenceEntity evidence = requireEvidence(run.getEvidenceArtifactId());
            latest.put(run.getGateCode(), new GateResult(
                    run.getId(), run.getOutcome(), evidence.getId(), evidence.getFileHash(), run.getExecutedBy(),
                    run.getEnvironmentJson(), run.getReportJson(), run.getCreatedAt()));
        }
        boolean allPassed = REQUIRED_GATES.stream()
                .allMatch(gate -> latest.containsKey(gate) && "PASSED".equals(latest.get(gate).outcome()));
        boolean anyFailed = latest.values().stream().anyMatch(result -> "FAILED".equals(result.outcome()));
        return new GateSnapshot(Map.copyOf(latest), allPassed, anyFailed);
    }

    private static void applyGateSnapshot(ResumeLayoutTemplateVersionEntity value, GateSnapshot snapshot) {
        value.setAuthorizationVerified(snapshot.passed("AUTHORIZATION"));
        value.setSecurityVerified(snapshot.passed("SECURITY"));
        value.setRenderVerified(snapshot.passed("RENDER"));
        value.setWordVerified(snapshot.passed("WORD"));
        value.setWpsVerified(snapshot.passed("WPS"));
        value.setAtsVerified(snapshot.passed("ATS"));
    }

    private Provenance validateProvenance(String sourceAssetId, String independentDesignEvidenceId) {
        String sourceId = blankToNull(sourceAssetId);
        String independentId = blankToNull(independentDesignEvidenceId);
        if ((sourceId == null) == (independentId == null))
            throw AppException.user("RESUME_TEMPLATE_PROVENANCE_REQUIRED",
                    "模板版本必须且只能绑定一个已批准候选资产或独立设计证据");
        if (sourceId != null) {
            ResumeTemplateAssetEntity asset = requireAsset(sourceId);
            if (!"APPROVED".equals(asset.getStatus()) || asset.getLicenseEvidenceId() == null
                    || !"PASSED".equals(asset.getScanStatus())
                    || asset.getStorageKey() == null || !asset.getStorageKey().startsWith("template-assets/"))
                throw AppException.conflict("RESUME_TEMPLATE_SOURCE_ASSET_NOT_APPROVED",
                        "模板版本只能绑定已完成权利与安全审批的候选资产");
            validateLicenseEvidence(asset.getLicenseStatus(), asset.getLicenseEvidenceId());
            return new Provenance(asset.getId(), null, "asset:" + asset.getId());
        }
        ResumeTemplateEvidenceEntity evidence = requireEvidence(independentId);
        if (!"INDEPENDENT_DESIGN".equals(evidence.getEvidenceType()))
            throw AppException.user("RESUME_TEMPLATE_INDEPENDENT_EVIDENCE_TYPE_INVALID",
                    "独立设计来源必须引用 INDEPENDENT_DESIGN 类型证据");
        return new Provenance(null, evidence.getId(), "independent:" + evidence.getId());
    }

    private void validatePersistedProvenance(ResumeLayoutTemplateVersionEntity value) {
        validateProvenance(value.getSourceAssetId(), value.getIndependentDesignEvidenceId());
    }

    private void bindOrAssertProvenance(ResumeLayoutTemplateVersionEntity value, DraftCommand command) {
        String sourceId = blankToNull(command.sourceAssetId());
        String independentId = blankToNull(command.independentDesignEvidenceId());
        if (value.getSourceAssetId() == null && value.getIndependentDesignEvidenceId() == null) {
            Provenance provenance = validateProvenance(sourceId, independentId);
            value.setSourceAssetId(provenance.sourceAssetId());
            value.setIndependentDesignEvidenceId(provenance.independentDesignEvidenceId());
            return;
        }
        if (sourceId == null && independentId == null) return;
        if (!java.util.Objects.equals(sourceId, value.getSourceAssetId())
                || !java.util.Objects.equals(independentId, value.getIndependentDesignEvidenceId()))
            throw AppException.conflict("RESUME_TEMPLATE_PROVENANCE_IMMUTABLE",
                    "模板版本来源绑定不可修改；请创建新修订版本");
    }

    private ResumeTemplateEvidenceEntity validateLicenseEvidence(String licenseStatus, String evidenceId) {
        if (!Set.of("APPROVED", "INDEPENDENT_DESIGN").contains(licenseStatus)) {
            if (evidenceId != null)
                throw AppException.user("RESUME_TEMPLATE_LICENSE_EVIDENCE_NOT_ALLOWED",
                        "未确认或已驳回的授权状态不能绑定生产权利证据");
            return null;
        }
        ResumeTemplateEvidenceEntity evidence = requireEvidence(evidenceId);
        String requiredType = "APPROVED".equals(licenseStatus) ? "LICENSE" : "INDEPENDENT_DESIGN";
        if (!requiredType.equals(evidence.getEvidenceType()))
            throw AppException.user("RESUME_TEMPLATE_LICENSE_EVIDENCE_TYPE_INVALID",
                    "权利状态必须引用匹配类型的不可变证据，要求=" + requiredType);
        return evidence;
    }

    private ResumeTemplateEvidenceEntity validateGateEvidence(
            ResumeLayoutTemplateVersionEntity value, String gateCode, String evidenceId) {
        ResumeTemplateEvidenceEntity evidence = requireEvidence(evidenceId);
        Set<String> allowedTypes = switch (gateCode) {
            case "AUTHORIZATION" -> Set.of("LICENSE", "INDEPENDENT_DESIGN");
            case "SECURITY" -> Set.of("SECURITY_SCAN");
            case "RENDER" -> Set.of("RENDER_TEST");
            case "WORD" -> Set.of("WORD_TEST");
            case "WPS" -> Set.of("WPS_TEST");
            case "ATS" -> Set.of("ATS_TEST");
            default -> Set.of();
        };
        if (!allowedTypes.contains(evidence.getEvidenceType()))
            throw AppException.user("RESUME_TEMPLATE_TEST_EVIDENCE_TYPE_INVALID",
                    gateCode + " 门禁不能引用 " + evidence.getEvidenceType() + " 类型证据");
        if ("AUTHORIZATION".equals(gateCode)) {
            String provenanceEvidenceId = value.getIndependentDesignEvidenceId();
            if (value.getSourceAssetId() != null) {
                provenanceEvidenceId = requireAsset(value.getSourceAssetId()).getLicenseEvidenceId();
            }
            if (!evidence.getId().equals(provenanceEvidenceId))
                throw AppException.conflict("RESUME_TEMPLATE_AUTHORIZATION_EVIDENCE_MISMATCH",
                        "授权门禁必须引用该模板版本来源绑定的权利证据");
        }
        return evidence;
    }

    private static String normalizedGate(String value) {
        String gate = value == null ? "" : value.trim().toUpperCase();
        if (!REQUIRED_GATES.contains(gate))
            throw AppException.user("RESUME_TEMPLATE_TEST_GATE_UNKNOWN",
                    "gateCode 仅支持 AUTHORIZATION、SECURITY、RENDER、WORD、WPS、ATS");
        return gate;
    }

    private static String normalizedOutcome(String value) {
        String outcome = value == null ? "" : value.trim().toUpperCase();
        if (!Set.of("PASSED", "FAILED").contains(outcome))
            throw AppException.user("RESUME_TEMPLATE_TEST_OUTCOME_UNKNOWN", "outcome 仅支持 PASSED 或 FAILED");
        return outcome;
    }

    private static String normalizedEvidenceType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!EVIDENCE_TYPES.contains(type))
            throw AppException.user("RESUME_TEMPLATE_EVIDENCE_TYPE_UNKNOWN",
                    "evidenceType 不在受支持的模板证据类型中");
        return type;
    }

    private String normalizedJson(String value, String message) {
        if (value != null && value.length() > 8192)
            throw AppException.user("RESUME_TEMPLATE_TEST_ENVIRONMENT_INVALID", message);
        try {
            var node = mapper.readTree(value == null || value.isBlank() ? "{}" : value);
            if (!node.isObject()) throw AppException.user("RESUME_TEMPLATE_TEST_ENVIRONMENT_INVALID", message);
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw AppException.user("RESUME_TEMPLATE_TEST_ENVIRONMENT_INVALID", message);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw AppException.conflict("RESUME_TEMPLATE_SERIALIZATION_FAILED", "测试报告无法序列化"); }
    }

    private static String normalizedLicenseStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!LICENSE_STATUSES.contains(status))
            throw AppException.user("RESUME_TEMPLATE_LICENSE_STATUS_UNKNOWN",
                    "licenseStatus 仅支持 UNCONFIRMED、APPROVED、REJECTED 或 INDEPENDENT_DESIGN");
        return status;
    }

    private static String sourceName(String requested, String originalFilename) {
        String value = requested == null || requested.isBlank() ? originalFilename : requested.trim();
        return safeFilename(value, "template.docx");
    }

    private static String safeFilename(String requested, String fallback) {
        String value = requested == null || requested.isBlank() ? fallback : requested.trim();
        value = value.replaceAll("[\\p{Cntrl}]", "");
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        if (value.isBlank()) value = fallback;
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ScanResult scanMalware(ResumeTemplateDocxInspector.Inspection inspection,
            byte[] content, String fileHash) {
        if (!inspection.accepted()) return ScanResult.notRun();
        try {
            ScanResult result = malwareScanner.scan(content);
            if (result != null && result.outcome() != null) return result;
        } catch (RuntimeException exception) {
            log.warn("template asset malware scanner failed hash={}", fileHash.substring(0, 12), exception);
        }
        return ScanResult.error("UNKNOWN", null, "SCANNER_INTERNAL_ERROR");
    }

    private String scanReport(ResumeTemplateDocxInspector.Inspection inspection, ScanResult malwareInspection) {
        return json(Map.of("staticInspection", inspection, "malwareInspection", malwareInspection));
    }

    private static String scanStatus(
            ResumeTemplateDocxInspector.Inspection inspection, ScanResult malwareInspection) {
        if (!inspection.accepted()) return "STATIC_REJECTED";
        return switch (malwareInspection.outcome()) {
            case CLEAN -> "PASSED";
            case INFECTED -> "MALWARE_DETECTED";
            case UNAVAILABLE -> "MALWARE_SCANNER_UNAVAILABLE";
            case ERROR, NOT_RUN -> "MALWARE_SCANNER_ERROR";
        };
    }

    private static String rejectionReason(ResumeTemplateDocxInspector.Inspection inspection,
            ScanResult malwareInspection, String licenseStatus) {
        if (!inspection.accepted()) {
            return inspection.findings().stream().map(ResumeTemplateDocxInspector.Finding::code)
                    .distinct().reduce((left, right) -> left + "," + right).orElse("STATIC_SCAN_REJECTED");
        }
        if (malwareInspection.outcome() != TemplateAssetMalwareScanner.Outcome.CLEAN) {
            return malwareInspection.detailCode() == null ? "MALWARE_SCAN_FAILED" : malwareInspection.detailCode();
        }
        return "REJECTED".equals(licenseStatus) ? "LICENSE_REJECTED" : null;
    }

    private void moveStoredObject(String previousKey, String nextKey, byte[] content) {
        if (previousKey.equals(nextKey)) return;
        storage.put(nextKey, content);
        registerRollbackCleanup(nextKey);
        registerCommitCleanup(previousKey);
    }

    private void registerRollbackCleanup(String objectKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) return;
                try {
                    storage.delete(objectKey);
                } catch (RuntimeException exception) {
                    log.warn("template asset rollback cleanup failed objectKey={}", objectKey, exception);
                }
            }
        });
    }
    private void registerCommitCleanup(String objectKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storage.delete(objectKey);
                } catch (RuntimeException exception) {
                    log.warn("template asset rejection cleanup failed objectKey={}", objectKey, exception);
                }
            }
        });
    }
    private static void assertAdmin(CurrentAccount current) {
        if (!"ADMIN".equals(current.role()))
            throw AppException.forbidden("RESUME_TEMPLATE_ADMIN_REQUIRED", "仅 ADMIN 可管理模板候选与版本");
    }

    public record UploadEvidenceCommand(String evidenceType, String description,
            String originalFilename, byte[] content) {}
    public record TemplateAdminView(String id, String displayName, String familyName, String languageCode,
            String recommendedPages, String atsCandidateLevel, String photoPolicy, String status,
            Instant updatedAt) {}
    public record ScannerStatusView(boolean ready, String outcome, String engine,
            String engineVersion, String detailCode, Instant checkedAt) {}
    public record ImportAssetCommand(String sourceName, String sourceUri, String licenseStatus,
            String licenseEvidenceId, String originalFilename, byte[] content) {}
    public record ReviewAssetCommand(String decision, String licenseStatus,
            String licenseEvidenceId, String reason) {}
    public record DraftCommand(String rendererProtocol, String definitionJson, String thumbnailUri,
            String sourceAssetId, String independentDesignEvidenceId) {}
    public record TestCommand(String gateCode, String outcome, String evidenceId,
            String environmentJson, String summary) {}
    public record AssetView(String id, String sourceName, String sourceUri, String licenseStatus,
            String licenseEvidenceId, String fileHash, String contentType, Long sizeBytes, String scanStatus,
            String scanReportJson, boolean stored, String status, String rejectionReason, String uploadedBy,
            int version, String reviewedBy, Instant scannedAt, Instant reviewedAt, Instant createdAt) {}
    public record EvidenceView(String id, String evidenceType, String originalFilename, String contentType,
            long sizeBytes, String fileHash, String description, String uploadedBy, Instant createdAt) {}
    public record EvidenceDownload(String filename, String contentType, byte[] body) {}
    public record VersionView(String id, String templateId, int revisionNo, String status, String rendererProtocol,
            String definitionJson, String thumbnailUri, String sourceAssetId, String independentDesignEvidenceId,
            String testReportJson, boolean authorizationVerified,
            boolean securityVerified, boolean renderVerified, boolean wordVerified, boolean wpsVerified,
            boolean atsVerified, int version, Instant publishedAt, Instant retiredAt) {}

    private record GateResult(String runId, String outcome, String evidenceId, String evidenceHash, String executedBy,
            String environmentJson, String reportJson, Instant createdAt) {}

    private record Provenance(String sourceAssetId, String independentDesignEvidenceId, String summary) {}

    private record GateSnapshot(Map<String, GateResult> gates, boolean allPassed, boolean anyFailed) {
        boolean passed(String gateCode) {
            GateResult result = gates.get(gateCode);
            return result != null && "PASSED".equals(result.outcome());
        }
    }
}
