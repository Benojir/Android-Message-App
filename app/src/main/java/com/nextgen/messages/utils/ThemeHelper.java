package com.nextgen.messages.utils;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.nextgen.messages.R;

/**
 * Utility helper for managing Material Design 3 day/night themes and Dynamic Colors.
 */
public final class ThemeHelper {

    public static final int THEME_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    public static final int THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES;

    private ThemeHelper() {
        // Prevent instantiation
    }

    /**
     * Applies the given night mode immediately to the application.
     */
    public static void applyTheme(int mode) {
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /**
     * Returns a human-readable localized label for the current theme mode.
     */
    public static String getThemeName(Context context, int mode) {
        if (mode == THEME_LIGHT) {
            return context.getString(R.string.theme_light);
        } else if (mode == THEME_DARK) {
            return context.getString(R.string.theme_dark);
        } else {
            return context.getString(R.string.theme_system);
        }
    }

    /**
     * Checks if Material You Dynamic Colors are supported on this device (Android 12+ / API 31+).
     */
    public static boolean isDynamicColorSupported() {
        return DynamicColors.isDynamicColorAvailable();
    }
}
