package com.jobproof.modules.careerplanning.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerPlanScheduleTest {

    @Test
    void eachWeekIsDueOnItsLastChosenLearningDay() {
        LocalDate wednesday = LocalDate.of(2026, 10, 21);
        assertThat(wednesday.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        // 周一、周三、周六; week 1 runs Wed 21 – Tue 27, so its last learning day is Monday 26.
        LocalDate due = CareerPlanningExecutionService.weekDueDate(wednesday, 1, List.of(1, 3, 6));
        assertThat(due).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(due.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(CareerPlanningExecutionService.weekDueDate(wednesday, 2, List.of(1, 3, 6)))
                .isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void aWeekStartingOnALearningDayCanBeDueThatSameWeekday() {
        LocalDate monday = LocalDate.of(2026, 10, 19);
        assertThat(CareerPlanningExecutionService.weekDueDate(monday, 1, List.of(1))).isEqualTo(monday);
        assertThat(CareerPlanningExecutionService.weekDueDate(monday, 1, List.of(7))).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void intensityLeavesTimeForReviewExceptWhenFocused() {
        assertThat(CareerPlanningExecutionService.INTENSITY_SHARE).containsEntry("LIGHT", 0.7)
                .containsEntry("STANDARD", 0.85).containsEntry("FOCUSED", 1.0);
    }
}
