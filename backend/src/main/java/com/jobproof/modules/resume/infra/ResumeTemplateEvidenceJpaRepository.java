package com.jobproof.modules.resume.infra;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeTemplateEvidenceJpaRepository extends JpaRepository<ResumeTemplateEvidenceEntity, String> {
    Page<ResumeTemplateEvidenceEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Optional<ResumeTemplateEvidenceEntity> findByFileHash(String fileHash);
}
