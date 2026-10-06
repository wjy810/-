package com.jobproof.modules.aiconfig.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAuditLogJpaRepository extends JpaRepository<AiAuditLogEntity, String> {
    List<AiAuditLogEntity> findByObjectTypeAndObjectIdOrderByCreatedAtDesc(String objectType, String objectId);
    List<AiAuditLogEntity> findByActorAccountIdOrderByCreatedAtDesc(String actorAccountId);
}
