package com.jobproof.modules.jobmatch.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 岗位匹配评分 (docs/phase2/07)：分数只由“每条岗位要求的判定”算出，不看字数、不看字段名。
 *
 * <ul>
 *   <li>判定：MET 符合 100、PARTIAL 部分符合 60、UNCLEAR 无法判断 35、NOT_FOUND 暂未发现证据 0；</li>
 *   <li>权重：必须（MUST）3、重要（IMPORTANT）2、加分（BONUS）1；岗位职责只说明这份工作做什么，按 1 计入“经验”；</li>
 *   <li>维度按要求类别分组，没有该类要求的维度不给分（null），而不是编一个数；</li>
 *   <li>硬性要求暂未发现证据时，投递建议最高只能是“谨慎评估”；</li>
 *   <li>可信度看有多少条判定有引用来源，越多“无法判断”越低。</li>
 * </ul>
 */
public final class JobMatchScoring {
    private JobMatchScoring() {}

    public enum Verdict {
        MET(100), PARTIAL(60), UNCLEAR(35), NOT_FOUND(0);

        public final int value;

        Verdict(int value) { this.value = value; }

        public static Verdict parse(String value) {
            if (value == null) return null;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    public static final Map<String, Integer> PRIORITY_WEIGHTS = Map.of("MUST", 3, "IMPORTANT", 2, "BONUS", 1);
    public static final String METHOD_AI = "AI_VERDICT";
    public static final String METHOD_RULES = "RULE_PREVIEW";

    public record Requirement(String id, String text, String category, String priority, boolean hardGate) {}

    /**
     * One requirement's verdict and what backs it. {@code userConfirmed}: backed only by the user's own answer
     * to a clarification question, not by the resume or the evidence library.
     */
    public record Judgement(String requirementId, Verdict verdict, List<String> resumeRefs, List<String> evidenceIds,
            boolean userConfirmed, String explanation) {
        public Judgement {
            resumeRefs = resumeRefs == null ? List.of() : List.copyOf(resumeRefs);
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }

        public boolean cited() { return !resumeRefs.isEmpty() || !evidenceIds.isEmpty() || userConfirmed; }
    }

    public record HardGate(String requirementId, String text, String status, String explanation) {}

    public record Result(String method, int overall, Map<String, Integer> dimensions, String confidence,
            int citedCount, int requirementCount, boolean hardGatePassed, int hardGapCount, String recommendation,
            List<HardGate> hardGates, List<Map<String, Object>> items) {

        /** Keys of the report document; consumers read these instead of the legacy rule fields. */
        public Map<String, Object> scoringDocument() {
            Map<String, Object> scoring = new LinkedHashMap<>();
            scoring.put("method", method);
            scoring.put("verdictValues", Map.of("MET", 100, "PARTIAL", 60, "UNCLEAR", 35, "NOT_FOUND", 0));
            scoring.put("priorityWeights", PRIORITY_WEIGHTS);
            scoring.put("citedCount", citedCount);
            scoring.put("requirementCount", requirementCount);
            return scoring;
        }
    }

    /** Requirements without a judgement count as UNCLEAR. */
    public static Result score(String method, List<Requirement> requirements, Map<String, Judgement> judgements) {
        double weighted = 0;
        double weightTotal = 0;
        Map<String, double[]> byDimension = new LinkedHashMap<>();
        int cited = 0;
        int hardGaps = 0;
        boolean hardUnclear = false;
        int supported = 0;
        int supportedByRecords = 0;
        List<HardGate> hardGates = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Requirement requirement : requirements) {
            Judgement judgement = judgements.getOrDefault(requirement.id(),
                    new Judgement(requirement.id(), Verdict.UNCLEAR, List.of(), List.of(), false, null));
            Verdict verdict = judgement.verdict() == null ? Verdict.UNCLEAR : judgement.verdict();
            int weight = isResponsibility(requirement.category()) ? 1
                    : PRIORITY_WEIGHTS.getOrDefault(normalize(requirement.priority()), 2);
            weighted += verdict.value * weight;
            weightTotal += weight;
            String dimension = dimensionOf(requirement.category());
            if (dimension != null) {
                double[] sum = byDimension.computeIfAbsent(dimension, key -> new double[2]);
                sum[0] += verdict.value * weight;
                sum[1] += weight;
            }
            // NOT_FOUND is a decision too: the model looked and found nothing. UNCLEAR is not.
            if (verdict != Verdict.UNCLEAR && (judgement.cited() || verdict == Verdict.NOT_FOUND)) cited++;
            if (verdict == Verdict.MET || verdict == Verdict.PARTIAL) {
                supported++;
                if (!judgement.resumeRefs().isEmpty() || !judgement.evidenceIds().isEmpty()) supportedByRecords++;
            }
            if (requirement.hardGate()) {
                String status = switch (verdict) {
                    case MET -> "PASSED";
                    case PARTIAL -> "PARTIAL";
                    case NOT_FOUND -> "FAILED";
                    case UNCLEAR -> "UNKNOWN";
                };
                if (verdict == Verdict.NOT_FOUND) hardGaps++;
                if (verdict == Verdict.UNCLEAR) hardUnclear = true;
                hardGates.add(new HardGate(requirement.id(), requirement.text(), status, judgement.explanation()));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("requirementId", requirement.id());
            item.put("text", requirement.text());
            item.put("category", requirement.category());
            item.put("priority", normalize(requirement.priority()));
            item.put("hardGate", requirement.hardGate());
            item.put("verdict", verdict.name());
            item.put("value", verdict.value);
            item.put("resumeRefs", judgement.resumeRefs());
            item.put("evidenceIds", judgement.evidenceIds());
            item.put("userConfirmed", judgement.userConfirmed());
            if (judgement.explanation() != null && !judgement.explanation().isBlank()) item.put("explanation", judgement.explanation());
            items.add(item);
        }
        int overall = weightTotal == 0 ? 0 : (int) Math.round(weighted / weightTotal);
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        for (String key : List.of("coreSkills", "experience", "qualification")) {
            double[] sum = byDimension.get(key);
            dimensions.put(key, sum == null || sum[1] == 0 ? null : (int) Math.round(sum[0] / sum[1]));
        }
        dimensions.put("evidence", supported == 0 ? null : (int) Math.round(supportedByRecords * 100d / supported));
        int total = requirements.size();
        double share = total == 0 ? 0 : (double) cited / total;
        // Keyword matching is a rough estimate whatever it finds, so the rule preview never claims more than 低.
        String confidence = total == 0 || METHOD_RULES.equals(method) ? "LOW"
                : share >= 0.75 ? "HIGH" : share >= 0.5 ? "MEDIUM" : "LOW";
        boolean hardPassed = hardGaps == 0;
        String recommendation;
        if (!hardPassed) recommendation = "CAUTION";
        else if (overall >= 75 && !hardUnclear) recommendation = "RECOMMENDED";
        else if (overall >= 55) recommendation = "CONDITIONAL";
        else recommendation = "PREPARE_FIRST";
        return new Result(method, overall, dimensions, confidence, cited, total, hardPassed, hardGaps, recommendation,
                List.copyOf(hardGates), List.copyOf(items));
    }

    /** The recommendation shown: the model's, unless it is more optimistic than the score allows. */
    public static String cappedRecommendation(String modelCode, String scoredCode) {
        int model = rank(modelCode);
        int scored = rank(scoredCode);
        if (model < 0) return scoredCode;
        return model > scored ? scoredCode : modelCode;
    }

    private static int rank(String code) {
        if (code == null) return -1;
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "NOT_RECOMMENDED" -> 0;
            case "CAUTION" -> 1;
            case "PREPARE_FIRST" -> 2;
            case "CONDITIONAL" -> 3;
            case "RECOMMENDED" -> 4;
            default -> -1;
        };
    }

    static String dimensionOf(String category) {
        if (category == null) return "coreSkills";
        return switch (category.trim().toUpperCase(Locale.ROOT)) {
            case "EXPERIENCE", "RESPONSIBILITY" -> "experience";
            case "EDUCATION", "CONSTRAINT" -> "qualification";
            case "BONUS" -> null;
            default -> "coreSkills";
        };
    }

    private static boolean isResponsibility(String category) {
        return category != null && "RESPONSIBILITY".equalsIgnoreCase(category.trim());
    }

    private static String normalize(String priority) {
        return priority == null ? "IMPORTANT" : priority.trim().toUpperCase(Locale.ROOT);
    }
}
