package com.nextgen.messages;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.nextgen.messages.security.backup.BackupConstants;
import com.nextgen.messages.security.backup.BackupCrypto;
import com.nextgen.messages.security.backup.BackupManifest;
import com.nextgen.messages.security.lock.PinCryptoUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import org.junit.Test;

/**
 * Unit test suite for Step 11: Privacy, Security, App Lock & Encrypted SMS/MMS Backup and Restore.
 */
public class Step11SecurityBackupTest {

    @Test
    public void testPinValidation() {
        // Valid 6-digit numeric PINs
        assertTrue(PinCryptoUtils.isValidPinFormat("123456"));
        assertTrue(PinCryptoUtils.isValidPinFormat("000000"));
        assertTrue(PinCryptoUtils.isValidPinFormat("987654"));

        // Invalid lengths
        assertFalse(PinCryptoUtils.isValidPinFormat("12345"));
        assertFalse(PinCryptoUtils.isValidPinFormat("1234567"));

        // Non-numeric characters
        assertFalse(PinCryptoUtils.isValidPinFormat("12345a"));
        assertFalse(PinCryptoUtils.isValidPinFormat("abcdef"));
        assertFalse(PinCryptoUtils.isValidPinFormat("12 456"));
        assertFalse(PinCryptoUtils.isValidPinFormat(""));
        assertFalse(PinCryptoUtils.isValidPinFormat(null));
    }

    @Test
    public void testPinHashingAndVerification() throws Exception {
        String pin = "654321";
        byte[] salt = PinCryptoUtils.generateSalt();
        assertEquals(PinCryptoUtils.SALT_LENGTH_BYTES, salt.length);

        int iterations = 100000;
        byte[] hash = PinCryptoUtils.hashPin(pin, salt, iterations);
        assertNotNull(hash);
        assertEquals(32, hash.length); // 256 bits

        String saltBase64 = PinCryptoUtils.toBase64(salt);
        String hashBase64 = PinCryptoUtils.toBase64(hash);

        // Verification with correct PIN
        assertTrue(PinCryptoUtils.verifyPin(pin, saltBase64, hashBase64, iterations));

        // Verification with wrong PIN
        assertFalse(PinCryptoUtils.verifyPin("111111", saltBase64, hashBase64, iterations));
        assertFalse(PinCryptoUtils.verifyPin("654320", saltBase64, hashBase64, iterations));

        // Verification with invalid / tampered inputs
        assertFalse(PinCryptoUtils.verifyPin("invalid", saltBase64, hashBase64, iterations));
        assertFalse(PinCryptoUtils.verifyPin(pin, null, hashBase64, iterations));
        assertFalse(PinCryptoUtils.verifyPin(pin, saltBase64, null, iterations));
    }

    @Test
    public void testAesGcmEncryptionDecryption() throws Exception {
        String password = "SecureBackupPassword!2026";
        assertTrue(BackupCrypto.isPasswordStrongEnough(password));

        byte[] salt = BackupCrypto.generateSalt();
        byte[] iv = BackupCrypto.generateNonce();
        assertEquals(16, salt.length);
        assertEquals(12, iv.length);

        SecretKey key = BackupCrypto.deriveKey(password, salt, 1000); // 1000 iterations for test speed

        String plainText = "Confidential SMS message body with sensitive data: 483920";
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);

        // Encrypt
        Cipher encryptCipher = BackupCrypto.createEncryptCipher(key, iv);
        byte[] cipherText = encryptCipher.doFinal(plainBytes);
        assertNotNull(cipherText);
        // GCM appends a 16-byte authentication tag
        assertEquals(plainBytes.length + 16, cipherText.length);

        // Decrypt
        Cipher decryptCipher = BackupCrypto.createDecryptCipher(key, iv);
        byte[] decryptedBytes = decryptCipher.doFinal(cipherText);
        assertEquals(plainText, new String(decryptedBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testAesGcmTamperDetection() throws Exception {
        String password = "StrongBackupPassword123#";
        byte[] salt = BackupCrypto.generateSalt();
        byte[] iv = BackupCrypto.generateNonce();
        SecretKey key = BackupCrypto.deriveKey(password, salt, 1000);

        byte[] plainBytes = "Hello secure backup world".getBytes(StandardCharsets.UTF_8);
        Cipher encryptCipher = BackupCrypto.createEncryptCipher(key, iv);
        byte[] cipherText = encryptCipher.doFinal(plainBytes);

        // Tamper with one single byte in the ciphertext
        cipherText[5] = (byte) (cipherText[5] ^ 0xFF);

        // Decrypt should fail due to AEAD authentication tag validation failure
        Cipher decryptCipher = BackupCrypto.createDecryptCipher(key, iv);
        try {
            decryptCipher.doFinal(cipherText);
            fail("Expected AEADBadTagException or GeneralSecurityException due to tampering");
        } catch (AEADBadTagException e) {
            // Expected
        } catch (GeneralSecurityException e) {
            // Also acceptable
        }
    }

    @Test
    public void testWrongPasswordRejection() throws Exception {
        String correctPassword = "CorrectPassword123";
        String wrongPassword = "WrongPassword999";

        byte[] salt = BackupCrypto.generateSalt();
        byte[] iv = BackupCrypto.generateNonce();

        SecretKey keyCorrect = BackupCrypto.deriveKey(correctPassword, salt, 1000);
        SecretKey keyWrong = BackupCrypto.deriveKey(wrongPassword, salt, 1000);

        byte[] plainBytes = "Secret message payload".getBytes(StandardCharsets.UTF_8);
        byte[] cipherText = BackupCrypto.createEncryptCipher(keyCorrect, iv).doFinal(plainBytes);

        try {
            BackupCrypto.createDecryptCipher(keyWrong, iv).doFinal(cipherText);
            fail("Expected decryption failure with wrong password");
        } catch (AEADBadTagException e) {
            // Expected
        } catch (GeneralSecurityException e) {
            // Also acceptable
        }
    }

    @Test
    public void testBackupHeaderSerialization() throws Exception {
        byte[] salt = BackupCrypto.generateSalt();
        byte[] iv = BackupCrypto.generateNonce();
        int iterations = 100000;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BackupCrypto.writeHeader(baos, salt, iterations, iv);

        byte[] headerBytes = baos.toByteArray();
        assertEquals(51, headerBytes.length); // 7 magic + 4 version + 4 saltLen + 16 salt + 4 iter + 4 ivLen + 12 iv

        ByteArrayInputStream bais = new ByteArrayInputStream(headerBytes);
        BackupCrypto.HeaderMetadata meta = BackupCrypto.readHeader(bais);

        assertEquals(BackupConstants.CURRENT_FORMAT_VERSION, meta.version);
        assertArrayEquals(salt, meta.salt);
        assertEquals(iterations, meta.iterations);
        assertArrayEquals(iv, meta.iv);
    }

    @Test(expected = IOException.class)
    public void testBackupHeaderInvalidMagic() throws Exception {
        byte[] corruptedHeader = new byte[51];
        Arrays.fill(corruptedHeader, (byte) 0x00);
        ByteArrayInputStream bais = new ByteArrayInputStream(corruptedHeader);
        BackupCrypto.readHeader(bais);
    }

    @Test
    public void testBackupManifestJsonSerialization() throws Exception {
        BackupManifest manifest = new BackupManifest(
                1,
                "1.3.0",
                1730590000000L,
                1500,
                42,
                35,
                10
        );

        String json = manifest.toJsonString();
        assertNotNull(json);

        BackupManifest parsed = BackupManifest.fromJsonString(json);
        assertEquals(1, parsed.getFormatVersion());
        assertEquals("1.3.0", parsed.getAppVersion());
        assertEquals(1730590000000L, parsed.getTimestamp());
        assertEquals(1500, parsed.getSmsCount());
        assertEquals(42, parsed.getMmsCount());
        assertEquals(35, parsed.getAttachmentCount());
        assertEquals(10, parsed.getMetadataCount());
    }
}
