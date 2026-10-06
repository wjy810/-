package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;

public enum ResumeCandidateStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CORRECTED;

    public static ResumeCandidateStatus parse(String raw) {
        try {
            return valueOf(raw);
        } catch (RuntimeException ex) {
            throw AppException.user("RESUME_CANDIDATE_STATUS_UNKNOWN", "简历候选状态无法识别");
        }
    }

    public boolean pending() {
        return this == PENDING;
    }
}
