package com.jobproof.modules.jobmatch.domain;

import java.util.List;
import java.util.Map;

public final class JobMatchModels {
    private JobMatchModels() {}

    public static final List<String> STATUSES = List.of(
            "DRAFT", "JD_PARSED", "RESUME_CONFIRMED", "EVIDENCE_AUTHORIZED", "ANALYZING",
            "NEEDS_CLARIFICATION", "COMPLETED", "JD_PARSE_FAILED", "RESUME_UPLOAD_FAILED",
            "ANALYSIS_PAUSED", "CANCELLED");

    public record ParsedJd(String title, String company, String location, String workMode,
            List<RequirementDraft> requirements, List<String> responsibilities, int confidence,
            List<String> conflicts, String normalizedText) {}

    public record RequirementDraft(String category, String text, String priority, boolean hardGate,
            String locator, String quote, int confidence) {}

    public record EvidenceCandidate(String sourceType, String sourceId, String title, String excerpt,
            String locator, String strength, int relevance, boolean recommended) {}

    public record RuleResult(boolean hardGatePassed, int overallScore, int confidence,
            Map<String, Integer> dimensions, List<Map<String, Object>> requirements,
            List<Map<String, Object>> strengths, List<Map<String, Object>> gaps) {}
}
