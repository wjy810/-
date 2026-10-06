package com.jobproof.modules.aibilling.domain;

import java.time.Instant;
import java.util.UUID;

public record BillingAuthorization(UUID id, UUID accountId, String chargeChainId, Money heldAmount, Status status, Instant createdAt) {
    public enum Status { HELD, SETTLED, RELEASED, EXPIRED }
    public BillingAuthorization settle() { requireHeld(); return new BillingAuthorization(id, accountId, chargeChainId, heldAmount, Status.SETTLED, createdAt); }
    public BillingAuthorization release() { requireHeld(); return new BillingAuthorization(id, accountId, chargeChainId, heldAmount, Status.RELEASED, createdAt); }
    public BillingAuthorization expire() { requireHeld(); return new BillingAuthorization(id, accountId, chargeChainId, heldAmount, Status.EXPIRED, createdAt); }
    private void requireHeld() { if (status != Status.HELD) throw new IllegalStateException("Authorization is final"); }
}