package com.jobproof.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SourceCredentialSafetyTest {
    @Test
    void removedCredentialConfigurationCannotReappearInTheBuild() {
        assertThat(Files.exists(Path.of("src/main/java/com/jobproof/infrastructure/config/DevVerificationProviderConfiguration.java")))
                .isFalse();
        assertThat(getClass().getClassLoader().getResource(
                "com/jobproof/infrastructure/config/DevVerificationProviderConfiguration.class")).isNull();
    }

    @Test
    void providerCredentialsAreNeverJavaStringConstants() throws Exception {
        Pattern credential = Pattern.compile("(?i)(?:SMTP_AUTH_CODE|ACCESS_KEY_ID|ACCESS_KEY_SECRET)\\s*=\\s*\"[^\"]+\"");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(credential.matcher(Files.readString(path)).find())
                        .as("No literal provider credentials in %s", path).isFalse();
            }
        }
    }
}
