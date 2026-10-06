package com.jobproof.modules.aibilling.infra;
import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AiBillingRecordJpaRepository extends JpaRepository<AiBillingRecordEntity,String>{Page<AiBillingRecordEntity> findByAccountId(String accountId,Pageable page);}
