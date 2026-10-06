package com.jobproof.modules.resume.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single-column, ATS-friendly layout used for the Word file of a built-in (resume-render-v4)
 * template: the template's look lives in HTML/CSS and is not reproduced in DOCX (docs/phase2/01 §4),
 * but the user's section order, hidden sections, renamed titles and basic typography are kept.
 */
public final class ResumeGenericLayout {
    public static final String VARIANT = "DEFAULT";

    private static final Map<String, String> ZH_TITLES = Map.of(
            "summary", "个人简介", "experience", "工作经历", "projects", "项目经历", "education", "教育经历",
            "organizations", "校园与社团", "skills", "专业技能", "certificates", "证书资质", "honors", "荣誉奖项",
            "languages", "语言能力");
    private static final Map<String, String> EN_TITLES = Map.of(
            "summary", "Summary", "experience", "Experience", "projects", "Projects", "education", "Education",
            "organizations", "Leadership & Activities", "skills", "Skills", "certificates", "Certifications",
            "honors", "Honors & Awards", "languages", "Languages");

    private ResumeGenericLayout() {
    }

    public static ResumeLayoutDefinition definition(ResumeTemplateManifest manifest, ResumeDesignV2 design) {
        List<String> visible = design.visibleSections();
        if (visible.isEmpty()) visible = List.of("summary");
        List<ResumeLayoutDefinition.Slot> slots = new ArrayList<>();
        int order = 10;
        for (String key : visible) {
            slots.add(new ResumeLayoutDefinition.Slot(key, order, 1000, true, true, title(manifest, design, key), "RULE"));
            order += 10;
        }
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accent." + VARIANT, design.customAccent() != null ? design.customAccent()
                : manifest.palettes().stream().filter(palette -> palette.id().equals(design.paletteId()))
                        .map(ResumeTemplateManifest.Palette::accent).findFirst()
                        .orElse(manifest.palettes().get(0).accent()));
        tokens.put("fontPreset", "serif".equals(design.fontPairing()) ? "CLASSIC_SERIF" : "MODERN_SANS");
        tokens.put("fontScale", switch (design.fontSize()) {
            case "XS", "S" -> "SMALL";
            case "L", "XL" -> "LARGE";
            default -> "STANDARD";
        });
        tokens.put("lineHeight", switch (design.lineHeight()) {
            case "COMPACT" -> "COMPACT";
            case "RELAXED" -> "AIRY";
            default -> "STANDARD";
        });
        tokens.put("dateFormat", "YYYY年MM月".equals(design.dateFormat()) ? "YYYY_CN_MM" : "YYYY_DOT_MM");
        tokens.put("photoMode", manifest.photoAllowed() ? design.photo().mode() : "HIDE");
        int margin = switch (design.pageMargin()) {
            case "NARROW" -> 34;
            case "WIDE" -> 57;
            default -> 45;
        };
        String density = switch (design.spacing()) {
            case "TIGHT" -> "COMPACT";
            case "RELAXED" -> "AIRY";
            default -> "STANDARD";
        };
        return new ResumeLayoutDefinition(
                new ResumeLayoutDefinition.Page(Math.max(1, Math.min(4, design.pageLimit(manifest))), 1000,
                        margin, margin, margin),
                List.of(new ResumeLayoutDefinition.Column("main", 100, visible, "PLAIN")),
                List.copyOf(slots),
                Map.copyOf(tokens),
                new ResumeLayoutDefinition.Visual("MINIMAL", "RULE", null, false, density));
    }

    public static String title(ResumeTemplateManifest manifest, ResumeDesignV2 design, String key) {
        String custom = design.sectionTitles().get(key);
        if (custom != null && !custom.isBlank()) return custom;
        String templateTitle = manifest.sectionTitles() == null ? null : manifest.sectionTitles().get(key);
        if (templateTitle != null && !templateTitle.isBlank()) return templateTitle;
        return ("en".equals(manifest.locale()) ? EN_TITLES : ZH_TITLES).getOrDefault(key, key);
    }
}
