package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FlywayMigrationIT {

    @Test
    void freshDatabaseMigratesThroughLatestVersion() {
        Flyway flyway = flyway("fresh-" + UUID.randomUUID(), null);

        assertThat(flyway.migrate().targetSchemaVersion).isEqualTo("66");
    }

    @Test
    void version39DatabaseUpgradesToLatestVersion() {
        String database = "upgrade-" + UUID.randomUUID();
        Flyway version39 = flyway(database, MigrationVersion.fromVersion("39"));
        assertThat(version39.migrate().targetSchemaVersion).isEqualTo("39");

        Flyway latest = flyway(database, null);
        assertThat(latest.migrate().targetSchemaVersion).isEqualTo("66");
    }

    private static Flyway flyway(String database, MigrationVersion target) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + database
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa", "");
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
