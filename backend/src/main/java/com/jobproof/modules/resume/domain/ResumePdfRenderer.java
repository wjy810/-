package com.jobproof.modules.resume.domain;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Portable, text-based PDF renderer for immutable resume snapshots. */
public final class ResumePdfRenderer {

    public static final String VERSION = "resume-pdf-v9";

    private static final String FONT_RESOURCE = "/fonts/NotoSansSC-Regular.ttf";
    private static final Color MONO_ACCENT = new Color(31, 41, 55);
    private static final Color BODY = new Color(31, 41, 55);
    private static final Color MUTED = new Color(107, 114, 128);

    private ResumePdfRenderer() {
    }

    public static byte[] render(String title, String body) {
        return buildPdf(
                safeTitle(title),
                "JobProof 结构化简历",
                List.of(new SectionBlock(null, body == null ? "" : body)),
                MONO_ACCENT);
    }

    public static byte[] render(
            String title,
            ResumeDocumentModel document,
            ResumeLayoutDefinition definition,
            String variantCode) {
        return render(title, document, definition, variantCode, ResumeLayoutProtocol.V1);
    }

    public static byte[] render(
            String title,
            ResumeDocumentModel document,
            ResumeLayoutDefinition definition,
            String variantCode,
            String rendererProtocol) {
        ResumeLayoutProtocol.validate(rendererProtocol, definition);
        if (ResumeLayoutProtocol.V2.equals(rendererProtocol)
                || ResumeLayoutProtocol.V3.equals(rendererProtocol)) {
            return ResumeStructuredPdfRenderer.render(title, document, definition, variantCode);
        }
        Map<String, ResumeDocumentModel.Section> sections = new LinkedHashMap<>();
        for (ResumeDocumentModel.Section section : document.sections()) {
            sections.putIfAbsent(section.slotKey(), section);
        }
        List<SectionBlock> blocks = new ArrayList<>();
        definition.slots().stream()
                .sorted(Comparator.comparingInt(ResumeLayoutDefinition.Slot::order))
                .forEach(slot -> {
                    ResumeDocumentModel.Section section = sections.get(slot.key());
                    if (section == null || section.plainText() == null || section.plainText().isBlank()) {
                        return;
                    }
                    blocks.add(new SectionBlock(slotTitle(slot.key()), section.plainText()));
                });
        String variant = variantCode == null || variantCode.isBlank()
                ? "DEFAULT"
                : variantCode.trim().toUpperCase(Locale.ROOT);
        return buildPdf(
                safeTitle(title),
                "结构化简历 · " + variant,
                blocks,
                variantColor(definition, variant));
    }

    private static byte[] buildPdf(String title, String subtitle, List<SectionBlock> blocks, Color accent) {
        try (PDDocument document = new PDDocument();
                InputStream fontInput = ResumePdfRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
            if (fontInput == null) {
                throw new IllegalStateException("Bundled PDF font is missing: " + FONT_RESOURCE);
            }
            PDType0Font font = PDType0Font.load(document, fontInput, true);
            document.getDocumentInformation().setTitle(title);
            document.getDocumentInformation().setCreator("JobProof AI");
            document.getDocumentInformation().setSubject("Immutable structured resume snapshot");

            try (Canvas canvas = new Canvas(document, font, title, accent)) {
                canvas.start();
                for (String titleLine : wrap(title, font, 20f, canvas.contentWidth())) {
                    canvas.ensure(26f);
                    canvas.line(titleLine, 20f, 26f, accent);
                }
                canvas.line(subtitle, 8.5f, 18f, MUTED);
                canvas.rule(accent);
                canvas.space(14f);

                for (SectionBlock block : blocks) {
                    List<String> bodyLines = wrap(block.body(), font, 10.2f, canvas.contentWidth());
                    if (block.heading() != null) {
                        canvas.ensure(32f);
                        canvas.line(block.heading(), 11.5f, 20f, accent);
                        canvas.rule(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
                        canvas.space(12f);
                    }
                    for (String line : bodyLines) {
                        if (isNumberedItemHeading(line)) {
                            canvas.ensure(46.5f);
                        }
                        canvas.ensure(15.5f);
                        if (line.isEmpty()) {
                            canvas.space(7f);
                        } else {
                            canvas.line(line, 10.2f, 15.5f, BODY);
                        }
                    }
                    canvas.space(10f);
                }
            }

            addPageNumbers(document, font);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to render resume PDF", exception);
        }
    }

    private static boolean isNumberedItemHeading(String value) {
        return value != null && value.matches("^\\d+\\.\\s+.+");
    }

    private static void addPageNumbers(PDDocument document, PDFont font) throws IOException {
        int total = document.getNumberOfPages();
        for (int index = 0; index < total; index++) {
            PDPage page = document.getPage(index);
            String label = "第 " + (index + 1) + " / " + total + " 页";
            float size = 8f;
            float width = textWidth(font, size, label);
            try (PDPageContentStream stream = new PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true)) {
                stream.beginText();
                stream.setFont(font, size);
                stream.setNonStrokingColor(MUTED);
                stream.newLineAtOffset((page.getMediaBox().getWidth() - width) / 2f, 25f);
                stream.showText(label);
                stream.endText();
            }
        }
    }

    private static List<String> wrap(String value, PDType0Font font, float size, float maxWidth) throws IOException {
        String normalized = (value == null ? "" : value).replace("\r\n", "\n");
        List<String> result = new ArrayList<>();
        for (String paragraph : normalized.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                result.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < paragraph.length();) {
                int codePoint = paragraph.codePointAt(offset);
                String next = new String(Character.toChars(codePoint));
                String candidate = line + next;
                if (!line.isEmpty() && textWidth(font, size, candidate) > maxWidth) {
                    result.add(trimTrailing(line.toString()));
                    line.setLength(0);
                    if (!Character.isWhitespace(codePoint)) {
                        line.append(next);
                    }
                } else if (!line.isEmpty() || !Character.isWhitespace(codePoint)) {
                    line.append(next);
                }
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty()) {
                result.add(trimTrailing(line.toString()));
            }
        }
        return result.isEmpty() ? List.of("") : result;
    }

    private static float textWidth(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(value) / 1000f * size;
    }

    private static String trimTrailing(String value) {
        int end = value.length();
        while (end > 0) {
            int codePoint = value.codePointBefore(end);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    private static Color variantColor(ResumeLayoutDefinition definition, String variant) {
        String token = definition.tokens() == null ? null : definition.tokens().get("accent." + variant);
        if (token == null || !token.matches("#[0-9A-Fa-f]{6}")) {
            return "BLUE".equals(variant) ? new Color(23, 92, 211) : MONO_ACCENT;
        }
        return new Color(Integer.parseInt(token.substring(1), 16));
    }

    private static String safeTitle(String title) {
        return title == null || title.isBlank() ? "未命名简历" : title.trim();
    }

    private static String slotTitle(String key) {
        return switch (key) {
            case "summary" -> "个人简介";
            case "education" -> "教育经历";
            case "experience" -> "工作经历";
            case "projects" -> "项目经历";
            case "skills" -> "专业技能";
            case "certificates" -> "证书与资质";
            default -> key;
        };
    }

    private record SectionBlock(String heading, String body) {
    }

    private static final class Canvas implements AutoCloseable {
        private static final float MARGIN_X = 54f;
        private static final float TOP = 56f;
        private static final float BOTTOM = 48f;

        private final PDDocument document;
        private final PDFont font;
        private final String title;
        private final Color accent;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        private Canvas(PDDocument document, PDFont font, String title, Color accent) {
            this.document = document;
            this.font = font;
            this.title = title;
            this.accent = accent;
        }

        private void start() throws IOException {
            newPage();
        }

        private float contentWidth() {
            return PDRectangle.A4.getWidth() - MARGIN_X * 2f;
        }

        private void ensure(float requiredHeight) throws IOException {
            if (y - requiredHeight < BOTTOM) {
                newPage();
            }
        }

        private void line(String value, float size, float leading, Color color) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(MARGIN_X, y);
            stream.showText(value);
            stream.endText();
            y -= leading;
        }

        private void rule(Color color) throws IOException {
            stream.setStrokingColor(color);
            stream.setLineWidth(0.8f);
            stream.moveTo(MARGIN_X, y);
            stream.lineTo(PDRectangle.A4.getWidth() - MARGIN_X, y);
            stream.stroke();
            y -= 1f;
        }

        private void space(float value) {
            y -= value;
        }

        private void newPage() throws IOException {
            closeStream();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - TOP;
            pageNumber++;
            if (pageNumber > 1) {
                line(title, 8.5f, 15f, MUTED);
                rule(accent);
                space(10f);
            }
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        @Override
        public void close() throws IOException {
            closeStream();
        }
    }
}
