package com.jobproof;

import static org.assertj.core.api.Assertions.*;

import com.jobproof.modules.aibilling.application.*;
import com.jobproof.modules.membership.application.MembershipService;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;

@SpringBootTest
@ActiveProfiles("test")
class CommerceIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired BalanceWalletService wallets;
    @Autowired MembershipService memberships;
    @Autowired CommercePurchaseService purchase;
    @Autowired CommerceCatalogService catalog;
    @Autowired AiUserBillingQueryService queries;
    @Autowired PlatformTransactionManager tx;
    @Autowired com.jobproof.modules.aibilling.web.CommerceController commerceController;
    @Autowired com.jobproof.modules.membership.application.MembershipReminders reminders;

    CurrentAccount account(String role) {
        String id = Ids.newId();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO"
                    + " accounts(id,email,password_hash,status,role,created_at,updated_at,password_changed_at)"
                    + " VALUES(?,?,'test','ACTIVE',?,?,?,?)",
                id,
                id + "@example.com",
                role,
                now,
                now,
                now);
        return new CurrentAccount(id, id + "@example.com", role, "");
    }

    MembershipService.BatchCreated batch() {
        return memberships.createBatch(
                account("ADMIN"),
                new MembershipService.BatchCommand(
                        "CNY test", null, null, 1, null, new BigDecimal("100")));
    }

    @Test
    void zeroWalletNoSubscriptionAndUsdIndependence() {
        var a = account("SEEKER");
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isZero();
        assertThat(queries.subscription(a).status()).isEqualTo("INACTIVE");
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO"
                    + " wallet_account(id,account_id,currency,available_balance,held_balance,version_no,created_at,updated_at)"
                    + " VALUES(?,?,'USD',200,0,0,?,?)",
                Ids.newId(),
                a.accountId(),
                now,
                now);
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isZero();
    }

    @Test
    void concurrentRedeemCreditsExactlyOnceAndRepeatIsSuccessful() throws Exception {
        var a = account("SEEKER");
        var b = batch();
        String code = b.codes().getFirst();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Object> claim =
                    () -> {
                        start.await();
                        return memberships.redeem(a, code);
                    };
            var one = pool.submit(claim);
            var two = pool.submit(claim);
            start.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }
        memberships.redeem(a, code);
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isEqualByComparingTo("100");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM wallet_ledger WHERE account_id=?",
                                Integer.class,
                                a.accountId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT redeemed_count FROM redeem_code_batches WHERE id=?",
                                Integer.class,
                                b.batch().id()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM wallet_ledger l JOIN wallet_account w ON"
                                        + " w.id=l.wallet_account_id WHERE l.account_id=? AND"
                                        + " w.currency='CNY'",
                                Integer.class,
                                a.accountId()))
                .isEqualTo(1);
    }

    @Test
    void concurrentDifferentAccountsOnlyOneWins() throws Exception {
        var a = account("SEEKER");
        var b = account("SEEKER");
        String code = batch().codes().getFirst();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var f1 =
                    pool.submit(
                            () -> {
                                gate.await();
                                try {
                                    memberships.redeem(a, code);
                                    return 1;
                                } catch (AppException e) {
                                    return 0;
                                }
                            });
            var f2 =
                    pool.submit(
                            () -> {
                                gate.await();
                                try {
                                    memberships.redeem(b, code);
                                    return 1;
                                } catch (AppException e) {
                                    return 0;
                                }
                            });
            gate.countDown();
            assertThat(f1.get(20, TimeUnit.SECONDS) + f2.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(
                        wallets.wallet(a.accountId())
                                .availableBalance()
                                .add(wallets.wallet(b.accountId()).availableBalance()))
                .isEqualByComparingTo("100");
    }

    @Test
    void redeemRollbackDoesNotConsumeCodeAndFailureLogCommitsSeparately() {
        var a = account("SEEKER");
        var b = batch();
        String code = b.codes().getFirst();
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(tx)
                                        .execute(
                                                s -> {
                                                    memberships.redeem(a, code);
                                                    throw new IllegalStateException(
                                                            "simulate downstream failure");
                                                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT redeemed_count FROM redeem_code_batches WHERE id=?",
                                Integer.class,
                                b.batch().id()))
                .isZero();
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isZero();
        memberships.redeem(a, code);
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isEqualByComparingTo("100");
        assertThatThrownBy(() -> memberships.redeem(a, "bad-code"))
                .isInstanceOf(AppException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM redeem_attempts WHERE account_id=? AND"
                                        + " succeeded=FALSE",
                                Integer.class,
                                a.accountId()))
                .isEqualTo(1);
    }

    @Test
    void purchaseRepeatVersionAndManualRenewalAreAtomic() {
        var a = account("SEEKER");
        var p = catalog.requirePrice("PRO_MONTH");
        assertThatThrownBy(
                        () ->
                                purchase.purchase(
                                        a.accountId(),
                                        new CommercePurchaseService.Purchase(
                                                p.code(), p.version(), "poor")))
                .isInstanceOf(AppException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM commerce_orders WHERE account_id=?",
                                Integer.class,
                                a.accountId()))
                .isZero();
        wallets.credit(a.accountId(), "seed", new BigDecimal("100"));
        assertThatThrownBy(
                        () ->
                                purchase.purchase(
                                        a.accountId(),
                                        new CommercePurchaseService.Purchase(
                                                p.code(), p.version() + 1, "stale")))
                .isInstanceOf(AppException.class);
        var cmd = new CommercePurchaseService.Purchase(p.code(), p.version(), "first");
        var one = purchase.purchase(a.accountId(), cmd);
        assertThat(purchase.purchase(a.accountId(), cmd)).isEqualTo(one);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='COMMERCE_PURCHASED' AND object_id=?",Integer.class,one.id())).isEqualTo(1);
        var two =
                purchase.purchase(
                        a.accountId(),
                        new CommercePurchaseService.Purchase(p.code(), p.version(), "second"));
        assertThat(two.expiresAt()).isEqualTo(one.expiresAt().plus(Duration.ofDays(30)));
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isEqualByComparingTo("62");
        assertThatThrownBy(
                        () ->
                                purchase.purchase(
                                        a.accountId(),
                                        new CommercePurchaseService.Purchase(
                                                "PERSONAL_CHANNEL", 0, "off")))
                .isInstanceOf(AppException.class);
    }

    @Test
    void personalSubscriptionExtendsAndNeverAutoRenews() {
        var admin = account("ADMIN");
        var a = account("SEEKER");
        var original =
                catalog.prices().stream()
                        .filter(p -> p.code().equals("PERSONAL_CHANNEL"))
                        .findFirst()
                        .orElseThrow();
        var p =
                catalog.save(
                        admin,
                        new CommerceCatalogService.ProductPrice(
                                original.code(),
                                original.name(),
                                new BigDecimal("10"),
                                "CNY",
                                30,
                                true,
                                original.version()));
        try {
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM audit_events WHERE actor_id=? AND"
                                            + " action='COMMERCE_PRICE_UPDATED'",
                                    Integer.class,
                                    admin.accountId()))
                    .isEqualTo(1);
            wallets.credit(a.accountId(), "seed", new BigDecimal("100"));
            var first =
                    purchase.purchase(
                            a.accountId(),
                            new CommercePurchaseService.Purchase(p.code(), p.version(), "first"));
            var second =
                    purchase.purchase(
                            a.accountId(),
                            new CommercePurchaseService.Purchase(p.code(), p.version(), "second"));
            assertThat(second.expiresAt()).isEqualTo(first.expiresAt().plus(Duration.ofDays(30)));
            assertThat(queries.subscription(a).autoRenew()).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE actor_id=? AND action='COMMERCE_PURCHASED'", Integer.class, a.accountId())).isEqualTo(2);
            int oldVersion=queries.subscription(a).versionNo();
            var cancelled=queries.cancelRenewal(a,oldVersion);
            assertThat(cancelled.versionNo()).isEqualTo(oldVersion+1);
            assertThat(queries.subscription(a).versionNo()).isEqualTo(cancelled.versionNo());
            assertThat(wallets.wallet(a.accountId()).availableBalance()).isEqualByComparingTo("80");
        } finally {
            catalog.save(
                    admin,
                    new CommerceCatalogService.ProductPrice(
                            p.code(), p.name(), null, "CNY", 30, false, p.version()));
        }
    }

    @Test
    void holdsSettleReleaseAndRejectOverSettlement() {
        var a = account("SEEKER");
        wallets.credit(a.accountId(), "seed", new BigDecimal("100"));
        String h = wallets.hold(a.accountId(), "request", new BigDecimal("20"));
        assertThat(wallets.hold(a.accountId(), "request", new BigDecimal("20"))).isEqualTo(h);
        assertThatThrownBy(() -> wallets.settle(a.accountId(), h, new BigDecimal("21")))
                .isInstanceOf(AppException.class);
        wallets.settle(a.accountId(), h, new BigDecimal("12"));
        wallets.settle(a.accountId(), h, new BigDecimal("12"));
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isEqualByComparingTo("88");
        assertThat(wallets.wallet(a.accountId()).heldBalance()).isZero();
        String other = wallets.hold(a.accountId(), "other", new BigDecimal("8"));
        wallets.release(a.accountId(), other);
        wallets.release(a.accountId(), other);
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isEqualByComparingTo("88");
    }

    @Test
    void purchaseRollbackRestoresBalanceAndRights() {
        var a = account("SEEKER");
        wallets.credit(a.accountId(), "seed", new BigDecimal("30"));
        var p = catalog.requirePrice("PRO_MONTH");
        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(tx)
                                        .execute(
                                                t -> {
                                                    purchase.purchase(
                                                            a.accountId(),
                                                            new CommercePurchaseService.Purchase(
                                                                    p.code(),
                                                                    p.version(),
                                                                    "rollback"));
                                                    throw new IllegalStateException("downstream");
                                                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isEqualByComparingTo("30");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE actor_id=? AND action='COMMERCE_PURCHASED'", Integer.class, a.accountId())).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM account_memberships WHERE account_id=?",
                                Integer.class,
                                a.accountId()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM commerce_orders WHERE account_id=?",
                                Integer.class,
                                a.accountId()))
                .isZero();
    }

    @Test
    void revokedAndExpiredBalanceCodesNeverCredit() {
        var a = account("SEEKER");
        var admin = account("ADMIN");
        var b = batch();
        memberships.revokeBatch(admin, b.batch().id());
        assertThatThrownBy(() -> memberships.redeem(a, b.codes().getFirst()))
                .isInstanceOf(AppException.class);
        var c = batch();
        jdbc.update(
                "UPDATE redeem_code_batches SET redeem_before=? WHERE id=?",
                Instant.now().minusSeconds(10),
                c.batch().id());
        assertThatThrownBy(() -> memberships.redeem(a, c.codes().getFirst()))
                .isInstanceOf(AppException.class);
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isZero();
    }

    @Test
    void concurrentHoldsCannotOverspend() throws Exception {
        var a = account("SEEKER");
        wallets.credit(a.accountId(), "seed", new BigDecimal("10"));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first =
                    pool.submit(
                            () -> {
                                gate.await();
                                try {
                                    wallets.hold(a.accountId(), "one", new BigDecimal("7"));
                                    return 1;
                                } catch (AppException e) {
                                    return 0;
                                }
                            });
            var second =
                    pool.submit(
                            () -> {
                                gate.await();
                                try {
                                    wallets.hold(a.accountId(), "two", new BigDecimal("7"));
                                    return 1;
                                } catch (AppException e) {
                                    return 0;
                                }
                            });
            gate.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS))
                    .isEqualTo(1);
        }
        assertThat(wallets.wallet(a.accountId()).availableBalance()).isEqualByComparingTo("3");
        assertThat(wallets.wallet(a.accountId()).heldBalance()).isEqualByComparingTo("7");
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminSubscriptionHistoryUsesEffectiveStatusForRowsFiltersAndCounts() {
        Instant updated = Instant.parse("2001-01-01T00:00:00Z");
        Instant past = Instant.now().minus(Duration.ofDays(1));
        for (int i = 0; i < 51; i++)
            subscriptionRow(account("SEEKER").accountId(), past, updated, "ACTIVE");
        subscriptionRow(
                account("SEEKER").accountId(),
                Instant.now().plus(Duration.ofDays(1)),
                updated,
                "ACTIVE");
        var cancelled = account("SEEKER");
        subscriptionRow(cancelled.accountId(), past, updated, "CANCELLED");
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        account("ADMIN"), null));
        try {
            var page0 =
                    (java.util.Map<String, Object>)
                            commerceController
                                    .adminHistory(
                                            null,
                                            "subscriptions",
                                            "EXPIRED",
                                            updated,
                                            updated.plusSeconds(1),
                                            0)
                                    .data();
            var page1 =
                    (java.util.Map<String, Object>)
                            commerceController
                                    .adminHistory(
                                            null,
                                            "subscriptions",
                                            "EXPIRED",
                                            updated,
                                            updated.plusSeconds(1),
                                            1)
                                    .data();
            assertThat(((Number) page0.get("totalElements")).longValue()).isEqualTo(51);
            var rows0 = (java.util.List<java.util.Map<String, Object>>) page0.get("content");
            var rows1 = (java.util.List<java.util.Map<String, Object>>) page1.get("content");
            assertThat(rows0)
                    .hasSize(50)
                    .allSatisfy(row -> assertThat(row.get("status")).isEqualTo("EXPIRED"));
            assertThat(rows1)
                    .hasSize(1)
                    .allSatisfy(row -> assertThat(row.get("status")).isEqualTo("EXPIRED"));
            var active =
                    (java.util.Map<String, Object>)
                            commerceController
                                    .adminHistory(
                                            null,
                                            "subscriptions",
                                            "ACTIVE",
                                            updated,
                                            updated.plusSeconds(1),
                                            0)
                                    .data();
            assertThat(((Number) active.get("totalElements")).longValue()).isEqualTo(1);
            var stopped =
                    (java.util.Map<String, Object>)
                            commerceController
                                    .adminHistory(
                                            null,
                                            "subscriptions",
                                            "CANCELLED",
                                            updated,
                                            updated.plusSeconds(1),
                                            0)
                                    .data();
            assertThat(((Number) stopped.get("totalElements")).longValue()).isEqualTo(1);
            assertThat(queries.subscription(cancelled).status()).isEqualTo("CANCELLED");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void personalReminderKeysetReaches201SameExpiryAccountsAndDeduplicatesRepeats() {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Instant expiry = now.plus(Duration.ofDays(2));
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 201; i++) {
            String id = account("SEEKER").accountId();
            ids.add(id);
            subscriptionRow(id, expiry, now, "ACTIVE");
        }
        reminders.sendDue(now);
        reminders.sendDue(now.plusSeconds(1));
        for (String id : ids)
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM notifications WHERE account_id=? AND"
                                        + " event_id=?",
                                    Integer.class,
                                    id,
                                    "personal-channel-soon:" + expiry.toEpochMilli()))
                    .isEqualTo(1);
    }

    private void subscriptionRow(String accountId, Instant end, Instant updated, String status) {
        String id = Ids.newId();
        jdbc.update(
                "INSERT INTO"
                    + " personal_channel_subscription(id,account_id,status,price,currency,current_period_start,current_period_end,auto_renew,idempotency_key,version_no,created_at,updated_at)"
                    + " VALUES(?,?,?,10,'CNY',?,?,FALSE,?,0,?,?)",
                id,
                accountId,
                status,
                end.minus(Duration.ofDays(30)),
                end,
                id,
                updated,
                updated);
    }

    @Test
    void shopRequiresHttpsAndAdminAndVersion() {
        var admin = account("ADMIN");
        var old = catalog.settings();
        assertThat(old.enabled()).isFalse();
        assertThat(old.url()).isEmpty();
        assertThat(old.name()).isEqualTo("链动小铺");
        assertThat(old.description()).contains("外部小铺", "返回本站钱包");
        assertThatThrownBy(
                        () ->
                                catalog.settings(
                                        admin,
                                        new CommerceCatalogService.ShopSettings(
                                                true,
                                                "http://example.com",
                                                old.name(),
                                                old.description(),
                                                old.version())))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> catalog.settings(account("SEEKER"), old))
                .isInstanceOf(AppException.class);
        var saved =
                catalog.settings(
                        admin,
                        new CommerceCatalogService.ShopSettings(
                                true,
                                "https://example.com/store",
                                "自定义小铺",
                                "外部购买后返回钱包充值",
                                old.version()));
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.name()).isEqualTo("自定义小铺");
        assertThat(saved.description()).isEqualTo("外部购买后返回钱包充值");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM audit_events WHERE actor_id=? AND"
                                        + " action='COMMERCE_SHOP_UPDATED'",
                                Integer.class,
                                admin.accountId()))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                catalog.settings(
                                        admin,
                                        new CommerceCatalogService.ShopSettings(
                                                true,
                                                saved.url(),
                                                " ",
                                                saved.description(),
                                                saved.version())))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(
                        () ->
                                catalog.settings(
                                        admin,
                                        new CommerceCatalogService.ShopSettings(
                                                true,
                                                saved.url(),
                                                saved.name(),
                                                "x".repeat(501),
                                                saved.version())))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> catalog.settings(admin, old)).isInstanceOf(AppException.class);
        catalog.settings(
                admin,
                new CommerceCatalogService.ShopSettings(
                        false, "", old.name(), old.description(), saved.version()));
    }
}
