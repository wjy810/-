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
}
