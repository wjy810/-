package com.jobproof.modules.task.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TaskStatusTest {

    @Test
    void cancelRulesMatchSecondRoundContract() {
        assertTrue(TaskStatus.PENDING.cancellable());
        assertTrue(TaskStatus.RUNNING.cancellable());
        assertFalse(TaskStatus.SUCCEEDED.cancellable());
        assertFalse(TaskStatus.FAILED.cancellable());
        assertFalse(TaskStatus.CANCELLED.cancellable());
        assertTrue(TaskStatus.SUCCEEDED.terminal());
    }
}
