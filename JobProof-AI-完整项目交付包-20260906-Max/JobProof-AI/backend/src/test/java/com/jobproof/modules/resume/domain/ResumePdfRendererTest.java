package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ResumePdfRendererTest {

    @Test
    void embedsPortableCjkTextAndRendersTheSelectedAccent() throws Exception {
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                new ResumeDocumentModel.Section("summary", "重视可验证事实与稳定交付。", 1),
                new ResumeDocumentModel.Section("education", "测试大学 软件工程 本科 2022-2026", 1),
                new ResumeDocumentModel.Section("skills", "Java, Spring Boot, Vue, TypeScript, SQL", 1)));
        ResumeLayoutDefinition definition = new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(1, 3600),
                List.of(new ResumeLayoutDefinition.Column("main", 100, List.of("summary", "education", "skills"))),
                List.of(
                        new ResumeLayoutDefinition.Slot("summary", 10, 420, false, true),
                        new ResumeLayoutDefinition.Slot("education", 20, 520, true, true),
                        new ResumeLayoutDefinition.Slot("skills", 30, 520, true, true)),
                Map.of("accent.BLUE", "#175CD3"));

        byte[] bytes = ResumePdfRenderer.render("结构化模板验收简历", document, definition, "BLUE");

        assertThat(bytes).hasSizeGreaterThan(5_000);
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo("结构化模板验收简历");
            String extractedText = new PDFTextStripper().getText(pdf);
            assertThat(extractedText)
                    .contains("结构化模板验收简历", "结构化简历 · BLUE", "个人简介", "测试大学", "软件工程",
                            "TypeScript", "第 1 / 1 页")
                    .doesNotContain("resume-");
            assertThat(extractedText.codePoints()
                    .noneMatch(codePoint -> codePoint >= 0x2E80 && codePoint <= 0x2FDF)).isTrue();
            assertThat(pdf.getPage(0).getResources().getFontNames())
                    .anySatisfy(name -> assertThat(pdf.getPage(0).getResources().getFont(name).isEmbedded()).isTrue());

            BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(0, 144);
            long bluePixels = 0;
            List<Integer> horizontalRuleRows = new ArrayList<>();
            for (int y = 0; y < image.getHeight(); y++) {
                int bluePixelsInRow = 0;
                int darkPixelsInRow = 0;
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y);
                    int red = (rgb >> 16) & 0xff;
                    int green = (rgb >> 8) & 0xff;
                    int blue = rgb & 0xff;
                    if (blue > 120 && blue > red * 1.3 && blue > green * 1.15) {
                        bluePixels++;
                        bluePixelsInRow++;
                    }
                    if (red < 110 && green < 110 && blue < 110) {
                        darkPixelsInRow++;
                    }
                }
                if (bluePixelsInRow > image.getWidth() / 2) {
                    horizontalRuleRows.add(y);
                    assertThat(darkPixelsInRow)
                            .as("horizontal accent rule at row %s must not cross body text", y)
                            .isLessThan(5);
                }
            }
            assertThat(bluePixels).isGreaterThan(100);
            assertThat(horizontalRuleRows).hasSizeGreaterThanOrEqualTo(4);
        }
    }

    @Test
    void keepsNumberedItemHeadingWithItsFollowingDetail() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < 42; index++) {
            body.append("用于填充首页的验收内容").append(index).append('\n');
        }
        body.append("6. 测试质量与代码规范\n")
                .append("该条正文必须与编号标题一起出现在下一页，避免标题孤立在上一页底部。");

        byte[] bytes = ResumePdfRenderer.render("岗位匹配报告", body.toString());

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            assertThat(stripper.getText(pdf)).doesNotContain("6. 测试质量与代码规范");
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertThat(stripper.getText(pdf))
                    .contains("6. 测试质量与代码规范", "该条正文必须与编号标题一起出现在下一页");
        }
    }
}
