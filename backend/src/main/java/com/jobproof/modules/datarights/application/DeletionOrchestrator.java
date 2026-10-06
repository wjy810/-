package com.jobproof.modules.datarights.application;

import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.datarights.domain.DeletionStateMachine;
import com.jobproof.modules.datarights.domain.DeletionStateMachine.Receipt;
import com.jobproof.modules.datarights.domain.DeletionStateMachine.ReceiptStatus;
import com.jobproof.modules.datarights.domain.DeletionStatus;
import com.jobproof.modules.datarights.infra.DeletionReceiptEntity;
import com.jobproof.modules.datarights.infra.DeletionReceiptJpaRepository;
import com.jobproof.modules.datarights.infra.DeletionRequestEntity;
import com.jobproof.modules.datarights.infra.DeletionRequestJpaRepository;
import com.jobproof.modules.identity.application.IdentityService;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileEntity;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import com.jobproof.modules.task.infra.AsyncTaskJpaRepository;
import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeletionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DeletionOrchestrator.class);

    private final DeletionRequestJpaRepository deletions;
    private final DeletionReceiptJpaRepository receipts;
    private final IdentityService identityService;
    private final DataRightsService dataRightsService;
    private final PrivateFileJpaRepository privateFiles;
    private final ObjectStoragePort storage;
    private final AsyncTaskJpaRepository tasks;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ClockPort clock;
    private final Map<String, DeletionModuleHandler> moduleHandlers;
    private final ObjectDeletionCatalog objectDeletionCatalog;

    public DeletionOrchestrator(
            DeletionRequestJpaRepository deletions,
            DeletionReceiptJpaRepository receipts,
            IdentityService identityService,
            DataRightsService dataRightsService,
            PrivateFileJpaRepository privateFiles,
            ObjectStoragePort storage,
            AsyncTaskJpaRepository tasks,
            NotificationService notificationService,
            AuditService auditService,
            ClockPort clock,
            List<DeletionModuleHandler> moduleHandlers,
            ObjectDeletionCatalog objectDeletionCatalog) {
        this.deletions = deletions;
        this.receipts = receipts;
        this.identityService = identityService;
        this.dataRightsService = dataRightsService;
        this.privateFiles = privateFiles;
        this.storage = storage;
        this.tasks = tasks;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.clock = clock;
        this.moduleHandlers = moduleHandlers.stream()
                .collect(Collectors.toMap(DeletionModuleHandler::moduleCode, Function.identity()));
        this.objectDeletionCatalog = objectDeletionCatalog;
    }

    @Transactional
    public void processDue() {
        for (DeletionRequestEntity request : deletions.findByStatusOrderByCreatedAtAsc(DeletionStatus.SUBMITTED.name())) {
            processOne(request);
        }
        for (DeletionRequestEntity request : deletions.findByStatusOrderByCreatedAtAsc(DeletionStatus.PROCESSING.name())) {
            if (receipts.findByRequestId(request.getId()).isEmpty()) {
                processOne(request);
            }
        }
    }

    private void processOne(DeletionRequestEntity request) {
        Instant now = clock.now();
        if (DeletionStatus.SUBMITTED.name().equals(request.getStatus())) {
            request.setStatus(DeletionStateMachine.startProcessing(DeletionStatus.SUBMITTED).name());
            request.setUpdatedAt(now);
            request.setVersionNo(request.getVersionNo() + 1);
            deletions.save(request);
        }

        List<Receipt> collected;
        if ("OBJECT".equals(request.getScope())) {
            log.info("object deletion orchestrate requestId={} type={} targetId={}",
                    request.getId(), request.getTargetType(), request.getTargetId());
            collected = processObject(request);
        } else {
            collected = processAccount(request, now);
        }

        for (Receipt receipt : collected) {
            DeletionReceiptEntity entity = new DeletionReceiptEntity();
            entity.setId(Ids.newId());
            entity.setRequestId(request.getId());
            entity.setModuleCode(receipt.moduleCode());
            entity.setReceiptStatus(receipt.status().name());
            entity.setMessage(receipt.message());
            entity.setCreatedAt(now);
            receipts.save(entity);
        }

        DeletionStatus next = DeletionStateMachine.conclude(collected);
        request.setStatus(next.name());
        request.setUpdatedAt(clock.now());
        request.setVersionNo(request.getVersionNo() + 1);
        deletions.save(request);
        auditService.append(request.getAccountId(), "DELETION_PROGRESS", "DELETION_REQUEST", request.getId(),
                "删除编排当前状态=" + next.name());
        try {
            notificationService.request(
                    request.getAccountId(),
                    NotificationType.DATA_DELETION_PROGRESS,
                    request.getId() + ":" + next.name(),
                    "删除进度更新",
                    "当前状态：" + next.name() + "。未完成不得视为全部删除完成。");
        } catch (RuntimeException e) {
            log.warn("deletion notification failed, business status unchanged requestId={}", request.getId());
        }
    }

    private List<Receipt> processObject(DeletionRequestEntity request) {
        try {
            return objectDeletionCatalog.execute(request.getAccountId(), request.getTargetType(), request.getTargetId());
        } catch (RuntimeException e) {
            log.warn("object deletion failed requestId={} type={} targetId={}",
                    request.getId(), request.getTargetType(), request.getTargetId(), e);
            return List.of(new Receipt("object", ReceiptStatus.FAILED, "对象级删除失败，删除申请保持可重试"));
        }
    }

    private List<Receipt> processAccount(DeletionRequestEntity request, Instant now) {
        List<Receipt> collected = new ArrayList<>();
        collected.add(run("identity", () -> {
            identityService.markDeletionPending(request.getAccountId());
            return new Receipt("identity", ReceiptStatus.SUCCEEDED, "账号进入删除编排，禁止重新登录；当前会话仍可查询进度");
        }));
        collected.add(run("share", () -> {
            dataRightsService.revokeAllShares(request.getAccountId());
            return new Receipt("share", ReceiptStatus.SUCCEEDED, "只读分享已撤销");
        }));
        collected.add(run("storage", () -> {
            for (PrivateFileEntity file : privateFiles.findByOwnerId(request.getAccountId())) {
                storage.delete(file.getObjectKey());
            }
            return new Receipt("storage", ReceiptStatus.SUCCEEDED, "S0 私有文件已清理");
        }));
        collected.add(run("task", () -> {
            for (AsyncTaskEntity task : tasks.findByAccountIdAndTaskTypeAndStatusIn(
                    request.getAccountId(),
                    TaskTypes.ACCOUNT_EXPORT,
                    List.of("PENDING", "RUNNING"))) {
                task.setStatus("CANCELLED");
                task.setFailureReason("账号删除编排取消");
                task.setUpdatedAt(now);
            }
            return new Receipt("task", ReceiptStatus.SUCCEEDED, "未完成导出任务已取消");
        }));
        collected.add(new Receipt("audit", ReceiptStatus.RESTRICTED, "审计索引保留且不含用户原文；法定例外走合规工单"));
        collected.add(moduleReceipt("career-library", request.getAccountId(), "求职资料库尚未接入，不能假装已删除"));
        collected.add(moduleReceipt("job", request.getAccountId(), "S2 岗位尚未接入，不能假装已删除"));
        collected.add(moduleReceipt("matching", request.getAccountId(), "已退役岗位分析历史尚未接入，不能假装已删除"));
        collected.add(moduleReceipt("resume-import", request.getAccountId(), "简历导入尚未接入，不能假装已删除"));
        collected.add(moduleReceipt("resume", request.getAccountId(), "S3 简历尚未接入，不能假装已删除"));
        collected.add(moduleReceipt("career-planning", request.getAccountId(), "职业规划尚未接入，不能假装已删除"));
        return collected;
    }

    private Receipt moduleReceipt(String moduleCode, String accountId, String skippedMessage) {
        DeletionModuleHandler handler = moduleHandlers.get(moduleCode);
        if (handler == null) {
            return new Receipt(moduleCode, ReceiptStatus.SKIPPED, skippedMessage);
        }
        DeletionModuleHandler.Result result = handler.onAccountDeletion(accountId);
        return new Receipt(moduleCode, ReceiptStatus.valueOf(result.status()), result.message());
    }

    private Receipt run(String module, java.util.function.Supplier<Receipt> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            log.warn("deletion participant failed module={}", module, e);
            return new Receipt(module, ReceiptStatus.FAILED, "模块处理失败，进入可重试/工单");
        }
    }
}
