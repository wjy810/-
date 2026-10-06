package com.jobproof.modules.resume.domain;

import java.util.List;

public interface ResumeTemplatePreviewRenderer {
    String version();

    List<RenderResult> render(List<RenderRequest> requests);

    record RenderRequest(String catalogEntryId, String fileHash, byte[] docx) {}

    record RenderResult(String catalogEntryId, List<byte[]> pages, String errorCode) {
        public static RenderResult success(String catalogEntryId, List<byte[]> pages) {
            return new RenderResult(catalogEntryId, List.copyOf(pages), null);
        }

        public static RenderResult failure(String catalogEntryId, String errorCode) {
            return new RenderResult(catalogEntryId, List.of(), errorCode);
        }

        public boolean successful() {
            return errorCode == null && !pages.isEmpty();
        }
    }
}
