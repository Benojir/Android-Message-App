package com.nextgen.messages.security.backup;

/**
 * Constants and format specification definitions for NextGen Messages encrypted backups.
 */
public final class BackupConstants {

    // Magic identifier: "NGMBKP1"
    public static final byte[] MAGIC_HEADER = new byte[]{0x4E, 0x47, 0x4D, 0x42, 0x4B, 0x50, 0x31};
    public static final int CURRENT_FORMAT_VERSION = 1;

    public static final int KDF_SALT_LENGTH_BYTES = 16;
    public static final int KDF_ITERATIONS = 100000;
    public static final int AES_KEY_LENGTH_BITS = 256;
    public static final int GCM_NONCE_LENGTH_BYTES = 12;
    public static final int GCM_TAG_LENGTH_BITS = 128;

    public static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    public static final String KEY_SPEC_ALGORITHM = "AES";

    public static final String ENTRY_MANIFEST = "manifest.json";
    public static final String ENTRY_SMS = "sms_records.json";
    public static final String ENTRY_MMS = "mms_records.json";
    public static final String ENTRY_METADATA = "app_metadata.json";
    public static final String ATTACHMENTS_DIR = "attachments/";

    public static final String MIME_TYPE_BACKUP = "application/octet-stream";
    public static final String BACKUP_FILE_EXTENSION = ".ngmbkp";

    private BackupConstants() {
        // Utility
    }
}
