package com.jobproof.modules.resume.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public final class ResumeTemplateEvidenceFixtures {
    public enum Scenario {
        SHORT,
        MEDIUM,
        LONG,
        BOUNDARY
    }

    private static final Map<String, String> MARKERS = markers();

    private ResumeTemplateEvidenceFixtures() {
    }

    public static ResumeDocumentModel document(Scenario scenario) {
        return switch (scenario) {
            case SHORT -> model(
                    lines("summary", "可验证事实与稳定交付", 1),
                    lines("education", "受控测试学院 软件工程", 1),
                    lines("experience", "受控测试岗位 完成结构化交付", 1),
                    lines("projects", "结构化简历模板验证项目", 1),
                    lines("skills", "Java SQL TypeScript", 1),
                    lines("certificates", "受控测试资质", 1));
            case MEDIUM -> model(
                    lines("summary", "坚持事实可追溯并完成端到端质量检查", 2),
                    lines("education", "受控测试学院 软件工程 本科课程与实践", 2),
                    lines("experience", "负责需求拆解 实现 回归和发布记录", 4),
                    lines("projects", "结构化模板 内容版式分离与不可变导出", 4),
                    lines("skills", "Java Spring Boot Vue TypeScript SQL", 2),
                    lines("certificates", "受控测试资质与继续教育记录", 1));
            case LONG -> model(
                    lines("summary", "围绕真实输入完成设计 实现 验证和复盘", 3),
                    lines("education", "受控测试学院 软件工程 课程 项目与研究实践", 3),
                    lines("experience", "负责跨模块需求分析 风险控制 自动化回归和稳定交付", 8),
                    lines("projects", "结构化模板项目覆盖 preview export freeze and replay", 8),
                    lines("skills", "Java Spring Boot Vue TypeScript SQL PDF OOXML", 4),
                    lines("certificates", "受控测试资质 安全检查与质量体系记录", 3));
            case BOUNDARY -> model(
                    "",
                    lines("education", "受控测试学院 Software Engineering", 1),
                    marker("experience") + " 超长组织名称（中国）产品技术与质量保障联合实验中心有限公司\n"
                            + "Backend Engineer 负责可验证交付与 regression testing",
                    IntStream.rangeClosed(1, 6)
                            .mapToObj(index -> (index == 1 ? marker("projects") + " " : "")
                                    + "项目 " + index + " / Project " + index + "：结构化边界验证")
                            .reduce((left, right) -> left + "\n" + right).orElse(""),
                    marker("skills") + " Java / SQL / Vue / 中文 English mixed content",
                    "");
        };
    }

    public static ResumeDocumentModel paginatedDocument() {
        return model(
                lines("summary", "双页受控分页验证", 2),
                lines("education", "受控测试学院 软件工程", 2),
                lines("experience", "分页经历 mixed content", 35),
                lines("projects", "分页项目 immutable export", 35),
                lines("skills", "Java Spring Boot Vue TypeScript SQL", 3),
                lines("certificates", "受控测试资质", 2));
    }

    public static ResumeDocumentModel thumbnailDocument() {
        return model(
                "结构化内容示例 / Structured profile",
                "教育背景示例 / Education",
                "经历示例 / Experience\n职责、行动与可验证结果",
                "项目示例 / Project\n目标、方法与交付结果",
                "Java · SQL · Vue · Data",
                "资质与培训示例 / Credentials");
    }

    public static ResumeDocumentModel completeStructuredDocument() {
        return new ResumeDocumentModel(List.of(
                section("summary", "九模块统一内容模型 / Unified structured resume"),
                section("education", "示例大学 · 软件工程 · 本科\n2022.09 - 2026.06"),
                section("experience", "JobProof · 后端工程师\n负责结构化内容、渲染门禁与稳定交付"),
                section("projects", "智能简历工作台\n统一网页、PDF 与 DOCX 输出"),
                section("organizations", "开源技术社 · 负责人\n组织技术分享与协作实践"),
                section("skills", "Java · Spring Boot · Vue · TypeScript · SQL"),
                section("certificates", "软件设计师 · 2025.05"),
                section("honors", "优秀项目奖 · 示例大学 · 2025.06"),
                section("languages", "中文 · 母语\nEnglish · CET-6")));
    }

    public static String marker(String slotKey) {
        return MARKERS.get(slotKey);
    }

    public static List<String> populatedMarkers(
            ResumeLayoutDefinition definition,
            ResumeDocumentModel document) {
        Map<String, ResumeDocumentModel.Section> sections = new LinkedHashMap<>();
        document.sections().forEach(section -> sections.put(section.slotKey(), section));
        return definition.columns().stream()
                .flatMap(column -> column.slotKeys().stream())
                .filter(key -> {
                    ResumeDocumentModel.Section section = sections.get(key);
                    return section != null && section.plainText() != null && !section.plainText().isBlank();
                })
                .map(ResumeTemplateEvidenceFixtures::marker)
                .toList();
    }

    private static ResumeDocumentModel model(
            String summary,
            String education,
            String experience,
            String projects,
            String skills,
            String certificates) {
        return new ResumeDocumentModel(List.of(
                section("summary", summary),
                section("education", education),
                section("experience", experience),
                section("projects", projects),
                section("skills", skills),
                section("certificates", certificates)));
    }

    private static ResumeDocumentModel.Section section(String key, String text) {
        int items = text == null || text.isBlank() ? 0 : Math.max(1, text.split("\\R", -1).length);
        return new ResumeDocumentModel.Section(key, text, items);
    }

    private static String lines(String slotKey, String body, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> (index == 1 ? marker(slotKey) + " " : "")
                        + body + " " + String.format("%02d", index))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static Map<String, String> markers() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("summary", "SUM01");
        values.put("education", "EDU02");
        values.put("experience", "EXP03");
        values.put("projects", "PRO04");
        values.put("skills", "SKI05");
        values.put("certificates", "CER06");
        return Map.copyOf(values);
    }
}
