package com.jobproof.modules.identity.application;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.identity.domain.VerificationChannel;
import com.jobproof.shared.security.Tokens;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class VerificationHasher {

    private final byte[] contactKey;
    private final byte[] codeKey;
    private final boolean contactSecretConfigured;
    private final boolean codeSecretConfigured;

    public VerificationHasher(JobProofProperties properties) {
        String contact = clean(properties.getVerification().getContactHmacSecret());
        String code = clean(properties.getVerification().getCodeHmacSecret());
        this.contactSecretConfigured = !contact.isEmpty();
        this.codeSecretConfigured = !code.isEmpty();
        this.contactKey = (contact.isEmpty() ? Tokens.randomToken() : contact).getBytes(StandardCharsets.UTF_8);
        this.codeKey = (code.isEmpty() ? Tokens.randomToken() : code).getBytes(StandardCharsets.UTF_8);
    }

    public String contactHash(VerificationChannel channel, String destination) {
        return hmac(contactKey, channel.name() + ":" + destination);
    }

    public String ipHash(String ipAddress) {
        return hmac(contactKey, "IP:" + clean(ipAddress));
    }

    public String codeHash(String challengeId, String code) {
        return hmac(codeKey, challengeId + ":" + clean(code));
    }

    public String tokenHash(String token) {
        return hmac(contactKey, "TOKEN:" + clean(token));
    }

    public boolean contactSecretConfigured() {
        return contactSecretConfigured;
    }

    public boolean codeSecretConfigured() {
        return codeSecretConfigured;
    }

    private static String hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

