package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumePdfPreflightTest {

    @Test
    void allowsSlotEstimateOverflowWhenTheRealPdfStillFits() {
        ResumeLayoutDefinition definition = definition(1, 3_200, 80);
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                new ResumeDocumentModel.Section("honors", "校级一等奖，负责完整项目交付与成果答辩。".repeat(8), 1)));

        assertThat(ResumeOverflowEngine.evaluate(document, definition).valid()).isFalse();

        ResumeOverflowEngine.Report report = ResumePdfPreflight.evaluate(
                document, definition, "BLUE", ResumeLayoutProtocol.V3);

        assertThat(report.valid()).isTrue();
        assertThat(report.items()).isEmpty();
    }

    @Test
    void keepsSlotOverflowBlockedWhenTheRealPdfExceedsMaxPages() {
        ResumeLayoutDefinition definition = definition(1, 20_000, 80);
        String manyPhysicalLines = String.join("\n", java.util.Collections.nCopies(180, "荣誉说明"));
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                new ResumeDocumentModel.Section("honors", manyPhysicalLines, 1)));

        ResumeOverflowEngine.Report report = ResumePdfPreflight.evaluate(
                document, definition, "BLUE", ResumeLayoutProtocol.V3);

        assertThat(report.valid()).isFalse();
        assertThat(report.items()).anyMatch(item -> "honors".equals(item.slotKey()));
    }

    @Test
    void rejectsDocumentCapacityOverflowWithoutRelaxingTheHardLimit() {
        ResumeLayoutDefinition definition = definition(2, 600, 5_000);
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                new ResumeDocumentModel.Section("honors", "超出整份文档容量".repeat(200), 1)));

        ResumeOverflowEngine.Report report = ResumePdfPreflight.evaluate(
                document, definition, "BLUE", ResumeLayoutProtocol.V3);

        assertThat(report.valid()).isFalse();
        assertThat(report.items()).anyMatch(item -> "DOCUMENT".equals(item.slotKey()));
    }

    private static ResumeLayoutDefinition definition(int maxPages, int pageCapacity, int slotCapacity) {
        return ResumeLayoutProtocol.validate(ResumeLayoutProtocol.V3, new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(maxPages, pageCapacity, 44, 42, 44),
                List.of(new ResumeLayoutDefinition.Column("main", 100, List.of("honors"), "PLAIN")),
                List.of(new ResumeLayoutDefinition.Slot(
                        "honors", 10, slotCapacity, true, true, "荣誉奖项", "BAR")),
                Map.of(
                        "accent.BLUE", "#0F766E",
                        "surface.BLUE", "#ECFDF3",
                        "body", "#1F2937",
                        "muted", "#667085"),
                new ResumeLayoutDefinition.Visual("BAND", "BAR", "校园与实践简历", false, "COMPACT")));
    }
}
