package com.jobproof.modules.resume.domain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

/** Deterministic OOXML renderer for frozen structured resume snapshots. */
public final class ResumeDocxRenderer {

    public static final String VERSION = "resume-docx-v3";
    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final int A4_WIDTH_TWIPS = 11906;
    private static final int A4_HEIGHT_TWIPS = 16838;
    private static final int MAX_UNCOMPRESSED_BYTES = 100 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 2_000;
    private static final int TABLE_INDENT_TWIPS = 120;
    private static final String LATIN_FONT = "Arial";
    private static final String CJK_FONT = "Microsoft YaHei";
    private static final Set<String> TABLE_TEMPLATE_IDS = Set.of("rlt-b-cn-table-v1");

    private ResumeDocxRenderer() {
    }

    public static byte[] render(
            String requestedTitle,
            ResumeDocumentModel documentModel,
            ResumeLayoutDefinition definition,
            String requestedVariant,
            String rendererProtocol,
            String templateId) {
        ResumeLayoutProtocol.validate(rendererProtocol, definition);
        ResumeDocumentModel.Header header = documentModel.header();
        String title = !header.name().isBlank()
                ? header.name()
                : requestedTitle == null || requestedTitle.isBlank() ? "未命名简历" : requestedTitle.trim();
        String variant = requestedVariant == null || requestedVariant.isBlank()
                ? "DEFAULT" : requestedVariant.trim().toUpperCase(Locale.ROOT);
        Palette palette = Palette.from(definition, variant);
        Map<String, ResumeDocumentModel.Section> sections = sections(documentModel);

        try (XWPFDocument output = new XWPFDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            configureDocument(output, definition, palette);
            Numbering numbering = configureNumbering(output);
            addHeader(output, title, header, definition, variant, palette);
            if (TABLE_TEMPLATE_IDS.contains(templateId)) {
                addTableSections(output, definition, sections, palette, numbering);
            } else if (definition.columns().size() == 1) {
                int usable = A4_WIDTH_TWIPS - ptToTwips(definition.page().effectiveMarginXPt()) * 2;
                addSections(output, definition.columns().get(0).slotKeys(), definition, sections, palette,
                        numbering, usable);
            } else {
                addColumnSections(output, definition, sections, palette, numbering);
            }
            addFooter(output, palette);
            output.getProperties().getCoreProperties().setTitle(title);
            output.getProperties().getCoreProperties().setCreator("JobProof AI");
            output.getProperties().getCoreProperties().setSubjectProperty(
                    "Immutable structured resume snapshot · " + rendererProtocol + " · " + templateId);
            output.write(bytes);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render resume DOCX", exception);
        }
    }

    public static Inspection inspect(
            byte[] bytes,
            String title,
            ResumeDocumentModel documentModel,
            ResumeLayoutDefinition definition) {
        boolean zipMagic = bytes != null && bytes.length >= 4
                && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
        ZipInspection zip = inspectZip(bytes);
        boolean reopened = false;
        boolean textOrder = false;
        int paragraphs = 0;
        int tables = 0;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            reopened = true;
            paragraphs = document.getParagraphs().size();
            tables = document.getTables().size();
            List<String> expected = new ArrayList<>();
            String resolvedTitle = documentModel.header().name().isBlank()
                    ? title : documentModel.header().name();
            if (resolvedTitle != null && !resolvedTitle.isBlank()) expected.add(resolvedTitle);
            if (!documentModel.header().targetJob().isBlank()) expected.add(documentModel.header().targetJob());
            if (!documentModel.header().contactLine().isBlank()) expected.add(documentModel.header().contactLine());
            Map<String, ResumeDocumentModel.Section> sections = sections(documentModel);
            List<String> orderedKeys = definition.columns().stream()
                    .flatMap(column -> column.slotKeys().stream()).distinct().toList();
            for (String key : orderedKeys) {
                ResumeDocumentModel.Section section = sections.get(key);
                if (section != null && !section.entries().isEmpty()) {
                    for (ResumeDocumentModel.Entry entry : section.entries()) {
                        addExpected(expected, entry.primary());
                        addExpected(expected, entry.date());
                        addExpected(expected, entry.secondary());
                        addExpected(expected, entry.location());
                        addExpected(expected, entry.description());
                        entry.highlights().forEach(value -> addExpected(expected, value));
                    }
                } else if (section != null && !section.plainText().isBlank()) {
                    for (String line : section.plainText().replace("\r\n", "\n").split("\n", -1)) {
                            String text = ParsedLine.parse(line).text();
                            if (!text.isBlank()) expected.add(text);
                    }
                }
            }
            textOrder = appearsInOrder(normalize(extractor.getText()), expected);
        } catch (Exception ignored) {
            // The inspection result is persisted and blocks publication/export when invalid.
        }
        return new Inspection(
                zipMagic,
                zip.contentTypes(),
                zip.documentXml(),
                reopened,
                textOrder,
                !zip.macros(),
                !zip.externalRelationships(),
                !zip.activeContent(),
                zip.entryCount(),
                zip.uncompressedBytes(),
                paragraphs,
                tables);
    }

    private static void configureDocument(
            XWPFDocument document, ResumeLayoutDefinition definition, Palette palette) {
        var section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        var pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(A4_WIDTH_TWIPS));
        pageSize.setH(BigInteger.valueOf(A4_HEIGHT_TWIPS));
        var margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setLeft(BigInteger.valueOf(ptToTwips(definition.page().effectiveMarginXPt())));
        margins.setRight(BigInteger.valueOf(ptToTwips(definition.page().effectiveMarginXPt())));
        margins.setTop(BigInteger.valueOf(ptToTwips(definition.page().effectiveMarginTopPt())));
        margins.setBottom(BigInteger.valueOf(ptToTwips(definition.page().effectiveMarginBottomPt())));
        margins.setHeader(BigInteger.valueOf(500));
        margins.setFooter(BigInteger.valueOf(500));

        XWPFStyles styles = document.createStyles();
        Typography typography = Typography.from(definition);
        addParagraphStyle(styles, "JobProofTitle", "JobProof Title", typography.halfPoints(40), true,
                palette.accent(), 0, 80, typography);
        addParagraphStyle(styles, "JobProofSubtitle", "JobProof Subtitle", typography.halfPoints(18), false,
                palette.muted(), 0, 180, typography);
        addParagraphStyle(styles, "JobProofHeading", "JobProof Section Heading", typography.halfPoints(22), true,
                palette.accent(), 160, 80, typography);
        addParagraphStyle(styles, "JobProofBody", "JobProof Body", typography.halfPoints(20), false,
                palette.body(), 0, 80, typography);
    }

    private static void addParagraphStyle(
            XWPFStyles styles, String id, String name, int halfPoints, boolean bold, String color,
            int beforeTwips, int afterTwips, Typography typography) {
        CTStyle style = CTStyle.Factory.newInstance();
        style.setStyleId(id);
        style.setType(STStyleType.PARAGRAPH);
        style.addNewName().setVal(name);
        style.addNewQFormat();
        CTRPr run = style.addNewRPr();
        var fonts = run.addNewRFonts();
        fonts.setAscii(typography.latinFont());
        fonts.setHAnsi(typography.latinFont());
        fonts.setEastAsia(typography.cjkFont());
        run.addNewSz().setVal(BigInteger.valueOf(halfPoints));
        run.addNewSzCs().setVal(BigInteger.valueOf(halfPoints));
        run.addNewColor().setVal(color);
        if (bold) run.addNewB();
        var paragraph = style.addNewPPr();
        var spacing = paragraph.addNewSpacing();
        spacing.setBefore(BigInteger.valueOf(beforeTwips));
        spacing.setAfter(BigInteger.valueOf(afterTwips));
        spacing.setLine(BigInteger.valueOf(typography.lineTwips(280)));
        styles.addStyle(new XWPFStyle(style));
    }

    private static Numbering configureNumbering(XWPFDocument document) {
        XWPFNumbering numbering = document.createNumbering();
        BigInteger bullet = addNumbering(numbering, STNumberFormat.BULLET, "•", 0);
        BigInteger decimal = addNumbering(numbering, STNumberFormat.DECIMAL, "%1.", 1);
        return new Numbering(bullet, decimal);
    }

    private static BigInteger addNumbering(
            XWPFNumbering numbering, STNumberFormat.Enum format, String marker, int abstractId) {
        CTAbstractNum definition = CTAbstractNum.Factory.newInstance();
        definition.setAbstractNumId(BigInteger.valueOf(abstractId));
        CTLvl level = definition.addNewLvl();
        level.setIlvl(BigInteger.ZERO);
        level.addNewStart().setVal(BigInteger.ONE);
        level.addNewNumFmt().setVal(format);
        level.addNewLvlText().setVal(marker);
        var paragraph = level.addNewPPr();
        CTInd indent = paragraph.addNewInd();
        indent.setLeft(BigInteger.valueOf(560));
        indent.setHanging(BigInteger.valueOf(280));
        BigInteger resolvedAbstractId = numbering.addAbstractNum(new XWPFAbstractNum(definition));
        return numbering.addNum(resolvedAbstractId);
    }

    private static void addHeader(
            XWPFDocument document,
            String title,
            ResumeDocumentModel.Header header,
            ResumeLayoutDefinition definition,
            String variant,
            Palette palette) {
        String headerStyle = definition.visual() == null
                ? "MINIMAL" : definition.visual().effectiveHeaderStyle().toUpperCase(Locale.ROOT);
        String subtitle = definition.visual() == null
                ? "JobProof 结构化简历" : definition.visual().effectiveSubtitle();
        Typography typography = Typography.from(definition);
        XWPFParagraph heading = document.createParagraph();
        heading.setStyle("JobProofTitle");
        heading.setSpacingBefore(0);
        heading.setSpacingAfter("COMPACT".equals(headerStyle) ? 40 : 80);
        if ("BAND".equals(headerStyle)) {
            shade(heading.getCTP().getPPr(), palette.accent());
            heading.setIndentationLeft(160);
            heading.setIndentationRight(160);
        }
        XWPFRun titleRun = heading.createRun();
        titleRun.setText(title);
        formatRun(titleRun, typography.points(20), true,
                "BAND".equals(headerStyle) ? "FFFFFF" : palette.accent(), typography);

        XWPFParagraph meta = document.createParagraph();
        meta.setStyle("JobProofSubtitle");
        meta.setSpacingAfter(header.contactLine().isBlank() ? 180 : 40);
        XWPFRun metaRun = meta.createRun();
        metaRun.setText(join(" · ", header.targetJob(), subtitle, variant));
        formatRun(metaRun, typography.points(9), false, palette.muted(), typography);
        if (header.contactLine().isBlank()) {
            meta.setBorderBottom(Borders.SINGLE);
        } else {
            XWPFParagraph contact = document.createParagraph();
            contact.setStyle("JobProofSubtitle");
            contact.setSpacingAfter(180);
            XWPFRun contactRun = contact.createRun();
            contactRun.setText(header.contactLine());
            formatRun(contactRun, typography.points(8.5), false, palette.muted(), typography);
            contact.setBorderBottom(Borders.SINGLE);
        }
    }

    private static String join(String separator, String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) result.add(value.trim());
        }
        return String.join(separator, result);
    }

    private static void addSections(
            IBody body,
            List<String> slotKeys,
            ResumeLayoutDefinition definition,
            Map<String, ResumeDocumentModel.Section> sections,
            Palette palette,
            Numbering numbering,
            int bodyWidthTwips) {
        Map<String, ResumeLayoutDefinition.Slot> slots = slotMap(definition);
        for (String slotKey : slotKeys) {
            ResumeDocumentModel.Section section = sections.get(slotKey);
            ResumeLayoutDefinition.Slot slot = slots.get(slotKey);
            if (slot == null || section == null || !hasContent(section)) continue;
            addSectionHeading(body, label(slot), slot.headingStyle(), palette, definition);
            addSectionBody(body, section, definition, palette, numbering, bodyWidthTwips);
        }
    }

    private static void addColumnSections(
            XWPFDocument document,
            ResumeLayoutDefinition definition,
            Map<String, ResumeDocumentModel.Section> sections,
            Palette palette,
            Numbering numbering) {
        int usable = A4_WIDTH_TWIPS - ptToTwips(definition.page().effectiveMarginXPt()) * 2
                - TABLE_INDENT_TWIPS;
        XWPFTable table = document.createTable(1, definition.columns().size());
        configureTable(table, usable, definition.columns().stream()
                .mapToInt(column -> Math.max(1, usable * column.widthPercent() / 100)).toArray(), false);
        for (int index = 0; index < definition.columns().size(); index++) {
            ResumeLayoutDefinition.Column column = definition.columns().get(index);
            XWPFTableCell cell = table.getRow(0).getCell(index);
            cell.removeParagraph(0);
            if (!"PLAIN".equalsIgnoreCase(column.effectiveTone())) shade(cell, palette.surface());
            int cellWidth = Math.max(720, usable * column.widthPercent() / 100 - 240);
            addSections(cell, column.slotKeys(), definition, sections, palette, numbering, cellWidth);
        }
    }

    private static void addTableSections(
            XWPFDocument document,
            ResumeLayoutDefinition definition,
            Map<String, ResumeDocumentModel.Section> sections,
            Palette palette,
            Numbering numbering) {
        List<ResumeLayoutDefinition.Slot> visible = definition.slots().stream()
                .filter(slot -> sections.containsKey(slot.key()))
                .filter(slot -> hasContent(sections.get(slot.key())))
                .sorted(Comparator.comparingInt(ResumeLayoutDefinition.Slot::order))
                .toList();
        if (visible.isEmpty()) return;
        int usable = A4_WIDTH_TWIPS - ptToTwips(definition.page().effectiveMarginXPt()) * 2
                - TABLE_INDENT_TWIPS;
        int labelWidth = Math.min(2200, usable / 4);
        XWPFTable table = document.createTable(visible.size(), 2);
        configureTable(table, usable, new int[]{labelWidth, usable - labelWidth}, true);
        for (int index = 0; index < visible.size(); index++) {
            ResumeLayoutDefinition.Slot slot = visible.get(index);
            XWPFTableCell label = table.getRow(index).getCell(0);
            XWPFTableCell detail = table.getRow(index).getCell(1);
            shade(label, palette.surface());
            XWPFParagraph labelParagraph = label.getParagraphs().get(0);
            labelParagraph.setStyle("JobProofHeading");
            labelParagraph.setSpacingAfter(0);
            XWPFRun labelRun = labelParagraph.createRun();
            labelRun.setText(label(slot));
            Typography typography = Typography.from(definition);
            formatRun(labelRun, typography.points(10.5), true, palette.accent(), typography);
            detail.removeParagraph(0);
            addSectionBody(detail, sections.get(slot.key()), definition, palette, numbering,
                    usable - labelWidth - 240);
        }
    }

    private static void addSectionBody(
            IBody body,
            ResumeDocumentModel.Section section,
            ResumeLayoutDefinition definition,
            Palette palette,
            Numbering numbering,
            int bodyWidthTwips) {
        if (section.entries().isEmpty()) {
            addBodyLines(body, section.plainText(), definition, palette, numbering);
        } else {
            addStructuredEntries(body, section.entries(), definition, palette, numbering, bodyWidthTwips);
        }
    }

    private static void addStructuredEntries(
            IBody body,
            List<ResumeDocumentModel.Entry> entries,
            ResumeLayoutDefinition definition,
            Palette palette,
            Numbering numbering,
            int bodyWidthTwips) {
        Typography typography = Typography.from(definition);
        String density = definition.visual() == null
                ? "STANDARD" : definition.visual().effectiveDensity().toUpperCase(Locale.ROOT);
        double fontSize = typography.points("COMPACT".equals(density) ? 9.2 : "AIRY".equals(density) ? 10.6 : 10.0);
        for (int index = 0; index < entries.size(); index++) {
            ResumeDocumentModel.Entry entry = entries.get(index);
            if (!entry.primary().isBlank() || !entry.date().isBlank()) {
                XWPFParagraph top = createParagraph(body);
                top.setStyle("JobProofBody");
                top.setKeepNext(true);
                top.setSpacingAfter(40);
                configureRightTab(top, bodyWidthTwips);
                if (!entry.primary().isBlank()) {
                    XWPFRun primary = top.createRun();
                    primary.setText(entry.primary());
                    formatRun(primary, fontSize, true, palette.body(), typography);
                }
                if (!entry.date().isBlank()) {
                    XWPFRun date = top.createRun();
                    date.addTab();
                    date.setText(entry.date());
                    formatRun(date, fontSize, false, palette.muted(), typography);
                }
            }
            String meta = join(" · ", entry.secondary(), entry.location());
            if (!meta.isBlank()) addSimpleBodyLine(body, meta, definition, palette, false);
            if (!entry.description().isBlank()) {
                addBodyLines(body, entry.description(), definition, palette, numbering);
            }
            for (String highlight : entry.highlights()) {
                addBodyLines(body, "• " + highlight, definition, palette, numbering);
            }
            if (index < entries.size() - 1) {
                XWPFParagraph spacer = createParagraph(body);
                spacer.setStyle("JobProofBody");
                spacer.setSpacingAfter(30);
            }
        }
    }

    private static void configureRightTab(XWPFParagraph paragraph, int bodyWidthTwips) {
        CTPPr properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        var tabs = properties.isSetTabs() ? properties.getTabs() : properties.addNewTabs();
        var tab = tabs.addNewTab();
        tab.setVal(STTabJc.RIGHT);
        tab.setPos(BigInteger.valueOf(Math.max(720, bodyWidthTwips)));
    }

    private static void addSimpleBodyLine(IBody body, String value, ResumeLayoutDefinition definition,
            Palette palette, boolean bold) {
        Typography typography = Typography.from(definition);
        String density = definition.visual() == null
                ? "STANDARD" : definition.visual().effectiveDensity().toUpperCase(Locale.ROOT);
        double fontSize = typography.points("COMPACT".equals(density) ? 9.2 : "AIRY".equals(density) ? 10.6 : 10.0);
        XWPFParagraph paragraph = createParagraph(body);
        paragraph.setStyle("JobProofBody");
        paragraph.setSpacingAfter(55);
        XWPFRun run = paragraph.createRun();
        run.setText(value);
        formatRun(run, fontSize, bold, palette.muted(), typography);
    }

    private static void addSectionHeading(IBody body, String label, String style, Palette palette,
            ResumeLayoutDefinition definition) {
        String headingStyle = style == null || style.isBlank() ? "RULE" : style.toUpperCase(Locale.ROOT);
        XWPFParagraph paragraph = createParagraph(body);
        paragraph.setStyle("JobProofHeading");
        paragraph.setKeepNext(true);
        paragraph.setSpacingBefore(160);
        paragraph.setSpacingAfter(60);
        if ("BAR".equals(headingStyle)) {
            shade(paragraph.getCTP().getPPr(), palette.surface());
            paragraph.setIndentationLeft(100);
        } else if ("SIDELINE".equals(headingStyle)) {
            paragraph.setBorderLeft(Borders.SINGLE);
            paragraph.setIndentationLeft(100);
        } else if ("RULE".equals(headingStyle)) {
            paragraph.setBorderBottom(Borders.SINGLE);
        }
        XWPFRun run = paragraph.createRun();
        run.setText(label);
        Typography typography = Typography.from(definition);
        formatRun(run, typography.points(11), true, palette.accent(), typography);
    }

    private static void addBodyLines(
            IBody body,
            String value,
            ResumeLayoutDefinition definition,
            Palette palette,
            Numbering numbering) {
        if (value == null || value.isBlank()) return;
        String density = definition.visual() == null
                ? "STANDARD" : definition.visual().effectiveDensity().toUpperCase(Locale.ROOT);
        Typography typography = Typography.from(definition);
        double fontSize = typography.points("COMPACT".equals(density) ? 9.2 : "AIRY".equals(density) ? 10.6 : 10.0);
        int lineSpacing = typography.lineTwips("COMPACT".equals(density) ? 264 : "AIRY".equals(density) ? 344 : 304);
        for (String sourceLine : value.replace("\r\n", "\n").split("\n", -1)) {
            ParsedLine line = ParsedLine.parse(sourceLine);
            XWPFParagraph paragraph = createParagraph(body);
            paragraph.setStyle("JobProofBody");
            paragraph.setSpacingAfter(line.text().isBlank() ? 40 : 70);
            paragraph.setSpacingLineRule(org.apache.poi.xwpf.usermodel.LineSpacingRule.AUTO);
            paragraph.setSpacingBetween(lineSpacing / 240.0);
            if (line.numbered()) paragraph.setNumID(numbering.decimal());
            else if (line.bullet()) paragraph.setNumID(numbering.bullet());
            if (!line.text().isBlank()) {
                XWPFRun run = paragraph.createRun();
                run.setText(line.text());
                formatRun(run, fontSize, false, palette.body(), typography);
            }
        }
    }

    private static void addFooter(XWPFDocument document, Palette palette) {
        XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(40);
        XWPFRun run = paragraph.createRun();
        run.setText("JobProof · ");
        formatRun(run, 8, false, palette.muted());
        var field = paragraph.getCTP().addNewFldSimple();
        field.setInstr("PAGE");
        field.addNewR().addNewT().setStringValue("1");
    }

    private static void configureTable(XWPFTable table, int totalWidth, int[] widths, boolean borders) {
        table.setTableAlignment(TableRowAlign.LEFT);
        CTTblPr properties = table.getCTTbl().getTblPr();
        CTTblWidth tableWidth = properties.isSetTblW() ? properties.getTblW() : properties.addNewTblW();
        tableWidth.setType(STTblWidth.DXA);
        tableWidth.setW(BigInteger.valueOf(totalWidth));
        CTTblWidth tableIndent = properties.getTblInd() == null
                ? properties.addNewTblInd() : properties.getTblInd();
        tableIndent.setType(STTblWidth.DXA);
        tableIndent.setW(BigInteger.valueOf(TABLE_INDENT_TWIPS));
        CTTblLayoutType layout = properties.isSetTblLayout()
                ? properties.getTblLayout() : properties.addNewTblLayout();
        layout.setType(STTblLayoutType.FIXED);
        var grid = table.getCTTbl().getTblGrid() == null
                ? table.getCTTbl().addNewTblGrid() : table.getCTTbl().getTblGrid();
        while (grid.sizeOfGridColArray() > 0) grid.removeGridCol(0);
        for (int width : widths) grid.addNewGridCol().setW(BigInteger.valueOf(width));
        for (int rowIndex = 0; rowIndex < table.getNumberOfRows(); rowIndex++) {
            for (int column = 0; column < widths.length; column++) {
                XWPFTableCell cell = table.getRow(rowIndex).getCell(column);
                CTTcPr cellProperties = cell.getCTTc().isSetTcPr()
                        ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                CTTblWidth cellWidth = cellProperties.isSetTcW()
                        ? cellProperties.getTcW() : cellProperties.addNewTcW();
                cellWidth.setType(STTblWidth.DXA);
                cellWidth.setW(BigInteger.valueOf(widths[column]));
                setCellMargins(cellProperties, 90, 90, 120, 120);
            }
        }
        if (!borders) table.removeBorders();
    }

    private static void setCellMargins(CTTcPr properties, int top, int bottom, int start, int end) {
        CTTcMar margins = properties.isSetTcMar() ? properties.getTcMar() : properties.addNewTcMar();
        setWidth(margins.isSetTop() ? margins.getTop() : margins.addNewTop(), top);
        setWidth(margins.isSetBottom() ? margins.getBottom() : margins.addNewBottom(), bottom);
        setWidth(margins.isSetStart() ? margins.getStart() : margins.addNewStart(), start);
        setWidth(margins.isSetEnd() ? margins.getEnd() : margins.addNewEnd(), end);
    }

    private static void setWidth(CTTblWidth width, int value) {
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(value));
    }

    private static void formatRun(XWPFRun run, double size, boolean bold, String color) {
        run.setFontFamily(LATIN_FONT);
        run.setFontFamily(CJK_FONT, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(color);
        run.setUnderline(UnderlinePatterns.NONE);
    }

    private static void formatRun(
            XWPFRun run, double size, boolean bold, String color, Typography typography) {
        run.setFontFamily(typography.latinFont());
        run.setFontFamily(typography.cjkFont(), XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        run.setBold(bold);
        run.setColor(color);
        run.setUnderline(UnderlinePatterns.NONE);
    }

    private static XWPFParagraph createParagraph(IBody body) {
        if (body instanceof XWPFDocument document) return document.createParagraph();
        if (body instanceof XWPFTableCell cell) return cell.addParagraph();
        throw new IllegalArgumentException("Unsupported DOCX body: " + body.getClass().getName());
    }

    private static void shade(CTPPr properties, String color) {
        if (properties == null) return;
        if (properties.isSetShd()) properties.getShd().setFill(color);
        else properties.addNewShd().setFill(color);
    }

    private static void shade(XWPFTableCell cell, String color) {
        CTTcPr properties = cell.getCTTc().isSetTcPr()
                ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        if (properties.isSetShd()) properties.getShd().setFill(color);
        else properties.addNewShd().setFill(color);
    }

    private static Map<String, ResumeDocumentModel.Section> sections(ResumeDocumentModel model) {
        Map<String, ResumeDocumentModel.Section> result = new LinkedHashMap<>();
        for (ResumeDocumentModel.Section section : model.sections()) result.putIfAbsent(section.slotKey(), section);
        return result;
    }

    private static boolean hasContent(ResumeDocumentModel.Section section) {
        return section != null && (!section.entries().isEmpty() || !section.plainText().isBlank());
    }

    private static void addExpected(List<String> expected, String value) {
        if (value != null && !value.isBlank()) expected.add(value);
    }

    private static Map<String, ResumeLayoutDefinition.Slot> slotMap(ResumeLayoutDefinition definition) {
        Map<String, ResumeLayoutDefinition.Slot> result = new HashMap<>();
        for (ResumeLayoutDefinition.Slot slot : definition.slots()) result.put(slot.key(), slot);
        return result;
    }

    private static String label(ResumeLayoutDefinition.Slot slot) {
        if (slot.label() != null && !slot.label().isBlank()) return slot.label();
        return switch (slot.key()) {
            case "summary" -> "个人简介";
            case "education" -> "教育经历";
            case "experience" -> "工作经历";
            case "projects" -> "项目经历";
            case "skills" -> "专业技能";
            case "certificates" -> "证书与资质";
            default -> slot.key();
        };
    }

    private static int ptToTwips(int points) {
        return Math.max(0, points * 20);
    }

    private static ZipInspection inspectZip(byte[] bytes) {
        if (bytes == null) return ZipInspection.empty();
        boolean contentTypes = false;
        boolean documentXml = false;
        boolean macros = false;
        boolean external = false;
        boolean active = false;
        int entries = 0;
        long uncompressed = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) return ZipInspection.empty();
                ByteArrayOutputStream entryBytes = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    uncompressed += read;
                    if (uncompressed > MAX_UNCOMPRESSED_BYTES) return ZipInspection.empty();
                    if (entryBytes.size() < 2 * 1024 * 1024) entryBytes.write(buffer, 0, read);
                }
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) contentTypes = true;
                if ("word/document.xml".equals(name)) documentXml = true;
                if (name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) macros = true;
                if (name.endsWith(".rels")) {
                    String xml = entryBytes.toString(StandardCharsets.UTF_8);
                    if (xml.contains("TargetMode=\"External\"")) external = true;
                }
                if ("word/document.xml".equals(name)) {
                    String xml = entryBytes.toString(StandardCharsets.UTF_8);
                    if (xml.contains("<w:altChunk") || xml.contains("<w:object") || xml.contains("<w:control")) {
                        active = true;
                    }
                }
            }
            return new ZipInspection(contentTypes, documentXml, macros, external, active, entries, uncompressed);
        } catch (IOException exception) {
            return ZipInspection.empty();
        }
    }

    private static boolean appearsInOrder(String rendered, List<String> expected) {
        int cursor = 0;
        for (String value : expected) {
            String normalized = normalize(value);
            if (normalized.isBlank()) continue;
            int found = rendered.indexOf(normalized, cursor);
            if (found < 0) return false;
            cursor = found + normalized.length();
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private record Palette(String accent, String surface, String body, String muted) {
        private static Palette from(ResumeLayoutDefinition definition, String variant) {
            Map<String, String> tokens = definition.tokens() == null ? Map.of() : definition.tokens();
            String accent = color(tokens.get("accent." + variant), "1F2937");
            String surface = color(tokens.get("surface." + variant), "F3F4F6");
            String body = color(tokens.get("body"), "1F2937");
            String muted = color(tokens.get("muted"), "667085");
            return new Palette(accent, surface, body, muted);
        }

        private static String color(String value, String fallback) {
            return value != null && value.matches("#[0-9A-Fa-f]{6}") ? value.substring(1) : fallback;
        }
    }

    private record Typography(String latinFont, String cjkFont, double scale, double lineScale) {
        private static Typography from(ResumeLayoutDefinition definition) {
            Map<String, String> tokens = definition.tokens() == null ? Map.of() : definition.tokens();
            boolean serif = "CLASSIC_SERIF".equalsIgnoreCase(tokens.get("fontPreset"));
            double scale = switch (tokens.getOrDefault("fontScale", "STANDARD").toUpperCase(Locale.ROOT)) {
                case "SMALL" -> 0.90;
                case "LARGE" -> 1.08;
                default -> 1.0;
            };
            double lineScale = switch (tokens.getOrDefault("lineHeight", "STANDARD").toUpperCase(Locale.ROOT)) {
                case "COMPACT" -> 0.90;
                case "AIRY" -> 1.14;
                default -> 1.0;
            };
            return new Typography(serif ? "Times New Roman" : LATIN_FONT,
                    serif ? "SimSun" : CJK_FONT, scale, lineScale);
        }

        private double points(double base) {
            return Math.round(base * scale * 10.0) / 10.0;
        }

        private int halfPoints(int base) {
            return Math.max(1, (int) Math.round(base * scale));
        }

        private int lineTwips(int base) {
            return Math.max(1, (int) Math.round(base * lineScale));
        }
    }

    private record Numbering(BigInteger bullet, BigInteger decimal) {
    }

    private record ParsedLine(String text, boolean bullet, boolean numbered) {
        private static ParsedLine parse(String value) {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.matches("^[-*•]\\s+.*")) return new ParsedLine(trimmed.substring(1).trim(), true, false);
            if (trimmed.matches("^\\d+[.)、]\\s+.*")) {
                return new ParsedLine(trimmed.replaceFirst("^\\d+[.)、]\\s+", ""), false, true);
            }
            return new ParsedLine(trimmed, false, false);
        }
    }

    private record ZipInspection(
            boolean contentTypes,
            boolean documentXml,
            boolean macros,
            boolean externalRelationships,
            boolean activeContent,
            int entryCount,
            long uncompressedBytes) {
        private static ZipInspection empty() {
            return new ZipInspection(false, false, true, true, true, 0, 0);
        }
    }

    public record Inspection(
            boolean zipMagic,
            boolean contentTypes,
            boolean documentXml,
            boolean reopened,
            boolean textOrder,
            boolean macroFree,
            boolean externalRelationshipFree,
            boolean activeContentFree,
            int entryCount,
            long uncompressedBytes,
            int paragraphCount,
            int tableCount) {
        public boolean valid() {
            return zipMagic && contentTypes && documentXml && reopened && textOrder && macroFree
                    && externalRelationshipFree && activeContentFree && entryCount > 0
                    && uncompressedBytes > 0 && uncompressedBytes <= MAX_UNCOMPRESSED_BYTES;
        }
    }
}
