package com.jobproof.modules.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.identity.application.VerificationHasher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Offline fixture only: no real credentials, phone numbers, or cloud requests. */
class AliyunSmsCredentialConfigurationTest {
    @Test
    void developmentProfileAlsoAcceptsTheExistingDeploymentEnvironmentNames() {
        new ApplicationContextRunner()
                .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(ProviderProperties.class)
                .withPropertyValues("spring.profiles.active=dev", "QQ_SMTP_USER=fixture@example.invalid",
                        "QQ_SMTP_AUTH_CODE=fixture-smtp-secret", "MAIL_FROM=fixture@example.invalid",
                        "ALIBABA_CLOUD_ACCESS_KEY_ID=fixture-access-id",
                        "ALIBABA_CLOUD_ACCESS_KEY_SECRET=fixture-access-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JavaMailSenderImpl.class).getUsername()).isEqualTo("fixture@example.invalid");
                    assertThat(context.getBean(JavaMailSenderImpl.class).getPassword()).isEqualTo("fixture-smtp-secret");
                    JobProofProperties properties = context.getBean(JobProofProperties.class);
                    assertThat(properties.getVerification().getQqSmtp().getFrom()).isEqualTo("fixture@example.invalid");
                    assertThat(properties.getVerification().getAliyun().getAccessKeyId()).isEqualTo("fixture-access-id");
                });
    }

    @Test
    void providersUseInjectedConfigurationWithoutSendingAnyNetworkRequests() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(ProviderProperties.class)
                .withPropertyValues("spring.mail.host=smtp.invalid", "spring.mail.username=fixture@example.invalid",
                        "spring.mail.password=fixture-smtp-secret", "jobproof.verification.aliyun.endpoint=sms.invalid",
                        "jobproof.verification.aliyun.access-key-id=fixture-access-id",
                        "jobproof.verification.aliyun.access-key-secret=fixture-access-secret",
                        "jobproof.verification.aliyun.sign-name=Fixture", "jobproof.verification.aliyun.template-code=fixture",
                        "jobproof.verification.contact-hmac-secret=fixture-contact-hmac-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JavaMailSenderImpl mail = context.getBean(JavaMailSenderImpl.class);
                    assertThat(mail.getHost()).isEqualTo("smtp.invalid");
                    assertThat(mail.getUsername()).isEqualTo("fixture@example.invalid");
                    assertThat(mail.getPassword()).isEqualTo("fixture-smtp-secret");
                    JobProofProperties properties = context.getBean(JobProofProperties.class);
                    assertThat(properties.getVerification().getAliyun().getAccessKeyId()).isEqualTo("fixture-access-id");
                    assertThat(new AliyunSmsVerificationDeliveryAdapter(properties, new VerificationHasher(properties)).available()).isTrue();
                });
    }

    @Test
    void missingCredentialsCannotFallBackToBundledSecrets() {
        JobProofProperties properties = new JobProofProperties();
        assertThat(new AliyunSmsVerificationDeliveryAdapter(properties, new VerificationHasher(properties)).available()).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JobProofProperties.class)
    static class ProviderProperties { }
}
