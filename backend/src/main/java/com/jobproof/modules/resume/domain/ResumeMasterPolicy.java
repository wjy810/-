package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;

public final class ResumeMasterPolicy {

    private ResumeMasterPolicy() {
    }

    public static ResumeMasterStatus archive(ResumeMasterStatus current) {
        if (current == ResumeMasterStatus.ARCHIVED) {
            throw AppException.conflict("RESUME_ALREADY_ARCHIVED", "主档已归档");
        }
        return ResumeMasterStatus.ARCHIVED;
    }

    public static ResumeMasterStatus restore(ResumeMasterStatus current, ResumeMasterStatus beforeArchive) {
        if (current != ResumeMasterStatus.ARCHIVED) {
            throw AppException.conflict("RESUME_NOT_ARCHIVED", "只有已归档主档可以恢复");
        }
        if (beforeArchive == null || beforeArchive == ResumeMasterStatus.ARCHIVED) {
            throw AppException.conflict("RESUME_RESTORE_TARGET_MISSING", "缺少归档前状态，无法恢复");
        }
        return beforeArchive;
    }

    public static void assertEditable(ResumeMasterStatus current) {
        if (!current.editable()) {
            throw AppException.conflict("RESUME_ARCHIVED", "已归档主档不可编辑，请先恢复");
        }
    }
}
