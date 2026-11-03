package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.ui.conversation.WallpaperManager;
import com.nextgen.messages.utils.ThemeHelper;
import org.junit.Test;

/**
 * Unit tests for Step 10: Advanced Themes, Material You, Personalization & UI Tokens.
 */
public class Step10UiThemeTest {

    @Test
    public void testThemeConstantsAndAmoled() {
        assertEquals(-1, ThemeHelper.THEME_SYSTEM);
        assertEquals(1, ThemeHelper.THEME_LIGHT);
        assertEquals(2, ThemeHelper.THEME_DARK);
        assertEquals(3, ThemeHelper.THEME_AMOLED);

        // Verification of distinct theme integers
        assertNotEquals(ThemeHelper.THEME_LIGHT, ThemeHelper.THEME_DARK);
        assertNotEquals(ThemeHelper.THEME_DARK, ThemeHelper.THEME_AMOLED);
        assertNotEquals(ThemeHelper.THEME_SYSTEM, ThemeHelper.THEME_AMOLED);
    }

    @Test
    public void testAccentOverlayResourceResolution() {
        // Custom accent overlays
        assertEquals(R.style.ThemeOverlay_NextGen_Accent_Purple, ThemeHelper.getAccentOverlayRes("PURPLE"));
        assertEquals(R.style.ThemeOverlay_NextGen_Accent_Green, ThemeHelper.getAccentOverlayRes("GREEN"));
        assertEquals(R.style.ThemeOverlay_NextGen_Accent_Teal, ThemeHelper.getAccentOverlayRes("TEAL"));
        assertEquals(R.style.ThemeOverlay_NextGen_Accent_Orange, ThemeHelper.getAccentOverlayRes("ORANGE"));
        assertEquals(R.style.ThemeOverlay_NextGen_Accent_Red, ThemeHelper.getAccentOverlayRes("RED"));
        assertEquals(R.style.ThemeOverlay_NextGen_Accent_Pink, ThemeHelper.getAccentOverlayRes("PINK"));

        // Case insensitivity
        assertEquals(R.style.ThemeOverlay_NextGen_Accent_Purple, ThemeHelper.getAccentOverlayRes("purple"));
        assertEquals(R.style.ThemeOverlay_NextGen_Accent_Green, ThemeHelper.getAccentOverlayRes("Green"));

        // Default or unmapped uses brand primary (0)
        assertEquals(0, ThemeHelper.getAccentOverlayRes("BLUE"));
        assertEquals(0, ThemeHelper.getAccentOverlayRes("DEFAULT"));
        assertEquals(0, ThemeHelper.getAccentOverlayRes("UNKNOWN"));
    }

    @Test
    public void testCuratedWallpaperGradients() {
        GradientDrawable gTwilight = WallpaperManager.createGradient("twilight");
        assertNotNull(gTwilight);

        GradientDrawable gDawn = WallpaperManager.createGradient("dawn");
        assertNotNull(gDawn);

        GradientDrawable gForest = WallpaperManager.createGradient("forest");
        assertNotNull(gForest);

        GradientDrawable gMidnight = WallpaperManager.createGradient("midnight");
        assertNotNull(gMidnight);

        GradientDrawable gOcean = WallpaperManager.createGradient("ocean");
        assertNotNull(gOcean);

        // Unrecognized key returns null gracefully
        assertNull(WallpaperManager.createGradient("nonexistent_gradient"));
    }

    @Test
    public void testPreferenceTokenDefaults() {
        assertEquals("DEFAULT", AppPreferences.ACCENT_DEFAULT);
        assertEquals("MODERN", AppPreferences.BUBBLE_STYLE_MODERN);
        assertEquals("CLASSIC", AppPreferences.BUBBLE_STYLE_CLASSIC);
        assertEquals("ROUNDED", AppPreferences.BUBBLE_STYLE_ROUNDED);
        assertEquals("MINIMAL", AppPreferences.BUBBLE_STYLE_MINIMAL);

        assertEquals("DEFAULT", AppPreferences.BUBBLE_COLOR_DEFAULT);
        assertEquals("BLUE", AppPreferences.BUBBLE_COLOR_BLUE);
        assertEquals("GREEN", AppPreferences.BUBBLE_COLOR_GREEN);
        assertEquals("PURPLE", AppPreferences.BUBBLE_COLOR_PURPLE);

        assertEquals("NONE", AppPreferences.WALLPAPER_TYPE_NONE);
        assertEquals("SOLID", AppPreferences.WALLPAPER_TYPE_SOLID);
        assertEquals("GRADIENT", AppPreferences.WALLPAPER_TYPE_GRADIENT);
        assertEquals("IMAGE", AppPreferences.WALLPAPER_TYPE_IMAGE);

        assertEquals("DEFAULT", AppPreferences.FONT_SIZE_DEFAULT);
        assertEquals("SMALL", AppPreferences.FONT_SIZE_SMALL);
        assertEquals("LARGE", AppPreferences.FONT_SIZE_LARGE);
        assertEquals("XLARGE", AppPreferences.FONT_SIZE_XLARGE);

        assertEquals("COMFORTABLE", AppPreferences.DENSITY_COMFORTABLE);
        assertEquals("COMPACT", AppPreferences.DENSITY_COMPACT);
        assertEquals("SPACIOUS", AppPreferences.DENSITY_SPACIOUS);

        assertEquals("COMFORTABLE", AppPreferences.INBOX_LAYOUT_COMFORTABLE);
        assertEquals("COMPACT", AppPreferences.INBOX_LAYOUT_COMPACT);
    }
}
