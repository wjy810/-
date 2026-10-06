package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;

public enum ResumeCreateMode {
    BLANK,
    TEMPLATE,
    IMPORT;

    public static ResumeCreateMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return BLANK;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw AppException.user("RESUME_CREATE_MODE_UNKNOWN", "创建方式只能是 BLANK、TEMPLATE 或 IMPORT");
        }
    }
}
