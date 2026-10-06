package com.jobproof.modules.aibilling.domain;

import java.time.Instant;
import java.util.UUID;

public record Subscription(UUID id, UUID accountId, Status status, Instant periodStart, Instant periodEnd, boolean cancelAtPeriodEnd) {
    public static final Money MONTHLY_PRICE = Money.usd("9.90");
    public static final int MAX_PERSONAL_CHANNELS = 5;
    public enum Status { ACTIVE, PAST_DUE, EXPIRED }
    public Subscription cancelAtExpiry() { return new Subscription(id, accountId, status, periodStart, periodEnd, true); }
    public Subscription renew(Instant nextEnd) { if (cancelAtPeriodEnd || status == Status.EXPIRED) throw new IllegalStateException("Subscription cannot renew"); return new Subscription(id, accountId, Status.ACTIVE, periodEnd, nextEnd, false); }
    public Subscription markPastDue() { if (status == Status.EXPIRED) throw new IllegalStateException("Expired subscription cannot become past due"); return new Subscription(id, accountId, Status.PAST_DUE, periodStart, periodEnd, cancelAtPeriodEnd); }
    public Subscription expire(Instant now) { return now.isBefore(periodEnd) ? this : new Subscription(id, accountId, Status.EXPIRED, periodStart, periodEnd, cancelAtPeriodEnd); }
    public boolean allowsPersonalChannel(Instant now, int count) { return status == Status.ACTIVE && now.isBefore(periodEnd) && count < MAX_PERSONAL_CHANNELS; }
}