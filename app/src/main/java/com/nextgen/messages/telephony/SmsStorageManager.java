package com.nextgen.messages.telephony;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.local.AppPreferences;

/**
 * Authoritative persistence manager for Android Telephony Provider (Telephony.Sms).
 * Responsible for creating conversation threads, persisting outgoing and incoming SMS records,
 * maintaining message delivery status states, and managing message drafts.
 */
public class SmsStorageManager {

    private static final String TAG = "SmsStorageManager";

    private static volatile SmsStorageManager instance;

    private final Context appContext;
    private final AppPreferences appPreferences;

    private SmsStorageManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.appPreferences = AppPreferences.getInstance(this.appContext);
    }

    public static SmsStorageManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SmsStorageManager.class) {
                if (instance == null) {
                    instance = new SmsStorageManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Resolves or creates a thread ID for a recipient phone number in the Telephony provider.
     */
    public long getOrCreateThreadId(@NonNull String recipient) {
        try {
            return Telephony.Threads.getOrCreateThreadId(appContext, recipient);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get or create thread ID for: " + recipient, e);
            return -1L;
        }
    }

    /**
     * Inserts a pending outgoing SMS into Telephony.Sms with MESSAGE_TYPE_OUTBOX status.
     *
     * @return The Uri of the newly created message record, or null on failure.
     */
    @Nullable
    public Uri insertOutgoingMessage(
            long threadId,
            @NonNull String recipient,
            @NonNull String body,
            int subscriptionId
    ) {
        if (threadId <= 0) {
            threadId = getOrCreateThreadId(recipient);
        }

        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.THREAD_ID, threadId);
        values.put(Telephony.Sms.ADDRESS, recipient);
        values.put(Telephony.Sms.BODY, body);
        values.put(Telephony.Sms.DATE, System.currentTimeMillis());
        values.put(Telephony.Sms.DATE_SENT, System.currentTimeMillis());
        values.put(Telephony.Sms.READ, 1);
        values.put(Telephony.Sms.SEEN, 1);
        values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX);
        values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE);
        values.put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId);

        try {
            ContentResolver resolver = appContext.getContentResolver();
            Uri uri = resolver.insert(Telephony.Sms.CONTENT_URI, values);
            Log.d(TAG, "Inserted outgoing message into Outbox: " + uri);
            return uri;
        } catch (SecurityException se) {
            Log.e(TAG, "Permission denied writing to Telephony.Sms: " + se.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error inserting outgoing message into Telephony.Sms", e);
            return null;
        }
    }

    /**
     * Updates an outgoing message's transmission state (e.g., SENT, FAILED).
     */
    public boolean updateMessageStatus(
            @NonNull Uri messageUri,
            int messageType,
            int status,
            int errorCode
    ) {
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.TYPE, messageType);
        values.put(Telephony.Sms.STATUS, status);
        values.put(Telephony.Sms.ERROR_CODE, errorCode);

        try {
            ContentResolver resolver = appContext.getContentResolver();
            int rows = resolver.update(messageUri, values, null, null);
            Log.d(TAG, "Updated message status for " + messageUri + " (type=" + messageType + ", status=" + status + ", rows=" + rows + ")");
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to update message status for " + messageUri, e);
            return false;
        }
    }

    /**
     * Updates delivery status to delivered (Telephony.Sms.STATUS_COMPLETE).
     */
    public boolean markMessageDelivered(@NonNull Uri messageUri) {
        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE);

        try {
            ContentResolver resolver = appContext.getContentResolver();
            int rows = resolver.update(messageUri, values, null, null);
            Log.d(TAG, "Marked message delivered for " + messageUri + " (rows=" + rows + ")");
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to mark message delivered: " + messageUri, e);
            return false;
        }
    }

    /**
     * Inserts an incoming SMS received from SMS_DELIVER into Telephony.Sms.Inbox.
     *
     * @return The Uri of the inserted message, or null on failure.
     */
    @Nullable
    public Uri insertIncomingMessage(
            @NonNull String sender,
            @NonNull String body,
            long timestampMillis,
            int subscriptionId
    ) {
        long threadId = getOrCreateThreadId(sender);

        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.THREAD_ID, threadId);
        values.put(Telephony.Sms.ADDRESS, sender);
        values.put(Telephony.Sms.BODY, body);
        values.put(Telephony.Sms.DATE, timestampMillis > 0 ? timestampMillis : System.currentTimeMillis());
        values.put(Telephony.Sms.DATE_SENT, timestampMillis > 0 ? timestampMillis : System.currentTimeMillis());
        values.put(Telephony.Sms.READ, 0);
        values.put(Telephony.Sms.SEEN, 0);
        values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);
        values.put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE);
        values.put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId);

        try {
            ContentResolver resolver = appContext.getContentResolver();
            Uri uri = resolver.insert(Telephony.Sms.CONTENT_URI, values);
            Log.i(TAG, "Successfully persisted incoming SMS from " + sender + " (threadId=" + threadId + ", uri=" + uri + ")");
            return uri;
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException persisting incoming message. Is app default SMS? " + se.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist incoming message from " + sender, e);
            return null;
        }
    }

    /**
     * Retrieves saved draft for a conversation thread.
     */
    @NonNull
    public String getDraft(long threadId) {
        return appPreferences.getDraftForThread(threadId);
    }

    /**
     * Saves draft for a conversation thread.
     */
    public void saveDraft(long threadId, @Nullable String draft) {
        appPreferences.setDraftForThread(threadId, draft);
    }

    /**
     * Clears draft for a conversation thread.
     */
    public void clearDraft(long threadId) {
        appPreferences.setDraftForThread(threadId, null);
    }
}
