package com.jobproof.modules.aiconfig.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AiConfigRules {
    public static final String CURRENCY = "USD";
    public static final int MONEY_SCALE = 8;
    public static final int MAX_PAGE_SIZE = 100;

    private AiConfigRules() {
    }

    public static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
