package com.jobproof.modules.resume.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ResumeOverflowEngine {
    private ResumeOverflowEngine() {}

    public static Report evaluate(ResumeDocumentModel document, ResumeLayoutDefinition layout) {
        Map<String, ResumeDocumentModel.Section> sections = document.sections().stream()
                .collect(Collectors.toMap(ResumeDocumentModel.Section::slotKey, value -> value, (left, right) -> left));
        List<Item> items = new ArrayList<>();
        int consumed = document.header().deterministicUnits();
        for (ResumeLayoutDefinition.Slot slot : layout.slots().stream()
                .sorted(Comparator.comparingInt(ResumeLayoutDefinition.Slot::order)).toList()) {
            ResumeDocumentModel.Section section = sections.get(slot.key());
            int used = section == null ? 0 : section.deterministicUnits();
            consumed += used;
            if (used > slot.capacityUnits()) {
                items.add(new Item(Math.max(1, consumed / Math.max(1, layout.page().capacityUnits()) + 1),
                        slot.key(), used - slot.capacityUnits(), "SHORTEN_CONTENT_OR_CHOOSE_LARGER_TEMPLATE"));
            }
        }
        int pageOverflow = consumed - layout.page().capacityUnits() * layout.page().maxPages();
        if (pageOverflow > 0) {
            items.add(new Item(layout.page().maxPages() + 1, "DOCUMENT", pageOverflow,
                    "CHOOSE_LARGER_TEMPLATE"));
        }
        return new Report(items.isEmpty(), consumed, List.copyOf(items));
    }

    public record Report(boolean valid, int consumedUnits, List<Item> items) {}
    public record Item(int page, String slotKey, int excessUnits, String suggestedAction) {}
}
