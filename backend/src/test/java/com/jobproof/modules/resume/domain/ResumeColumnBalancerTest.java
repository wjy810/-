package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeColumnBalancerTest {

    @Test
    void rebalancesAOnePageDocumentWithoutDroppingOrDuplicatingSlots() {
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                section("summary", 'S', 200),
                section("experience", 'E', 400),
                section("skills", 'K', 300),
                section("education", 'D', 300),
                section("certificates", 'C', 250),
                section("organizations", 'O', 250)));
        ResumeLayoutDefinition definition = definition();

        ResumeLayoutDefinition balanced = ResumeColumnBalancer.balance(document, definition);
        List<String> allKeys = balanced.columns().stream()
                .flatMap(column -> column.slotKeys().stream())
                .toList();

        assertThat(allKeys).containsExactlyInAnyOrder(
                "summary", "experience", "skills", "education", "certificates", "organizations");
        assertThat(new HashSet<>(allKeys)).hasSameSizeAs(allKeys);
        assertThat(balanced.columns().get(0).slotKeys())
                .containsAnyOf("certificates", "organizations");
        assertThat(normalizedLoads(balanced, document)).allMatch(load -> load <= 1.0);
    }

    @Test
    void keepsStructuredStudentContentOnOnePageByUsingBothColumns() {
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                lines("summary", 140, 0),
                lines("education", 188, 4),
                lines("skills", 427, 23),
                lines("certificates", 243, 4),
                lines("projects", 215, 4),
                lines("experience", 193, 4),
                lines("organizations", 174, 4),
                lines("honors", 499, 10),
                lines("languages", 153, 3)));
        ResumeLayoutDefinition definition = campusDefinition();

        ResumeLayoutDefinition balanced = ResumeColumnBalancer.balance(document, definition);

        assertThat(normalizedLoads(balanced, document)).allMatch(load -> load <= 1.0);
        assertThat(balanced.columns().get(0).slotKeys()).contains("skills").doesNotContain("certificates");
        assertThat(balanced.columns().get(1).slotKeys()).contains("certificates");
    }

    @Test
    void rebalancesMultiPageContentSoAShortSidebarTailDoesNotOccupyPageTwoAlone() {
        ResumeDocumentModel document = new ResumeDocumentModel(List.of(
                section("experience", 'E', 600),
                section("projects", 'P', 600),
                section("education", 'D', 400),
                section("organizations", 'O', 300),
                section("honors", 'H', 300),
                section("summary", 'S', 500),
                section("skills", 'K', 800),
                section("certificates", 'C', 500),
                section("languages", 'L', 500)));
        ResumeLayoutDefinition definition = financeDefinition();
        double originalMaximumLoad = normalizedLoads(definition, document).stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElseThrow();

        ResumeLayoutDefinition balanced = ResumeColumnBalancer.balance(document, definition);
        double balancedMaximumLoad = normalizedLoads(balanced, document).stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElseThrow();

        assertThat(balancedMaximumLoad).isLessThan(originalMaximumLoad);
        assertThat(balanced.columns().get(0).slotKeys()).contains("languages");
        assertThat(balanced.columns().get(1).slotKeys()).doesNotContain("languages");
    }

    private static ResumeDocumentModel.Section section(String key, char value, int length) {
        return new ResumeDocumentModel.Section(key, String.valueOf(value).repeat(length), 1);
    }

    private static ResumeDocumentModel.Section lines(String key, int length, int lineBreaks) {
        StringBuilder value = new StringBuilder("内".repeat(Math.max(0, length - lineBreaks)));
        for (int index = 0; index < lineBreaks; index++) {
            value.insert(Math.min(value.length(), (index + 1) * value.length() / (lineBreaks + 1)), '\n');
        }
        return new ResumeDocumentModel.Section(key, value.toString(), 1);
    }

    private static ResumeLayoutDefinition campusDefinition() {
        List<String> keys = List.of(
                "summary", "education", "skills", "certificates", "projects",
                "experience", "organizations", "honors", "languages");
        List<ResumeLayoutDefinition.Slot> slots = new ArrayList<>();
        for (int index = 0; index < keys.size(); index++) {
            slots.add(new ResumeLayoutDefinition.Slot(
                    keys.get(index), (index + 1) * 10, 1_200, true, true,
                    keys.get(index), "BAR"));
        }
        return new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(2, 3_300),
                List.of(
                        new ResumeLayoutDefinition.Column(
                                "profile", 36, List.of("summary", "education", "skills", "certificates"),
                                "NEUTRAL"),
                        new ResumeLayoutDefinition.Column(
                                "growth", 64,
                                List.of("projects", "experience", "organizations", "honors", "languages"),
                                "PLAIN")),
                slots,
                Map.of("accent.BLUE", "#0F766E"));
    }

    private static ResumeLayoutDefinition definition() {
        List<String> keys = List.of(
                "summary", "experience", "skills", "education", "certificates", "organizations");
        List<ResumeLayoutDefinition.Slot> slots = new ArrayList<>();
        for (int index = 0; index < keys.size(); index++) {
            slots.add(new ResumeLayoutDefinition.Slot(
                    keys.get(index), (index + 1) * 10, 1_000, true, true,
                    keys.get(index), "RULE"));
        }
        return new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(1, 3_000),
                List.of(
                        new ResumeLayoutDefinition.Column(
                                "main", 70, List.of("summary", "experience"), "PLAIN"),
                        new ResumeLayoutDefinition.Column(
                                "facts", 30,
                                List.of("skills", "education", "certificates", "organizations"),
                                "ACCENT_SOFT")),
                slots,
                Map.of("accent.BLUE", "#175CD3"));
    }

    private static ResumeLayoutDefinition financeDefinition() {
        List<String> keys = List.of(
                "experience", "projects", "education", "organizations", "honors",
                "summary", "skills", "certificates", "languages");
        List<ResumeLayoutDefinition.Slot> slots = new ArrayList<>();
        for (int index = 0; index < keys.size(); index++) {
            slots.add(new ResumeLayoutDefinition.Slot(
                    keys.get(index), (index + 1) * 10, 1_200, true, true,
                    keys.get(index), "BAR"));
        }
        return new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(2, 3_400),
                List.of(
                        new ResumeLayoutDefinition.Column(
                                "main", 68,
                                List.of("experience", "projects", "education", "organizations", "honors"),
                                "PLAIN"),
                        new ResumeLayoutDefinition.Column(
                                "facts", 32, List.of("summary", "skills", "certificates", "languages"),
                                "NEUTRAL")),
                slots,
                Map.of("accent.FINANCE_MINIMAL", "#14532D"));
    }

    private static List<Double> normalizedLoads(
            ResumeLayoutDefinition definition,
            ResumeDocumentModel document) {
        Map<String, Integer> units = document.sections().stream().collect(java.util.stream.Collectors.toMap(
                ResumeDocumentModel.Section::slotKey,
                section -> ResumeColumnBalancer.estimatedUnits(section.plainText())));
        return definition.columns().stream().map(column -> {
            int used = column.slotKeys().stream().mapToInt(key -> units.getOrDefault(key, 0)).sum();
            double capacity = definition.page().capacityUnits() * column.widthPercent() / 100.0;
            return used / capacity;
        }).toList();
    }
}
