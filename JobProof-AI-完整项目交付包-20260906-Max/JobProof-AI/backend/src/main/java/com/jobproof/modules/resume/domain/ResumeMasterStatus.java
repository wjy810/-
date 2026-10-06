package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;

/**
 * 主档：草稿 → 待确认 → 可导出 → 已归档。已归档可恢复为归档前状态。
 */
public enum ResumeMasterStatus {
    DRAFT,
    PENDING_CONFIRMATION,
    READY_TO_EXPORT,
    ARCHIVED;

    public static ResumeMasterStatus parse(String raw) {
        try {
            return valueOf(raw);
        } catch (RuntimeException ex) {
            throw AppException.user("RESUME_STATUS_UNKNOWN", "简历主档状态无法识别");
        }
    }

    public String label() {
        return switch (this) {
            case DRAFT -> "草稿";
            case PENDING_CONFIRMATION -> "待确认";
            case READY_TO_EXPORT -> "可导出";
            case ARCHIVED -> "已归档";
        };
    }

    public boolean editable() {
        return this != ARCHIVED;
    }
}
