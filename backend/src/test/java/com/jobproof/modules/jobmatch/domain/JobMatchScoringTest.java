package com.jobproof.modules.jobmatch.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobproof.modules.jobmatch.domain.JobMatchScoring.Judgement;
import com.jobproof.modules.jobmatch.domain.JobMatchScoring.Requirement;
import com.jobproof.modules.jobmatch.domain.JobMatchScoring.Verdict;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobMatchScoringTest {

    private static final Requirement SQL = new Requirement("r1", "熟练使用 SQL", "SKILL", "MUST", true);
    private static final Requirement PYTHON = new Requirement("r2", "会 Python", "SKILL", "IMPORTANT", false);
    private static final Requirement INTERN = new Requirement("r3", "一段数据分析实习", "EXPERIENCE", "IMPORTANT", false);
    private static final Requirement TABLEAU = new Requirement("r4", "了解 Tableau", "BONUS", "BONUS", false);

    private static Judgement judged(String id, Verdict verdict, boolean cited) {
        return new Judgement(id, verdict, cited ? List.of("resume:/skills/0") : List.of(), List.of(), false, null);
    }

    @Test
    void scoreIsThePriorityWeightedMeanOfVerdicts() {
        var result = JobMatchScoring.score(JobMatchScoring.METHOD_AI, List.of(SQL, PYTHON, INTERN, TABLEAU), Map.of(
                "r1", judged("r1", Verdict.MET, true),
                "r2", judged("r2", Verdict.PARTIAL, true),
                "r3", judged("r3", Verdict.NOT_FOUND, false),
                "r4", judged("r4", Verdict.MET, true)));
        // (100*3 + 60*2 + 0*2 + 100*1) / 8 = 65
        assertThat(result.overall()).isEqualTo(65);
        assertThat(result.dimensions()).containsEntry("coreSkills", 84).containsEntry("experience", 0)
                .containsEntry("evidence", 100);
        assertThat(result.dimensions().get("qualification")).as("no education or constraint requirement").isNull();
        assertThat(result.hardGatePassed()).isTrue();
        assertThat(result.recommendation()).isEqualTo("CONDITIONAL");
        assertThat(result.confidence()).isEqualTo("HIGH");
    }

    @Test
    void aResponsibilityCountsOnceTowardsExperienceWhateverItsPriority() {
        var duty = new Requirement("d1", "负责搭建数据看板", "RESPONSIBILITY", "IMPORTANT", false);
        var result = JobMatchScoring.score(JobMatchScoring.METHOD_AI, List.of(SQL, duty), Map.of(
                "r1", judged("r1", Verdict.MET, true),
                "d1", judged("d1", Verdict.NOT_FOUND, false)));
        // (100*3 + 0*1) / 4 = 75
        assertThat(result.overall()).isEqualTo(75);
        assertThat(result.dimensions()).containsEntry("coreSkills", 100).containsEntry("experience", 0);
    }

    @Test
    void aMissingHardRequirementCapsTheRecommendation() {
        var result = JobMatchScoring.score(JobMatchScoring.METHOD_AI, List.of(SQL, PYTHON, INTERN), Map.of(
                "r1", judged("r1", Verdict.NOT_FOUND, false),
                "r2", judged("r2", Verdict.MET, true),
                "r3", judged("r3", Verdict.MET, true)));
        assertThat(result.hardGatePassed()).isFalse();
        assertThat(result.hardGapCount()).isEqualTo(1);
        assertThat(result.recommendation()).isEqualTo("CAUTION");
        assertThat(result.hardGates()).singleElement().extracting(JobMatchScoring.HardGate::status).isEqualTo("FAILED");
        assertThat(JobMatchScoring.cappedRecommendation("RECOMMENDED", result.recommendation())).isEqualTo("CAUTION");
        assertThat(JobMatchScoring.cappedRecommendation("NOT_RECOMMENDED", result.recommendation())).isEqualTo("NOT_RECOMMENDED");
        assertThat(JobMatchScoring.cappedRecommendation("SOMETHING_ELSE", result.recommendation())).isEqualTo("CAUTION");
    }

    @Test
    void unjudgedAndUnclearRequirementsLowerConfidenceAndBlockRecommended() {
        var result = JobMatchScoring.score(JobMatchScoring.METHOD_AI, List.of(SQL, PYTHON, INTERN, TABLEAU), Map.of(
                "r2", judged("r2", Verdict.MET, true)));
        assertThat(result.items()).filteredOn(item -> "r1".equals(item.get("requirementId")))
                .singleElement().satisfies(item -> assertThat(item.get("verdict")).isEqualTo("UNCLEAR"));
        assertThat(result.confidence()).isEqualTo("LOW");
        assertThat(result.hardGates()).singleElement().extracting(JobMatchScoring.HardGate::status).isEqualTo("UNKNOWN");

        var allMet = JobMatchScoring.score(JobMatchScoring.METHOD_AI, List.of(SQL, PYTHON), Map.of(
                "r1", new Judgement("r1", Verdict.UNCLEAR, List.of(), List.of(), false, null),
                "r2", judged("r2", Verdict.MET, true)));
        assertThat(allMet.recommendation()).as("an unknown hard gate is never 推荐投递").isNotEqualTo("RECOMMENDED");
    }

    @Test
    void userConfirmedSupportCountsButNotAsRecordEvidence() {
        var result = JobMatchScoring.score(JobMatchScoring.METHOD_AI, List.of(PYTHON), Map.of(
                "r2", new Judgement("r2", Verdict.MET, List.of(), List.of(), true, null)));
        assertThat(result.overall()).isEqualTo(100);
        assertThat(result.dimensions()).containsEntry("evidence", 0);
        assertThat(result.confidence()).isEqualTo("HIGH");
    }

    @Test
    void theRulePreviewNeverClaimsHighConfidence() {
        var result = JobMatchScoring.score(JobMatchScoring.METHOD_RULES, List.of(SQL, PYTHON), Map.of(
                "r1", judged("r1", Verdict.MET, true), "r2", judged("r2", Verdict.MET, true)));
        assertThat(result.confidence()).isEqualTo("LOW");
    }

    @Test
    void noRequirementsMeansNoScoreToSpeakOf() {
        var result = JobMatchScoring.score(JobMatchScoring.METHOD_RULES, List.of(), Map.of());
        assertThat(result.overall()).isZero();
        assertThat(result.confidence()).isEqualTo("LOW");
        assertThat(result.dimensions().values()).containsOnlyNulls();
    }
}
