package com.jobproof.modules.resume.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeVersionJpaRepository extends JpaRepository<ResumeVersionEntity, String> {

    List<ResumeVersionEntity> findByMasterIdOrderByCreatedAtDesc(String masterId);

    List<ResumeVersionEntity> findByAccountId(String accountId);

    Optional<ResumeVersionEntity> findFirstByCustomizeTaskId(String customizeTaskId);

    long countByMasterId(String masterId);

    void deleteByAccountId(String accountId);
}
