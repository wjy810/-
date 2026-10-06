package com.jobproof.modules.resume.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** Deterministic renderer projection for canonical resume-content-v3 sections. */
public final class ResumeStructuredContent {
    private ResumeStructuredContent() {
    }

    public static String text(JsonNode content, String slotKey) {
        return text(content, slotKey, "YYYY_DOT_MM");
    }

    public static String text(JsonNode content, String slotKey, String dateFormat) {
        if (content == null || !content.isObject()) return "";
        String contentKey = "experience".equals(slotKey) ? "experiences" : slotKey;
        JsonNode value = content.path(contentKey);
        if (value.isMissingNode() || value.isNull()) return "";
        if (value.isTextual()) return value.asText("").trim();
        if (value.isObject()) {
            String direct = value.path("text").asText("").trim();
            return direct.isEmpty() ? value.toString() : direct;
        }
        if (!value.isArray()) return value.asText("").trim();
        List<String> blocks = new ArrayList<>();
        for (JsonNode item : value) {
            if (item.isTextual()) {
                if (!item.asText().isBlank()) blocks.add(item.asText().trim());
                continue;
            }
            String heading = heading(slotKey, item, dateFormat);
            String dates = dates(item, dateFormat);
            String location = item.path("location").asText("").trim();
            String meta = join(" · ", dates, location);
            List<String> lines = new ArrayList<>();
            add(lines, heading);
            add(lines, meta);
            add(lines, item.path("description").asText(""));
            JsonNode highlights = item.path("highlights");
            if (highlights.isArray()) highlights.forEach(line -> add(lines, "• " + line.asText("")));
            if ("skills".equals(slotKey) && item.path("items").isArray()) {
                List<String> skills = new ArrayList<>();
                item.path("items").forEach(skill -> add(skills, skill.asText("")));
                add(lines, String.join("、", skills));
            }
            String block = String.join("\n", lines);
            if (!block.isBlank()) blocks.add(block);
        }
        return String.join("\n\n", blocks);
    }

    public static ResumeDocumentModel.Header header(JsonNode content) {
        if (content == null || !content.isObject()) return ResumeDocumentModel.Header.empty();
        JsonNode basics = content.path("basics");
        JsonNode intentions = content.path("intentions");
        List<String> links = new ArrayList<>();
        JsonNode linkValues = basics.path("links");
        if (linkValues.isArray()) {
            for (JsonNode link : linkValues) {
                if (link.isTextual()) {
                    add(links, link.asText());
                } else if (link.isObject()) {
                    add(links, firstText(link, "url", "value", "href"));
                }
            }
        }
        return new ResumeDocumentModel.Header(
                basics.path("name").asText(""),
                intentions.path("targetJob").asText(""),
                basics.path("email").asText(""),
                basics.path("phone").asText(""),
                basics.path("location").asText(""),
                links);
    }

    public static int itemCount(JsonNode content, String slotKey) {
        if (content == null || !content.isObject()) return 0;
        String key = "experience".equals(slotKey) ? "experiences" : slotKey;
        JsonNode value = content.path(key);
        return value.isArray() ? value.size() : text(content, slotKey).isBlank() ? 0 : 1;
    }

    public static List<ResumeDocumentModel.Entry> entries(JsonNode content, String slotKey, String dateFormat) {
        if (content == null || !content.isObject() || !isTimeline(slotKey)) return List.of();
        String key = "experience".equals(slotKey) ? "experiences" : slotKey;
        JsonNode value = content.path(key);
        if (!value.isArray()) return List.of();
        List<ResumeDocumentModel.Entry> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isObject()) continue;
            String primary;
            String secondary;
            switch (slotKey) {
                case "education" -> {
                    primary = item.path("school").asText("");
                    secondary = join(" · ", item.path("major").asText(""), item.path("degree").asText(""));
                }
                case "experience" -> {
                    primary = item.path("company").asText("");
                    secondary = join(" · ", item.path("role").asText(""), item.path("department").asText(""));
                }
                case "projects", "organizations" -> {
                    primary = item.path("name").asText("");
                    secondary = join(" · ", item.path("role").asText(""), item.path("department").asText(""));
                }
                default -> throw new IllegalStateException("Unexpected timeline slot: " + slotKey);
            }
            List<String> highlights = new ArrayList<>();
            JsonNode highlightValues = item.path("highlights");
            if (highlightValues.isArray()) {
                highlightValues.forEach(line -> add(highlights, line.asText("")));
            }
            ResumeDocumentModel.Entry entry = new ResumeDocumentModel.Entry(
                    primary,
                    secondary,
                    dates(item, dateFormat),
                    item.path("location").asText(""),
                    item.path("description").asText(""),
                    highlights);
            if (!entry.isEmpty()) result.add(entry);
        }
        return List.copyOf(result);
    }

    private static boolean isTimeline(String slotKey) {
        return "education".equals(slotKey) || "experience".equals(slotKey)
                || "projects".equals(slotKey) || "organizations".equals(slotKey);
    }

    private static String heading(String slotKey, JsonNode item, String dateFormat) {
        return switch (slotKey) {
            case "education" -> join(" · ", item.path("school").asText(), item.path("major").asText(),
                    item.path("degree").asText());
            case "experience" -> join(" · ", item.path("company").asText(), item.path("role").asText());
            case "projects", "organizations" -> join(" · ", item.path("name").asText(),
                    item.path("role").asText());
            case "skills" -> join(" · ", item.path("category").asText(), item.path("name").asText());
            case "certificates", "honors" -> join(" · ", item.path("name").asText(),
                    item.path("issuer").asText(), formatDate(item.path("date").asText(), dateFormat));
            case "languages" -> join(" · ", item.path("language").asText(),
                    item.path("level").asText(), item.path("score").asText());
            default -> item.path("text").asText("");
        };
    }

    private static String dates(JsonNode item, String dateFormat) {
        String start = formatDate(item.path("startDate").asText(""), dateFormat);
        String end = item.path("current").asBoolean(false) ? "至今"
                : formatDate(item.path("endDate").asText(""), dateFormat);
        return join(" - ", start, end);
    }

    private static String formatDate(String value, String dateFormat) {
        String text = value == null ? "" : value.trim();
        if (!text.matches("\\d{4}-\\d{2}")) return text;
        String[] parts = text.split("-");
        return "YYYY_CN_MM".equalsIgnoreCase(dateFormat)
                ? parts[0] + "年" + Integer.parseInt(parts[1]) + "月"
                : parts[0] + "." + parts[1];
    }

    private static String join(String separator, String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) add(result, value);
        return String.join(separator, result);
    }

    private static void add(List<String> values, String value) {
        if (value != null && !value.trim().isEmpty() && !"•".equals(value.trim())) values.add(value.trim());
    }

    private static String firstText(JsonNode value, String... keys) {
        for (String key : keys) {
            String text = value.path(key).asText("").trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }
}
