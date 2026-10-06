package com.jobproof.modules.identity.application;

import com.jobproof.infrastructure.config.LoginRateLimitProperties;
import com.jobproof.shared.security.Tokens;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Counts all attempts before BCrypt. Rejection never refreshes TTL or permanently locks an account. */
@Service
public class LoginRateLimitService {
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local ip = tonumber(redis.call('GET', KEYS[1]) or '0')
            local account = tonumber(redis.call('GET', KEYS[2]) or '0')
            if ip >= tonumber(ARGV[1]) or account >= tonumber(ARGV[2]) then return 0 end
            for i=1,2 do
              local count = redis.call('INCR', KEYS[i])
              if count == 1 then redis.call('EXPIRE', KEYS[i], ARGV[3]) end
            end
            return 1
            """, Long.class);
    private final LoginRateLimitProperties properties;
    private final StringRedisTemplate redis;
    private final ClockPort clock;
    private final Map<String, Counter> local = new HashMap<>();

    public LoginRateLimitService(LoginRateLimitProperties properties, ObjectProvider<StringRedisTemplate> redis, ClockPort clock) {
        if (properties.getAccountLimit() < 1 || properties.getIpLimit() < 1 || properties.getWindowSeconds() < 1) {
            throw new IllegalArgumentException("Login rate limits and window must be positive");
        }
        this.properties = properties;
        this.redis = redis.getIfAvailable();
        this.clock = clock;
    }

    public void checkAndRecord(String account, String ip) {
        List<String> keys = List.of("jobproof:login:{budget}:ip:" + Tokens.sha256(ip == null ? "unknown" : ip),
                "jobproof:login:{budget}:account:" + Tokens.sha256(account));
        if ("local".equalsIgnoreCase(properties.getMode())) {
            acquireLocal(keys);
            return;
        }
        try {
            if (redis == null) throw new IllegalStateException("Redis unavailable");
            Long result = redis.execute(ACQUIRE, keys, String.valueOf(properties.getIpLimit()),
                    String.valueOf(properties.getAccountLimit()), String.valueOf(properties.getWindowSeconds()));
            if (Long.valueOf(0).equals(result)) throw limited();
            if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("Rate limiter returned no result");
        } catch (AppException exception) { throw exception; }
        catch (RuntimeException exception) {
            throw AppException.dependency("LOGIN_RATE_LIMIT_UNAVAILABLE", "登录服务暂时不可用，请稍后重试");
        }
    }

    private synchronized void acquireLocal(List<String> keys) {
        Instant now = clock.now();
        local.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        int[] limits = {properties.getIpLimit(), properties.getAccountLimit()};
        for (int i = 0; i < keys.size(); i++) {
            Counter counter = local.get(keys.get(i));
            if (counter != null && counter.count() >= limits[i]) throw limited();
        }
        if (local.size() >= 10000) throw limited();
        for (String key : keys) {
            local.compute(key, (ignored, counter) -> counter == null
                    ? new Counter(1, now.plusSeconds(properties.getWindowSeconds()))
                    : new Counter(counter.count() + 1, counter.expiresAt()));
        }
    }

    private static AppException limited() { return AppException.rateLimited("LOGIN_RATE_LIMITED", "登录尝试过于频繁，请稍后再试"); }
    private record Counter(long count, Instant expiresAt) { }
}
