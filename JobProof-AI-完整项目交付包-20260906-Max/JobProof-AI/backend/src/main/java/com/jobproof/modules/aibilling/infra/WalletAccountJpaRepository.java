package com.jobproof.modules.aibilling.infra;
import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface WalletAccountJpaRepository extends JpaRepository<WalletAccountEntity,String>{Optional<WalletAccountEntity> findByAccountIdAndCurrency(String accountId,String currency);}
