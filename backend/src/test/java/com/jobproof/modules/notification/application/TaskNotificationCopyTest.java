package com.jobproof.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobproof.modules.task.domain.TaskTypes;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskNotificationCopyTest {

    @Test
    void linksAJobMatchToItsReportOrBackToTheAnalysis() {
        var done = TaskNotificationCopy.of(TaskTypes.JOB_MATCH, true, Map.of("matchId", "m-1")).orElseThrow();
        assertThat(done.title()).isEqualTo("岗位匹配报告已生成");
        assertThat(done.actionPath()).isEqualTo("/job-match/m-1/report");

        var failed = TaskNotificationCopy.of(TaskTypes.JOB_MATCH, false, Map.of("matchId", "m-1")).orElseThrow();
        assertThat(failed.actionPath()).isEqualTo("/job-match/m-1/analyzing");
    }

    @Test
    void fallsBackToTheListWhenTheIdIsMissingOrUnsafe() {
        assertThat(TaskNotificationCopy.of(TaskTypes.JOB_MATCH, true, Map.of()).orElseThrow().actionPath())
                .isEqualTo("/job-match/history");
        assertThat(TaskNotificationCopy.of(TaskTypes.CAREER_PLANNING_CANVAS, true, Map.of("sessionId", "../admin")).orElseThrow().actionPath())
                .isEqualTo("/career-planning");
        assertThat(TaskNotificationCopy.of(TaskTypes.RESUME_PDF_EXPORT, true, Map.of("resumeVersionId", "v-9")).orElseThrow().actionPath())
                .isEqualTo("/resumes/versions/v-9");
    }

    @Test
    void staysQuietForStepsTheUserIsWatchingButReportsTheirFailures() {
        assertThat(TaskNotificationCopy.of(TaskTypes.JD_PARSE, true, Map.of())).isEmpty();
        assertThat(TaskNotificationCopy.of(TaskTypes.RESUME_IMPORT_PARSE, true, Map.of())).isEmpty();
        assertThat(TaskNotificationCopy.of(TaskTypes.CAREER_PLANNING_INTERVIEW, true, Map.of())).isEmpty();
        assertThat(TaskNotificationCopy.of(TaskTypes.ACCOUNT_DELETION, false, Map.of())).isEmpty();
        assertThat(TaskNotificationCopy.of(TaskTypes.RESUME_IMPORT_PARSE, false, Map.of())).isPresent();
    }

    @Test
    void everyTaskTypeGetsItsOwnWordingInsteadOfTheGenericOne() throws IllegalAccessException {
        for (Field field : TaskTypes.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) continue;
            String type = (String) field.get(null);
            for (boolean succeeded : new boolean[] {true, false}) {
                TaskNotificationCopy.of(type, succeeded, Map.of()).ifPresent(copy -> {
                    assertThat(copy.title()).as(type).doesNotStartWith("后台任务");
                    assertThat(copy.actionPath()).as(type).startsWith("/");
                });
            }
        }
    }
}
