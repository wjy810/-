package com.jobproof.modules.datarights.domain;

import com.jobproof.shared.error.AppException;
import java.util.List;

public final class DeletionStateMachine {

    private DeletionStateMachine() {
    }

    public static DeletionStatus afterSubmit() {
        return DeletionStatus.SUBMITTED;
    }

    public static DeletionStatus startProcessing(DeletionStatus current) {
        if (current != DeletionStatus.SUBMITTED) {
            throw AppException.conflict("DELETION_STATE_CONFLICT", "只有已提交的删除申请可以进入处理中");
        }
        return DeletionStatus.PROCESSING;
    }

    public static DeletionStatus conclude(List<Receipt> receipts) {
        boolean failed = receipts.stream().anyMatch(r -> r.status() == ReceiptStatus.FAILED);
        boolean restricted = receipts.stream().anyMatch(r -> r.status() == ReceiptStatus.RESTRICTED);
        if (failed) {
            return DeletionStatus.FAILED;
        }
        if (restricted) {
            return DeletionStatus.PARTIALLY_RESTRICTED;
        }
        if (receipts.isEmpty()) {
            return DeletionStatus.FAILED;
        }
        return DeletionStatus.COMPLETED;
    }

    public record Receipt(String moduleCode, ReceiptStatus status, String message) {
    }

    public enum ReceiptStatus {
        SUCCEEDED,
        RESTRICTED,
        FAILED,
        SKIPPED
    }
}
