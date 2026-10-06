package com.jobproof.modules.resume.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ResumeLayoutProtocol {
    public static final String V1 = "resume-layout-v1";
    public static final String V2 = "resume-layout-v2";
    public static final String V3 = "resume-layout-v3";

    private static final Set<String> SLOT_KEYS = Set.of(
            "summary", "education", "experience", "projects", "organizations",
            "skills", "certificates", "honors", "languages");
    private static final Set<String> COLUMN_TONES = Set.of("PLAIN", "NEUTRAL", "ACCENT_SOFT");
    private static final Set<String> HEADER_STYLES = Set.of("MINIMAL", "BAND", "SPLIT", "COMPACT");
    private static final Set<String> SECTION_STYLES = Set.of("RULE", "BAR", "SIDELINE", "PLAIN", "TABLE");
    private static final Set<String> DENSITIES = Set.of("COMPACT", "STANDARD", "AIRY");

    private ResumeLayoutProtocol() {
    }

    public static ResumeLayoutDefinition validate(String protocol, ResumeLayoutDefinition definition) {
        if (!V1.equals(protocol) && !V2.equals(protocol) && !V3.equals(protocol)) {
            throw invalid("不支持的渲染协议");
        }
        if (definition == null || definition.page() == null) {
            throw invalid("缺少页面定义");
        }
        ResumeLayoutDefinition.Page page = definition.page();
        if (page.maxPages() < 1 || page.maxPages() > 4 || page.capacityUnits() < 1) {
            throw invalid("页面数量或容量无效");
        }
        if (page.effectiveMarginXPt() < 24 || page.effectiveMarginXPt() > 96
                || page.effectiveMarginTopPt() < 24 || page.effectiveMarginTopPt() > 120
                || page.effectiveMarginBottomPt() < 24 || page.effectiveMarginBottomPt() > 96) {
            throw invalid("页面边距超出受控范围");
        }
        if (definition.columns() == null || definition.columns().isEmpty() || definition.columns().size() > 2) {
            throw invalid("版式必须包含一至两列");
        }
        if (definition.slots() == null || definition.slots().isEmpty()) {
            throw invalid("版式必须包含至少一个槽位");
        }

        Set<String> slotKeys = new HashSet<>();
        Set<Integer> slotOrders = new HashSet<>();
        for (ResumeLayoutDefinition.Slot slot : definition.slots()) {
            if (slot == null || slot.key() == null || !SLOT_KEYS.contains(slot.key())
                    || slot.capacityUnits() < 1 || !slotKeys.add(slot.key()) || !slotOrders.add(slot.order())) {
                throw invalid("槽位标识、顺序或容量无效");
            }
            if (slot.headingStyle() != null && !slot.headingStyle().isBlank()
                    && !SECTION_STYLES.contains(normalized(slot.headingStyle()))) {
                throw invalid("槽位标题样式无效");
            }
        }

        int width = 0;
        Set<String> columnIds = new HashSet<>();
        Set<String> assignedSlots = new HashSet<>();
        for (ResumeLayoutDefinition.Column column : definition.columns()) {
            if (column == null || column.id() == null || column.id().isBlank()
                    || !columnIds.add(column.id()) || column.widthPercent() < 20 || column.widthPercent() > 100
                    || !COLUMN_TONES.contains(normalized(column.effectiveTone()))) {
                throw invalid("列标识、宽度或色调无效");
            }
            width += column.widthPercent();
            List<String> keys = column.slotKeys();
            if (keys == null || keys.isEmpty()) throw invalid("每一列都必须包含槽位");
            for (String key : keys) {
                if (!slotKeys.contains(key) || !assignedSlots.add(key)) {
                    throw invalid("列必须且只能引用一次已定义槽位");
                }
            }
        }
        if (width != 100 || !assignedSlots.equals(slotKeys)) {
            throw invalid("列宽总和必须为 100，且必须覆盖全部槽位");
        }

        if (V2.equals(protocol) || V3.equals(protocol)) {
            ResumeLayoutDefinition.Visual visual = definition.visual();
            if (visual == null
                    || !HEADER_STYLES.contains(normalized(visual.effectiveHeaderStyle()))
                    || !SECTION_STYLES.contains(normalized(visual.effectiveSectionStyle()))
                    || !DENSITIES.contains(normalized(visual.effectiveDensity()))) {
                throw invalid("v2 必须包含受支持的视觉定义");
            }
        }
        if (definition.tokens() != null) {
            definition.tokens().forEach((key, value) -> {
                if ((key.startsWith("accent.") || key.startsWith("surface."))
                        && (value == null || !value.matches("#[0-9A-Fa-f]{6}"))) {
                    throw invalid("颜色令牌必须使用六位十六进制颜色");
                }
            });
        }
        return V3.equals(protocol) ? normalizeV3(definition) : definition;
    }

    private static ResumeLayoutDefinition normalizeV3(ResumeLayoutDefinition definition) {
        List<ResumeLayoutDefinition.Slot> slots = new java.util.ArrayList<>(definition.slots());
        List<ResumeLayoutDefinition.Column> columns = new java.util.ArrayList<>(definition.columns());
        Set<String> existing = new HashSet<>();
        slots.forEach(slot -> existing.add(slot.key()));
        java.util.Map<String, String> labels = java.util.Map.of(
                "organizations", "社团与活动", "honors", "荣誉奖项", "languages", "语言能力");
        int nextOrder = slots.stream().mapToInt(ResumeLayoutDefinition.Slot::order).max().orElse(0) + 10;
        List<String> missing = List.of("organizations", "honors", "languages").stream()
                .filter(key -> !existing.contains(key)).toList();
        for (String key : missing) {
            slots.add(new ResumeLayoutDefinition.Slot(key, nextOrder, 420, true, true,
                    labels.get(key), definition.visual().effectiveSectionStyle()));
            nextOrder += 10;
        }
        if (!missing.isEmpty()) {
            java.util.Map<String, Integer> capacities = slots.stream().collect(
                    java.util.stream.Collectors.toMap(
                            ResumeLayoutDefinition.Slot::key,
                            ResumeLayoutDefinition.Slot::capacityUnits));
            for (String key : missing) {
                int target = 0;
                double lowestLoad = Double.MAX_VALUE;
                for (int index = 0; index < columns.size(); index++) {
                    ResumeLayoutDefinition.Column candidate = columns.get(index);
                    int declaredUnits = candidate.slotKeys().stream()
                            .mapToInt(slotKey -> capacities.getOrDefault(slotKey, 0))
                            .sum();
                    double normalizedLoad = declaredUnits / (double) Math.max(1, candidate.widthPercent());
                    if (normalizedLoad < lowestLoad) {
                        target = index;
                        lowestLoad = normalizedLoad;
                    }
                }
                ResumeLayoutDefinition.Column column = columns.get(target);
                List<String> keys = new java.util.ArrayList<>(column.slotKeys());
                keys.add(key);
                columns.set(target, new ResumeLayoutDefinition.Column(
                        column.id(), column.widthPercent(), List.copyOf(keys), column.tone()));
            }
        }
        ResumeLayoutDefinition.DesignCapabilities capabilities = definition.designCapabilities();
        if (capabilities == null) {
            List<String> colors = definition.tokens() == null ? List.of() : definition.tokens().entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("accent."))
                    .map(java.util.Map.Entry::getValue).distinct().toList();
            capabilities = new ResumeLayoutDefinition.DesignCapabilities(
                    List.of("MODERN_SANS", "CLASSIC_SERIF"),
                    List.of("SMALL", "STANDARD", "LARGE"),
                    List.of("COMPACT", "STANDARD", "AIRY"),
                    List.of("NARROW", "STANDARD", "WIDE"),
                    colors,
                    List.of("YYYY_DOT_MM", "YYYY_CN_MM"),
                    List.of("MINIMAL", "BAND", "SPLIT", "COMPACT"),
                    List.of("RULE", "BAR", "SIDELINE", "PLAIN", "TABLE"),
                    List.of("AUTO", "SHOW", "HIDE"),
                    List.of("COMPACT", "STANDARD", "AIRY"),
                    slots.stream().sorted(java.util.Comparator.comparingInt(ResumeLayoutDefinition.Slot::order))
                            .map(ResumeLayoutDefinition.Slot::key).toList());
        }
        return new ResumeLayoutDefinition(definition.page(), List.copyOf(columns), List.copyOf(slots),
                definition.tokens(), definition.visual(), capabilities);
    }

    private static String normalized(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Invalid resume layout definition: " + detail);
    }
}
