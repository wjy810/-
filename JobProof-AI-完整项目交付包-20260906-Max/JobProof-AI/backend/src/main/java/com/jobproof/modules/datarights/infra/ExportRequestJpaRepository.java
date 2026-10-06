package com.jobproof.modules.datarights.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportRequestJpaRepository extends JpaRepository<ExportRequestEntity, String> {
    Optional<ExportRequestEntity> findByTaskId(String taskId);

    Optional<ExportRequestEntity> findByDownloadTokenHash(String downloadTokenHash);

    List<ExportRequestEntity> findByAccountIdOrderByCreatedAtDesc(String accountId);
}
