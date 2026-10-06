package com.jobproof;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        RedisRepositoriesAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@EnableScheduling
@ConfigurationPropertiesScan
public class JobProofApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobProofApiApplication.class, args);
    }
}
