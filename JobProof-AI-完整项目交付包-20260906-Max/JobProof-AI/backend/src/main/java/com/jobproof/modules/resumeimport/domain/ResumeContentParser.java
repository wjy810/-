package com.jobproof.modules.resumeimport.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative local parser. Ambiguous text stays in a description for user confirmation. */
public final class ResumeContentParser {
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern DATE_RANGE = Pattern.compile("((?:19|20)\\d{2}(?:[.\\-/年](?:0?[1-9]|1[0-2])月?)?)\\s*(?:-|—|至|~|～)\\s*((?:19|20)\\d{2}(?:[.\\-/年](?:0?[1-9]|1[0-2])月?)?|至今|现在)");
    private static final Set<String> TECH = Set.of("java", "python", "javascript", "typescript", "vue", "react", "sql", "mysql", "redis", "spring", "spring boot", "docker", "kubernetes", "linux", "git", "excel", "power bi", "tableau", "spark", "hadoop", "kafka");
    private static final Map<String, String> HEADINGS = headings();

    private ResumeContentParser() {}

    public static Parsed parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("HEADER", new ArrayList<>());
        String current = "HEADER";
        for (String source : text.replace("\r", "").split("\n")) {
            String line = source.replaceFirst("^[\\s•·●▪*-]+", "").trim();
            if (line.isEmpty()) continue;
            String heading = heading(line);
            if (heading != null) {
                current = heading;
                sections.computeIfAbsent(current, ignored -> new ArrayList<>());
                String inline = line.replaceFirst("^[^:：]{2,12}[:：]", "").trim();
                if (!inline.equals(line) && !inline.isEmpty()) sections.get(current).add(inline);
            } else {
                sections.computeIfAbsent(current, ignored -> new ArrayList<>()).add(line);
            }
        }

        Map<String, Object> basics = new LinkedHashMap<>();
        Matcher email = EMAIL.matcher(text);
        Matcher phone = PHONE.matcher(text);
        if (email.find()) basics.put("email", email.group());
        if (phone.find()) basics.put("phone", phone.group());
        List<String> header = sections.getOrDefault("HEADER", List.of());
        if (!header.isEmpty() && header.get(0).length() <= 24 && !header.get(0).contains("@")) basics.put("name", header.get(0));

        Map<String, Object> content = empty();
        content.put("basics", basics);
        content.put("summary", join(sections.get("SUMMARY")));
        content.put("education", timeline(sections.get("EDUCATION"), "education"));
        content.put("experiences", timeline(sections.get("EXPERIENCE"), "experience"));
        content.put("projects", timeline(sections.get("PROJECTS"), "projects"));
        content.put("organizations", timeline(sections.get("ORGANIZATIONS"), "organizations"));
        content.put("skills", skills(sections.get("SKILLS"), text));
        content.put("certificates", simple(sections.get("CERTIFICATES"), "name"));
        content.put("honors", simple(sections.get("HONORS"), "name"));
        content.put("languages", simple(sections.get("LANGUAGES"), "language"));

        Map<String, Object> sourceMap = new LinkedHashMap<>();
        sections.forEach((key, lines) -> sourceMap.put(key.toLowerCase(Locale.ROOT), List.copyOf(lines)));
        int populated = substantiveCount(content);
        Map<String, Object> confidence = Map.of(
                "parser", "resume-local-parser-v1",
                "substantiveSections", populated,
                "requiresUserConfirmation", true);
        return new Parsed(content, sourceMap, confidence, populated > 0);
    }

    public static int substantiveCount(Map<String, Object> content) {
        int count = 0;
        for (String key : List.of("education", "experiences", "projects", "skills")) {
            if (content.get(key) instanceof List<?> values && !values.isEmpty()) count++;
        }
        return count;
    }

    private static List<Map<String, Object>> timeline(List<String> values, String type) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> clean = values.stream().filter(value -> !value.isBlank()).toList();
        if (clean.isEmpty()) return List.of();
        Map<String, Object> item = new LinkedHashMap<>();
        String first = clean.get(0);
        if ("education".equals(type)) item.put("school", first);
        else if ("experience".equals(type)) item.put("company", first);
        else item.put("name", first);
        Matcher dates = DATE_RANGE.matcher(String.join(" ", clean));
        if (dates.find()) {
            item.put("startDate", month(dates.group(1)));
            if (Set.of("至今", "现在").contains(dates.group(2))) item.put("current", true);
            else item.put("endDate", month(dates.group(2)));
        }
        List<String> highlights = clean.stream().skip(1).filter(line -> !DATE_RANGE.matcher(line).matches()).toList();
        if (!highlights.isEmpty()) item.put("highlights", highlights);
        item.put("description", String.join("\n", highlights));
        return List.of(item);
    }

    private static List<Map<String, Object>> skills(List<String> values, String allText) {
        List<String> found = new ArrayList<>();
        if (values != null) {
            for (String line : values) for (String token : line.split("[,，、;；|/]+")) addUnique(found, token);
        }
        String lower = allText.toLowerCase(Locale.ROOT);
        for (String tech : TECH) if (lower.contains(tech)) addUnique(found, tech);
        if (found.isEmpty()) return List.of();
        return List.of(Map.of("category", "专业技能", "items", found, "description", String.join("、", found)));
    }

    private static List<Map<String, Object>> simple(List<String> values, String field) {
        if (values == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String value : values) if (!value.isBlank()) result.add(Map.of(field, value, "description", value));
        return List.copyOf(result);
    }

    private static String heading(String line) {
        String clean = line.replaceAll("[\\s:：]+", "").toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : HEADINGS.entrySet()) {
            if (clean.equals(entry.getKey()) || clean.startsWith(entry.getKey() + "：") || clean.startsWith(entry.getKey() + ":")) return entry.getValue();
        }
        String prefix = line.split("[:：]", 2)[0].replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return HEADINGS.get(prefix);
    }

    private static Map<String, Object> empty() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "resume-content-v3");
        result.put("basics", new LinkedHashMap<>());
        result.put("intentions", new LinkedHashMap<>());
        result.put("summary", "");
        for (String key : List.of("education", "experiences", "projects", "organizations", "skills", "certificates", "honors", "languages", "evidence")) result.put(key, List.of());
        result.put("photoFileId", null);
        return result;
    }

    private static Map<String, String> headings() {
        Map<String, String> result = new LinkedHashMap<>();
        aliases(result, "SUMMARY", "个人简介", "自我评价", "职业概述", "summary", "profile");
        aliases(result, "EDUCATION", "教育经历", "教育背景", "教育", "education");
        aliases(result, "EXPERIENCE", "工作经历", "实习经历", "工作与实习", "experience", "workexperience");
        aliases(result, "PROJECTS", "项目经历", "项目经验", "projects", "projectexperience");
        aliases(result, "ORGANIZATIONS", "社团经历", "校园经历", "组织经历", "organizations");
        aliases(result, "SKILLS", "专业技能", "技能", "技能清单", "skills", "technicalskills");
        aliases(result, "CERTIFICATES", "证书", "资格证书", "certificates");
        aliases(result, "HONORS", "荣誉奖项", "荣誉", "奖项", "honors", "awards");
        aliases(result, "LANGUAGES", "语言能力", "语言", "languages");
        return Map.copyOf(result);
    }

    private static void aliases(Map<String, String> map, String section, String... aliases) { for (String alias : aliases) map.put(alias, section); }
    private static String join(List<String> values) { return values == null ? "" : String.join("\n", values); }
    private static String month(String value) { return value.replace('年', '-').replace("月", "").replace('.', '-').replace('/', '-'); }
    private static void addUnique(List<String> values, String value) { String clean = value == null ? "" : value.trim(); if (!clean.isEmpty() && values.stream().noneMatch(item -> item.equalsIgnoreCase(clean))) values.add(clean); }

    public record Parsed(Map<String, Object> content, Map<String, Object> sourceMap,
            Map<String, Object> confidence, boolean confirmable) {}
}
