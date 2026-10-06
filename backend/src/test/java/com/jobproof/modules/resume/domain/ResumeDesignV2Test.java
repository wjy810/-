package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.resume.application.BuiltInTemplateCatalog;
import org.junit.jupiter.api.Test;

class ResumeDesignV2Test {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BuiltInTemplateCatalog CATALOG = new BuiltInTemplateCatalog(MAPPER);

    private static ResumeTemplateManifest manifest(String id) {
        return CATALOG.find(id).orElseThrow().manifest();
    }

    private static ObjectNode json(String value) throws Exception {
        return (ObjectNode) MAPPER.readTree(value);
    }

    @Test
    void defaultsFollowTheManifest() {
        ResumeDesignV2 banker = ResumeDesignV2.defaults(manifest("banker"));
        assertThat(banker.pageTarget()).isEqualTo("ONE");
        assertThat(banker.fontSize()).isEqualTo("S");
        assertThat(banker.pageLimit(manifest("banker"))).isEqualTo(1);

        ResumeDesignV2 harvard = ResumeDesignV2.defaults(manifest("harvard"));
        assertThat(harvard.dateFormat()).isEqualTo("MMM YYYY");
        assertThat(harvard.contactIcons()).isFalse();
        assertThat(harvard.photo().mode()).isEqualTo("HIDE");

        ResumeDesignV2 meridian = ResumeDesignV2.defaults(manifest("meridian"));
        assertThat(meridian.sectionOrder()).hasSize(ResumeTemplateManifest.SECTION_KEYS.size())
                .startsWith("summary", "experience");
        assertThat(meridian.pageLimit(manifest("meridian"))).isEqualTo(2);
    }

    @Test
    void coerceRepairsAnythingStored() throws Exception {
        ResumeTemplateManifest meridian = manifest("meridian");
        ResumeDesignV2 value = ResumeDesignV2.coerce(meridian, json("""
                {"schemaVersion":"resume-design-v2","paletteId":"nope","fontSize":"HUGE","customAccent":"0F766E",
                 "sectionOrder":["skills","skills","bogus"],"sectionTitles":{"skills":"\\u0007技能清单一二三四五六七八九十一二三"},
                 "regionAssignments":{"skills":"main","projects":"nowhere"}}
                """));
        assertThat(value.paletteId()).isEqualTo(meridian.palettes().get(0).id());
        assertThat(value.fontSize()).isEqualTo("M");
        assertThat(value.customAccent()).isEqualTo("#0f766e");
        assertThat(value.sectionOrder().get(0)).isEqualTo("skills");
        assertThat(value.sectionOrder()).doesNotHaveDuplicates().hasSize(9);
        assertThat(value.sectionTitles().get("skills")).hasSize(16).doesNotContain("\u0007");
        assertThat(value.regionAssignments()).containsOnlyKeys("skills");
        assertThat(ResumeDesignV2.coerce(meridian, MAPPER.nullNode())).isEqualTo(ResumeDesignV2.defaults(meridian));
    }

    @Test
    void validateRejectsInsteadOfRepairing() throws Exception {
        ResumeTemplateManifest classic = manifest("classic");
        assertThatThrownBy(() -> ResumeDesignV2.validate(classic, json("{\"paletteId\":\"nope\"}")))
                .hasMessageContaining("配色");
        assertThatThrownBy(() -> ResumeDesignV2.validate(classic, json("{\"photo\":{\"mode\":\"SHOW\"}}")))
                .hasMessageContaining("照片");
        assertThatThrownBy(() -> ResumeDesignV2.validate(classic, json("{\"fontSize\":12}")))
                .hasMessageContaining("fontSize");
        assertThatThrownBy(() -> ResumeDesignV2.validate(classic, json("{\"sectionOrder\":[\"skills\",\"skills\"]}")))
                .hasMessageContaining("重复");
        assertThatThrownBy(() -> ResumeDesignV2.validate(classic, json("{\"schemaVersion\":\"resume-design-v9\"}")))
                .hasMessageContaining("版本");
        ResumeDesignV2 partial = ResumeDesignV2.validate(classic, json("{\"fontSize\":\"XL\",\"customAccent\":null}"));
        assertThat(partial.fontSize()).isEqualTo("XL");
        assertThat(partial.paletteId()).isEqualTo(classic.palettes().get(0).id());
    }

    @Test
    void legacySettingsConvertToTheMatchingPalette() throws Exception {
        ResumeTemplateManifest meridian = manifest("meridian");
        String pine = meridian.palettes().stream().filter(p -> p.id().equals("pine")).findFirst().orElseThrow().accent();
        ResumeDesignV2 value = ResumeDesignV2.coerce(meridian, json("""
                {"schemaVersion":"resume-design-v1","fontPreset":"CLASSIC_SERIF","fontScale":"LARGE","density":"COMPACT",
                 "lineHeight":"AIRY","dateFormat":"YYYY_CN_MM","photoMode":"HIDE","accentColor":"%s",
                 "hiddenSections":["honors"]}
                """.formatted(pine.toUpperCase())));
        assertThat(value.paletteId()).isEqualTo("pine");
        assertThat(value.customAccent()).isNull();
        assertThat(value.fontPairing()).isEqualTo("sans"); // serif is not offered by meridian
        assertThat(value.fontSize()).isEqualTo("L");
        assertThat(value.spacing()).isEqualTo("TIGHT");
        assertThat(value.lineHeight()).isEqualTo("RELAXED");
        assertThat(value.dateFormat()).isEqualTo("YYYY年MM月");
        assertThat(value.photo().mode()).isEqualTo("HIDE");
        assertThat(value.hiddenSections()).containsExactly("honors");
    }

    @Test
    void switchingTemplatesKeepsOnlyWhatTheUserChanged() throws Exception {
        ResumeTemplateManifest meridian = manifest("meridian");
        ResumeTemplateManifest banker = manifest("banker");
        JsonNode untouched = MAPPER.valueToTree(ResumeDesignV2.defaults(meridian));
        ResumeDesignV2 dense = ResumeDesignV2.carryOver(banker, untouched, meridian);
        assertThat(dense).isEqualTo(ResumeDesignV2.defaults(banker));

        ObjectNode changed = (ObjectNode) untouched.deepCopy();
        changed.put("fontSize", "XL").put("paletteId", "pine").put("customAccent", "#123456");
        changed.putArray("hiddenSections").add("languages");
        changed.putObject("sectionTitles").put("projects", "代表项目");
        changed.putObject("regionAssignments").put("skills", "main");
        ResumeDesignV2 carried = ResumeDesignV2.carryOver(banker, changed, meridian);
        assertThat(carried.fontSize()).isEqualTo("XL");
        assertThat(carried.spacing()).isEqualTo(ResumeDesignV2.defaults(banker).spacing());
        assertThat(carried.paletteId()).isEqualTo(banker.palettes().get(0).id());
        assertThat(carried.customAccent()).isEqualTo("#123456");
        assertThat(carried.hiddenSections()).containsExactly("languages");
        assertThat(carried.sectionTitles()).containsEntry("projects", "代表项目");
        assertThat(carried.regionAssignments()).isEmpty();
        assertThat(carried.sectionOrder()).isEqualTo(ResumeDesignV2.defaults(banker).sectionOrder());

        // English template: a Chinese date format chosen elsewhere does not follow.
        ObjectNode chineseDates = (ObjectNode) untouched.deepCopy();
        chineseDates.put("dateFormat", "YYYY年MM月");
        assertThat(ResumeDesignV2.carryOver(manifest("harvard"), chineseDates, meridian).dateFormat())
                .isEqualTo("MMM YYYY");
    }

    @Test
    void everyBuiltInDefaultPassesStrictValidation() {
        for (BuiltInTemplateCatalog.Entry entry : CATALOG.all()) {
            ResumeDesignV2 defaults = ResumeDesignV2.defaults(entry.manifest());
            assertThat(ResumeDesignV2.validate(entry.manifest(), MAPPER.valueToTree(defaults)))
                    .as(entry.manifest().id()).isEqualTo(defaults);
            assertThat(ResumeGenericLayout.definition(entry.manifest(), defaults))
                    .satisfies(definition -> ResumeLayoutProtocol.validate(ResumeLayoutProtocol.V2, definition));
        }
    }
}
