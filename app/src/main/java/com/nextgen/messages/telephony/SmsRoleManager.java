package com.nextgen.messages.telephony;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.provider.Telephony;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Dedicated manager for Android's official RoleManager default SMS application mechanism.
 * Handles role availability detection, role ownership queries, intent creation,
 * and system settings fallbacks.
 */
public class SmsRoleManager {

    /**
     * Represents the four distinct states of default SMS role eligibility and ownership.
     */
    public enum RoleStatus {
        /**
         * The device supports the SMS role, but NextGen Messages is not currently the default app.
         */
        NOT_DEFAULT,

        /**
         * NextGen Messages is currently the device's active default SMS application.
         */
        IS_DEFAULT,

        /**
         * The current device or Android user profile does not support assigning the SMS role.
         */
        ROLE_UNAVAILABLE,

        /**
         * NextGen Messages holds the role, but essential runtime SMS permissions are missing.
         */
        PERMISSIONS_REQUIRED
    }

    private final Context context;
    @Nullable
    private final RoleManager roleManager;
    private final SmsPermissionManager permissionManager;

    public SmsRoleManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.permissionManager = new SmsPermissionManager(this.context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            this.roleManager = this.context.getSystemService(RoleManager.class);
        } else {
            this.roleManager = null;
        }
    }

    /**
     * Detects whether the SMS role is available on this device and user profile.
     *
     * @return true if RoleManager.ROLE_SMS is supported, false otherwise.
     */
    public boolean isRoleAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            try {
                return roleManager.isRoleAvailable(RoleManager.ROLE_SMS);
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether NextGen Messages currently holds the default SMS role.
     * Combines RoleManager.isRoleHeld() with Telephony.Sms.getDefaultSmsPackage()
     * for complete reliability across all Android devices and custom OEM ROMs.
     *
     * @return true if NextGen Messages is the active default SMS application.
     */
    public boolean isDefaultSmsApp() {
        boolean isHeldViaRoleManager = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            try {
                isHeldViaRoleManager = roleManager.isRoleHeld(RoleManager.ROLE_SMS);
            } catch (Exception ignored) {
            }
        }

        String defaultPackage = null;
        try {
            defaultPackage = Telephony.Sms.getDefaultSmsPackage(context);
        } catch (Exception ignored) {
        }

        boolean isHeldViaTelephony = defaultPackage != null && defaultPackage.equals(context.getPackageName());

        return isHeldViaRoleManager || isHeldViaTelephony;
    }

    /**
     * Static utility to check whether the application is the default SMS app.
     */
    public static boolean isDefaultSmsApp(@NonNull Context context) {
        return new SmsRoleManager(context).isDefaultSmsApp();
    }

    /**
     * Launches the official system default SMS role request using an ActivityResultLauncher.
     */
    public void requestDefaultSmsRole(@NonNull androidx.activity.result.ActivityResultLauncher<Intent> launcher) {
        Intent intent = createRequestRoleIntent();
        if (intent != null) {
            launcher.launch(intent);
        }
    }

    /**
     * Evaluates the comprehensive default SMS status of the application.
     *
     * @return Current {@link RoleStatus}.
     */
    @NonNull
    public RoleStatus getRoleStatus() {
        if (!isRoleAvailable()) {
            return RoleStatus.ROLE_UNAVAILABLE;
        }

        boolean isDefault = isDefaultSmsApp();
        if (isDefault) {
            if (!permissionManager.hasRequiredSmsPermissions()) {
                return RoleStatus.PERMISSIONS_REQUIRED;
            }
            return RoleStatus.IS_DEFAULT;
        }

        return RoleStatus.NOT_DEFAULT;
    }

    /**
     * Creates the official system Intent to request the default SMS role.
     *
     * @return An Intent suitable for ActivityResultLauncher or startActivity,
     *         or null if role assignment is completely unsupported.
     */
    @Nullable
    public Intent createRequestRoleIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                try {
                    return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
                } catch (Exception ignored) {
                }
            }
        }

        // Standard telephony fallback
        try {
            return new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.getPackageName());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Creates an intent to open Android's Default Apps system settings page.
     * Useful when the user wants to manage default applications directly.
     */
    @NonNull
    public Intent createDefaultAppsSettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            return intent;
        }
        // Fallback to application details settings
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.getPackageName(), null));
    }
}
