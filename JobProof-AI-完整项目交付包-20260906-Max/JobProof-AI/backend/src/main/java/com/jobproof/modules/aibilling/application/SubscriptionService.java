package com.jobproof.modules.aibilling.application;

import com.jobproof.modules.aibilling.domain.LedgerEntry;
import com.jobproof.modules.aibilling.domain.Subscription;
import com.jobproof.modules.aibilling.domain.WalletAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

public final class SubscriptionService {
    private final SubscriptionStore subscriptions;
    private final BillingService billing;
    private final Clock clock;

    public SubscriptionService(SubscriptionStore subscriptions, BillingService billing, Clock clock) {
        this.subscriptions = subscriptions;
        this.billing = billing;
        this.clock = clock;
    }

    public Subscription activate(UUID accountId, String idempotencyKey) {
        billing.adminDebit(accountId, Subscription.MONTHLY_PRICE, "subscription", idempotencyKey);
        Instant start = Instant.now(clock);
        Instant end = ZonedDateTime.ofInstant(start, ZoneOffset.UTC).plusMonths(1).toInstant();
        Subscription subscription = new Subscription(
                UUID.randomUUID(), accountId, Subscription.Status.ACTIVE, start, end, false);
        subscriptions.save(subscription);
        return subscription;
    }

    public Subscription cancelAtPeriodEnd(UUID accountId) {
        Subscription subscription = require(accountId).cancelAtExpiry();
        subscriptions.save(subscription);
        return subscription;
    }

    public Subscription renew(UUID accountId, String idempotencyKey) {
        Subscription current = require(accountId);
        try {
            billing.adminDebit(accountId, Subscription.MONTHLY_PRICE,
                    current.id().toString(), idempotencyKey);
            Instant nextEnd = ZonedDateTime.ofInstant(current.periodEnd(), ZoneOffset.UTC)
                    .plusMonths(1).toInstant();
            Subscription renewed = current.renew(nextEnd);
            subscriptions.save(renewed);
            return renewed;
        } catch (WalletAccount.InsufficientFundsException exception) {
            Subscription pastDue = current.markPastDue();
            subscriptions.save(pastDue);
            return pastDue;
        }
    }

    public Subscription expireDue(UUID accountId) {
        Subscription subscription = require(accountId).expire(Instant.now(clock));
        subscriptions.save(subscription);
        return subscription;
    }

    private Subscription require(UUID accountId) {
        return subscriptions.findByAccountId(accountId).orElseThrow();
    }
}
