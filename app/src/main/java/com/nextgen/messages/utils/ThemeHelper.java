package com.nextgen.messages.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.color.DynamicColors;
import com.nextgen.messages.R;
import com.nextgen.messages.data.local.AppPreferences;

/**
 * Enhanced Theme & Appearance Helper for NextGen Messages.
 * Orchestrates Material You Dynamic Colors, Day/Night modes, AMOLED Pure Black,
 * Custom Accent Color Overlays, and edge-to-edge system bar contrast.
 */
public final class ThemeHelper {

    public static final int THEME_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    public static final int THEME_LIGHT = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int THEME_DARK = AppCompatDelegate.MODE_NIGHT_YES;
    public static final int THEME_AMOLED = 3;

    private ThemeHelper() {
        // Prevent instantiation
    }

    /**
     * Applies the selected night mode to AppCompatDelegate globally.
     */
    public static void applyTheme(int mode) {
        applyGlobalNightMode(mode);
    }

    public static void applyGlobalNightMode(int mode) {
        if (mode == THEME_AMOLED) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    /**
     * Applies themes, accent overlays, AMOLED styling, and system bar appearance to an Activity.
     * Should be called in Activity.onCreate() before setContentView().
     */
    public static void applyToActivity(@NonNull Activity activity) {
        AppPreferences prefs = AppPreferences.getInstance(activity);
        int themeMode = prefs.getThemeMode();

        // 1. AMOLED Base Theme Override
        boolean isAmoled = (themeMode == THEME_AMOLED);
        if (isAmoled) {
            activity.setTheme(R.style.Theme_NextGenMessages_Amoled);
        }

        // 2. Custom Accent Color Overlay (when not using Material You Dynamic Colors)
        boolean dynamicColorsActive = prefs.isDynamicColorsEnabled() && isDynamicColorSupported();
        if (!dynamicColorsActive) {
            String accent = prefs.getAccentColor();
            int overlayRes = getAccentOverlayRes(accent);
            if (overlayRes != 0) {
                activity.getTheme().applyStyle(overlayRes, true);
            }
        }

        // 3. System Bar Edge-to-Edge and Contrast Management
        setupSystemBars(activity, isAmoled);
    }

    /**
     * Configures the system status and navigation bars with transparent backgrounds
     * and readable foreground icon contrast.
     */
    public static void setupSystemBars(@NonNull Activity activity, boolean isAmoled) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            boolean isNight = isNightMode(activity) || isAmoled;
            // On dark backgrounds, light icons are needed (isAppearanceLight = false)
            controller.setAppearanceLightStatusBars(!isNight);
            controller.setAppearanceLightNavigationBars(!isNight);
        }
    }

    /**
     * Checks if the device is currently rendering in dark/night mode.
     */
    public static boolean isNightMode(@NonNull Context context) {
        AppPreferences prefs = AppPreferences.getInstance(context);
        int mode = prefs.getThemeMode();
        if (mode == THEME_DARK || mode == THEME_AMOLED) {
            return true;
        }
        if (mode == THEME_LIGHT) {
            return false;
        }
        int nightFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Returns a human-readable localized label for the current theme mode.
     */
    @NonNull
    public static String getThemeName(@NonNull Context context, int mode) {
        if (mode == THEME_LIGHT) {
            return context.getString(R.string.theme_light);
        } else if (mode == THEME_DARK) {
            return context.getString(R.string.theme_dark);
        } else if (mode == THEME_AMOLED) {
            return context.getString(R.string.theme_amoled);
        } else {
            return context.getString(R.string.theme_system);
        }
    }

    /**
     * Checks if Material You Dynamic Colors are supported on this device (Android 12+ / API 31+).
     */
    public static boolean isDynamicColorSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && DynamicColors.isDynamicColorAvailable();
    }

    /**
     * Resolves the theme overlay style resource for a given accent key.
     */
    @StyleRes
    public static int getAccentOverlayRes(@NonNull String accentKey) {
        switch (accentKey.toUpperCase()) {
            case "PURPLE":
                return R.style.ThemeOverlay_NextGen_Accent_Purple;
            case "GREEN":
                return R.style.ThemeOverlay_NextGen_Accent_Green;
            case "TEAL":
                return R.style.ThemeOverlay_NextGen_Accent_Teal;
            case "ORANGE":
                return R.style.ThemeOverlay_NextGen_Accent_Orange;
            case "RED":
                return R.style.ThemeOverlay_NextGen_Accent_Red;
            case "PINK":
                return R.style.ThemeOverlay_NextGen_Accent_Pink;
            case "BLUE":
            case "DEFAULT":
            default:
                return 0; // Uses default brand primary
        }
    }
}
