package com.jobproof.modules.resumeimport.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative local parser for pasted resume text. It only splits what the text states (sections,
 * entries, dates, labelled contact fields); nothing is invented, and every result is confirmed by
 * the user in the workbench before it counts.
 *
 * <p>Entries: inside a timeline section a line carrying a date range starts a new entry (a date on
 * its own line attaches to the line above it). The entry's first line is split into fields
 * (school / major / degree, company / role, project / role); the remaining lines become its
 * description, one bullet per line.
 */
public final class ResumeContentParser {
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final String MONTH = "(?:1[0-2]|0?[1-9])";
    private static final String POINT = "(?:19|20)\\d{2}(?:\\s*[.\\-/年]\\s*" + MONTH + "\\s*月?)?";
    private static final Pattern DATE_RANGE = Pattern.compile("(" + POINT + ")\\s*(?:-|–|—|至|~|～|到)\\s*(" + POINT + "|至今|现在|今|present|now)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE_DATE = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})\\s*[.\\-/年]\\s*(" + MONTH + ")(?!\\d)\\s*月?");
    private static final Pattern YEAR_MONTH = Pattern.compile("((?:19|20)\\d{2})(?:\\s*[.\\-/年]\\s*(" + MONTH + "))?");
    private static final Pattern LABEL = Pattern.compile("^[^:：]{1,8}[:：]\\s*");
    private static final Pattern LOCATION = Pattern.compile("(?:所在城市|所在地|城市|现居|居住地|地址|location)\\s*[:：]\\s*([^|｜,，;；\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TARGET_JOB = Pattern.compile("(?:求职意向|目标岗位|应聘岗位|意向岗位|期望职位|求职方向)\\s*[:：]\\s*([^|｜;；]+?)(?=\\s{2,}|[|｜;；]|$)");
    private static final Pattern LINK = Pattern.compile("(?:https?://|(?:www\\.)?(?:github|gitee|gitlab|linkedin)\\.com/)[^\\s|｜,，;；]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD_SPLIT = Pattern.compile("\\s{2,}|\\t|[|｜/·•，,]");
    private static final Pattern LIST_SPLIT = Pattern.compile("[,，、;；|｜/]+");
    private static final Pattern BULLET = Pattern.compile("^[\\s•·●▪◦*\\-–]+");
    private static final Pattern DEGREE = Pattern.compile("^(?:本科|学士|硕士|研究生|博士|博士后|大专|专科|高中|MBA|EMBA|MPA|Bachelor.*|Master.*|PhD|Ph\\.D\\.?|B\\.S\\.?|M\\.S\\.?|B\\.A\\.?|M\\.A\\.?)$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> FALLBACK_SKILLS = Set.of("Java", "Python", "JavaScript", "TypeScript", "Vue", "React", "SQL", "MySQL",
            "Redis", "Spring Boot", "Spring", "Docker", "Kubernetes", "Linux", "Git", "Excel", "Power BI", "Tableau", "Spark", "Hadoop", "Kafka", "Go", "C++");
    private static final Map<String, String> HEADINGS = headings();

    private ResumeContentParser() {}

    public static Parsed parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        Map<String, List<Line>> sections = new LinkedHashMap<>();
        sections.put("HEADER", new ArrayList<>());
        String current = "HEADER";
        for (String source : text.replace("\r", "").split("\n")) {
            boolean bullet = BULLET.matcher(source).find() && !source.isBlank() && !Character.isLetterOrDigit(source.strip().charAt(0));
            String line = BULLET.matcher(source).replaceFirst("").trim();
            if (line.isEmpty()) continue;
            String heading = heading(line);
            if (heading != null) {
                current = heading;
                sections.computeIfAbsent(current, ignored -> new ArrayList<>());
                String inline = line.replaceFirst("^[^:：]{2,12}[:：]", "").trim();
                if (!inline.equals(line) && !inline.isEmpty()) sections.get(current).add(new Line(inline, false));
            } else {
                sections.computeIfAbsent(current, ignored -> new ArrayList<>()).add(new Line(line, bullet));
            }
        }

        List<String> header = sections.get("HEADER").stream().map(Line::text).toList();
        Map<String, Object> content = empty();
        content.put("basics", basics(header, text));
        String targetJob = firstGroup(TARGET_JOB, header);
        if (targetJob != null) content.put("intentions", new LinkedHashMap<>(Map.of("targetJob", targetJob)));
        content.put("summary", join(sections.get("SUMMARY")));
        content.put("education", timeline(sections.get("EDUCATION"), "education"));
        content.put("experiences", timeline(sections.get("EXPERIENCE"), "experience"));
        content.put("projects", timeline(sections.get("PROJECTS"), "projects"));
        content.put("organizations", timeline(sections.get("ORGANIZATIONS"), "organizations"));
        content.put("skills", skills(sections.get("SKILLS"), text));
        content.put("certificates", dated(sections.get("CERTIFICATES")));
        content.put("honors", dated(sections.get("HONORS")));
        content.put("languages", languages(sections.get("LANGUAGES")));

        Map<String, Object> sourceMap = new LinkedHashMap<>();
        sections.forEach((key, lines) -> sourceMap.put(key.toLowerCase(Locale.ROOT), lines.stream().map(Line::text).toList()));
        int populated = substantiveCount(content);
        Map<String, Object> confidence = Map.of(
                "parser", "resume-local-parser-v2",
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

    private static Map<String, Object> basics(List<String> header, String allText) {
        Map<String, Object> basics = new LinkedHashMap<>();
        Matcher email = EMAIL.matcher(allText);
        Matcher phone = PHONE.matcher(allText);
        for (String line : header) {
            String candidate = line.replaceFirst("^姓名\\s*[:：]\\s*", "").trim();
            if (EMAIL.matcher(candidate).find() || PHONE.matcher(candidate).find() || candidate.contains("：") || candidate.contains(":")) continue;
            if (!candidate.isEmpty() && candidate.length() <= 20 && !candidate.matches(".*\\d.*")) basics.put("name", candidate);
            break;
        }
        if (email.find()) basics.put("email", email.group());
        if (phone.find()) basics.put("phone", phone.group().replaceAll("^\\+?86[- ]?", ""));
        String location = firstGroup(LOCATION, header);
        if (location != null) basics.put("location", location);
        List<String> links = new ArrayList<>();
        for (String line : header) {
            Matcher link = LINK.matcher(line);
            while (link.find()) if (!links.contains(link.group())) links.add(link.group());
        }
        if (!links.isEmpty()) basics.put("links", links);
        return basics;
    }

    private static List<Map<String, Object>> timeline(List<Line> lines, String type) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<Entry> entries = new ArrayList<>();
        Entry current = null;
        for (Line line : lines) {
            Matcher range = DATE_RANGE.matcher(line.text());
            boolean dated = !line.bullet() && range.find();
            String rest = dated ? (line.text().substring(0, range.start()) + " " + line.text().substring(range.end())).trim() : line.text();
            if (dated && rest.isEmpty()) {
                // A date on its own line belongs to the header line just above it.
                if (current != null && current.start == null) {
                    current.dates(range);
                } else if (current != null && !current.details.isEmpty()) {
                    String header = current.details.remove(current.details.size() - 1);
                    current = new Entry(header);
                    current.dates(range);
                    entries.add(current);
                }
            } else if (dated) {
                current = new Entry(rest);
                current.dates(range);
                entries.add(current);
            } else if (current == null) {
                current = new Entry(line.text());
                entries.add(current);
            } else {
                current.details.add(line.text());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Entry entry : entries) result.add(entry.toItem(type));
        return List.copyOf(result);
    }

    /** Fields of an entry's first line, e.g. "浙江大学 计算机科学与技术 本科" or "Example University 软件工程". */
    static List<String> fields(String header) {
        String[] coarse = FIELD_SPLIT.split(header.trim());
        List<String> result = new ArrayList<>();
        for (String part : coarse) {
            String clean = part.trim();
            if (clean.isEmpty()) continue;
            // Single spaces separate Chinese fields but also join English words: keep Latin runs together.
            StringBuilder latin = new StringBuilder();
            for (String word : clean.split(" ")) {
                if (word.isBlank()) continue;
                boolean isLatin = word.codePoints().noneMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
                if (isLatin && !DEGREE.matcher(word).matches()) {
                    if (!latin.isEmpty()) latin.append(' ');
                    latin.append(word);
                } else {
                    if (!latin.isEmpty()) { result.add(latin.toString()); latin.setLength(0); }
                    result.add(word);
                }
            }
            if (!latin.isEmpty()) result.add(latin.toString());
        }
        return result;
    }

    private static List<Map<String, Object>> skills(List<Line> lines, String allText) {
        List<Map<String, Object>> groups = new ArrayList<>();
        List<String> ungrouped = new ArrayList<>();
        if (lines != null) {
            for (Line line : lines) {
                Matcher label = LABEL.matcher(line.text());
                String category = label.find() ? label.group().replaceAll("[:：\\s]", "") : null;
                String body = category == null ? line.text() : line.text().substring(label.end());
                List<String> items = new ArrayList<>();
                for (String token : LIST_SPLIT.split(body)) addUnique(items, token);
                if (items.isEmpty()) continue;
                if (category == null) items.forEach(item -> addUnique(ungrouped, item));
                else groups.add(group(category, items));
            }
        }
        if (!ungrouped.isEmpty()) groups.add(0, group("专业技能", ungrouped));
        if (groups.isEmpty() && lines == null) {
            // No skills section at all: offer the well-known tools the text names as whole words.
            List<String> found = new ArrayList<>();
            for (String skill : FALLBACK_SKILLS) {
                Pattern word = Pattern.compile("(?<![A-Za-z0-9+#])" + Pattern.quote(skill) + "(?![A-Za-z0-9+#])", Pattern.CASE_INSENSITIVE);
                if (word.matcher(allText).find()) addUnique(found, skill);
            }
            found.removeIf(skill -> found.stream().anyMatch(other -> !other.equals(skill) && other.toLowerCase(Locale.ROOT).contains(skill.toLowerCase(Locale.ROOT))));
            if (!found.isEmpty()) groups.add(group("专业技能", found));
        }
        return List.copyOf(groups);
    }

    private static Map<String, Object> group(String category, List<String> items) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("category", category);
        group.put("items", List.copyOf(items));
        return group;
    }

    /** Certificates and honors: one per line, with the date pulled out when the line states one. */
    private static List<Map<String, Object>> dated(List<Line> lines) {
        if (lines == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Line line : lines) {
            for (String part : LIST_SPLIT.split(line.text())) {
                String value = part.trim();
                if (value.isEmpty()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                Matcher date = SINGLE_DATE.matcher(value);
                if (date.find()) {
                    item.put("date", date.group(1) + "-" + String.format("%02d", Integer.parseInt(date.group(2))));
                    value = (value.substring(0, date.start()) + " " + value.substring(date.end())).trim();
                }
                if (value.isEmpty()) continue;
                item.put("name", value);
                result.add(item);
            }
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> languages(List<Line> lines) {
        if (lines == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Line line : lines) {
            for (String part : LIST_SPLIT.split(line.text())) {
                List<String> words = List.of(part.trim().split("\\s+"));
                if (words.isEmpty() || words.get(0).isEmpty()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("language", words.get(0));
                if (words.size() > 1) item.put("level", String.join(" ", words.subList(1, words.size())));
                result.add(item);
            }
        }
        return List.copyOf(result);
    }

    private static String heading(String line) {
        String clean = line.replaceAll("[\\s:：]+", "").toLowerCase(Locale.ROOT);
        String direct = HEADINGS.get(clean);
        if (direct != null) return direct;
        String prefix = line.split("[:：]", 2)[0].replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return line.matches("^[^:：]{2,12}[:：].*") ? HEADINGS.get(prefix) : null;
    }

    private static String firstGroup(Pattern pattern, List<String> lines) {
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) return matcher.group(1).trim();
        }
        return null;
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
        aliases(result, "SUMMARY", "个人简介", "自我评价", "个人总结", "职业概述", "summary", "profile");
        aliases(result, "EDUCATION", "教育经历", "教育背景", "教育", "education");
        aliases(result, "EXPERIENCE", "工作经历", "实习经历", "工作与实习", "工作经验", "实习经验", "experience", "workexperience");
        aliases(result, "PROJECTS", "项目经历", "项目经验", "projects", "projectexperience");
        aliases(result, "ORGANIZATIONS", "社团经历", "校园经历", "组织经历", "学生工作", "organizations");
        aliases(result, "SKILLS", "专业技能", "技能", "技能清单", "技术栈", "skills", "technicalskills");
        aliases(result, "CERTIFICATES", "证书", "资格证书", "certificates");
        aliases(result, "HONORS", "荣誉奖项", "获奖经历", "荣誉", "奖项", "honors", "awards");
        aliases(result, "LANGUAGES", "语言能力", "语言", "languages");
        return Map.copyOf(result);
    }

    private static void aliases(Map<String, String> map, String section, String... aliases) { for (String alias : aliases) map.put(alias, section); }
    private static String join(List<Line> values) { return values == null ? "" : String.join("\n", values.stream().map(Line::text).toList()); }
    private static void addUnique(List<String> values, String value) { String clean = value == null ? "" : value.trim(); if (!clean.isEmpty() && values.stream().noneMatch(item -> item.equalsIgnoreCase(clean))) values.add(clean); }

    /** "2019.9" / "2019年09月" / "2019" → "2019-09" / "2019". */
    static String month(String value) {
        Matcher matcher = YEAR_MONTH.matcher(value);
        if (!matcher.find()) return value.trim();
        return matcher.group(2) == null ? matcher.group(1) : matcher.group(1) + "-" + String.format("%02d", Integer.parseInt(matcher.group(2)));
    }

    private record Line(String text, boolean bullet) {}

    private static final class Entry {
        private final String header;
        private final List<String> details = new ArrayList<>();
        private String start;
        private String end;
        private boolean current;

        Entry(String header) { this.header = header; }

        void dates(Matcher range) {
            start = month(range.group(1));
            String to = range.group(2);
            if (to.matches("(?i)至今|现在|今|present|now")) current = true;
            else end = month(to);
        }

        Map<String, Object> toItem(String type) {
            List<String> parts = fields(header);
            Map<String, Object> item = new LinkedHashMap<>();
            String first = parts.isEmpty() ? header : parts.get(0);
            List<String> others = parts.size() > 1 ? parts.subList(1, parts.size()) : List.of();
            switch (type) {
                case "education" -> {
                    item.put("school", first);
                    String degree = others.stream().filter(part -> DEGREE.matcher(part).matches()).findFirst().orElse(null);
                    List<String> major = others.stream().filter(part -> !part.equals(degree)).toList();
                    if (!major.isEmpty()) item.put("major", String.join(" ", major));
                    if (degree != null) item.put("degree", degree);
                }
                case "experience" -> {
                    item.put("company", first);
                    if (!others.isEmpty()) item.put("role", String.join(" ", others));
                }
                default -> {
                    item.put("name", first);
                    if (!others.isEmpty()) item.put("role", String.join(" ", others));
                }
            }
            if (start != null) item.put("startDate", start);
            if (end != null) item.put("endDate", end);
            if (current) item.put("current", true);
            item.put("description", String.join("\n", details));
            return item;
        }
    }

    public record Parsed(Map<String, Object> content, Map<String, Object> sourceMap,
            Map<String, Object> confidence, boolean confirmable) {}
}
