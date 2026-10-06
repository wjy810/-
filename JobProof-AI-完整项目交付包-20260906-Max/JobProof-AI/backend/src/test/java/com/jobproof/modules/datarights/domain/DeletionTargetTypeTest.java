package com.jobproof.modules.datarights.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobproof.shared.error.AppException;
import org.junit.jupiter.api.Test;

class DeletionTargetTypeTest {

    @Test
    void aliasesResolveToCanonicalTypes() {
        assertEquals(DeletionTargetType.CAREER_RECORD, DeletionTargetType.parse("RECORD"));
        assertEquals(DeletionTargetType.CAREER_FILE, DeletionTargetType.parse("career-file"));
        assertEquals(DeletionTargetType.CAREER_PROFILE, DeletionTargetType.parse("PROFILE"));
        assertEquals(DeletionTargetType.RESUME_MASTER, DeletionTargetType.parse("resume"));
        assertEquals(DeletionTargetType.RESUME_VERSION, DeletionTargetType.parse("frozen-version"));
    }

    @Test
    void unknownTypeIsUserCorrectable() {
        AppException ex = assertThrows(AppException.class, () -> DeletionTargetType.parse("MENTOR_FEEDBACK"));
        assertEquals("TARGET_TYPE_UNSUPPORTED", ex.reason());
    }
}
