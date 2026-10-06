package com.jobproof.modules.resume.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeTemplateTestRunJpaRepository extends JpaRepository<ResumeTemplateTestRunEntity, String> {
    List<ResumeTemplateTestRunEntity> findByTemplateVersionIdOrderByTemplateVersionNoDesc(String templateVersionId);
}
