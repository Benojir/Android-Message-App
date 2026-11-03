# NextGen Messages — Quality Assurance & Testing Guide

This document describes the testing strategy, test suites, execution procedures, and physical device test matrix for NextGen Messages.

---

## 1. Automated Test Suite Summary

The automated unit test suite covers core business logic, encoding algorithms, database integrity, security boundaries, and release requirements across 15 dedicated test suites comprising **104 automated tests**:

| Test Class | Focus Area | Test Count | Status |
| :--- | :--- | :---: | :---: |
| `SmsEncodingUtilsTest` | GSM 7-bit vs UCS-2 character counting, multipart boundary calculation | 10 | **PASSED** |
| `SmsRoleManagerTest` | Default SMS role qualification, intent generation, state checks | 6 | **PASSED** |
| `SmsModelAndGroupingTest` | Thread aggregation, recipient normalization, composite IDs | 9 | **PASSED** |
| `SimInfoTest` | Dual SIM subscription resolution, fallback handling | 6 | **PASSED** |
| `PermissionsAndManifestTest` | Telephony permission declarations and security attributes | 5 | **PASSED** |
| `DateFormatterTest` | Relative date/time formatting, internationalized strings | 8 | **PASSED** |
| `ThemeHelperTest` | Theme mode switching, dynamic color capability validation | 5 | **PASSED** |
| `CategoryRepositoryTest` | Built-in smart inbox categories, icon mapping | 4 | **PASSED** |
| `Step5AdvancedManagementTest` | Selection, deletion, read/unread status models | 7 | **PASSED** |
| `Step6MmsTest` | MMS PDU structures, attachment parsing, media type verification | 8 | **PASSED** |
| `Step7SmartInboxTest` | Keyword classification, OTP pattern matching, rule evaluation | 10 | **PASSED** |
| `Step8AdvancedFeaturesTest` | Archive, pin, star, and spam metadata persistence | 6 | **PASSED** |
| `Step9NotificationTest` | Notification channels, privacy filters, mute duration | 5 | **PASSED** |
| `Step10UiThemeTest` | Material You attributes, AMOLED styling, bubble appearance | 8 | **PASSED** |
| `Step11SecurityBackupTest` | PBKDF2 PIN hashing, AES-256-GCM cipher, tamper resistance, backup headers | 8 | **PASSED** |
| `Step12ProductionAuditTest` | Directional snippet resolution, failed/draft indicators, duplicate tolerance | 7 | **PASSED** |
| **Total** | | **104** | **100% PASSED** |

---

## 2. Running Automated Tests

```bash
# Run unit tests on JVM
./gradlew testDebugUnitTest

# View HTML Test Report
# File path: app/build/reports/tests/testDebugUnitTest/index.html
```

---

## 3. Physical Device Testing Matrix

Carrier network testing requires a physical device with active SIM card(s):

| Scenario | Test Procedure | Expected Result |
| :--- | :--- | :--- |
| **SMS Send (Single)** | Send 50-character ASCII text to active mobile number. | Outgoing bubble displayed; status changes to Sent/Delivered. |
| **SMS Send (Multipart)** | Send 300-character ASCII text. | Divided into 2 parts; recipient receives coherent concatenated message. |
| **SMS Send (Unicode)** | Send Bengali (`নমস্কার কেমন আছেন?`) and Hindi (`नमस्ते`) texts. | Encoded as UCS-2 (70-char segment); correctly displayed on recipient handset. |
| **SMS Receive** | Receive single and multipart SMS while app is in background. | Notification displayed with sender and snippet; unread badge updated. |
| **Dual SIM Switching** | On Dual SIM device, select SIM 2 in composer and send. | Sent via SIM 2 subscription ID; carrier reflects SIM 2 billing. |
| **App Lock Gateway** | Enable App Lock, background app for 1 minute, reopen. | `AuthLockActivity` displays; biometric prompt opens; back button moves task to background. |
| **Encrypted Backup** | Export backup with password to device Downloads folder. | `.ngmbkp` generated with 51-byte header + AES-256-GCM stream. |
| **Encrypted Restore** | Delete 1 conversation, restore from `.ngmbkp`. | Pre-flight preview displays message counts; deleted thread restored without duplicating existing messages. |
