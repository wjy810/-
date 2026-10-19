package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FlywayMigrationIT {

    @Test
    void freshDatabaseMigratesThroughLatestVersion() {
        Flyway flyway = flyway("fresh-" + UUID.randomUUID(), null);

        assertThat(flyway.migrate().targetSchemaVersion).isEqualTo("78");
    }

    @Test
    void version39DatabaseUpgradesToLatestVersion() {
        String database = "upgrade-" + UUID.randomUUID();
        Flyway version39 = flyway(database, MigrationVersion.fromVersion("39"));
        assertThat(version39.migrate().targetSchemaVersion).isEqualTo("39");

        Flyway latest = flyway(database, null);
        assertThat(latest.migrate().targetSchemaVersion).isEqualTo("78");
    }

    @Test
    void version67BackfillsOutboxDeliveryState() {
        String database = "outbox-" + UUID.randomUUID();
        assertThat(flyway(database, MigrationVersion.fromVersion("66")).migrate().targetSchemaVersion).isEqualTo("66");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource(database));
        jdbc.update("INSERT INTO outbox_events (id, event_type, payload_json, created_at, published_at) VALUES ('done', 'X', '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO outbox_events (id, event_type, payload_json, created_at) VALUES ('open', 'X', '{}', CURRENT_TIMESTAMP)");

        assertThat(flyway(database, MigrationVersion.fromVersion("67")).migrate().targetSchemaVersion).isEqualTo("67");

        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id='done'", String.class)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id='open'", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE id='open'", Integer.class)).isZero();
    }

    @Test
    void version68MarksExistingGateEvidenceAndAddsCatalogColumns() {
        String database = "builtin-" + UUID.randomUUID();
        assertThat(flyway(database, MigrationVersion.fromVersion("67")).migrate().targetSchemaVersion).isEqualTo("67");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource(database));
        jdbc.update("UPDATE resume_layout_template_versions SET status='PUBLISHED' WHERE template_id='rlt-b-campus-v1'");

        assertThat(flyway(database, MigrationVersion.fromVersion("68")).migrate().targetSchemaVersion).isEqualTo("68");

        assertThat(jdbc.queryForObject("SELECT verification_source FROM resume_layout_template_versions "
                + "WHERE template_id='rlt-b-campus-v1'", String.class)).isEqualTo("ADMIN_EVIDENCE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resume_layout_template_versions "
                + "WHERE status='DRAFT' AND verification_source IS NULL", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resume_layout_templates WHERE builtin=0 AND sort_order=1000 "
                + "AND featured=0", Integer.class)).isEqualTo(12);
    }

    @Test
    void version71GivesLegacyTaskNotificationsTheirTaskSpecificWording() {
        String database = "notice-" + UUID.randomUUID();
        assertThat(flyway(database, MigrationVersion.fromVersion("70")).migrate().targetSchemaVersion).isEqualTo("70");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource(database));
        String taskId = "11111111-1111-1111-1111-111111111111";
        jdbc.update("INSERT INTO async_tasks (id, account_id, task_type, status, input_version, attempt_count, created_at, updated_at) "
                + "VALUES (?, 'acc', 'JOB_MATCH', 'SUCCEEDED', 'v1', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", taskId);
        String insert = "INSERT INTO notifications (id, account_id, type, status, event_id, title, body, created_at) "
                + "VALUES (?, 'acc', ?, 'DELIVERED', ?, '任务状态更新', '你的一项任务状态有变化，打开对应页面查看详情。', CURRENT_TIMESTAMP)";
        jdbc.update(insert, "n1", "TASK_COMPLETED", taskId + ":SUCCEEDED");
        jdbc.update(insert, "n2", "TASK_FAILED", "22222222-2222-2222-2222-222222222222:FAILED");

        assertThat(flyway(database, MigrationVersion.fromVersion("71")).migrate().targetSchemaVersion).isEqualTo("71");

        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE id='n1'", String.class)).isEqualTo("岗位匹配报告已生成");
        assertThat(jdbc.queryForObject("SELECT action_path FROM notifications WHERE id='n1'", String.class)).isEqualTo("/job-match/history");
        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE id='n2'", String.class)).isEqualTo("后台任务未完成");
    }

    @Test
    void version72NamesFilesWhoseChineseNameWasLost() {
        String database = "names-" + UUID.randomUUID();
        assertThat(flyway(database, MigrationVersion.fromVersion("71")).migrate().targetSchemaVersion).isEqualTo("71");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource(database));
        String insert = "INSERT INTO career_library_files (id, account_id, private_file_id, category, display_name, original_filename, "
                + "content_type, size_bytes, sha256, scan_status, preview_status, preview_page_count, status, version_no, created_at, updated_at) "
                + "VALUES (?, 'acc', ?, 'RESUME', ?, ?, 'application/pdf', 10, 'x', 'PASSED', 'READY', 1, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        jdbc.update(insert, "f1", "p1", "____.pdf", "____.pdf");
        jdbc.update(insert, "f2", "p2", "resume_2026.pdf", "resume_2026.pdf");

        assertThat(flyway(database, MigrationVersion.fromVersion("72")).migrate().targetSchemaVersion).isEqualTo("72");

        assertThat(jdbc.queryForObject("SELECT display_name FROM career_library_files WHERE id='f1'", String.class)).isEqualTo("未命名文件.pdf");
        assertThat(jdbc.queryForObject("SELECT display_name FROM career_library_files WHERE id='f2'", String.class)).isEqualTo("resume_2026.pdf");
    }

    private static DriverManagerDataSource dataSource(String database) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + database
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa", "");
    }

    private static Flyway flyway(String database, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource(database))
                .locations("classpath:db/migration", "classpath:db/vendor/h2");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
