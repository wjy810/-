package com.jobproof.modules.aibilling.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(UUID id, UUID accountId, Type type, Money amount, Money balanceSnapshot, String referenceType, String referenceId, String idempotencyKey, long versionNo, Instant occurredAt) {
    public LedgerEntry { if (amount.isNegative()) throw new IllegalArgumentException("Ledger amount cannot be negative"); }
    public enum Type { TOP_UP, ADMIN_CREDIT, ADMIN_DEBIT, HOLD, RELEASE, CHARGE, ADJUSTMENT_CHARGE, DEBT_RECOVERY, REFUND }
}