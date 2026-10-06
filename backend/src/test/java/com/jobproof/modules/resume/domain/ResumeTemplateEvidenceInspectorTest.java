package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ResumeTemplateEvidenceInspectorTest {

    @Test
    void acceptsSupportedImmutableEvidenceFormats() {
        assertThat(ResumeTemplateEvidenceInspector.inspect("controlled report".getBytes(StandardCharsets.UTF_8)))
                .extracting(ResumeTemplateEvidenceInspector.Inspection::accepted,
                        ResumeTemplateEvidenceInspector.Inspection::contentType)
                .containsExactly(true, "text/plain;charset=UTF-8");
        assertThat(ResumeTemplateEvidenceInspector.inspect("%PDF-1.7\nbody\n%%EOF".getBytes(StandardCharsets.US_ASCII)))
                .extracting(ResumeTemplateEvidenceInspector.Inspection::accepted,
                        ResumeTemplateEvidenceInspector.Inspection::contentType)
                .containsExactly(true, "application/pdf");
        assertThat(ResumeTemplateEvidenceInspector.inspect(
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01}))
                .extracting(ResumeTemplateEvidenceInspector.Inspection::accepted,
                        ResumeTemplateEvidenceInspector.Inspection::contentType)
                .containsExactly(true, "image/png");
        assertThat(ResumeTemplateEvidenceInspector.inspect(
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01, (byte) 0xff, (byte) 0xd9}))
                .extracting(ResumeTemplateEvidenceInspector.Inspection::accepted,
                        ResumeTemplateEvidenceInspector.Inspection::contentType)
                .containsExactly(true, "image/jpeg");
    }

    @Test
    void rejectsEmptyOversizedAndUnknownBinaryContent() {
        assertThat(ResumeTemplateEvidenceInspector.inspect(new byte[0]).rejectionCode()).isEqualTo("EMPTY_FILE");
        assertThat(ResumeTemplateEvidenceInspector.inspect(
                new byte[ResumeTemplateEvidenceInspector.MAX_SIZE_BYTES + 1]).rejectionCode())
                .isEqualTo("FILE_TOO_LARGE");
        assertThat(ResumeTemplateEvidenceInspector.inspect(new byte[] {0x00, 0x01, 0x02}).rejectionCode())
                .isEqualTo("UNSUPPORTED_CONTENT");
    }
}
