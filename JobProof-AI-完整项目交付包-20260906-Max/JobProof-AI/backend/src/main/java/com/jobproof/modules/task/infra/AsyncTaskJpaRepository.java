package com.jobproof.modules.task.infra;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsyncTaskJpaRepository extends JpaRepository<AsyncTaskEntity, String> {
    Optional<AsyncTaskEntity> findByAccountIdAndTaskTypeAndIdempotencyKey(String accountId, String taskType, String idempotencyKey);

    List<AsyncTaskEntity> findByStatusOrderByCreatedAtAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from AsyncTaskEntity task where task.id = :id")
    Optional<AsyncTaskEntity> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AsyncTaskEntity> findFirstByTaskTypeAndStatusOrderByCreatedAtAsc(String taskType, String status);

    List<AsyncTaskEntity> findByAccountIdAndTaskTypeAndStatusIn(String accountId, String taskType, List<String> statuses);

    boolean existsByAccountIdAndTaskTypeAndInputVersionAndStatusIn(
            String accountId, String taskType, String inputVersion, List<String> statuses);

    List<AsyncTaskEntity> findByAccountIdAndStatusInOrderByUpdatedAtDesc(String accountId, List<String> statuses);

    List<AsyncTaskEntity> findByTaskTypeAndStatus(String taskType, String status);
}
