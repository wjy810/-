package com.jobproof.modules.resume.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jobproof.shared.error.AppException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeFreezePolicyTest {

    @Test
    void unconfirmedAiBlocksReadyAndFreeze() {
        AppException ready = assertThrows(AppException.class, () -> ResumeFreezePolicy.assertCanMarkReady(
                ResumeMasterStatus.DRAFT, true, List.of("c1"), List.of()));
        assertEquals("UNCONFIRMED_AI_FACTS", ready.reason());

        AppException freeze = assertThrows(AppException.class, () -> ResumeFreezePolicy.assertReadyAndResolved(
                ResumeMasterStatus.READY_TO_EXPORT, true, List.of("c1"), List.of()));
        assertEquals("UNCONFIRMED_AI_FACTS", freeze.reason());
    }

    @Test
    void unresolvedOutcomeBlocksReadyAndFreeze() {
        KeyOutcome open = new KeyOutcome("o1", "完成招聘平台后端", null, false);
        AppException ready = assertThrows(AppException.class, () -> ResumeFreezePolicy.assertCanMarkReady(
                ResumeMasterStatus.DRAFT, false, List.of(), List.of(open)));
        assertEquals("OUTCOME_EVIDENCE_REQUIRED", ready.reason());

        AppException freeze = assertThrows(AppException.class, () -> ResumeFreezePolicy.assertReadyAndResolved(
                ResumeMasterStatus.READY_TO_EXPORT, false, List.of(), List.of(open)));
        assertEquals("OUTCOME_EVIDENCE_REQUIRED", freeze.reason());
    }

    @Test
    void evidenceOrWaiveAllowsReadyAndFreezeFromReadyMaster() {
        KeyOutcome linked = new KeyOutcome("o1", "完成招聘平台后端", "ev-1", false);
        KeyOutcome waived = new KeyOutcome("o2", "口头成果", null, true);
        assertDoesNotThrow(() -> ResumeFreezePolicy.assertCanMarkReady(
                ResumeMasterStatus.DRAFT, false, List.of(), List.of(linked, waived)));
        assertDoesNotThrow(() -> ResumeFreezePolicy.assertReadyAndResolved(
                ResumeMasterStatus.READY_TO_EXPORT, false, List.of(), List.of(linked, waived)));
    }

    @Test
    void draftMasterCannotFreezeEvenIfGatesPass() {
        AppException ex = assertThrows(AppException.class, () -> ResumeFreezePolicy.assertReadyAndResolved(
                ResumeMasterStatus.DRAFT, false, List.of(), List.of()));
        assertEquals("MASTER_NOT_READY_TO_EXPORT", ex.reason());
    }

    @Test
    void frozenVersionCannotBeMutatedOrReboundFromDraft() {
        AppException immutable = assertThrows(AppException.class,
                () -> ResumeFreezePolicy.assertContentImmutable(ResumeVersionStatus.FROZEN));
        assertEquals("RESUME_VERSION_IMMUTABLE", immutable.reason());
        assertDoesNotThrow(() -> ResumeFreezePolicy.assertBindable(ResumeVersionStatus.FROZEN));
        AppException notFrozen = assertThrows(AppException.class,
                () -> ResumeFreezePolicy.assertBindable(ResumeVersionStatus.PENDING_USER_CONFIRMATION));
        assertEquals("RESUME_VERSION_NOT_FROZEN", notFrozen.reason());
    }

    @Test
    void archivedMasterCanRestoreToPrevious() {
        assertEquals(ResumeMasterStatus.READY_TO_EXPORT,
                ResumeMasterPolicy.restore(ResumeMasterStatus.ARCHIVED, ResumeMasterStatus.READY_TO_EXPORT));
        assertThrows(AppException.class, () -> ResumeMasterPolicy.archive(ResumeMasterStatus.ARCHIVED));
        AppException notArchived = assertThrows(AppException.class,
                () -> ResumeMasterPolicy.restore(ResumeMasterStatus.DRAFT, ResumeMasterStatus.DRAFT));
        assertEquals("RESUME_NOT_ARCHIVED", notArchived.reason());
        AppException missing = assertThrows(AppException.class,
                () -> ResumeMasterPolicy.restore(ResumeMasterStatus.ARCHIVED, ResumeMasterStatus.ARCHIVED));
        assertEquals("RESUME_RESTORE_TARGET_MISSING", missing.reason());
        AppException archivedReady = assertThrows(AppException.class, () -> ResumeFreezePolicy.assertCanMarkReady(
                ResumeMasterStatus.ARCHIVED, false, List.of(), List.of()));
        assertEquals("RESUME_ARCHIVED", archivedReady.reason());
    }

    @Test
    void generatingArchivedCannotBindAndCustomizeMustNotFreeze() {
        AppException generating = assertThrows(AppException.class,
                () -> ResumeFreezePolicy.assertBindable(ResumeVersionStatus.GENERATING));
        assertEquals("RESUME_VERSION_NOT_FROZEN", generating.reason());
        AppException archived = assertThrows(AppException.class,
                () -> ResumeFreezePolicy.assertBindable(ResumeVersionStatus.ARCHIVED));
        assertEquals("RESUME_VERSION_NOT_FROZEN", archived.reason());
        assertDoesNotThrow(() -> ResumeFreezePolicy.assertCustomizeNotFrozen(
                ResumeVersionStatus.PENDING_USER_CONFIRMATION));
        AppException frozen = assertThrows(AppException.class,
                () -> ResumeFreezePolicy.assertCustomizeNotFrozen(ResumeVersionStatus.FROZEN));
        assertEquals("RESUME_VERSION_IMMUTABLE", frozen.reason());
    }

    @Test
    void unknownTemplateIsRejected() {
        AppException ex = assertThrows(AppException.class, () -> ResumeTemplateCode.parse("BANKING"));
        assertEquals("RESUME_TEMPLATE_UNKNOWN", ex.reason());
    }
}
