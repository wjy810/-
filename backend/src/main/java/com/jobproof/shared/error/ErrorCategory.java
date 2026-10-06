package com.jobproof.shared.error;

public enum ErrorCategory {
    USER_CORRECTABLE,
    FORBIDDEN,
    CONFLICT,
    RATE_LIMITED,
    DEPENDENCY_FAILED,
    SYSTEM_FAILURE,
    REQUIRES_HUMAN,
    UNAUTHENTICATED
}
