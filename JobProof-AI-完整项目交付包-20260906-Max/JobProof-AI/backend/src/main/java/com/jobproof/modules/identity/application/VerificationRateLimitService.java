package com.jobproof.modules.identity.application;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.shared.error.AppException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class VerificationRateLimitService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);

    private final JobProofProperties properties;
    private final StringRedisTemplate redis;
    private final Map<String, LocalCounter> local = new ConcurrentHashMap<>();

    public VerificationRateLimitService(JobProofProperties properties, ObjectProvider<StringRedisTemplate> redis) {
        this.properties = properties;
        this.redis = redis.getIfAvailable();
    }

    public void checkAndRecord(
            VerificationChannel channel,
            String destinationHash,
            String ipHash,
            Instant now) {
        int addressLimit = channel == VerificationChannel.EMAIL
                ? properties.getVerification().getEmailAddressDailyLimit()
                : properties.getVerification().getSmsAddressDailyLimit();
        int ipLimit = channel == VerificationChannel.EMAIL
                ? properties.getVerification().getEmailIpHourlyLimit()
                : properties.getVerification().getSmsIpHourlyLimit();
        String prefix = channel.name().toLowerCase();
        increment(prefix + ":address-day:" + DAY.format(now) + ":" + destinationHash,
                addressLimit, Duration.ofDays(2), now);
        increment(prefix + ":ip-hour:" + HOUR.format(now) + ":" + ipHash,
                ipLimit, Duration.ofHours(2), now);
    }

    private void increment(String key, int limit, Duration ttl, Instant now) {
        if (limit < 1) throw rateLimited();
        if ("local".equalsIgnoreCase(properties.getVerification().getRateLimitMode())) {
            LocalCounter next = local.compute(key, (ignored, current) -> {
                if (current == null || !current.expiresAt().isAfter(now)) {
                    return new LocalCounter(1, now.plus(ttl));
                }
                return new LocalCounter(current.count() + 1, current.expiresAt());
            });
            if (next.count() > limit) throw rateLimited();
            return;
        }
        try {
            if (redis == null) {
                throw new IllegalStateException("Redis rate limiter is unavailable");
            }
            Long count = redis.opsForValue().increment(key);
            if (Long.valueOf(1L).equals(count)) redis.expire(key, ttl);
            if (count == null || count > limit) throw rateLimited();
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AppException.dependency("VERIFICATION_RATE_LIMIT_UNAVAILABLE", "验证服务暂时不可用，请稍后重试");
        }
    }

    private static AppException rateLimited() {
        return AppException.rateLimited("VERIFICATION_RATE_LIMITED", "请求过于频繁，请稍后再试");
    }

    private record LocalCounter(long count, Instant expiresAt) {
    }
}
