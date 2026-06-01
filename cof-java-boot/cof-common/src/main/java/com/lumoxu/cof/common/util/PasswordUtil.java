package com.lumoxu.cof.common.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

public final class PasswordUtil {

    public static final String MAGIC_RESET_HASH = "123456";
    public static final int DEFAULT_ITERATIONS = 210_000;
    public static final String DEFAULT_DIGEST = "sha256";
    public static final int KEY_LENGTH_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String newSaltHex() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    public static String hashPassword(String password, String saltHex, int iterations) {
        return pbkdf2(password, HexFormat.of().parseHex(saltHex), iterations);
    }

    /** Matches legacy Node {@code pbkdf2Sync(password, saltHexString, ...)} (salt as UTF-8, not decoded). */
    public static String hashPasswordNodeLegacy(String password, String saltHex, int iterations) {
        return pbkdf2(password, saltHex.getBytes(StandardCharsets.UTF_8), iterations);
    }

    private static String pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return HexFormat.of().formatHex(factory.generateSecret(spec).getEncoded());
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 failed", ex);
        }
    }

    public static boolean isMagicResetHash(String storedHash) {
        return MAGIC_RESET_HASH.equals(storedHash);
    }

    public static VerificationResult verify(
            String password,
            String storedHash,
            String saltHex,
            Integer iterations) {
        if (isMagicResetHash(storedHash)) {
            return new VerificationResult(true, true);
        }
        if (saltHex == null || saltHex.isBlank() || storedHash == null || storedHash.isBlank()) {
            return new VerificationResult(false, false);
        }
        int iter = iterations != null && iterations > 0 ? iterations : DEFAULT_ITERATIONS;
        String expected = hashPassword(password, saltHex, iter);
        if (timingSafeEqualHex(expected, storedHash)) {
            return new VerificationResult(true, false, false);
        }
        String legacyExpected = hashPasswordNodeLegacy(password, saltHex, iter);
        if (timingSafeEqualHex(legacyExpected, storedHash)) {
            return new VerificationResult(true, false, true);
        }
        return new VerificationResult(false, false, false);
    }

    public static boolean timingSafeEqualHex(String left, String right) {
        try {
            byte[] leftBytes = HexFormat.of().parseHex(left);
            byte[] rightBytes = HexFormat.of().parseHex(right);
            return MessageDigest.isEqual(leftBytes, rightBytes);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public record VerificationResult(boolean ok, boolean resetPassword, boolean legacyNodeHash) {
        public VerificationResult(boolean ok, boolean resetPassword) {
            this(ok, resetPassword, false);
        }
    }
}
