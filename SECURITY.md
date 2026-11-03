# NextGen Messages — Security Architecture & Threat Model

This document outlines the security architecture, cryptographic safeguards, and vulnerability mitigation strategies implemented in NextGen Messages.

---

## 1. Security Architecture Principles

1. **Zero-Knowledge & 100% Offline Processing:** The application does not include any network client libraries, third-party analytics trackers, crash-reporting daemons, or remote sync endpoints. All database reads, spam evaluations, and classification algorithms execute strictly on the local device.
2. **Explicit Component Exposure:** All internal broadcast receivers (`SmsSentReceiver`, `SmsDeliveryReceiver`, `MmsSentReceiver`, `MmsDownloadedReceiver`) are explicitly marked `android:exported="false"` in `AndroidManifest.xml` to prevent intent spoofing by third-party applications.
3. **Task-Switcher and Screenshot Privacy:** Dynamic enforcement of `WindowManager.LayoutParams.FLAG_SECURE` obscures screen contents in the Android recent-tasks switcher and prevents untrusted screen capture tools from extracting sensitive message contents.

---

## 2. Authentication & App Lock Cryptography

### A. PIN Storage & Verification
- **Key Derivation Function:** `PBKDF2WithHmacSHA256`
- **Salt Generation:** 16 cryptographically secure random bytes generated via `java.security.SecureRandom`.
- **Iteration Count:** 100,000 iterations.
- **Key Size:** 256 bits (32 bytes).
- **Comparison Logic:** Constant-time byte array verification via `java.security.MessageDigest.isEqual()` to prevent side-channel timing analysis attacks.

### B. Persistent Lockout & Rate-Limiting Policy
To prevent brute-force automated keypad attacks, `AppLockManager` enforces progressive, non-destructive lockout delays:
- Attempts 1–4: Allowed without delay; visual warning indicating remaining attempts.
- Attempt 5: 30-second lockout.
- Attempts 6–9: 1-minute lockout.
- Attempts 10+: 5-minute lockout.

Lockout timestamps are saved to disk and persist across application restarts and device reboots. Keypad entry is completely disabled during lockout countdowns.

---

## 3. Encrypted Backup & Restore Container

The backup file format (`.ngmbkp`) provides authenticated encryption ensuring confidentiality and integrity:

### Binary Header Specification (51 Bytes)
```
+---------------------------------------------------------------------------------------------------+
| Magic Header ("NGMBKP1") | Version (int32) | Salt (len + 16B) | Iterations (int32) | Nonce (len + 12B)|
| 7 Bytes                  | 4 Bytes         | 20 Bytes         | 4 Bytes            | 16 Bytes         |
+---------------------------------------------------------------------------------------------------+
```

### Cryptographic Parameters
- **Encryption Algorithm:** AES-256-GCM (`AES/GCM/NoPadding`)
- **Key Derivation:** `PBKDF2WithHmacSHA256` derived from user-entered password with 100,000 iterations and 16-byte random salt.
- **Nonce (IV):** 12 bytes (96 bits) randomly generated per backup file using `SecureRandom`.
- **Integrity Verification:** 128-bit AEAD GCM authentication tag. Any modification, bit-flip, or password discrepancy raises an immediate `AEADBadTagException` during decryption, preventing chosen-ciphertext tampering attacks.

---

## 4. Release Build Hardening

- **R8 / ProGuard Stripping:** In release builds, `android.util.Log.d()` and `android.util.Log.v()` calls are automatically stripped at bytecode level by R8 optimization rules, preventing sensitive data or PII from remaining in compiled artifacts.
- **No Plaintext Temporary Files:** PDU cache files are cleaned up immediately following carrier transport, and backup/restore streams directly via `CipherOutputStream` and `CipherInputStream` without writing unencrypted intermediate ZIP archives to disk.
