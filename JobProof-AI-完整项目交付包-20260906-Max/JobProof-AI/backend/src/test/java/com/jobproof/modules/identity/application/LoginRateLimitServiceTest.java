package com.jobproof.modules.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.jobproof.infrastructure.config.LoginRateLimitProperties;
import com.jobproof.shared.error.AppException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class LoginRateLimitServiceTest {
    @Test
    void redisOutageAndMissingScriptResultFailClosed() {
        var redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        var factory = new StaticListableBeanFactory();
        factory.addBean("redis", redis);
        var limiter = new LoginRateLimitService(new LoginRateLimitProperties(),
                factory.getBeanProvider(StringRedisTemplate.class), Instant::now);
        assertThat(assertThrows(AppException.class, () -> limiter.checkAndRecord("fixture", "192.0.2.6")).reason())
                .isEqualTo("LOGIN_RATE_LIMIT_UNAVAILABLE");
        org.mockito.Mockito.when(redis.execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.script.RedisScript.class),
                        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline fixture"));
        assertThat(assertThrows(AppException.class, () -> limiter.checkAndRecord("fixture", "192.0.2.6")).reason())
                .isEqualTo("LOGIN_RATE_LIMIT_UNAVAILABLE");
    }

    @Test
    void redisFailureClosesLoginInsteadOfFallingBackToLocal() {
        var limiter = new LoginRateLimitService(new LoginRateLimitProperties(),
                new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class), Instant::now);
        assertThat(assertThrows(AppException.class, () -> limiter.checkAndRecord("fixture", "192.0.2.5")).reason())
                .isEqualTo("LOGIN_RATE_LIMIT_UNAVAILABLE");
    }

    @Test
    void independentLocalInstancesDoNotShareTestCounters() {
        var settings = new LoginRateLimitProperties();
        settings.setMode("local"); settings.setAccountLimit(1);
        var beans = new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class);
        new LoginRateLimitService(settings, beans, Instant::now).checkAndRecord("fixture", "192.0.2.5");
        new LoginRateLimitService(settings, beans, Instant::now).checkAndRecord("fixture", "192.0.2.5");
    }
    @Test
    void accountAndIpBudgetsAreIndependentAndRecoverAfterWindow() {
        var settings = new LoginRateLimitProperties();
        settings.setMode("local"); settings.setAccountLimit(2); settings.setIpLimit(3);
        Instant[] now = {Instant.parse("2026-09-05T00:00:00Z")};
        var limiter = new LoginRateLimitService(settings,
                new StaticListableBeanFactory().getBeanProvider(StringRedisTemplate.class), () -> now[0]);
        limiter.checkAndRecord("account-a", "ip-a");
        limiter.checkAndRecord("account-a", "ip-b");
        assertThat(assertThrows(AppException.class, () -> limiter.checkAndRecord("account-a", "ip-c")).reason())
                .isEqualTo("LOGIN_RATE_LIMITED");
        limiter.checkAndRecord("account-b", "ip-a");
        limiter.checkAndRecord("account-c", "ip-a");
        assertThat(assertThrows(AppException.class, () -> limiter.checkAndRecord("account-d", "ip-a")).reason())
                .isEqualTo("LOGIN_RATE_LIMITED");
        now[0] = now[0].plusSeconds(299);
        assertThat(assertThrows(AppException.class, () -> limiter.checkAndRecord("account-a", "ip-a")).reason())
                .isEqualTo("LOGIN_RATE_LIMITED");
        now[0] = now[0].plusSeconds(2);
        limiter.checkAndRecord("account-a", "ip-a");
    }
}
