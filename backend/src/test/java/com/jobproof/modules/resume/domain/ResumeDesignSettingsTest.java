package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeDesignSettingsTest {

    private static final Map<String, List<String>> VARIANTS = Map.ofEntries(
            Map.entry("rlt-b-ats-minimal-v1", List.of("MONO", "BLUE")),
            Map.entry("rlt-b-tech-single-v1", List.of("MONO", "BLUE")),
            Map.entry("rlt-b-tech-double-v1", List.of("BLUE", "GRAY")),
            Map.entry("rlt-b-campus-v1", List.of("NO_PHOTO", "PHOTO")),
            Map.entry("rlt-b-career-pro-v1", List.of("BLUE", "MONO")),
            Map.entry("rlt-b-consulting-v1", List.of("NO_PHOTO_01", "NO_PHOTO_02")),
            Map.entry("rlt-b-finance-v1", List.of("FINANCE_MINIMAL", "BANKING_FORMAL")),
            Map.entry("rlt-b-product-ops-v1", List.of("MARKETING", "ECOMMERCE")),
            Map.entry("rlt-b-education-research-v1", List.of("TEACHER", "ACADEMIC")),
            Map.entry("rlt-b-english-single-v1", List.of("CLASSIC", "MODERN")),
            Map.entry("rlt-b-cn-table-v1", List.of("STANDARD", "COMPACT")),
            Map.entry("rlt-b-qa-data-v1", List.of("QA", "DATA")));

    @Test
    void everyTemplateFamilyProvidesTwoStructurallyDifferentDesignPresets() {
        ResumeLayoutDefinition definition = ResumeLayoutProtocolTest.twoColumnDefinition();
        VARIANTS.forEach((templateId, variants) -> {
            ResumeDesignSettings first = ResumeDesignSettings.defaults(
                    definition, variants.get(0), photoPolicy(templateId), templateId);
            ResumeDesignSettings second = ResumeDesignSettings.defaults(
                    definition, variants.get(1), photoPolicy(templateId), templateId);
            assertThat(signature(first)).as(templateId).isNotEqualTo(signature(second));
        });
    }

    @Test
    void appliedAccentKeepsPdfSurfaceInSyncWithTheWebPreview() {
        ResumeLayoutDefinition definition = ResumeLayoutProtocolTest.twoColumnDefinition();
        ResumeDesignSettings settings = new ResumeDesignSettings(
                ResumeDesignSettings.SCHEMA, "MODERN_SANS", "STANDARD", "STANDARD", "STANDARD",
                "#175CD3", "YYYY_DOT_MM", "SPLIT", "BAR", "HIDE", "STANDARD",
                List.of(), definition.slots().stream().map(ResumeLayoutDefinition.Slot::key).toList());

        ResumeLayoutDefinition applied = settings.applyTo(definition);

        assertThat(applied.tokens().get("accent.BLUE")).isEqualTo("#175CD3");
        assertThat(applied.tokens().get("surface.BLUE")).isEqualTo("#EAF0FB");
        assertThat(applied.tokens().get("fontPreset")).isEqualTo("MODERN_SANS");
    }

    private static List<String> signature(ResumeDesignSettings value) {
        return List.of(value.fontPreset(), value.fontScale(), value.lineHeight(), value.pageMargin(),
                value.dateFormat(), value.headerLayout(), value.headingStyle(), value.photoMode(), value.density());
    }

    private static String photoPolicy(String templateId) {
        return switch (templateId) {
            case "rlt-b-campus-v1", "rlt-b-career-pro-v1", "rlt-b-finance-v1",
                    "rlt-b-product-ops-v1", "rlt-b-education-research-v1", "rlt-b-cn-table-v1" -> "OPTIONAL";
            default -> "DISABLED";
        };
    }
}
