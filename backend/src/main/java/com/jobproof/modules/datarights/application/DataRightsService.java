package com.jobproof.modules.datarights.application;

import com.jobproof.infrastructure.queue.OutboxService;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.datarights.application.ObjectDeletionPlan.ImpactItem;
import com.jobproof.modules.datarights.domain.DeletionStateMachine;
import com.jobproof.modules.datarights.domain.DeletionStatus;
import com.jobproof.modules.datarights.domain.DeletionTargetType;
import com.jobproof.modules.datarights.infra.DeletionReceiptEntity;
import com.jobproof.modules.datarights.infra.DeletionReceiptJpaRepository;
import com.jobproof.modules.datarights.infra.DeletionRequestEntity;
import com.jobproof.modules.datarights.infra.DeletionRequestJpaRepository;
import com.jobproof.modules.datarights.infra.ExportRequestEntity;
import com.jobproof.modules.datarights.infra.ExportRequestJpaRepository;
import com.jobproof.modules.datarights.infra.ShareGrantEntity;
import com.jobproof.modules.datarights.infra.ShareGrantJpaRepository;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileEntity;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.modules.task.domain.TaskStatus;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.event.EventTypes;
import com.jobproof.shared.export.AccountExportContributor;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.security.Tokens;
import com.jobproof.shared.time.ClockPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataRightsService {

    public static final String IMPACT = """
            关联影响：账号将进入删除编排；处理开始后禁止重新登录，当前会话仍可查询删除进度；已发出的只读分享将被撤销；未完成导出任务将被取消。\
            求职资料库、私有资料文件、岗位、匹配报告、简历及只读历史求职记录归档将随账号删除编排清理。\
            安全审计索引会保留且不含用户原文。提交不是立即物理清空全库。""";

    public static final String LEGAL_NOTE = """
            法定保留年限不在 P0A 锁死。处理中可进入部分受限；例外走合规工单。\
            P0A 不提供完整运营后台。""";

    private final DeletionRequestJpaRepository deletions;
    private final DeletionReceiptJpaRepository receipts;
    private final ExportRequestJpaRepository exports;
    private final ShareGrantJpaRepository shares;
    private final TaskService taskService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final ObjectStoragePort storage;
    private final PrivateFileJpaRepository privateFiles;
    private final ClockPort clock;
    private final ObjectMapper objectMapper;
    private final List<AccountExportContributor> exportContributors;
    private final ObjectDeletionCatalog objectDeletionCatalog;
    private final Duration downloadTtl;
    private final Duration shareTtl;

    public DataRightsService(
            DeletionRequestJpaRepository deletions,
            DeletionReceiptJpaRepository receipts,
            ExportRequestJpaRepository exports,
            ShareGrantJpaRepository shares,
            TaskService taskService,
            NotificationService notificationService,
            AuditService auditService,
            OutboxService outboxService,
            ObjectStoragePort storage,
            PrivateFileJpaRepository privateFiles,
            ClockPort clock,
            ObjectMapper objectMapper,
            List<AccountExportContributor> exportContributors,
            ObjectDeletionCatalog objectDeletionCatalog,
            @Value("${jobproof.export.download-ttl-hours:1}") long downloadHours,
            @Value("${jobproof.share.default-ttl-hours:24}") long shareHours) {
        this.deletions = deletions;
        this.receipts = receipts;
        this.exports = exports;
        this.shares = shares;
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.storage = storage;
        this.privateFiles = privateFiles;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.exportContributors = exportContributors;
        this.objectDeletionCatalog = objectDeletionCatalog;
        this.downloadTtl = Duration.ofHours(downloadHours);
        this.shareTtl = Duration.ofHours(shareHours);
    }

    public DeletionPreview previewDeletion(String accountId, String scope, String targetType, String targetId) {
        String resolvedScope = scope == null || scope.isBlank() ? "ACCOUNT" : scope;
        if ("ACCOUNT".equals(resolvedScope)) {
            return new DeletionPreview(
                    "ACCOUNT",
                    null,
                    null,
                    IMPACT,
                    LEGAL_NOTE,
                    "提交后状态为已提交，不会立即显示已完成。",
                    true,
                    true,
                    List.of(),
                    List.of());
        }
        if (!"OBJECT".equals(resolvedScope)) {
            throw AppException.user("SCOPE_INVALID", "S0 仅支持 ACCOUNT 或 OBJECT 范围");
        }
        ObjectDeletionPlan plan = objectDeletionCatalog.analyze(accountId, targetType, targetId);
        return new DeletionPreview(
                "OBJECT",
                plan.targetType().apiValue(),
                plan.targetId(),
                plan.impactSummary(),
                LEGAL_NOTE,
                "提交后状态为已提交，不会立即显示已完成。",
                true,
                plan.canProceed(),
                plan.impacts(),
                plan.blockers());
    }

    @Transactional
    public DeletionView submitDeletion(String accountId, String scope, String targetType, String targetId, boolean ack) {
        if (!ack) {
            throw AppException.user("CONFIRMATION_REQUIRED", "提交删除前必须确认关联影响与不可逆说明");
        }
        if (!"ACCOUNT".equals(scope) && !"OBJECT".equals(scope)) {
            throw AppException.user("SCOPE_INVALID", "S0 仅支持 ACCOUNT 或 OBJECT 范围");
        }
        List<String> open = openStatuses();
        if ("OBJECT".equals(scope)) {
            if (targetType == null || targetId == null || targetType.isBlank() || targetId.isBlank()) {
                throw AppException.user("TARGET_REQUIRED", "对象级删除必须指定对象类型与 ID");
            }
            if (deletions.findFirstByAccountIdAndScopeAndStatusInOrderByCreatedAtDesc(accountId, "ACCOUNT", open).isPresent()) {
                throw AppException.conflict("ACCOUNT_DELETION_IN_PROGRESS", "账号级删除进行中，不能再提交对象级删除");
            }
            DeletionTargetType parsed = DeletionTargetType.parse(targetType);
            ObjectDeletionPlan plan = objectDeletionCatalog.analyze(accountId, parsed.apiValue(), targetId.trim());
            if (!plan.canProceed()) {
                throw AppException.conflict(
                        plan.blockers().isEmpty() ? "DELETION_BLOCKED" : plan.blockers().get(0),
                        "存在未解除的有效引用，不能进入物理删除");
            }
            return deletions.findFirstByAccountIdAndScopeAndTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDesc(
                            accountId, "OBJECT", parsed.apiValue(), plan.targetId(), open)
                    .map(this::toView)
                    .orElseGet(() -> createDeletion(accountId, "OBJECT", parsed.apiValue(), plan.targetId(), plan.impactSummary()));
        }
        return deletions.findFirstByAccountIdAndScopeAndStatusInOrderByCreatedAtDesc(accountId, "ACCOUNT", open)
                .map(this::toView)
                .orElseGet(() -> createDeletion(accountId, "ACCOUNT", null, null, IMPACT));
    }

    private List<String> openStatuses() {
        return List.of(
                DeletionStatus.SUBMITTED.name(),
                DeletionStatus.PROCESSING.name(),
                DeletionStatus.PARTIALLY_RESTRICTED.name());
    }

    private DeletionView createDeletion(
            String accountId, String scope, String targetType, String targetId, String impactSummary) {
        Instant now = clock.now();
        DeletionRequestEntity entity = new DeletionRequestEntity();
        entity.setId(Ids.newId());
        entity.setAccountId(accountId);
        entity.setScope(scope);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setStatus(DeletionStateMachine.afterSubmit().name());
        entity.setImpactSummary(impactSummary);
        entity.setLegalExceptionNote(LEGAL_NOTE);
        entity.setConfirmationAck(true);
        entity.setVersionNo(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        deletions.save(entity);
        outboxService.enqueue(EventTypes.DELETION_REQUESTED, Map.of(
                "requestId", entity.getId(),
                "accountId", accountId,
                "scope", scope));
        auditService.append(accountId, "DELETION_REQUESTED", "DELETION_REQUEST", entity.getId(), "删除申请已提交，进入编排");
        tryNotify(accountId, NotificationType.DATA_DELETION_PROGRESS, entity.getId(), "删除申请已提交", "当前状态：已提交。尚未完成删除。");
        return toView(entity);
    }

    @Transactional(readOnly = true)
    public DeletionView getDeletion(String accountId, String requestId) {
        DeletionRequestEntity entity = deletions.findById(requestId)
                .orElseThrow(() -> AppException.user("DELETION_NOT_FOUND", "删除申请不存在"));
        if (!entity.getAccountId().equals(accountId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能查看他人的删除申请");
        }
        return toView(entity);
    }

    @Transactional
    public ExportView createExport(String accountId, String idempotencyKey) {
        TaskView task = taskService.create(accountId, TaskTypes.ACCOUNT_EXPORT, idempotencyKey, "{\"scope\":\"ACCOUNT\"}");
        ExportRequestEntity existing = exports.findByTaskId(task.id()).orElse(null);
        if (existing != null) {
            return toExportView(existing, task);
        }
        ExportRequestEntity entity = new ExportRequestEntity();
        entity.setId(Ids.newId());
        entity.setAccountId(accountId);
        entity.setTaskId(task.id());
        entity.setScope("ACCOUNT");
        entity.setCreatedAt(clock.now());
        exports.save(entity);
        auditService.append(accountId, "EXPORT_REQUESTED", "EXPORT_REQUEST", entity.getId(), "账号级导出任务已创建");
        tryNotify(accountId, NotificationType.DATA_EXPORT_PROGRESS, entity.getId(), "导出任务已创建", "当前状态：排队中。");
        return toExportView(entity, task);
    }

    @Transactional(readOnly = true)
    public ExportView getExport(String accountId, String exportId) {
        ExportRequestEntity entity = requireExport(exportId, accountId);
        TaskView task = taskService.getOwned(accountId, entity.getTaskId());
        return toExportView(entity, task);
    }

    @Transactional
    public ExportView cancelExport(String accountId, String exportId) {
        ExportRequestEntity entity = requireExport(exportId, accountId);
        TaskView task = taskService.cancel(accountId, entity.getTaskId());
        return toExportView(entity, task);
    }

    @Transactional
    public ExportView retryExport(String accountId, String exportId) {
        ExportRequestEntity old = requireExport(exportId, accountId);
        TaskView created = taskService.retryManually(accountId, old.getTaskId());
        ExportRequestEntity entity = new ExportRequestEntity();
        entity.setId(Ids.newId());
        entity.setAccountId(accountId);
        entity.setTaskId(created.id());
        entity.setScope("ACCOUNT");
        entity.setCreatedAt(clock.now());
        exports.save(entity);
        return toExportView(entity, created);
    }

    @Transactional
    public byte[] downloadExport(String accountId, String exportId) {
        ExportRequestEntity entity = requireExport(exportId, accountId);
        TaskView task = taskService.getOwned(accountId, entity.getTaskId());
        if (!TaskStatus.SUCCEEDED.name().equals(task.status())) {
            throw AppException.conflict("EXPORT_NOT_READY", "导出尚未成功，不能下载");
        }
        if (entity.getDownloadExpiresAt() == null || entity.getDownloadExpiresAt().isBefore(clock.now())) {
            throw AppException.user("EXPORT_LINK_EXPIRED", "下载链接已过期，请重新发起导出");
        }
        if (entity.getObjectKey() == null) {
            throw AppException.conflict("EXPORT_NOT_READY", "导出文件尚不可用");
        }
        return storage.get(entity.getObjectKey());
    }

    @Transactional
    public ShareView createShare(String accountId, String resourceType, String resourceId) {
        assertOwnResource(accountId, resourceType, resourceId);
        Instant now = clock.now();
        String raw = Tokens.randomToken();
        ShareGrantEntity grant = new ShareGrantEntity();
        grant.setId(Ids.newId());
        grant.setOwnerId(accountId);
        grant.setResourceType(resourceType);
        grant.setResourceId(resourceId);
        grant.setTokenHash(Tokens.sha256(raw));
        grant.setExpiresAt(now.plus(shareTtl));
        grant.setCreatedAt(now);
        shares.save(grant);
        outboxService.enqueue(EventTypes.AUTHORIZATION_REVOKED, Map.of(
                "note", "share-created",
                "shareId", grant.getId()));
        auditService.append(accountId, "SHARE_CREATED", "SHARE_GRANT", grant.getId(), "创建只读限时分享");
        tryNotify(accountId, NotificationType.DATA_RIGHTS, grant.getId(), "已创建只读分享", "分享默认只读、限时、可撤销。");
        return new ShareView(grant.getId(), resourceType, resourceId, raw, grant.getExpiresAt(), null, grant.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ShareView> listShares(String accountId) {
        return shares.findByOwnerIdOrderByCreatedAtDesc(accountId).stream()
                .map(grant -> new ShareView(
                        grant.getId(),
                        grant.getResourceType(),
                        grant.getResourceId(),
                        null,
                        grant.getExpiresAt(),
                        grant.getRevokedAt(),
                        grant.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void revokeShare(String accountId, String shareId) {
        ShareGrantEntity grant = shares.findById(shareId)
                .orElseThrow(() -> AppException.user("SHARE_NOT_FOUND", "分享不存在"));
        if (!grant.getOwnerId().equals(accountId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能撤销他人的分享");
        }
        Instant now = clock.now();
        grant.setRevokedAt(now);
        shares.save(grant);
        outboxService.enqueue(EventTypes.AUTHORIZATION_REVOKED, Map.of(
                "shareId", grant.getId(),
                "accountId", accountId));
        auditService.append(accountId, "SHARE_REVOKED", "SHARE_GRANT", grant.getId(), "分享已撤销");
    }

    public boolean canReadShared(String resourceType, String resourceId, String rawToken) {
        if (rawToken == null) {
            return false;
        }
        Instant now = clock.now();
        return shares.findByTokenHash(Tokens.sha256(rawToken))
                .filter(grant -> grant.getRevokedAt() == null)
                .filter(grant -> grant.getExpiresAt().isAfter(now))
                .filter(grant -> grant.getResourceType().equals(resourceType) && grant.getResourceId().equals(resourceId))
                .isPresent();
    }

    @Transactional
    public int revokeAllShares(String accountId) {
        return shares.revokeAllByOwner(accountId, clock.now());
    }

    @Transactional
    public void completeExportFile(String taskId, String objectKey, String rawDownloadToken) {
        ExportRequestEntity entity = exports.findByTaskId(taskId)
                .orElseThrow(() -> AppException.user("EXPORT_NOT_FOUND", "导出不存在"));
        Instant now = clock.now();
        entity.setObjectKey(objectKey);
        entity.setDownloadTokenHash(Tokens.sha256(rawDownloadToken));
        entity.setDownloadExpiresAt(now.plus(downloadTtl));
        exports.save(entity);
    }

    public String exportJsonPlaceholder(String accountId, String email) {
        try {
            return new String(exportBytes(accountId, email), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return "{\"scope\":\"ACCOUNT\",\"note\":\"export-unavailable\"}";
        }
    }

    public byte[] exportBytes(String accountId, String email) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("scope", "ACCOUNT");
        root.put("account", Map.of("id", accountId, "email", email));
        List<String> included = new ArrayList<>();
        included.add("account");
        for (AccountExportContributor contributor : exportContributors) {
            root.put(contributor.moduleKey(), contributor.contribute(accountId));
            included.add(contributor.moduleKey());
        }
        List<String> pending = new ArrayList<>(List.of("careerLibrary", "careerPlanning", "job", "resume"));
        pending.removeAll(included);
        root.put("included", included);
        root.put("pendingModules", pending);
        root.put("note", "账号级导出包含求职资料库、岗位与简历数据，以及只读的历史求职记录归档；不含他人数据、运营内部审计和供应商原始日志。");
        try {
            return objectMapper.writeValueAsBytes(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ExportRequestEntity requireExport(String exportId, String accountId) {
        ExportRequestEntity entity = exports.findById(exportId)
                .orElseThrow(() -> AppException.user("EXPORT_NOT_FOUND", "导出不存在"));
        if (!entity.getAccountId().equals(accountId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能访问他人的导出");
        }
        return entity;
    }

    private void assertOwnResource(String accountId, String resourceType, String resourceId) {
        if ("PRIVATE_FILE".equals(resourceType)) {
            PrivateFileEntity file = privateFiles.findById(resourceId)
                    .orElseThrow(() -> AppException.user("FILE_NOT_FOUND", "文件不存在"));
            if (!file.getOwnerId().equals(accountId)) {
                throw AppException.forbidden("OBJECT_FORBIDDEN", "不能分享他人的文件");
            }
            return;
        }
        if ("EXPORT".equals(resourceType)) {
            requireExport(resourceId, accountId);
            return;
        }
        throw AppException.user("RESOURCE_TYPE_UNSUPPORTED", "S0 分享仅支持 PRIVATE_FILE 或 EXPORT");
    }

    private void tryNotify(String accountId, NotificationType type, String eventId, String title, String body) {
        try {
            notificationService.request(accountId, type, eventId, title, body);
        } catch (RuntimeException ignored) {
            // 通知失败不回滚业务
        }
    }

    private DeletionView toView(DeletionRequestEntity entity) {
        List<ReceiptView> receiptViews = receipts.findByRequestId(entity.getId()).stream()
                .map(r -> new ReceiptView(r.getModuleCode(), r.getReceiptStatus(), r.getMessage()))
                .collect(Collectors.toList());
        return new DeletionView(
                entity.getId(),
                entity.getScope(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getStatus(),
                entity.getImpactSummary(),
                entity.getLegalExceptionNote(),
                entity.getVersionNo(),
                receiptViews,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ExportView toExportView(ExportRequestEntity entity, TaskView task) {
        boolean expired = entity.getDownloadExpiresAt() != null && entity.getDownloadExpiresAt().isBefore(clock.now());
        boolean downloadable = TaskStatus.SUCCEEDED.name().equals(task.status()) && !expired && entity.getObjectKey() != null;
        return new ExportView(
                entity.getId(),
                entity.getTaskId(),
                entity.getScope(),
                task.status(),
                task.failureReason(),
                downloadable,
                expired,
                entity.getDownloadExpiresAt(),
                entity.getCreatedAt());
    }

    public record DeletionPreview(
            String scope,
            String targetType,
            String targetId,
            String impactSummary,
            String legalExceptionNote,
            String statusHint,
            boolean irreversible,
            boolean canProceed,
            List<ImpactItem> impacts,
            List<String> blockers) {
    }

    public record ReceiptView(String moduleCode, String status, String message) {
    }

    public record DeletionView(
            String id,
            String scope,
            String targetType,
            String targetId,
            String status,
            String impactSummary,
            String legalExceptionNote,
            int version,
            List<ReceiptView> receipts,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ExportView(
            String id,
            String taskId,
            String scope,
            String taskStatus,
            String failureReason,
            boolean downloadAvailable,
            boolean downloadExpired,
            Instant downloadExpiresAt,
            Instant createdAt) {
    }

    public record ShareView(
            String id,
            String resourceType,
            String resourceId,
            String token,
            Instant expiresAt,
            Instant revokedAt,
            Instant createdAt) {
    }
}
