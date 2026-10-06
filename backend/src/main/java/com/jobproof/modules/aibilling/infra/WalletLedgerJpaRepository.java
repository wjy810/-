package com.jobproof.modules.aibilling.infra;
import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface WalletLedgerJpaRepository extends JpaRepository<WalletLedgerEntity,String>{Page<WalletLedgerEntity> findByAccountId(String accountId,Pageable page);Page<WalletLedgerEntity> findByAccountIdAndLedgerType(String accountId,String type,Pageable page);}
