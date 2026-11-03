# NextGen Messages — Known Limitations & Platform Constraints

This document details hardware, carrier, and platform-level constraints that impact traditional SMS and MMS applications on Android.

---

## 1. Android Telephony Platform Restrictions

### A. Default SMS Role Requirement for Database Mutations
- Under Android's telephony architecture (introduced in Android 4.4 KitKat), only the currently selected **Default SMS App** is granted write access to `Telephony.Sms` and `Telephony.Mms` content providers.
- If NextGen Messages is not selected as the default SMS app:
  - It can still read messages if runtime permissions are granted.
  - Outgoing messages cannot be inserted into the system store.
  - Backups cannot restore messages into the system database until the user confirms the role change.
  - A friendly system prompt (`RoleManager.createRequestRoleIntent()`) guides the user when default status is required.

### B. No End-to-End Encryption for Cellular SMS/MMS
- While NextGen Messages provides an encrypted on-device backup format and an application PIN lock, **traditional SMS and MMS carrier protocols are unencrypted over cellular towers**.
- NextGen Messages does not implement RCS (Rich Communication Services) or proprietary cloud chat protocols; messaging is subject to standard carrier transport limitations.

---

## 2. Carrier & MMS Variations

### A. MMS APN & Cellular Data Connectivity
- Traditional MMS requires active cellular data and carrier MMS APN support.
- If cellular data is toggled off, or if the user is connected only to Wi-Fi without carrier Wi-Fi Calling/MMS support, MMS send and download operations will be queued or fail depending on carrier infrastructure.
- Maximum MMS message sizes (typically 300 KB to 1 MB) are dictated by carrier configurations via `CarrierConfigManager`. The app automatically compresses images to fit within carrier limits.

### B. Dual SIM Slot & eSIM Idiosyncrasies
- Certain OEM firmware implementations (e.g. customized Android skins) assign dynamic, non-consecutive subscription IDs when SIM cards are swapped or eSIM profiles are toggled.
- The app discovers subscriptions dynamically via `SubscriptionManager.getActiveSubscriptionInfoList()`. If a previously selected subscription becomes unavailable, the app falls back to the system's default SMS subscription rather than failing silently.

---

## 3. Hardware & Sensor Availability

- **Biometric Hardware:** Biometric authentication (`BiometricPrompt`) is conditional on hardware sensor availability. On devices lacking hardware fingerprint or face sensors (`BIOMETRIC_ERROR_NO_HARDWARE`), biometric options are automatically hidden and the 6-digit PIN remains the primary credential.
