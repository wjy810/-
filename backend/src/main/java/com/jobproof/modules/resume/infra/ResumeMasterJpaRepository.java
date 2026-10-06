package com.jobproof.modules.resume.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeMasterJpaRepository extends JpaRepository<ResumeMasterEntity, String> {

    List<ResumeMasterEntity> findByAccountIdOrderByUpdatedAtDesc(String accountId);

    void deleteByAccountId(String accountId);
}
