package com.nextgen.messages.security.lock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Cryptographic utility for securing application PIN authentication.
 * Uses PBKDF2WithHmacSHA256 with cryptographically random salts, 100,000 iterations,
 * and constant-time equality verification to resist timing attacks.
 */
public final class PinCryptoUtils {

    public static final int DEFAULT_ITERATIONS = 100000;
    public static final int SALT_LENGTH_BYTES = 16;
    public static final int KEY_LENGTH_BITS = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private PinCryptoUtils() {
        // Utility class
    }

    /**
     * Validates that a PIN strictly consists of 6 decimal digits.
     */
    public static boolean isValidPinFormat(@Nullable String pin) {
        return pin != null && pin.matches("^[0-9]{6}$");
    }

    /**
     * Generates a cryptographically strong 16-byte random salt.
     */
    @NonNull
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Encodes a byte array to standard Base64 string without newlines.
     */
    @NonNull
    public static String toBase64(@NonNull byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decodes a Base64 string to byte array.
     */
    @Nullable
    public static byte[] fromBase64(@Nullable String base64) {
        if (base64 == null) return null;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Derives a cryptographic hash from a 6-digit PIN using PBKDF2WithHmacSHA256.
     */
    @NonNull
    public static byte[] hashPin(@NonNull String pin, @NonNull byte[] salt, int iterations)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
        return factory.generateSecret(spec).getEncoded();
    }

    /**
     * Verifies an entered PIN against a stored salt and hash using constant-time comparison.
     */
    public static boolean verifyPin(
            @Nullable String inputPin,
            @Nullable String saltBase64,
            @Nullable String expectedHashBase64,
            int iterations
    ) {
        if (!isValidPinFormat(inputPin) || saltBase64 == null || expectedHashBase64 == null) {
            return false;
        }

        byte[] salt = fromBase64(saltBase64);
        byte[] expectedHash = fromBase64(expectedHashBase64);
        if (salt == null || expectedHash == null) {
            return false;
        }

        try {
            byte[] computedHash = hashPin(inputPin, salt, iterations > 0 ? iterations : DEFAULT_ITERATIONS);
            // Constant-time comparison to prevent side-channel timing analysis
            return MessageDigest.isEqual(computedHash, expectedHash);
        } catch (Exception e) {
            return false;
        }
    }
}
