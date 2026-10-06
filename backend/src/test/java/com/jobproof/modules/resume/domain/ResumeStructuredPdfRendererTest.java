package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

class ResumeStructuredPdfRendererTest {

    @Test
    void v3AlignsTimelineDateWithThePrimaryTitle() throws Exception {
        ResumeDocumentModel.Entry education = new ResumeDocumentModel.Entry(
                "河南科技学院", "数据科学与大数据技术 · 本科",
                "2026.02 - 2026.09", "新乡",
                "系统学习数据结构、数据库原理和机器学习。", List.of("完成课程实践"));
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                new ResumeDocumentModel.Section(
                        "education",
                        "河南科技学院 · 数据科学与大数据技术 · 本科\n"
                                + "2026.02 - 2026.09 · 新乡\n系统学习数据结构、数据库原理和机器学习。",
                        1,
                        List.of(education))));

        byte[] bytes = ResumePdfRenderer.render(
                "日期位置验收", document, ResumeLayoutProtocolTest.twoColumnDefinition(),
                "BLUE", ResumeLayoutProtocol.V3);

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            Map<String, Float> positions = textYPositions(pdf, "河南科技学院", "2026.02 - 2026.09");
            assertThat(positions).containsKeys("河南科技学院", "2026.02 - 2026.09");
            assertThat(positions.get("河南科技学院"))
                    .isCloseTo(positions.get("2026.02 - 2026.09"),
                            org.assertj.core.data.Offset.offset(0.6f));
        }
    }

    @Test
    void v3RendersCanonicalHeaderFactsInsteadOfDroppingThem() throws Exception {
        ResumeDocumentModel document = new ResumeDocumentModel(
                new ResumeDocumentModel.Header(
                        "陈同学",
                        "Java Backend Engineer",
                        "candidate@example.com",
                        "13800000000",
                        "杭州",
                        List.of("https://example.com/portfolio")),
                List.of(new ResumeDocumentModel.Section("education", "示例科技大学 · 软件工程", 1)));

        byte[] bytes = ResumePdfRenderer.render(
                "应届生求职简历",
                document,
                ResumeLayoutProtocolTest.twoColumnDefinition(),
                "BLUE",
                ResumeLayoutProtocol.V3);

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(new PDFTextStripper().getText(pdf)).contains(
                    "陈同学",
                    "Java Backend Engineer",
                    "candidate@example.com",
                    "13800000000",
                    "杭州",
                    "https://example.com/portfolio",
                    "示例科技大学");
        }
    }

    @Test
    void v2RendersDefinitionDrivenColumnsAndKeepsControlledExtractionOrder() throws Exception {
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                new ResumeDocumentModel.Section("summary", "证据优先，稳定交付。", 1),
                new ResumeDocumentModel.Section("skills", "Java / Spring Boot / SQL", 1),
                new ResumeDocumentModel.Section("certificates", "云原生工程师", 1),
                new ResumeDocumentModel.Section("experience", "超长公司名称（中国）有限公司 · Backend Engineer", 1),
                new ResumeDocumentModel.Section("projects", "项目甲：结构化模板\nProject Beta: immutable export", 2),
                new ResumeDocumentModel.Section("education", "测试大学 · 软件工程", 1)));

        byte[] bytes = ResumePdfRenderer.render(
                "技术版式验收",
                document,
                ResumeLayoutProtocolTest.twoColumnDefinition(),
                "BLUE",
                ResumeLayoutProtocol.V2);

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains(
                    "技术版式验收", "技术项目简历", "证据优先", "Spring Boot",
                    "超长公司名称（中国）有限公司", "Project Beta", "第 1 / 1 页");
            assertThat(text.indexOf("个人简介")).isLessThan(text.indexOf("专业技能"));
            assertThat(text.indexOf("专业技能")).isLessThan(text.indexOf("工作经历"));
            assertThat(pdf.getPage(0).getResources().getFontNames())
                    .anySatisfy(name -> assertThat(pdf.getPage(0).getResources().getFont(name).isEmbedded()).isTrue());

            BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(0, 144);
            long tintedLeft = tintedPixels(image, 0, image.getWidth() / 2);
            long tintedRight = tintedPixels(image, image.getWidth() / 2, image.getWidth());
            assertThat(tintedLeft).isGreaterThan(tintedRight * 2);
        }
    }

    @Test
    void v2FailsClosedWhenPhysicalLayoutWouldExceedTheControlledPageCount() {
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                new ResumeDocumentModel.Section("experience", "Long mixed content 中英文混排 ".repeat(1500), 1)));

        assertThatThrownBy(() -> ResumePdfRenderer.render(
                "分页阻断",
                document,
                ResumeLayoutProtocolTest.twoColumnDefinition(),
                "BLUE",
                ResumeLayoutProtocol.V2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maxPages=1");
    }

    @Test
    void v3PaginatesEveryCanonicalSectionWithoutDroppingTheFinalContent() throws Exception {
        ResumeDocumentModel document = new ResumeDocumentModel(
                new ResumeDocumentModel.Header(
                        "林知远", "Platform Engineer", "lin@example.com", "13800001111", "杭州",
                        List.of("https://portfolio.example.com/lin")),
                List.of(
                        section("summary", "SUMMARY-END", 2),
                        section("education", "EDUCATION-END", 3),
                        section("experience", "EXPERIENCE-END", 12),
                        section("projects", "PROJECTS-END", 10),
                        section("organizations", "ORGANIZATIONS-END", 3),
                        section("skills", "SKILLS-END", 4),
                        section("certificates", "CERTIFICATES-END", 2),
                        section("honors", "HONORS-END", 2),
                        section("languages", "LANGUAGES-END", 2)));

        byte[] bytes = ResumePdfRenderer.render(
                "九模块双页验收", document, twoPageAllSectionsDefinition(), "BLUE", ResumeLayoutProtocol.V3);

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains(
                    "林知远", "lin@example.com", "observability",
                    "SUMMARY-END", "EDUCATION-END", "EXPERIENCE-END", "PROJECTS-END",
                    "ORGANIZATIONS-END", "SKILLS-END", "CERTIFICATES-END", "HONORS-END",
                    "LANGUAGES-END", "第 1 / 2 页", "第 2 / 2 页");
            for (int pageIndex = 0; pageIndex < pdf.getNumberOfPages(); pageIndex++) {
                assertThat(inkPixels(new PDFRenderer(pdf).renderImageWithDPI(pageIndex, 72)))
                        .as("page %s", pageIndex + 1).isGreaterThan(1_000);
            }
            int firstPageCharacters = pageText(pdf, 1).replaceAll("\\s+", "").length();
            int secondPageCharacters = pageText(pdf, 2).replaceAll("\\s+", "").length();
            assertThat(firstPageCharacters)
                    .as("the first page is filled before continuation content is created")
                    .isGreaterThan(secondPageCharacters);
            assertThat(secondPageCharacters)
                    .as("the continuation page still contains the final controlled content")
                    .isGreaterThan(80);
        }
    }

    @Test
    void v3FillsTheFirstPageBeforeStartingAContinuationPage() throws Exception {
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                new ResumeDocumentModel.Section("summary", "摘要内容".repeat(240), 1),
                new ResumeDocumentModel.Section(
                        "honors", "HONORS-START " + "荣誉说明".repeat(900) + " HONORS-END", 1)));
        ResumeLayoutDefinition definition = ResumeLayoutProtocol.validate(
                ResumeLayoutProtocol.V3,
                new ResumeLayoutDefinition(
                        new ResumeLayoutDefinition.Page(2, 4_000, 44, 42, 44),
                        List.of(new ResumeLayoutDefinition.Column(
                                "main", 100, List.of("summary", "honors"), "PLAIN")),
                        List.of(
                                new ResumeLayoutDefinition.Slot(
                                        "summary", 10, 1_500, false, true, "个人概况", "BAR"),
                                new ResumeLayoutDefinition.Slot(
                                        "honors", 20, 3_000, true, true, "荣誉奖项", "BAR")),
                        Map.of(
                                "accent.BLUE", "#0F766E",
                                "surface.BLUE", "#ECFDF3",
                                "body", "#1F2937",
                                "muted", "#667085"),
                        new ResumeLayoutDefinition.Visual(
                                "BAND", "BAR", "校园与实践简历", false, "COMPACT")));

        byte[] bytes = ResumePdfRenderer.render(
                "顺序填充分页", document, definition, "BLUE", ResumeLayoutProtocol.V3);

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            assertThat(pageText(pdf, 1)).contains("HONORS-START");
            assertThat(pageText(pdf, 2)).contains("HONORS-END");
        }
    }

    @Test
    void v3EmbedsTheControlledSerifFontAndPrivatePhoto() throws Exception {
        BufferedImage source = new BufferedImage(120, 160, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = source.createGraphics();
        graphics.setColor(new java.awt.Color(23, 92, 211));
        graphics.fillRect(0, 0, 120, 80);
        graphics.setColor(new java.awt.Color(240, 249, 255));
        graphics.fillRect(0, 80, 120, 80);
        graphics.dispose();
        ByteArrayOutputStream photoBytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", photoBytes);

        ResumeDocumentModel document = new ResumeDocumentModel(
                new ResumeDocumentModel.Header(
                        "宋体与照片验收", "数据分析师", "candidate@example.com", "13800000000", "上海",
                        List.of(), new ResumeDocumentModel.Photo("image/png", photoBytes.toByteArray())),
                List.of(new ResumeDocumentModel.Section("summary", "结构化事实与 PDF 视觉验收。", 1)));
        ResumeLayoutDefinition base = ResumeLayoutProtocolTest.twoColumnDefinition();
        ResumeDesignSettings settings = ResumeDesignSettings.defaults(
                base, "MONO", "OPTIONAL", "rlt-b-career-pro-v1");
        byte[] bytes = ResumePdfRenderer.render(
                "宋体与照片验收", document, settings.applyTo(base), "MONO", ResumeLayoutProtocol.V3);

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("宋体与照片验收", "结构化事实");
            assertThat(pdf.getPage(0).getResources().getFontNames()).anySatisfy(name ->
                    assertThat(pdf.getPage(0).getResources().getFont(name).getName())
                            .containsIgnoringCase("NotoSerifSC"));
            assertThat(pdf.getPage(0).getResources().getXObjectNames()).isNotEmpty();
        }
    }

    private static ResumeDocumentModel.Section section(String key, String marker, int lines) {
        StringBuilder value = new StringBuilder();
        for (int index = 1; index <= lines; index++) {
            if (!value.isEmpty()) value.append('\n');
            value.append("第 ").append(index).append(" 条中英文混排内容用于验证 PDF observability 与稳定分页");
        }
        value.append('\n').append(marker);
        return new ResumeDocumentModel.Section(key, value.toString(), lines);
    }

    private static ResumeLayoutDefinition twoPageAllSectionsDefinition() {
        List<String> keys = List.of("summary", "education", "experience", "projects", "organizations",
                "skills", "certificates", "honors", "languages");
        List<ResumeLayoutDefinition.Slot> slots = new java.util.ArrayList<>();
        for (int index = 0; index < keys.size(); index++) {
            slots.add(new ResumeLayoutDefinition.Slot(
                    keys.get(index), (index + 1) * 10, 2_500, true, true,
                    keys.get(index).toUpperCase(), "RULE"));
        }
        return ResumeLayoutProtocol.validate(ResumeLayoutProtocol.V3, new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(2, 7_000, 48, 44, 44),
                List.of(new ResumeLayoutDefinition.Column("main", 100, keys, "PLAIN")),
                slots,
                java.util.Map.of("accent.BLUE", "#175CD3", "body", "#1F2937", "muted", "#667085"),
                new ResumeLayoutDefinition.Visual("COMPACT", "RULE", "九模块结构化简历", false, "COMPACT")));
    }

    private static long inkPixels(BufferedImage image) {
        long result = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                if (red < 245 || green < 245 || blue < 245) result++;
            }
        }
        return result;
    }

    private static long tintedPixels(BufferedImage image, int fromX, int toX) {
        long result = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = fromX; x < toX; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                if (red >= 225 && red <= 245 && green >= 235 && green <= 250 && blue >= 248) result++;
            }
        }
        return result;
    }

    private static String pageText(PDDocument document, int pageNumber) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(document);
    }

    private static Map<String, Float> textYPositions(PDDocument document, String... terms) throws IOException {
        class PositionStripper extends PDFTextStripper {
            private final Map<String, Float> positions = new HashMap<>();

            PositionStripper() throws IOException {
            }

            @Override
            protected void writeString(String text, List<TextPosition> textPositions) {
                for (String term : terms) {
                    if (text.contains(term) && !textPositions.isEmpty()) {
                        positions.putIfAbsent(term, textPositions.get(0).getYDirAdj());
                    }
                }
            }
        }
        PositionStripper stripper = new PositionStripper();
        stripper.getText(document);
        return stripper.positions;
    }
}
