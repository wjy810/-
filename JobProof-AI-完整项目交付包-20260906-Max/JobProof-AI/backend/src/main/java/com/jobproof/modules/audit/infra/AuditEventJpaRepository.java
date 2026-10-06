package com.jobproof.modules.audit.infra;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, String> {
}
