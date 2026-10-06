package com.jobproof.modules.resume.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A built-in template manifest (frontend src/resume-render/templates/manifest.ts), stored as the
 * definition of a resume-render-v4 template version. The layout itself lives in the frontend; the
 * server only needs what it validates designs against and what the catalog shows.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeTemplateManifest(
        String id,
        int revision,
        String name,
        String nameEn,
        String category,
        String summary,
        String bestFor,
        List<String> tags,
        int maxPages,
        String locale,
        List<String> paperSizes,
        List<String> fontPairings,
        List<Palette> palettes,
        List<HeaderVariant> headerVariants,
        String photo,
        List<Region> regions,
        String atsLevel,
        boolean decorations,
        Map<String, String> sectionTitles,
        JsonNode defaults) {

    public static final List<String> SECTION_KEYS = List.of(
            "summary", "experience", "projects", "education", "organizations",
            "skills", "certificates", "honors", "languages");
    private static final Set<String> CATEGORIES = Set.of("steady", "modern", "design", "industry");
    private static final Set<String> PAPERS = Set.of("A4", "LETTER");
    private static final Set<String> FONT_PAIRINGS = Set.of("sans", "serif", "mixed", "tech");
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9-]{1,40}$");
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Palette(String id, String name, String accent) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeaderVariant(String id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Region(String id, List<String> sections) {}

    public static ResumeTemplateManifest parse(ObjectMapper mapper, JsonNode node) {
        ResumeTemplateManifest manifest;
        try {
            manifest = mapper.treeToValue(node, ResumeTemplateManifest.class);
        } catch (Exception exception) {
            throw invalid("清单无法解析");
        }
        return manifest.validated();
    }

    public static ResumeTemplateManifest parse(ObjectMapper mapper, String json) {
        try {
            return parse(mapper, mapper.readTree(json));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("清单无法解析");
        }
    }

    private ResumeTemplateManifest validated() {
        if (id == null || !ID.matcher(id).matches()) throw invalid("模板标识无效");
        if (revision < 1) throw invalid("修订号无效");
        if (name == null || name.isBlank()) throw invalid("缺少模板名称");
        if (category == null || !CATEGORIES.contains(category)) throw invalid("模板分类无效");
        if (maxPages < 1 || maxPages > 4) throw invalid("最大页数无效");
        if (!"zh-CN".equals(locale) && !"en".equals(locale)) throw invalid("模板语言无效");
        if (paperSizes == null || paperSizes.isEmpty() || !PAPERS.containsAll(paperSizes)) throw invalid("纸张尺寸无效");
        if (fontPairings == null || fontPairings.isEmpty() || !FONT_PAIRINGS.containsAll(fontPairings)) {
            throw invalid("字体组合无效");
        }
        if (palettes == null || palettes.isEmpty() || palettes.stream().anyMatch(palette -> palette == null
                || palette.id() == null || palette.accent() == null || !HEX.matcher(palette.accent()).matches())) {
            throw invalid("配色无效");
        }
        if (headerVariants == null || headerVariants.isEmpty()) throw invalid("缺少页头样式");
        if (!"none".equals(photo) && !"optional".equals(photo)) throw invalid("照片策略无效");
        if (regions == null || regions.isEmpty() || regions.size() > 3) throw invalid("版面区域无效");
        Set<String> seen = new java.util.HashSet<>();
        for (Region region : regions) {
            if (region == null || region.id() == null || region.sections() == null) throw invalid("版面区域无效");
            for (String key : region.sections()) {
                if (!SECTION_KEYS.contains(key) || !seen.add(key)) throw invalid("区域板块无效或重复");
            }
        }
        if (!seen.containsAll(SECTION_KEYS)) throw invalid("区域必须覆盖全部板块");
        if (!"strict".equals(atsLevel) && !"standard".equals(atsLevel)) throw invalid("ATS 等级无效");
        return this;
    }

    public boolean photoAllowed() {
        return "optional".equals(photo);
    }

    public boolean hasPalette(String paletteId) {
        return palettes.stream().anyMatch(palette -> palette.id().equals(paletteId));
    }

    public boolean hasHeaderVariant(String variant) {
        return headerVariants.stream().anyMatch(item -> item.id().equals(variant));
    }

    public boolean hasRegion(String regionId) {
        return regions.stream().anyMatch(region -> region.id().equals(regionId));
    }

    public List<String> defaultSectionOrder() {
        return regions.stream().flatMap(region -> region.sections().stream()).toList();
    }

    public String regionOf(String sectionKey) {
        return regions.stream().filter(region -> region.sections().contains(sectionKey))
                .map(Region::id).findFirst().orElse(regions.get(0).id());
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Invalid resume template manifest: " + detail);
    }
}
