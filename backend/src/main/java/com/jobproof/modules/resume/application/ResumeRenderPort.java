package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The resume renderer service (renderer/, docs/phase2/03 §5.3): the same HTML templates as the
 * workbench preview, printed by Chromium.
 */
public interface ResumeRenderPort {

    RenderedDocument render(RenderRequest request);

    /**
     * @param pageLimit pages the content must fit on; the renderer reports overflow beyond it
     * @param photo PNG or JPEG data URL, or null
     */
    record RenderRequest(
            String format,
            String templateId,
            JsonNode design,
            JsonNode content,
            String photo,
            String title,
            int pageLimit,
            boolean firstPageOnly) {

        public static RenderRequest pdf(String templateId, JsonNode design, JsonNode content, String photo,
                String title, int pageLimit) {
            return new RenderRequest("pdf", templateId, design, content, photo, title, pageLimit, false);
        }
    }

    record RenderedDocument(
            byte[] body,
            String contentType,
            int pageCount,
            double overflowMm,
            String overflowSection,
            long renderMs) {
    }

    /** The renderer could not produce a document; {@link #retryable()} distinguishes outages from bad input. */
    final class RenderFailedException extends RuntimeException {
        private final String code;
        private final boolean retryable;

        public RenderFailedException(String code, String message, boolean retryable) {
            super(message);
            this.code = code;
            this.retryable = retryable;
        }

        public RenderFailedException(String code, String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.retryable = retryable;
        }

        public String code() {
            return code;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
