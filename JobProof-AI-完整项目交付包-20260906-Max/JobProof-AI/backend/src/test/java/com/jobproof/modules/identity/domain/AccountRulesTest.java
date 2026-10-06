package com.jobproof.modules.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobproof.shared.error.AppException;
import com.jobproof.shared.error.ErrorCategory;
import org.junit.jupiter.api.Test;

class AccountRulesTest {

    @Test
    void normalizeEmailRejectsBlankAndBadFormat() {
        AppException blank = assertThrows(AppException.class, () -> AccountRules.normalizeEmail("  "));
        assertEquals("EMAIL_INVALID", blank.reason());
        assertEquals(ErrorCategory.USER_CORRECTABLE, blank.category());

        AppException bad = assertThrows(AppException.class, () -> AccountRules.normalizeEmail("not-an-email"));
        assertEquals("EMAIL_INVALID", bad.reason());
        assertEquals("seeker@example.com", AccountRules.normalizeEmail("  Seeker@Example.COM "));
    }

    @Test
    void passwordMustBeStrongEnoughAndNotEqualEmail() {
        AppException shortPwd = assertThrows(AppException.class, () -> AccountRules.validatePassword("short", "a@b.com"));
        assertEquals("PASSWORD_TOO_WEAK", shortPwd.reason());
        assertEquals(ErrorCategory.USER_CORRECTABLE, shortPwd.category());

        AppException sameAsEmail = assertThrows(AppException.class,
                () -> AccountRules.validatePassword("Seeker@Example.com", "seeker@example.com"));
        assertEquals("PASSWORD_TOO_WEAK", sameAsEmail.reason());

        AppException tooLong = assertThrows(AppException.class,
                () -> AccountRules.validatePassword("x".repeat(73), "a@b.com"));
        assertEquals("PASSWORD_TOO_WEAK", tooLong.reason());
        assertEquals(ErrorCategory.USER_CORRECTABLE, tooLong.category());

        AccountRules.validatePassword("Passw0rd!", "seeker@example.com");
        AccountRules.validatePassword("password", "seeker@example.com");
        AccountRules.validatePassword("x".repeat(8), "a@b.com");
        AccountRules.validatePassword("x".repeat(72), "a@b.com");
        AccountRules.validatePassword("\u4e2d".repeat(24), "a@b.com");
        assertEquals("PASSWORD_TOO_WEAK", assertThrows(AppException.class,
                () -> AccountRules.validatePassword("\u4e2d".repeat(25), "a@b.com")).reason());
    }
}
