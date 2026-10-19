package com.jobproof.modules.jobapply.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NextStepRemindersTest {
    private static final Instant NOW = Instant.parse("2026-10-18T02:00:00Z"); // 10:00 in China

    @Test
    void timesReadAsTodayTomorrowOrADateInChinaTime() {
        assertThat(NextStepReminders.when(Instant.parse("2026-10-18T07:30:00Z"), NOW)).isEqualTo("今天 15:30");
        // 01:00 UTC on the 19th is already 09:00 on the 19th in China.
        assertThat(NextStepReminders.when(Instant.parse("2026-10-19T01:00:00Z"), NOW)).isEqualTo("明天 09:00");
        assertThat(NextStepReminders.when(Instant.parse("2026-10-20T06:00:00Z"), NOW)).isEqualTo("10月20日 14:00");
    }

    @Test
    void theTitleNamesTheCompanyAndTheStepAndStaysWithinTheColumn() {
        Instant at = Instant.parse("2026-10-19T06:00:00Z");
        assertThat(NextStepReminders.title(new NextStepReminders.Due("a", "u", "合成科技", "Java", "一面", at), NOW))
                .isEqualTo("明天 14:00 · 合成科技 一面");
        assertThat(NextStepReminders.title(new NextStepReminders.Due("a", "u", "合成科技", "Java", " ", at), NOW))
                .isEqualTo("明天 14:00 · 合成科技 下一步");
        assertThat(NextStepReminders.title(new NextStepReminders.Due("a", "u", "长".repeat(300), "Java", "一面", at), NOW))
                .hasSize(200).endsWith("…");
    }
}
