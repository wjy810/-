package com.jobproof.modules.jobmatch.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobproof.modules.jobmatch.domain.JobMatchScoring.Verdict;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClarificationVerdictsTest {

    @Test
    void readsWhatTheUserActuallyAnswered() {
        assertThat(ClarificationVerdicts.of("有真实使用经验")).isEqualTo(Verdict.MET);
        assertThat(ClarificationVerdicts.of("可以确认")).isEqualTo(Verdict.MET);
        assertThat(ClarificationVerdicts.of("了解但没有项目经验")).isEqualTo(Verdict.PARTIAL);
        assertThat(ClarificationVerdicts.of("正在学习")).isEqualTo(Verdict.PARTIAL);
        assertThat(ClarificationVerdicts.of("不会")).isEqualTo(Verdict.NOT_FOUND);
        assertThat(ClarificationVerdicts.of("不了解")).isEqualTo(Verdict.NOT_FOUND);
        assertThat(ClarificationVerdicts.of("没有")).isEqualTo(Verdict.NOT_FOUND);
        assertThat(ClarificationVerdicts.of("暂不确认")).isEqualTo(Verdict.UNCLEAR);
        assertThat(ClarificationVerdicts.of(null)).isEqualTo(Verdict.UNCLEAR);
    }

    /** Options gpt-6.1-sol wrote for a data-analyst JD; each read wrongly by the first keyword rules. */
    @Test
    void readsTheOptionsARealModelWrites() {
        assertThat(ClarificationVerdicts.of("两类工具均有实际使用，可补充案例")).isEqualTo(Verdict.MET);
        assertThat(ClarificationVerdicts.of("仅SQL有实际案例")).isEqualTo(Verdict.PARTIAL);
        assertThat(ClarificationVerdicts.of("尚无实际案例")).isEqualTo(Verdict.NOT_FOUND);
        assertThat(ClarificationVerdicts.of("仅学习过，暂无实践")).isEqualTo(Verdict.PARTIAL);
        assertThat(ClarificationVerdicts.of("使用其他工具")).isEqualTo(Verdict.PARTIAL);
        assertThat(ClarificationVerdicts.of("未涉及上述团队")).isEqualTo(Verdict.NOT_FOUND);
        assertThat(ClarificationVerdicts.of("仅与其中一类团队协作")).isEqualTo(Verdict.PARTIAL);
        assertThat(ClarificationVerdicts.of("不属于上述行业")).isEqualTo(Verdict.NOT_FOUND);
        assertThat(ClarificationVerdicts.of("行业归属不确定")).isEqualTo(Verdict.UNCLEAR);
    }

    @Test
    void theVerdictTheModelGaveAnOptionWins() {
        Map<String, String> given = Map.of("用过 Hive", "PARTIAL", "暂不确认", "UNCLEAR");
        assertThat(ClarificationVerdicts.of("用过 Hive", given)).isEqualTo(Verdict.PARTIAL);
        assertThat(ClarificationVerdicts.of("有真实使用经验", given)).as("no verdict given: wording").isEqualTo(Verdict.MET);
        assertThat(ClarificationVerdicts.of("用过 Hive", Map.of("用过 Hive", "MAYBE"))).as("invalid verdict: wording")
                .isEqualTo(Verdict.MET);
    }
}
