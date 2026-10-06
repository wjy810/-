package com.jobproof.modules.jobmatch.application;

import static org.assertj.core.api.Assertions.assertThat;

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
}
