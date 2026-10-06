package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;
import java.util.Locale;

public enum ResumePdfExportMode {
    STANDARD,
    ANONYMOUS;

    public static ResumePdfExportMode parse(String raw) {
        if (raw == null || raw.isBlank()) return STANDARD;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw AppException.user("RESUME_PDF_EXPORT_MODE_INVALID", "PDF 导出版本仅支持正式版或匿名版");
        }
    }
}
