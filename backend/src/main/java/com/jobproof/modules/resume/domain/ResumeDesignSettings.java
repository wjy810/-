package com.jobproof.modules.resume.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Controlled per-template design overrides. Content never belongs in this object. */
public record ResumeDesignSettings(
        String schemaVersion,
        String fontPreset,
        String fontScale,
        String lineHeight,
        String pageMargin,
        String accentColor,
        String dateFormat,
        String headerLayout,
        String headingStyle,
        String photoMode,
        String density,
        List<String> hiddenSections,
        List<String> sectionOrder) {

    public static final String SCHEMA = "resume-design-v1";
    public static final Set<String> SECTION_KEYS = Set.of(
            "summary", "education", "experience", "projects", "organizations",
            "skills", "certificates", "honors", "languages");
    private static final Set<String> FONT_PRESETS = Set.of("MODERN_SANS", "CLASSIC_SERIF");
    private static final Set<String> FONT_SCALES = Set.of("SMALL", "STANDARD", "LARGE");
    private static final Set<String> LINE_HEIGHTS = Set.of("COMPACT", "STANDARD", "AIRY");
    private static final Set<String> PAGE_MARGINS = Set.of("NARROW", "STANDARD", "WIDE");
    private static final Set<String> DATE_FORMATS = Set.of("YYYY_DOT_MM", "YYYY_CN_MM");
    private static final Set<String> HEADER_LAYOUTS = Set.of("MINIMAL", "BAND", "SPLIT", "COMPACT");
    private static final Set<String> HEADING_STYLES = Set.of("RULE", "BAR", "SIDELINE", "PLAIN", "TABLE");
    private static final Set<String> PHOTO_MODES = Set.of("AUTO", "SHOW", "HIDE");
    private static final Set<String> DENSITIES = Set.of("COMPACT", "STANDARD", "AIRY");

    public ResumeDesignSettings {
        hiddenSections = hiddenSections == null ? List.of() : List.copyOf(hiddenSections);
        sectionOrder = sectionOrder == null ? List.of() : List.copyOf(sectionOrder);
    }

    public static ResumeDesignSettings defaults(
            ResumeLayoutDefinition definition, String variantCode, String photoPolicy) {
        return defaults(definition, variantCode, photoPolicy, null);
    }

    public static ResumeDesignSettings defaults(
            ResumeLayoutDefinition definition, String variantCode, String photoPolicy, String templateId) {
        String variant = normalized(blankTo(variantCode, "DEFAULT"));
        Map<String, String> tokens = definition.tokens() == null ? Map.of() : definition.tokens();
        String accent = tokens.getOrDefault("accent." + variant, "#1F2937");
        ResumeLayoutDefinition.Visual visual = definition.visual();
        String header = visual == null ? "MINIMAL" : visual.effectiveHeaderStyle();
        String heading = visual == null ? "RULE" : visual.effectiveSectionStyle();
        String density = visual == null ? "STANDARD" : visual.effectiveDensity();
        List<String> order = definition.slots().stream()
                .sorted(java.util.Comparator.comparingInt(ResumeLayoutDefinition.Slot::order))
                .map(ResumeLayoutDefinition.Slot::key).toList();
        PresetProfile profile = presetProfile(templateId, variant, normalized(header), normalized(heading),
                normalized(density), "DISABLED".equalsIgnoreCase(photoPolicy) ? "HIDE" : "AUTO");
        return new ResumeDesignSettings(SCHEMA, profile.fontPreset(), profile.fontScale(), profile.lineHeight(),
                profile.pageMargin(), accent, profile.dateFormat(), profile.headerLayout(), profile.headingStyle(),
                profile.photoMode(), profile.density(),
                List.of(), order);
    }

    public static ResumeDesignSettings fromJson(
            JsonNode node, ResumeLayoutDefinition definition, String variantCode, String photoPolicy) {
        return fromJson(node, definition, variantCode, photoPolicy, null);
    }

    public static ResumeDesignSettings fromJson(
            JsonNode node,
            ResumeLayoutDefinition definition,
            String variantCode,
            String photoPolicy,
            String templateId) {
        ResumeDesignSettings base = defaults(definition, variantCode, photoPolicy, templateId);
        if (node == null || !node.isObject()) return base;
        List<String> hidden = strings(node.path("hiddenSections"));
        List<String> order = strings(node.path("sectionOrder"));
        ResumeDesignSettings value = new ResumeDesignSettings(
                node.path("schemaVersion").asText(SCHEMA),
                node.path("fontPreset").asText(base.fontPreset()),
                node.path("fontScale").asText(base.fontScale()),
                node.path("lineHeight").asText(base.lineHeight()),
                node.path("pageMargin").asText(base.pageMargin()),
                node.path("accentColor").asText(base.accentColor()),
                node.path("dateFormat").asText(base.dateFormat()),
                node.path("headerLayout").asText(base.headerLayout()),
                node.path("headingStyle").asText(base.headingStyle()),
                node.path("photoMode").asText(base.photoMode()),
                node.path("density").asText(base.density()),
                hidden,
                order.isEmpty() ? base.sectionOrder() : order);
        return validate(value, definition, photoPolicy);
    }

    private static PresetProfile presetProfile(
            String templateId,
            String variant,
            String baseHeader,
            String baseHeading,
            String baseDensity,
            String basePhoto) {
        String key = (templateId == null ? "" : templateId.trim().toLowerCase(Locale.ROOT)) + ":" + variant;
        return switch (key) {
            case "rlt-b-ats-minimal-v1:MONO" -> profile("MODERN_SANS", "STANDARD", "COMPACT", "STANDARD",
                    "YYYY_DOT_MM", "MINIMAL", "RULE", "HIDE", "COMPACT");
            case "rlt-b-ats-minimal-v1:BLUE" -> profile("MODERN_SANS", "STANDARD", "STANDARD", "STANDARD",
                    "YYYY_DOT_MM", "SPLIT", "SIDELINE", "HIDE", "STANDARD");
            case "rlt-b-tech-single-v1:MONO" -> profile("MODERN_SANS", "SMALL", "COMPACT", "NARROW",
                    "YYYY_DOT_MM", "COMPACT", "RULE", "HIDE", "COMPACT");
            case "rlt-b-tech-single-v1:BLUE" -> profile("MODERN_SANS", "STANDARD", "COMPACT", "STANDARD",
                    "YYYY_DOT_MM", "SPLIT", "BAR", "HIDE", "COMPACT");
            case "rlt-b-tech-double-v1:BLUE" -> profile("MODERN_SANS", "STANDARD", "STANDARD", "STANDARD",
                    "YYYY_DOT_MM", "SPLIT", "SIDELINE", "HIDE", "STANDARD");
            case "rlt-b-tech-double-v1:GRAY" -> profile("MODERN_SANS", "STANDARD", "AIRY", "WIDE",
                    "YYYY_CN_MM", "COMPACT", "RULE", "HIDE", "STANDARD");
            case "rlt-b-campus-v1:NO_PHOTO" -> profile("MODERN_SANS", "STANDARD", "COMPACT", "NARROW",
                    "YYYY_DOT_MM", "BAND", "BAR", "HIDE", "COMPACT");
            case "rlt-b-campus-v1:PHOTO" -> profile("MODERN_SANS", "STANDARD", "STANDARD", "STANDARD",
                    "YYYY_CN_MM", "SPLIT", "SIDELINE", "SHOW", "STANDARD");
            case "rlt-b-career-pro-v1:BLUE" -> profile("MODERN_SANS", "STANDARD", "STANDARD", "STANDARD",
                    "YYYY_DOT_MM", "SPLIT", "SIDELINE", "AUTO", "STANDARD");
            case "rlt-b-career-pro-v1:MONO" -> profile("CLASSIC_SERIF", "STANDARD", "STANDARD", "WIDE",
                    "YYYY_CN_MM", "COMPACT", "RULE", "AUTO", "STANDARD");
            case "rlt-b-consulting-v1:NO_PHOTO_01" -> profile("MODERN_SANS", "SMALL", "COMPACT", "NARROW",
                    "YYYY_DOT_MM", "COMPACT", "RULE", "HIDE", "COMPACT");
            case "rlt-b-consulting-v1:NO_PHOTO_02" -> profile("CLASSIC_SERIF", "STANDARD", "STANDARD", "WIDE",
                    "YYYY_CN_MM", "MINIMAL", "SIDELINE", "HIDE", "STANDARD");
            case "rlt-b-finance-v1:FINANCE_MINIMAL" -> profile("MODERN_SANS", "SMALL", "COMPACT", "NARROW",
                    "YYYY_DOT_MM", "COMPACT", "RULE", "AUTO", "COMPACT");
            case "rlt-b-finance-v1:BANKING_FORMAL" -> profile("CLASSIC_SERIF", "STANDARD", "STANDARD", "WIDE",
                    "YYYY_CN_MM", "BAND", "TABLE", "AUTO", "STANDARD");
            case "rlt-b-product-ops-v1:MARKETING" -> profile("MODERN_SANS", "STANDARD", "COMPACT", "NARROW",
                    "YYYY_DOT_MM", "BAND", "BAR", "AUTO", "COMPACT");
            case "rlt-b-product-ops-v1:ECOMMERCE" -> profile("MODERN_SANS", "STANDARD", "STANDARD", "STANDARD",
                    "YYYY_CN_MM", "SPLIT", "SIDELINE", "AUTO", "STANDARD");
            case "rlt-b-education-research-v1:TEACHER" -> profile("MODERN_SANS", "STANDARD", "AIRY", "WIDE",
                    "YYYY_CN_MM", "SPLIT", "SIDELINE", "AUTO", "AIRY");
            case "rlt-b-education-research-v1:ACADEMIC" -> profile("CLASSIC_SERIF", "STANDARD", "STANDARD", "WIDE",
                    "YYYY_CN_MM", "MINIMAL", "RULE", "AUTO", "STANDARD");
            case "rlt-b-english-single-v1:CLASSIC" -> profile("CLASSIC_SERIF", "STANDARD", "STANDARD", "WIDE",
                    "YYYY_DOT_MM", "MINIMAL", "RULE", "HIDE", "STANDARD");
            case "rlt-b-english-single-v1:MODERN" -> profile("MODERN_SANS", "SMALL", "COMPACT", "NARROW",
                    "YYYY_DOT_MM", "COMPACT", "SIDELINE", "HIDE", "COMPACT");
            case "rlt-b-cn-table-v1:STANDARD" -> profile("CLASSIC_SERIF", "STANDARD", "STANDARD", "WIDE",
                    "YYYY_CN_MM", "BAND", "TABLE", "AUTO", "STANDARD");
            case "rlt-b-cn-table-v1:COMPACT" -> profile("MODERN_SANS", "SMALL", "COMPACT", "NARROW",
                    "YYYY_DOT_MM", "COMPACT", "TABLE", "AUTO", "COMPACT");
            case "rlt-b-qa-data-v1:QA" -> profile("MODERN_SANS", "SMALL", "COMPACT", "NARROW",
                    "YYYY_DOT_MM", "COMPACT", "SIDELINE", "HIDE", "COMPACT");
            case "rlt-b-qa-data-v1:DATA" -> profile("MODERN_SANS", "STANDARD", "STANDARD", "STANDARD",
                    "YYYY_CN_MM", "SPLIT", "BAR", "HIDE", "STANDARD");
            default -> profile("MODERN_SANS", "STANDARD", baseDensity, "STANDARD", "YYYY_DOT_MM",
                    baseHeader, baseHeading, basePhoto, baseDensity);
        };
    }

    private static PresetProfile profile(
            String fontPreset,
            String fontScale,
            String lineHeight,
            String pageMargin,
            String dateFormat,
            String headerLayout,
            String headingStyle,
            String photoMode,
            String density) {
        return new PresetProfile(fontPreset, fontScale, lineHeight, pageMargin, dateFormat,
                headerLayout, headingStyle, photoMode, density);
    }

    private record PresetProfile(
            String fontPreset,
            String fontScale,
            String lineHeight,
            String pageMargin,
            String dateFormat,
            String headerLayout,
            String headingStyle,
            String photoMode,
            String density) {
    }

    public static ResumeDesignSettings validate(
            ResumeDesignSettings value, ResumeLayoutDefinition definition, String photoPolicy) {
        if (value == null || !SCHEMA.equals(value.schemaVersion())) throw invalid("设计协议无效");
        String font = allowed(value.fontPreset(), FONT_PRESETS, "字体预设");
        String scale = allowed(value.fontScale(), FONT_SCALES, "字号档");
        String line = allowed(value.lineHeight(), LINE_HEIGHTS, "行距");
        String margin = allowed(value.pageMargin(), PAGE_MARGINS, "页边距");
        String date = allowed(value.dateFormat(), DATE_FORMATS, "日期格式");
        String header = allowed(value.headerLayout(), HEADER_LAYOUTS, "头部布局");
        String heading = allowed(value.headingStyle(), HEADING_STYLES, "标题样式");
        String photo = allowed(value.photoMode(), PHOTO_MODES, "照片模式");
        String density = allowed(value.density(), DENSITIES, "内容密度");
        if ("DISABLED".equalsIgnoreCase(photoPolicy) && !"HIDE".equals(photo)) {
            throw invalid("该模板不支持照片");
        }
        String accent = value.accentColor() == null ? "" : value.accentColor().toUpperCase(Locale.ROOT);
        Set<String> allowedColors = new LinkedHashSet<>();
        if (definition.tokens() != null) {
            definition.tokens().forEach((key, color) -> {
                if (key.startsWith("accent.") && color != null) allowedColors.add(color.toUpperCase(Locale.ROOT));
            });
        }
        if (!accent.matches("#[0-9A-F]{6}") || !allowedColors.contains(accent)) {
            throw invalid("主题色不属于该模板的受控色板");
        }
        List<String> hidden = normalizedSections(value.hiddenSections(), false);
        List<String> order = normalizedSections(value.sectionOrder(), true);
        List<String> completed = new ArrayList<>(order);
        definition.slots().stream().sorted(java.util.Comparator.comparingInt(ResumeLayoutDefinition.Slot::order))
                .map(ResumeLayoutDefinition.Slot::key).filter(key -> !completed.contains(key)).forEach(completed::add);
        return new ResumeDesignSettings(SCHEMA, font, scale, line, margin, accent, date, header,
                heading, photo, density, hidden, completed);
    }

    public ResumeLayoutDefinition applyTo(ResumeLayoutDefinition definition) {
        ResumeLayoutDefinition.Page originalPage = definition.page();
        int horizontal = switch (pageMargin) {
            case "NARROW" -> 34;
            case "WIDE" -> 66;
            default -> originalPage.effectiveMarginXPt();
        };
        int vertical = switch (pageMargin) {
            case "NARROW" -> 34;
            case "WIDE" -> 60;
            default -> originalPage.effectiveMarginTopPt();
        };
        ResumeLayoutDefinition.Page page = new ResumeLayoutDefinition.Page(
                originalPage.maxPages(), originalPage.capacityUnits(), horizontal, vertical,
                pageMargin.equals("WIDE") ? 56 : originalPage.effectiveMarginBottomPt());

        Map<String, String> tokens = new java.util.LinkedHashMap<>();
        if (definition.tokens() != null) tokens.putAll(definition.tokens());
        tokens.replaceAll((key, value) -> key.startsWith("accent.") ? accentColor
                : key.startsWith("surface.") ? softSurface(accentColor) : value);
        tokens.put("fontPreset", fontPreset);
        tokens.put("fontScale", fontScale);
        tokens.put("lineHeight", lineHeight);
        tokens.put("dateFormat", dateFormat);
        tokens.put("photoMode", photoMode);

        Map<String, Integer> positions = new java.util.HashMap<>();
        for (int index = 0; index < sectionOrder.size(); index++) positions.put(sectionOrder.get(index), index);
        List<ResumeLayoutDefinition.Slot> slots = definition.slots().stream()
                .filter(slot -> !hiddenSections.contains(slot.key()))
                .sorted(java.util.Comparator.comparingInt(slot -> positions.getOrDefault(slot.key(), Integer.MAX_VALUE)))
                .map(slot -> new ResumeLayoutDefinition.Slot(slot.key(),
                        (positions.getOrDefault(slot.key(), slot.order()) + 1) * 10,
                        slot.capacityUnits(), slot.repeatable(), slot.hideWhenEmpty(),
                        slot.label(), headingStyle))
                .toList();
        Set<String> visible = slots.stream().map(ResumeLayoutDefinition.Slot::key).collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ResumeLayoutDefinition.Column> columns = definition.columns().stream()
                .map(column -> new ResumeLayoutDefinition.Column(column.id(), column.widthPercent(),
                        column.slotKeys().stream().filter(visible::contains)
                                .sorted(java.util.Comparator.comparingInt(key -> positions.getOrDefault(key, Integer.MAX_VALUE)))
                                .toList(), column.tone()))
                .filter(column -> !column.slotKeys().isEmpty())
                .toList();
        if (columns.isEmpty()) throw invalid("不能隐藏全部简历模块");
        if (columns.size() == 1 && columns.get(0).widthPercent() != 100) {
            ResumeLayoutDefinition.Column only = columns.get(0);
            columns = List.of(new ResumeLayoutDefinition.Column(only.id(), 100, only.slotKeys(), only.tone()));
        }
        ResumeLayoutDefinition.Visual prior = definition.visual();
        ResumeLayoutDefinition.Visual visual = new ResumeLayoutDefinition.Visual(
                headerLayout, headingStyle, prior == null ? null : prior.subtitle(),
                prior != null && prior.showMark(), density);
        return new ResumeLayoutDefinition(page, columns, slots, Map.copyOf(tokens), visual,
                definition.designCapabilities());
    }

    private static List<String> normalizedSections(List<String> values, boolean requireUnique) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) for (String value : values) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (!SECTION_KEYS.contains(normalized)) throw invalid("模块标识无效");
            if (requireUnique && !result.add(normalized)) throw invalid("模块顺序不能重复");
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static String allowed(String value, Set<String> allowed, String label) {
        String normalized = normalized(value);
        if (!allowed.contains(normalized)) throw invalid(label + "无效");
        return normalized;
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String softSurface(String accent) {
        int rgb = Integer.parseInt(accent.substring(1), 16);
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        red = Math.round(red * 0.09f + 255 * 0.91f);
        green = Math.round(green * 0.09f + 255 * 0.91f);
        blue = Math.round(blue * 0.09f + 255 * 0.91f);
        return "#%02X%02X%02X".formatted(red, green, blue);
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Invalid resume design: " + detail);
    }
}
