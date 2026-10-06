package com.jobproof.modules.resume.domain;

import com.jobproof.shared.error.AppException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 导入只出候选，不写正式事实。无真实模型。
 */
public final class ResumeImportHeuristic {

    public static final int MAX_TEXT_LENGTH = 50_000;
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "年龄", "性别", "婚姻", "婚育", "民族", "出生日期", "出生年月",
            "age", "gender", "marital", "ethnicity", "date of birth");
    private static final List<SectionMarker> SECTIONS = List.of(
            new SectionMarker(ResumeFieldKey.SELF_INTRO,
                    List.of("个人简介", "自我评价", "职业概况", "profile", "summary", "objective")),
            new SectionMarker(ResumeFieldKey.EDUCATION,
                    List.of("教育经历", "教育背景", "学历", "education")),
            new SectionMarker(ResumeFieldKey.EXPERIENCE,
                    List.of("工作经历", "实习经历", "任职经历", "职业经历", "experience", "employment")),
            new SectionMarker(ResumeFieldKey.PROJECTS,
                    List.of("项目经历", "项目经验", "projects", "project experience")),
            new SectionMarker(ResumeFieldKey.SKILLS,
                    List.of("专业技能", "技能", "技能清单", "skills", "technical skills")),
            new SectionMarker(ResumeFieldKey.CERTIFICATES,
                    List.of("证书", "证书与资质", "资质认证", "certificates", "certifications")),
            new SectionMarker(ResumeFieldKey.KEY_OUTCOMES,
                    List.of("关键成果", "工作成果", "主要业绩", "achievements", "accomplishments")));

    private ResumeImportHeuristic() {
    }

    public record CandidateDraft(ResumeFieldKey fieldKey, String proposedValue) {
    }

    public record ParseResult(List<CandidateDraft> drafts, int ignoredSensitiveLines, int ambiguousLines) {
        public ParseResult {
            drafts = List.copyOf(drafts);
        }
    }

    public static List<CandidateDraft> extract(String rawText) {
        return parse(rawText).drafts();
    }

    public static ParseResult parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw AppException.user("RESUME_IMPORT_TEXT_REQUIRED", "请粘贴已有简历文本");
        }
        if (rawText.length() > MAX_TEXT_LENGTH) {
            throw AppException.user("RESUME_IMPORT_TEXT_TOO_LARGE", "粘贴文本最长 50000 个字符");
        }
        Map<ResumeFieldKey, StringBuilder> sections = new LinkedHashMap<>();
        List<String> preface = new ArrayList<>();
        ResumeFieldKey current = null;
        int sensitive = 0;
        int ambiguous = 0;
        for (String line : rawText.trim().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (containsSensitiveMarker(trimmed)) {
                sensitive++;
                continue;
            }
            SectionMatch match = section(trimmed);
            if (match != null) {
                current = match.fieldKey();
                if (!match.inlineContent().isBlank()) {
                    append(sections, current, match.inlineContent());
                }
                continue;
            }
            if (current == null) {
                if (looksLikeContact(trimmed)) {
                    ambiguous++;
                } else {
                    preface.add(trimmed);
                }
            } else {
                append(sections, current, trimmed);
            }
        }

        if (!preface.isEmpty()) {
            String prefaceText = String.join("\n", preface);
            if (sections.isEmpty()) {
                sections.put(ResumeFieldKey.SELF_INTRO, new StringBuilder(prefaceText));
            } else {
                ambiguous += preface.size();
            }
        }
        if (sections.isEmpty()) {
            throw AppException.user("RESUME_IMPORT_NO_SUPPORTED_CONTENT", "没有识别到可导入的简历模块");
        }
        List<CandidateDraft> drafts = new ArrayList<>();
        for (SectionMarker marker : SECTIONS) {
            StringBuilder value = sections.get(marker.fieldKey());
            if (value != null && !value.toString().isBlank()) {
                drafts.add(new CandidateDraft(marker.fieldKey(), value.toString().trim()));
            }
        }
        return new ParseResult(drafts, sensitive, ambiguous);
    }

    private static SectionMatch section(String line) {
        String normalized = normalizeHeading(line);
        for (SectionMarker marker : SECTIONS) {
            for (String alias : marker.aliases()) {
                String normalizedAlias = normalizeHeading(alias);
                if (normalized.equals(normalizedAlias)) {
                    return new SectionMatch(marker.fieldKey(), "");
                }
                if (normalized.startsWith(normalizedAlias + ":")) {
                    int colon = firstColon(line);
                    return new SectionMatch(marker.fieldKey(), colon < 0 ? "" : line.substring(colon + 1).trim());
                }
            }
        }
        return null;
    }

    private static void append(Map<ResumeFieldKey, StringBuilder> sections, ResumeFieldKey key, String value) {
        StringBuilder target = sections.computeIfAbsent(key, ignored -> new StringBuilder());
        if (!target.isEmpty()) target.append('\n');
        target.append(value);
    }

    private static boolean containsSensitiveMarker(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace(" ", "");
        return SENSITIVE_MARKERS.stream().anyMatch(marker -> normalized.contains(marker.replace(" ", "")));
    }

    private static boolean looksLikeContact(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("@")
                || normalized.matches(".*(?:\\+?86[- ]?)?1[3-9]\\d{9}.*")
                || normalized.startsWith("电话")
                || normalized.startsWith("手机")
                || normalized.startsWith("邮箱")
                || normalized.startsWith("email")
                || normalized.startsWith("phone");
    }

    private static String normalizeHeading(String value) {
        return value.trim().toLowerCase(Locale.ROOT)
                .replace('：', ':')
                .replaceAll("[\\s【】\\[\\]（）()]", "");
    }

    private static int firstColon(String value) {
        int ascii = value.indexOf(':');
        int chinese = value.indexOf('：');
        if (ascii < 0) return chinese;
        if (chinese < 0) return ascii;
        return Math.min(ascii, chinese);
    }

    private record SectionMarker(ResumeFieldKey fieldKey, List<String> aliases) {}
    private record SectionMatch(ResumeFieldKey fieldKey, String inlineContent) {}
}
