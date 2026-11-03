# NextGen Messages — Privacy Policy Draft

**Last Updated:** November 2026  
**Status:** Pre-Release Draft for Production Review (Requires Owner Information Confirmation)

---

## 1. Overview & Commitment

NextGen Messages is designed from the ground up as a privacy-first, on-device SMS and MMS application. We believe personal conversations belong exclusively to the user. NextGen Messages does **NOT** collect, track, upload, share, or monetize your personal text messages, contacts, phone numbers, or metadata.

---

## 2. On-Device Data Handling

### A. SMS and MMS Messaging
- All incoming and outgoing SMS and MMS messages are processed and stored directly in your device's native Android Telephony Provider.
- Messages are never transmitted to any external server, cloud platform, or third party operated by NextGen Messages.
- Messages are transmitted exclusively over your cellular network provider's cellular SMS and MMS protocols as initiated by you.

### B. Contacts
- NextGen Messages accesses your device's contacts solely to display contact names and profile photos inside your conversations and inbox list.
- Contact data is read locally and is never exported, synced, or transmitted over any network.

### C. Smart Inbox & Spam Classification
- Message categorization (Transactions, OTP, Promotions, Personal, Business) and spam analysis are computed **100% locally on your device** using local keyword dictionaries and regular expressions.
- No message content is ever submitted to remote servers for classification or machine learning training.

### D. Encrypted Backups
- If you choose to create a backup, all messages and media attachments are encrypted on your device using AES-256-GCM before writing to the destination you select using Android's Storage Access Framework.
- NextGen Messages does not maintain or possess the keys or passwords to decrypt your backups. If you lose your password, the backup cannot be recovered.

---

## 3. Permissions Justification

| Permission | Purpose |
| :--- | :--- |
| `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `RECEIVE_MMS` | Required core functionality to qualify as the Android Default SMS application, display message history, and send/receive carrier messages. |
| `READ_CONTACTS` | Displays recipient names and profile pictures in conversation threads. |
| `READ_PHONE_STATE` | Discovers Dual SIM subscriptions to allow per-conversation SIM selection. |
| `POST_NOTIFICATIONS` | Displays incoming message notifications and enables Quick Reply on Android 13+. |
| `ACCESS_NETWORK_STATE` | Monitors cellular network availability for carrier MMS downloads. |

---

## 4. Third-Party Services & Telemetry

- **No Analytics SDKs:** NextGen Messages does not bundle Google Analytics, Firebase Analytics, or any tracking SDKs.
- **No Crash-Reporting SDKs:** No external crash-reporting libraries are included. Diagnostics remain in local device system logs.
- **No Advertising:** NextGen Messages contains no advertisements or ad measurement libraries.

---

## 5. Contact & Data Controller Information

*[Owner/Publisher Information to be confirmed prior to Google Play submission]*  
- **Entity:** NextGen Development Team  
- **Support Contact:** support@example.com *(Pending confirmation)*
