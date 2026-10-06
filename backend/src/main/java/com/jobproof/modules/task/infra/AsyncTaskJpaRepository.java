package com.jobproof.modules.task.infra;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsyncTaskJpaRepository extends JpaRepository<AsyncTaskEntity, String> {
    Optional<AsyncTaskEntity> findByAccountIdAndTaskTypeAndIdempotencyKey(String accountId, String taskType, String idempotencyKey);

    List<AsyncTaskEntity> findByStatusOrderByCreatedAtAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AsyncTaskEntity task where task.id = :id")
    Optional<AsyncTaskEntity> findByIdForUpdate(@Param("id") String id);

    /**
     * The oldest task of a type that is queued, or running on a lease that has expired (its worker
     * died). SKIP LOCKED: concurrent claimers never wait for, or take, a row another transaction holds.
     */
    @Query(value = """
            SELECT * FROM async_tasks
             WHERE task_type = :taskType
               AND (status = 'PENDING' OR (status = 'RUNNING' AND (lease_expires_at IS NULL OR lease_expires_at < :now)))
             ORDER BY created_at
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AsyncTaskEntity> lockNextClaimable(@Param("taskType") String taskType, @Param("now") Instant now);

    @Modifying
    @Query("update AsyncTaskEntity task set task.leaseExpiresAt = :until where task.leaseOwner = :owner and task.status = 'RUNNING' and task.leaseExpiresAt >= :now")
    int renewLeases(@Param("owner") String owner, @Param("until") Instant until, @Param("now") Instant now);

    List<AsyncTaskEntity> findByAccountIdAndTaskTypeAndStatusIn(String accountId, String taskType, List<String> statuses);

    boolean existsByAccountIdAndTaskTypeAndInputVersionAndStatusIn(
            String accountId, String taskType, String inputVersion, List<String> statuses);

    List<AsyncTaskEntity> findByAccountIdAndStatusInOrderByUpdatedAtDesc(String accountId, List<String> statuses);

    List<AsyncTaskEntity> findByTaskTypeAndStatus(String taskType, String status);
}
