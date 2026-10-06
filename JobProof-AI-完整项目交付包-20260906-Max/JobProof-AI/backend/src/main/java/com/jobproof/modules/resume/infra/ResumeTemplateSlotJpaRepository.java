package com.jobproof.modules.resume.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeTemplateSlotJpaRepository extends JpaRepository<ResumeTemplateSlotEntity, String> {
    List<ResumeTemplateSlotEntity> findByTemplateVersionIdOrderByDisplayOrder(String templateVersionId);
    long countByTemplateVersionId(String templateVersionId);
    void deleteByTemplateVersionId(String templateVersionId);
}
