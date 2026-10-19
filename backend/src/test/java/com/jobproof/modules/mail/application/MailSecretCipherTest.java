package com.jobproof.modules.mail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class MailSecretCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void passwordsRoundTripAndCannotBeReadWithAnotherKey() {
        MailSecretCipher cipher = new MailSecretCipher(KEY);
        String sealed = cipher.encrypt("smtp-authorization-code");
        assertThat(sealed).doesNotContain("smtp-authorization-code");
        assertThat(cipher.encrypt("smtp-authorization-code")).isNotEqualTo(sealed);
        assertThat(cipher.decrypt(sealed)).isEqualTo("smtp-authorization-code");

        byte[] other = new byte[32];
        other[0] = 1;
        MailSecretCipher stranger = new MailSecretCipher(Base64.getEncoder().encodeToString(other));
        assertThatThrownBy(() -> stranger.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutAMasterKeyNothingIsStored() {
        assertThat(new MailSecretCipher("").ready()).isFalse();
        assertThat(new MailSecretCipher("not-base64!").ready()).isFalse();
        assertThatThrownBy(() -> new MailSecretCipher("").encrypt("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failuresAreExplainedInPlainWords() {
        assertThat(MailSettingsService.explain(new org.springframework.mail.MailAuthenticationException("535")))
                .contains("授权码不正确");
        assertThat(MailSettingsService.explain(new org.springframework.mail.MailSendException("x", new java.net.UnknownHostException("smtp.nowhere"))))
                .contains("找不到");
        assertThat(MailSettingsService.mask("operations@example.com")).isEqualTo("op***@example.com");
        assertThat(MailSettingsService.requiresMatchingSender("smtp.exmail.qq.com")).isTrue();
        assertThat(MailSettingsService.requiresMatchingSender("smtp.163.com")).isFalse();
    }
}
