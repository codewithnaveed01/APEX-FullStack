package com.apex.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * SCRAM-SHA-256 client (RFC 5802 / RFC 7677) used by the PostgreSQL
 * wire-protocol client for SASL authentication. Built only on JDK
 * crypto primitives (HmacSHA256, PBKDF2WithHmacSHA256, SHA-256).
 */
final class Scram {

    static final SecureRandom RANDOM = new SecureRandom();

    // Small state machine shared with PgConnection (single in-flight
    // auth per connection by design).
    static String[] step(String serverFirst) {
        String[] st = (String[]) scramState;
        String r = kv(serverFirst, "r"), s = kv(serverFirst, "s"), i = kv(serverFirst, "i");
        if (r == null || s == null || i == null) throw new PgConnection.PgException("28000", "Bad server-first-message");
        if (!r.startsWith(st[0])) throw new PgConnection.PgException("28000", "Server nonce does not extend client nonce");
        byte[] salt = Base64.getDecoder().decode(s);
        int iter = Integer.parseInt(i);
        byte[] salted = pbkdf2(st[1].getBytes(StandardCharsets.UTF_8), salt, iter);
        byte[] clientKey = hmac(salted, "Client Key");
        byte[] storedKey = sha256(clientKey);
        String cfb = "c=biws,r=" + r;
        String authMessage = st[2] + "," + serverFirst + "," + cfb;
        byte[] sig = hmac(storedKey, authMessage.getBytes(StandardCharsets.UTF_8));
        byte[] proof = xor(clientKey, sig);
        byte[] serverKey = hmac(storedKey, "Server Key");
        String expectedSig = Base64.getEncoder().encodeToString(hmac(serverKey, authMessage.getBytes(StandardCharsets.UTF_8)));
        return new String[]{cfb + ",p=" + Base64.getEncoder().encodeToString(proof), expectedSig};
    }

    static void verifyFinal(String serverFinal, String expectedSig) {
        String v = kv(serverFinal, "v");
        if (v == null || !constantTimeEquals(v, expectedSig)) {
            throw new PgConnection.PgException("28000", "SCRAM server signature mismatch");
        }
    }

    static String[] init(String password, String bare, String nonce) {
        return new String[]{nonce, password, bare};
    }

    /** Scratch holder so the state machine stays inside PgConnection's auth loop. */
    static Object scramState;
    static String scramExpectedServerSig;

    static String randomNonce() {
        byte[] b = new byte[18];
        RANDOM.nextBytes(b);
        return Base64.getEncoder().withoutPadding().encodeToString(b);
    }

    private static String kv(String msg, String key) {
        for (String part : msg.split(",")) {
            if (part.startsWith(key + "=")) return part.substring(key.length() + 1);
        }
        return null;
    }

    private static byte[] pbkdf2(byte[] password, byte[] salt, int iter) {
        try {
            PBEKeySpec spec = new PBEKeySpec(new String(password, StandardCharsets.UTF_8).toCharArray(), salt, iter, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new PgConnection.PgException("28000", "PBKDF2 failed: " + e.getMessage());
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new PgConnection.PgException("28000", "HMAC failed: " + e.getMessage());
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        return hmac(key, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new PgConnection.PgException("28000", "SHA-256 failed: " + e.getMessage());
        }
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) out[i] = (byte) (a[i] ^ b[i]);
        return out;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8), y = b.getBytes(StandardCharsets.UTF_8);
        int r = x.length ^ y.length;
        for (int i = 0; i < Math.min(x.length, y.length); i++) r |= x[i] ^ y[i];
        return r == 0;
    }
}
