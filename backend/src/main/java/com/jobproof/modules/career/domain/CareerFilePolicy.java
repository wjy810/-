package com.jobproof.modules.career.domain;

import com.jobproof.modules.resume.domain.ResumeTemplateDocxInspector;
import com.jobproof.shared.error.AppException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.pdmodel.PDDocument;

public final class CareerFilePolicy {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_PAGES = 100;
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    /** Dictionary keys that run code, fire on events or carry files; their presence rejects the PDF. */
    private static final Set<String> PDF_ACTIVE_KEYS = Set.of("JS", "JavaScript", "AA", "EmbeddedFiles", "EF", "XFA", "RichMediaContent");
    /** Action types (/S) that run code, open other files or send data. Links (/URI) and in-document jumps (/GoTo) are fine. */
    private static final Set<String> PDF_ACTIVE_ACTIONS = Set.of("JavaScript", "Launch", "GoToR", "GoToE", "SubmitForm",
            "ImportData", "RichMediaExecute", "Rendition", "Movie", "Sound");
    private static final Set<String> PDF_ACTIVE_TYPES = Set.of("EmbeddedFile", "RichMedia", "FileAttachment", "Movie", "Sound", "Screen");
    private static final int PDF_MAX_OBJECTS = 200_000;
    /** Path separators, characters file systems reserve, and control characters. */
    private static final Pattern UNSAFE_NAME = Pattern.compile("[\\p{Cntrl}\\\\/:*?\"<>|]+");
    private static final int NAME_MAX_CODE_POINTS = 160;

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
        return new Accepted(filename, type, readableFilename(originalFilename, filename));
    }

    private static void inspectPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) throw AppException.user("CAREER_FILE_PDF_ENCRYPTED", "暂不支持加密 PDF");
            if (hasActiveContent(document)) {
                throw AppException.user("CAREER_FILE_PDF_ACTIVE_CONTENT", "PDF 包含脚本、自动动作或嵌入文件等主动内容");
            }
            int pages = document.getNumberOfPages();
            if (pages < 1 || pages > MAX_PAGES) throw AppException.user("CAREER_FILE_PAGE_LIMIT", "文件页数必须在 1 至 100 页之间");
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.user("CAREER_FILE_PDF_INVALID", "PDF 无法安全解析");
        }
    }

    /**
     * Walks every object of the parsed PDF (object streams included, names already decoded) looking for
     * scripts, event actions, launch/remote/submit actions, embedded files and rich media. Ordinary
     * link annotations and bookmarks stay allowed: resumes routinely link e-mail, GitHub or LinkedIn,
     * and browser "save as PDF" output names font subsets like /AAAAAA+Font, which a byte search for
     * "/AA" used to reject.
     */
    static boolean hasActiveContent(PDDocument document) {
        Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<COSBase> pending = new ArrayDeque<>();
        pending.push(document.getDocumentCatalog().getCOSObject());
        for (COSObjectKey key : document.getDocument().getXrefTable().keySet()) {
            COSObject object = document.getDocument().getObjectFromPool(key);
            if (object != null) pending.push(object);
        }
        while (!pending.isEmpty()) {
            COSBase next = pending.pop();
            COSBase value = next instanceof COSObject object ? object.getObject() : next;
            if (value == null || !seen.add(value)) continue;
            if (seen.size() > PDF_MAX_OBJECTS) return true;
            if (value instanceof COSDictionary dictionary) {
                if (active(dictionary)) return true;
                for (COSBase item : dictionary.getValues()) pending.push(item);
            } else if (value instanceof COSArray array) {
                for (COSBase item : array) pending.push(item);
            }
        }
        return false;
    }

    private static boolean active(COSDictionary dictionary) {
        for (COSName key : dictionary.keySet()) {
            if (PDF_ACTIVE_KEYS.contains(key.getName())) return true;
        }
        if (dictionary.getDictionaryObject(COSName.getPDFName("OpenAction")) instanceof COSDictionary) return true;
        String action = dictionary.getNameAsString(COSName.S);
        if (action != null && PDF_ACTIVE_ACTIONS.contains(action)) return true;
        String type = dictionary.getNameAsString(COSName.TYPE);
        String subtype = dictionary.getNameAsString(COSName.SUBTYPE);
        return (type != null && PDF_ACTIVE_TYPES.contains(type)) || (subtype != null && PDF_ACTIVE_TYPES.contains(subtype));
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

    /**
     * The uploaded file's name as the person knows it ("我的简历.pdf"), for display and downloads: path,
     * reserved and control characters removed, everything else kept. Storage keys keep using the
     * ASCII-only {@link #safeFilename}.
     */
    public static String readableFilename(String raw, String fallback) {
        if (raw == null) return fallback;
        String value = raw.replace('\\', '/');
        value = UNSAFE_NAME.matcher(value.substring(value.lastIndexOf('/') + 1)).replaceAll(" ").replaceAll("\\s+", " ").strip();
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) return fallback;
        if (value.codePointCount(0, value.length()) > NAME_MAX_CODE_POINTS) {
            value = value.substring(0, value.offsetByCodePoints(0, NAME_MAX_CODE_POINTS)).strip();
        }
        return value;
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

    /** {@code filename} is the ASCII-safe storage name; {@code originalName} the readable one shown to the owner. */
    public record Accepted(String filename, String contentType, String originalName) {}
}
