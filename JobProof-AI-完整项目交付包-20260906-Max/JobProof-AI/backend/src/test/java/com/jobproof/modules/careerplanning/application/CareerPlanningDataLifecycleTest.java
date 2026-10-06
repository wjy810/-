package com.jobproof.modules.careerplanning.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobproof.shared.deletion.DeletionModuleHandler;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class CareerPlanningDataLifecycleTest {
    private static final List<String> ACCOUNT_TABLES = List.of(
            "career_ability_validation_evidences",
            "career_ability_validations",
            "career_learning_weekly_reviews",
            "career_learning_evidences",
            "career_learning_plan_tasks",
            "career_learning_plan_revisions",
            "career_learning_plans",
            "career_canvas_ai_proposal_items",
            "career_canvas_ai_proposals",
            "node_relations",
            "canvas_nodes",
            "canvas_versions",
            "career_goal_transitions",
            "career_goals",
            "career_recommendations",
            "career_recommendation_sets",
            "career_planning_stream_events",
            "career_planning_messages",
            "career_planning_interview_rounds",
            "career_planning_evidence_permissions",
            "career_planning_profile_items",
            "career_planning_profiles",
            "career_planning_sessions");

    @Test
    void failedDeletionRollsBackEveryCareerPlanningTable() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ACCOUNT_TABLES.forEach(table -> jdbc.execute("CREATE TABLE " + table + " (account_id VARCHAR(64))"));
            jdbc.execute("CREATE TABLE async_tasks (account_id VARCHAR(64), task_type VARCHAR(64))");

            String accountId = "account-atomic-delete";
            jdbc.update("INSERT INTO career_ability_validation_evidences(account_id) VALUES (?)", accountId);
            jdbc.update("INSERT INTO career_ability_validations(account_id) VALUES (?)", accountId);

            // Force the third delete to fail after the first two statements have succeeded.
            jdbc.execute("DROP TABLE career_learning_weekly_reviews");

            var result = context.getBean(DeletionModuleHandler.class).onAccountDeletion(accountId);

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(count(jdbc, "career_ability_validation_evidences", accountId)).isOne();
            assertThat(count(jdbc, "career_ability_validations", accountId)).isOne();
        }
    }

    private int count(JdbcTemplate jdbc, String table, String accountId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE account_id=?", Integer.class, accountId);
        return count == null ? 0 : count;
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:career-deletion-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        CareerPlanningDataLifecycle careerPlanningDataLifecycle(
                JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
            return new CareerPlanningDataLifecycle(jdbcTemplate, transactionManager);
        }
    }
}
