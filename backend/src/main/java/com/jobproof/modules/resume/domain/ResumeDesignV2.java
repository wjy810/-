package com.jobproof.modules.resume.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Design settings v2 (docs/phase2/03 §6.2), mirrored from frontend src/resume-render/theme/design.ts
 * and tokens.ts. {@link #coerce} matches the frontend's lenient reading of stored settings;
 * {@link #validate} is the strict check for settings a user saves.
 */
public record ResumeDesignV2(
        String schemaVersion,
        String paletteId,
        String customAccent,
        String fontPairing,
        String fontSize,
        String lineHeight,
        String spacing,
        String pageMargin,
        String headerVariant,
        Photo photo,
        boolean contactIcons,
        String dateFormat,
        String pageTarget,
        String paperSize,
        boolean decorations,
        List<String> sectionOrder,
        List<String> hiddenSections,
        Map<String, String> sectionTitles,
        Map<String, String> regionAssignments) {

    public static final String SCHEMA = "resume-design-v2";
    public static final int SECTION_TITLE_MAX = 16;

    static final List<String> FONT_SIZES = List.of("XS", "S", "M", "L", "XL");
    static final List<String> LINE_HEIGHTS = List.of("COMPACT", "NORMAL", "RELAXED");
    static final List<String> SPACINGS = List.of("TIGHT", "NORMAL", "RELAXED");
    static final List<String> PAGE_MARGINS = List.of("NARROW", "STANDARD", "WIDE");
    static final List<String> PAGE_TARGETS = List.of("AUTO", "ONE", "TWO");
    static final List<String> PHOTO_MODES = List.of("AUTO", "SHOW", "HIDE");
    static final List<String> PHOTO_SHAPES = List.of("CIRCLE", "ROUNDED", "SQUARE");
    static final List<String> DATE_FORMATS = List.of("YYYY.MM", "YYYY年MM月", "MM/YYYY", "MMM YYYY");
    private static final Pattern HEX = Pattern.compile("^#?([0-9a-fA-F]{6})$");
    private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u001f\\u007f]");

    public record Photo(String mode, String shape) {}

    public static ResumeDesignV2 defaults(ResumeTemplateManifest manifest) {
        ResumeDesignV2 base = new ResumeDesignV2(
                SCHEMA,
                manifest.palettes().get(0).id(),
                null,
                manifest.fontPairings().get(0),
                "M", "NORMAL", "NORMAL", "STANDARD",
                manifest.headerVariants().get(0).id(),
                new Photo(manifest.photoAllowed() ? "AUTO" : "HIDE", "CIRCLE"),
                true,
                "en".equals(manifest.locale()) ? "MMM YYYY" : "YYYY.MM",
                "AUTO",
                manifest.paperSizes().get(0),
                true,
                manifest.defaultSectionOrder(),
                List.of(),
                Map.of(),
                Map.of());
        JsonNode overrides = manifest.defaults();
        return overrides == null || !overrides.isObject() ? base : read(manifest, overrides, base);
    }

    /** Any stored settings (v2, v1 or garbage) → valid v2 settings for this template. */
    public static ResumeDesignV2 coerce(ResumeTemplateManifest manifest, JsonNode input) {
        JsonNode raw = input != null && input.isObject() ? input : null;
        if (raw == null) return defaults(manifest);
        if (ResumeDesignSettings.SCHEMA.equals(raw.path("schemaVersion").asText())) raw = fromV1(manifest, raw);
        return read(manifest, raw, defaults(manifest));
    }

    /**
     * Strict validation of saved settings: unknown values are rejected instead of replaced. Missing
     * fields take the template default, so partial updates are allowed. v1 settings are converted.
     */
    public static ResumeDesignV2 validate(ResumeTemplateManifest manifest, JsonNode input) {
        if (input == null || !input.isObject()) throw invalid("设计设置必须是对象");
        String schema = input.path("schemaVersion").asText(SCHEMA);
        if (ResumeDesignSettings.SCHEMA.equals(schema)) return coerce(manifest, input);
        if (!SCHEMA.equals(schema)) throw invalid("设计设置版本不受支持");
        ResumeDesignV2 defaults = defaults(manifest);
        String palette = text(input, "paletteId", defaults.paletteId());
        if (!manifest.hasPalette(palette)) throw invalid("配色不属于当前模板");
        String accent = null;
        JsonNode accentNode = input.path("customAccent");
        if (!accentNode.isMissingNode() && !accentNode.isNull()) {
            accent = normalizeHex(accentNode.asText(""));
            if (accent == null) throw invalid("自定义强调色必须是六位十六进制颜色");
        }
        String fonts = text(input, "fontPairing", defaults.fontPairing());
        if (!manifest.fontPairings().contains(fonts)) throw invalid("字体组合不属于当前模板");
        String header = text(input, "headerVariant", defaults.headerVariant());
        if (!manifest.hasHeaderVariant(header)) throw invalid("页头样式不属于当前模板");
        JsonNode photoNode = input.path("photo");
        String photoMode = text(photoNode, "mode", defaults.photo().mode());
        String photoShape = text(photoNode, "shape", defaults.photo().shape());
        require(PHOTO_MODES, photoMode, "照片显示方式无效");
        require(PHOTO_SHAPES, photoShape, "照片形状无效");
        if (!manifest.photoAllowed() && "SHOW".equals(photoMode)) throw invalid("当前模板不支持照片");
        String paper = text(input, "paperSize", defaults.paperSize());
        if (!manifest.paperSizes().contains(paper)) throw invalid("纸张尺寸不属于当前模板");
        List<String> order = strictSections(input.path("sectionOrder"), "板块顺序");
        if (order.size() != new LinkedHashSet<>(order).size()) throw invalid("板块顺序存在重复");
        List<String> hidden = strictSections(input.path("hiddenSections"), "隐藏板块");
        Map<String, String> titles = new LinkedHashMap<>();
        JsonNode titleNode = input.path("sectionTitles");
        if (!titleNode.isMissingNode() && !titleNode.isNull()) {
            if (!titleNode.isObject()) throw invalid("板块标题必须是对象");
            titleNode.fields().forEachRemaining(entry -> {
                if (!ResumeTemplateManifest.SECTION_KEYS.contains(entry.getKey())) throw invalid("板块标题引用了未知板块");
                if (!entry.getValue().isTextual()) throw invalid("板块标题必须是文本");
                String value = entry.getValue().asText();
                if (CONTROL.matcher(value).find()) throw invalid("板块标题包含控制字符");
                String trimmed = value.trim();
                if (trimmed.codePointCount(0, trimmed.length()) > SECTION_TITLE_MAX) {
                    throw invalid("板块标题不能超过 " + SECTION_TITLE_MAX + " 个字");
                }
                if (!trimmed.isEmpty()) titles.put(entry.getKey(), trimmed);
            });
        }
        Map<String, String> regions = new LinkedHashMap<>();
        JsonNode regionNode = input.path("regionAssignments");
        if (!regionNode.isMissingNode() && !regionNode.isNull()) {
            if (!regionNode.isObject()) throw invalid("板块分栏必须是对象");
            regionNode.fields().forEachRemaining(entry -> {
                if (!ResumeTemplateManifest.SECTION_KEYS.contains(entry.getKey())) throw invalid("板块分栏引用了未知板块");
                if (!entry.getValue().isTextual() || !manifest.hasRegion(entry.getValue().asText())) {
                    throw invalid("板块分栏引用了当前模板没有的栏位");
                }
                regions.put(entry.getKey(), entry.getValue().asText());
            });
        }
        return new ResumeDesignV2(
                SCHEMA, palette, accent, fonts,
                strictEnum(input, "fontSize", FONT_SIZES, defaults.fontSize(), "字号无效"),
                strictEnum(input, "lineHeight", LINE_HEIGHTS, defaults.lineHeight(), "行距无效"),
                strictEnum(input, "spacing", SPACINGS, defaults.spacing(), "段落间距无效"),
                strictEnum(input, "pageMargin", PAGE_MARGINS, defaults.pageMargin(), "页边距无效"),
                header,
                new Photo(manifest.photoAllowed() ? photoMode : "HIDE", photoShape),
                strictBoolean(input, "contactIcons", defaults.contactIcons()),
                strictEnum(input, "dateFormat", DATE_FORMATS, defaults.dateFormat(), "日期格式无效"),
                strictEnum(input, "pageTarget", PAGE_TARGETS, defaults.pageTarget(), "目标页数无效"),
                paper,
                strictBoolean(input, "decorations", defaults.decorations()),
                completeOrder(order, defaults.sectionOrder()),
                List.copyOf(new LinkedHashSet<>(hidden)),
                sorted(titles),
                sorted(regions));
    }

    /**
     * Switching templates keeps content and the general settings the user changed (size, spacing,
     * margins, dates, page target, hidden sections, renamed titles, photo, custom accent); everything
     * else, including template-specific settings (palette, header, region assignments, section
     * order), takes the new template's defaults (DSN-06). Values a user never touched follow the new
     * template, so a dense one-page template stays dense.
     *
     * @param previousManifest the template the settings belong to, or null for v1 settings of a
     *     retired template, whose type-scale values were preset defaults rather than user choices
     */
    public static ResumeDesignV2 carryOver(ResumeTemplateManifest target, JsonNode previous,
            ResumeTemplateManifest previousManifest) {
        ResumeDesignV2 defaults = defaults(target);
        if (previous == null || !previous.isObject()) return defaults;
        boolean legacy = ResumeDesignSettings.SCHEMA.equals(previous.path("schemaVersion").asText());
        ResumeDesignV2 source = legacy ? read(target, fromV1(target, previous), defaults)
                : previousManifest == null ? read(target, previous, defaults) : coerce(previousManifest, previous);
        ResumeDesignV2 baseline = legacy ? null : previousManifest == null ? defaults : defaults(previousManifest);
        boolean sameLocale = previousManifest == null || target.locale().equals(previousManifest.locale());
        String fonts = changed(baseline, source, ResumeDesignV2::fontPairing)
                && target.fontPairings().contains(source.fontPairing()) ? source.fontPairing() : defaults.fontPairing();
        String paper = changed(baseline, source, ResumeDesignV2::paperSize)
                && target.paperSizes().contains(source.paperSize()) ? source.paperSize() : defaults.paperSize();
        String photoMode = !target.photoAllowed() ? "HIDE"
                : legacy || changed(baseline, source, value -> value.photo().mode()) ? source.photo().mode()
                : defaults.photo().mode();
        return new ResumeDesignV2(
                SCHEMA,
                defaults.paletteId(),
                source.customAccent(),
                fonts,
                pick(baseline, source, defaults, ResumeDesignV2::fontSize),
                pick(baseline, source, defaults, ResumeDesignV2::lineHeight),
                pick(baseline, source, defaults, ResumeDesignV2::spacing),
                pick(baseline, source, defaults, ResumeDesignV2::pageMargin),
                defaults.headerVariant(),
                new Photo(photoMode, pick(baseline, source, defaults, value -> value.photo().shape())),
                pick(baseline, source, defaults, ResumeDesignV2::contactIcons),
                sameLocale && (legacy || changed(baseline, source, ResumeDesignV2::dateFormat))
                        ? source.dateFormat() : defaults.dateFormat(),
                pick(baseline, source, defaults, ResumeDesignV2::pageTarget),
                paper,
                pick(baseline, source, defaults, ResumeDesignV2::decorations),
                defaults.sectionOrder(),
                source.hiddenSections(),
                source.sectionTitles(),
                Map.of());
    }

    private static <T> boolean changed(ResumeDesignV2 baseline, ResumeDesignV2 source,
            java.util.function.Function<ResumeDesignV2, T> field) {
        return baseline != null && !java.util.Objects.equals(field.apply(source), field.apply(baseline));
    }

    private static <T> T pick(ResumeDesignV2 baseline, ResumeDesignV2 source, ResumeDesignV2 defaults,
            java.util.function.Function<ResumeDesignV2, T> field) {
        return changed(baseline, source, field) ? field.apply(source) : field.apply(defaults);
    }

    /** Pages the content must fit on: the explicit target, or the template maximum. */
    public int pageLimit(ResumeTemplateManifest manifest) {
        return switch (pageTarget) {
            case "ONE" -> 1;
            case "TWO" -> 2;
            default -> manifest.maxPages();
        };
    }

    /** Sections in display order, without hidden ones. */
    public List<String> visibleSections() {
        return sectionOrder.stream().filter(key -> !hiddenSections.contains(key)).toList();
    }

    public ObjectNode toJson(ObjectMapper mapper) {
        return mapper.valueToTree(this);
    }

    /** Legacy resume-design-v1 fields → v2 field names (docs/phase2/03 §6.3). */
    static ObjectNode fromV1(ResumeTemplateManifest manifest, JsonNode raw) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        putMapped(result, "fontPairing", raw.path("fontPreset").asText(),
                Map.of("MODERN_SANS", "sans", "CLASSIC_SERIF", "serif"));
        putMapped(result, "fontSize", raw.path("fontScale").asText(),
                Map.of("SMALL", "S", "STANDARD", "M", "LARGE", "L"));
        putMapped(result, "lineHeight", raw.path("lineHeight").asText(),
                Map.of("COMPACT", "COMPACT", "STANDARD", "NORMAL", "AIRY", "RELAXED"));
        putMapped(result, "spacing", raw.path("density").asText(),
                Map.of("COMPACT", "TIGHT", "STANDARD", "NORMAL", "AIRY", "RELAXED"));
        String margin = raw.path("pageMargin").asText();
        if (PAGE_MARGINS.contains(margin)) result.put("pageMargin", margin);
        putMapped(result, "dateFormat", raw.path("dateFormat").asText(),
                Map.of("YYYY_DOT_MM", "YYYY.MM", "YYYY_CN_MM", "YYYY年MM月"));
        String photoMode = raw.path("photoMode").asText();
        if (PHOTO_MODES.contains(photoMode)) result.putObject("photo").put("mode", photoMode).put("shape", "CIRCLE");
        String accent = normalizeHex(raw.path("accentColor").asText(""));
        if (accent != null) {
            manifest.palettes().stream().filter(palette -> palette.accent().equalsIgnoreCase(accent)).findFirst()
                    .ifPresentOrElse(palette -> result.put("paletteId", palette.id()),
                            () -> result.put("customAccent", accent));
        }
        if (raw.path("sectionOrder").isArray()) result.set("sectionOrder", raw.path("sectionOrder"));
        if (raw.path("hiddenSections").isArray()) result.set("hiddenSections", raw.path("hiddenSections"));
        return result;
    }

    private static ResumeDesignV2 read(ResumeTemplateManifest manifest, JsonNode raw, ResumeDesignV2 defaults) {
        JsonNode photo = raw.path("photo");
        List<String> order = lenientSections(raw.path("sectionOrder"));
        Map<String, String> titles = new LinkedHashMap<>();
        raw.path("sectionTitles").fields().forEachRemaining(entry -> {
            if (!ResumeTemplateManifest.SECTION_KEYS.contains(entry.getKey()) || !entry.getValue().isTextual()) return;
            String clean = CONTROL.matcher(entry.getValue().asText()).replaceAll("").trim();
            if (clean.codePointCount(0, clean.length()) > SECTION_TITLE_MAX) {
                clean = clean.substring(0, clean.offsetByCodePoints(0, SECTION_TITLE_MAX));
            }
            if (!clean.isEmpty()) titles.put(entry.getKey(), clean);
        });
        Map<String, String> regions = new LinkedHashMap<>();
        raw.path("regionAssignments").fields().forEachRemaining(entry -> {
            if (ResumeTemplateManifest.SECTION_KEYS.contains(entry.getKey()) && entry.getValue().isTextual()
                    && manifest.hasRegion(entry.getValue().asText())) {
                regions.put(entry.getKey(), entry.getValue().asText());
            }
        });
        String palette = raw.path("paletteId").asText(null);
        String fonts = raw.path("fontPairing").asText(null);
        String header = raw.path("headerVariant").asText(null);
        String paper = raw.path("paperSize").asText(null);
        String photoMode = oneOf(PHOTO_MODES, photo.path("mode").asText(null), defaults.photo().mode());
        return new ResumeDesignV2(
                SCHEMA,
                palette != null && manifest.hasPalette(palette) ? palette : defaults.paletteId(),
                raw.path("customAccent").isTextual() ? normalizeHex(raw.path("customAccent").asText())
                        : raw.path("customAccent").isMissingNode() ? defaults.customAccent() : null,
                fonts != null && manifest.fontPairings().contains(fonts) ? fonts : defaults.fontPairing(),
                oneOf(FONT_SIZES, raw.path("fontSize").asText(null), defaults.fontSize()),
                oneOf(LINE_HEIGHTS, raw.path("lineHeight").asText(null), defaults.lineHeight()),
                oneOf(SPACINGS, raw.path("spacing").asText(null), defaults.spacing()),
                oneOf(PAGE_MARGINS, raw.path("pageMargin").asText(null), defaults.pageMargin()),
                header != null && manifest.hasHeaderVariant(header) ? header : defaults.headerVariant(),
                new Photo(manifest.photoAllowed() ? photoMode : "HIDE",
                        oneOf(PHOTO_SHAPES, photo.path("shape").asText(null), defaults.photo().shape())),
                raw.path("contactIcons").isBoolean() ? raw.path("contactIcons").asBoolean() : defaults.contactIcons(),
                oneOf(DATE_FORMATS, raw.path("dateFormat").asText(null), defaults.dateFormat()),
                oneOf(PAGE_TARGETS, raw.path("pageTarget").asText(null), defaults.pageTarget()),
                paper != null && manifest.paperSizes().contains(paper) ? paper : defaults.paperSize(),
                raw.path("decorations").isBoolean() ? raw.path("decorations").asBoolean() : defaults.decorations(),
                order.isEmpty() ? defaults.sectionOrder() : completeOrder(order, defaults.sectionOrder()),
                raw.has("hiddenSections") ? lenientSections(raw.path("hiddenSections")) : defaults.hiddenSections(),
                raw.has("sectionTitles") ? sorted(titles) : defaults.sectionTitles(),
                raw.has("regionAssignments") ? sorted(regions) : defaults.regionAssignments());
    }

    private static List<String> completeOrder(List<String> order, List<String> fallback) {
        List<String> result = new ArrayList<>(order);
        fallback.stream().filter(key -> !result.contains(key)).forEach(result::add);
        return List.copyOf(result);
    }

    private static List<String> lenientSections(JsonNode value) {
        if (!value.isArray()) return List.of();
        Set<String> result = new LinkedHashSet<>();
        value.forEach(item -> {
            if (item.isTextual() && ResumeTemplateManifest.SECTION_KEYS.contains(item.asText())) result.add(item.asText());
        });
        return List.copyOf(result);
    }

    private static List<String> strictSections(JsonNode value, String label) {
        if (value.isMissingNode() || value.isNull()) return List.of();
        if (!value.isArray()) throw invalid(label + "必须是数组");
        List<String> result = new ArrayList<>();
        value.forEach(item -> {
            if (!item.isTextual() || !ResumeTemplateManifest.SECTION_KEYS.contains(item.asText())) {
                throw invalid(label + "包含未知板块");
            }
            result.add(item.asText());
        });
        return result;
    }

    private static String strictEnum(JsonNode input, String field, List<String> allowed, String fallback,
            String message) {
        String value = text(input, field, fallback);
        require(allowed, value, message);
        return value;
    }

    private static boolean strictBoolean(JsonNode input, String field, boolean fallback) {
        JsonNode value = input.path(field);
        if (value.isMissingNode() || value.isNull()) return fallback;
        if (!value.isBoolean()) throw invalid(field + " 必须是布尔值");
        return value.asBoolean();
    }

    private static String text(JsonNode input, String field, String fallback) {
        JsonNode value = input.path(field);
        if (value.isMissingNode() || value.isNull()) return fallback;
        if (!value.isTextual()) throw invalid(field + " 必须是文本");
        return value.asText();
    }

    private static void require(List<String> allowed, String value, String message) {
        if (!allowed.contains(value)) throw invalid(message);
    }

    private static String oneOf(List<String> allowed, String value, String fallback) {
        return value != null && allowed.contains(value) ? value : fallback;
    }

    private static void putMapped(ObjectNode target, String field, String value, Map<String, String> mapping) {
        String mapped = mapping.get(value);
        if (mapped != null) target.put(field, mapped);
    }

    private static Map<String, String> sorted(Map<String, String> value) {
        return java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(value));
    }

    static String normalizeHex(String value) {
        var matcher = HEX.matcher(value == null ? "" : value.trim());
        return matcher.matches() ? "#" + matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException(detail);
    }
}
