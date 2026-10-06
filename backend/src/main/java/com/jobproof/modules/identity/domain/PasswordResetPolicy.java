package com.jobproof.modules.identity.domain;

import com.jobproof.shared.error.AppException;
import com.jobproof.shared.security.Tokens;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * 重置验证码不变量：一次性、过期作废、错误次数上限；对外不区分原因。
 */
public final class PasswordResetPolicy {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final String INVALID_REASON = "RESET_CODE_INVALID";
    public static final String INVALID_MESSAGE = "验证码无效或已过期";

    private PasswordResetPolicy() {
    }

    public static boolean expired(Instant expiresAt, Instant now) {
        return expiresAt == null || now == null || !expiresAt.isAfter(now);
    }

    public static boolean locked(int failedAttempts) {
        return failedAttempts >= MAX_FAILED_ATTEMPTS;
    }

    public static boolean matches(String expectedHash, String rawCode) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        String actual = Tokens.sha256(rawCode == null ? "" : rawCode);
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public static AppException invalid() {
        return AppException.user(INVALID_REASON, INVALID_MESSAGE);
    }
}
