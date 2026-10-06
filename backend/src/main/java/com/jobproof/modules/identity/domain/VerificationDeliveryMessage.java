package com.jobproof.modules.identity.domain;

public record VerificationDeliveryMessage(
        VerificationChannel channel,
        String destination,
        VerificationPurpose purpose,
        String code,
        long ttlMinutes) {
}
