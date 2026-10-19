package com.jobproof.modules.jobmatch.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Key terms of one JD requirement for the rule preview used when AI is unavailable: Latin tokens (SQL, C++,
 * Node.js) and the Chinese phrases left after removing the filler words a JD wraps them in. "熟练使用SQL进行数据查询与分析"
 * becomes [sql, 数据查询, 分析], so the resume is matched on what the requirement is about, not on the sentence.
 */
public final class RequirementTerms {
    private RequirementTerms() {}

    private static final Pattern LATIN = Pattern.compile("[a-z][a-z0-9+#.]*[a-z0-9+#]|[a-z]", Pattern.CASE_INSENSITIVE);
    private static final List<String> FILLERS = List.of(
            "熟练掌握", "熟练使用", "熟练运用", "熟悉使用", "能够熟练", "具有", "具备", "拥有", "熟练", "掌握", "熟悉", "精通", "了解",
            "使用", "运用", "能够", "可以", "进行", "负责", "参与", "相关", "良好的", "良好", "较强的", "较强", "优秀的", "优秀",
            "一定的", "以上", "以下", "优先考虑", "优先", "从事", "工作", "能力", "经验", "要求", "岗位", "方面", "基础", "知识",
            "技能",
            // Single-character joiners last, once the words that contain them are gone.
            "等", "及", "和", "与", "或", "并", "的");
    private static final Pattern SPLIT = Pattern.compile("[\\s,，。；;、/（）()【】\\[\\]：:！!？?\"'“”‘’·|]+");
    private static final Set<String> LATIN_STOPWORDS = Set.of("and", "or", "the", "of", "with", "in", "to", "for", "a", "an");

    public static List<String> of(String requirement) {
        if (requirement == null || requirement.isBlank()) return List.of();
        String text = requirement.toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        Matcher latin = LATIN.matcher(text);
        while (latin.find()) {
            String token = latin.group();
            if (token.length() >= 2 && !LATIN_STOPWORDS.contains(token)) terms.add(token);
        }
        String chinese = LATIN.matcher(text).replaceAll(" ").replaceAll("[0-9]+(年|个|项|种|门|次)?", " ");
        for (String filler : FILLERS) chinese = chinese.replace(filler, " ");
        for (String part : SPLIT.split(chinese)) {
            String phrase = part.trim();
            if (phrase.codePointCount(0, phrase.length()) >= 2 && phrase.codePoints().allMatch(RequirementTerms::isHan)) {
                terms.add(phrase);
            }
        }
        return List.copyOf(terms);
    }

    /** Whole-value matching: true when {@code haystack} (already lower-cased resume or evidence text) has the term. */
    public static boolean present(String term, String haystack) {
        if (term.codePoints().allMatch(RequirementTerms::isHan)) return haystack.contains(term);
        // Latin terms must stand alone, so "r" does not match every word and "java" does not match "javascript".
        Pattern word = Pattern.compile("(?<![a-z0-9+#])" + Pattern.quote(term) + "(?![a-z0-9+#])");
        return word.matcher(haystack).find();
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }
}
