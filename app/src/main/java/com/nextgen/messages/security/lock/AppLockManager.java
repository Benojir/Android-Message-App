package com.nextgen.messages.security.lock;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import com.nextgen.messages.data.local.AppPreferences;

/**
 * Centralized manager coordinating application authentication locking, session states,
 * PBKDF2 PIN verification, rate-limiting lockout windows, and biometric readiness.
 */
public class AppLockManager {

    private static volatile AppLockManager instance;

    private final Context appContext;
    private final AppPreferences preferences;

    private volatile boolean isSessionUnlocked = false;
    private volatile long lastBackgroundTime = 0L;
    private volatile boolean isChangingConfigurations = false;

    public static class AuthResult {
        public enum Status {
            SUCCESS,
            INCORRECT_PIN,
            LOCKED_OUT,
            INVALID_FORMAT
        }

        private final Status status;
        private final int failedAttempts;
        private final long lockoutRemainingSeconds;

        public AuthResult(Status status, int failedAttempts, long lockoutRemainingSeconds) {
            this.status = status;
            this.failedAttempts = failedAttempts;
            this.lockoutRemainingSeconds = lockoutRemainingSeconds;
        }

        public Status getStatus() {
            return status;
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public int getFailedAttempts() {
            return failedAttempts;
        }

        public long getLockoutRemainingSeconds() {
            return lockoutRemainingSeconds;
        }
    }

    private AppLockManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.preferences = AppPreferences.getInstance(this.appContext);
    }

    public static AppLockManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppLockManager.class) {
                if (instance == null) {
                    instance = new AppLockManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Checks if the app lock feature is enabled and configured with a PIN.
     */
    public boolean isAppLockEnabled() {
        return preferences.isAppLockEnabled();
    }

    /**
     * Returns true if the user has authenticated and the current session is unlocked.
     */
    public boolean isSessionUnlocked() {
        return isSessionUnlocked;
    }

    /**
     * Marks the session as unlocked.
     */
    public void unlockSession() {
        this.isSessionUnlocked = true;
        this.preferences.setFailedAttempts(0);
        this.preferences.setLockoutUntil(0L);
    }

    /**
     * Locks the session immediately.
     */
    public void lockSession() {
        this.isSessionUnlocked = false;
    }

    /**
     * Returns true if the application requires authentication to access content.
     */
    public boolean isLocked() {
        return isAppLockEnabled() && !isSessionUnlocked;
    }

    /**
     * Checks remaining lockout seconds if currently rate-limited.
     * Returns 0 if not locked out.
     */
    public long getRemainingLockoutSeconds() {
        long lockoutUntil = preferences.getLockoutUntil();
        long now = System.currentTimeMillis();
        if (lockoutUntil > now) {
            return Math.max(1, (lockoutUntil - now + 999) / 1000);
        }
        return 0L;
    }

    /**
     * Verifies an entered PIN with PBKDF2, enforcing rate-limiting delays.
     */
    @NonNull
    public AuthResult verifyPin(@Nullable String pin) {
        if (!PinCryptoUtils.isValidPinFormat(pin)) {
            return new AuthResult(AuthResult.Status.INVALID_FORMAT, preferences.getFailedAttempts(), 0);
        }

        long remainingLockout = getRemainingLockoutSeconds();
        if (remainingLockout > 0) {
            return new AuthResult(AuthResult.Status.LOCKED_OUT, preferences.getFailedAttempts(), remainingLockout);
        }

        String salt = preferences.getPinSalt();
        String hash = preferences.getPinHash();
        int iterations = preferences.getPinIterations();

        boolean match = PinCryptoUtils.verifyPin(pin, salt, hash, iterations);
        if (match) {
            unlockSession();
            return new AuthResult(AuthResult.Status.SUCCESS, 0, 0);
        } else {
            int attempts = preferences.getFailedAttempts() + 1;
            preferences.setFailedAttempts(attempts);

            long lockoutDurationMs = 0L;
            if (attempts >= 10) {
                lockoutDurationMs = 300_000L; // 5 minutes
            } else if (attempts >= 6) {
                lockoutDurationMs = 60_000L;  // 1 minute
            } else if (attempts >= 5) {
                lockoutDurationMs = 30_000L;  // 30 seconds
            }

            if (lockoutDurationMs > 0) {
                long lockoutUntil = System.currentTimeMillis() + lockoutDurationMs;
                preferences.setLockoutUntil(lockoutUntil);
                return new AuthResult(AuthResult.Status.LOCKED_OUT, attempts, lockoutDurationMs / 1000);
            }

            return new AuthResult(AuthResult.Status.INCORRECT_PIN, attempts, 0);
        }
    }

    /**
     * Sets up a new 6-digit PIN.
     */
    public boolean setupPin(@NonNull String pin) {
        if (!PinCryptoUtils.isValidPinFormat(pin)) {
            return false;
        }

        try {
            byte[] salt = PinCryptoUtils.generateSalt();
            byte[] hash = PinCryptoUtils.hashPin(pin, salt, PinCryptoUtils.DEFAULT_ITERATIONS);

            preferences.setPinSalt(PinCryptoUtils.toBase64(salt));
            preferences.setPinHash(PinCryptoUtils.toBase64(hash));
            preferences.setPinIterations(PinCryptoUtils.DEFAULT_ITERATIONS);
            preferences.setAppLockEnabled(true);
            preferences.setFailedAttempts(0);
            preferences.setLockoutUntil(0L);

            unlockSession();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Changes an existing PIN after validating the current PIN.
     */
    public boolean changePin(@NonNull String oldPin, @NonNull String newPin) {
        AuthResult result = verifyPin(oldPin);
        if (!result.isSuccess()) {
            return false;
        }
        return setupPin(newPin);
    }

    /**
     * Disables the application lock after verifying the current PIN.
     */
    public boolean disableAppLock(@NonNull String pin) {
        AuthResult result = verifyPin(pin);
        if (!result.isSuccess()) {
            return false;
        }
        preferences.clearPinData();
        unlockSession();
        return true;
    }

    /**
     * Checks if the device hardware and enrollment support biometric authentication.
     */
    public boolean canAuthenticateWithBiometrics() {
        if (!preferences.isBiometricEnabled()) {
            return false;
        }
        BiometricManager bm = BiometricManager.from(appContext);
        int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Sets whether activity is undergoing configuration change (screen rotation).
     */
    public void setChangingConfigurations(boolean changing) {
        this.isChangingConfigurations = changing;
    }

    /**
     * Lifecycle callback when the entire application transitions to the background.
     */
    public void onAppBackgrounded() {
        if (isChangingConfigurations) {
            isChangingConfigurations = false;
            return;
        }
        this.lastBackgroundTime = System.currentTimeMillis();
    }

    /**
     * Lifecycle callback when the entire application transitions to the foreground.
     */
    public void onAppForegrounded() {
        if (isChangingConfigurations) {
            isChangingConfigurations = false;
            return;
        }

        if (isAppLockEnabled()) {
            int timeoutSeconds = preferences.getAutoLockTimeoutSeconds();
            if (timeoutSeconds == AppPreferences.AUTO_LOCK_IMMEDIATELY || lastBackgroundTime == 0L) {
                lockSession();
            } else {
                long elapsed = System.currentTimeMillis() - lastBackgroundTime;
                if (elapsed >= (timeoutSeconds * 1000L)) {
                    lockSession();
                }
            }
        }
    }
}
