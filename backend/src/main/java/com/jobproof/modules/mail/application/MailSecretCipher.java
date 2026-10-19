package com.jobproof.modules.mail.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts the SMTP password with the deployment's master key (the one that protects AI channel keys),
 * AES-GCM with its own associated data so a mail secret can never be swapped for another kind.
 */
@Component
public class MailSecretCipher {

    private static final byte FORMAT = 1;
    private static final int NONCE_BYTES = 12;
    private static final byte[] AAD = "jobproof:mail:smtp-password:v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public MailSecretCipher(@Value("${jobproof.ai.gateway.master-key:}") String encoded) {
        this.key = parse(encoded);
    }

    /** False when the deployment has no master key: a password cannot be stored safely. */
    public boolean ready() {
        return key != null;
    }

    public String encrypt(String value) {
        if (key == null) throw new IllegalStateException("master key is not configured");
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(1 + nonce.length + sealed.length).put(FORMAT).put(nonce).put(sealed).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("unable to encrypt the SMTP password", exception);
        }
    }

    public String decrypt(String encoded) {
        if (key == null) throw new IllegalStateException("master key is not configured");
        try {
            byte[] packed = Base64.getDecoder().decode(encoded);
            if (packed.length <= 1 + NONCE_BYTES || packed[0] != FORMAT) throw new IllegalArgumentException("unsupported format");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(packed, 1, 1 + NONCE_BYTES)));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(Arrays.copyOfRange(packed, 1 + NONCE_BYTES, packed.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("the stored SMTP password cannot be decrypted with this master key", exception);
        }
    }

    private static SecretKeySpec parse(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(encoded.trim());
            return raw.length == 32 ? new SecretKeySpec(raw, "AES") : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
