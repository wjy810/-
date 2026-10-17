package com.jobproof.modules.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskRefsTest {

    @Test
    void keepsOnlyKnownPlainIds() {
        assertThat(TaskRefs.from("{\"matchId\":\"abc-1\",\"requestId\":\"r\",\"sessionId\":\"../x\",\"careerFileId\":7}"))
                .isEqualTo(Map.of("matchId", "abc-1"));
    }

    @Test
    void toleratesMissingOrBrokenPayloads() {
        assertThat(TaskRefs.from(null)).isEmpty();
        assertThat(TaskRefs.from("not json")).isEmpty();
        assertThat(TaskRefs.from("[1,2]")).isEmpty();
    }
}
