package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobproof.shared.error.AppException;
import org.junit.jupiter.api.Test;

class ResumeImportHeuristicTest {

    @Test
    void groupsChineseAndEnglishSectionsWithoutTurningTheWholeResumeIntoASummary() {
        ResumeImportHeuristic.ParseResult result = ResumeImportHeuristic.parse("""
                SUMMARY: Java backend developer
                Education
                Example University, Software Engineering
                项目经历：订单服务重构
                使用 Java 和 PostgreSQL
                Skills
                Java, SQL
                """);

        assertThat(result.drafts()).extracting(ResumeImportHeuristic.CandidateDraft::fieldKey)
                .containsExactly(ResumeFieldKey.SELF_INTRO, ResumeFieldKey.EDUCATION,
                        ResumeFieldKey.PROJECTS, ResumeFieldKey.SKILLS);
        assertThat(result.drafts().get(2).proposedValue()).contains("订单服务重构", "PostgreSQL");
    }

    @Test
    void removesSensitiveAndContactLinesBeforeCreatingCandidates() {
        ResumeImportHeuristic.ParseResult result = ResumeImportHeuristic.parse("""
                性别：女
                email@example.com
                教育经历
                示例大学 本科
                """);

        assertThat(result.ignoredSensitiveLines()).isEqualTo(1);
        assertThat(result.ambiguousLines()).isEqualTo(1);
        assertThat(result.drafts()).singleElement()
                .satisfies(candidate -> assertThat(candidate.fieldKey()).isEqualTo(ResumeFieldKey.EDUCATION));
    }

    @Test
    void rejectsOversizedText() {
        assertThatThrownBy(() -> ResumeImportHeuristic.parse("x".repeat(ResumeImportHeuristic.MAX_TEXT_LENGTH + 1)))
                .isInstanceOf(AppException.class);
    }
}
