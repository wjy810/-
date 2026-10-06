package com.jobproof.modules.resume.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeLayoutTemplateVersionJpaRepository extends JpaRepository<ResumeLayoutTemplateVersionEntity, String> {
    Optional<ResumeLayoutTemplateVersionEntity> findFirstByTemplateIdAndStatusOrderByRevisionNoDesc(String templateId, String status);
    List<ResumeLayoutTemplateVersionEntity> findByTemplateIdOrderByRevisionNoDesc(String templateId);
}
