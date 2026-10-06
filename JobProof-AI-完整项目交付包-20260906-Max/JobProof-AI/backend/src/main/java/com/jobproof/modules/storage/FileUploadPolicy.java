package com.jobproof.modules.storage;

import com.jobproof.shared.error.AppException;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * P0A 本人核验附件闸：非空、≤1MB、扩展名白名单，且魔数须与扩展名一致。
 * 不信任客户端 Content-Type。路径穿越文件名只取最后一段。
 */
public final class FileUploadPolicy {

    public static final int MAX_BYTES = 1024 * 1024;

    private static final int MAX_STEM_LEN = 80;
    private static final Pattern TOKEN = Pattern.compile("[a-zA-Z0-9-]{1,64}");
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private FileUploadPolicy() {
    }

    public record Accepted(String safeFilename, String contentType) {
    }

    public static Accepted inspect(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) {
            throw AppException.user("FILE_INVALID", "文件不能为空");
        }
        if (content.length > MAX_BYTES) {
            throw AppException.user("FILE_TOO_LARGE", "附件不超过 1MB");
        }
        String safe = safeFilename(originalFilename);
        String detected = sniff(content);
        if (detected == null || !extensionMatches(detected, extension(safe))) {
            throw AppException.user("FILE_TYPE_NOT_ALLOWED", "仅允许 PDF、PNG、JPEG、WebP 核验附件，且扩展名须与内容一致");
        }
        return new Accepted(safe, detected);
    }

    public static String ownedPrivateKey(String accountId, String fileId, String safeFilename) {
        requireToken(accountId);
        requireToken(fileId);
        if (safeFilename == null
                || safeFilename.isBlank()
                || safeFilename.indexOf('/') >= 0
                || safeFilename.indexOf('\\') >= 0
                || ".".equals(safeFilename)
                || "..".equals(safeFilename)) {
            throw new IllegalStateException("非法对象键");
        }
        return "private/" + accountId + "/" + fileId + "-" + safeFilename;
    }

    public static String safeFilename(String originalFilename) {
        String cleaned = basename(originalFilename).replaceAll("[^a-zA-Z0-9._-]", "_");
        String ext = extension(cleaned);
        String stem;
        if (ext.isEmpty()) {
            stem = cleaned;
        } else {
            stem = cleaned.substring(0, cleaned.length() - ext.length() - 1);
        }
        if (stem.isBlank() || ".".equals(stem) || "..".equals(stem)) {
            stem = "file";
        }
        if (stem.length() > MAX_STEM_LEN) {
            stem = stem.substring(0, MAX_STEM_LEN);
        }
        return ext.isEmpty() ? stem : stem + "." + ext;
    }

    private static String basename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.isBlank() ? "file" : name;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sniff(byte[] content) {
        if (startsWith(content, PDF)) {
            return "application/pdf";
        }
        if (startsWith(content, PNG)) {
            return "image/png";
        }
        if (startsWith(content, JPEG)) {
            return "image/jpeg";
        }
        if (content.length >= 12 && startsWith(content, RIFF) && regionEquals(content, 8, WEBP)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean extensionMatches(String mime, String ext) {
        return switch (mime) {
            case "application/pdf" -> "pdf".equals(ext);
            case "image/png" -> "png".equals(ext);
            case "image/jpeg" -> "jpg".equals(ext) || "jpeg".equals(ext);
            case "image/webp" -> "webp".equals(ext);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        return Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static boolean regionEquals(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        return Arrays.equals(content, offset, offset + expected.length, expected, 0, expected.length);
    }

    private static void requireToken(String value) {
        if (value == null || !TOKEN.matcher(value).matches()) {
            throw new IllegalStateException("非法对象键");
        }
    }
}
