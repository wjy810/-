package com.jobproof.modules.aibilling.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingDomainTest {
    @Test void walletSupportsFreezeReleaseDebtAndRecovery() { WalletAccount w = new WalletAccount(UUID.randomUUID(), Money.usd("10"), Money.ZERO, Money.ZERO, 0, false).hold(Money.usd("3")).release(Money.usd("1")).addPendingDebit(Money.usd("4")).credit(Money.usd("5")).recoverPendingDebit(); assertEquals(Money.usd("9"), w.available()); assertFalse(w.systemChannelsSuspended()); }
    @Test void subscriptionLifecycleHonorsExpiryCancellationAndRenewalBoundaries() {
        UUID account = UUID.randomUUID(); Instant end = Instant.parse("2030-01-01T00:00:00Z");
        Subscription active = new Subscription(UUID.randomUUID(), account, Subscription.Status.ACTIVE, Instant.EPOCH, end, false);
        assertTrue(active.allowsPersonalChannel(end.minusSeconds(1), 4)); assertFalse(active.allowsPersonalChannel(end, 4));
        assertSame(active, active.expire(end.minusSeconds(1))); assertEquals(Subscription.Status.EXPIRED, active.expire(end).status());
        assertThrows(IllegalStateException.class, () -> active.cancelAtExpiry().renew(end.plusSeconds(1)));
        assertThrows(IllegalStateException.class, () -> active.expire(end).markPastDue());
        assertEquals(Subscription.Status.PAST_DUE, active.markPastDue().status());
    }
    @Test void billingRefundBoundaryTracksCumulativeRefundableBalance() {
        BillingRecord bill = new BillingRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Money.usd("5"), Money.ZERO, BillingRecord.Status.SETTLED);
        BillingRecord partial = bill.refund(Money.usd("2")); assertEquals(Money.usd("3"), partial.refundable()); assertEquals(BillingRecord.Status.PARTIALLY_REFUNDED, partial.status());
        BillingRecord full = partial.refund(Money.usd("3")); assertEquals(Money.ZERO, full.refundable()); assertEquals(BillingRecord.Status.REFUNDED, full.status());
        assertThrows(IllegalArgumentException.class, () -> partial.refund(Money.usd("4")));
        assertThrows(IllegalArgumentException.class, () -> full.refund(Money.usd("0.01")));
    }
    @Test void calculatesFrozenTextAndMediaFormulaAtEightDecimalScale() { UsagePricing p = new UsagePricing(Money.usd("2"), Money.usd("8"), new java.math.BigDecimal("0.25"), new java.math.BigDecimal("1.5"), new java.math.BigDecimal("1.2"), Money.usd("0.05")); assertEquals(Money.usd("0.01680000"), p.estimateText(1000, 1000)); assertEquals(Money.usd("0.00870000"), p.actualText(1000, 500, 500)); assertEquals(Money.usd("0.12000000"), p.estimateMedia(2)); assertEquals(Money.usd("0.06000000"), p.actualMedia(1)); }
}
