package com.jobproof.modules.resume.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeLayoutInstanceJpaRepository extends JpaRepository<ResumeLayoutInstanceEntity, String> {
    Optional<ResumeLayoutInstanceEntity> findFirstByAccountIdAndMasterIdOrderByCreatedAtDesc(String accountId, String masterId);
    Optional<ResumeLayoutInstanceEntity> findFirstByAccountIdAndMasterIdAndStatusInOrderByUpdatedAtDescCreatedAtDescIdDesc(
            String accountId, String masterId, Collection<String> statuses);
    Optional<ResumeLayoutInstanceEntity> findFirstByAccountIdAndMasterIdOrderByUpdatedAtDescCreatedAtDescIdDesc(
            String accountId, String masterId);
    List<ResumeLayoutInstanceEntity> findByAccountId(String accountId);
    void deleteByAccountId(String accountId);
}
