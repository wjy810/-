package com.jobproof.modules.aiconfig.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobproof.shared.error.AppException;
import org.junit.jupiter.api.Test;

class AiUserChannelServiceTest {
    @Test
    void masksApiKeyWithoutReturningPlaintext() {
        String masked = AiUserChannelService.mask("secret-value-1234");
        assertEquals("****1234", masked);
        assertFalse(masked.contains("secret-value"));
    }

    @Test
    void forbiddenReasonMatchesFrozenContract() {
        AppException exception = AppException.forbidden("AI_CHANNEL_FORBIDDEN", "forbidden");
        assertEquals("AI_CHANNEL_FORBIDDEN", exception.reason());
    }

    @Test
    void conflictReasonMatchesFrozenVersionContract() {
        AppException exception = AppException.conflict("AI_VERSION_CONFLICT", "stale");
        assertEquals("AI_VERSION_CONFLICT", exception.reason());
        assertThrows(IllegalArgumentException.class, () -> "x".repeat(-1));
    }
}
