package com.jobproof.modules.aibilling.domain;

import java.util.UUID;

public record BillingRecord(
        UUID id,
        UUID accountId,
        UUID authorizationId,
        Money actualAmount,
        Money refundedAmount,
        Status status) {

    public enum Status {
        PENDING, SETTLED, PAYMENT_REQUIRED, VOID, PARTIALLY_REFUNDED, REFUNDED
    }

    public BillingRecord {
        if (actualAmount.isNegative() || refundedAmount.isNegative()
                || refundedAmount.compareTo(actualAmount) > 0) {
            throw new IllegalArgumentException("Invalid bill amounts");
        }
    }

    public Money refundable() {
        return actualAmount.subtract(refundedAmount);
    }

    public BillingRecord refund(Money amount) {
        if (!amount.isPositive() || amount.compareTo(refundable()) > 0) {
            throw new IllegalArgumentException("Cumulative refund exceeds actual bill");
        }
        Money nextRefunded = refundedAmount.add(amount);
        Status nextStatus = nextRefunded.compareTo(actualAmount) == 0
                ? Status.REFUNDED : Status.PARTIALLY_REFUNDED;
        return new BillingRecord(id, accountId, authorizationId, actualAmount, nextRefunded, nextStatus);
    }
}
