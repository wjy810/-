package com.jobproof.modules.aibilling.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jobproof.shared.error.AppException;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class BalanceWalletCurrentReadTest {
    private final SnapshotJdbc jdbc = new SnapshotJdbc();
    private final BalanceWalletService wallet = new BalanceWalletService(jdbc, () -> Instant.EPOCH);

    @Test
    void snapshotBalanceCannotFundAnOverspendingHold() {
        // Old snapshot has 10, while the current committed balance after another debit is 3.
        assertThatThrownBy(() -> wallet.hold("account", "new", new BigDecimal("7")))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.reason()).isEqualTo("WALLET_INSUFFICIENT"));
        assertThat(jdbc.updates).isZero();
        assertThat(jdbc.reads).allMatch(sql -> sql.endsWith("FOR UPDATE"));
    }

    @Test
    void snapshotBalanceCannotFundAnOverspendingDebit() {
        assertThatThrownBy(() -> wallet.debit("account", "new", new BigDecimal("7")))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.reason()).isEqualTo("WALLET_INSUFFICIENT"));
        assertThat(jdbc.updates).isZero();
        assertThat(jdbc.reads).allMatch(sql -> sql.endsWith("FOR UPDATE"));
    }

    @Test
    void existingCurrentLedgerEntryPreventsReplayCreditDespiteMissingSnapshotEntry() {
        jdbc.currentLedger = new BigDecimal("5");
        wallet.credit("account", "prior-credit", new BigDecimal("5"));
        assertThat(jdbc.updates).isZero();
        assertThat(jdbc.reads)
                .anyMatch(sql -> sql.contains("wallet_ledger") && sql.endsWith("FOR UPDATE"));
    }

    @Test
    void existingCurrentHoldIsReusedDespiteMissingSnapshotEntry() {
        jdbc.currentHold = true;
        assertThat(wallet.hold("account", "prior-hold", new BigDecimal("7")))
                .isEqualTo("existing-hold");
        assertThat(jdbc.updates).isZero();
        assertThat(jdbc.reads)
                .anyMatch(sql -> sql.contains("wallet_hold") && sql.endsWith("FOR UPDATE"));
    }

    @Test
    void walletReturnsCurrentBalanceEvenWhenTransactionSnapshotIsOlder() {
        assertThat(wallet.wallet("account").availableBalance()).isEqualByComparingTo("3");
        assertThat(jdbc.updates).isZero();
    }

    /**
     * Consistent reads see stale balance/absent keys; locking reads see the current committed rows.
     */
    private static class SnapshotJdbc extends JdbcTemplate {
        final List<String> reads = new ArrayList<>();
        BigDecimal currentLedger;
        boolean currentHold;
        int updates;

        @Override
        public int update(String sql, Object... args) {
            updates++;
            return 1;
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            reads.add(sql);
            if (sql.contains("FROM accounts")) return List.of(type.cast("account"));
            if (sql.contains("wallet_ledger")
                    && sql.endsWith("FOR UPDATE")
                    && currentLedger != null) return List.of(type.cast(currentLedger));
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            reads.add(sql);
            boolean current = sql.endsWith("FOR UPDATE");
            try {
                ResultSet r = mock(ResultSet.class);
                if (sql.contains("wallet_account")) {
                    when(r.getString("id")).thenReturn("wallet");
                    when(r.getBigDecimal("available_balance"))
                            .thenReturn(new BigDecimal(current ? "3" : "10"));
                    when(r.getBigDecimal("held_balance")).thenReturn(BigDecimal.ZERO);
                    when(r.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.EPOCH));
                } else if (sql.contains("wallet_hold") && current && currentHold) {
                    when(r.getString(1)).thenReturn("existing-hold");
                    when(r.getBigDecimal(2)).thenReturn(new BigDecimal("7"));
                    when(r.getString(3)).thenReturn("HELD");
                } else return List.of();
                return List.of(mapper.mapRow(r, 0));
            } catch (SQLException e) {
                throw new AssertionError(e);
            }
        }
    }
}
