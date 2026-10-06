package com.jobproof.modules.datarights.domain;

public enum DeletionStatus {
    SUBMITTED,
    PROCESSING,
    PARTIALLY_RESTRICTED,
    COMPLETED,
    FAILED;

    public static DeletionStatus fromChineseOrCode(String raw) {
        return valueOf(raw);
    }

    public String apiValue() {
        return name();
    }
}
