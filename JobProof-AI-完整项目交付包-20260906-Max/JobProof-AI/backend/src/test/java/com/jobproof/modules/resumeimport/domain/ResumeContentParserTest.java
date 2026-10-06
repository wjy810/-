package com.jobproof.modules.resumeimport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeContentParserTest {

    @Test
    void parsesContactTimelineAndSkillSectionsForConfirmation() {
        ResumeContentParser.Parsed parsed = ResumeContentParser.parse("""
                Zhang San
                zhangsan@example.com | 13800138000
                个人简介：关注后端服务稳定性与可追溯交付。
                教育经历
                Example University 软件工程
                2020.09 - 2024.06
                主修数据结构、数据库系统与软件工程。
                项目经历
                Order Service
                2023.03 - 2023.08
                使用 Java、Spring Boot 和 MySQL 完成订单接口开发。
                专业技能：Java, Spring Boot, SQL
                """);

        assertThat(parsed.confirmable()).isTrue();
        assertThat(parsed.confidence()).containsEntry("parser", "resume-local-parser-v1");
        assertThat(map(parsed.content().get("basics")))
                .containsEntry("name", "Zhang San")
                .containsEntry("email", "zhangsan@example.com")
                .containsEntry("phone", "13800138000");
        assertThat(list(parsed.content().get("education"))).singleElement()
                .satisfies(item -> assertThat(map(item))
                        .containsEntry("school", "Example University 软件工程")
                        .containsEntry("startDate", "2020-09")
                        .containsEntry("endDate", "2024-06"));
        assertThat(list(parsed.content().get("projects"))).singleElement()
                .satisfies(item -> assertThat(map(item).get("description").toString())
                        .contains("Java", "Spring Boot", "MySQL"));
        assertThat(list(parsed.content().get("skills"))).isNotEmpty();
        assertThat(parsed.sourceMap()).containsKeys("education", "projects", "skills");
    }

    @Test
    void leavesUnstructuredContactOnlyTextUnconfirmable() {
        ResumeContentParser.Parsed parsed = ResumeContentParser.parse("Zhang San\nzhangsan@example.com\n13800138000");

        assertThat(parsed.confirmable()).isFalse();
        assertThat(ResumeContentParser.substantiveCount(parsed.content())).isZero();
        assertThat(parsed.content()).containsEntry("schemaVersion", "resume-content-v3");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }
}
