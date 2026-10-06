package com.jobproof.modules.resume.domain;

import java.util.List;
import java.util.Map;

public record ResumeLayoutDefinition(
        Page page,
        List<Column> columns,
        List<Slot> slots,
        Map<String, String> tokens,
        Visual visual,
        DesignCapabilities designCapabilities) {

    public ResumeLayoutDefinition(Page page, List<Column> columns, List<Slot> slots,
            Map<String, String> tokens, Visual visual) {
        this(page, columns, slots, tokens, visual, null);
    }

    public ResumeLayoutDefinition(Page page, List<Column> columns, List<Slot> slots, Map<String, String> tokens) {
        this(page, columns, slots, tokens, null, null);
    }

    public record Page(
            int maxPages,
            int capacityUnits,
            int marginXPt,
            int marginTopPt,
            int marginBottomPt) {

        public Page(int maxPages, int capacityUnits) {
            this(maxPages, capacityUnits, 0, 0, 0);
        }

        public int effectiveMarginXPt() { return marginXPt > 0 ? marginXPt : 54; }
        public int effectiveMarginTopPt() { return marginTopPt > 0 ? marginTopPt : 56; }
        public int effectiveMarginBottomPt() { return marginBottomPt > 0 ? marginBottomPt : 48; }
    }

    public record Column(String id, int widthPercent, List<String> slotKeys, String tone) {
        public Column(String id, int widthPercent, List<String> slotKeys) {
            this(id, widthPercent, slotKeys, "PLAIN");
        }

        public String effectiveTone() { return tone == null || tone.isBlank() ? "PLAIN" : tone; }
    }

    public record Slot(
            String key,
            int order,
            int capacityUnits,
            boolean repeatable,
            boolean hideWhenEmpty,
            String label,
            String headingStyle) {

        public Slot(String key, int order, int capacityUnits, boolean repeatable, boolean hideWhenEmpty) {
            this(key, order, capacityUnits, repeatable, hideWhenEmpty, null, null);
        }

        public String effectiveLabel() { return label == null || label.isBlank() ? key : label; }
    }

    public record Visual(
            String headerStyle,
            String sectionStyle,
            String subtitle,
            boolean showMark,
            String density) {

        public String effectiveHeaderStyle() {
            return headerStyle == null || headerStyle.isBlank() ? "MINIMAL" : headerStyle;
        }

        public String effectiveSectionStyle() {
            return sectionStyle == null || sectionStyle.isBlank() ? "RULE" : sectionStyle;
        }

        public String effectiveSubtitle() {
            return subtitle == null || subtitle.isBlank() ? "JobProof 结构化简历" : subtitle;
        }

        public String effectiveDensity() {
            return density == null || density.isBlank() ? "STANDARD" : density;
        }
    }

    public record DesignCapabilities(
            List<String> fontPresets,
            List<String> fontScales,
            List<String> lineHeights,
            List<String> pageMargins,
            List<String> accentColors,
            List<String> dateFormats,
            List<String> headerLayouts,
            List<String> headingStyles,
            List<String> photoModes,
            List<String> densities,
            List<String> sectionKeys) {
    }
}
