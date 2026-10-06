package com.jobproof.modules.identity.domain;

public record VerificationDeliveryReceipt(
        VerificationMode mode,
        String providerRequestId) {

    public static VerificationDeliveryReceipt local() {
        return new VerificationDeliveryReceipt(VerificationMode.LOCAL_CODE, null);
    }

    public static VerificationDeliveryReceipt providerCheck(String providerRequestId) {
        return new VerificationDeliveryReceipt(VerificationMode.PROVIDER_CHECK, providerRequestId);
    }
}

