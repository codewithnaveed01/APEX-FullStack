package com.apex.security;

import com.apex.web.ApiException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM envelope encryption for payout account fields stored in PostgreSQL. */
public final class PayoutCrypto {
    private static final String PREFIX = "v1:";
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PayoutCrypto(String keyMaterial) {
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalStateException("Payout encryption key material is missing");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize payout encryption", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(encrypted, 0, envelope, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(envelope);
        } catch (Exception e) {
            throw ApiException.server("Could not protect payout account details");
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return "";
        if (!ciphertext.startsWith(PREFIX)) {
            throw ApiException.server("Stored payout account data has an unsupported format");
        }
        try {
            byte[] envelope = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (envelope.length < 29) throw new IllegalArgumentException("short envelope");
            byte[] iv = java.util.Arrays.copyOfRange(envelope, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(envelope, 12, envelope.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw ApiException.server("Stored payout account details could not be decrypted");
        }
    }
}
