package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;

/** P0A 模板：软件开发、测试、数据分析、产品。 */
public enum ResumeTemplateCode {
    SOFTWARE_DEV,
    QA,
    DATA_ANALYSIS,
    PRODUCT;

    public static ResumeTemplateCode parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw AppException.user("RESUME_TEMPLATE_UNKNOWN", "P0A 模板仅支持 SOFTWARE_DEV、QA、DATA_ANALYSIS、PRODUCT");
        }
    }

    public String title() {
        return switch (this) {
            case SOFTWARE_DEV -> "软件开发简历";
            case QA -> "测试简历";
            case DATA_ANALYSIS -> "数据分析简历";
            case PRODUCT -> "产品简历";
        };
    }
}
