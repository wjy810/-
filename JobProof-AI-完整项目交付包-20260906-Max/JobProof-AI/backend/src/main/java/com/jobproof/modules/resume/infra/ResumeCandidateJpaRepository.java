package com.jobproof.modules.resume.infra;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeCandidateJpaRepository extends JpaRepository<ResumeCandidateEntity, String> {

    List<ResumeCandidateEntity> findByMasterIdAndStatus(String masterId, String status);

    Page<ResumeCandidateEntity> findByMasterIdOrderByCreatedAtDesc(String masterId, Pageable pageable);

    Page<ResumeCandidateEntity> findByMasterIdAndStatusOrderByCreatedAtDesc(String masterId, String status, Pageable pageable);

    List<ResumeCandidateEntity> findByAccountId(String accountId);

    void deleteByAccountId(String accountId);
}
