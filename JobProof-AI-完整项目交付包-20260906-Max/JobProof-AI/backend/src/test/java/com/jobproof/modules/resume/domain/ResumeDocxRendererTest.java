package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.junit.jupiter.api.Test;

class ResumeDocxRendererTest {

    @Test
    void keepsTimelineTitleAndDateInOneParagraphWithARightTabStop() throws Exception {
        ResumeDocumentModel.Entry education = new ResumeDocumentModel.Entry(
                "河南科技学院", "数据科学与大数据技术 · 本科",
                "2026.02 - 2026.09", "新乡",
                "系统学习数据结构、数据库原理和机器学习。", List.of("完成课程实践"));
        ResumeDocumentModel content = new ResumeDocumentModel(List.of(
                new ResumeDocumentModel.Section(
                        "education",
                        "河南科技学院 · 数据科学与大数据技术 · 本科\n"
                                + "2026.02 - 2026.09 · 新乡\n系统学习数据结构、数据库原理和机器学习。",
                        1,
                        List.of(education))));

        byte[] bytes = render("日期位置验收", content, technicalDefinition(), "BLUE",
                ResumeLayoutProtocol.V3, "rlt-b-tech-double-v1");

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var row = document.getParagraphs().stream()
                    .filter(paragraph -> paragraph.getText().contains("河南科技学院")
                            && paragraph.getText().contains("2026.02 - 2026.09"))
                    .findFirst();
            assertThat(row).isPresent();
            assertThat(row.orElseThrow().getCTP().getPPr().getTabs().getTabList())
                    .anySatisfy(tab -> assertThat(tab.getVal()).isEqualTo(STTabJc.RIGHT));
        }
    }

    @Test
    void rendersAndReopensTheThreeSmartTemplateFamilies() throws Exception {
        Path output = Path.of("target", "docx-acceptance");
        Files.createDirectories(output);

        ResumeDocumentModel atsContent = document(
                "事实清晰，关注稳定交付。",
                "华中科技大学 软件工程 本科",
                "JobProof 后端开发\n负责版本冻结与导出链路",
                "模板中心\n实现内容与版式分离",
                "Java / Spring Boot / Vue / TypeScript",
                "英语六级");
        byte[] ats = render("张明 - 软件工程师", atsContent, atsDefinition(), "BLUE",
                ResumeLayoutProtocol.V1, "rlt-b-ats-minimal-v1");
        Files.write(output.resolve("ats-minimal-short.docx"), ats);
        assertValid(ats, "张明 - 软件工程师", atsContent, atsDefinition(), 0);

        String experience = String.join("\n", java.util.Collections.nCopies(24,
                "负责招聘平台服务治理、可观测性与发布质量，保持输入事实和结果可追溯。"));
        ResumeDocumentModel technicalContent = document(
                "Senior backend engineer focused on reliable delivery and measurable outcomes.",
                "同济大学 Software Engineering / 软件工程",
                experience,
                "JobProof Template Center\nBuilt async DOCX export and immutable snapshot validation.",
                "Java 17, Spring Boot, MySQL 8, Redis, TypeScript",
                "AWS Certified Developer");
        byte[] technical = render("李华 / Senior Backend Engineer", technicalContent,
                technicalDefinition(), "BLUE", ResumeLayoutProtocol.V2, "rlt-b-tech-double-v1");
        Files.write(output.resolve("technical-double-long.docx"), technical);
        assertValid(technical, "李华 / Senior Backend Engineer", technicalContent, technicalDefinition(), 0);

        ResumeDocumentModel tableContent = document(
                "应届毕业生，具备数据分析和跨团队协作能力。",
                "2022.09 - 2026.06 复旦大学 金融学 本科",
                "2025.07 - 2025.12 财务实习生\n1. 整理月度报表并核对来源数据",
                "预算分析项目\n- 使用 Excel / SQL 完成差异分析",
                "Excel, SQL, Power BI, English CET-6",
                "初级会计专业技术资格");
        byte[] table = render("王悦 - 财务分析", tableContent, tableDefinition(), "STANDARD",
                ResumeLayoutProtocol.V2, "rlt-b-cn-table-v1");
        Files.write(output.resolve("chinese-table-mixed.docx"), table);
        assertValid(table, "王悦 - 财务分析", tableContent, tableDefinition(), 1);
    }

    private static byte[] render(
            String title,
            ResumeDocumentModel content,
            ResumeLayoutDefinition definition,
            String variant,
            String protocol,
            String templateId) {
        return ResumeDocxRenderer.render(title, content, definition, variant, protocol, templateId);
    }

    private static void assertValid(
            byte[] bytes,
            String title,
            ResumeDocumentModel content,
            ResumeLayoutDefinition definition,
            int expectedTables) throws Exception {
        ResumeDocxRenderer.Inspection inspection = ResumeDocxRenderer.inspect(bytes, title, content, definition);
        assertThat(inspection.valid()).as(inspection.toString()).isTrue();
        assertThat(inspection.zipMagic()).isTrue();
        assertThat(inspection.contentTypes()).isTrue();
        assertThat(inspection.documentXml()).isTrue();
        assertThat(inspection.textOrder()).isTrue();
        assertThat(inspection.macroFree()).isTrue();
        assertThat(inspection.externalRelationshipFree()).isTrue();
        assertThat(inspection.activeContentFree()).isTrue();
        assertThat(inspection.tableCount()).isEqualTo(expectedTables);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            assertThat(document.getDocument().getBody().getSectPr().getPgSz().getW())
                    .isEqualTo(java.math.BigInteger.valueOf(11906));
            assertThat(extractor.getText()).contains(title);
        }
    }

    private static ResumeDocumentModel document(
            String summary, String education, String experience, String projects, String skills, String certificates) {
        return new ResumeDocumentModel(List.of(
                section("education", education),
                section("experience", experience),
                section("projects", projects),
                section("skills", skills),
                section("certificates", certificates),
                section("summary", summary)));
    }

    private static ResumeDocumentModel.Section section(String key, String text) {
        return new ResumeDocumentModel.Section(key, text, Math.max(1, text.split("\\R", -1).length));
    }

    private static ResumeLayoutDefinition atsDefinition() {
        return definition(1, null, null, "JobProof 结构化简历", "COMPACT", List.of(
                slot("summary", 10, "个人简介", "RULE"),
                slot("education", 20, "教育经历", "RULE"),
                slot("experience", 30, "工作经历", "RULE"),
                slot("projects", 40, "项目经历", "RULE"),
                slot("skills", 50, "专业技能", "RULE"),
                slot("certificates", 60, "证书与资质", "RULE")));
    }

    private static ResumeLayoutDefinition technicalDefinition() {
        return definition(2, "COMPACT", "SIDELINE", "技术经历双页", "STANDARD", List.of(
                slot("summary", 10, "职业摘要", "SIDELINE"),
                slot("experience", 20, "技术经历", "SIDELINE"),
                slot("projects", 30, "项目成果", "SIDELINE"),
                slot("skills", 40, "技术能力", "SIDELINE"),
                slot("education", 50, "教育经历", "SIDELINE"),
                slot("certificates", 60, "认证资质", "SIDELINE")));
    }

    private static ResumeLayoutDefinition tableDefinition() {
        return definition(1, "BAND", "TABLE", "中文标准表格简历", "COMPACT", List.of(
                slot("summary", 10, "个人概况", "TABLE"),
                slot("education", 20, "教育经历", "TABLE"),
                slot("experience", 30, "工作经历", "TABLE"),
                slot("projects", 40, "项目经历", "TABLE"),
                slot("skills", 50, "专业技能", "TABLE"),
                slot("certificates", 60, "证书与资质", "TABLE")));
    }

    private static ResumeLayoutDefinition definition(
            int pages,
            String header,
            String sectionStyle,
            String subtitle,
            String density,
            List<ResumeLayoutDefinition.Slot> slots) {
        List<String> keys = slots.stream().sorted(java.util.Comparator.comparingInt(ResumeLayoutDefinition.Slot::order))
                .map(ResumeLayoutDefinition.Slot::key).toList();
        return new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(pages, 5_000, 48, 44, 44),
                List.of(new ResumeLayoutDefinition.Column("main", 100, keys, "PLAIN")),
                slots,
                Map.of(
                        "accent.BLUE", "#175CD3",
                        "surface.BLUE", "#EEF4FF",
                        "accent.STANDARD", "#334155",
                        "surface.STANDARD", "#F1F5F9",
                        "body", "#1F2937",
                        "muted", "#667085"),
                header == null ? null : new ResumeLayoutDefinition.Visual(
                        header, sectionStyle, subtitle, false, density));
    }

    private static ResumeLayoutDefinition.Slot slot(String key, int order, String label, String style) {
        return new ResumeLayoutDefinition.Slot(key, order, 2_500, true, true, label, style);
    }
}
