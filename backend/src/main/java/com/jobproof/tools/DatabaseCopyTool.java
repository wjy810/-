package com.jobproof.tools;

import java.io.PrintStream;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

/**
 * One-off copy of an existing JobProof database into an empty one, e.g. the old single-host H2 file
 * into MySQL (docs/ops/mysql-migration.md). The target is migrated to exactly the source's Flyway
 * version first, every table is copied in batches, and row counts are compared table by table. The
 * application then upgrades the target to the latest version on its next start.
 *
 * <p>Refuses to write into a target that already holds data (checked before migrating; rows the
 * migrations seed are then replaced by the source's own). Reads the source only.
 *
 * <pre>
 * SOURCE_URL=jdbc:h2:file:/var/lib/jobproof/jobproof;MODE=MySQL;... SOURCE_USER=sa SOURCE_PASSWORD=
 * TARGET_URL=jdbc:mysql://mysql:3306/jobproof TARGET_USER=jobproof TARGET_PASSWORD=...
 * java -Dloader.main=com.jobproof.tools.DatabaseCopyTool -cp jobproof-backend.jar \
 *      org.springframework.boot.loader.launch.PropertiesLauncher [--verify-only]
 * </pre>
 */
public final class DatabaseCopyTool {
    private static final String HISTORY = "flyway_schema_history";
    private static final int BATCH = 500;

    private final PrintStream out;

    public DatabaseCopyTool(PrintStream out) {
        this.out = out;
    }

    public static void main(String[] args) throws Exception {
        boolean verifyOnly = List.of(args).contains("--verify-only");
        Endpoint source = Endpoint.fromEnv("SOURCE");
        Endpoint target = Endpoint.fromEnv("TARGET");
        Report report = new DatabaseCopyTool(System.out).run(source, target, verifyOnly);
        System.exit(report.matches() ? 0 : 2);
    }

    public Report run(Endpoint source, Endpoint target, boolean verifyOnly) throws SQLException {
        try (Connection from = source.open(); Connection to = target.open()) {
            from.setReadOnly(true);
            String version = latestVersion(from);
            out.println("source schema version: " + version);
            if (!verifyOnly) {
                List<String> populated = populatedTables(to);
                if (!populated.isEmpty()) {
                    throw new IllegalStateException("target already holds data in " + populated.size()
                            + " table(s), e.g. " + populated.get(0) + "; copy only into an empty database");
                }
                migrate(target, to, version);
                copyAll(from, to);
            }
            return verify(from, to);
        }
    }

    private static String latestVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version FROM " + HISTORY + " WHERE success = TRUE AND version IS NOT NULL")) {
            MigrationVersion latest = null;
            while (rows.next()) {
                MigrationVersion version = MigrationVersion.fromVersion(rows.getString(1));
                if (latest == null || version.isNewerThan(latest.getVersion())) latest = version;
            }
            if (latest == null) throw new IllegalStateException("source has no applied Flyway migrations");
            return latest.getVersion();
        }
    }

    private void migrate(Endpoint target, Connection to, String version) throws SQLException {
        String vendor = to.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql") ? "mysql" : "h2";
        Flyway.configure()
                .dataSource(target.url(), target.user(), target.password())
                .locations("classpath:db/migration", "classpath:db/vendor/" + vendor)
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
        out.println("target migrated to version " + version + " (" + vendor + ")");
    }

    private void copyAll(Connection from, Connection to) throws SQLException {
        boolean mysql = to.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
        boolean autoCommit = to.getAutoCommit();
        to.setAutoCommit(false);
        try (Statement session = to.createStatement()) {
            if (mysql) session.execute("SET FOREIGN_KEY_CHECKS = 0");
            else session.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String table : tables(to)) {
                if (table.equals(HISTORY)) continue;
                List<String> columns = sharedColumns(from, to, table);
                if (columns.isEmpty()) continue;
                // Migrations seed reference rows (models, templates, taxonomy); the source holds the
                // same rows as they are today, so the target copy starts from an empty table.
                try (Statement clear = to.createStatement()) {
                    clear.executeUpdate("DELETE FROM " + table);
                }
                long copied = copyTable(from, to, table, columns);
                to.commit();
                out.printf("copied %-48s %8d rows%n", table, copied);
            }
            if (mysql) session.execute("SET FOREIGN_KEY_CHECKS = 1");
            else session.execute("SET REFERENTIAL_INTEGRITY TRUE");
            to.commit();
        } catch (SQLException | RuntimeException failure) {
            to.rollback();
            throw failure;
        } finally {
            to.setAutoCommit(autoCommit);
        }
    }

    private static long copyTable(Connection from, Connection to, String table, List<String> columns) throws SQLException {
        String list = String.join(",", columns);
        String marks = String.join(",", columns.stream().map(column -> "?").toList());
        long count = 0;
        try (Statement read = from.createStatement();
             PreparedStatement write = to.prepareStatement("INSERT INTO " + table + " (" + list + ") VALUES (" + marks + ")")) {
            read.setFetchSize(BATCH);
            try (ResultSet rows = read.executeQuery("SELECT " + list + " FROM " + table)) {
                while (rows.next()) {
                    for (int index = 1; index <= columns.size(); index++) write.setObject(index, value(rows.getObject(index)));
                    write.addBatch();
                    if (++count % BATCH == 0) write.executeBatch();
                }
            }
            write.executeBatch();
        }
        return count;
    }

    private static Object value(Object raw) throws SQLException {
        if (raw instanceof Clob clob) return clob.getSubString(1, (int) clob.length());
        if (raw instanceof Blob blob) return blob.getBytes(1, (int) blob.length());
        if (raw instanceof java.util.UUID uuid) return uuid.toString();
        return raw;
    }

    private Report verify(Connection from, Connection to) throws SQLException {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Set<String> sourceTables = new LinkedHashSet<>(tables(from));
        for (String table : tables(to)) {
            if (table.equals(HISTORY) || !sourceTables.contains(table)) continue;
            counts.put(table, new long[] {count(from, table), count(to, table)});
        }
        List<String> mismatched = new ArrayList<>();
        counts.forEach((table, pair) -> { if (pair[0] != pair[1]) mismatched.add(table + " source=" + pair[0] + " target=" + pair[1]); });
        long rows = counts.values().stream().mapToLong(pair -> pair[0]).sum();
        out.println("verified " + counts.size() + " tables, " + rows + " source rows; mismatches: " + mismatched.size());
        mismatched.forEach(line -> out.println("  MISMATCH " + line));
        return new Report(counts.size(), rows, List.copyOf(mismatched));
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static List<String> populatedTables(Connection connection) throws SQLException {
        List<String> populated = new ArrayList<>();
        for (String table : tables(connection)) {
            if (!table.equals(HISTORY) && count(connection, table) > 0) populated.add(table);
        }
        return populated;
    }

    /** Lower-case names of the base tables in the connection's current schema. */
    static List<String> tables(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<String> names = new ArrayList<>();
        try (ResultSet rows = meta.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[] {"TABLE"})) {
            while (rows.next()) names.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static List<String> sharedColumns(Connection from, Connection to, String table) throws SQLException {
        Set<String> source = columns(from, table);
        List<String> shared = new ArrayList<>();
        for (String column : columns(to, table)) if (source.contains(column)) shared.add(column);
        return shared;
    }

    private static Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            for (int index = 1; index <= rows.getMetaData().getColumnCount(); index++) {
                names.add(rows.getMetaData().getColumnLabel(index).toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    public record Endpoint(String url, String user, String password) {
        static Endpoint fromEnv(String prefix) {
            String url = System.getenv(prefix + "_URL");
            if (url == null || url.isBlank()) throw new IllegalArgumentException(prefix + "_URL is required");
            return new Endpoint(url, System.getenv().getOrDefault(prefix + "_USER", ""), System.getenv().getOrDefault(prefix + "_PASSWORD", ""));
        }

        Connection open() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }
    }

    public record Report(int tables, long rows, List<String> mismatches) {
        public boolean matches() {
            return mismatches.isEmpty();
        }
    }
}
