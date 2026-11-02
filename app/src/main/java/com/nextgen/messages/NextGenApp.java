package com.nextgen.messages;

import android.app.Application;
import com.google.android.material.color.DynamicColors;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.utils.ThemeHelper;

/**
 * Custom Application class for NextGen Messages.
 * Initializes Material You Dynamic Colors and applies saved theme preferences.
 */
public class NextGenApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        AppPreferences preferences = AppPreferences.getInstance(this);

        // Apply dynamic colors if enabled and available
        if (preferences.isDynamicColorsEnabled() && ThemeHelper.isDynamicColorSupported()) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }

        // Apply saved theme mode
        ThemeHelper.applyTheme(preferences.getThemeMode());

        // Initialize notification channels for Android 8.0+
        com.nextgen.messages.notification.NotificationChannelManager.initChannels(this);
    }
}
