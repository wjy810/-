package com.jobproof.modules.jobapply.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyDigestTest {

    @Test
    void weeksAreKeyedByIsoWeek() {
        assertThat(WeeklyDigest.weekKey(LocalDate.parse("2026-10-12"))).isEqualTo("2026-W42");
        assertThat(WeeklyDigest.weekKey(LocalDate.parse("2027-01-01"))).isEqualTo("2026-W53");
    }

    @Test
    void theDigestSaysWhatHappenedAndWhatIsNext() {
        var busy = new WeeklyDigest.Summary(3, 5, List.of("合成科技", "星图数据", "拾光电商", "青禾教育"), 1,
                List.of("10月21日 14:00 合成科技 一面"));
        assertThat(busy.empty()).isFalse();
        assertThat(busy.title()).isEqualTo("求职周报：上周投递 3 家，简历被打开 5 次，进入下一轮 1 次");
        assertThat(busy.body()).isEqualTo("合成科技、星图数据、拾光电商 等 4 家公司打开了你的简历。本周安排：10月21日 14:00 合成科技 一面。");

        var quiet = new WeeklyDigest.Summary(2, 0, List.of(), 0, List.of());
        assertThat(quiet.title()).isEqualTo("求职周报：上周投递 2 家");
        assertThat(quiet.body()).contains("还没有安排面试或笔试");

        var onlyPlans = new WeeklyDigest.Summary(0, 0, List.of(), 0, List.of("10月22日 09:00 星图数据 笔试"));
        assertThat(onlyPlans.title()).isEqualTo("求职周报：本周有 1 个安排");

        assertThat(new WeeklyDigest.Summary(0, 0, List.of(), 0, List.of()).empty()).isTrue();
    }
}
