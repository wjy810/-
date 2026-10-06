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

        assertThat(flyway.migrate().targetSchemaVersion).isEqualTo("70");
    }

    @Test
    void version39DatabaseUpgradesToLatestVersion() {
        String database = "upgrade-" + UUID.randomUUID();
        Flyway version39 = flyway(database, MigrationVersion.fromVersion("39"));
        assertThat(version39.migrate().targetSchemaVersion).isEqualTo("39");

        Flyway latest = flyway(database, null);
        assertThat(latest.migrate().targetSchemaVersion).isEqualTo("70");
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
