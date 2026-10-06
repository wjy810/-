package com.jobproof.modules.aibilling.application;

import java.util.Objects;

public record IdempotencyRecord(IdempotencyRequest request, Object result) {
    public IdempotencyRecord {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(result, "result");
    }
}
