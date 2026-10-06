package com.jobproof.modules.datarights.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletionRequestJpaRepository extends JpaRepository<DeletionRequestEntity, String> {
    List<DeletionRequestEntity> findByAccountIdOrderByCreatedAtDesc(String accountId);

    Optional<DeletionRequestEntity> findFirstByAccountIdAndScopeAndStatusInOrderByCreatedAtDesc(
            String accountId, String scope, List<String> statuses);

    Optional<DeletionRequestEntity> findFirstByAccountIdAndScopeAndTargetTypeAndTargetIdAndStatusInOrderByCreatedAtDesc(
            String accountId, String scope, String targetType, String targetId, List<String> statuses);

    List<DeletionRequestEntity> findByStatusOrderByCreatedAtAsc(String status);
}
