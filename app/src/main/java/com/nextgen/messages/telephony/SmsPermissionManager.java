package com.nextgen.messages.telephony;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized permission manager for SMS and Telephony permissions.
 * Follows Android best practices: prefers role-based permission grants,
 * minimizes explicit permission prompts, and avoids redundant permission requests.
 */
public class SmsPermissionManager {

    public static final String[] SMS_PERMISSIONS = new String[]{
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_MMS
    };

    public static final String PERMISSION_CONTACTS = Manifest.permission.READ_CONTACTS;

    private final Context context;

    public SmsPermissionManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Checks if all primary SMS permissions are currently granted.
     * Note: When NextGen Messages holds the default SMS role (ROLE_SMS),
     * Android automatically grants the SMS permissions at runtime.
     *
     * @return true if all primary SMS permissions are granted, false otherwise.
     */
    public boolean hasRequiredSmsPermissions() {
        for (String permission : SMS_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a specific permission is granted.
     */
    public boolean hasPermission(@NonNull String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks if contact reading permission is granted.
     * Per Step 2 requirements, this is not requested until contact features require it.
     */
    public boolean hasContactPermission() {
        return hasPermission(PERMISSION_CONTACTS);
    }

    /**
     * Returns a list of SMS permissions that have not yet been granted.
     */
    @NonNull
    public String[] getMissingSmsPermissions() {
        List<String> missing = new ArrayList<>();
        for (String permission : SMS_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing.toArray(new String[0]);
    }
}
