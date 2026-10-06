package com.jobproof.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class DatabaseCopyToolTest {

    private static DatabaseCopyTool.Endpoint memory(String name) {
        return new DatabaseCopyTool.Endpoint("jdbc:h2:mem:" + name + "-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE", "sa", "");
    }

    private static void migrate(DatabaseCopyTool.Endpoint endpoint, String version) {
        Flyway.configure().dataSource(endpoint.url(), endpoint.user(), endpoint.password())
                .locations("classpath:db/migration", "classpath:db/vendor/h2")
                .target(version).load().migrate();
    }

    @Test
    void copiesEveryRowIntoAnEmptyTargetAtTheSourceVersion() throws Exception {
        DatabaseCopyTool.Endpoint source = memory("copy-source");
        DatabaseCopyTool.Endpoint target = memory("copy-target");
        migrate(source, "68");
        try (Connection connection = source.open(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO outbox_events (id, event_type, payload_json, created_at, status, attempts) "
                    + "VALUES ('event-1', 'X', '{\"text\":\"中文与 emoji ✓\"}', CURRENT_TIMESTAMP, 'PENDING', 0)");
        }
        ByteArrayOutputStream log = new ByteArrayOutputStream();

        DatabaseCopyTool.Report report = new DatabaseCopyTool(new PrintStream(log, true, StandardCharsets.UTF_8))
                .run(source, target, false);

        assertThat(report.matches()).isTrue();
        assertThat(report.rows()).isPositive();
        assertThat(log.toString(StandardCharsets.UTF_8)).contains("source schema version: 68", "target migrated to version 68");
        try (Connection connection = target.open(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT payload_json FROM outbox_events WHERE id='event-1'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("{\"text\":\"中文与 emoji ✓\"}");
        }
        try (Connection connection = target.open(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1")) {
            rows.next();
            // The application, not the tool, upgrades the copy to the latest version.
            assertThat(rows.getString(1)).isEqualTo("68");
        }
    }

    @Test
    void refusesToWriteIntoATargetThatHoldsData() throws Exception {
        DatabaseCopyTool.Endpoint source = memory("refuse-source");
        DatabaseCopyTool.Endpoint target = memory("refuse-target");
        migrate(source, "68");
        migrate(target, "68");
        try (Connection connection = DriverManager.getConnection(target.url(), "sa", ""); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO outbox_events (id, event_type, payload_json, created_at, status, attempts) "
                    + "VALUES ('existing', 'X', '{}', CURRENT_TIMESTAMP, 'PENDING', 0)");
        }

        assertThatThrownBy(() -> new DatabaseCopyTool(new PrintStream(new ByteArrayOutputStream())).run(source, target, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("target already holds data");
    }
}
