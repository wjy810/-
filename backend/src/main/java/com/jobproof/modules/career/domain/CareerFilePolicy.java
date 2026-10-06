package com.jobproof.modules.career.domain;

import com.jobproof.modules.resume.domain.ResumeTemplateDocxInspector;
import com.jobproof.shared.error.AppException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

public final class CareerFilePolicy {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_PAGES = 100;
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final Set<String> PDF_ACTIVE_MARKERS = Set.of("/JavaScript", "/JS", "/Launch",
            "/EmbeddedFile", "/OpenAction", "/AA", "/GoToR", "/SubmitForm", "/ImportData");

    private CareerFilePolicy() {}

    public static Accepted inspect(String originalFilename, byte[] content) {
        if (content == null || content.length == 0) throw AppException.user("CAREER_FILE_EMPTY", "文件不能为空");
        if (content.length > MAX_BYTES) throw AppException.user("CAREER_FILE_TOO_LARGE", "文件不能超过 10 MiB");
        String filename = safeFilename(originalFilename);
        String extension = extension(filename);
        String type = sniff(content);
        if (type == null || !extensionMatches(type, extension)) {
            throw AppException.user("CAREER_FILE_TYPE_INVALID", "仅支持真实的 DOCX、PDF、PNG、JPEG 或 WebP 文件");
        }
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(type)) {
            ResumeTemplateDocxInspector.Inspection inspection = ResumeTemplateDocxInspector.inspect(content, MAX_BYTES);
            if (!inspection.accepted()) {
                String code = inspection.findings().isEmpty() ? "DOCX_REJECTED" : inspection.findings().get(0).code();
                throw AppException.user("CAREER_FILE_" + code, "DOCX 包含不安全内容或文件结构损坏");
            }
        } else if ("application/pdf".equals(type)) {
            inspectPdf(content);
        } else {
            inspectImage(content);
        }
        return new Accepted(filename, type);
    }

    private static void inspectPdf(byte[] content) {
        String raw = new String(content, StandardCharsets.ISO_8859_1);
        for (String marker : PDF_ACTIVE_MARKERS) {
            if (raw.contains(marker)) throw AppException.user("CAREER_FILE_PDF_ACTIVE_CONTENT", "PDF 包含脚本、外链或主动内容");
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) throw AppException.user("CAREER_FILE_PDF_ENCRYPTED", "暂不支持加密 PDF");
            int pages = document.getNumberOfPages();
            if (pages < 1 || pages > MAX_PAGES) throw AppException.user("CAREER_FILE_PAGE_LIMIT", "文件页数必须在 1 至 100 页之间");
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.user("CAREER_FILE_PDF_INVALID", "PDF 无法安全解析");
        }
    }

    private static void inspectImage(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > 12_000 || image.getHeight() > 12_000
                    || (long) image.getWidth() * image.getHeight() > 50_000_000L) {
                throw AppException.user("CAREER_FILE_IMAGE_INVALID", "图片尺寸无效或像素总量超过安全上限");
            }
            image.flush();
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.user("CAREER_FILE_IMAGE_INVALID", "图片无法安全解析");
        }
    }

    public static String safeFilename(String raw) {
        String value = raw == null ? "file" : raw.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._-]", "_");
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) value = "file";
        return value.substring(0, Math.min(160, value.length()));
    }

    private static String sniff(byte[] content) {
        if (starts(content, PDF)) return "application/pdf";
        if (starts(content, PNG)) return "image/png";
        if (starts(content, JPEG)) return "image/jpeg";
        if (content.length >= 12 && starts(content, RIFF) && Arrays.equals(content, 8, 12, WEBP, 0, 4)) return "image/webp";
        if (content.length >= 4 && content[0] == 'P' && content[1] == 'K' && content[2] == 3 && content[3] == 4)
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return null;
    }

    private static boolean extensionMatches(String type, String extension) {
        return switch (type) {
            case "application/pdf" -> "pdf".equals(extension);
            case "image/png" -> "png".equals(extension);
            case "image/jpeg" -> "jpg".equals(extension) || "jpeg".equals(extension);
            case "image/webp" -> "webp".equals(extension);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx".equals(extension);
            default -> false;
        };
    }

    private static boolean starts(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 1 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record Accepted(String filename, String contentType) {}
}
