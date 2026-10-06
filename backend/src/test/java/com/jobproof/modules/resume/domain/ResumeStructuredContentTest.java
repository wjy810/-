package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ResumeStructuredContentTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void projectsTimelineFieldsWithoutFlatteningTheDateIntoDescription() throws Exception {
        var content = mapper.readTree("""
                {
                  "education":[{
                    "school":"河南科技学院",
                    "major":"数据科学与大数据技术",
                    "degree":"本科",
                    "startDate":"2026-02",
                    "endDate":"2026-09",
                    "location":"新乡",
                    "description":"系统学习数据结构、数据库原理和机器学习。"
                  }]
                }
                """);

        var entries = ResumeStructuredContent.entries(content, "education", "YYYY_DOT_MM");

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.primary()).isEqualTo("河南科技学院");
            assertThat(entry.secondary()).isEqualTo("数据科学与大数据技术 · 本科");
            assertThat(entry.date()).isEqualTo("2026.02 - 2026.09");
            assertThat(entry.location()).isEqualTo("新乡");
            assertThat(entry.description()).doesNotContain("河南科技学院", "2026");
        });
    }
}
