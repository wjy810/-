package com.jobproof.modules.resume.domain;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

final class ResumeStructuredPdfRenderer {
    private static final String SANS_FONT_RESOURCE = "/fonts/NotoSansSC-Regular.ttf";
    private static final String SERIF_FONT_RESOURCE = "/fonts/NotoSerifSC-Variable.ttf";
    private static final Color DEFAULT_ACCENT = new Color(31, 41, 55);
    private static final Color DEFAULT_BODY = new Color(31, 41, 55);
    private static final Color DEFAULT_MUTED = new Color(107, 114, 128);
    private static final Color NEUTRAL_SURFACE = new Color(244, 246, 248);

    private ResumeStructuredPdfRenderer() {
    }

    static byte[] render(
            String requestedTitle,
            ResumeDocumentModel documentModel,
            ResumeLayoutDefinition definition,
            String requestedVariant) {
        ResumeDocumentModel.Header header = documentModel.header();
        String title = !header.name().isBlank()
                ? header.name()
                : requestedTitle == null || requestedTitle.isBlank() ? "未命名简历" : requestedTitle.trim();
        String variant = requestedVariant == null || requestedVariant.isBlank()
                ? "DEFAULT"
                : requestedVariant.trim().toUpperCase(Locale.ROOT);
        Map<String, ResumeDocumentModel.Section> sections = new LinkedHashMap<>();
        for (ResumeDocumentModel.Section section : documentModel.sections()) {
            sections.putIfAbsent(section.slotKey(), section);
        }
        ResumeLayoutDefinition balancedDefinition = ResumeColumnBalancer.balance(documentModel, definition);

        try {
            try {
                return renderAttempt(title, variant, header, sections, balancedDefinition).bytes();
            } catch (IllegalStateException exception) {
                if (!isPageOverflow(exception)
                        || "COMPACT".equalsIgnoreCase(balancedDefinition.visual().effectiveDensity())) {
                    throw exception;
                }
                return renderAttempt(title, variant, header, sections,
                        compactFallback(balancedDefinition)).bytes();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render structured resume PDF", exception);
        }
    }

    private static boolean isPageOverflow(IllegalStateException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("maxPages=");
    }

    private static ResumeLayoutDefinition compactFallback(ResumeLayoutDefinition definition) {
        Map<String, String> tokens = new LinkedHashMap<>(
                definition.tokens() == null ? Map.of() : definition.tokens());
        tokens.put("fontScale", "SMALL");
        tokens.put("lineHeight", "COMPACT");
        ResumeLayoutDefinition.Visual visual = definition.visual();
        ResumeLayoutDefinition.Visual compactVisual = new ResumeLayoutDefinition.Visual(
                visual.headerStyle(), visual.sectionStyle(), visual.subtitle(), visual.showMark(), "COMPACT");
        return new ResumeLayoutDefinition(
                definition.page(), definition.columns(), definition.slots(), Map.copyOf(tokens),
                compactVisual, definition.designCapabilities());
    }

    private static RenderResult renderAttempt(
            String title,
            String variant,
            ResumeDocumentModel.Header header,
            Map<String, ResumeDocumentModel.Section> sections,
            ResumeLayoutDefinition definition) throws IOException {
        String fontResource = fontResource(definition);
        try (PDDocument pdf = new PDDocument();
                InputStream fontInput = ResumeStructuredPdfRenderer.class.getResourceAsStream(fontResource)) {
            if (fontInput == null) throw new IllegalStateException("Bundled PDF font is missing: " + fontResource);
            PDType0Font font = PDType0Font.load(pdf, fontInput, true);
            pdf.getDocumentInformation().setTitle(title);
            pdf.getDocumentInformation().setCreator("JobProof AI");
            pdf.getDocumentInformation().setSubject("Immutable structured resume snapshot · resume-layout-v3");

            try (LayoutCanvas canvas = new LayoutCanvas(
                    pdf, font, definition, title, variant, header)) {
                canvas.render(sections);
            }
            addPageNumbers(pdf, font);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            pdf.save(output);
            return new RenderResult(output.toByteArray(), pdf.getNumberOfPages());
        }
    }

    private static void addPageNumbers(PDDocument document, PDFont font) throws IOException {
        int total = document.getNumberOfPages();
        for (int index = 0; index < total; index++) {
            PDPage page = document.getPage(index);
            String label = "第 " + (index + 1) + " / " + total + " 页";
            float size = 8f;
            float width = textWidth(font, size, label);
            try (PDPageContentStream stream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                text(stream, font, label, size,
                        (page.getMediaBox().getWidth() - width) / 2f, 25f, DEFAULT_MUTED);
            }
        }
    }

    private static final class LayoutCanvas implements AutoCloseable {
        private final PDDocument document;
        private final PDType0Font font;
        private final ResumeLayoutDefinition definition;
        private final String title;
        private final String subtitle;
        private final String contactLine;
        private final ResumeDocumentModel.Photo photo;
        private final boolean showPhoto;
        private final ResumeLayoutDefinition.Visual visual;
        private final Color accent;
        private final Color surface;
        private final Color body;
        private final Color muted;
        private final float marginX;
        private final float marginTop;
        private final float marginBottom;
        private final float columnGap;
        private final float bodySize;
        private final float bodyLeading;
        private final float sectionSize;
        private final float fontScale;
        private final Map<String, ResumeLayoutDefinition.Slot> slots = new HashMap<>();
        private final List<PageCanvas> pages = new ArrayList<>();
        private final List<Region> regions = new ArrayList<>();

        private LayoutCanvas(
                PDDocument document,
                PDType0Font font,
                ResumeLayoutDefinition definition,
                String title,
                String variant,
                ResumeDocumentModel.Header header) {
            this.document = document;
            this.font = font;
            this.definition = definition;
            this.title = title;
            this.visual = definition.visual();
            this.subtitle = header.targetJob().isBlank() ? visual.effectiveSubtitle() : header.targetJob();
            this.contactLine = header.contactLine();
            this.photo = header.photo();
            this.accent = tokenColor(definition, "accent." + variant, DEFAULT_ACCENT);
            this.surface = tokenColor(definition, "surface." + variant, soften(accent, 0.90f));
            this.body = tokenColor(definition, "body", DEFAULT_BODY);
            this.muted = tokenColor(definition, "muted", DEFAULT_MUTED);
            this.marginX = definition.page().effectiveMarginXPt();
            this.marginTop = definition.page().effectiveMarginTopPt();
            this.marginBottom = definition.page().effectiveMarginBottomPt();
            this.columnGap = definition.columns().size() == 1 ? 0f : 18f;
            Map<String, String> tokens = definition.tokens() == null ? Map.of() : definition.tokens();
            this.showPhoto = photo != null
                    && !"HIDE".equalsIgnoreCase(tokens.getOrDefault("photoMode", "AUTO"));
            this.fontScale = switch (tokens.getOrDefault("fontScale", "STANDARD").toUpperCase(Locale.ROOT)) {
                case "SMALL" -> 0.90f;
                case "LARGE" -> 1.08f;
                default -> 1.0f;
            };
            float lineScale = switch (tokens.getOrDefault("lineHeight", "STANDARD").toUpperCase(Locale.ROOT)) {
                case "COMPACT" -> 0.90f;
                case "AIRY" -> 1.14f;
                default -> 1.0f;
            };
            String density = visual.effectiveDensity().toUpperCase(Locale.ROOT);
            this.bodySize = scaled("COMPACT".equals(density) ? 7.6f : "AIRY".equals(density) ? 10.6f : 10.0f);
            this.bodyLeading = ("COMPACT".equals(density) ? 10.4f : "AIRY".equals(density) ? 17.2f : 15.2f) * lineScale;
            this.sectionSize = scaled("COMPACT".equals(density) ? 9.2f : 11.3f);
            for (ResumeLayoutDefinition.Slot slot : definition.slots()) slots.put(slot.key(), slot);
            buildRegions();
        }

        private void render(Map<String, ResumeDocumentModel.Section> sections) throws IOException {
            ensurePage(0);
            for (int columnIndex = 0; columnIndex < definition.columns().size(); columnIndex++) {
                ResumeLayoutDefinition.Column column = definition.columns().get(columnIndex);
                Cursor cursor = new Cursor(columnIndex, 0, pages.get(0).contentTop());
                for (String slotKey : column.slotKeys()) {
                    ResumeLayoutDefinition.Slot slot = slots.get(slotKey);
                    ResumeDocumentModel.Section section = sections.get(slotKey);
                    if (slot == null || section == null
                            || (section.plainText().isBlank() && section.entries().isEmpty())) {
                        continue;
                    }
                    drawSection(cursor, slot, section);
                }
            }
        }

        private void buildRegions() {
            float contentWidth = PDRectangle.A4.getWidth() - marginX * 2f;
            float distributable = contentWidth - columnGap * (definition.columns().size() - 1);
            float x = marginX;
            for (int index = 0; index < definition.columns().size(); index++) {
                ResumeLayoutDefinition.Column column = definition.columns().get(index);
                float width = index == definition.columns().size() - 1
                        ? PDRectangle.A4.getWidth() - marginX - x
                        : distributable * column.widthPercent() / 100f;
                float inset = "PLAIN".equalsIgnoreCase(column.effectiveTone()) ? 0f : 10f;
                regions.add(new Region(x, width, x + inset, width - inset * 2f, column.effectiveTone()));
                x += width + columnGap;
            }
        }

        private void drawSection(Cursor cursor, ResumeLayoutDefinition.Slot slot,
                ResumeDocumentModel.Section section) throws IOException {
            Region region = regions.get(cursor.columnIndex());
            String style = slot.headingStyle() == null || slot.headingStyle().isBlank()
                    ? visual.effectiveSectionStyle()
                    : slot.headingStyle();
            style = style.toUpperCase(Locale.ROOT);
            ensure(cursor, headingHeight(style) + bodyLeading);
            drawHeading(cursor, slot.effectiveLabel(), style, false);
            if (!section.entries().isEmpty()) {
                drawEntries(cursor, slot, style, section.entries());
            } else {
                drawPlainText(cursor, slot, style, section.plainText());
            }
            cursor.move("COMPACT".equalsIgnoreCase(visual.effectiveDensity()) ? 4.5f : 11f);
        }

        private void drawPlainText(Cursor cursor, ResumeLayoutDefinition.Slot slot, String style, String value)
                throws IOException {
            Region region = regions.get(cursor.columnIndex());
            List<String> lines = wrap(value, font, bodySize, bodyWidth(region, style));
            for (String line : lines) {
                ensureBodyLine(cursor, slot, style);
                if (line.isEmpty()) {
                    cursor.move(bodyLeading * 0.55f);
                    continue;
                }
                float x = region.textX() + ("SIDELINE".equals(style) ? 7f : 0f);
                drawBodyLine(cursor, style, line, x, body);
            }
        }

        private void drawEntries(Cursor cursor, ResumeLayoutDefinition.Slot slot, String style,
                List<ResumeDocumentModel.Entry> entries) throws IOException {
            Region region = regions.get(cursor.columnIndex());
            float x = region.textX() + ("SIDELINE".equals(style) ? 7f : 0f);
            float width = bodyWidth(region, style);
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                ResumeDocumentModel.Entry entry = entries.get(entryIndex);
                float topSize = fittedTimelineTopSize(entry, width);
                float dateWidth = entry.date().isBlank() ? 0f : textWidth(font, topSize, entry.date());
                float titleWidth = dateWidth == 0f ? width : Math.max(20f, width - dateWidth - 8f);
                List<String> primaryLines = entry.primary().isBlank()
                        ? List.of() : wrap(entry.primary(), font, topSize, titleWidth);

                if (!primaryLines.isEmpty() || !entry.date().isBlank()) {
                    ensureBodyLine(cursor, slot, style);
                    String firstTitle = primaryLines.isEmpty() ? "" : primaryLines.get(0);
                    if (!firstTitle.isBlank()) {
                        text(current(cursor).stream(), font, firstTitle, topSize, x, cursor.y(), body);
                    }
                    if (!entry.date().isBlank()) {
                        rightText(current(cursor).stream(), entry.date(), topSize,
                                x + width, cursor.y(), muted);
                    }
                    drawTableRule(cursor, style, region);
                    cursor.move(bodyLeading);
                    for (int lineIndex = 1; lineIndex < primaryLines.size(); lineIndex++) {
                        ensureBodyLine(cursor, slot, style);
                        drawBodyLine(cursor, style, primaryLines.get(lineIndex), x, body, topSize);
                    }
                }

                String secondary = join(" · ", entry.secondary(), entry.location());
                drawWrappedEntryText(cursor, slot, style, secondary, x, width, muted);
                drawWrappedEntryText(cursor, slot, style, entry.description(), x, width, body);
                for (String highlight : entry.highlights()) {
                    drawWrappedEntryText(cursor, slot, style, "• " + highlight, x, width, body);
                }
                if (entryIndex < entries.size() - 1) cursor.move(bodyLeading * 0.42f);
            }
        }

        private float fittedTimelineTopSize(ResumeDocumentModel.Entry entry, float width) throws IOException {
            if (entry.primary().isBlank() || entry.date().isBlank()) return bodySize;
            float combined = textWidth(font, bodySize, entry.primary())
                    + textWidth(font, bodySize, entry.date()) + 8f;
            if (combined <= width) return bodySize;
            return Math.max(scaled(6.0f), Math.min(bodySize, bodySize * (width - 10f) / combined));
        }

        private void drawWrappedEntryText(Cursor cursor, ResumeLayoutDefinition.Slot slot, String style,
                String value, float x, float width, Color color) throws IOException {
            if (value == null || value.isBlank()) return;
            for (String line : wrap(value, font, bodySize, width)) {
                ensureBodyLine(cursor, slot, style);
                if (line.isBlank()) {
                    cursor.move(bodyLeading * 0.55f);
                } else {
                    drawBodyLine(cursor, style, line, x, color);
                }
            }
        }

        private void ensureBodyLine(Cursor cursor, ResumeLayoutDefinition.Slot slot, String style)
                throws IOException {
            if (cursor.y() - bodyLeading >= marginBottom) return;
            nextPage(cursor);
            drawHeading(cursor, slot.effectiveLabel() + "（续）", style, true);
        }

        private void drawBodyLine(Cursor cursor, String style, String value, float x, Color color)
                throws IOException {
            drawBodyLine(cursor, style, value, x, color, bodySize);
        }

        private void drawBodyLine(Cursor cursor, String style, String value, float x, Color color, float size)
                throws IOException {
            text(current(cursor).stream(), font, value, size, x, cursor.y(), color);
            drawTableRule(cursor, style, regions.get(cursor.columnIndex()));
            cursor.move(bodyLeading);
        }

        private void drawTableRule(Cursor cursor, String style, Region region) throws IOException {
            if ("TABLE".equals(style)) {
                rule(current(cursor).stream(), region.textX(), cursor.y() - 3f,
                        region.textX() + region.textWidth(), cursor.y() - 3f,
                        new Color(221, 225, 231), 0.45f);
            }
        }

        private void drawHeading(Cursor cursor, String label, String style, boolean continuation) throws IOException {
            Region region = regions.get(cursor.columnIndex());
            PageCanvas page = current(cursor);
            float x = region.textX();
            if ("BAR".equals(style) || "TABLE".equals(style)) {
                float headingBoxHeight = "COMPACT".equalsIgnoreCase(visual.effectiveDensity()) ? 14f : 17f;
                fill(page.stream(), x, cursor.y() - 4f, region.textWidth(), headingBoxHeight,
                        "TABLE".equals(style) ? NEUTRAL_SURFACE : surface);
                text(page.stream(), font, label, sectionSize, x + 5f, cursor.y(), accent);
                cursor.move("COMPACT".equalsIgnoreCase(visual.effectiveDensity()) ? 18f : 22f);
                return;
            }
            if ("SIDELINE".equals(style)) {
                rule(page.stream(), x, cursor.y() + 3f, x, cursor.y() - 12f, accent, 2.2f);
                text(page.stream(), font, label, sectionSize, x + 7f, cursor.y(), accent);
                cursor.move(20f);
                return;
            }
            text(page.stream(), font, label, sectionSize, x, cursor.y(), accent);
            cursor.move(17f);
            if ("RULE".equals(style) && !continuation) {
                rule(page.stream(), x, cursor.y() + 3f, x + region.textWidth(), cursor.y() + 3f, accent, 0.7f);
                cursor.move(5f);
            } else {
                cursor.move(3f);
            }
        }

        private void ensure(Cursor cursor, float requiredHeight) throws IOException {
            if (cursor.y() - requiredHeight < marginBottom) nextPage(cursor);
        }

        private void nextPage(Cursor cursor) throws IOException {
            int next = cursor.pageIndex() + 1;
            ensurePage(next);
            cursor.setPage(next, pages.get(next).contentTop());
        }

        private PageCanvas current(Cursor cursor) {
            return pages.get(cursor.pageIndex());
        }

        private void ensurePage(int index) throws IOException {
            if (index >= definition.page().maxPages()) {
                throw new IllegalStateException(
                        "Structured resume render exceeded template maxPages=" + definition.page().maxPages());
            }
            while (pages.size() <= index) pages.add(createPage(pages.size()));
        }

        private PageCanvas createPage(int pageIndex) throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float contentTop = pageIndex == 0 ? drawFirstHeader(stream) : drawContinuationHeader(stream);
            for (Region region : regions) {
                Color tone = switch (region.tone().toUpperCase(Locale.ROOT)) {
                    case "ACCENT_SOFT" -> surface;
                    case "NEUTRAL" -> NEUTRAL_SURFACE;
                    default -> null;
                };
                if (tone != null) {
                    fill(stream, region.x(), marginBottom - 6f, region.width(), contentTop - marginBottom + 12f, tone);
                }
            }
            return new PageCanvas(stream, contentTop);
        }

        private float drawFirstHeader(PDPageContentStream stream) throws IOException {
            float pageTop = PDRectangle.A4.getHeight() - marginTop;
            String headerStyle = visual.effectiveHeaderStyle().toUpperCase(Locale.ROOT);
            float contentWidth = PDRectangle.A4.getWidth() - marginX * 2f;
            float photoReserve = showPhoto ? 58f : 0f;
            List<String> titleLines = wrap(title, font, scaled(20f), contentWidth - photoReserve);
            if ("BAND".equals(headerStyle)) {
                List<String> metaLines = headerMetaLines(
                        scaled(8.8f), contentWidth - 28f - photoReserve);
                float height = Math.max(64f, 25f + titleLines.size() * 23f + metaLines.size() * 13f);
                fill(stream, marginX, pageTop - height + 10f, contentWidth, height, accent);
                float y = pageTop - 12f;
                for (String line : titleLines) {
                    text(stream, font, line, scaled(20f), marginX + 14f, y, Color.WHITE);
                    y -= 23f;
                }
                for (String line : metaLines) {
                    text(stream, font, line, scaled(8.8f), marginX + 14f, y - 1f, Color.WHITE);
                    y -= 13f;
                }
                if (showPhoto) drawPhoto(stream, PDRectangle.A4.getWidth() - marginX - 50f, pageTop - height + 15f,
                        40f, Math.min(52f, height - 14f), Color.WHITE);
                return pageTop - height - 13f;
            }

            if ("MINIMAL".equals(headerStyle)) {
                float y = pageTop;
                for (String line : titleLines) {
                    centeredText(stream, line, scaled(20f), y, accent, marginX, contentWidth - photoReserve);
                    y -= 24f;
                }
                for (String line : headerMetaLines(scaled(8.7f), contentWidth - photoReserve)) {
                    centeredText(stream, line, scaled(8.7f), y, muted, marginX, contentWidth - photoReserve);
                    y -= 12f;
                }
                if (showPhoto) drawPhoto(stream, PDRectangle.A4.getWidth() - marginX - 44f, pageTop - 50f,
                        40f, 52f, new Color(215, 220, 229));
                y -= 4f;
                rule(stream, marginX, y, PDRectangle.A4.getWidth() - marginX, y, accent, 0.8f);
                return y - 16f;
            }

            if ("SPLIT".equals(headerStyle)) {
                float leftWidth = contactLine.isBlank() ? contentWidth - photoReserve
                        : Math.max(170f, contentWidth * 0.56f - photoReserve * 0.4f);
                float y = pageTop;
                for (String line : wrap(title, font, scaled(20f), leftWidth)) {
                    text(stream, font, line, scaled(20f), marginX, y, accent);
                    y -= 24f;
                }
                for (String line : wrap(subtitle, font, scaled(8.8f), leftWidth)) {
                    text(stream, font, line, scaled(8.8f), marginX, y, muted);
                    y -= 12f;
                }
                float rightEdge = PDRectangle.A4.getWidth() - marginX - photoReserve;
                float rightWidth = Math.max(100f, contentWidth - leftWidth - 14f - photoReserve);
                float rightY = pageTop - 2f;
                for (String line : wrap(contactLine, font, scaled(8.4f), rightWidth)) {
                    rightText(stream, line, scaled(8.4f), rightEdge, rightY, muted);
                    rightY -= 12f;
                }
                if (showPhoto) drawPhoto(stream, PDRectangle.A4.getWidth() - marginX - 44f, pageTop - 50f,
                        40f, 52f, new Color(215, 220, 229));
                y = Math.min(y, Math.min(rightY, showPhoto ? pageTop - 56f : pageTop));
                if (visual.showMark() && !showPhoto) {
                    float markX = PDRectangle.A4.getWidth() - marginX - 34f;
                    fill(stream, markX, pageTop - 31f, 34f, 34f, accent);
                    text(stream, font, "JP", scaled(10f), markX + 9f, pageTop - 19f, Color.WHITE);
                }
                y -= 4f;
                rule(stream, marginX, y, PDRectangle.A4.getWidth() - marginX, y, accent, 1.6f);
                return y - 16f;
            }

            float y = pageTop;
            if ("COMPACT".equals(headerStyle)) {
                List<String> compactTitle = wrap(title, font, scaled(18f), contentWidth - photoReserve);
                for (String line : compactTitle) {
                    text(stream, font, line, scaled(18f), marginX, y, accent);
                    y -= 20f;
                }
                for (String line : headerMetaLines(scaled(8.5f), contentWidth - photoReserve)) {
                    text(stream, font, line, scaled(8.5f), marginX, y, muted);
                    y -= 12f;
                }
                if (showPhoto) drawPhoto(stream, PDRectangle.A4.getWidth() - marginX - 40f, pageTop - 45f,
                        36f, 47f, new Color(215, 220, 229));
                y -= 3f;
            } else {
                for (String line : titleLines) {
                    text(stream, font, line, scaled(20f), marginX, y, accent);
                    y -= 24f;
                }
                for (String line : headerMetaLines(scaled(8.7f), PDRectangle.A4.getWidth() - marginX * 2f)) {
                    text(stream, font, line, scaled(8.7f), marginX, y, muted);
                    y -= 12f;
                }
                y -= 4f;
            }
            rule(stream, marginX, y, PDRectangle.A4.getWidth() - marginX, y, accent,
                    "MINIMAL".equals(headerStyle) ? 0.8f : 1.6f);
            return y - 16f;
        }

        private void centeredText(PDPageContentStream stream, String value, float size, float y, Color color,
                float x, float width) throws IOException {
            text(stream, font, value, size, x + Math.max(0f, (width - textWidth(font, size, value)) / 2f), y,
                    color);
        }

        private void rightText(PDPageContentStream stream, String value, float size, float rightX, float y,
                Color color) throws IOException {
            text(stream, font, value, size, rightX - textWidth(font, size, value), y, color);
        }

        private void drawPhoto(PDPageContentStream stream, float x, float y, float width, float height,
                Color border) throws IOException {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, photo.bytes(), "resume-photo");
            float scale = Math.max(width / image.getWidth(), height / image.getHeight());
            float drawWidth = image.getWidth() * scale;
            float drawHeight = image.getHeight() * scale;
            stream.saveGraphicsState();
            stream.addRect(x, y, width, height);
            stream.clip();
            stream.drawImage(image, x + (width - drawWidth) / 2f, y + (height - drawHeight) / 2f,
                    drawWidth, drawHeight);
            stream.restoreGraphicsState();
            rule(stream, x, y, x + width, y, border, 0.7f);
            rule(stream, x + width, y, x + width, y + height, border, 0.7f);
            rule(stream, x + width, y + height, x, y + height, border, 0.7f);
            rule(stream, x, y + height, x, y, border, 0.7f);
        }

        private List<String> headerMetaLines(float size, float maxWidth) throws IOException {
            List<String> result = new ArrayList<>(wrap(subtitle, font, size, maxWidth));
            if (!contactLine.isBlank()) result.addAll(wrap(contactLine, font, size, maxWidth));
            return result;
        }

        private float scaled(float size) {
            return Math.round(size * fontScale * 10f) / 10f;
        }

        private float drawContinuationHeader(PDPageContentStream stream) throws IOException {
            float y = PDRectangle.A4.getHeight() - marginTop;
            text(stream, font, title, 8.7f, marginX, y, muted);
            rule(stream, marginX, y - 6f, PDRectangle.A4.getWidth() - marginX, y - 6f, accent, 0.7f);
            return y - 20f;
        }

        private float bodyWidth(Region region, String style) {
            return region.textWidth() - ("SIDELINE".equals(style) ? 7f : 0f);
        }

        private float headingHeight(String style) {
            if ("COMPACT".equalsIgnoreCase(visual.effectiveDensity())) {
                return "BAR".equals(style) || "TABLE".equals(style) ? 18f
                        : "RULE".equals(style) ? 21f : 17f;
            }
            return "BAR".equals(style) || "TABLE".equals(style) ? 22f : "RULE".equals(style) ? 25f : 20f;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (PageCanvas page : pages) {
                try {
                    page.stream().close();
                } catch (IOException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw failure;
        }
    }

    private record PageCanvas(PDPageContentStream stream, float contentTop) {
    }

    private record RenderResult(byte[] bytes, int pageCount) {
    }

    private record Region(float x, float width, float textX, float textWidth, String tone) {
    }

    private static final class Cursor {
        private final int columnIndex;
        private int pageIndex;
        private float y;

        private Cursor(int columnIndex, int pageIndex, float y) {
            this.columnIndex = columnIndex;
            this.pageIndex = pageIndex;
            this.y = y;
        }

        int columnIndex() { return columnIndex; }
        int pageIndex() { return pageIndex; }
        float y() { return y; }
        void move(float amount) { y -= amount; }
        void setPage(int value, float nextY) { pageIndex = value; y = nextY; }
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
                boolean consumed = false;
                while (!consumed) {
                    String candidate = line + next;
                    if (line.isEmpty() || textWidth(font, size, candidate) <= maxWidth) {
                        if (!line.isEmpty() || !Character.isWhitespace(codePoint)) line.append(next);
                        consumed = true;
                        continue;
                    }

                    int boundary = lastWrapBoundary(line);
                    if (boundary > 0) {
                        String completed = trimTrailing(line.substring(0, boundary));
                        if (!completed.isEmpty()) result.add(completed);
                        String carry = trimLeading(line.substring(boundary));
                        line.setLength(0);
                        line.append(carry);
                    } else {
                        result.add(trimTrailing(line.toString()));
                        line.setLength(0);
                    }
                }
                offset += Character.charCount(codePoint);
            }
            if (!line.isEmpty()) result.add(trimTrailing(line.toString()));
        }
        return result.isEmpty() ? List.of("") : result;
    }

    private static int lastWrapBoundary(CharSequence value) {
        int result = -1;
        for (int offset = 0; offset < value.length();) {
            int codePoint = Character.codePointAt(value, offset);
            if (Character.isWhitespace(codePoint)) result = offset;
            int width = Character.charCount(codePoint);
            if (isSoftWrapDelimiter(codePoint)) result = offset + width;
            offset += width;
        }
        return result;
    }

    private static boolean isSoftWrapDelimiter(int codePoint) {
        return codePoint == '、' || codePoint == '，' || codePoint == '。'
                || codePoint == '；' || codePoint == '：'
                || codePoint == ',' || codePoint == ';' || codePoint == ':' || codePoint == '/';
    }

    private static String trimLeading(String value) {
        int start = 0;
        while (start < value.length()) {
            int codePoint = value.codePointAt(start);
            if (!Character.isWhitespace(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        return value.substring(start);
    }

    private static String trimTrailing(String value) {
        int end = value.length();
        while (end > 0) {
            int codePoint = value.codePointBefore(end);
            if (!Character.isWhitespace(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    private static String join(String separator, String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) result.add(value.trim());
        }
        return String.join(separator, result);
    }

    private static Color tokenColor(ResumeLayoutDefinition definition, String key, Color fallback) {
        String value = definition.tokens() == null ? null : definition.tokens().get(key);
        return value != null && value.matches("#[0-9A-Fa-f]{6}")
                ? new Color(Integer.parseInt(value.substring(1), 16))
                : fallback;
    }

    private static String fontResource(ResumeLayoutDefinition definition) {
        String preset = definition.tokens() == null ? null : definition.tokens().get("fontPreset");
        return "CLASSIC_SERIF".equalsIgnoreCase(preset) ? SERIF_FONT_RESOURCE : SANS_FONT_RESOURCE;
    }

    private static Color soften(Color color, float whiteRatio) {
        float colorRatio = 1f - whiteRatio;
        return new Color(
                Math.min(255, Math.round(color.getRed() * colorRatio + 255 * whiteRatio)),
                Math.min(255, Math.round(color.getGreen() * colorRatio + 255 * whiteRatio)),
                Math.min(255, Math.round(color.getBlue() * colorRatio + 255 * whiteRatio)));
    }

    private static float textWidth(PDFont font, float size, String value) throws IOException {
        return font.getStringWidth(value) / 1000f * size;
    }

    private static void text(
            PDPageContentStream stream,
            PDFont font,
            String value,
            float size,
            float x,
            float y,
            Color color) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    private static void fill(PDPageContentStream stream, float x, float y, float width, float height, Color color)
            throws IOException {
        stream.setNonStrokingColor(color);
        stream.addRect(x, y, width, height);
        stream.fill();
    }

    private static void rule(
            PDPageContentStream stream,
            float x1,
            float y1,
            float x2,
            float y2,
            Color color,
            float width) throws IOException {
        stream.setStrokingColor(color);
        stream.setLineWidth(width);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }
}
