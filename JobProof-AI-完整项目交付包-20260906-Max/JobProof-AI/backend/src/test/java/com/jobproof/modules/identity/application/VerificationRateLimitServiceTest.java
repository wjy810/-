package com.jobproof.modules.identity.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.shared.error.AppException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class VerificationRateLimitServiceTest {

    @Test
    void localLimiterEnforcesAddressAndIpBudgets() {
        JobProofProperties properties = properties("local");
        properties.getVerification().setEmailAddressDailyLimit(2);
        properties.getVerification().setEmailIpHourlyLimit(2);
        VerificationRateLimitService service = service(properties);
        Instant now = Instant.parse("2026-08-26T12:00:00Z");

        assertDoesNotThrow(() -> service.checkAndRecord(VerificationChannel.EMAIL, "address-a", "ip-a", now));
        assertDoesNotThrow(() -> service.checkAndRecord(VerificationChannel.EMAIL, "address-a", "ip-b", now));
        AppException address = assertThrows(AppException.class,
                () -> service.checkAndRecord(VerificationChannel.EMAIL, "address-a", "ip-c", now));
        assertEquals("VERIFICATION_RATE_LIMITED", address.reason());

        assertDoesNotThrow(() -> service.checkAndRecord(VerificationChannel.EMAIL, "address-b", "ip-shared", now));
        assertDoesNotThrow(() -> service.checkAndRecord(VerificationChannel.EMAIL, "address-c", "ip-shared", now));
        AppException ip = assertThrows(AppException.class,
                () -> service.checkAndRecord(VerificationChannel.EMAIL, "address-d", "ip-shared", now));
        assertEquals("VERIFICATION_RATE_LIMITED", ip.reason());
    }

    @Test
    void redisModeFailsClosedWhenRedisIsUnavailable() {
        AppException exception = assertThrows(AppException.class, () -> service(properties("redis"))
                .checkAndRecord(VerificationChannel.SMS, "address", "ip", Instant.now()));

        assertEquals("VERIFICATION_RATE_LIMIT_UNAVAILABLE", exception.reason());
    }

    private static VerificationRateLimitService service(JobProofProperties properties) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new VerificationRateLimitService(properties, provider);
    }

    private static JobProofProperties properties(String mode) {
        JobProofProperties properties = new JobProofProperties();
        properties.getVerification().setRateLimitMode(mode);
        return properties;
    }
}
