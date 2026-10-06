package com.jobproof.modules.resume.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Checks that an exported PDF reads correctly as text, the way applicant tracking systems read it
 * (EXP-03): the name, contact details, section titles and entry titles must be extractable, and
 * sections within one column must come out in their visual order.
 */
public final class ResumeAtsTextCheck {
    private static final List<String> TIMELINE_KEYS = List.of("experience", "projects", "education", "organizations");

    private ResumeAtsTextCheck() {
    }

    public record Expectation(String name, String email, String phone, Map<String, List<String>> sectionTitlesByRegion,
            List<String> entryTitles) {}

    public record Check(String code, boolean passed, String detail) {}

    public record Result(boolean passed, int pageCount, int textLength, List<Check> checks) {}

    public static Expectation expectation(ResumeTemplateManifest manifest, ResumeDesignV2 design, JsonNode content) {
        JsonNode basics = content.path("basics");
        Map<String, List<String>> titles = new LinkedHashMap<>();
        manifest.regions().forEach(region -> titles.put(region.id(), new ArrayList<>()));
        List<String> entries = new ArrayList<>();
        for (String key : design.visibleSections()) {
            if (!hasContent(content, key)) continue;
            String region = design.regionAssignments().getOrDefault(key, manifest.regionOf(key));
            if (!manifest.hasRegion(region)) region = manifest.regionOf(key);
            titles.computeIfAbsent(region, ignored -> new ArrayList<>())
                    .add(ResumeGenericLayout.title(manifest, design, key));
            if (TIMELINE_KEYS.contains(key)) {
                String first = firstEntryTitle(content, key);
                if (!first.isBlank()) entries.add(first);
            }
        }
        return new Expectation(basics.path("name").asText("").trim(), basics.path("email").asText("").trim(),
                basics.path("phone").asText("").trim(), titles, entries);
    }

    public static Result check(byte[] pdf, Expectation expectation) {
        String text;
        int pages;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            pages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            // Content-stream order: what a parser that ignores layout sees.
            stripper.setSortByPosition(false);
            text = stripper.getText(document);
        } catch (IOException exception) {
            return new Result(false, 0, 0, List.of(new Check("PDF_READABLE", false, "PDF 无法解析")));
        }
        String haystack = squash(text);
        List<Check> checks = new ArrayList<>();
        checks.add(new Check("TEXT_LAYER", haystack.length() > 20, haystack.length() > 20 ? null : "未提取到文字层"));
        if (!expectation.name().isBlank()) {
            checks.add(found("NAME", haystack, expectation.name(), "姓名"));
        }
        if (!expectation.email().isBlank()) {
            checks.add(found("EMAIL", haystack, expectation.email(), "邮箱"));
        }
        if (!expectation.phone().isBlank()) {
            checks.add(found("PHONE", haystack, expectation.phone(), "电话"));
        }
        List<String> missingTitles = new ArrayList<>();
        List<String> outOfOrder = new ArrayList<>();
        expectation.sectionTitlesByRegion().forEach((region, titles) -> {
            int previous = -1;
            for (String title : titles) {
                int index = haystack.indexOf(squash(title), Math.max(0, previous));
                if (index < 0) {
                    if (haystack.contains(squash(title))) outOfOrder.add(title);
                    else missingTitles.add(title);
                    continue;
                }
                previous = index;
            }
        });
        checks.add(new Check("SECTION_TITLES", missingTitles.isEmpty(),
                missingTitles.isEmpty() ? null : "未提取到板块标题：" + String.join("、", missingTitles)));
        checks.add(new Check("SECTION_ORDER", outOfOrder.isEmpty(),
                outOfOrder.isEmpty() ? null : "板块提取顺序与版面不一致：" + String.join("、", outOfOrder)));
        List<String> missingEntries = expectation.entryTitles().stream()
                .filter(title -> !haystack.contains(squash(title))).toList();
        checks.add(new Check("ENTRY_TITLES", missingEntries.isEmpty(),
                missingEntries.isEmpty() ? null : "未提取到条目标题：" + String.join("、", missingEntries)));
        boolean passed = checks.stream().allMatch(Check::passed);
        return new Result(passed, pages, text.length(), List.copyOf(checks));
    }

    private static Check found(String code, String haystack, String expected, String label) {
        boolean ok = haystack.contains(squash(expected));
        return new Check(code, ok, ok ? null : "未提取到" + label);
    }

    /** Whitespace-free, lower-case text: letter-spaced titles and wrapped phone numbers still match. */
    static String squash(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\u00a0\\u3000]+", "").toLowerCase(Locale.ROOT);
    }

    static boolean hasContent(JsonNode content, String key) {
        if ("summary".equals(key)) {
            JsonNode summary = content.path("summary");
            return summary.isTextual() ? !summary.asText().isBlank() : !summary.path("text").asText("").isBlank();
        }
        JsonNode list = content.path("experience".equals(key) ? "experiences" : key);
        if (!list.isArray()) return false;
        for (JsonNode item : list) {
            if (item.isTextual() && !item.asText().isBlank()) return true;
            if (item.isObject()) {
                var fields = item.fields();
                while (fields.hasNext()) {
                    JsonNode value = fields.next().getValue();
                    if ((value.isTextual() && !value.asText().isBlank()) || (value.isArray() && !value.isEmpty())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String firstEntryTitle(JsonNode content, String key) {
        JsonNode list = content.path("experience".equals(key) ? "experiences" : key);
        for (JsonNode item : list) {
            if (!item.isObject()) continue;
            String title = switch (key) {
                case "education" -> item.path("school").asText("");
                case "experience" -> item.path("company").asText("");
                default -> item.path("name").asText("");
            };
            if (!title.isBlank()) return title.trim();
        }
        return "";
    }
}
