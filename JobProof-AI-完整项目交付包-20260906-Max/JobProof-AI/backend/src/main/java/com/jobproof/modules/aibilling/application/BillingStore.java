package com.jobproof.modules.aibilling.application;

import com.jobproof.modules.aibilling.domain.*;
import java.util.Optional;
import java.util.UUID;

public interface BillingStore {
    WalletAccount getWallet(UUID accountId);
    void saveWallet(WalletAccount wallet, long expectedVersionNo);
    void appendLedger(LedgerEntry entry);
    BillingAuthorization getAuthorization(UUID authorizationId);
    void saveAuthorization(BillingAuthorization authorization);
    Optional<BillingAuthorization> findAuthorizationByChargeChainId(String chargeChainId);
    Optional<IdempotencyRecord> findIdempotency(IdempotencyRequest request);
    void saveIdempotency(IdempotencyRecord record);
    Optional<BillingRecord> findBilling(UUID billingId);
    void saveBilling(BillingRecord billing);
}
