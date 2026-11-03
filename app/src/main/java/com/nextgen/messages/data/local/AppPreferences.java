package com.nextgen.messages.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

    // Step 10: Advanced Themes, Personalization & UI Tokens
    public static final String KEY_ACCENT_COLOR = "pref_accent_color";
    public static final String KEY_BUBBLE_STYLE = "pref_bubble_style";
    public static final String KEY_BUBBLE_COLOR_PRESET = "pref_bubble_color_preset";
    public static final String KEY_WALLPAPER_TYPE = "pref_wallpaper_type";
    public static final String KEY_WALLPAPER_VALUE = "pref_wallpaper_value";
    public static final String KEY_FONT_SIZE = "pref_font_size";
    public static final String KEY_CHAT_DENSITY = "pref_chat_density";
    public static final String KEY_INBOX_LAYOUT = "pref_inbox_layout";

    private static final String PREFIX_CONVERSATION_WALLPAPER_TYPE = "pref_conv_wp_type_";
    private static final String PREFIX_CONVERSATION_WALLPAPER_VALUE = "pref_conv_wp_val_";

    public static final String ACCENT_DEFAULT = "DEFAULT";
    public static final String ACCENT_BLUE = "BLUE";
    public static final String ACCENT_PURPLE = "PURPLE";
    public static final String ACCENT_GREEN = "GREEN";
    public static final String ACCENT_TEAL = "TEAL";
    public static final String ACCENT_ORANGE = "ORANGE";
    public static final String ACCENT_RED = "RED";
    public static final String ACCENT_PINK = "PINK";

    public static final String BUBBLE_STYLE_MODERN = "MODERN";
    public static final String BUBBLE_STYLE_CLASSIC = "CLASSIC";
    public static final String BUBBLE_STYLE_ROUNDED = "ROUNDED";
    public static final String BUBBLE_STYLE_MINIMAL = "MINIMAL";

    public static final String BUBBLE_COLOR_DEFAULT = "DEFAULT";
    public static final String BUBBLE_COLOR_BLUE = "BLUE";
    public static final String BUBBLE_COLOR_GREEN = "GREEN";
    public static final String BUBBLE_COLOR_PURPLE = "PURPLE";
    public static final String BUBBLE_COLOR_ORANGE = "ORANGE";
    public static final String BUBBLE_COLOR_ROSE = "ROSE";
    public static final String BUBBLE_COLOR_SLATE = "SLATE";

    public static final String WALLPAPER_TYPE_NONE = "NONE";
    public static final String WALLPAPER_TYPE_SOLID = "SOLID";
    public static final String WALLPAPER_TYPE_GRADIENT = "GRADIENT";
    public static final String WALLPAPER_TYPE_IMAGE = "IMAGE";

    public static final String FONT_SIZE_SMALL = "SMALL";
    public static final String FONT_SIZE_DEFAULT = "DEFAULT";
    public static final String FONT_SIZE_LARGE = "LARGE";
    public static final String FONT_SIZE_XLARGE = "XLARGE";

    public static final String DENSITY_COMPACT = "COMPACT";
    public static final String DENSITY_COMFORTABLE = "COMFORTABLE";
    public static final String DENSITY_SPACIOUS = "SPACIOUS";

    public static final String INBOX_LAYOUT_COMFORTABLE = "COMFORTABLE";
    public static final String INBOX_LAYOUT_COMPACT = "COMPACT";

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

    // --- Step 10: Personalization Getters & Setters ---

    public String getAccentColor() {
        return sharedPreferences.getString(KEY_ACCENT_COLOR, ACCENT_DEFAULT);
    }

    public void setAccentColor(String accent) {
        sharedPreferences.edit().putString(KEY_ACCENT_COLOR, accent != null ? accent : ACCENT_DEFAULT).apply();
    }

    public String getBubbleStyle() {
        return sharedPreferences.getString(KEY_BUBBLE_STYLE, BUBBLE_STYLE_MODERN);
    }

    public void setBubbleStyle(String style) {
        sharedPreferences.edit().putString(KEY_BUBBLE_STYLE, style != null ? style : BUBBLE_STYLE_MODERN).apply();
    }

    public String getBubbleColorPreset() {
        return sharedPreferences.getString(KEY_BUBBLE_COLOR_PRESET, BUBBLE_COLOR_DEFAULT);
    }

    public void setBubbleColorPreset(String preset) {
        sharedPreferences.edit().putString(KEY_BUBBLE_COLOR_PRESET, preset != null ? preset : BUBBLE_COLOR_DEFAULT).apply();
    }

    public String getWallpaperType() {
        return sharedPreferences.getString(KEY_WALLPAPER_TYPE, WALLPAPER_TYPE_NONE);
    }

    public void setWallpaperType(String type) {
        sharedPreferences.edit().putString(KEY_WALLPAPER_TYPE, type != null ? type : WALLPAPER_TYPE_NONE).apply();
    }

    public String getWallpaperValue() {
        return sharedPreferences.getString(KEY_WALLPAPER_VALUE, null);
    }

    public void setWallpaperValue(String value) {
        if (value == null) {
            sharedPreferences.edit().remove(KEY_WALLPAPER_VALUE).apply();
        } else {
            sharedPreferences.edit().putString(KEY_WALLPAPER_VALUE, value).apply();
        }
    }

    public String getConversationWallpaperType(long threadId) {
        return sharedPreferences.getString(PREFIX_CONVERSATION_WALLPAPER_TYPE + threadId, null);
    }

    public String getConversationWallpaperValue(long threadId) {
        return sharedPreferences.getString(PREFIX_CONVERSATION_WALLPAPER_VALUE + threadId, null);
    }

    public void setConversationWallpaper(long threadId, String type, String value) {
        if (threadId <= 0) return;
        if (type == null || WALLPAPER_TYPE_NONE.equalsIgnoreCase(type)) {
            clearConversationWallpaper(threadId);
        } else {
            sharedPreferences.edit()
                    .putString(PREFIX_CONVERSATION_WALLPAPER_TYPE + threadId, type)
                    .putString(PREFIX_CONVERSATION_WALLPAPER_VALUE + threadId, value)
                    .apply();
        }
    }

    public void clearConversationWallpaper(long threadId) {
        if (threadId <= 0) return;
        sharedPreferences.edit()
                .remove(PREFIX_CONVERSATION_WALLPAPER_TYPE + threadId)
                .remove(PREFIX_CONVERSATION_WALLPAPER_VALUE + threadId)
                .apply();
    }

    public String getFontSize() {
        return sharedPreferences.getString(KEY_FONT_SIZE, FONT_SIZE_DEFAULT);
    }

    public void setFontSize(String size) {
        sharedPreferences.edit().putString(KEY_FONT_SIZE, size != null ? size : FONT_SIZE_DEFAULT).apply();
    }

    public String getChatDensity() {
        return sharedPreferences.getString(KEY_CHAT_DENSITY, DENSITY_COMFORTABLE);
    }

    public void setChatDensity(String density) {
        sharedPreferences.edit().putString(KEY_CHAT_DENSITY, density != null ? density : DENSITY_COMFORTABLE).apply();
    }

    public String getInboxLayout() {
        return sharedPreferences.getString(KEY_INBOX_LAYOUT, INBOX_LAYOUT_COMFORTABLE);
    }

    public void setInboxLayout(String layout) {
        sharedPreferences.edit().putString(KEY_INBOX_LAYOUT, layout != null ? layout : INBOX_LAYOUT_COMFORTABLE).apply();
    }

    // =========================================================================
    // Step 11: Privacy, Security, App Lock & Encrypted Backup Constants & Settings
    // =========================================================================

    public static final String KEY_APP_LOCK_ENABLED = "pref_app_lock_enabled";
    public static final String KEY_PIN_SALT = "pref_pin_salt";
    public static final String KEY_PIN_HASH = "pref_pin_hash";
    public static final String KEY_PIN_ITERATIONS = "pref_pin_iterations";
    public static final String KEY_BIOMETRIC_ENABLED = "pref_biometric_enabled";
    public static final String KEY_AUTO_LOCK_TIMEOUT = "pref_auto_lock_timeout";
    public static final String KEY_FAILED_ATTEMPTS = "pref_failed_attempts";
    public static final String KEY_LOCKOUT_UNTIL = "pref_lockout_until";
    public static final String KEY_SCREENSHOT_PROTECTION = "pref_screenshot_protection";
    public static final String KEY_PRIVATE_THREADS = "pref_private_threads";

    public static final String SCREENSHOT_DISABLED = "DISABLED";
    public static final String SCREENSHOT_PRIVATE_ONLY = "PRIVATE_ONLY";
    public static final String SCREENSHOT_ALL = "ALL";

    public static final int AUTO_LOCK_IMMEDIATELY = 0;
    public static final int AUTO_LOCK_30_SECONDS = 30;
    public static final int AUTO_LOCK_1_MINUTE = 60;
    public static final int AUTO_LOCK_5_MINUTES = 300;
    public static final int AUTO_LOCK_15_MINUTES = 900;

    public boolean isAppLockEnabled() {
        return sharedPreferences.getBoolean(KEY_APP_LOCK_ENABLED, false) && hasConfiguredPin();
    }

    public void setAppLockEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply();
    }

    public boolean hasConfiguredPin() {
        return getPinHash() != null && getPinSalt() != null;
    }

    @Nullable
    public String getPinSalt() {
        return sharedPreferences.getString(KEY_PIN_SALT, null);
    }

    public void setPinSalt(@Nullable String saltBase64) {
        if (saltBase64 == null) {
            sharedPreferences.edit().remove(KEY_PIN_SALT).apply();
        } else {
            sharedPreferences.edit().putString(KEY_PIN_SALT, saltBase64).apply();
        }
    }

    @Nullable
    public String getPinHash() {
        return sharedPreferences.getString(KEY_PIN_HASH, null);
    }

    public void setPinHash(@Nullable String hashBase64) {
        if (hashBase64 == null) {
            sharedPreferences.edit().remove(KEY_PIN_HASH).apply();
        } else {
            sharedPreferences.edit().putString(KEY_PIN_HASH, hashBase64).apply();
        }
    }

    public int getPinIterations() {
        return sharedPreferences.getInt(KEY_PIN_ITERATIONS, 100000);
    }

    public void setPinIterations(int iterations) {
        sharedPreferences.edit().putInt(KEY_PIN_ITERATIONS, iterations).apply();
    }

    public boolean isBiometricEnabled() {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, true);
    }

    public void setBiometricEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
    }

    public int getAutoLockTimeoutSeconds() {
        return sharedPreferences.getInt(KEY_AUTO_LOCK_TIMEOUT, AUTO_LOCK_IMMEDIATELY);
    }

    public void setAutoLockTimeoutSeconds(int seconds) {
        sharedPreferences.edit().putInt(KEY_AUTO_LOCK_TIMEOUT, seconds).apply();
    }

    public int getFailedAttempts() {
        return sharedPreferences.getInt(KEY_FAILED_ATTEMPTS, 0);
    }

    public void setFailedAttempts(int attempts) {
        sharedPreferences.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();
    }

    public long getLockoutUntil() {
        return sharedPreferences.getLong(KEY_LOCKOUT_UNTIL, 0L);
    }

    public void setLockoutUntil(long timestampMs) {
        sharedPreferences.edit().putLong(KEY_LOCKOUT_UNTIL, timestampMs).apply();
    }

    public void clearPinData() {
        sharedPreferences.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .remove(KEY_PIN_ITERATIONS)
                .remove(KEY_APP_LOCK_ENABLED)
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_LOCKOUT_UNTIL)
                .apply();
    }

    @NonNull
    public String getScreenshotProtection() {
        return sharedPreferences.getString(KEY_SCREENSHOT_PROTECTION, SCREENSHOT_PRIVATE_ONLY);
    }

    public void setScreenshotProtection(@Nullable String mode) {
        sharedPreferences.edit()
                .putString(KEY_SCREENSHOT_PROTECTION, mode != null ? mode : SCREENSHOT_PRIVATE_ONLY)
                .apply();
    }

    @NonNull
    public Set<String> getPrivateThreadIds() {
        Set<String> set = sharedPreferences.getStringSet(KEY_PRIVATE_THREADS, null);
        return set != null ? new HashSet<>(set) : new HashSet<>();
    }

    public void setPrivateThreadIds(@NonNull Set<String> threadIds) {
        sharedPreferences.edit().putStringSet(KEY_PRIVATE_THREADS, new HashSet<>(threadIds)).apply();
    }

    public boolean isConversationPrivate(long threadId) {
        if (threadId <= 0) return false;
        Set<String> privateThreads = sharedPreferences.getStringSet(KEY_PRIVATE_THREADS, null);
        return privateThreads != null && privateThreads.contains(String.valueOf(threadId));
    }

    public void setConversationPrivate(long threadId, boolean isPrivate) {
        if (threadId <= 0) return;
        Set<String> current = getPrivateThreadIds();
        if (isPrivate) {
            current.add(String.valueOf(threadId));
        } else {
            current.remove(String.valueOf(threadId));
        }
        setPrivateThreadIds(current);
    }
}
