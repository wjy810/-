package com.jobproof.modules.aibilling.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jobproof.shared.error.AppException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class CommercePurchaseServiceTest {
    private static final String ACCOUNT = "test-account";
    private static final String PRICE_READ =
            "SELECT code,name,amount,currency,days,enabled,version FROM commerce_prices WHERE"
                    + " code=? FOR UPDATE";
    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private final SnapshotJdbc jdbc = new SnapshotJdbc();
    private final BalanceWalletService wallet = mock(BalanceWalletService.class);
    private final CommercePurchaseService service =
            new CommercePurchaseService(jdbc, wallet, () -> NOW, org.mockito.Mockito.mock(com.jobproof.modules.audit.application.AuditService.class));

    @Test
    void staleSnapshotVersionCannotAuthorizeChangedLockedPrice() {
        // The transaction snapshot still advertises v1/19, but the current locked row is v2/29.
        jdbc.current = price("29", true, "CNY", 2);
        assertThatThrownBy(
                        () ->
                                service.purchase(
                                        ACCOUNT,
                                        new CommercePurchaseService.Purchase(
                                                "PRO_MONTH", 1, "old-quote")))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.reason()).isEqualTo("COMMERCE_PRICE_CHANGED"));
        verify(wallet, never()).debit(anyString(), anyString(), any());
        assertThat(jdbc.priceReads).containsExactly(PRICE_READ);
    }

    @Test
    void purchaseDebitsAndSnapshotsTheCurrentLockedPrice() {
        jdbc.current = price("29", true, "CNY", 2);
        var order =
                service.purchase(
                        ACCOUNT, new CommercePurchaseService.Purchase("PRO_MONTH", 2, "new-quote"));
        assertThat(order.amount()).isEqualByComparingTo("29");
        assertThat(order.priceVersion()).isEqualTo(2);
        verify(wallet).debit(eq(ACCOUNT), startsWith("ORDER:"), eq(new BigDecimal("29")));
        assertThat(jdbc.priceReads).containsExactly(PRICE_READ);
    }

    @ParameterizedTest
    @MethodSource("unavailableCurrentPrices")
    void rejectsUnavailableLockedRowEvenWhenSnapshotIsPurchasable(
            CommerceCatalogService.ProductPrice current) {
        jdbc.current = current;
        assertThatThrownBy(
                        () ->
                                service.purchase(
                                        ACCOUNT,
                                        new CommercePurchaseService.Purchase(
                                                "PRO_MONTH", 1, "quote")))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.reason()).isEqualTo("COMMERCE_PRICE_UNAVAILABLE"));
        verify(wallet, never()).debit(anyString(), anyString(), any());
        assertThat(jdbc.priceReads).containsExactly(PRICE_READ);
    }

    @Test
    void validatesDaysFromTheCurrentLockedRow() {
        jdbc.current =
                new CommerceCatalogService.ProductPrice(
                        "PRO_MONTH", "月卡", new BigDecimal("19"), "CNY", 0, true, 1);
        assertThatThrownBy(
                        () ->
                                service.purchase(
                                        ACCOUNT,
                                        new CommercePurchaseService.Purchase(
                                                "PRO_MONTH", 1, "invalid-days")))
                .isInstanceOfSatisfying(
                        AppException.class,
                        e -> assertThat(e.reason()).isEqualTo("COMMERCE_PRICE_UNAVAILABLE"));
        verify(wallet, never()).debit(anyString(), anyString(), any());
    }

    static Stream<CommerceCatalogService.ProductPrice> unavailableCurrentPrices() {
        return Stream.of(
                price("19", false, "CNY", 1),
                price(null, true, "CNY", 1),
                price("-1", true, "CNY", 1),
                price("19", true, "USD", 1),
                null);
    }

    private static CommerceCatalogService.ProductPrice price(
            String amount, boolean enabled, String currency, int version) {
        return new CommerceCatalogService.ProductPrice(
                "PRO_MONTH",
                "月卡",
                amount == null ? null : new BigDecimal(amount),
                currency,
                30,
                enabled,
                version);
    }

    /**
     * Simulates MySQL's distinction between an old consistent-read snapshot and current locking
     * reads.
     */
    private static class SnapshotJdbc extends JdbcTemplate {
        final CommerceCatalogService.ProductPrice snapshot = price("19", true, "CNY", 1);
        CommerceCatalogService.ProductPrice current;
        final List<String> priceReads = new ArrayList<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            return type.cast("SEEKER");
        }

        @Override
        public int update(String sql, Object... args) {
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            if (!sql.contains("commerce_prices")) return List.of();
            priceReads.add(sql);
            var p = sql.contains("FOR UPDATE") ? current : snapshot;
            if (p == null) return List.of();
            try {
                ResultSet r = mock(ResultSet.class);
                when(r.getString("code")).thenReturn(p.code());
                when(r.getString("name")).thenReturn(p.name());
                when(r.getString("currency")).thenReturn(p.currency());
                when(r.getBigDecimal("amount")).thenReturn(p.amount());
                when(r.getInt("days")).thenReturn(p.days());
                when(r.getInt("version")).thenReturn(p.version());
                when(r.getBoolean("enabled")).thenReturn(p.enabled());
                return List.of(mapper.mapRow(r, 0));
            } catch (SQLException e) {
                throw new AssertionError(e);
            }
        }
    }
}
