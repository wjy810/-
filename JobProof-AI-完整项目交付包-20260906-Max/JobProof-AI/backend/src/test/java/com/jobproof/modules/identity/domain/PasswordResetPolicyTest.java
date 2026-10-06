package com.jobproof.modules.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobproof.shared.error.AppException;
import com.jobproof.shared.error.ErrorCategory;
import com.jobproof.shared.security.Tokens;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PasswordResetPolicyTest {

    @Test
    void expiredWhenNowReachesExpiresAt() {
        Instant expires = Instant.parse("2026-08-19T00:10:00Z");
        assertFalse(PasswordResetPolicy.expired(expires, Instant.parse("2026-08-19T00:09:59Z")));
        assertTrue(PasswordResetPolicy.expired(expires, expires));
        assertTrue(PasswordResetPolicy.expired(expires, Instant.parse("2026-08-19T00:10:01Z")));
        assertTrue(PasswordResetPolicy.expired(null, expires));
    }

    @Test
    void lockedAfterFiveFailedAttempts() {
        assertFalse(PasswordResetPolicy.locked(0));
        assertFalse(PasswordResetPolicy.locked(4));
        assertTrue(PasswordResetPolicy.locked(5));
        assertTrue(PasswordResetPolicy.locked(6));
    }

    @Test
    void matchesUsesHashedCodeAndRejectsWrongValue() {
        String hash = Tokens.sha256("123456");
        assertTrue(PasswordResetPolicy.matches(hash, "123456"));
        assertFalse(PasswordResetPolicy.matches(hash, "000000"));
        assertFalse(PasswordResetPolicy.matches(hash, null));
        assertFalse(PasswordResetPolicy.matches(null, "123456"));
    }

    @Test
    void invalidDoesNotRevealWhy() {
        AppException ex = PasswordResetPolicy.invalid();
        assertEquals(PasswordResetPolicy.INVALID_REASON, ex.reason());
        assertEquals(ErrorCategory.USER_CORRECTABLE, ex.category());
        assertEquals("验证码无效或已过期", ex.getMessage());
    }
}
