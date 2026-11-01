package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.appcompat.app.AppCompatDelegate;
import com.nextgen.messages.utils.ThemeHelper;
import org.junit.Test;

public class ThemeHelperTest {

    @Test
    public void testThemeConstantsMatchAppCompatDelegate() {
        assertEquals("THEME_SYSTEM must match MODE_NIGHT_FOLLOW_SYSTEM",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeHelper.THEME_SYSTEM);
        assertEquals("THEME_LIGHT must match MODE_NIGHT_NO",
                AppCompatDelegate.MODE_NIGHT_NO, ThemeHelper.THEME_LIGHT);
        assertEquals("THEME_DARK must match MODE_NIGHT_YES",
                AppCompatDelegate.MODE_NIGHT_YES, ThemeHelper.THEME_DARK);
    }
}
