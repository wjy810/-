package com.jobproof.modules.identity.domain;

public interface VerificationDeliveryPort {
    String providerCode();

    boolean supports(VerificationChannel channel);

    boolean available();

    default VerificationMode deliveryMode() {
        return VerificationMode.LOCAL_CODE;
    }

    VerificationDeliveryReceipt deliver(VerificationDeliveryMessage message);

    default boolean verify(
            String destination,
            VerificationPurpose purpose,
            String code,
            String providerRequestId) {
        return false;
    }
}
