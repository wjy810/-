package com.jobproof.modules.mockinterview.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockInterviewFollowUpTest {

    private final MockInterviewAiService ai = new MockInterviewAiService(null, new ObjectMapper(), "test-model");

    @Test
    void aPressureInterviewDigsTwiceOthersOnce() {
        assertThat(MockInterviewService.followUpBudget("PRESSURE")).isEqualTo(2);
        assertThat(MockInterviewService.followUpBudget("COMPREHENSIVE")).isEqualTo(1);
        assertThat(MockInterviewService.followUpBudget("BEHAVIORAL")).isEqualTo(1);
    }

    @Test
    void anAnswerDirectionKeepsOnlyNumbersTheUserGave() {
        Map<String, Object> resume = Map.of("experiences", "拾光电商 数据分析实习 留存提升 18%");
        String grounded = "先讲拾光电商实习里留存提升 18% 的那次分析，说明你负责的部分，再补上对照组是怎么选的。";
        assertThat(ai.answerDirection(grounded, "我做过留存分析", resume, Map.of())).isEqualTo(grounded);
        String invented = "强调你把转化率提升了 35%，再说明团队规模 12 人。";
        assertThat(ai.answerDirection(invented, "我做过留存分析", resume, Map.of())).as("35 and 12 appear nowhere").isNull();
        assertThat(ai.answerDirection("太短", "", resume, Map.of())).isNull();
    }

    @Test
    void identifiersVersionsAndDigitsInsideOtherNumbersDoNotVouchForAnInventedNumber() {
        Map<String, Object> resume = Map.of(
                "id", "3f2b6c1e-9a30-4d12-8e30-0c5b2a7d9e30",
                "version", 30,
                "title", "数据分析简历",
                "experience", "[{\"id\":\"e1\",\"company\":\"拾光电商\",\"description\":\"留存提升 18%\",\"startDate\":\"2019.09\"}]");
        Map<String, Object> jd = Map.of("jobMatchId", "a1b2c3d4-0000-4000-8000-000000000012", "reportId", "r-12",
                "requirements", java.util.List.of(Map.of("id", "q7", "requirement_text", "熟练使用 SQL")));
        assertThat(ai.answerDirection("强调你把留存提升了 30%，再补上对照组的选法。", "我做过留存分析", resume, jd))
                .as("30 only appears in an id and the version").isNull();
        assertThat(ai.answerDirection("说明那次分析带来了 8% 的提升，并讲清你负责的部分。", "我做过留存分析", resume, jd))
                .as("8 only appears inside 18").isNull();
        assertThat(ai.answerDirection("把团队 12 人的分工讲清楚，再说你在其中的角色。", "我做过留存分析", resume, jd))
                .as("12 only appears in ids").isNull();
        String grounded = "先讲 2019 年在拾光电商留存提升 18% 的那次分析，再说明这 3 个月里你负责的部分。";
        assertThat(ai.answerDirection(grounded, "我在那里做了 3 个月", resume, jd)).isEqualTo(grounded);
        assertThat(ai.answerDirection("结尾总结一下：留存提升 18%。", "", resume, jd)).isNotNull();
    }
}
