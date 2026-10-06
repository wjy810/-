package com.jobproof.modules.aibilling.infra;
import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AiUsageRecordJpaRepository extends JpaRepository<AiUsageRecordEntity,String>{Page<AiUsageRecordEntity> findByAccountId(String accountId,Pageable page);}
