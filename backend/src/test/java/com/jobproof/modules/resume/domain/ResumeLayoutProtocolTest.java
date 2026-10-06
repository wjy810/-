package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeLayoutProtocolTest {

    @Test
    void acceptsACompleteTwoColumnV2Definition() {
        ResumeLayoutDefinition definition = twoColumnDefinition();

        assertThat(ResumeLayoutProtocol.validate(ResumeLayoutProtocol.V2, definition)).isSameAs(definition);
    }

    @Test
    void rejectsUnassignedDuplicateAndUnsupportedLayoutInputs() {
        ResumeLayoutDefinition duplicateAssignment = new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(1, 3200, 48, 48, 46),
                List.of(
                        new ResumeLayoutDefinition.Column("left", 35, List.of("skills"), "ACCENT_SOFT"),
                        new ResumeLayoutDefinition.Column("main", 65, List.of("skills", "experience"), "PLAIN")),
                List.of(
                        new ResumeLayoutDefinition.Slot("skills", 10, 500, true, true, "专业技能", "BAR"),
                        new ResumeLayoutDefinition.Slot("experience", 20, 1200, true, true, "工作经历", "RULE")),
                Map.of("accent.BLUE", "#175CD3"),
                new ResumeLayoutDefinition.Visual("SPLIT", "RULE", "技术简历", true, "STANDARD"));

        assertThatThrownBy(() -> ResumeLayoutProtocol.validate(ResumeLayoutProtocol.V2, duplicateAssignment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("列必须且只能引用一次");
        assertThatThrownBy(() -> ResumeLayoutProtocol.validate("resume-layout-v99", twoColumnDefinition()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的渲染协议");
    }

    @Test
    void v3DistributesNewCanonicalSectionsAcrossMultiColumnLayouts() {
        ResumeLayoutDefinition normalized = ResumeLayoutProtocol.validate(
                ResumeLayoutProtocol.V3, twoColumnDefinition());

        List<String> left = normalized.columns().get(0).slotKeys();
        List<String> right = normalized.columns().get(1).slotKeys();
        assertThat(left).containsAnyOf("organizations", "honors", "languages");
        assertThat(right).containsAnyOf("organizations", "honors", "languages");
        assertThat(left).doesNotContainSequence("organizations", "honors", "languages");
        assertThat(right).doesNotContainSequence("organizations", "honors", "languages");
    }

    static ResumeLayoutDefinition twoColumnDefinition() {
        return new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(1, 3200, 48, 48, 46),
                List.of(
                        new ResumeLayoutDefinition.Column(
                                "sidebar", 34, List.of("summary", "skills", "certificates"), "ACCENT_SOFT"),
                        new ResumeLayoutDefinition.Column(
                                "main", 66, List.of("experience", "projects", "education"), "PLAIN")),
                List.of(
                        new ResumeLayoutDefinition.Slot("summary", 10, 420, false, true, "个人简介", "BAR"),
                        new ResumeLayoutDefinition.Slot("skills", 20, 520, true, true, "专业技能", "BAR"),
                        new ResumeLayoutDefinition.Slot("certificates", 30, 300, true, true, "证书", "BAR"),
                        new ResumeLayoutDefinition.Slot("experience", 40, 900, true, true, "工作经历", "RULE"),
                        new ResumeLayoutDefinition.Slot("projects", 50, 800, true, true, "项目经历", "RULE"),
                        new ResumeLayoutDefinition.Slot("education", 60, 420, true, true, "教育经历", "RULE")),
                Map.of(
                        "accent.BLUE", "#175CD3",
                        "surface.BLUE", "#EEF4FF",
                        "body", "#1F2937",
                        "muted", "#667085"),
                new ResumeLayoutDefinition.Visual("SPLIT", "RULE", "技术项目简历", true, "COMPACT"));
    }
}
