package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;

/**
 * 版本：生成中 → 待用户确认 → 已冻结 → 已归档。
 */
public enum ResumeVersionStatus {
    GENERATING,
    PENDING_USER_CONFIRMATION,
    FROZEN,
    ARCHIVED;

    public static ResumeVersionStatus parse(String raw) {
        try {
            return valueOf(raw);
        } catch (RuntimeException ex) {
            throw AppException.user("RESUME_VERSION_STATUS_UNKNOWN", "简历版本状态无法识别");
        }
    }

    public String label() {
        return switch (this) {
            case GENERATING -> "生成中";
            case PENDING_USER_CONFIRMATION -> "待用户确认";
            case FROZEN -> "已冻结";
            case ARCHIVED -> "已归档";
        };
    }

    public boolean frozenOrBound() {
        return this == FROZEN;
    }

    public boolean bindable() {
        return this == FROZEN;
    }

    public boolean contentImmutable() {
        return this == FROZEN || this == ARCHIVED;
    }

    public boolean pdfExportable() {
        return this == FROZEN;
    }
}
