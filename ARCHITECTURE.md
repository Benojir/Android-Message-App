# NextGen Messages — System Architecture

This document describes the architectural foundation, component relationships, data flow pipelines, and design patterns employed across NextGen Messages.

---

## 1. Architectural Overview

NextGen Messages follows the **MVVM (Model-View-ViewModel)** architectural pattern recommended by Android Architecture Guidelines, implemented strictly in native **Java and XML**.

```
┌─────────────────────────────────────────────────────────────┐
│                         Presentation Layer                   │
│   Activities / Fragments (ViewBinding) & RecyclerView Adapters│
└──────────────────────────────┬──────────────────────────────┘
                               │ Observes LiveData / Callbacks
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                          ViewModel Layer                    │
│   InboxViewModel, ConversationViewModel, etc.                │
└──────────────────────────────┬──────────────────────────────┘
                               │ Dispatches Asynchronous Requests
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                         Repository Layer                     │
│   SmsRepository, ConversationMetadataRepository,             │
│   SmartInboxRepository, ContactRepository, etc.              │
└──────────────┬───────────────────────────────┬──────────────┘
               │ Queries / Inserts             │ Queries / Caches
               ▼                               ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│   Telephony ContentProvider  │ │   Room Database (Local)    │
│  (content://sms, mms, etc.)  │ │  (nextgen_smart_inbox.db)  │
└──────────────────────────────┘ └────────────────────────────┘
```

---

## 2. Core Subsystems

### A. Android Telephony Provider & Default SMS System
To qualify as the Android Default SMS application, the app implements the platform-contracted components declared in `AndroidManifest.xml`:
- **`SmsDeliverReceiver`:** Handles `Telephony.Sms.Intents.SMS_DELIVER_ACTION` (`android.provider.Telephony.SMS_DELIVER`) with permission `android.permission.BROADCAST_SMS`. Concatenates multipart SMS and persists incoming messages directly into `Telephony.Sms.Inbox`.
- **`MmsDeliverReceiver`:** Handles `Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION` (`android.provider.Telephony.WAP_PUSH_DELIVER`) with permission `android.permission.BROADCAST_WAP_PUSH`. Parses WAP PUSH MMS notifications and delegates download via `MmsDownloadManager`.
- **`RespondViaMessageService`:** Handles `android.intent.action.RESPOND_VIA_MESSAGE` with permission `android.permission.SEND_RESPOND_VIA_MESSAGE`. Enables auto-sending quick text responses when rejecting incoming phone calls.
- **`ComposeActivity`:** Handles `android.intent.action.SENDTO` and `android.intent.action.SEND` for `sms:`, `smsto:`, `mms:`, `mmsto:`, and multimedia MIME types.

### B. Dual SIM Subsystem
Managed by `SimManager`:
- Leverages `SubscriptionManager` to dynamically query active subscription records.
- Allows user selection of default SIM or per-conversation preferred SIM.
- Resolves proper `SmsManager` instance via `SmsManager.getSmsManagerForSubscriptionId(subId)`.
- Gracefully falls back to active default SMS subscription if a requested subscription is deactivated or removed.

### C. Smart Inbox & Local Classification Engine
- **`SmartInboxRepository`:** Evaluates incoming messages against keyword dictionaries, regex tokenizers, and custom user rules completely offline.
- **`SmartInboxDatabase`:** Room Database holding category definitions, rules, blocked senders, conversation metadata (pins, archives, mute states), and spam feedback.

### D. Security & App Lock Subsystem
- **`AppLockManager`:** Singleton managing session timeout state, progressive rate limiting, and PIN validation.
- **`PinCryptoUtils`:** Cryptographic operations for PIN storage using PBKDF2WithHmacSHA256 (100,000 iterations, 16-byte random salt, constant-time `MessageDigest.isEqual` comparison).
- **`NextGenApp`:** Registered `ActivityLifecycleCallbacks` enforcing auto-lock timeout checks and dynamically applying `WindowManager.LayoutParams.FLAG_SECURE`.

### E. Encrypted Backup & Restore Engine
- **`EncryptedBackupManager`:** Reads SMS and MMS tables alongside Room database metadata and streams them directly into an AES-256-GCM encrypted ZIP archive container (`CipherOutputStream`).
- **`EncryptedRestoreManager`:** Authenticates the backup password, verifies the header magic (`NGMBKP1`), validates the embedded manifest (`manifest.json`), and safely inserts records into the Telephony Provider with conservative duplicate detection ($\pm 2000\,\text{ms}$ timestamp matching).
