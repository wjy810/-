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
        assertThat(parsed.confidence()).containsEntry("parser", "resume-local-parser-v2");
        assertThat(map(parsed.content().get("basics")))
                .containsEntry("name", "Zhang San")
                .containsEntry("email", "zhangsan@example.com")
                .containsEntry("phone", "13800138000");
        assertThat(list(parsed.content().get("education"))).singleElement()
                .satisfies(item -> assertThat(map(item))
                        .containsEntry("school", "Example University")
                        .containsEntry("major", "软件工程")
                        .containsEntry("startDate", "2020-09")
                        .containsEntry("endDate", "2024-06"));
        assertThat(list(parsed.content().get("projects"))).singleElement()
                .satisfies(item -> assertThat(map(item).get("description").toString())
                        .contains("Java", "Spring Boot", "MySQL"));
        assertThat(list(parsed.content().get("skills"))).isNotEmpty();
        assertThat(parsed.sourceMap()).containsKeys("education", "projects", "skills");
    }

    @Test
    void splitsEntriesFieldsAndDatesWithoutDuplicatingOrInventingContent() {
        ResumeContentParser.Parsed parsed = ResumeContentParser.parse("""
                李明
                电话：13800001234  邮箱：liming@example.com  城市：杭州
                求职意向：后端开发工程师

                教育经历
                浙江大学 计算机科学与技术 本科 2019.09 - 2023.06
                GPA 3.7/4.0

                工作经历
                杭州某科技有限公司 后端开发工程师 2023.07 - 至今
                负责订单服务的接口设计与性能优化
                主导支付对账模块重构
                上海某网络公司 Java 实习生
                2022.07 - 2022.12
                - 参与库存服务开发

                项目经历
                分布式任务调度平台 核心开发 2022.03 - 2022.12
                基于 Spring Boot 与 Redis 实现任务分片

                专业技能
                Java、Spring Boot、MySQL、Redis
                工具：Docker、Git

                证书
                2021.06 英语六级、软件设计师
                """);

        Map<String, Object> basics = map(parsed.content().get("basics"));
        assertThat(basics).containsEntry("name", "李明").containsEntry("phone", "13800001234")
                .containsEntry("email", "liming@example.com").containsEntry("location", "杭州");
        assertThat(map(parsed.content().get("intentions"))).containsEntry("targetJob", "后端开发工程师");

        assertThat(list(parsed.content().get("education"))).singleElement().satisfies(item -> assertThat(map(item))
                .containsEntry("school", "浙江大学").containsEntry("major", "计算机科学与技术").containsEntry("degree", "本科")
                .containsEntry("startDate", "2019-09").containsEntry("endDate", "2023-06").containsEntry("description", "GPA 3.7/4.0"));

        List<Object> jobs = list(parsed.content().get("experiences"));
        assertThat(jobs).hasSize(2);
        assertThat(map(jobs.get(0))).containsEntry("company", "杭州某科技有限公司").containsEntry("role", "后端开发工程师")
                .containsEntry("startDate", "2023-07").containsEntry("current", true)
                .containsEntry("description", "负责订单服务的接口设计与性能优化\n主导支付对账模块重构")
                .doesNotContainKey("highlights");
        assertThat(map(jobs.get(1))).containsEntry("company", "上海某网络公司").containsEntry("role", "Java 实习生")
                .containsEntry("startDate", "2022-07").containsEntry("endDate", "2022-12")
                .containsEntry("description", "参与库存服务开发");

        // "2022.12" must not be read as month 1.
        assertThat(list(parsed.content().get("projects"))).singleElement().satisfies(item -> assertThat(map(item))
                .containsEntry("name", "分布式任务调度平台").containsEntry("role", "核心开发").containsEntry("endDate", "2022-12"));

        List<Object> skills = list(parsed.content().get("skills"));
        assertThat(skills).hasSize(2);
        assertThat(map(skills.get(0))).containsEntry("category", "专业技能")
                .containsEntry("items", List.of("Java", "Spring Boot", "MySQL", "Redis")).doesNotContainKey("description");
        assertThat(map(skills.get(1))).containsEntry("category", "工具").containsEntry("items", List.of("Docker", "Git"));

        assertThat(list(parsed.content().get("certificates"))).extracting(item -> map(item))
                .containsExactly(Map.of("date", "2021-06", "name", "英语六级"), Map.of("name", "软件设计师"));
    }

    @Test
    void readsMonthsOneToTwelve() {
        assertThat(ResumeContentParser.month("2022.12")).isEqualTo("2022-12");
        assertThat(ResumeContentParser.month("2019年9月")).isEqualTo("2019-09");
        assertThat(ResumeContentParser.month("2020")).isEqualTo("2020");
    }

    @Test
    void keepsEnglishNamesTogetherWhileSplittingChineseFields() {
        assertThat(ResumeContentParser.fields("Example University 软件工程 Master")).containsExactly("Example University", "软件工程", "Master");
        assertThat(ResumeContentParser.fields("Google | Software Engineer")).containsExactly("Google", "Software Engineer");
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
