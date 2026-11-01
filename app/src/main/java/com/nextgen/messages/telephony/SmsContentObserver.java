package com.nextgen.messages.telephony;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lifecycle-aware ContentObserver that monitors Telephony.Sms.CONTENT_URI for incoming or changed SMS messages.
 * Implements debouncing (350ms) to prevent UI thrashing on multi-part SMS or bulk database updates.
 */
public class SmsContentObserver extends ContentObserver {

    private static final String TAG = "SmsContentObserver";
    private static final long DEBOUNCE_DELAY_MS = 350L;

    public interface OnSmsChangedListener {
        void onSmsChanged();
    }

    private final Handler handler;
    private final OnSmsChangedListener listener;
    private final Runnable debounceRunnable;
    private boolean isRegistered = false;

    public SmsContentObserver(@NonNull Handler handler, @NonNull OnSmsChangedListener listener) {
        super(handler);
        this.handler = handler;
        this.listener = listener;
        this.debounceRunnable = () -> {
            Log.d(TAG, "Debounced SMS change triggered. Notifying listener.");
            this.listener.onSmsChanged();
        };
    }

    public static SmsContentObserver createOnMainThread(@NonNull OnSmsChangedListener listener) {
        return new SmsContentObserver(new Handler(Looper.getMainLooper()), listener);
    }

    @Override
    public void onChange(boolean selfChange) {
        onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, @Nullable Uri uri) {
        super.onChange(selfChange, uri);
        Log.d(TAG, "Telephony.Sms onChange detected: " + uri);
        handler.removeCallbacks(debounceRunnable);
        handler.postDelayed(debounceRunnable, DEBOUNCE_DELAY_MS);
    }

    /**
     * Registers this observer with the system ContentResolver for Telephony.Sms.CONTENT_URI.
     */
    public synchronized void register(@NonNull Context context) {
        if (isRegistered) {
            return;
        }
        try {
            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    this
            );
            isRegistered = true;
            Log.d(TAG, "SmsContentObserver successfully registered for Telephony.Sms.");
        } catch (SecurityException se) {
            Log.w(TAG, "Cannot register SmsContentObserver: permission denied: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Failed to register SmsContentObserver: " + e.getMessage(), e);
        }
    }

    /**
     * Unregisters this observer from the system ContentResolver.
     */
    public synchronized void unregister(@NonNull Context context) {
        handler.removeCallbacks(debounceRunnable);
        if (!isRegistered) {
            return;
        }
        try {
            ContentResolver resolver = context.getContentResolver();
            resolver.unregisterContentObserver(this);
            isRegistered = false;
            Log.d(TAG, "SmsContentObserver unregistered.");
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering SmsContentObserver: " + e.getMessage(), e);
        }
    }

    public boolean isRegistered() {
        return isRegistered;
    }
}
