package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.infrastructure.queue.OutboxService;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.career.application.CareerLibraryService;
import com.jobproof.modules.resume.domain.KeyOutcome;
import com.jobproof.modules.resume.domain.ResumeCandidateStatus;
import com.jobproof.modules.resume.domain.ResumeCreateMode;
import com.jobproof.modules.resume.domain.ResumeDocxRenderer;
import com.jobproof.modules.resume.domain.ResumeEventTypes;
import com.jobproof.modules.resume.domain.ResumeFieldKey;
import com.jobproof.modules.resume.domain.ResumeFreezePolicy;
import com.jobproof.modules.resume.domain.ResumeImportHeuristic;
import com.jobproof.modules.resume.domain.ResumeMasterPolicy;
import com.jobproof.modules.resume.domain.ResumeMasterStatus;
import com.jobproof.modules.resume.domain.ResumePdfExportMode;
import com.jobproof.modules.resume.domain.ResumeAtsTextCheck;
import com.jobproof.modules.resume.domain.ResumeLayoutProtocol;
import com.jobproof.modules.resume.domain.ResumePdfRenderer;
import com.jobproof.modules.resume.domain.ResumeTaskTypes;
import com.jobproof.modules.resume.domain.ResumeTemplateCode;
import com.jobproof.modules.resume.domain.ResumeTemplates;
import com.jobproof.modules.resume.domain.ResumeVersionStatus;
import com.jobproof.modules.resume.infra.ResumeCandidateEntity;
import com.jobproof.modules.resume.infra.ResumeCandidateJpaRepository;
import com.jobproof.modules.resume.infra.ResumeMasterEntity;
import com.jobproof.modules.resume.infra.ResumeMasterJpaRepository;
import com.jobproof.modules.resume.infra.ResumeRenderArtifactEntity;
import com.jobproof.modules.resume.infra.ResumeRenderArtifactJpaRepository;
import com.jobproof.modules.resume.infra.ResumeVersionEntity;
import com.jobproof.modules.resume.infra.ResumeVersionJpaRepository;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileEntity;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.export.AccountExportContributor;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeService implements DeletionModuleHandler, AccountExportContributor {

    private final ResumeMasterJpaRepository masters;
    private final ResumeCandidateJpaRepository candidates;
    private final ResumeVersionJpaRepository versions;
    private final CareerLibraryService careerLibrary;
    private final TaskService taskService;
    private final ObjectStoragePort storage;
    private final PrivateFileJpaRepository files;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final ResumeLayoutCoordinator layoutCoordinator;
    private final ResumeRenderArtifactJpaRepository renderArtifacts;
    private final ClockPort clock;
    private final ObjectMapper objectMapper;

    public ResumeService(
            ResumeMasterJpaRepository masters,
            ResumeCandidateJpaRepository candidates,
            ResumeVersionJpaRepository versions,
            CareerLibraryService careerLibrary,
            TaskService taskService,
            ObjectStoragePort storage,
            PrivateFileJpaRepository files,
            OutboxService outboxService,
            AuditService auditService,
            ResumeLayoutCoordinator layoutCoordinator,
            ResumeRenderArtifactJpaRepository renderArtifacts,
            ClockPort clock,
            ObjectMapper objectMapper) {
        this.masters = masters;
        this.candidates = candidates;
        this.versions = versions;
        this.careerLibrary = careerLibrary;
        this.taskService = taskService;
        this.storage = storage;
        this.files = files;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.layoutCoordinator = layoutCoordinator;
        this.renderArtifacts = renderArtifacts;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public String moduleCode() {
        return "resume";
    }

    @Override
    public String moduleKey() {
        return "resume";
    }

    @Transactional
    public MasterView create(CurrentAccount current, CreateCommand command) {
        assertSeeker(current);
        ResumeCreateMode mode = ResumeCreateMode.parse(command.mode());
        Instant now = clock.now();
        ResumeMasterEntity entity = new ResumeMasterEntity();
        entity.setId(Ids.newId());
        entity.setAccountId(current.accountId());
        entity.setSource(mode.name());
        entity.setKeyOutcomesJson("[]");
        entity.setVersionNo(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (mode == ResumeCreateMode.TEMPLATE) {
            if (command.templateCode() == null || command.templateCode().isBlank()) {
                throw AppException.user("RESUME_TEMPLATE_REQUIRED", "从模板创建须指定 templateCode");
            }
            ResumeTemplateCode template = ResumeTemplateCode.parse(command.templateCode());
            entity.setTemplateCode(template.name());
            entity.setTitle(blankTo(command.title(), template.title()));
            entity.setStatus(ResumeMasterStatus.DRAFT.name());
            Map<String, String> content = ResumeTemplates.content(template);
            entity.setEducationJson(content.get("education"));
            entity.setExperienceJson(content.get("experience"));
            entity.setProjectsJson(content.get("projects"));
            entity.setSkillsJson(content.get("skills"));
            entity.setCertificatesJson(content.get("certificates"));
            entity.setSelfIntro(content.get("selfIntro"));
        } else if (mode == ResumeCreateMode.IMPORT) {
            entity.setTitle(blankTo(command.title(), "导入简历"));
            entity.setStatus(ResumeMasterStatus.PENDING_CONFIRMATION.name());
            masters.save(entity);
            for (ResumeImportHeuristic.CandidateDraft draft : ResumeImportHeuristic.extract(command.importText())) {
                insertCandidate(current.accountId(), entity.getId(), draft.fieldKey(), draft.proposedValue(), now);
            }
            auditService.append(current.accountId(), "RESUME_IMPORTED", "RESUME_MASTER", entity.getId(),
                    "导入文本已入候选，主档待确认，未写正式事实");
            return toMasterView(entity, current.accountId());
        } else {
            entity.setTitle(blankTo(command.title(), "未命名简历"));
            entity.setStatus(ResumeMasterStatus.DRAFT.name());
        }
        masters.save(entity);
        auditService.append(current.accountId(), "RESUME_CREATED", "RESUME_MASTER", entity.getId(),
                "已创建主档 mode=" + mode.name());
        return toMasterView(entity, current.accountId());
    }

    @Transactional(readOnly = true)
    public List<MasterSummary> list(CurrentAccount current) {
        assertSeeker(current);
        return masters.findByAccountIdOrderByUpdatedAtDesc(current.accountId()).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public MasterView get(CurrentAccount current, String id) {
        assertSeeker(current);
        return toMasterView(requireOwnMaster(current.accountId(), id), current.accountId());
    }

    @Transactional
    public MasterView update(CurrentAccount current, String id, UpdateCommand command, Integer expectedVersion) {
        assertSeeker(current);
        ResumeMasterEntity entity = requireOwnMaster(current.accountId(), id);
        Versions.assertExpected(expectedVersion, entity.getVersionNo());
        ResumeMasterStatus status = ResumeMasterStatus.parse(entity.getStatus());
        ResumeMasterPolicy.assertEditable(status);
        if (command.title() != null) {
            entity.setTitle(blankTo(command.title(), entity.getTitle()));
        }
        if (command.education() != null) {
            entity.setEducationJson(blankToNull(command.education()));
        }
        if (command.experience() != null) {
            entity.setExperienceJson(blankToNull(command.experience()));
        }
        if (command.projects() != null) {
            entity.setProjectsJson(blankToNull(command.projects()));
        }
        if (command.skills() != null) {
            entity.setSkillsJson(blankToNull(command.skills()));
        }
        if (command.certificates() != null) {
            entity.setCertificatesJson(blankToNull(command.certificates()));
        }
        if (command.selfIntro() != null) {
            entity.setSelfIntro(blankToNull(command.selfIntro()));
        }
        if (command.keyOutcomes() != null) {
            entity.setKeyOutcomesJson(writeJson(normalizeOutcomes(command.keyOutcomes())));
            demoteIfOutcomesUnresolved(entity);
        }
        bumpMaster(entity, clock.now());
        masters.save(entity);
        emitMasterChanged(current.accountId(), entity.getId(), "UPDATED");
        return toMasterView(entity, current.accountId());
    }

    @Transactional
    public MasterView copy(CurrentAccount current, String id) {
        assertSeeker(current);
        ResumeMasterEntity source = requireOwnMaster(current.accountId(), id);
        Instant now = clock.now();
        ResumeMasterEntity copy = new ResumeMasterEntity();
        copy.setId(Ids.newId());
        copy.setAccountId(current.accountId());
        copy.setTitle(source.getTitle() + "（副本）");
        copy.setStatus(ResumeMasterStatus.DRAFT.name());
        copy.setSource("COPY");
        copy.setTemplateCode(source.getTemplateCode());
        copy.setEducationJson(source.getEducationJson());
        copy.setExperienceJson(source.getExperienceJson());
        copy.setProjectsJson(source.getProjectsJson());
        copy.setSkillsJson(source.getSkillsJson());
        copy.setCertificatesJson(source.getCertificatesJson());
        copy.setSelfIntro(source.getSelfIntro());
        copy.setKeyOutcomesJson(source.getKeyOutcomesJson());
        copy.setVersionNo(0);
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        masters.save(copy);
        auditService.append(current.accountId(), "RESUME_COPIED", "RESUME_MASTER", copy.getId(),
                "复制主档为独立新草稿 source=" + source.getId());
        return toMasterView(copy, current.accountId());
    }

    @Transactional
    public MasterView archive(CurrentAccount current, String id, Integer expectedVersion) {
        assertSeeker(current);
        ResumeMasterEntity entity = requireOwnMaster(current.accountId(), id);
        Versions.assertExpected(expectedVersion, entity.getVersionNo());
        ResumeMasterStatus currentStatus = ResumeMasterStatus.parse(entity.getStatus());
        Instant now = clock.now();
        entity.setStatusBeforeArchive(currentStatus.name());
        entity.setStatus(ResumeMasterPolicy.archive(currentStatus).name());
        entity.setArchivedAt(now);
        bumpMaster(entity, now);
        masters.save(entity);
        auditService.append(current.accountId(), "RESUME_ARCHIVED", "RESUME_MASTER", entity.getId(), "主档已归档，可恢复");
        return toMasterView(entity, current.accountId());
    }

    @Transactional
    public MasterView restore(CurrentAccount current, String id, Integer expectedVersion) {
        assertSeeker(current);
        ResumeMasterEntity entity = requireOwnMaster(current.accountId(), id);
        Versions.assertExpected(expectedVersion, entity.getVersionNo());
        ResumeMasterStatus restored = ResumeMasterPolicy.restore(
                ResumeMasterStatus.parse(entity.getStatus()),
                entity.getStatusBeforeArchive() == null ? null : ResumeMasterStatus.parse(entity.getStatusBeforeArchive()));
        entity.setStatus(restored.name());
        entity.setArchivedAt(null);
        bumpMaster(entity, clock.now());
        masters.save(entity);
        auditService.append(current.accountId(), "RESUME_RESTORED", "RESUME_MASTER", entity.getId(), "主档已恢复为归档前状态");
        return toMasterView(entity, current.accountId());
    }

    @Transactional
    public MasterView markReady(CurrentAccount current, String id, Integer expectedVersion) {
        assertSeeker(current);
        ResumeMasterEntity entity = requireOwnMaster(current.accountId(), id);
        Versions.assertExpected(expectedVersion, entity.getVersionNo());
        ResumeFreezePolicy.assertCanMarkReady(
                ResumeMasterStatus.parse(entity.getStatus()),
                false,
                List.of(),
                readOutcomes(entity));
        entity.setStatus(ResumeMasterStatus.READY_TO_EXPORT.name());
        bumpMaster(entity, clock.now());
        masters.save(entity);
        auditService.append(current.accountId(), "RESUME_MARKED_READY", "RESUME_MASTER", entity.getId(), "主档已标记为可导出");
        return toMasterView(entity, current.accountId());
    }

    @Transactional
    public CandidateView createCandidate(CurrentAccount current, String masterId, String fieldKey, JsonNode proposedValue) {
        assertSeeker(current);
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), masterId);
        ResumeMasterPolicy.assertEditable(ResumeMasterStatus.parse(master.getStatus()));
        ResumeFieldKey key = ResumeFieldKey.parse(fieldKey);
        if (proposedValue == null || proposedValue.isNull() || (proposedValue.isTextual() && proposedValue.asText().isBlank())) {
            throw AppException.user("CANDIDATE_VALUE_REQUIRED", "AI 候选不能为空");
        }
        Instant now = clock.now();
        ResumeCandidateEntity entity = insertCandidate(
                current.accountId(), master.getId(), key, writeJson(proposedValue), now);
        if (ResumeMasterStatus.READY_TO_EXPORT.name().equals(master.getStatus())
                || ResumeMasterStatus.DRAFT.name().equals(master.getStatus())) {
            master.setStatus(ResumeMasterStatus.PENDING_CONFIRMATION.name());
            bumpMaster(master, now);
            masters.save(master);
        }
        outboxService.enqueue(ResumeEventTypes.CANDIDATE_CREATED, Map.of(
                "accountId", current.accountId(),
                "masterId", masterId,
                "candidateId", entity.getId()));
        auditService.append(current.accountId(), "RESUME_CANDIDATE_CREATED", "RESUME_CANDIDATE", entity.getId(),
                "AI 候选已登记，未写正式事实 field=" + key.name());
        return toCandidateView(entity);
    }

    @Transactional
    public ImportCandidatesView importTextCandidates(CurrentAccount current, String masterId, String rawText) {
        assertSeeker(current);
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), masterId);
        ResumeMasterPolicy.assertEditable(ResumeMasterStatus.parse(master.getStatus()));
        ResumeImportHeuristic.ParseResult parsed = ResumeImportHeuristic.parse(rawText);
        Instant now = clock.now();
        List<ResumeCandidateEntity> created = new ArrayList<>();
        for (ResumeImportHeuristic.CandidateDraft draft : parsed.drafts()) {
            ResumeCandidateEntity candidate = insertCandidate(current.accountId(), masterId,
                    draft.fieldKey(), writeJson(draft.proposedValue()), now.plusMillis(created.size()));
            candidate.setCandidateSource("TEXT_IMPORT");
            candidate.setAiAction("LOCAL_PARSE");
            candidate.setReasonText("本地规则按段落标题解析，需逐项确认后才进入正式简历");
            candidate.setDiffJson(writeJson(Map.of(
                    "before", formalValue(master, draft.fieldKey()),
                    "after", draft.proposedValue(),
                    "changed", true)));
            candidate.setSourceFactsJson(writeJson(List.of(Map.of(
                    "source", "pasted_resume_text",
                    "quote", draft.proposedValue().substring(0, Math.min(500, draft.proposedValue().length()))))));
            candidate.setGenerationMetadataJson(writeJson(Map.of(
                    "parser", "resume-import-local-v2",
                    "aiCalled", false)));
            candidates.save(candidate);
            created.add(candidate);
            outboxService.enqueue(ResumeEventTypes.CANDIDATE_CREATED, Map.of(
                    "accountId", current.accountId(), "masterId", masterId, "candidateId", candidate.getId()));
        }
        if (ResumeMasterStatus.READY_TO_EXPORT.name().equals(master.getStatus())
                || ResumeMasterStatus.DRAFT.name().equals(master.getStatus())) {
            master.setStatus(ResumeMasterStatus.PENDING_CONFIRMATION.name());
            bumpMaster(master, now);
            masters.save(master);
        }
        auditService.append(current.accountId(), "RESUME_TEXT_PARSED", "RESUME_MASTER", masterId,
                "parser=resume-import-local-v2 candidates=" + created.size()
                        + " sensitiveIgnored=" + parsed.ignoredSensitiveLines()
                        + " ambiguous=" + parsed.ambiguousLines());
        return new ImportCandidatesView("resume-import-local-v2", false,
                parsed.ignoredSensitiveLines(), parsed.ambiguousLines(),
                created.stream().map(this::toCandidateView).toList());
    }

    @Transactional(readOnly = true)
    public PageResult<CandidateView> listCandidates(CurrentAccount current, String masterId, String status, PageQuery query) {
        assertSeeker(current);
        requireOwnMaster(current.accountId(), masterId);
        PageRequest pageable = PageRequest.of(query.page(), query.size());
        Page<ResumeCandidateEntity> page;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            page = candidates.findByMasterIdOrderByCreatedAtDesc(masterId, pageable);
        } else {
            page = candidates.findByMasterIdAndStatusOrderByCreatedAtDesc(
                    masterId, ResumeCandidateStatus.parse(status.trim().toUpperCase()).name(), pageable);
        }
        return new PageResult<>(page.getContent().stream().map(this::toCandidateView).toList(),
                page.getTotalElements(), query.page(), query.size());
    }

    @Transactional
    public MasterView confirmCandidate(CurrentAccount current, String masterId, String candidateId, Integer expectedVersion) {
        return decideCandidate(current, masterId, candidateId, expectedVersion, ResumeCandidateStatus.CONFIRMED, null);
    }

    @Transactional
    public CandidateView rejectCandidate(CurrentAccount current, String masterId, String candidateId, Integer expectedVersion) {
        assertSeeker(current);
        ResumeCandidateEntity candidate = requireOwnCandidate(current.accountId(), masterId, candidateId);
        Versions.assertExpected(expectedVersion, candidate.getVersionNo());
        if (!ResumeCandidateStatus.parse(candidate.getStatus()).pending()) {
            throw AppException.conflict("CANDIDATE_NOT_PENDING", "只能处理待确认的简历候选");
        }
        Instant now = clock.now();
        candidate.setStatus(ResumeCandidateStatus.REJECTED.name());
        candidate.setDecidedAt(now);
        candidate.setVersionNo(candidate.getVersionNo() + 1);
        candidates.save(candidate);
        refreshAfterCandidates(requireOwnMaster(current.accountId(), masterId), now);
        return toCandidateView(candidate);
    }

    @Transactional
    public MasterView correctCandidate(
            CurrentAccount current, String masterId, String candidateId, JsonNode value, Integer expectedVersion) {
        if (value == null || value.isNull()) {
            throw AppException.user("CANDIDATE_VALUE_REQUIRED", "更正须给出正式值");
        }
        return decideCandidate(current, masterId, candidateId, expectedVersion, ResumeCandidateStatus.CORRECTED, value);
    }

    @Transactional
    public MasterView linkEvidence(CurrentAccount current, String masterId, String outcomeId, String evidenceId, Integer expectedVersion) {
        assertSeeker(current);
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), masterId);
        Versions.assertExpected(expectedVersion, master.getVersionNo());
        ResumeMasterPolicy.assertEditable(ResumeMasterStatus.parse(master.getStatus()));
        careerLibrary.requireActiveOwned(current.accountId(), evidenceId);
        List<KeyOutcome> outcomes = readOutcomes(master);
        boolean found = false;
        List<KeyOutcome> next = new ArrayList<>();
        for (KeyOutcome outcome : outcomes) {
            if (outcome.id().equals(outcomeId)) {
                next.add(outcome.withEvidence(evidenceId));
                found = true;
            } else {
                next.add(outcome);
            }
        }
        if (!found) {
            throw AppException.user("OUTCOME_NOT_FOUND", "关键成果不存在");
        }
        master.setKeyOutcomesJson(writeJson(next));
        bumpMaster(master, clock.now());
        masters.save(master);
        return toMasterView(master, current.accountId());
    }

    @Transactional
    public MasterView waiveOutcome(CurrentAccount current, String masterId, String outcomeId, boolean confirmed, Integer expectedVersion) {
        assertSeeker(current);
        if (!confirmed) {
            throw AppException.user("OUTCOME_WAIVE_CONFIRM_REQUIRED", "须逐条确认「暂无证据仍要冻结」");
        }
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), masterId);
        Versions.assertExpected(expectedVersion, master.getVersionNo());
        ResumeMasterPolicy.assertEditable(ResumeMasterStatus.parse(master.getStatus()));
        List<KeyOutcome> next = new ArrayList<>();
        boolean found = false;
        for (KeyOutcome outcome : readOutcomes(master)) {
            if (outcome.id().equals(outcomeId)) {
                next.add(outcome.waived());
                found = true;
            } else {
                next.add(outcome);
            }
        }
        if (!found) {
            throw AppException.user("OUTCOME_NOT_FOUND", "关键成果不存在");
        }
        master.setKeyOutcomesJson(writeJson(next));
        bumpMaster(master, clock.now());
        masters.save(master);
        return toMasterView(master, current.accountId());
    }

    @Transactional
    public VersionView freeze(CurrentAccount current, String masterId, Integer expectedVersion) {
        assertSeeker(current);
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), masterId);
        Versions.assertExpected(expectedVersion, master.getVersionNo());
        List<KeyOutcome> outcomes = readOutcomes(master);
        ResumeFreezePolicy.assertReadyAndResolved(
                ResumeMasterStatus.parse(master.getStatus()), false, List.of(), outcomes);
        return freezeSnapshot(current.accountId(), master, snapshotOf(master), "USER_FREEZE", null, null);
    }

    /**
     * Freezes only the confirmed master fields for the AI workbench PDF flow.
     * Pending candidates and missing evidence stay outside the immutable snapshot
     * and are surfaced as warnings instead of blocking a user-requested export.
     */
    @Transactional
    public VersionView freezeConfirmedForWorkbench(
            CurrentAccount current, String masterId, Integer expectedVersion) {
        return freezeConfirmedForWorkbench(current, masterId, expectedVersion, ResumePdfExportMode.STANDARD);
    }

    @Transactional
    public VersionView freezeConfirmedForWorkbench(
            CurrentAccount current, String masterId, Integer expectedVersion, ResumePdfExportMode exportMode) {
        assertSeeker(current);
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), masterId);
        Versions.assertExpected(expectedVersion, master.getVersionNo());
        if (ResumeMasterStatus.parse(master.getStatus()) == ResumeMasterStatus.ARCHIVED) {
            throw AppException.conflict("RESUME_ARCHIVED", "已归档简历不能导出，请先恢复");
        }
        ResumePdfExportMode mode = exportMode == null ? ResumePdfExportMode.STANDARD : exportMode;
        Map<String, Object> snapshot = exportSnapshot(master, mode);
        String source = mode == ResumePdfExportMode.ANONYMOUS
                ? "AI_WORKBENCH_PDF_ANONYMOUS" : "AI_WORKBENCH_PDF";
        return freezeSnapshot(current.accountId(), master, snapshot, source, null, null);
    }

    @Transactional(readOnly = true)
    public List<VersionView> listVersions(CurrentAccount current, String masterId) {
        assertSeeker(current);
        requireOwnMaster(current.accountId(), masterId);
        return versions.findByMasterIdOrderByCreatedAtDesc(masterId).stream().map(this::toVersionView).toList();
    }

    @Transactional(readOnly = true)
    public VersionView getVersion(CurrentAccount current, String versionId) {
        assertSeeker(current);
        return toVersionView(requireOwnVersion(current.accountId(), versionId));
    }

    @Transactional
    public VersionView confirmCustomizeVersion(CurrentAccount current, String versionId, Integer expectedVersion) {
        assertSeeker(current);
        ResumeVersionEntity version = requireOwnVersion(current.accountId(), versionId);
        Versions.assertExpected(expectedVersion, version.getVersionNo());
        ResumeVersionStatus status = ResumeVersionStatus.parse(version.getStatus());
        if (status != ResumeVersionStatus.PENDING_USER_CONFIRMATION) {
            throw AppException.conflict("RESUME_VERSION_NOT_PENDING", "只有待用户确认的定制版本可以确认后冻结");
        }
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), version.getMasterId());
        List<KeyOutcome> outcomes = readOutcomesFromSnapshot(version.getSnapshotJson());
        ResumeFreezePolicy.assertReadyAndResolved(
                ResumeMasterStatus.parse(master.getStatus()), false, List.of(), outcomes);
        return applyFreeze(current.accountId(), master, version, outcomes);
    }

    @Transactional
    public VersionView archiveVersion(CurrentAccount current, String versionId, Integer expectedVersion) {
        assertSeeker(current);
        ResumeVersionEntity version = requireOwnVersion(current.accountId(), versionId);
        Versions.assertExpected(expectedVersion, version.getVersionNo());
        Instant now = clock.now();
        version.setStatus(ResumeVersionStatus.ARCHIVED.name());
        version.setArchivedAt(now);
        version.setVersionNo(version.getVersionNo() + 1);
        version.setUpdatedAt(now);
        versions.save(version);
        return toVersionView(version);
    }

    @Transactional(readOnly = true)
    public CompareView compare(CurrentAccount current, String leftId, String rightId) {
        assertSeeker(current);
        ResumeVersionEntity left = requireOwnVersion(current.accountId(), leftId);
        ResumeVersionEntity right = requireOwnVersion(current.accountId(), rightId);
        return new CompareView(toVersionView(left), toVersionView(right));
    }

    @Transactional
    public TaskView startPdfExport(CurrentAccount current, String versionId) {
        return startPdfExport(current, versionId, ResumePdfExportMode.STANDARD);
    }

    @Transactional
    public TaskView startPdfExport(
            CurrentAccount current, String versionId, ResumePdfExportMode exportMode) {
        assertSeeker(current);
        ResumePdfExportMode mode = exportMode == null ? ResumePdfExportMode.STANDARD : exportMode;
        ResumeVersionEntity version = requireOwnVersion(current.accountId(), versionId);
        if (!ResumeVersionStatus.parse(version.getStatus()).pdfExportable()) {
            throw AppException.conflict("RESUME_VERSION_NOT_EXPORTABLE", "仅可从已冻结版本导出 PDF");
        }
        String payload = writeJson(Map.of(
                "resumeVersionId", version.getId(),
                "rendererVersion", ResumePdfRenderer.VERSION,
                "exportMode", mode.name()));
        return taskService.create(
                current.accountId(),
                ResumeTaskTypes.RESUME_PDF_EXPORT,
                "RESUME_PDF:" + mode.name() + ":" + version.getId() + ":" + ResumePdfRenderer.VERSION,
                payload);
    }

    /**
     * Step 1 of a PDF export (docs/phase2/03 §5.4). Built-in HTML templates return a job for the
     * renderer service, which runs outside any transaction; legacy layouts render here with PDFBox.
     */
    @Transactional
    public Optional<HtmlPdfJob> preparePdfExport(
            String taskId,
            String accountId,
            String resumeVersionId,
            String requestedRendererVersion,
            ResumePdfExportMode exportMode) {
        ResumePdfExportMode mode = exportMode == null ? ResumePdfExportMode.STANDARD : exportMode;
        if (!taskService.stillRunning(taskId)) {
            return Optional.empty();
        }
        if (!ResumePdfRenderer.VERSION.equals(requestedRendererVersion)) {
            taskService.markFailed(taskId, "PDF 渲染器版本已过期，请重新发起导出");
            return Optional.empty();
        }
        ResumeVersionEntity version = requireOwnVersion(accountId, resumeVersionId);
        if (!ResumeVersionStatus.parse(version.getStatus()).pdfExportable()) {
            taskService.markFailed(taskId, "版本已不是可导出状态");
            return Optional.empty();
        }
        String pdfTitle = readSnapshotTitle(version.getSnapshotJson());
        ResumeLayoutCoordinator.RenderContext renderContext = layoutCoordinator.renderContext(accountId, version)
                .orElse(null);
        if (renderContext != null && renderContext.html() != null) {
            taskService.updateProgress(taskId, 20, "RENDER_PREPARED");
            return Optional.of(new HtmlPdfJob(taskId, accountId, version.getId(), mode, pdfTitle, renderContext));
        }
        byte[] pdf = renderContext == null
                ? ResumePdfRenderer.render(pdfTitle, readSnapshotBody(version.getSnapshotJson()))
                : ResumePdfRenderer.render(
                        pdfTitle,
                        renderContext.document(),
                        renderContext.definition(),
                        renderContext.variantCode(),
                        renderContext.rendererProtocol());
        String fileId = storePdf(accountId, version.getId(), mode, pdf);
        String fileHash = sha256(pdf);
        if (renderContext != null) {
            saveRenderArtifact(taskId, accountId, version.getId(), renderContext, fileId, fileHash, mode, pdf);
        }
        taskService.markSucceeded(taskId, ResumePdfRenderer.VERSION, writeJson(Map.of(
                "resumeVersionId", version.getId(),
                "rendererVersion", ResumePdfRenderer.VERSION,
                "exportMode", mode.name(),
                "fileId", fileId,
                "fileHash", fileHash)));
        return Optional.empty();
    }

    /** Render input for the export preview of a resume's current layout; nothing is frozen or stored. */
    @Transactional(readOnly = true)
    public Optional<HtmlPdfJob> exportPreview(String accountId, String masterId, ResumePdfExportMode exportMode) {
        ResumePdfExportMode mode = exportMode == null ? ResumePdfExportMode.STANDARD : exportMode;
        ResumeMasterEntity master = requireOwnMaster(accountId, masterId);
        String snapshot = writeJson(exportSnapshot(master, mode));
        return layoutCoordinator.previewContext(accountId, masterId, snapshot)
                .map(context -> new HtmlPdfJob(null, accountId, null, mode, readSnapshotTitle(snapshot), context));
    }

    /** Step 3 of an HTML-template export: store the rendered PDF and its checks. */
    @Transactional
    public void completeHtmlPdfExport(HtmlPdfJob job, ResumeRenderPort.RenderedDocument pdf,
            ResumeAtsTextCheck.Result ats) {
        if (!taskService.stillRunning(job.taskId())) return;
        String fileId = storePdf(job.accountId(), job.resumeVersionId(), job.mode(), pdf.body());
        String fileHash = sha256(pdf.body());
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("pdfMagic", hasPdfMagic(pdf.body()));
        validation.put("exportMode", job.mode().name());
        validation.put("byteLength", pdf.body().length);
        validation.put("pageCount", pdf.pageCount());
        validation.put("pageLimit", job.context().html().pageLimit());
        validation.put("renderMs", pdf.renderMs());
        validation.put("ats", ats);
        Instant now = clock.now();
        ResumeRenderArtifactEntity artifact = new ResumeRenderArtifactEntity();
        artifact.setId(job.taskId());
        artifact.setAccountId(job.accountId());
        artifact.setLayoutInstanceId(job.context().layoutInstanceId());
        artifact.setFormat("PDF");
        artifact.setStatus("SUCCEEDED");
        artifact.setRendererVersion(ResumeLayoutProtocol.V4);
        artifact.setContentVersionId(job.resumeVersionId());
        artifact.setTemplateVersionId(job.context().templateVersionId());
        artifact.setFileId(fileId);
        artifact.setFileHash(fileHash);
        artifact.setValidationJson(writeJson(validation));
        artifact.setCreatedAt(now);
        artifact.setUpdatedAt(now);
        renderArtifacts.save(artifact);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resumeVersionId", job.resumeVersionId());
        result.put("rendererVersion", ResumeLayoutProtocol.V4);
        result.put("exportMode", job.mode().name());
        result.put("fileId", fileId);
        result.put("fileHash", fileHash);
        result.put("pageCount", pdf.pageCount());
        result.put("atsCheck", ats);
        taskService.markSucceeded(job.taskId(), ResumeLayoutProtocol.V4, writeJson(result));
    }

    /** An HTML-template export that produced no file: keep the reason next to the task. */
    @Transactional
    public void failHtmlPdfExport(HtmlPdfJob job, String code, String reason, Map<String, Object> details) {
        if (!taskService.stillRunning(job.taskId())) return;
        Instant now = clock.now();
        ResumeRenderArtifactEntity artifact = new ResumeRenderArtifactEntity();
        artifact.setId(job.taskId());
        artifact.setAccountId(job.accountId());
        artifact.setLayoutInstanceId(job.context().layoutInstanceId());
        artifact.setFormat("PDF");
        artifact.setStatus("FAILED");
        artifact.setRendererVersion(ResumeLayoutProtocol.V4);
        artifact.setContentVersionId(job.resumeVersionId());
        artifact.setTemplateVersionId(job.context().templateVersionId());
        artifact.setValidationJson(writeJson(details == null ? Map.of() : details));
        artifact.setFailureCode(code);
        artifact.setFailureMessage(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
        artifact.setCreatedAt(now);
        artifact.setUpdatedAt(now);
        renderArtifacts.save(artifact);
        taskService.markFailed(job.taskId(), code, reason);
    }

    @Transactional
    public TaskView startDocxExport(CurrentAccount current, String versionId) {
        assertSeeker(current);
        ResumeVersionEntity version = requireOwnVersion(current.accountId(), versionId);
        if (!ResumeVersionStatus.parse(version.getStatus()).pdfExportable()) {
            throw AppException.conflict("RESUME_VERSION_NOT_EXPORTABLE", "仅可从已冻结版本导出 DOCX");
        }
        ResumeLayoutCoordinator.RenderContext context = layoutCoordinator.renderContext(current.accountId(), version)
                .orElseThrow(() -> AppException.conflict(
                        "RESUME_DOCX_LAYOUT_REQUIRED", "DOCX 仅支持已冻结的智能模板版式"));
        layoutCoordinator.assertDocxAvailable(context.templateVersionId());
        String payload = writeJson(Map.of(
                "resumeVersionId", version.getId(),
                "rendererVersion", ResumeDocxRenderer.VERSION));
        return taskService.create(
                current.accountId(),
                ResumeTaskTypes.RESUME_DOCX_EXPORT,
                "RESUME_DOCX:" + version.getId() + ":" + ResumeDocxRenderer.VERSION,
                payload);
    }

    @Transactional
    public void completeDocxExport(
            String taskId,
            String accountId,
            String resumeVersionId,
            String requestedRendererVersion) {
        if (!taskService.stillRunning(taskId)) return;
        if (!ResumeDocxRenderer.VERSION.equals(requestedRendererVersion)) {
            taskService.markFailed(taskId, "DOCX 渲染器版本已过期，请重新发起导出");
            return;
        }
        ResumeVersionEntity version = requireOwnVersion(accountId, resumeVersionId);
        if (!ResumeVersionStatus.parse(version.getStatus()).pdfExportable()) {
            taskService.markFailed(taskId, "版本已不是可导出状态");
            return;
        }
        ResumeLayoutCoordinator.RenderContext context = layoutCoordinator.renderContext(accountId, version)
                .orElseThrow(() -> AppException.conflict(
                        "RESUME_DOCX_LAYOUT_REQUIRED", "DOCX 仅支持已冻结的智能模板版式"));
        layoutCoordinator.assertDocxAvailable(context.templateVersionId());
        String title = readSnapshotTitle(version.getSnapshotJson());
        byte[] docx = ResumeDocxRenderer.render(
                title,
                context.document(),
                context.definition(),
                context.variantCode(),
                context.definitionProtocol(),
                context.templateId());
        ResumeDocxRenderer.Inspection inspection = ResumeDocxRenderer.inspect(
                docx, title, context.document(), context.definition());
        if (!inspection.valid()) {
            saveFailedDocxArtifact(taskId, accountId, version.getId(), context, inspection);
            taskService.markFailed(taskId, "DOCX OOXML 或文本顺序校验失败，未生成可下载文件");
            return;
        }
        String fileId = storeDocx(accountId, version.getId(), docx);
        String fileHash = sha256(docx);
        saveDocxRenderArtifact(taskId, accountId, version.getId(), context, fileId, fileHash, inspection);
        taskService.markSucceeded(taskId, ResumeDocxRenderer.VERSION, writeJson(Map.of(
                "resumeVersionId", version.getId(),
                "rendererVersion", ResumeDocxRenderer.VERSION,
                "fileId", fileId,
                "fileHash", fileHash)));
    }

    @Transactional(readOnly = true)
    public VersionView requireBindable(String accountId, String resumeVersionId) {
        ResumeVersionEntity version = requireOwnVersion(accountId, resumeVersionId);
        ResumeFreezePolicy.assertBindable(ResumeVersionStatus.parse(version.getStatus()));
        return toVersionView(version);
    }

    @Override
    @Transactional
    public Result onAccountDeletion(String accountId) {
        try {
            for (ResumeVersionEntity version : versions.findByAccountId(accountId)) {
                for (KeyOutcome outcome : readOutcomesFromSnapshot(version.getSnapshotJson())) {
                    if (outcome.evidenceId() != null && !outcome.evidenceId().isBlank()) {
                        careerLibrary.releaseReference(accountId, outcome.evidenceId(), version.getId());
                    }
                }
            }
            versions.deleteByAccountId(accountId);
            renderArtifacts.deleteByAccountId(accountId);
            candidates.deleteByAccountId(accountId);
            masters.deleteByAccountId(accountId);
            return Result.succeeded("简历、待确认内容与历史版本已清除");
        } catch (RuntimeException ex) {
            return Result.failed("简历清理失败，删除申请保持可重试");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> contribute(String accountId) {
        List<Map<String, Object>> masterItems = new ArrayList<>();
        for (ResumeMasterEntity master : masters.findByAccountIdOrderByUpdatedAtDesc(accountId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", master.getId());
            row.put("title", master.getTitle());
            row.put("status", master.getStatus());
            row.put("source", master.getSource());
            row.put("updatedAt", master.getUpdatedAt().toString());
            masterItems.add(row);
        }
        List<Map<String, Object>> versionItems = new ArrayList<>();
        for (ResumeVersionEntity version : versions.findByAccountId(accountId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", version.getId());
            row.put("masterId", version.getMasterId());
            row.put("status", version.getStatus());
            row.put("source", version.getSource());
            row.put("layoutInstanceId", version.getLayoutInstanceId());
            row.put("frozenAt", version.getFrozenAt() == null ? null : version.getFrozenAt().toString());
            versionItems.add(row);
        }
        return Map.of("masters", masterItems, "versions", versionItems);
    }

    private VersionView freezeSnapshot(
            String accountId,
            ResumeMasterEntity master,
            Map<String, Object> snapshot,
            String source,
            String jobVersionId,
            String taskId) {
        Instant now = clock.now();
        ResumeVersionEntity version = new ResumeVersionEntity();
        version.setId(Ids.newId());
        version.setAccountId(accountId);
        version.setMasterId(master.getId());
        version.setStatus(ResumeVersionStatus.GENERATING.name());
        version.setSource(source);
        version.setJobVersionId(jobVersionId);
        version.setCustomizeTaskId(taskId);
        version.setSnapshotJson(writeJson(snapshot));
        version.setVersionNo(0);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        versions.save(version);
        return applyFreeze(accountId, master, version, readOutcomes(master));
    }

    private VersionView applyFreeze(
            String accountId,
            ResumeMasterEntity master,
            ResumeVersionEntity version,
            List<KeyOutcome> outcomes) {
        for (KeyOutcome outcome : outcomes) {
            if (outcome.evidenceId() != null && !outcome.evidenceId().isBlank()) {
                careerLibrary.requireActiveOwned(accountId, outcome.evidenceId());
                careerLibrary.registerReference(accountId, outcome.evidenceId(), version.getId());
            }
        }
        version.setLayoutInstanceId(layoutCoordinator
                .freezeCurrentLayout(accountId, master.getId(), version)
                .orElse(null));
        Instant now = clock.now();
        version.setStatus(ResumeVersionStatus.FROZEN.name());
        version.setFrozenAt(now);
        version.setUpdatedAt(now);
        version.setVersionNo(version.getVersionNo() + 1);
        versions.save(version);
        outboxService.enqueue(ResumeEventTypes.VERSION_FROZEN, Map.of(
                "accountId", accountId,
                "masterId", master.getId(),
                "resumeVersionId", version.getId()));
        auditService.append(accountId, "RESUME_VERSION_FROZEN", "RESUME_VERSION", version.getId(),
                "已冻结版本并登记证据引用，失败不会留下半成品");
        return toVersionView(version);
    }

    private MasterView decideCandidate(
            CurrentAccount current,
            String masterId,
            String candidateId,
            Integer expectedVersion,
            ResumeCandidateStatus decided,
            JsonNode correction) {
        assertSeeker(current);
        ResumeMasterEntity master = requireOwnMaster(current.accountId(), masterId);
        ResumeCandidateEntity candidate = requireOwnCandidate(current.accountId(), masterId, candidateId);
        Versions.assertExpected(expectedVersion, candidate.getVersionNo());
        if (!ResumeCandidateStatus.parse(candidate.getStatus()).pending()) {
            throw AppException.conflict("CANDIDATE_NOT_PENDING", "只能处理待确认的简历候选");
        }
        if (candidate.isSourceStale()) {
            throw AppException.conflict("CANDIDATE_SOURCE_STALE", "候选引用的求职资料已变化，请拒绝该候选并重新生成");
        }
        Instant now = clock.now();
        String value = correction == null ? candidate.getProposedValueJson() : writeJson(correction);
        applyFormalField(master, ResumeFieldKey.parse(candidate.getFieldKey()), value);
        candidate.setStatus(decided.name());
        if (correction != null) {
            candidate.setCorrectionValueJson(value);
        }
        candidate.setDecidedAt(now);
        candidate.setVersionNo(candidate.getVersionNo() + 1);
        candidates.save(candidate);
        refreshAfterCandidates(master, now);
        return toMasterView(master, current.accountId());
    }

    private void applyFormalField(ResumeMasterEntity master, ResumeFieldKey key, String valueJson) {
        String text = unwrapJsonText(valueJson);
        switch (key) {
            case TITLE -> master.setTitle(blankTo(text, master.getTitle()));
            case EDUCATION -> master.setEducationJson(text);
            case EXPERIENCE -> master.setExperienceJson(text);
            case PROJECTS -> master.setProjectsJson(text);
            case SKILLS -> master.setSkillsJson(text);
            case CERTIFICATES -> master.setCertificatesJson(text);
            case SELF_INTRO -> master.setSelfIntro(text);
            case KEY_OUTCOMES -> {
                List<KeyOutcome> outcomes = new ArrayList<>(readOutcomes(master));
                outcomes.add(new KeyOutcome(Ids.newId(), text, null, false));
                master.setKeyOutcomesJson(writeJson(outcomes));
            }
        }
    }

    private static String formalValue(ResumeMasterEntity master, ResumeFieldKey key) {
        return Objects.toString(switch (key) {
            case TITLE -> master.getTitle();
            case EDUCATION -> master.getEducationJson();
            case EXPERIENCE -> master.getExperienceJson();
            case PROJECTS -> master.getProjectsJson();
            case SKILLS -> master.getSkillsJson();
            case CERTIFICATES -> master.getCertificatesJson();
            case SELF_INTRO -> master.getSelfIntro();
            case KEY_OUTCOMES -> master.getKeyOutcomesJson();
        }, "");
    }

    private void refreshAfterCandidates(ResumeMasterEntity master, Instant now) {
        PendingAi pending = pendingAi(master.getId());
        if (!pending.any() && ResumeMasterStatus.PENDING_CONFIRMATION.name().equals(master.getStatus())) {
            master.setStatus(ResumeMasterStatus.DRAFT.name());
        }
        bumpMaster(master, now);
        masters.save(master);
    }

    private void demoteIfOutcomesUnresolved(ResumeMasterEntity entity) {
        if (!ResumeMasterStatus.READY_TO_EXPORT.name().equals(entity.getStatus())) {
            return;
        }
        boolean unresolved = readOutcomes(entity).stream().anyMatch(item -> !item.resolved());
        if (unresolved) {
            entity.setStatus(ResumeMasterStatus.DRAFT.name());
        }
    }

    private ResumeCandidateEntity insertCandidate(
            String accountId, String masterId, ResumeFieldKey fieldKey, String valueJson, Instant now) {
        ResumeCandidateEntity entity = new ResumeCandidateEntity();
        entity.setId(Ids.newId());
        entity.setAccountId(accountId);
        entity.setMasterId(masterId);
        entity.setFieldKey(fieldKey.name());
        entity.setProposedValueJson(valueJson.startsWith("{") || valueJson.startsWith("[") || valueJson.startsWith("\"")
                ? valueJson
                : writeJson(valueJson));
        entity.setStatus(ResumeCandidateStatus.PENDING.name());
        entity.setCandidateSource("MANUAL");
        entity.setVersionNo(0);
        entity.setCreatedAt(now);
        candidates.save(entity);
        return entity;
    }

    private Map<String, Object> snapshotOf(ResumeMasterEntity master) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("masterId", master.getId());
        snapshot.put("title", master.getTitle());
        snapshot.put("education", master.getEducationJson());
        snapshot.put("experience", master.getExperienceJson());
        snapshot.put("projects", master.getProjectsJson());
        snapshot.put("skills", master.getSkillsJson());
        snapshot.put("certificates", master.getCertificatesJson());
        snapshot.put("selfIntro", master.getSelfIntro());
        snapshot.put("contentSchemaVersion", master.getContentSchemaVersion());
        snapshot.put("content", master.getContentJson() == null ? Map.of() : readJsonMap(master.getContentJson()));
        snapshot.put("keyOutcomes", readOutcomes(master));
        return snapshot;
    }

    private Map<String, Object> exportSnapshot(ResumeMasterEntity master, ResumePdfExportMode mode) {
        Map<String, Object> snapshot = snapshotOf(master);
        snapshot.put("exportMode", mode.name());
        if (mode != ResumePdfExportMode.ANONYMOUS) return snapshot;

        Map<String, Object> content = readJsonMap(writeJson(snapshot.getOrDefault("content", Map.of())));
        Map<String, Object> basics = readJsonMap(writeJson(content.getOrDefault("basics", Map.of())));
        for (String key : List.of("name", "email", "phone", "location", "links", "personalLinks",
                "website", "url", "github", "linkedin")) {
            basics.remove(key);
        }
        content.put("basics", basics);
        content.put("photoFileId", null);
        snapshot.put("title", "匿名简历");
        snapshot.put("content", content);
        snapshot.put("anonymizedFields", List.of(
                "basics.name", "basics.email", "basics.phone", "basics.location",
                "basics.links", "photoFileId", "title"));
        return snapshot;
    }

    private PendingAi pendingAi(String masterId) {
        List<ResumeCandidateEntity> pending = candidates.findByMasterIdAndStatus(masterId, ResumeCandidateStatus.PENDING.name());
        return new PendingAi(!pending.isEmpty(), pending.stream().map(ResumeCandidateEntity::getId).toList());
    }

    private List<KeyOutcome> readOutcomes(ResumeMasterEntity master) {
        return readOutcomeList(master.getKeyOutcomesJson());
    }

    private List<KeyOutcome> readOutcomesFromSnapshot(String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            JsonNode outcomes = root.path("keyOutcomes");
            if (outcomes.isMissingNode() || outcomes.isNull()) {
                return List.of();
            }
            return readOutcomeList(outcomes.toString());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String readSnapshotTitle(String snapshotJson) {
        try {
            return blankTo(objectMapper.readTree(snapshotJson).path("title").asText(), "未命名简历");
        } catch (Exception exception) {
            return "未命名简历";
        }
    }

    private String readSnapshotBody(String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            StringBuilder body = new StringBuilder();
            appendSnapshotField(body, "个人简介", root.path("selfIntro"));
            appendSnapshotField(body, "教育经历", root.path("education"));
            appendSnapshotField(body, "工作经历", root.path("experience"));
            appendSnapshotField(body, "项目经历", root.path("projects"));
            appendSnapshotField(body, "专业技能", root.path("skills"));
            appendSnapshotField(body, "证书与资质", root.path("certificates"));
            return body.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private void appendSnapshotField(StringBuilder body, String label, JsonNode node) {
        String value = node == null || node.isNull() || node.isMissingNode() ? "" : node.asText();
        try {
            JsonNode nested = objectMapper.readTree(value);
            if (nested.isTextual()) {
                value = nested.asText();
            }
        } catch (JsonProcessingException ignored) {
            // Stored plain text is also valid.
        }
        if (!value.isBlank()) {
            if (!body.isEmpty()) {
                body.append("\n\n");
            }
            body.append(label).append('\n').append(value.trim());
        }
    }

    private List<KeyOutcome> readOutcomeList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            List<KeyOutcome> result = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                result.add(new KeyOutcome(
                        String.valueOf(item.getOrDefault("id", Ids.newId())),
                        Objects.toString(item.get("text"), ""),
                        item.get("evidenceId") == null ? null : String.valueOf(item.get("evidenceId")),
                        Boolean.TRUE.equals(item.get("waiveNoEvidence"))));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<KeyOutcome> normalizeOutcomes(List<KeyOutcomeWrite> incoming) {
        List<KeyOutcome> result = new ArrayList<>();
        for (KeyOutcomeWrite item : incoming) {
            if (item == null || (item.text() == null || item.text().isBlank()) && item.id() == null) {
                continue;
            }
            result.add(new KeyOutcome(
                    item.id() == null || item.id().isBlank() ? Ids.newId() : item.id(),
                    item.text() == null ? "" : item.text().trim(),
                    blankToNull(item.evidenceId()),
                    Boolean.TRUE.equals(item.waiveNoEvidence())));
        }
        return result;
    }

    private MasterView toMasterView(ResumeMasterEntity entity, String accountId) {
        PendingAi pending = pendingAi(entity.getId());
        return new MasterView(
                entity.getId(),
                entity.getTitle(),
                entity.getStatus(),
                ResumeMasterStatus.parse(entity.getStatus()).label(),
                entity.getSource(),
                entity.getTemplateCode(),
                entity.getEducationJson(),
                entity.getExperienceJson(),
                entity.getProjectsJson(),
                entity.getSkillsJson(),
                entity.getCertificatesJson(),
                entity.getSelfIntro(),
                readOutcomes(entity),
                pending.ids(),
                entity.getVersionNo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getArchivedAt(),
                versions.findByMasterIdOrderByCreatedAtDesc(entity.getId()).stream().map(this::toVersionView).toList());
    }

    private MasterSummary toSummary(ResumeMasterEntity entity) {
        return new MasterSummary(
                entity.getId(),
                entity.getTitle(),
                entity.getStatus(),
                ResumeMasterStatus.parse(entity.getStatus()).label(),
                entity.getVersionNo(),
                entity.getUpdatedAt());
    }

    private VersionView toVersionView(ResumeVersionEntity entity) {
        ResumeVersionStatus status = ResumeVersionStatus.parse(entity.getStatus());
        return new VersionView(
                entity.getId(),
                entity.getMasterId(),
                entity.getStatus(),
                status.label(),
                entity.getSource(),
                entity.getCustomizeTaskId(),
                entity.getJobVersionId(),
                entity.getLayoutInstanceId(),
                readJsonMap(entity.getSnapshotJson()),
                status.contentImmutable(),
                entity.getVersionNo(),
                entity.getCreatedAt(),
                entity.getFrozenAt(),
                entity.getArchivedAt());
    }

    private CandidateView toCandidateView(ResumeCandidateEntity entity) {
        return new CandidateView(
                entity.getId(),
                entity.getMasterId(),
                entity.getFieldKey(),
                readJsonNode(entity.getProposedValueJson()),
                entity.getStatus(),
                entity.getCandidateSource(),
                entity.getAiAction(),
                entity.getReasonText(),
                readJsonNode(entity.getDiffJson()),
                readJsonNode(entity.getSourceFactsJson()),
                readJsonNode(entity.getGenerationMetadataJson()),
                entity.getCareerLibrarySnapshotVersion(),
                readJsonNode(entity.getCareerLibrarySourcesJson()),
                entity.isSourceStale(),
                entity.getVersionNo(),
                entity.getCreatedAt(),
                entity.getDecidedAt());
    }

    private ResumeMasterEntity requireOwnMaster(String accountId, String id) {
        ResumeMasterEntity entity = masters.findById(id)
                .orElseThrow(() -> AppException.user("RESUME_NOT_FOUND", "简历主档不存在"));
        if (!entity.getAccountId().equals(accountId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能访问他人的简历");
        }
        return entity;
    }

    private ResumeVersionEntity requireOwnVersion(String accountId, String id) {
        ResumeVersionEntity entity = versions.findById(id)
                .orElseThrow(() -> AppException.user("RESUME_VERSION_NOT_FOUND", "简历版本不存在"));
        if (!entity.getAccountId().equals(accountId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能访问他人的简历版本");
        }
        return entity;
    }

    private ResumeCandidateEntity requireOwnCandidate(String accountId, String masterId, String candidateId) {
        ResumeCandidateEntity entity = candidates.findById(candidateId)
                .orElseThrow(() -> AppException.user("RESUME_CANDIDATE_NOT_FOUND", "简历候选不存在"));
        if (!entity.getAccountId().equals(accountId) || !entity.getMasterId().equals(masterId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能处理他人的简历候选");
        }
        return entity;
    }

    private TaskView assertTaskType(String accountId, String taskId, String expectedType) {
        TaskView view = taskService.getOwned(accountId, taskId);
        if (!expectedType.equals(view.taskType())) {
            throw AppException.conflict("WRONG_TASK_TYPE", "任务类型不匹配");
        }
        return view;
    }

    private String storePdf(
            String accountId, String resumeVersionId, ResumePdfExportMode exportMode, byte[] pdf) {
        Instant now = clock.now();
        String fileId = Ids.newId();
        String label = exportMode == ResumePdfExportMode.ANONYMOUS ? "resume-anonymous" : "resume";
        String key = "private/" + accountId + "/" + fileId + "-" + label + "-" + resumeVersionId + ".pdf";
        storage.put(key, pdf);
        PrivateFileEntity file = new PrivateFileEntity();
        file.setId(fileId);
        file.setOwnerId(accountId);
        file.setObjectKey(key);
        file.setContentType("application/pdf");
        file.setSizeBytes(pdf.length);
        file.setCreatedAt(now);
        files.save(file);
        return fileId;
    }

    private String storeDocx(String accountId, String resumeVersionId, byte[] docx) {
        Instant now = clock.now();
        String fileId = Ids.newId();
        String key = "private/" + accountId + "/" + fileId + "-resume-" + resumeVersionId + ".docx";
        storage.put(key, docx);
        PrivateFileEntity file = new PrivateFileEntity();
        file.setId(fileId);
        file.setOwnerId(accountId);
        file.setObjectKey(key);
        file.setContentType(ResumeDocxRenderer.CONTENT_TYPE);
        file.setSizeBytes(docx.length);
        file.setCreatedAt(now);
        files.save(file);
        return fileId;
    }

    private void saveRenderArtifact(
            String taskId,
            String accountId,
            String contentVersionId,
            ResumeLayoutCoordinator.RenderContext context,
            String fileId,
            String fileHash,
            ResumePdfExportMode exportMode,
            byte[] pdf) {
        Instant now = clock.now();
        ResumeRenderArtifactEntity artifact = new ResumeRenderArtifactEntity();
        artifact.setId(taskId);
        artifact.setAccountId(accountId);
        artifact.setLayoutInstanceId(context.layoutInstanceId());
        artifact.setFormat("PDF");
        artifact.setStatus("SUCCEEDED");
        artifact.setRendererVersion(ResumePdfRenderer.VERSION);
        artifact.setContentVersionId(contentVersionId);
        artifact.setTemplateVersionId(context.templateVersionId());
        artifact.setFileId(fileId);
        artifact.setFileHash(fileHash);
        artifact.setValidationJson(writeJson(Map.of(
                "pdfMagic", hasPdfMagic(pdf),
                "exportMode", exportMode.name(),
                "byteLength", pdf.length)));
        artifact.setCreatedAt(now);
        artifact.setUpdatedAt(now);
        renderArtifacts.save(artifact);
    }

    private void saveDocxRenderArtifact(
            String taskId,
            String accountId,
            String contentVersionId,
            ResumeLayoutCoordinator.RenderContext context,
            String fileId,
            String fileHash,
            ResumeDocxRenderer.Inspection inspection) {
        Instant now = clock.now();
        ResumeRenderArtifactEntity artifact = new ResumeRenderArtifactEntity();
        artifact.setId(taskId);
        artifact.setAccountId(accountId);
        artifact.setLayoutInstanceId(context.layoutInstanceId());
        artifact.setFormat("DOCX");
        artifact.setStatus("SUCCEEDED");
        artifact.setRendererVersion(ResumeDocxRenderer.VERSION);
        artifact.setContentVersionId(contentVersionId);
        artifact.setTemplateVersionId(context.templateVersionId());
        artifact.setFileId(fileId);
        artifact.setFileHash(fileHash);
        artifact.setValidationJson(writeJson(docxValidation(inspection)));
        artifact.setCreatedAt(now);
        artifact.setUpdatedAt(now);
        renderArtifacts.save(artifact);
    }

    private void saveFailedDocxArtifact(
            String taskId,
            String accountId,
            String contentVersionId,
            ResumeLayoutCoordinator.RenderContext context,
            ResumeDocxRenderer.Inspection inspection) {
        Instant now = clock.now();
        ResumeRenderArtifactEntity artifact = new ResumeRenderArtifactEntity();
        artifact.setId(taskId);
        artifact.setAccountId(accountId);
        artifact.setLayoutInstanceId(context.layoutInstanceId());
        artifact.setFormat("DOCX");
        artifact.setStatus("FAILED");
        artifact.setRendererVersion(ResumeDocxRenderer.VERSION);
        artifact.setContentVersionId(contentVersionId);
        artifact.setTemplateVersionId(context.templateVersionId());
        artifact.setValidationJson(writeJson(docxValidation(inspection)));
        artifact.setFailureCode("DOCX_VALIDATION_FAILED");
        artifact.setFailureMessage("DOCX OOXML 或文本顺序校验失败");
        artifact.setCreatedAt(now);
        artifact.setUpdatedAt(now);
        renderArtifacts.save(artifact);
    }

    private static Map<String, Object> docxValidation(ResumeDocxRenderer.Inspection value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", value.valid());
        result.put("zipMagic", value.zipMagic());
        result.put("contentTypes", value.contentTypes());
        result.put("documentXml", value.documentXml());
        result.put("reopened", value.reopened());
        result.put("textOrder", value.textOrder());
        result.put("macroFree", value.macroFree());
        result.put("externalRelationshipFree", value.externalRelationshipFree());
        result.put("activeContentFree", value.activeContentFree());
        result.put("entryCount", value.entryCount());
        result.put("uncompressedBytes", value.uncompressedBytes());
        result.put("paragraphCount", value.paragraphCount());
        result.put("tableCount", value.tableCount());
        return result;
    }

    private static boolean hasPdfMagic(byte[] value) {
        return value.length >= 4
                && value[0] == '%'
                && value[1] == 'P'
                && value[2] == 'D'
                && value[3] == 'F';
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void bumpMaster(ResumeMasterEntity entity, Instant now) {
        entity.setVersionNo(entity.getVersionNo() + 1);
        entity.setUpdatedAt(now);
    }

    private void emitMasterChanged(String accountId, String masterId, String changeType) {
        outboxService.enqueue(ResumeEventTypes.MASTER_CHANGED, Map.of(
                "accountId", accountId,
                "masterId", masterId,
                "changeType", changeType));
    }

    private static void assertSeeker(CurrentAccount current) {
        if (current.operator()) {
            throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能查看或编辑用户简历原文");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    private JsonNode readJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }

    private String unwrapJsonText(String json) {
        if (json == null) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return node.asText();
            }
            return node.toString();
        } catch (Exception e) {
            return json;
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PendingAi(boolean any, List<String> ids) {
    }

    /** A PDF export waiting for the renderer service; holds no entities. */
    public record HtmlPdfJob(String taskId, String accountId, String resumeVersionId, ResumePdfExportMode mode,
            String title, ResumeLayoutCoordinator.RenderContext context) {}

    public record CreateCommand(String mode, String title, String templateCode, String importText) {
    }

    public record UpdateCommand(
            String title,
            String education,
            String experience,
            String projects,
            String skills,
            String certificates,
            String selfIntro,
            List<KeyOutcomeWrite> keyOutcomes) {
    }

    public record KeyOutcomeWrite(String id, String text, String evidenceId, Boolean waiveNoEvidence) {
    }

    public record MasterSummary(
            String id,
            String title,
            String status,
            String statusLabel,
            int version,
            Instant updatedAt) {
    }

    public record MasterView(
            String id,
            String title,
            String status,
            String statusLabel,
            String source,
            String templateCode,
            String education,
            String experience,
            String projects,
            String skills,
            String certificates,
            String selfIntro,
            List<KeyOutcome> keyOutcomes,
            List<String> pendingCandidateIds,
            int version,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt,
            List<VersionView> versions) {
    }

    public record VersionView(
            String id,
            String masterId,
            String status,
            String statusLabel,
            String source,
            String customizeTaskId,
            String jobVersionId,
            String layoutInstanceId,
            Map<String, Object> snapshot,
            boolean immutable,
            int version,
            Instant createdAt,
            Instant frozenAt,
            Instant archivedAt) {
    }

    public record CandidateView(
            String id,
            String masterId,
            String fieldKey,
            JsonNode proposedValue,
            String status,
            String candidateSource,
            String aiAction,
            String reason,
            JsonNode diff,
            JsonNode sourceFacts,
            JsonNode generationMetadata,
            Integer careerLibrarySnapshotVersion,
            JsonNode sourceRefs,
            boolean sourceStale,
            int version,
            Instant createdAt,
            Instant decidedAt) {
    }

    public record ImportCandidatesView(
            String parserVersion,
            boolean aiCalled,
            int ignoredSensitiveLines,
            int ambiguousLines,
            List<CandidateView> candidates) {
    }

    public record CompareView(VersionView left, VersionView right) {
    }

}
