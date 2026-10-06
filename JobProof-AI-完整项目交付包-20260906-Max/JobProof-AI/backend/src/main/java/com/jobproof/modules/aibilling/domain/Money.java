package com.jobproof.modules.aibilling.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) implements Comparable<Money> {
    public static final int SCALE = 8;
    public static final Money ZERO = new Money(BigDecimal.ZERO);
    public Money { Objects.requireNonNull(amount, "amount"); amount = amount.setScale(SCALE, RoundingMode.HALF_UP); }
    public static Money usd(String amount) { return new Money(new BigDecimal(amount)); }
    public Money add(Money other) { return new Money(amount.add(other.amount)); }
    public Money subtract(Money other) { return new Money(amount.subtract(other.amount)); }
    public Money multiply(long value) { return new Money(amount.multiply(BigDecimal.valueOf(value))); }
    public boolean isNegative() { return amount.signum() < 0; }
    public boolean isPositive() { return amount.signum() > 0; }
    @Override public int compareTo(Money other) { return amount.compareTo(other.amount); }
}