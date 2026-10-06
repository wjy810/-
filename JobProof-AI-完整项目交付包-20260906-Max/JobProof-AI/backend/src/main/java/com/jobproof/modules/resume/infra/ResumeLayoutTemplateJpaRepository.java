package com.jobproof.modules.resume.infra;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeLayoutTemplateJpaRepository extends JpaRepository<ResumeLayoutTemplateEntity, String> {
    List<ResumeLayoutTemplateEntity> findAllByOrderByDisplayNameAsc();
    Page<ResumeLayoutTemplateEntity> findByStatusOrderByDisplayNameAsc(String status, Pageable pageable);
}
