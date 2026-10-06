package com.jobproof.modules.resume.infra;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeTemplateAssetJpaRepository extends JpaRepository<ResumeTemplateAssetEntity, String> {
    Page<ResumeTemplateAssetEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Optional<ResumeTemplateAssetEntity> findByFileHash(String fileHash);
}
