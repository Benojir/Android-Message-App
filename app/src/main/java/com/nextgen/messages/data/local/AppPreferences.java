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

    /**
     * Retrieves the saved draft for a conversation thread.
     */
    public String getDraftForThread(long threadId) {
        if (threadId <= 0) {
            return "";
        }
        return sharedPreferences.getString(PREFIX_THREAD_DRAFT + threadId, "");
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
