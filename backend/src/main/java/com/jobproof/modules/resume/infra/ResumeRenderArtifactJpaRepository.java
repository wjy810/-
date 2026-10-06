package com.jobproof.modules.resume.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRenderArtifactJpaRepository extends JpaRepository<ResumeRenderArtifactEntity, String> {
    List<ResumeRenderArtifactEntity> findByAccountId(String accountId);
    void deleteByAccountId(String accountId);
}
