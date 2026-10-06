package com.jobproof.modules.aibilling.application;

import java.util.Objects;
import java.util.UUID;

public record IdempotencyRequest(UUID accountId, String operation, String key, String requestFingerprint) {
    public IdempotencyRequest {
        Objects.requireNonNull(accountId, "accountId");
        if (operation == null || operation.isBlank() || key == null || key.isBlank()
                || requestFingerprint == null || requestFingerprint.isBlank()) {
            throw new IllegalArgumentException("Idempotency scope and fingerprint are required");
        }
    }
}
