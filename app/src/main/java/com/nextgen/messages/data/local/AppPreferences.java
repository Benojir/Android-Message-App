package com.nextgen.messages.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Manages persistent local preferences for NextGen Messages.
 * Uses application SharedPreferences for lightweight, thread-safe access.
 */
public class AppPreferences {

    private static final String PREF_NAME = "nextgen_messages_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode";
    private static final String KEY_DYNAMIC_COLORS = "pref_dynamic_colors";
    private static final String PREFIX_RECIPIENT_SIM = "pref_sim_recipient_";
    private static final String PREFIX_THREAD_DRAFT = "pref_draft_thread_";

    private static volatile AppPreferences instance;
    private final SharedPreferences sharedPreferences;

    private AppPreferences(Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static AppPreferences getInstance(Context context) {
        if (instance == null) {
            synchronized (AppPreferences.class) {
                if (instance == null) {
                    instance = new AppPreferences(context);
                }
            }
        }
        return instance;
    }

    /**
     * Gets the saved theme mode.
     * Defaults to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.
     */
    public int getThemeMode() {
        return sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /**
     * Saves the selected theme mode.
     */
    public void setThemeMode(int themeMode) {
        sharedPreferences.edit().putInt(KEY_THEME_MODE, themeMode).apply();
    }

    /**
     * Checks whether Material You dynamic colors are enabled.
     * Default is true.
     */
    public boolean isDynamicColorsEnabled() {
        return sharedPreferences.getBoolean(KEY_DYNAMIC_COLORS, true);
    }

    /**
     * Sets whether Material You dynamic colors should be used.
     */
    public void setDynamicColorsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply();
    }

    /**
     * Retrieves the preferred SIM subscription ID for a recipient, or -1 if not set.
     */
    public int getPreferredSimForRecipient(String recipient) {
        if (recipient == null || recipient.trim().isEmpty()) {
            return -1;
        }
        String cleanRecipient = recipient.replaceAll("[^0-9+A-Za-z]", "");
        return sharedPreferences.getInt(PREFIX_RECIPIENT_SIM + cleanRecipient, -1);
    }

    /**
     * Saves the preferred SIM subscription ID for a recipient.
     */
    public void setPreferredSimForRecipient(String recipient, int subscriptionId) {
        if (recipient == null || recipient.trim().isEmpty()) {
            return;
        }
        String cleanRecipient = recipient.replaceAll("[^0-9+A-Za-z]", "");
        if (subscriptionId <= 0) {
            sharedPreferences.edit().remove(PREFIX_RECIPIENT_SIM + cleanRecipient).apply();
        } else {
            sharedPreferences.edit().putInt(PREFIX_RECIPIENT_SIM + cleanRecipient, subscriptionId).apply();
        }
    }

    public static final String KEY_MMS_AUTO_DOWNLOAD = "pref_mms_auto_download";
    public static final String KEY_MMS_ROAMING_AUTO_DOWNLOAD = "pref_mms_roaming_auto_download";
    public static final String KEY_MMS_DELIVERY_REPORTS = "pref_mms_delivery_reports";
    public static final String KEY_MMS_READ_REPORTS = "pref_mms_read_reports";
    public static final String KEY_MMS_GROUP_MESSAGING = "pref_mms_group_messaging";
    public static final String KEY_SMART_INBOX_ENABLED = "pref_smart_inbox_enabled";
    public static final String KEY_SMART_INBOX_INITIAL_SCAN = "pref_smart_inbox_initial_scan";
    public static final String KEY_AUTO_UNARCHIVE = "pref_auto_unarchive";
    public static final String KEY_SUSPICIOUS_LINK_WARNINGS = "pref_suspicious_link_warnings";
    public static final String KEY_UNKNOWN_SENDER_FILTER = "pref_unknown_sender_filter";
    public static final String KEY_SPAM_PROTECTION = "pref_spam_protection";
    public static final String KEY_SPAM_SENSITIVITY = "pref_spam_sensitivity";

    /**
     * Checks if Auto-Unarchive is enabled when new incoming messages arrive. Default true.
     */
    public boolean isAutoUnarchiveEnabled() {
        return sharedPreferences.getBoolean(KEY_AUTO_UNARCHIVE, true);
    }

    public void setAutoUnarchiveEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_UNARCHIVE, enabled).apply();
    }

    /**
     * Checks if suspicious link warnings and phishing detection are enabled. Default true.
     */
    public boolean isSuspiciousLinkWarningsEnabled() {
        return sharedPreferences.getBoolean(KEY_SUSPICIOUS_LINK_WARNINGS, true);
    }

    public void setSuspiciousLinkWarningsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_SUSPICIOUS_LINK_WARNINGS, enabled).apply();
    }

    /**
     * Checks if Unknown Senders filtering is enabled. Default false.
     */
    public boolean isUnknownSenderFilterEnabled() {
        return sharedPreferences.getBoolean(KEY_UNKNOWN_SENDER_FILTER, false);
    }

    public void setUnknownSenderFilterEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_UNKNOWN_SENDER_FILTER, enabled).apply();
    }

    /**
     * Checks if Spam Protection is enabled. Default true.
     */
    public boolean isSpamProtectionEnabled() {
        return sharedPreferences.getBoolean(KEY_SPAM_PROTECTION, true);
    }

    public void setSpamProtectionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_SPAM_PROTECTION, enabled).apply();
    }

    /**
     * Gets the spam sensitivity level: "CONSERVATIVE" or "BALANCED". Default "CONSERVATIVE".
     */
    public String getSpamSensitivity() {
        return sharedPreferences.getString(KEY_SPAM_SENSITIVITY, "CONSERVATIVE");
    }

    public void setSpamSensitivity(String sensitivity) {
        sharedPreferences.edit().putString(KEY_SPAM_SENSITIVITY, sensitivity != null ? sensitivity : "CONSERVATIVE").apply();
    }

    /**
     * Checks if Smart Inbox automatic categorization is enabled. Default true.
     */
    public boolean isSmartInboxEnabled() {
        return sharedPreferences.getBoolean(KEY_SMART_INBOX_ENABLED, true);
    }

    public void setSmartInboxEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_SMART_INBOX_ENABLED, enabled).apply();
    }

    /**
     * Checks if initial scan of existing inbox messages has been completed.
     */
    public boolean isInitialScanCompleted() {
        return sharedPreferences.getBoolean(KEY_SMART_INBOX_INITIAL_SCAN, false);
    }

    public void setInitialScanCompleted(boolean completed) {
        sharedPreferences.edit().putBoolean(KEY_SMART_INBOX_INITIAL_SCAN, completed).apply();
    }

    /**
     * Checks if MMS auto-download is enabled. Default true.
     */
    public boolean isMmsAutoDownloadEnabled() {
        return sharedPreferences.getBoolean(KEY_MMS_AUTO_DOWNLOAD, true);
    }

    public void setMmsAutoDownloadEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_MMS_AUTO_DOWNLOAD, enabled).apply();
    }

    /**
     * Checks if MMS auto-download while roaming is enabled. Default false.
     */
    public boolean isMmsRoamingAutoDownloadEnabled() {
        return sharedPreferences.getBoolean(KEY_MMS_ROAMING_AUTO_DOWNLOAD, false);
    }

    public void setMmsRoamingAutoDownloadEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_MMS_ROAMING_AUTO_DOWNLOAD, enabled).apply();
    }

    /**
     * Checks if MMS delivery reports are enabled.
     */
    public boolean isMmsDeliveryReportsEnabled() {
        return sharedPreferences.getBoolean(KEY_MMS_DELIVERY_REPORTS, false);
    }

    public void setMmsDeliveryReportsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_MMS_DELIVERY_REPORTS, enabled).apply();
    }

    /**
     * Checks if MMS read reports are enabled.
     */
    public boolean isMmsReadReportsEnabled() {
        return sharedPreferences.getBoolean(KEY_MMS_READ_REPORTS, false);
    }

    public void setMmsReadReportsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_MMS_READ_REPORTS, enabled).apply();
    }

    /**
     * Checks if Group MMS messaging is enabled (vs individual SMS).
     */
    public boolean isGroupMmsEnabled() {
        return sharedPreferences.getBoolean(KEY_MMS_GROUP_MESSAGING, true);
    }

    public void setGroupMmsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_MMS_GROUP_MESSAGING, enabled).apply();
    }

    /**
     * Retrieves the saved draft for a conversation thread.
     */
    public String getDraftForThread(long threadId) {
        if (threadId <= 0) {
            return "";
        }
        return sharedPreferences.getString(PREFIX_THREAD_DRAFT + threadId, "");
    }

    public static final String KEY_NOTIFICATIONS_ENABLED = "pref_notifications_enabled";
    public static final String KEY_NOTIFICATION_PRIVACY = "pref_notification_privacy";
    public static final String KEY_NOTIFICATION_HIDE_OTP_LOCKSCREEN = "pref_notification_hide_otp_lockscreen";
    public static final String KEY_NOTIFICATION_SHOW_SIM_BADGE = "pref_notification_show_sim_badge";
    public static final String KEY_NOTIFICATION_VIBRATION = "pref_notification_vibration";
    public static final String KEY_NOTIFICATION_SOUND = "pref_notification_sound";

    /**
     * Checks if notifications are globally enabled. Default true.
     */
    public boolean isNotificationsEnabled() {
        return sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    /**
     * Returns notification privacy level: "FULL", "HIDE_BODY", or "HIDE_ALL". Default "FULL".
     */
    public String getNotificationPrivacy() {
        return sharedPreferences.getString(KEY_NOTIFICATION_PRIVACY, "FULL");
    }

    public void setNotificationPrivacy(String privacy) {
        sharedPreferences.edit().putString(KEY_NOTIFICATION_PRIVACY, privacy != null ? privacy : "FULL").apply();
    }

    /**
     * Checks if OTP content should be hidden on lock screen. Default true.
     */
    public boolean isHideOtpOnLockscreenEnabled() {
        return sharedPreferences.getBoolean(KEY_NOTIFICATION_HIDE_OTP_LOCKSCREEN, true);
    }

    public void setHideOtpOnLockscreenEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_HIDE_OTP_LOCKSCREEN, enabled).apply();
    }

    /**
     * Checks if SIM badge should be displayed in notifications on Dual SIM devices. Default true.
     */
    public boolean isShowSimBadgeInNotifications() {
        return sharedPreferences.getBoolean(KEY_NOTIFICATION_SHOW_SIM_BADGE, true);
    }

    public void setShowSimBadgeInNotifications(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_SHOW_SIM_BADGE, enabled).apply();
    }

    /**
     * Checks if vibration is enabled for notifications. Default true.
     */
    public boolean isNotificationVibrationEnabled() {
        return sharedPreferences.getBoolean(KEY_NOTIFICATION_VIBRATION, true);
    }

    public void setNotificationVibrationEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFICATION_VIBRATION, enabled).apply();
    }

    /**
     * Gets custom notification sound URI string, or null for system default.
     */
    public String getNotificationSoundUri() {
        return sharedPreferences.getString(KEY_NOTIFICATION_SOUND, null);
    }

    public void setNotificationSoundUri(String uriString) {
        if (uriString == null) {
            sharedPreferences.edit().remove(KEY_NOTIFICATION_SOUND).apply();
        } else {
            sharedPreferences.edit().putString(KEY_NOTIFICATION_SOUND, uriString).apply();
        }
    }

    /**
     * Saves or clears a draft for a conversation thread.
     */
    public void setDraftForThread(long threadId, String draft) {
        if (threadId <= 0) {
            return;
        }
        if (draft == null || draft.trim().isEmpty()) {
            sharedPreferences.edit().remove(PREFIX_THREAD_DRAFT + threadId).apply();
        } else {
            sharedPreferences.edit().putString(PREFIX_THREAD_DRAFT + threadId, draft).apply();
        }
    }
}
