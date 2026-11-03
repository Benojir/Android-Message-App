package com.nextgen.messages.ui.conversation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.local.AppPreferences;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages background wallpapers for conversation views.
 * Supports solid colors, curated aesthetic gradients, custom image loading
 * with safe downsampling, and per-conversation overrides.
 */
public final class WallpaperManager {

    private static final String TAG = "WallpaperManager";

    // In-memory cache for decoded wallpaper bitmaps to prevent repeated disk I/O and OOMs
    private static final Map<String, WeakReference<Bitmap>> BITMAP_CACHE = new ConcurrentHashMap<>();

    private WallpaperManager() {
        // Prevent instantiation
    }

    /**
     * Applies the appropriate wallpaper (per-conversation override or global default)
     * to the conversation root view.
     */
    public static void applyWallpaper(
            @NonNull Context context,
            @NonNull View rootView,
            long threadId
    ) {
        AppPreferences prefs = AppPreferences.getInstance(context);

        // 1. Check for per-conversation override first
        String type = (threadId > 0) ? prefs.getConversationWallpaperType(threadId) : null;
        String value = (threadId > 0) ? prefs.getConversationWallpaperValue(threadId) : null;

        // 2. Fall back to global wallpaper
        if (type == null || AppPreferences.WALLPAPER_TYPE_NONE.equalsIgnoreCase(type)) {
            type = prefs.getWallpaperType();
            value = prefs.getWallpaperValue();
        }

        // 3. Render wallpaper
        Drawable wallpaperDrawable = resolveDrawable(context, type, value);
        if (wallpaperDrawable != null) {
            rootView.setBackground(wallpaperDrawable);
        } else {
            // Default background according to theme
            rootView.setBackground(null);
        }
    }

    @Nullable
    public static Drawable resolveDrawable(
            @NonNull Context context,
            @Nullable String type,
            @Nullable String value
    ) {
        if (type == null || AppPreferences.WALLPAPER_TYPE_NONE.equalsIgnoreCase(type) || value == null) {
            return null;
        }

        switch (type.toUpperCase()) {
            case AppPreferences.WALLPAPER_TYPE_SOLID:
                try {
                    int color = Color.parseColor(value);
                    return new ColorDrawable(color);
                } catch (Exception e) {
                    Log.w(TAG, "Failed parsing solid wallpaper color: " + value, e);
                    return null;
                }

            case AppPreferences.WALLPAPER_TYPE_GRADIENT:
                return createGradient(value);

            case AppPreferences.WALLPAPER_TYPE_IMAGE:
                return loadImageDrawable(context, value);

            default:
                return null;
        }
    }

    /**
     * Creates a curated high-aesthetic gradient background.
     */
    @Nullable
    public static GradientDrawable createGradient(@NonNull String gradientKey) {
        GradientDrawable gradient = new GradientDrawable();
        gradient.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);

        switch (gradientKey.toLowerCase()) {
            case "twilight":
                gradient.setColors(new int[]{
                        Color.parseColor("#141E30"),
                        Color.parseColor("#243B55")
                });
                return gradient;

            case "dawn":
                gradient.setColors(new int[]{
                        Color.parseColor("#FFEAA7"),
                        Color.parseColor("#DFE6E9")
                });
                return gradient;

            case "forest":
                gradient.setColors(new int[]{
                        Color.parseColor("#134E5E"),
                        Color.parseColor("#71B280")
                });
                return gradient;

            case "midnight":
                gradient.setColors(new int[]{
                        Color.parseColor("#0B0C10"),
                        Color.parseColor("#1F2833")
                });
                return gradient;

            case "ocean":
                gradient.setColors(new int[]{
                        Color.parseColor("#000428"),
                        Color.parseColor("#004E92")
                });
                return gradient;

            default:
                return null;
        }
    }

    /**
     * Safely decodes a user wallpaper image with memory-efficient downsampling.
     */
    @Nullable
    private static Drawable loadImageDrawable(@NonNull Context context, @NonNull String uriString) {
        WeakReference<Bitmap> ref = BITMAP_CACHE.get(uriString);
        Bitmap cached = (ref != null) ? ref.get() : null;
        if (cached != null && !cached.isRecycled()) {
            return new BitmapDrawable(context.getResources(), cached);
        }

        try {
            Uri uri = Uri.parse(uriString);

            // Step 1: Decode bounds only
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                if (stream == null) return null;
                BitmapFactory.decodeStream(stream, null, boundsOptions);
            }

            int reqWidth = 1080;
            int reqHeight = 1920;
            int inSampleSize = 1;

            if (boundsOptions.outHeight > reqHeight || boundsOptions.outWidth > reqWidth) {
                final int halfHeight = boundsOptions.outHeight / 2;
                final int halfWidth = boundsOptions.outWidth / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }

            // Step 2: Decode downsampled bitmap
            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = inSampleSize;
            decodeOptions.inPreferredConfig = Bitmap.Config.RGB_565; // High memory savings

            Bitmap bitmap;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                if (stream == null) return null;
                bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions);
            }

            if (bitmap != null) {
                BITMAP_CACHE.put(uriString, new WeakReference<>(bitmap));
                return new BitmapDrawable(context.getResources(), bitmap);
            }
        } catch (SecurityException se) {
            Log.w(TAG, "URI permission revoked or expired for wallpaper: " + uriString);
        } catch (Exception e) {
            Log.w(TAG, "Error decoding custom wallpaper image", e);
        }

        return null;
    }
}
