package com.nextgen.messages.ui.conversation;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;
import com.nextgen.messages.R;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.utils.ThemeHelper;

/**
 * Manages dynamic message bubble styling, corner shapes, contrast-validated colors,
 * chat density, and font scaling in conversation views.
 */
public final class BubbleStyleManager {

    private BubbleStyleManager() {
        // Prevent instantiation
    }

    public static class BubbleAppearance {
        public final GradientDrawable background;
        @ColorInt public final int textColor;
        @ColorInt public final int secondaryTextColor;

        public BubbleAppearance(
                @NonNull GradientDrawable background,
                @ColorInt int textColor,
                @ColorInt int secondaryTextColor
        ) {
            this.background = background;
            this.textColor = textColor;
            this.secondaryTextColor = secondaryTextColor;
        }
    }

    /**
     * Creates a custom GradientDrawable shape according to the user's chosen bubble style.
     */
    @NonNull
    public static GradientDrawable createBubbleDrawable(
            @NonNull Context context,
            boolean isIncoming,
            @NonNull String bubbleStyle,
            @ColorInt int backgroundColor
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(backgroundColor);

        float density = context.getResources().getDisplayMetrics().density;
        float r18 = 18f * density;
        float r4 = 4f * density;
        float r20 = 20f * density;
        float r8 = 8f * density;
        float r16 = 16f * density;

        switch (bubbleStyle.toUpperCase()) {
            case AppPreferences.BUBBLE_STYLE_ROUNDED:
                drawable.setCornerRadius(r20);
                break;

            case AppPreferences.BUBBLE_STYLE_MINIMAL:
                drawable.setCornerRadius(r8);
                break;

            case AppPreferences.BUBBLE_STYLE_CLASSIC:
                if (isIncoming) {
                    drawable.setCornerRadii(new float[]{
                            r16, r16, // Top-Left
                            r16, r16, // Top-Right
                            r16, r16, // Bottom-Right
                            r4, r4    // Bottom-Left
                    });
                } else {
                    drawable.setCornerRadii(new float[]{
                            r16, r16, // Top-Left
                            r16, r16, // Top-Right
                            r4, r4,   // Bottom-Right
                            r16, r16  // Bottom-Left
                    });
                }
                break;

            case AppPreferences.BUBBLE_STYLE_MODERN:
            default:
                if (isIncoming) {
                    drawable.setCornerRadii(new float[]{
                            r18, r18, // Top-Left
                            r18, r18, // Top-Right
                            r18, r18, // Bottom-Right
                            r4, r4    // Bottom-Left
                    });
                } else {
                    drawable.setCornerRadii(new float[]{
                            r18, r18, // Top-Left
                            r18, r18, // Top-Right
                            r4, r4,   // Bottom-Right
                            r18, r18  // Bottom-Left
                    });
                }
                break;
        }

        return drawable;
    }

    /**
     * Resolves the complete appearance (drawable + text colors) for incoming or outgoing bubbles.
     */
    @NonNull
    public static BubbleAppearance resolveAppearance(
            @NonNull Context context,
            boolean isIncoming
    ) {
        AppPreferences prefs = AppPreferences.getInstance(context);
        String style = prefs.getBubbleStyle();
        String colorPreset = prefs.getBubbleColorPreset();
        boolean isNight = ThemeHelper.isNightMode(context);

        int bgColor;
        int textColor;
        int secondaryColor;

        if (AppPreferences.BUBBLE_COLOR_DEFAULT.equalsIgnoreCase(colorPreset)) {
            if (isIncoming) {
                bgColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.LTGRAY);
                textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
                secondaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY);
            } else {
                bgColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, Color.BLUE);
                textColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.WHITE);
                secondaryColor = textColor;
            }
        } else {
            // Preset palette custom color resolution with guaranteed contrast
            if (isIncoming) {
                if (isNight) {
                    bgColor = ContextCompat.getColor(context, R.color.md_theme_dark_surfaceContainerHigh);
                    textColor = ContextCompat.getColor(context, R.color.md_theme_dark_onSurface);
                    secondaryColor = ContextCompat.getColor(context, R.color.md_theme_dark_onSurfaceVariant);
                } else {
                    bgColor = getPresetIncomingBg(context, colorPreset);
                    textColor = Color.parseColor("#191C20");
                    secondaryColor = Color.parseColor("#42474E");
                }
            } else {
                bgColor = getPresetOutgoingBg(context, colorPreset, isNight);
                textColor = Color.WHITE;
                secondaryColor = Color.parseColor("#E0E0E0");
            }
        }

        GradientDrawable bgDrawable = createBubbleDrawable(context, isIncoming, style, bgColor);
        return new BubbleAppearance(bgDrawable, textColor, secondaryColor);
    }

    private static int getPresetIncomingBg(@NonNull Context context, @NonNull String preset) {
        switch (preset.toUpperCase()) {
            case AppPreferences.BUBBLE_COLOR_BLUE:
                return ContextCompat.getColor(context, R.color.bubble_blue_incoming);
            case AppPreferences.BUBBLE_COLOR_GREEN:
                return ContextCompat.getColor(context, R.color.bubble_green_incoming);
            case AppPreferences.BUBBLE_COLOR_PURPLE:
                return ContextCompat.getColor(context, R.color.bubble_purple_incoming);
            case AppPreferences.BUBBLE_COLOR_ORANGE:
                return ContextCompat.getColor(context, R.color.bubble_orange_incoming);
            case AppPreferences.BUBBLE_COLOR_ROSE:
                return ContextCompat.getColor(context, R.color.bubble_rose_incoming);
            case AppPreferences.BUBBLE_COLOR_SLATE:
                return ContextCompat.getColor(context, R.color.bubble_slate_incoming);
            default:
                return MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.LTGRAY);
        }
    }

    private static int getPresetOutgoingBg(@NonNull Context context, @NonNull String preset, boolean isNight) {
        switch (preset.toUpperCase()) {
            case AppPreferences.BUBBLE_COLOR_BLUE:
                return ContextCompat.getColor(context, R.color.bubble_blue_outgoing);
            case AppPreferences.BUBBLE_COLOR_GREEN:
                return ContextCompat.getColor(context, R.color.bubble_green_outgoing);
            case AppPreferences.BUBBLE_COLOR_PURPLE:
                return ContextCompat.getColor(context, R.color.bubble_purple_outgoing);
            case AppPreferences.BUBBLE_COLOR_ORANGE:
                return ContextCompat.getColor(context, R.color.bubble_orange_outgoing);
            case AppPreferences.BUBBLE_COLOR_ROSE:
                return ContextCompat.getColor(context, R.color.bubble_rose_outgoing);
            case AppPreferences.BUBBLE_COLOR_SLATE:
                return ContextCompat.getColor(context, R.color.bubble_slate_outgoing);
            default:
                return MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, Color.BLUE);
        }
    }

    /**
     * Applies the configured font size to the message text view.
     */
    public static void applyFontSize(@NonNull Context context, @NonNull TextView textView) {
        AppPreferences prefs = AppPreferences.getInstance(context);
        String sizeKey = prefs.getFontSize();
        float targetSp;

        switch (sizeKey.toUpperCase()) {
            case AppPreferences.FONT_SIZE_SMALL:
                targetSp = 13f;
                break;
            case AppPreferences.FONT_SIZE_LARGE:
                targetSp = 17f;
                break;
            case AppPreferences.FONT_SIZE_XLARGE:
                targetSp = 20f;
                break;
            case AppPreferences.FONT_SIZE_DEFAULT:
            default:
                targetSp = 15f;
                break;
        }

        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSp);
    }

    /**
     * Applies chat density padding to message item rows and bubble containers.
     */
    public static void applyDensity(
            @NonNull Context context,
            @NonNull View itemRoot,
            @NonNull View bubbleContainer
    ) {
        AppPreferences prefs = AppPreferences.getInstance(context);
        String density = prefs.getChatDensity();
        float dp = context.getResources().getDisplayMetrics().density;

        int rowPadV;
        int bubblePadH;
        int bubblePadV;

        switch (density.toUpperCase()) {
            case AppPreferences.DENSITY_COMPACT:
                rowPadV = (int) (2 * dp);
                bubblePadH = (int) (10 * dp);
                bubblePadV = (int) (6 * dp);
                break;
            case AppPreferences.DENSITY_SPACIOUS:
                rowPadV = (int) (8 * dp);
                bubblePadH = (int) (18 * dp);
                bubblePadV = (int) (14 * dp);
                break;
            case AppPreferences.DENSITY_COMFORTABLE:
            default:
                rowPadV = (int) (4 * dp);
                bubblePadH = (int) (14 * dp);
                bubblePadV = (int) (10 * dp);
                break;
        }

        itemRoot.setPadding(itemRoot.getPaddingLeft(), rowPadV, itemRoot.getPaddingRight(), rowPadV);
        bubbleContainer.setPadding(bubblePadH, bubblePadV, bubblePadH, bubblePadV);
    }
}
