package com.jobproof.modules.jobapply.domain;

import com.jobproof.shared.error.AppException;
import java.util.Locale;

/**
 * Where an application stands. The open stages are ordered so the furthest one reached can be kept
 * for the funnel; CLOSED ends an application with an outcome and has no rank.
 */
public enum ApplicationStage {
    WISHLIST(0),
    APPLIED(1),
    ASSESSMENT(2),
    INTERVIEW(3),
    OFFER(4),
    CLOSED(-1);

    private final int rank;

    ApplicationStage(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean open() {
        return this != CLOSED;
    }

    /** The further of two open stages; a closed application keeps how far it had come. */
    public static ApplicationStage furthest(ApplicationStage reached, ApplicationStage next) {
        if (next == null || !next.open()) return reached;
        if (reached == null) return next;
        return next.rank > reached.rank ? next : reached;
    }

    public static ApplicationStage parse(String value) {
        if (value == null || value.isBlank()) throw AppException.user("APPLICATION_STAGE_INVALID", "请选择投递阶段");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw AppException.user("APPLICATION_STAGE_INVALID", "不支持的投递阶段");
        }
    }
}
