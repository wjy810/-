package com.jobproof.modules.resume.domain;

import java.util.List;

/**
 * Resolves estimated slot overflow against the real PDF renderer. Slot capacities
 * are layout guidance; the rendered page boundary remains the export gate.
 */
public final class ResumePdfPreflight {
    private ResumePdfPreflight() {
    }

    public static ResumeOverflowEngine.Report evaluate(
            ResumeDocumentModel document,
            ResumeLayoutDefinition definition,
            String variant,
            String rendererProtocol) {
        ResumeOverflowEngine.Report estimated = ResumeOverflowEngine.evaluate(document, definition);
        if (hasDocumentOverflow(estimated)) return estimated;

        try {
            ResumePdfRenderer.render("PDF preflight", document, definition, variant, rendererProtocol);
            return estimated.valid()
                    ? estimated
                    : new ResumeOverflowEngine.Report(true, estimated.consumedUnits(), List.of());
        } catch (IllegalStateException exception) {
            if (isPageOverflow(exception)) return estimated;
            throw exception;
        }
    }

    private static boolean hasDocumentOverflow(ResumeOverflowEngine.Report report) {
        return report.items().stream().anyMatch(item -> "DOCUMENT".equals(item.slotKey()));
    }

    private static boolean isPageOverflow(IllegalStateException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("maxPages=");
    }
}
