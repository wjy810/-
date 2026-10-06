package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

class JobProofApiApplicationTest {

    @Test
    void keepsRedisClientAutoConfigurationEnabledForProductionRateLimits() {
        SpringBootApplication annotation = JobProofApiApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation.exclude())
                .doesNotContain(RedisAutoConfiguration.class)
                .contains(RedisRepositoriesAutoConfiguration.class);
    }
}
