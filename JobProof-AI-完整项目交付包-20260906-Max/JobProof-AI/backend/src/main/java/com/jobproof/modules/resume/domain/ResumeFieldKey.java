package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;

public enum ResumeFieldKey {
    EDUCATION,
    EXPERIENCE,
    PROJECTS,
    SKILLS,
    CERTIFICATES,
    SELF_INTRO,
    KEY_OUTCOMES,
    TITLE;

    public static ResumeFieldKey parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw AppException.user("RESUME_FIELD_UNKNOWN", "简历字段只能是教育、经历、项目、技能、证书、自我介绍、关键成果或标题");
        }
    }
}
