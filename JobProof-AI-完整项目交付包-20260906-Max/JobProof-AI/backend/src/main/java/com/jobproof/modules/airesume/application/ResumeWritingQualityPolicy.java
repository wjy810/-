package com.jobproof.modules.airesume.application;

import com.jobproof.shared.error.AppException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ResumeWritingQualityPolicy {
    public static final String VERSION = "resume-writing-v1";

    private static final Pattern BULLET_PREFIX = Pattern.compile("^[\\s•·●▪\\-]+", Pattern.UNICODE_CASE);
    private static final Pattern HOLLOW_PHRASES = Pattern.compile(
            "(?iu)(?:负责相关工作|完成相关任务|推进相关事项|保障工作开展|提升工作效率|取得良好效果)");
    private static final Set<String> ACTION_MARKERS = Set.of(
            "设计", "开发", "实现", "分析", "搭建", "优化", "编写", "组织", "协调", "维护", "验证", "测试",
            "交付", "梳理", "制定", "解决", "支持", "管理", "调研", "使用", "应用", "完成", "主导", "参与");
    private static final Set<String> METHOD_MARKERS = Set.of(
            "通过", "采用", "基于", "结合", "利用", "借助", "按照", "围绕", "使用", "应用", "协同", "拆分");
    private static final Set<String> RESULT_MARKERS = Set.of(
            "实现", "完成", "交付", "形成", "支持", "确保", "降低", "提升", "解决", "验证", "上线", "沉淀");

    public QualityResult validate(String module, String targetPath, String value, boolean completeGeneration) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) throw invalid("AI_CHANGE_VALUE_EMPTY", "AI 修改内容不能为空");
        List<String> items = splitItems(text);
        if ("SUMMARY".equals(module)) {
            assertRange(text, 120, 220, 60, 110, "个人简介");
        } else {
            Range range = range(module);
            for (int index = 0; index < items.size(); index++) {
                String item = items.get(index);
                assertRange(item, range.zhMin(), range.zhMax(), range.enMin(), range.enMax(),
                        label(module) + "第 " + (index + 1) + " 条");
                assertSubstance(item, module, index);
            }
            if (completeGeneration && Set.of("EXPERIENCE", "PROJECTS").contains(module)
                    && (items.size() < 3 || items.size() > 5)) {
                throw invalid("AI_CHANGE_BULLET_COUNT_INVALID", label(module) + "完整生成必须提供 3 至 5 条有效要点");
            }
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("policyVersion", VERSION);
        metrics.put("module", module);
        metrics.put("targetPath", targetPath);
        metrics.put("itemCount", items.size());
        metrics.put("meaningfulCharacters", meaningfulCharacters(text));
        metrics.put("englishWords", englishWords(text));
        metrics.put("passed", true);
        return new QualityResult(true, Map.copyOf(metrics));
    }

    public void assertDistinct(List<String> values) {
        List<String> normalized = values.stream().map(ResumeWritingQualityPolicy::normalize).toList();
        for (int left = 0; left < normalized.size(); left++) {
            for (int right = left + 1; right < normalized.size(); right++) {
                String a = normalized.get(left);
                String b = normalized.get(right);
                if (a.equals(b) || (a.length() >= 24 && b.length() >= 24
                        && similarity(a, b) >= 0.82d)) {
                    throw invalid("AI_CHANGE_DUPLICATE", "同一批修改中存在重复表达");
                }
            }
        }
    }

    private static void assertRange(String value, int zhMin, int zhMax, int enMin, int enMax, String label) {
        if (isMostlyEnglish(value)) {
            int words = englishWords(value);
            if (words < enMin || words > enMax) {
                throw invalid("AI_CHANGE_LENGTH_INVALID", label + "需要 " + enMin + " 至 " + enMax
                        + " 个英文单词，当前为 " + words + " 个");
            }
            return;
        }
        int characters = meaningfulCharacters(value);
        if (characters < zhMin || characters > zhMax) {
            throw invalid("AI_CHANGE_LENGTH_INVALID", label + "需要 " + zhMin + " 至 " + zhMax
                    + " 个有效字符，当前为 " + characters + " 个");
        }
    }

    private static void assertSubstance(String value, String module, int index) {
        if (HOLLOW_PHRASES.matcher(value).find()) {
            throw invalid("AI_CHANGE_HOLLOW_PHRASE", label(module) + "第 " + (index + 1) + " 条包含空泛表达");
        }
        if (isMostlyEnglish(value)) return;
        int dimensions = containsAny(value, ACTION_MARKERS) ? 1 : 0;
        dimensions += containsAny(value, METHOD_MARKERS) ? 1 : 0;
        dimensions += containsAny(value, RESULT_MARKERS) ? 1 : 0;
        dimensions += (value.contains("，") || value.contains(",") || value.contains("；")) ? 1 : 0;
        if (dimensions < 3) {
            throw invalid("AI_CHANGE_STRUCTURE_WEAK", label(module) + "第 " + (index + 1)
                    + " 条需要包含行动、对象、方法、结果或验证中的至少三项");
        }
    }

    private static Range range(String module) {
        return switch (module) {
            case "EXPERIENCE" -> new Range(45, 100, 18, 35);
            case "PROJECTS" -> new Range(50, 110, 20, 40);
            case "EDUCATION", "ORGANIZATIONS", "LANGUAGES" -> new Range(40, 90, 16, 32);
            case "SKILLS", "CERTIFICATES", "HONORS" -> new Range(30, 80, 12, 28);
            default -> throw invalid("AI_CHANGE_MODULE_INVALID", "不支持修改该简历模块");
        };
    }

    private static List<String> splitItems(String value) {
        List<String> result = new ArrayList<>();
        for (String line : value.replace("\\r", "").split("\\n+")) {
            String clean = BULLET_PREFIX.matcher(line).replaceFirst("").trim();
            if (!clean.isEmpty()) result.add(clean);
        }
        if (result.isEmpty()) result.add(BULLET_PREFIX.matcher(value).replaceFirst("").trim());
        return List.copyOf(result);
    }

    private static int meaningfulCharacters(String value) {
        return (int) value.codePoints().filter(Character::isLetterOrDigit).count();
    }

    private static int englishWords(String value) {
        String clean = BULLET_PREFIX.matcher(value).replaceFirst("").trim();
        if (clean.isEmpty()) return 0;
        return (int) Pattern.compile("[A-Za-z0-9][A-Za-z0-9+#.\\-/]*")
                .matcher(clean).results().count();
    }

    private static boolean isMostlyEnglish(String value) {
        long letters = value.codePoints().filter(Character::isLetter).count();
        long latin = value.codePoints().filter(code -> code < 128 && Character.isLetter(code)).count();
        return letters > 0 && latin * 100 / letters >= 75;
    }

    private static boolean containsAny(String value, Set<String> markers) {
        return markers.stream().anyMatch(value::contains);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。；、]+", "");
    }

    private static double similarity(String left, String right) {
        Set<Integer> a = bigrams(left);
        Set<Integer> b = bigrams(right);
        long common = a.stream().filter(b::contains).count();
        return a.isEmpty() || b.isEmpty() ? 0d : (2d * common) / (a.size() + b.size());
    }

    private static Set<Integer> bigrams(String value) {
        java.util.LinkedHashSet<Integer> result = new java.util.LinkedHashSet<>();
        for (int index = 0; index + 1 < value.length(); index++) {
            result.add(value.substring(index, index + 2).hashCode());
        }
        return Set.copyOf(result);
    }

    private static String label(String module) {
        return switch (module) {
            case "EXPERIENCE" -> "工作经历";
            case "PROJECTS" -> "项目经历";
            case "EDUCATION" -> "教育经历";
            case "ORGANIZATIONS" -> "组织经历";
            case "SKILLS" -> "技能说明";
            case "CERTIFICATES" -> "证书说明";
            case "HONORS" -> "荣誉说明";
            case "LANGUAGES" -> "语言说明";
            default -> "简历内容";
        };
    }

    private static AppException invalid(String reason, String message) {
        return AppException.conflict(reason, message);
    }

    private record Range(int zhMin, int zhMax, int enMin, int enMax) {}
    public record QualityResult(boolean passed, Map<String, Object> metrics) {}
}
