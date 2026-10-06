package com.jobproof.modules.career.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.infrastructure.queue.OutboxService;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.page.PageQuery;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class CareerLibraryConsistencyTest {
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
    private final CurrentAccount current = new CurrentAccount("owner", "owner@example.com", "SEEKER", "session");
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<Runnable> competingWrite = new AtomicReference<>();
    private final OutboxService outbox = mock(OutboxService.class);
    private final AuditService audit = mock(AuditService.class);
    private JdbcTemplate jdbc;
    private JdbcTemplate otherConnection;
    private CountingDataSource source;
    private TransactionTemplate tx;
    private CareerLibraryService service;

    @BeforeEach
    void setup() throws Exception {
        String url = "jdbc:h2:mem:career-consistency-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        source = new CountingDataSource(url);
        jdbc = new JdbcTemplate(source);
        otherConnection = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        String migration = new ClassPathResource("db/migration/V25__career_library_product_contraction.sql")
                .getContentAsString(StandardCharsets.UTF_8).split("CREATE TABLE career_library_files")[0];
        new ResourceDatabasePopulator(new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8))).execute(source);
        jdbc.execute("ALTER TABLE career_library_profiles ADD avatar_file_id VARCHAR(36)");
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        service = new CareerLibraryService(jdbc, mapper, outbox, audit, () -> {
            Runnable competitor = competingWrite.getAndSet(null);
            if (competitor != null) competitor.run();
            return NOW;
        }, mock(PrivateFileJpaRepository.class), mock(ObjectStoragePort.class), 1024);
        service.profile(current);
    }

    @Test
    void profileUpdateRejectsCommittedWriteAfterVersionReadWithoutPublishingChange() {
        competingWrite.set(() -> otherConnection.update("UPDATE career_library_profiles SET summary_text='winner',version_no=1,snapshot_version=1 WHERE account_id='owner'"));
        AppException error = assertThrows(AppException.class, () -> tx.execute(status -> service.updateProfile(current,
                new CareerLibraryService.ProfileWrite(mapper.createObjectNode(), mapper.createObjectNode(),
                        mapper.createObjectNode(), "loser", 0))));
        assertEquals("VERSION_CONFLICT", error.reason());
        assertEquals("winner", service.profile(current).summary());
        assertEquals(1, service.profile(current).version());
        verifyNoInteractions(outbox, audit);
    }

    @Test
    void firstProfileReadSucceedsWhenAnotherRequestCreatesTheProfileAfterTheExistenceCheck() {
        CurrentAccount newcomer = new CurrentAccount("new-owner", "new-owner@example.com", "SEEKER", "session-2");
        competingWrite.set(() -> otherConnection.update(
                "INSERT INTO career_library_profiles(account_id,basics_json,intentions_json,preferences_json,summary_text,snapshot_version,version_no,created_at,updated_at,avatar_file_id) VALUES(?,'{}','{}','{}',NULL,0,0,?,?,NULL)",
                newcomer.accountId(), NOW, NOW));

        CareerLibraryService.ProfileView profile = tx.execute(status -> service.profile(newcomer));

        assertEquals(newcomer.accountId(), profile.accountId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM career_library_profiles WHERE account_id=?", Integer.class,
                newcomer.accountId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "archive", "restore", "reorder"})
    void recordMutationRejectsCommittedWriteAfterVersionRead(String operation) {
        insertRecord("record-1", "owner");
        competingWrite.set(() -> otherConnection.update("UPDATE career_library_records SET title='winner',version_no=1 WHERE id='record-1'"));
        AppException error = assertThrows(AppException.class, () -> tx.execute(status -> switch (operation) {
            case "update" -> service.updateRecord(current, "record-1", new CareerLibraryService.RecordWrite(
                    null, "loser", null, null, null, null, null, null, null, null, null, null, null, 0));
            case "archive" -> service.archiveRecord(current, "record-1", 0);
            case "restore" -> service.restoreRecord(current, "record-1", 0);
            default -> service.reorder(current, List.of(new CareerLibraryService.RecordOrder("record-1", 7, 0)));
        }));
        assertEquals("VERSION_CONFLICT", error.reason());
        assertEquals("winner", service.record(current, "record-1").title());
        assertEquals(1, service.record(current, "record-1").version());
        assertEquals(0, service.profile(current).snapshotVersion());
        verifyNoInteractions(outbox, audit);
    }

    @Test
    void reorderRollsBackEarlierItemsWhenALaterItemConflicts() {
        insertRecord("record-1", "owner");
        insertRecord("record-2", "owner");
        competingWrite.set(() -> competingWrite.set(() -> otherConnection.update(
                "UPDATE career_library_records SET title='winner',version_no=1 WHERE id='record-2'")));
        AppException error = assertThrows(AppException.class, () -> tx.execute(status -> service.reorder(current,
                List.of(new CareerLibraryService.RecordOrder("record-1", 7, 0),
                        new CareerLibraryService.RecordOrder("record-2", 8, 0)))));
        assertEquals("VERSION_CONFLICT", error.reason());
        assertEquals(0, service.record(current, "record-1").sortOrder());
        assertEquals(0, service.record(current, "record-1").version());
        assertEquals("winner", service.record(current, "record-2").title());
        assertEquals(0, service.profile(current).snapshotVersion());
        verifyNoInteractions(outbox, audit);
    }

    @Test
    void referenceCountsRemainAccurateWithConstantQueryCountAcrossPageSizes() {
        for (int i = 0; i < 20; i++) insertRecord("record-" + i, "owner");
        insertRecord("foreign", "another-owner");
        jdbc.update("INSERT INTO career_library_record_refs VALUES('ref-1','record-0','resume-1',1,?)", NOW);
        jdbc.update("INSERT INTO career_library_record_refs VALUES('ref-2','record-0','resume-2',1,?)", NOW);
        jdbc.update("INSERT INTO career_library_record_refs VALUES('ref-3','record-0','resume-3',0,?)", NOW);
        jdbc.update("INSERT INTO career_library_record_refs VALUES('ref-4','foreign','resume-4',1,?)", NOW);
        source.queries = 0;
        service.records(current, null, "ALL", null, pageSize(1));
        int smallPageQueries = source.queries;
        source.queries = 0;
        var page = service.records(current, null, "ALL", null, pageSize(20));
        assertEquals(20, page.total());
        assertEquals(2, page.items().stream().filter(record -> record.id().equals("record-0")).findFirst().orElseThrow().resumeReferenceCount());
        assertEquals(0, page.items().stream().filter(record -> record.id().equals("record-1")).findFirst().orElseThrow().resumeReferenceCount());
        assertEquals(smallPageQueries, source.queries, "reference count queries must not grow with result count");
        assertTrue(source.queries <= 3, "count + page + at most one grouped reference query");
    }

    private void insertRecord(String id, String accountId) {
        jdbc.update("INSERT INTO career_library_records(id,account_id,record_type,title,payload_json,pending_supplement,source_type,status,confirmed,sort_order,version_no,created_at,updated_at) VALUES(?,?,'PROJECT','original','{}',0,'USER','ACTIVE',1,0,0,?,?)", id, accountId, NOW, NOW);
    }

    private static PageQuery pageSize(int size) {
        PageQuery page = new PageQuery();
        page.setSize(size);
        return page;
    }

    private static final class CountingDataSource extends DriverManagerDataSource {
        int queries;
        CountingDataSource(String url) { super(url, "sa", ""); }
        @Override public Connection getConnection() throws SQLException {
            Connection delegate = super.getConnection();
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && sql.stripLeading().startsWith("SELECT")) queries++;
                        try { return method.invoke(delegate, args); }
                        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                    });
        }
    }
}
