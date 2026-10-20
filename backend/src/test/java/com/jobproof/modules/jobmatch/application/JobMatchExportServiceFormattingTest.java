package com.jobproof.modules.jobmatch.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobMatchExportServiceFormattingTest {

    @Test
    void rendersStructuredSummaryWithoutJavaMapSyntax() {
        assertThat(JobMatchExportService.summaryText(Map.of(
                "matchId", "synthetic-match",
                "assessment", "简历已有后端基础，但没有已授权 evidence，ruleResult 仍为 NOT_FOUND。")))
                .isEqualTo("简历已有后端基础，但没有已授权证据，规则结果仍为暂未发现证据。");
    }

    @Test
    void usesRequirementTextWhenGapSchemaHasNoTitle() {
        assertThat(JobMatchExportService.sectionTitle(
                Map.of("requirementId", "requirement-1", "type", "CAPABILITY_GAP"),
                "title",
                Map.of("requirement-1", "使用消息队列实现幂等、重试和失败补偿。")))
                .isEqualTo("使用消息队列实现幂等、重试和失败补偿。");
    }

    @Test
    void theExportCarriesEveryPartOfAV3Report() {
        Map<String, Object> document = Map.ofEntries(
                Map.entry("score", 72), Map.entry("confidence", "MEDIUM"), Map.entry("hardGatePassed", true),
                Map.entry("generatedAt", "2026-10-20T03:15:00Z"), Map.entry("preview", false),
                Map.entry("scoring", Map.of("method", "AI_VERDICT", "citedCount", 2, "requirementCount", 3)),
                Map.entry("dimensions", Map.of("coreSkills", 80, "experience", 60, "evidence", 50)),
                Map.entry("recommendation", Map.of("code", "CONDITIONAL", "capped", true, "rationale", "主要要求基本符合，还有 1 条要求需要补充证据。")),
                Map.entry("hardGates", List.of(Map.of("requirementId", "r1", "text", "本科及以上学历", "status", "PASSED"))),
                Map.entry("requirements", List.of(
                        Map.of("requirementId", "r1", "text", "本科及以上学历", "priority", "MUST", "hardGate", true,
                                "verdict", "MET", "value", 100, "resumeRefs", List.of("resume:education/0"), "evidenceIds", List.of(), "userConfirmed", false),
                        Map.of("requirementId", "r2", "text", "熟悉 Redis", "priority", "IMPORTANT", "hardGate", false,
                                "verdict", "PARTIAL", "value", 60, "resumeRefs", List.of(), "evidenceIds", List.of(), "userConfirmed", true,
                                "explanation", "只在课程项目里用过"),
                        Map.of("requirementId", "r3", "text", "有 Kubernetes 经验", "priority", "BONUS", "hardGate", false,
                                "verdict", "NOT_FOUND", "value", 0, "resumeRefs", List.of(), "evidenceIds", List.of(), "userConfirmed", false))),
                Map.entry("strengths", List.of(Map.of("requirementId", "r1", "title", "本科学历", "explanation", "教育经历写明了学历"))),
                Map.entry("gaps", List.of(Map.of("requirementId", "r3", "type", "CAPABILITY_GAP", "impact", "暂未在简历中找到证据"))),
                Map.entry("ai", Map.of(
                        "summary", Map.of("headline", "基础扎实，缓存经验偏少。"),
                        "resumeSuggestions", List.of(Map.of("targetPath", "/projects/0/description", "proposedValue", "用 Redis 缓存热点商品，接口耗时下降。", "reason", "补上与岗位相关的缓存经历")),
                        "interviewTopics", List.of("缓存一致性", Map.of("topic", "MySQL 索引设计")))));
        JobMatchService.MatchView match = new JobMatchService.MatchView("m1", "COMPLETED", 3, 100, null, null, "j1", "jv1",
                "Java 后端工程师", "合成科技", null, null, List.of(), List.of(), null, null, "NONE", null, "report-1", null, 1,
                List.of(), Instant.EPOCH, Instant.EPOCH);
        JobMatchService.ReportView report = new JobMatchService.ReportView("report-1", "m1", document, List.of(),
                List.of(new JobMatchService.ImprovementView("t1", "GENERAL", "BEFORE_INTERVIEW", "练习缓存一致性方案",
                        "实现两种缓存更新策略", "对比说明", "说清适用场景", 6, "P2", "DONE", 2)),
                List.of(), "COMPLETED", Instant.EPOCH);

        List<JobMatchExportService.Section> sections = JobMatchExportService.sections(match, report);
        assertThat(sections).extracting(JobMatchExportService.Section::heading).containsExactly(
                "概览", "投递建议", "维度得分", "硬性要求", "逐条判定", "匹配优势", "能力与材料缺口",
                "简历修改建议", "面试准备主题", "提升计划", "说明");
        String text = sections.stream().flatMap(section -> section.lines().stream()).reduce("", (a, b) -> a + b + "\n");
        assertThat(text)
                .contains("综合匹配度：72 分", "3 条要求中 2 条有依据", "生成时间：2026-10-20 03:15")
                .contains("建议：准备后投递", "已按分数调整")
                .contains("硬性要求：1 / 1 条已满足", "核心技能：80 分", "学历与限制：无相关要求")
                .contains("1. 本科及以上学历 — 已满足")
                .contains("[必须 · 硬性] 本科及以上学历", "判定：符合（100 分） · 依据：简历")
                .contains("判定：部分符合（60 分） · 依据：你的确认", "说明：只在课程项目里用过")
                .contains("判定：暂未发现证据（0 分）")
                .contains("1. 项目经历", "建议写成：用 Redis 缓存热点商品")
                .contains("1. 缓存一致性", "2. MySQL 索引设计")
                .contains("已完成 1 / 1 项", "练习缓存一致性方案（已完成 · 6 小时）")
                .doesNotContain("MUST", "NOT_FOUND", "CONDITIONAL", "AI_VERDICT");
    }
}
