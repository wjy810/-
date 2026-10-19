package com.jobproof.modules.jobmatch.domain;

import com.jobproof.modules.jobmatch.domain.JobMatchScoring.Verdict;
import java.util.List;
import java.util.Map;

/**
 * What a clarification answer means for its requirement. The model gives each option it writes a verdict
 * ("仅 SQL 有实际案例" → PARTIAL); that verdict wins. Options without one (older questions, the built-in
 * fallback set) are read from their wording.
 */
public final class ClarificationVerdicts {
    private ClarificationVerdicts() {}

    public static final String DEFER = "暂不确认";

    /** Built-in options for a question the model sent without any. */
    public static final List<String> FALLBACK_OPTIONS = List.of("有真实使用经验", "了解但没有项目经验", "正在学习", "不会", DEFER);

    private static final List<String> NEGATIVE = List.of("不了解", "不熟悉", "不会", "没用过", "没有接触", "未接触", "从未");
    private static final List<String> PARTIAL = List.of("仅", "只有", "其中", "其他", "了解", "学习", "部分", "基础", "接触", "没有项目", "暂无实践");
    private static final List<String> ABSENT = List.of("尚无", "暂无", "没有", "未涉及", "未使用", "不属于", "不涉及");

    public static Verdict of(String answer, Map<String, String> optionVerdicts) {
        if (answer != null && optionVerdicts != null) {
            Verdict given = Verdict.parse(optionVerdicts.get(answer));
            if (given != null) return given;
        }
        return of(answer);
    }

    /** "有真实使用经验" → MET, "仅学习过，暂无实践" → PARTIAL, "尚无实际案例" → NOT_FOUND, "暂不确认" → UNCLEAR. */
    public static Verdict of(String answer) {
        String text = answer == null ? "" : answer.replaceAll("\\s+", "");
        if (text.isEmpty() || "null".equals(text) || text.contains(DEFER) || text.contains("不确定")) return Verdict.UNCLEAR;
        if (containsAny(text, NEGATIVE)) return Verdict.NOT_FOUND;
        if (containsAny(text, PARTIAL)) return Verdict.PARTIAL;
        if (text.startsWith("不") || text.startsWith("没") || text.startsWith("无") || text.startsWith("未")
                || containsAny(text, ABSENT)) return Verdict.NOT_FOUND;
        return Verdict.MET;
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }
}
