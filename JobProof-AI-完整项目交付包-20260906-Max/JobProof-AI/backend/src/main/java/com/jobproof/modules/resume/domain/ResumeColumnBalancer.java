package com.jobproof.modules.resume.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ResumeColumnBalancer {
    private ResumeColumnBalancer() {
    }

    static ResumeLayoutDefinition balance(ResumeDocumentModel document, ResumeLayoutDefinition definition) {
        if (definition.columns().size() < 2) return definition;
        Map<String, Integer> units = new LinkedHashMap<>();
        for (ResumeDocumentModel.Section section : document.sections()) {
            int value = estimatedUnits(section.plainText());
            if (value > 0) units.putIfAbsent(section.slotKey(), value);
        }
        int total = units.values().stream().mapToInt(Integer::intValue).sum();
        int pageCapacity = Math.max(1, definition.page().capacityUnits());
        int maximumPages = Math.max(1, definition.page().maxPages());
        if ((long) total > (long) pageCapacity * maximumPages) return definition;

        List<List<String>> assignments = definition.columns().stream()
                .<List<String>>map(column -> new ArrayList<>(column.slotKeys()))
                .toList();
        int maxMoves = units.size() * Math.max(1, assignments.size() - 1);
        for (int move = 0; move < maxMoves; move++) {
            List<Double> loads = loads(assignments, definition.columns(), units, pageCapacity);
            double currentMax = loads.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            if (currentMax <= 1.0) break;
            int source = loads.indexOf(currentMax);
            Move best = null;
            for (int priority = 0; priority <= 4 && best == null; priority++) {
                for (int sectionIndex = 0; sectionIndex < assignments.get(source).size(); sectionIndex++) {
                    String key = assignments.get(source).get(sectionIndex);
                    int sectionUnits = units.getOrDefault(key, 0);
                    if (sectionUnits == 0 || movementPriority(key) != priority) continue;
                    for (int target = 0; target < assignments.size(); target++) {
                        if (target == source) continue;
                        List<Double> next = new ArrayList<>(loads);
                        next.set(source, next.get(source) - sectionUnits / capacity(
                                definition.columns().get(source), pageCapacity));
                        next.set(target, next.get(target) + sectionUnits / capacity(
                                definition.columns().get(target), pageCapacity));
                        double nextMax = next.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                        if (nextMax >= currentMax - 0.001) continue;
                        if (best == null || nextMax < best.maxLoad()) {
                            best = new Move(sectionIndex, target, nextMax);
                        }
                    }
                }
            }
            if (best == null) break;
            String key = assignments.get(source).remove(best.sectionIndex());
            assignments.get(best.targetIndex()).add(key);
        }

        if (loads(assignments, definition.columns(), units, pageCapacity).stream()
                .mapToDouble(Double::doubleValue).max().orElse(0) > maximumPages) {
            return definition;
        }
        Map<String, Integer> order = new HashMap<>();
        for (ResumeLayoutDefinition.Slot slot : definition.slots()) order.put(slot.key(), slot.order());
        List<ResumeLayoutDefinition.Column> columns = new ArrayList<>();
        for (int index = 0; index < definition.columns().size(); index++) {
            ResumeLayoutDefinition.Column original = definition.columns().get(index);
            List<String> keys = assignments.get(index).stream()
                    .sorted(Comparator.comparingInt(key -> order.getOrDefault(key, Integer.MAX_VALUE)))
                    .toList();
            columns.add(new ResumeLayoutDefinition.Column(
                    original.id(), original.widthPercent(), keys, original.tone()));
        }
        return new ResumeLayoutDefinition(definition.page(), List.copyOf(columns), definition.slots(),
                definition.tokens(), definition.visual(), definition.designCapabilities());
    }

    static int estimatedUnits(String value) {
        if (value == null || value.isBlank()) return 0;
        int codePoints = value.codePointCount(0, value.length());
        int lineBreaks = (int) value.chars().filter(character -> character == '\n').count();
        // Explicit line breaks already separate concise resume bullets. Counting each
        // one as a full 32-character line made structured sections look almost twice
        // as tall as their rendered form and pushed otherwise valid content to page 2.
        return codePoints + lineBreaks * 12 + 36;
    }

    private static List<Double> loads(
            List<List<String>> assignments,
            List<ResumeLayoutDefinition.Column> columns,
            Map<String, Integer> units,
            int pageCapacity) {
        List<Double> result = new ArrayList<>();
        for (int index = 0; index < assignments.size(); index++) {
            int used = assignments.get(index).stream().mapToInt(key -> units.getOrDefault(key, 0)).sum();
            result.add(used / capacity(columns.get(index), pageCapacity));
        }
        return result;
    }

    private static double capacity(ResumeLayoutDefinition.Column column, int pageCapacity) {
        return Math.max(1.0, pageCapacity * column.widthPercent() / 100.0);
    }

    private static int movementPriority(String key) {
        if (List.of("organizations", "honors", "languages").contains(key)) return 0;
        if ("certificates".equals(key)) return 1;
        if (List.of("education", "skills").contains(key)) return 2;
        if (List.of("summary", "projects").contains(key)) return 3;
        return 4;
    }

    private record Move(int sectionIndex, int targetIndex, double maxLoad) {
    }
}
