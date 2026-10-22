package com.jobproof.modules.aibilling.infra;
import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AiBillingRecordJpaRepository extends JpaRepository<AiBillingRecordEntity,String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AiBillingRecordEntity>{Page<AiBillingRecordEntity> findByAccountId(String accountId,Pageable page);}
