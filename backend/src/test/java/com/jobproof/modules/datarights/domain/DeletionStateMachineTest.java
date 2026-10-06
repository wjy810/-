package com.jobproof.modules.datarights.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.jobproof.modules.datarights.domain.DeletionStateMachine.Receipt;
import com.jobproof.modules.datarights.domain.DeletionStateMachine.ReceiptStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeletionStateMachineTest {

    @Test
    void submitIsNotCompleted() {
        assertEquals(DeletionStatus.SUBMITTED, DeletionStateMachine.afterSubmit());
        assertNotEquals(DeletionStatus.COMPLETED, DeletionStateMachine.afterSubmit());
    }

    @Test
    void auditRestrictionYieldsPartiallyRestricted() {
        DeletionStatus status = DeletionStateMachine.conclude(List.of(
                new Receipt("identity", ReceiptStatus.SUCCEEDED, "ok"),
                new Receipt("audit", ReceiptStatus.RESTRICTED, "retain index")));
        assertEquals(DeletionStatus.PARTIALLY_RESTRICTED, status);
    }

    @Test
    void participantFailureYieldsFailed() {
        DeletionStatus status = DeletionStateMachine.conclude(List.of(
                new Receipt("identity", ReceiptStatus.FAILED, "boom"),
                new Receipt("audit", ReceiptStatus.RESTRICTED, "retain")));
        assertEquals(DeletionStatus.FAILED, status);
    }
}
