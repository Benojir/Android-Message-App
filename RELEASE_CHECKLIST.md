# NextGen Messages — Production Release Checklist

This checklist defines the required verification gates and tasks prior to deploying NextGen Messages to Google Play Internal / Closed Testing tracks.

---

## 1. Build & Compilation Verification
- [x] All 104 automated unit tests pass with zero errors (`./gradlew testDebugUnitTest`).
- [x] Debug build compiles successfully (`./gradlew assembleDebug`).
- [x] Release build compiles successfully with ProGuard/R8 optimization (`./gradlew assembleRelease`).
- [x] Lint vital checks pass with 0 errors (`lintVitalRelease`).

---

## 2. Manifest & Security Review
- [x] `targetSdkVersion` is set to `34` (Android 14) conforming to Google Play guidelines.
- [x] `minSdkVersion` is set to `29` (Android 10).
- [x] All internal callback receivers (`SmsSentReceiver`, `SmsDeliveryReceiver`, `MmsSentReceiver`, `MmsDownloadedReceiver`) are marked `android:exported="false"`.
- [x] Default SMS platform receivers (`SmsDeliverReceiver`, `MmsDeliverReceiver`, `RespondViaMessageService`) have strictly required permissions (`BROADCAST_SMS`, `BROADCAST_WAP_PUSH`, `SEND_RESPOND_VIA_MESSAGE`).
- [x] `FLAG_SECURE` configuration tested for recent-tasks protection.
- [x] PBKDF2 iterations set to 100,000 for PIN hashing and backup key derivation.
- [x] No plaintext logging of PII (passwords, OTPs, raw message contents) in release builds.

---

## 3. Google Play Store Readiness Tasks

The following administrative items must be executed by the developer / release owner:

- [ ] **Production Keystore Signing:** Configure a secure `signingConfigs.release` in `app/build.gradle` using the organization's release keystore and environment variables:
  ```groovy
  signingConfigs {
      release {
          storeFile file(System.getenv("KEYSTORE_PATH"))
          storePassword System.getenv("KEYSTORE_PASSWORD")
          keyAlias System.getenv("KEY_ALIAS")
          keyPassword System.getenv("KEY_PASSWORD")
      }
  }
  ```
- [ ] **Android App Bundle (.aab):** Execute `./gradlew bundleRelease` to generate the production App Bundle for Google Play Console upload.
- [ ] **Google Play Console Permission Declaration:** Prepare the *SMS and Call Log Permission Declaration Form*, justifying `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, and `RECEIVE_MMS` under the **Default SMS Handler** core app functionality exemption.
- [ ] **Privacy Policy Hosting:** Host the privacy policy draft (`PRIVACY.md`) on a public HTTPS URL and enter it in Google Play Console under App Content $\rightarrow$ Privacy Policy.
- [ ] **Data Safety Form:** Complete the Data Safety questionnaire in Google Play Console declaring that SMS/MMS messages and contacts are processed on-device and NOT collected, shared, or uploaded to external servers.
