package com.nextgen.messages.security.backup;

import androidx.annotation.NonNull;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cryptographic engine handling AES-256-GCM authenticated encryption and decryption
 * with PBKDF2WithHmacSHA256 key derivation and versioned header serialization.
 */
public final class BackupCrypto {

    private BackupCrypto() {
        // Utility
    }

    public static class HeaderMetadata {
        public final int version;
        public final byte[] salt;
        public final int iterations;
        public final byte[] iv;

        public HeaderMetadata(int version, byte[] salt, int iterations, byte[] iv) {
            this.version = version;
            this.salt = salt;
            this.iterations = iterations;
            this.iv = iv;
        }
    }

    /**
     * Validates that a backup password meets the minimum security threshold (at least 8 chars).
     */
    public static boolean isPasswordStrongEnough(@NonNull String password) {
        return password.trim().length() >= 8;
    }

    /**
     * Generates a 16-byte random salt.
     */
    @NonNull
    public static byte[] generateSalt() {
        byte[] salt = new byte[BackupConstants.KDF_SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Generates a 12-byte random GCM nonce.
     */
    @NonNull
    public static byte[] generateNonce() {
        byte[] iv = new byte[BackupConstants.GCM_NONCE_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * Derives a 256-bit AES key from the user's password using PBKDF2WithHmacSHA256.
     */
    @NonNull
    public static SecretKey deriveKey(@NonNull String password, @NonNull byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                iterations > 0 ? iterations : BackupConstants.KDF_ITERATIONS,
                BackupConstants.AES_KEY_LENGTH_BITS
        );
        SecretKeyFactory factory = SecretKeyFactory.getInstance(BackupConstants.KDF_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, BackupConstants.KEY_SPEC_ALGORITHM);
    }

    /**
     * Initializes an AES-256-GCM encryption cipher with the derived key and random nonce.
     */
    @NonNull
    public static Cipher createEncryptCipher(@NonNull SecretKey secretKey, @NonNull byte[] iv)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(BackupConstants.CIPHER_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(BackupConstants.GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        return cipher;
    }

    /**
     * Initializes an AES-256-GCM decryption cipher with the derived key and stored nonce.
     */
    @NonNull
    public static Cipher createDecryptCipher(@NonNull SecretKey secretKey, @NonNull byte[] iv)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(BackupConstants.CIPHER_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(BackupConstants.GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        return cipher;
    }

    /**
     * Writes the versioned format header into the output stream.
     */
    public static void writeHeader(
            @NonNull OutputStream out,
            @NonNull byte[] salt,
            int iterations,
            @NonNull byte[] iv
    ) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        // 1. Magic bytes
        dos.write(BackupConstants.MAGIC_HEADER);
        // 2. Format version
        dos.writeInt(BackupConstants.CURRENT_FORMAT_VERSION);
        // 3. Salt
        dos.writeInt(salt.length);
        dos.write(salt);
        // 4. Iterations
        dos.writeInt(iterations);
        // 5. IV / Nonce
        dos.writeInt(iv.length);
        dos.write(iv);
        dos.flush();
    }

    /**
     * Reads and verifies the versioned format header from the input stream.
     */
    @NonNull
    public static HeaderMetadata readHeader(@NonNull InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);

        // 1. Check magic bytes
        byte[] magic = new byte[BackupConstants.MAGIC_HEADER.length];
        dis.readFully(magic);
        if (!Arrays.equals(magic, BackupConstants.MAGIC_HEADER)) {
            throw new IOException("Invalid backup file: header magic mismatch.");
        }

        // 2. Format version
        int version = dis.readInt();
        if (version < 1 || version > BackupConstants.CURRENT_FORMAT_VERSION) {
            throw new IOException("Unsupported backup format version: " + version);
        }

        // 3. Salt
        int saltLength = dis.readInt();
        if (saltLength != BackupConstants.KDF_SALT_LENGTH_BYTES) {
            throw new IOException("Malformed backup file: invalid salt length.");
        }
        byte[] salt = new byte[saltLength];
        dis.readFully(salt);

        // 4. Iterations
        int iterations = dis.readInt();
        if (iterations <= 0 || iterations > 500000) {
            throw new IOException("Malformed backup file: invalid iteration count.");
        }

        // 5. IV
        int ivLength = dis.readInt();
        if (ivLength != BackupConstants.GCM_NONCE_LENGTH_BYTES) {
            throw new IOException("Malformed backup file: invalid nonce length.");
        }
        byte[] iv = new byte[ivLength];
        dis.readFully(iv);

        return new HeaderMetadata(version, salt, iterations, iv);
    }
}
