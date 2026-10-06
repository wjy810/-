package com.jobproof.modules.resume.domain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public final class ResumeTemplateDocxInspector {

    public static final String SCANNER_VERSION = "docx-static-v1";
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_BATCH_BYTES = 20 * 1024 * 1024;

    private static final int MAX_ENTRIES = 2_048;
    private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 25L * 1024 * 1024;
    private static final int MAX_INSPECTED_XML_BYTES = 2 * 1024 * 1024;
    private static final Set<String> REQUIRED_ENTRIES = Set.of(
            "[content_types].xml", "_rels/.rels", "word/document.xml");
    private static final Set<String> EXECUTABLE_EXTENSIONS = Set.of(
            ".bat", ".class", ".cmd", ".com", ".dll", ".exe", ".jar", ".js",
            ".msi", ".ps1", ".scr", ".vbe", ".vbs", ".wsf");

    private ResumeTemplateDocxInspector() {
    }

    public static Inspection inspect(byte[] content) {
        return inspect(content, MAX_BYTES);
    }

    public static Inspection inspect(byte[] content, int maxBytes) {
        List<Finding> findings = new ArrayList<>();
        if (content == null || content.length == 0) {
            findings.add(new Finding("EMPTY_FILE", "DOCX 文件不能为空"));
            return result(0, 0, findings);
        }
        if (maxBytes < 1 || maxBytes > MAX_BATCH_BYTES) {
            throw new IllegalArgumentException("DOCX 检查上限必须在 1 到 20MiB 之间");
        }
        if (content.length > maxBytes) {
            findings.add(new Finding("FILE_TOO_LARGE", "DOCX 文件超过受控大小上限"));
            return result(0, 0, findings);
        }
        if (!isZip(content)) {
            findings.add(new Finding("INVALID_ZIP_MAGIC", "DOCX 必须是有效的 OOXML ZIP 包"));
            return result(0, 0, findings);
        }

        Set<String> names = new HashSet<>();
        int entryCount = 0;
        long uncompressedBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES) {
                    findings.add(new Finding("TOO_MANY_ENTRIES", "DOCX ZIP 条目数量超过安全上限"));
                    break;
                }
                String name = entry.getName();
                String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
                String checkedName = entry.isDirectory() && name != null && name.endsWith("/")
                        ? name.substring(0, name.length() - 1) : name;
                if (unsafeEntryName(checkedName)) {
                    findings.add(new Finding("UNSAFE_ENTRY_PATH", "DOCX 包含不安全的 ZIP 条目路径"));
                }
                if (!names.add(lower)) {
                    findings.add(new Finding("DUPLICATE_ENTRY", "DOCX 包含大小写冲突或重复 ZIP 条目"));
                }
                inspectEntryName(lower, findings);
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }

                boolean inspectXml = "[content_types].xml".equals(lower) || lower.endsWith(".rels");
                ByteArrayOutputStream inspected = inspectXml ? new ByteArrayOutputStream() : null;
                long entryBytes = 0;
                byte[] buffer = new byte[8_192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    uncompressedBytes += read;
                    if (entryBytes > MAX_ENTRY_BYTES || uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                        findings.add(new Finding("ZIP_BOMB_LIMIT", "DOCX 解压体积超过安全上限"));
                        return result(entryCount, uncompressedBytes, findings);
                    }
                    if (inspected != null && inspected.size() + read <= MAX_INSPECTED_XML_BYTES) {
                        inspected.write(buffer, 0, read);
                    }
                }
                if (inspectXml) {
                    if (entryBytes > MAX_INSPECTED_XML_BYTES) {
                        findings.add(new Finding("SECURITY_XML_TOO_LARGE", "DOCX 安全元数据超过检查上限"));
                    } else if ("[content_types].xml".equals(lower)) {
                        inspectContentTypes(inspected.toByteArray(), findings);
                    } else {
                        inspectRelationships(inspected.toByteArray(), findings);
                    }
                }
                zip.closeEntry();
            }
        } catch (Exception exception) {
            findings.add(new Finding("INVALID_DOCX_PACKAGE", "DOCX ZIP 或安全 XML 无法解析"));
        }

        for (String required : REQUIRED_ENTRIES) {
            if (!names.contains(required)) {
                findings.add(new Finding("REQUIRED_PART_MISSING", "DOCX 缺少必要的 OOXML 文档部件"));
                break;
            }
        }
        return result(entryCount, uncompressedBytes, findings);
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void inspectEntryName(String lower, List<Finding> findings) {
        if (lower.equals("word/vbaproject.bin") || lower.endsWith("/vbaproject.bin")) {
            findings.add(new Finding("MACRO_BINARY", "DOCX 包含 VBA 宏"));
        }
        if (lower.startsWith("word/embeddings/") || lower.startsWith("word/activex/")) {
            findings.add(new Finding("ACTIVE_CONTENT", "DOCX 包含 OLE 嵌入或 ActiveX 内容"));
        }
        for (String extension : EXECUTABLE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                findings.add(new Finding("EXECUTABLE_CONTENT", "DOCX 包含可执行或脚本内容"));
                break;
            }
        }
    }

    private static void inspectContentTypes(byte[] xml, List<Finding> findings) throws Exception {
        var document = parseXml(xml);
        for (String elementName : List.of("Default", "Override")) {
            NodeList nodes = document.getElementsByTagNameNS("*", elementName);
            for (int index = 0; index < nodes.getLength(); index++) {
                Element element = (Element) nodes.item(index);
                String contentType = element.getAttribute("ContentType").toLowerCase(Locale.ROOT);
                if (contentType.contains("macroenabled") || contentType.contains("vbaproject")
                        || contentType.contains("activex") || contentType.contains("oleobject")) {
                    findings.add(new Finding("ACTIVE_CONTENT_TYPE", "DOCX 声明了宏或主动内容类型"));
                }
            }
        }
    }

    private static void inspectRelationships(byte[] xml, List<Finding> findings) throws Exception {
        var document = parseXml(xml);
        NodeList nodes = document.getElementsByTagNameNS("*", "Relationship");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            String mode = element.getAttribute("TargetMode");
            String target = element.getAttribute("Target").trim();
            if ("external".equalsIgnoreCase(mode) || target.startsWith("//") || hasUriScheme(target)) {
                findings.add(new Finding("EXTERNAL_RELATIONSHIP", "DOCX 包含外部链接关系"));
            }
        }
    }

    private static org.w3c.dom.Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return builder.parse(new ByteArrayInputStream(xml));
    }

    private static boolean unsafeEntryName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0
                || name.startsWith("/") || name.startsWith("//") || name.indexOf(':') >= 0) {
            return true;
        }
        for (String part : name.split("/")) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUriScheme(String target) {
        int colon = target.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        for (int index = 0; index < colon; index++) {
            char value = target.charAt(index);
            if (!(Character.isLetterOrDigit(value) || value == '+' || value == '-' || value == '.')) {
                return false;
            }
        }
        return Character.isLetter(target.charAt(0));
    }

    private static boolean isZip(byte[] content) {
        return content.length >= 4 && content[0] == 'P' && content[1] == 'K'
                && content[2] == 3 && content[3] == 4;
    }

    private static Inspection result(int entryCount, long uncompressedBytes, List<Finding> findings) {
        return new Inspection(findings.isEmpty(), SCANNER_VERSION, entryCount, uncompressedBytes,
                List.copyOf(findings));
    }

    public record Finding(String code, String message) {
    }

    public record Inspection(boolean accepted, String scannerVersion, int entryCount,
            long uncompressedBytes, List<Finding> findings) {
    }
}
