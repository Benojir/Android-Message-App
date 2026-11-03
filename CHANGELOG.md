# NextGen Messages — Changelog

All notable changes to this project will be documented in this file.

---

## [1.3.0] - 2026-11-03 — Step 12: Production Audit, Performance Optimization & Release Preparation

### Fixed & Hardened
- **Security Audit:** Explicitly configured `android:exported="false"` for internal callback receivers (`SmsSentReceiver`, `SmsDeliveryReceiver`, `MmsSentReceiver`, `MmsDownloadedReceiver`) in `AndroidManifest.xml` to eliminate intent spoofing risks.
- **Directional Snippet Resolution:** Resolved ambiguous conversation list snippets. Outgoing sent messages now clearly display `"You: <message>"`, drafts display `"Draft: <message>"`, and failed transmissions display `"Not sent: <message>"` with highlighted status indicators.
- **Lint Vital Fix:** Added missing default dimension fallbacks (`chat_screen_horizontal_padding` and `inbox_row_padding_horizontal`) to `values/dimens.xml` resolving `MissingDefaultResource` fatal lint error during release assembly.
- **R8 / ProGuard Optimization:** Configured comprehensive release rules in `proguard-rules.pro` protecting Room DAOs, ViewBinding classes, cryptographic models, and stripping verbose/debug logging in release builds.

---

## [1.2.0] - 2026-11-02 — Step 11: Privacy, Security, App Lock & Encrypted Backup/Restore
- **App Lock:** 6-digit PIN authentication with PBKDF2WithHmacSHA256 (100,000 iterations, 16-byte random salt) and constant-time verification.
- **Biometric Prompt:** Fingerprint and Face Unlock support with graceful PIN fallback.
- **Rate-Limiting Lockouts:** Progressive non-destructive lockout countdowns (30s, 1m, 5m) persisted across reboots.
- **Private Conversations:** Thread-level isolation with masked inbox previews and concealed lock-screen notifications.
- **Encrypted Backup & Restore:** Versioned `NGMBKP1` binary header with AES-256-GCM authenticated streaming encryption and conservative duplicate detection window ($\pm 2000\,\text{ms}$).

---

## [1.1.0] - 2026-11-02 — Step 10: Premium UI, Material You & AMOLED Themes
- **Dynamic Colors:** Material You dynamic color extraction on Android 12+.
- **AMOLED Dark Mode:** Pure `#000000` pitch-black theme for battery efficiency on OLED screens.
- **Custom Bubble Styles:** Modern, Classic, Rounded, and Minimal bubble corner geometries with custom font scale multipliers and density spacing.
- **Per-Conversation Wallpapers:** Custom wallpaper backgrounds with brightness dimming overlays.

---

## [1.0.0] - 2026-11-01 — Steps 1–9: Foundation & Core Feature Suite
- Initial release featuring default SMS qualification, SMS/MMS sending and receiving, Dual SIM selection, delivery tracking, conversation threading, Smart Inbox classification (Room DB), spam/phishing protection, and real-time `MessagingStyle` notifications with inline Quick Reply.
