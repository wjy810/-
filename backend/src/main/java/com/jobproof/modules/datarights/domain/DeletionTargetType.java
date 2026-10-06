package com.jobproof.modules.datarights.domain;

import com.jobproof.shared.error.AppException;
import java.util.Locale;

public enum DeletionTargetType {
    CAREER_RECORD,
    CAREER_FILE,
    CAREER_PROFILE,
    RESUME_MASTER,
    RESUME_VERSION;

    public static DeletionTargetType parse(String raw) {
        if (raw == null || raw.isBlank()) throw AppException.user("TARGET_REQUIRED", "对象级删除必须指定对象类型与 ID");
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "CAREER_RECORD", "RECORD" -> CAREER_RECORD;
            case "CAREER_FILE", "FILE" -> CAREER_FILE;
            case "CAREER_PROFILE", "PROFILE" -> CAREER_PROFILE;
            case "RESUME_MASTER", "RESUME" -> RESUME_MASTER;
            case "RESUME_VERSION", "FROZEN_RESUME", "FROZEN_VERSION" -> RESUME_VERSION;
            default -> throw AppException.user("TARGET_TYPE_UNSUPPORTED", "这类内容不支持永久删除，请使用归档");
        };
    }

    public String apiValue() { return name(); }
    public String moduleCode() {
        return switch (this) {
            case CAREER_RECORD, CAREER_FILE, CAREER_PROFILE -> "career-library";
            case RESUME_MASTER, RESUME_VERSION -> "resume";
        };
    }
    public String label() {
        return switch (this) {
            case CAREER_RECORD -> "求职资料记录";
            case CAREER_FILE -> "求职资料文件";
            case CAREER_PROFILE -> "求职职业主档";
            case RESUME_MASTER -> "简历主档";
            case RESUME_VERSION -> "简历冻结版本";
        };
    }
}
