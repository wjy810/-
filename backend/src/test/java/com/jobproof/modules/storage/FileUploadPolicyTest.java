package com.jobproof.modules.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobproof.shared.error.AppException;
import org.junit.jupiter.api.Test;

class FileUploadPolicyTest {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
    };
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    private static final byte[] WEBP = {
            'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'
    };

    @Test
    void emptyAndOversizedAreRejected() {
        AppException empty = assertThrows(AppException.class, () -> FileUploadPolicy.inspect("a.png", new byte[0]));
        assertEquals("FILE_INVALID", empty.reason());

        byte[] tooLarge = new byte[FileUploadPolicy.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, tooLarge, 0, PDF.length);
        AppException oversized = assertThrows(AppException.class, () -> FileUploadPolicy.inspect("a.pdf", tooLarge));
        assertEquals("FILE_TOO_LARGE", oversized.reason());
    }

    @Test
    void disallowedAndSpoofedTypesAreRejected() {
        AppException exe = assertThrows(AppException.class,
                () -> FileUploadPolicy.inspect("tool.exe", new byte[] {'M', 'Z', 0x00}));
        assertEquals("FILE_TYPE_NOT_ALLOWED", exe.reason());

        AppException htmlAsPdf = assertThrows(AppException.class,
                () -> FileUploadPolicy.inspect("cert.pdf", "<html>".getBytes()));
        assertEquals("FILE_TYPE_NOT_ALLOWED", htmlAsPdf.reason());

        AppException pngAsJpeg = assertThrows(AppException.class,
                () -> FileUploadPolicy.inspect("shot.jpg", PNG));
        assertEquals("FILE_TYPE_NOT_ALLOWED", pngAsJpeg.reason());

        AppException noExt = assertThrows(AppException.class, () -> FileUploadPolicy.inspect("plain", PNG));
        assertEquals("FILE_TYPE_NOT_ALLOWED", noExt.reason());
    }

    @Test
    void allowedTypesKeepMatchingMime() {
        assertEquals("image/png", FileUploadPolicy.inspect("award.png", PNG).contentType());
        assertEquals("image/jpeg", FileUploadPolicy.inspect("award.JPG", JPEG).contentType());
        assertEquals("application/pdf", FileUploadPolicy.inspect("cert.PDF", PDF).contentType());
        assertEquals("image/webp", FileUploadPolicy.inspect("shot.webp", WEBP).contentType());
        byte[] maxPdf = new byte[FileUploadPolicy.MAX_BYTES];
        System.arraycopy(PDF, 0, maxPdf, 0, PDF.length);
        FileUploadPolicy.Accepted max = FileUploadPolicy.inspect("full.pdf", maxPdf);
        assertEquals("application/pdf", max.contentType());
        assertEquals("full.pdf", max.safeFilename());
    }

    @Test
    void pathTraversalNameKeepsOnlyBasename() {
        FileUploadPolicy.Accepted accepted = FileUploadPolicy.inspect("..\\..\\etc\\passwd.png", PNG);
        assertEquals("passwd.png", accepted.safeFilename());
        assertEquals("secret.png", FileUploadPolicy.inspect("../../secret.png", PNG).safeFilename());
        assertEquals("file.png", FileUploadPolicy.safeFilename("..png"));
    }

    @Test
    void objectKeyIsAccountIsolated() {
        String accountId = "11111111-1111-1111-1111-111111111111";
        String fileId = "22222222-2222-2222-2222-222222222222";
        String key = FileUploadPolicy.ownedPrivateKey(accountId, fileId, "award.png");
        assertEquals("private/" + accountId + "/" + fileId + "-award.png", key);
        assertTrue(key.startsWith("private/" + accountId + "/"));
        assertFalse(key.contains(".."));
        assertThrows(IllegalStateException.class, () -> FileUploadPolicy.ownedPrivateKey("../x", fileId, "a.png"));
        assertThrows(IllegalStateException.class, () -> FileUploadPolicy.ownedPrivateKey(accountId, fileId, "../a.png"));
    }
}
