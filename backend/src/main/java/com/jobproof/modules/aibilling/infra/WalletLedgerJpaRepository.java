package com.jobproof.modules.aibilling.infra;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WalletLedgerJpaRepository extends JpaRepository<WalletLedgerEntity, String> {
    Page<WalletLedgerEntity> findByAccountId(String accountId, Pageable page);

    Page<WalletLedgerEntity> findByAccountIdAndLedgerType(
            String accountId, String type, Pageable page);

    @Query(
            value =
                    "SELECT l.* FROM wallet_ledger l JOIN wallet_account w ON"
                        + " w.id=l.wallet_account_id WHERE l.account_id=:id AND w.currency='CNY'"
                        + " AND (:type IS NULL OR l.ledger_type=:type)",
            countQuery =
                    "SELECT COUNT(*) FROM wallet_ledger l JOIN wallet_account w ON"
                        + " w.id=l.wallet_account_id WHERE l.account_id=:id AND w.currency='CNY'"
                        + " AND (:type IS NULL OR l.ledger_type=:type)",
            nativeQuery = true)
    Page<WalletLedgerEntity> findCny(
            @Param("id") String id, @Param("type") String type, Pageable page);
}
