package com.jobproof.modules.resume.domain;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class ResumeTemplateEvidenceInspector {
    public static final int MAX_SIZE_BYTES = 10 * 1024 * 1024;

    private ResumeTemplateEvidenceInspector() {}

    public static Inspection inspect(byte[] content) {
        if (content == null || content.length == 0) {
            return Inspection.rejected("EMPTY_FILE");
        }
        if (content.length > MAX_SIZE_BYTES) {
            return Inspection.rejected("FILE_TOO_LARGE");
        }
        if (startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII)) && containsPdfEof(content)) {
            return Inspection.accepted("application/pdf", "pdf");
        }
        if (startsWith(content, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return Inspection.accepted("image/png", "png");
        }
        if (content.length >= 4 && content[0] == (byte) 0xff && content[1] == (byte) 0xd8
                && content[content.length - 2] == (byte) 0xff && content[content.length - 1] == (byte) 0xd9) {
            return Inspection.accepted("image/jpeg", "jpg");
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
            if (text.isBlank() || text.indexOf('\0') >= 0 || hasUnsafeControls(text)) {
                return Inspection.rejected("UNSUPPORTED_CONTENT");
            }
            return Inspection.accepted("text/plain;charset=UTF-8", "txt");
        } catch (CharacterCodingException exception) {
            return Inspection.rejected("UNSUPPORTED_CONTENT");
        }
    }

    private static boolean hasUnsafeControls(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isISOControl(current) && current != '\r' && current != '\n' && current != '\t') {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPdfEof(byte[] content) {
        byte[] marker = "%%EOF".getBytes(StandardCharsets.US_ASCII);
        int start = Math.max(0, content.length - 1024);
        for (int i = start; i <= content.length - marker.length; i++) {
            if (matchesAt(content, marker, i)) return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && matchesAt(content, prefix, 0);
    }

    private static boolean matchesAt(byte[] content, byte[] expected, int offset) {
        for (int i = 0; i < expected.length; i++) {
            if (content[offset + i] != expected[i]) return false;
        }
        return true;
    }

    public record Inspection(boolean accepted, String contentType, String extension, String rejectionCode) {
        static Inspection accepted(String contentType, String extension) {
            return new Inspection(true, contentType, extension, null);
        }

        static Inspection rejected(String code) {
            return new Inspection(false, null, null, code);
        }
    }
}
