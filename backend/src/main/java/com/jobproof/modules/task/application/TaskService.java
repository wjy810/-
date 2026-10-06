package com.jobproof.modules.task.application;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.infrastructure.queue.OutboxService;
import com.jobproof.infrastructure.queue.WorkerIdentity;
import com.jobproof.modules.task.domain.TaskStatus;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import com.jobproof.modules.task.infra.AsyncTaskJpaRepository;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.event.EventTypes;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final AsyncTaskJpaRepository tasks;
    private final ClockPort clock;
    private final OutboxService outboxService;
    private final WorkerIdentity worker;
    private final Duration lease;

    public TaskService(AsyncTaskJpaRepository tasks, ClockPort clock, OutboxService outboxService,
            WorkerIdentity worker, JobProofProperties properties) {
        this.tasks = tasks;
        this.clock = clock;
        this.outboxService = outboxService;
        this.worker = worker;
        this.lease = Duration.ofSeconds(properties.getWorker().getTaskLeaseSeconds());
    }

    @Transactional
    public TaskView create(String accountId, String taskType, String idempotencyKey, String payloadJson) {
        return create(accountId, taskType, idempotencyKey, payloadJson, "v1");
    }

    @Transactional
    public TaskView create(String accountId, String taskType, String idempotencyKey, String payloadJson,
            String inputVersion) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? Ids.newId() : idempotencyKey;
        Optional<AsyncTaskEntity> existing = tasks.findByAccountIdAndTaskTypeAndIdempotencyKey(accountId, taskType, key);
        if (existing.isPresent()) {
            return TaskView.from(existing.get());
        }
        Instant now = clock.now();
        AsyncTaskEntity entity = new AsyncTaskEntity();
        entity.setId(Ids.newId());
        entity.setAccountId(accountId);
        entity.setTaskType(taskType);
        entity.setStatus(TaskStatus.PENDING.name());
        entity.setIdempotencyKey(key);
        entity.setInputVersion(inputVersion == null || inputVersion.isBlank() ? "v1" : inputVersion);
        entity.setPayloadJson(payloadJson);
        entity.setProgressPercent(0);
        entity.setCheckpointCode("QUEUED");
        entity.setErrorCode(null);
        entity.setAttemptCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        tasks.save(entity);
        return TaskView.from(entity);
    }

    @Transactional(readOnly = true)
    public boolean hasOpen(String accountId, String taskType, String inputVersion) {
        return tasks.existsByAccountIdAndTaskTypeAndInputVersionAndStatusIn(accountId, taskType, inputVersion,
                List.of(TaskStatus.PENDING.name(), TaskStatus.RUNNING.name()));
    }

    @Transactional(readOnly = true)
    public Optional<TaskView> findExisting(String accountId, String taskType, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
        return tasks.findByAccountIdAndTaskTypeAndIdempotencyKey(accountId, taskType, idempotencyKey)
                .map(TaskView::from);
    }

    @Transactional(readOnly = true)
    public TaskView getOwned(String accountId, String taskId) {
        TaskView view = TaskView.from(require(taskId));
        view.assertOwner(accountId);
        return view;
    }

    @Transactional
    public TaskView cancel(String accountId, String taskId) {
        AsyncTaskEntity entity = requireForUpdate(taskId);
        if (!entity.getAccountId().equals(accountId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能取消他人的任务");
        }
        TaskStatus status = TaskStatus.valueOf(entity.getStatus());
        if (!status.cancellable()) {
            throw AppException.conflict("TASK_NOT_CANCELLABLE", "终态任务不可取消，请开新任务");
        }
        Instant now = clock.now();
        releaseLease(entity);
        entity.setStatus(TaskStatus.CANCELLED.name());
        entity.setUpdatedAt(now);
        entity.setFailureReason("用户取消");
        entity.setCheckpointCode("CANCELLED");
        entity.setErrorCode("TASK_CANCELLED_BY_USER");
        tasks.save(entity);
        return TaskView.from(entity);
    }

    @Transactional
    public TaskView retryManually(String accountId, String taskId) {
        AsyncTaskEntity old = require(taskId);
        if (!old.getAccountId().equals(accountId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能重试他人的任务");
        }
        TaskStatus status = TaskStatus.valueOf(old.getStatus());
        if (status != TaskStatus.FAILED && status != TaskStatus.CANCELLED) {
            throw AppException.conflict("TASK_RETRY_NOT_ALLOWED", "仅失败或已取消任务可手动重试");
        }
        return create(accountId, old.getTaskType(), Ids.newId(), old.getPayloadJson(), old.getInputVersion());
    }

    /**
     * Claims the oldest queued task of a type for this process, or one whose worker died (lease
     * expired). Safe with any number of concurrent workers: locked rows are skipped, not waited on.
     */
    @Transactional
    public Optional<AsyncTaskEntity> claimNext(String taskType) {
        Instant now = clock.now();
        return tasks.lockNextClaimable(taskType, now)
                .map(task -> {
                    boolean reclaimed = TaskStatus.RUNNING.name().equals(task.getStatus());
                    task.setStatus(TaskStatus.RUNNING.name());
                    task.setAttemptCount(task.getAttemptCount() + 1);
                    task.setProgressPercent(Math.max(task.getProgressPercent(), 5));
                    task.setCheckpointCode(reclaimed ? "RESUMING" : "STARTING");
                    task.setErrorCode(null);
                    task.setFailureReason(null);
                    task.setLeaseOwner(worker.id());
                    task.setLeaseExpiresAt(now.plus(lease));
                    task.setUpdatedAt(now);
                    return tasks.save(task);
                });
    }

    /** Extends the leases of every task this process is running (called by the lease heartbeat). */
    @Transactional
    public int renewLeases() {
        Instant now = clock.now();
        return tasks.renewLeases(worker.id(), now.plus(lease), now);
    }

    /**
     * Kept for processors that call it on startup. Interrupted tasks no longer need resetting: a
     * task whose worker died is claimable again once its lease expires (immediately for tasks that
     * were running before leases existed). Resetting every RUNNING task here would steal work from
     * other live instances.
     */
    @Transactional(readOnly = true)
    public int recoverRunning(String taskType) {
        return 0;
    }

    @Transactional
    public boolean markSucceeded(String taskId, String resultVersion, String payloadJson) {
        AsyncTaskEntity entity = requireForUpdate(taskId);
        TaskStatus current = TaskStatus.valueOf(entity.getStatus());
        if (current.terminal() || ownedElsewhere(entity)) return false;
        Instant now = clock.now();
        releaseLease(entity);
        entity.setStatus(TaskStatus.SUCCEEDED.name());
        entity.setResultVersion(resultVersion);
        entity.setPayloadJson(payloadJson);
        entity.setFailureReason(null);
        entity.setProgressPercent(100);
        entity.setCheckpointCode("COMPLETED");
        entity.setErrorCode(null);
        entity.setUpdatedAt(now);
        tasks.save(entity);
        outboxService.enqueue(EventTypes.TASK_COMPLETED, Map.of(
                "taskId", entity.getId(),
                "accountId", entity.getAccountId(),
                "taskType", entity.getTaskType(),
                "status", entity.getStatus()));
        return true;
    }

    @Transactional
    public void requeuePending(String taskId) {
        AsyncTaskEntity entity = requireForUpdate(taskId);
        if (TaskStatus.CANCELLED.name().equals(entity.getStatus()) || ownedElsewhere(entity)) {
            return;
        }
        Instant now = clock.now();
        releaseLease(entity);
        entity.setStatus(TaskStatus.PENDING.name());
        entity.setFailureReason(null);
        entity.setCheckpointCode("RETRY_QUEUED");
        entity.setErrorCode(null);
        entity.setUpdatedAt(now);
        tasks.save(entity);
    }

    @Transactional
    public boolean markFailed(String taskId, String reason) {
        String code = reason != null && reason.matches("[A-Z][A-Z0-9_]{2,127}") ? reason : "TASK_FAILED";
        return markFailed(taskId, code, reason);
    }

    @Transactional
    public boolean markFailed(String taskId, String errorCode, String reason) {
        AsyncTaskEntity entity = requireForUpdate(taskId);
        TaskStatus current = TaskStatus.valueOf(entity.getStatus());
        if (current.terminal() || ownedElsewhere(entity)) return false;
        Instant now = clock.now();
        releaseLease(entity);
        entity.setStatus(TaskStatus.FAILED.name());
        entity.setFailureReason(reason);
        entity.setCheckpointCode("FAILED");
        entity.setErrorCode(errorCode);
        entity.setUpdatedAt(now);
        tasks.save(entity);
        outboxService.enqueue(EventTypes.TASK_FAILED, Map.of(
                "taskId", entity.getId(),
                "accountId", entity.getAccountId(),
                "taskType", entity.getTaskType(),
                "status", entity.getStatus()));
        return true;
    }

    @Transactional
    public void updateProgress(String taskId, int progressPercent, String checkpointCode) {
        AsyncTaskEntity entity = requireForUpdate(taskId);
        TaskStatus status = TaskStatus.valueOf(entity.getStatus());
        if (status.terminal() || ownedElsewhere(entity)) return;
        if (worker.id().equals(entity.getLeaseOwner())) entity.setLeaseExpiresAt(clock.now().plus(lease));
        entity.setProgressPercent(Math.max(entity.getProgressPercent(), Math.min(99, Math.max(0, progressPercent))));
        entity.setCheckpointCode(checkpointCode);
        entity.setErrorCode(null);
        entity.setFailureReason(null);
        entity.setUpdatedAt(clock.now());
        tasks.save(entity);
    }

    @Transactional(readOnly = true)
    public boolean stillRunning(String taskId) {
        return TaskStatus.RUNNING.name().equals(require(taskId).getStatus());
    }

    @Transactional(readOnly = true)
    public List<TaskView> listOpen(String accountId) {
        return tasks.findByAccountIdAndStatusInOrderByUpdatedAtDesc(
                        accountId,
                        List.of(TaskStatus.PENDING.name(), TaskStatus.RUNNING.name(), TaskStatus.FAILED.name()))
                .stream()
                .map(TaskView::from)
                .toList();
    }

    public AsyncTaskEntity require(String taskId) {
        return tasks.findById(taskId).orElseThrow(() -> AppException.user("TASK_NOT_FOUND", "任务不存在"));
    }

    /** A running task leased by another live worker may only be finished by that worker. */
    private boolean ownedElsewhere(AsyncTaskEntity entity) {
        return TaskStatus.RUNNING.name().equals(entity.getStatus())
                && entity.getLeaseOwner() != null
                && !entity.getLeaseOwner().equals(worker.id())
                && entity.getLeaseExpiresAt() != null
                && entity.getLeaseExpiresAt().isAfter(clock.now());
    }

    private static void releaseLease(AsyncTaskEntity entity) {
        entity.setLeaseOwner(null);
        entity.setLeaseExpiresAt(null);
    }

    private AsyncTaskEntity requireForUpdate(String taskId) {
        return tasks.findByIdForUpdate(taskId)
                .orElseThrow(() -> AppException.user("TASK_NOT_FOUND", "任务不存在"));
    }
}
