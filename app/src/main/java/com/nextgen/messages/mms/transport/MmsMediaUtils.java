package com.nextgen.messages.mms.transport;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Handles image downsampling, JPEG compression, and MIME/filename extraction
 * to satisfy carrier MMS size limitations.
 */
public final class MmsMediaUtils {

    private MmsMediaUtils() {}

    /**
     * Reads and safely compresses an image URI so that its byte size is within maxSizeBytes.
     */
    @Nullable
    public static byte[] compressImageIfNeeded(
            @NonNull Context context,
            @NonNull Uri imageUri,
            int maxSizeBytes
    ) {
        ContentResolver resolver = context.getContentResolver();

        // 1. Check original size
        byte[] original = readMediaBytes(context, imageUri);
        if (original == null) {
            return null;
        }

        if (original.length <= maxSizeBytes && original.length > 0) {
            return original;
        }

        // 2. Decode image bounds
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream is = resolver.openInputStream(imageUri)) {
                if (is == null) return original;
                BitmapFactory.decodeStream(is, null, options);
            }

            int reqWidth = 1280;
            int reqHeight = 720;
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;

            Bitmap bitmap;
            try (InputStream is = resolver.openInputStream(imageUri)) {
                bitmap = BitmapFactory.decodeStream(is, null, options);
            }

            if (bitmap == null) {
                return original;
            }

            // 3. Compress with decreasing quality until within limit
            int quality = 85;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

            while (baos.size() > maxSizeBytes && quality > 30) {
                baos.reset();
                quality -= 15;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            }

            bitmap.recycle();
            return baos.toByteArray();
        } catch (Throwable t) {
            return original;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @Nullable
    public static byte[] readMediaBytes(@NonNull Context context, @NonNull Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (is == null) return null;
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    public static String getMediaMimeType(@NonNull Context context, @NonNull Uri uri) {
        String type = context.getContentResolver().getType(uri);
        if (type != null) {
            return type.toLowerCase();
        }
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (extension != null) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mime != null) return mime.toLowerCase();
        }
        return "application/octet-stream";
    }

    @NonNull
    public static String getFileName(@NonNull Context context, @NonNull Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIdx >= 0) {
                    String name = cursor.getString(nameIdx);
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String lastSegment = uri.getLastPathSegment();
        return lastSegment != null ? lastSegment : "attachment";
    }

    public static long getFileSize(@NonNull Context context, @NonNull Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIdx >= 0) {
                    return cursor.getLong(sizeIdx);
                }
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }
}
