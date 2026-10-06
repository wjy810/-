package com.jobproof.modules.audit.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuditSanitizeTest {

    @Test
    void stripsPasswordAndSixDigitCodes() {
        String sanitized = AuditService.sanitize("password=secret123 code=123456 kept");
        assertFalse(sanitized.contains("secret123"));
        assertFalse(sanitized.contains("123456"));
        assertTrue(sanitized.contains("kept"));
    }
}
