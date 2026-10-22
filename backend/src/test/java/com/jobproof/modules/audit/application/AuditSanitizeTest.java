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

    @Test
    void masksWholeBearerAndQuotedMultiwordValues() {
        for (String value : java.util.List.of("Authorization: Bearer DEMO_TOKEN_ABC", "aUtHoRiZaTiOn: bEaReR DEMO_TOKEN_DEF", "{\"authorization\":\"Bearer DEMO_TOKEN_GHI\"}", "secret=\"multi word credential\" kept")) {
            String clean=AuditService.sanitize(value);
            assertFalse(clean.contains("DEMO_TOKEN"));
            assertFalse(clean.contains("multi"));
            assertFalse(clean.contains("credential"));
        }
    }
    @Test
    void masksBeforeTruncationAndHandlesAlreadyTruncatedQuotedSecrets() {
        String secret = "private word ".repeat(80);
        for (String input : java.util.List.of("secret=\"" + secret + "\" kept", "secret=\"" + secret,
                "api_key='" + secret + "' kept", "authorization=\"Bearer " + secret)) {
            String clean = AuditService.sanitize(input);
            assertFalse(clean.contains("private"));
            assertFalse(clean.contains("word"));
            assertTrue(clean.length() <= 512);
        }
    }
}
