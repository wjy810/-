package com.jobproof.modules.aibilling.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record UsagePricing(Money inputPerMillionTokens, Money outputPerMillionTokens,
                           BigDecimal cacheRate, BigDecimal completionRate,
                           BigDecimal channelRate, Money fixedMediaPrice) {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public UsagePricing {
        if (cacheRate.signum() < 0 || completionRate.signum() < 0 || channelRate.signum() < 0) {
            throw new IllegalArgumentException("Pricing multipliers cannot be negative");
        }
    }

    public Money estimateText(long maximumInputTokens, long maximumOutputTokens) {
        return textCost(maximumInputTokens, 0, maximumOutputTokens);
    }

    public Money estimateMedia(long requestedUnits) {
        return mediaCost(requestedUnits);
    }

    public Money actualText(long inputTokens, long cachedInputTokens, long outputTokens) {
        return textCost(inputTokens, cachedInputTokens, outputTokens);
    }

    public Money actualMedia(long successfulUnits) {
        return mediaCost(successfulUnits);
    }

    private Money textCost(long input, long cachedInput, long output) {
        requireNonNegative(input, cachedInput, output);
        if (cachedInput > input) {
            throw new IllegalArgumentException("Cached input cannot exceed total input");
        }
        BigDecimal ordinaryInput = inputPerMillionTokens.amount().multiply(BigDecimal.valueOf(input - cachedInput));
        BigDecimal cached = inputPerMillionTokens.amount().multiply(BigDecimal.valueOf(cachedInput)).multiply(cacheRate);
        BigDecimal completion = outputPerMillionTokens.amount().multiply(BigDecimal.valueOf(output)).multiply(completionRate);
        return new Money(ordinaryInput.add(cached).add(completion).divide(MILLION, Money.SCALE, RoundingMode.HALF_UP).multiply(channelRate));
    }

    private Money mediaCost(long units) {
        requireNonNegative(units);
        return new Money(fixedMediaPrice.amount().multiply(BigDecimal.valueOf(units)).multiply(channelRate));
    }

    private static void requireNonNegative(long... values) {
        for (long value : values) if (value < 0) throw new IllegalArgumentException("Usage cannot be negative");
    }
}