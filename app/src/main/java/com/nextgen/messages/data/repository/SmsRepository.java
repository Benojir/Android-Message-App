package com.nextgen.messages.data.repository;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.nextgen.messages.data.model.ContactInfo;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative repository for querying the Android Telephony Provider (Telephony.Sms).
 * Strictly READ-ONLY for Step 3: no deletion, no modifications to READ/SEEN flags, no sending.
 */
public class SmsRepository {

    private static final String TAG = "SmsRepository";

    private static final String[] CONVERSATION_PROJECTION = new String[]{
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
    };

    private static final String[] MESSAGE_PROJECTION = new String[]{
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SEEN,
            Telephony.Sms.STATUS,
            Telephony.Sms.ERROR_CODE,
            Telephony.Sms.SUBSCRIPTION_ID
    };

    private static volatile SmsRepository instance;

    private final Context appContext;
    private final ContactRepository contactRepository;
    private final AppExecutors executors;

    private SmsRepository(
            @NonNull Context context,
            @NonNull ContactRepository contactRepository,
            @NonNull AppExecutors executors
    ) {
        this.appContext = context.getApplicationContext();
        this.contactRepository = contactRepository;
        this.executors = executors;
    }

    public static SmsRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SmsRepository.class) {
                if (instance == null) {
                    instance = new SmsRepository(
                            context,
                            ContactRepository.getInstance(context),
                            AppExecutors.getInstance()
                    );
                }
            }
        }
        return instance;
    }

    public interface DataCallback<T> {
        void onSuccess(T result);
        void onError(Throwable throwable);
    }

    /**
     * Checks if READ_SMS permission is granted.
     */
    public boolean hasReadSmsPermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Asynchronously loads all conversation threads.
     */
    public void loadConversations(@NonNull DataCallback<List<Conversation>> callback) {
        executors.diskIO().execute(() -> {
            try {
                List<Conversation> conversations = getConversationsSync();
                executors.mainThread().execute(() -> callback.onSuccess(conversations));
            } catch (Throwable t) {
                Log.e(TAG, "Error loading conversations: " + t.getMessage(), t);
                executors.mainThread().execute(() -> callback.onError(t));
            }
        });
    }

    /**
     * Synchronously loads and groups conversations from Telephony.Sms.
     * Guaranteed to be thread-safe; should be called from background threads.
     */
    @NonNull
    public List<Conversation> getConversationsSync() {
        if (!hasReadSmsPermission()) {
            Log.w(TAG, "READ_SMS permission not granted. Returning empty conversations list.");
            return Collections.emptyList();
        }

        ContentResolver resolver = appContext.getContentResolver();
        Map<Long, ConversationBuilder> threadMap = new LinkedHashMap<>();

        try (Cursor cursor = resolver.query(
                Telephony.Sms.CONTENT_URI,
                CONVERSATION_PROJECTION,
                null,
                null,
                Telephony.Sms.DATE + " DESC"
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return Collections.emptyList();
            }

            int threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID);
            int addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
            int bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY);
            int dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE);
            int typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE);
            int readIdx = cursor.getColumnIndex(Telephony.Sms.READ);

            do {
                long threadId = threadIdIdx >= 0 ? cursor.getLong(threadIdIdx) : -1L;
                if (threadId <= 0) {
                    continue;
                }

                String address = addressIdx >= 0 ? cursor.getString(addressIdx) : null;
                String body = bodyIdx >= 0 ? cursor.getString(bodyIdx) : null;
                long date = dateIdx >= 0 ? cursor.getLong(dateIdx) : 0L;
                int type = typeIdx >= 0 ? cursor.getInt(typeIdx) : 0;
                boolean isRead = readIdx >= 0 && cursor.getInt(readIdx) == 1;

                ConversationBuilder builder = threadMap.get(threadId);
                if (builder == null) {
                    // First encounter: this is the latest message in this thread
                    builder = new ConversationBuilder(threadId, address, body, date);
                    threadMap.put(threadId, builder);
                } else if ((builder.address == null || builder.address.isEmpty()) && address != null) {
                    // Backfill address if latest row was missing it
                    builder.address = address;
                }

                builder.messageCount++;
                if (!isRead && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    builder.unreadCount++;
                }
            } while (cursor.moveToNext());

        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException while querying Telephony.Sms: " + se.getMessage(), se);
            return Collections.emptyList();
        } catch (Exception e) {
            Log.e(TAG, "Exception while querying Telephony.Sms: " + e.getMessage(), e);
            return Collections.emptyList();
        }

        // Resolve contacts and construct final Conversation objects
        List<Conversation> result = new ArrayList<>(threadMap.size());
        for (ConversationBuilder b : threadMap.values()) {
            ContactInfo contact = contactRepository.resolveContact(b.address);
            result.add(new Conversation(
                    b.threadId,
                    b.address,
                    contact.getDisplayName(),
                    contact.getPhotoUri(),
                    b.snippet,
                    b.latestDate,
                    b.unreadCount,
                    b.messageCount
            ));
        }

        return result;
    }

    /**
     * Asynchronously loads all messages for a specific conversation thread.
     */
    public void loadMessagesForThread(long threadId, @NonNull DataCallback<List<SmsMessage>> callback) {
        executors.diskIO().execute(() -> {
            try {
                List<SmsMessage> messages = getMessagesForThreadSync(threadId);
                executors.mainThread().execute(() -> callback.onSuccess(messages));
            } catch (Throwable t) {
                Log.e(TAG, "Error loading messages for thread " + threadId + ": " + t.getMessage(), t);
                executors.mainThread().execute(() -> callback.onError(t));
            }
        });
    }

    /**
     * Synchronously loads all messages for a specific conversation thread ordered chronologically (oldest to newest).
     */
    @NonNull
    public List<SmsMessage> getMessagesForThreadSync(long threadId) {
        if (!hasReadSmsPermission() || threadId <= 0) {
            return Collections.emptyList();
        }

        List<SmsMessage> messages = new ArrayList<>();
        ContentResolver resolver = appContext.getContentResolver();

        String selection = Telephony.Sms.THREAD_ID + " = ?";
        String[] selectionArgs = new String[]{String.valueOf(threadId)};

        try (Cursor cursor = resolver.query(
                Telephony.Sms.CONTENT_URI,
                MESSAGE_PROJECTION,
                selection,
                selectionArgs,
                Telephony.Sms.DATE + " ASC"
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return Collections.emptyList();
            }

            int idIdx = cursor.getColumnIndex(Telephony.Sms._ID);
            int threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID);
            int addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
            int bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY);
            int dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE);
            int dateSentIdx = cursor.getColumnIndex(Telephony.Sms.DATE_SENT);
            int typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE);
            int readIdx = cursor.getColumnIndex(Telephony.Sms.READ);
            int seenIdx = cursor.getColumnIndex(Telephony.Sms.SEEN);
            int statusIdx = cursor.getColumnIndex(Telephony.Sms.STATUS);
            int errorCodeIdx = cursor.getColumnIndex(Telephony.Sms.ERROR_CODE);
            int subIdIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID);

            do {
                long id = idIdx >= 0 ? cursor.getLong(idIdx) : -1L;
                long tId = threadIdIdx >= 0 ? cursor.getLong(threadIdIdx) : threadId;
                String address = addressIdx >= 0 ? cursor.getString(addressIdx) : null;
                String body = bodyIdx >= 0 ? cursor.getString(bodyIdx) : null;
                long date = dateIdx >= 0 ? cursor.getLong(dateIdx) : 0L;
                long dateSent = dateSentIdx >= 0 ? cursor.getLong(dateSentIdx) : 0L;
                int type = typeIdx >= 0 ? cursor.getInt(typeIdx) : 0;
                boolean isRead = readIdx >= 0 && cursor.getInt(readIdx) == 1;
                boolean isSeen = seenIdx >= 0 && cursor.getInt(seenIdx) == 1;
                int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : -1;
                int errorCode = errorCodeIdx >= 0 ? cursor.getInt(errorCodeIdx) : 0;
                int subId = subIdIdx >= 0 ? cursor.getInt(subIdIdx) : -1;

                messages.add(new SmsMessage(
                        id,
                        tId,
                        address,
                        body,
                        date,
                        dateSent,
                        type,
                        isRead,
                        isSeen,
                        status,
                        errorCode,
                        subId
                ));
            } while (cursor.moveToNext());

        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException while loading messages for thread " + threadId + ": " + se.getMessage(), se);
            return Collections.emptyList();
        } catch (Exception e) {
            Log.e(TAG, "Exception while loading messages for thread " + threadId + ": " + e.getMessage(), e);
            return Collections.emptyList();
        }

        return messages;
    }

    /**
     * Asynchronously searches conversations and message bodies matching the given query string.
     */
    public void searchConversations(@NonNull String rawQuery, @NonNull DataCallback<List<Conversation>> callback) {
        executors.diskIO().execute(() -> {
            try {
                List<Conversation> filtered = searchConversationsSync(rawQuery);
                executors.mainThread().execute(() -> callback.onSuccess(filtered));
            } catch (Throwable t) {
                Log.e(TAG, "Error searching conversations: " + t.getMessage(), t);
                executors.mainThread().execute(() -> callback.onError(t));
            }
        });
    }

    /**
     * Synchronously searches conversations matching the query.
     */
    @NonNull
    public List<Conversation> searchConversationsSync(@Nullable String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return getConversationsSync();
        }

        String query = rawQuery.trim().toLowerCase(Locale.ROOT);
        List<Conversation> all = getConversationsSync();
        List<Conversation> matched = new ArrayList<>();
        Set<Long> matchedThreadIds = new HashSet<>();

        // First pass: match on conversation title (contact name), phone number/address, or latest snippet
        for (Conversation c : all) {
            boolean titleMatch = c.getTitle().toLowerCase(Locale.ROOT).contains(query);
            boolean addressMatch = c.getAddress() != null && c.getAddress().toLowerCase(Locale.ROOT).contains(query);
            boolean snippetMatch = c.getDisplaySnippet().toLowerCase(Locale.ROOT).contains(query);

            if (titleMatch || addressMatch || snippetMatch) {
                matched.add(c);
                matchedThreadIds.add(c.getThreadId());
            }
        }

        // Second pass: match deep inside message bodies across older messages in Telephony.Sms
        if (hasReadSmsPermission()) {
            ContentResolver resolver = appContext.getContentResolver();
            String selection = Telephony.Sms.BODY + " LIKE ?";
            String[] selectionArgs = new String[]{"%" + rawQuery.trim() + "%"};
            String[] threadProjection = new String[]{Telephony.Sms.THREAD_ID};

            try (Cursor cursor = resolver.query(
                    Telephony.Sms.CONTENT_URI,
                    threadProjection,
                    selection,
                    selectionArgs,
                    null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int threadIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID);
                    if (threadIdx >= 0) {
                        do {
                            long threadId = cursor.getLong(threadIdx);
                            if (!matchedThreadIds.contains(threadId)) {
                                for (Conversation c : all) {
                                    if (c.getThreadId() == threadId) {
                                        matched.add(c);
                                        matchedThreadIds.add(threadId);
                                        break;
                                    }
                                }
                            }
                        } while (cursor.moveToNext());
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error in deep message search: " + e.getMessage());
            }
        }

        return matched;
    }

    /**
     * Temporary builder structure used during conversation grouping.
     */
    private static class ConversationBuilder {
        final long threadId;
        String address;
        final String snippet;
        final long latestDate;
        int unreadCount = 0;
        int messageCount = 0;

        ConversationBuilder(long threadId, @Nullable String address, @Nullable String snippet, long latestDate) {
            this.threadId = threadId;
            this.address = address;
            this.snippet = snippet;
            this.latestDate = latestDate;
        }
    }
}
