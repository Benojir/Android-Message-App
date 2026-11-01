package com.nextgen.messages.data.repository;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.nextgen.messages.data.model.ContactInfo;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.mms.storage.MmsStorageManager;
import com.nextgen.messages.telephony.SmsRoleManager;
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

        // Merge MMS Conversations (Step 6)
        try (Cursor mmsCursor = resolver.query(
                Telephony.Mms.CONTENT_URI,
                new String[]{
                        Telephony.Mms._ID,
                        Telephony.Mms.THREAD_ID,
                        Telephony.Mms.DATE,
                        Telephony.Mms.SUBJECT,
                        Telephony.Mms.MESSAGE_BOX,
                        Telephony.Mms.READ
                },
                null,
                null,
                Telephony.Mms.DATE + " DESC"
        )) {
            if (mmsCursor != null && mmsCursor.moveToFirst()) {
                int threadIdIdx = mmsCursor.getColumnIndex(Telephony.Mms.THREAD_ID);
                int dateIdx = mmsCursor.getColumnIndex(Telephony.Mms.DATE);
                int subjIdx = mmsCursor.getColumnIndex(Telephony.Mms.SUBJECT);
                int boxIdx = mmsCursor.getColumnIndex(Telephony.Mms.MESSAGE_BOX);
                int readIdx = mmsCursor.getColumnIndex(Telephony.Mms.READ);

                do {
                    long threadId = threadIdIdx >= 0 ? mmsCursor.getLong(threadIdIdx) : -1L;
                    if (threadId <= 0) continue;

                    long dateSec = dateIdx >= 0 ? mmsCursor.getLong(dateIdx) : 0L;
                    long dateMs = dateSec * 1000L;
                    String subject = subjIdx >= 0 ? mmsCursor.getString(subjIdx) : null;
                    int box = boxIdx >= 0 ? mmsCursor.getInt(boxIdx) : Telephony.Mms.MESSAGE_BOX_INBOX;
                    boolean isRead = readIdx >= 0 && mmsCursor.getInt(readIdx) == 1;

                    String snippet = (subject != null && !subject.trim().isEmpty())
                            ? subject.trim()
                            : "📷 Multimedia message";

                    ConversationBuilder builder = threadMap.get(threadId);
                    if (builder == null) {
                        builder = new ConversationBuilder(threadId, null, snippet, dateMs);
                        threadMap.put(threadId, builder);
                    } else if (dateMs > builder.latestDate) {
                        builder.latestDate = dateMs;
                        builder.snippet = snippet;
                    }

                    builder.messageCount++;
                    if (!isRead && box == Telephony.Mms.MESSAGE_BOX_INBOX) {
                        builder.unreadCount++;
                    }
                } while (mmsCursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while querying Telephony.Mms for conversations: " + e.getMessage(), e);
        }

        // Resolve contacts and construct final Conversation objects
        List<Conversation> result = new ArrayList<>(threadMap.size());
        for (ConversationBuilder b : threadMap.values()) {
            String address = b.address;
            if (address == null || address.isEmpty()) {
                address = resolveThreadAddress(b.threadId);
            }
            ContactInfo contact = contactRepository.resolveContact(address);
            result.add(new Conversation(
                    b.threadId,
                    address,
                    contact.getDisplayName(),
                    contact.getPhotoUri(),
                    b.snippet,
                    b.latestDate,
                    b.unreadCount,
                    b.messageCount
            ));
        }

        Collections.sort(result, (c1, c2) -> Long.compare(c2.getDate(), c1.getDate()));
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

        // Merge MMS messages for this thread (Step 6)
        try {
            List<SmsMessage> mmsMessages = MmsStorageManager.getInstance(appContext).getMmsMessagesForThread(threadId);
            if (!mmsMessages.isEmpty()) {
                messages.addAll(mmsMessages);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error merging MMS messages for thread " + threadId + ": " + e.getMessage(), e);
        }

        Collections.sort(messages, (m1, m2) -> Long.compare(m1.getDate(), m2.getDate()));
        return messages;
    }

    @Nullable
    private String resolveThreadAddress(long threadId) {
        try (Cursor cursor = appContext.getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[]{Telephony.Sms.ADDRESS},
                Telephony.Sms.THREAD_ID + " = ? AND " + Telephony.Sms.ADDRESS + " IS NOT NULL",
                new String[]{String.valueOf(threadId)},
                Telephony.Sms.DATE + " DESC LIMIT 1"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
                if (idx >= 0) {
                    String addr = cursor.getString(idx);
                    if (addr != null && !addr.isEmpty()) return addr;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            List<SmsMessage> mmsList = MmsStorageManager.getInstance(appContext).getMmsMessagesForThread(threadId);
            if (!mmsList.isEmpty()) {
                SmsMessage last = mmsList.get(mmsList.size() - 1);
                if (last.getAddress() != null && !last.getAddress().isEmpty()) {
                    return last.getAddress();
                }
                if (!last.getRecipients().isEmpty()) {
                    return TextUtils.join(", ", last.getRecipients());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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
     * Marks all unread incoming messages in a thread as read (READ=1, SEEN=1).
     */
    public void markThreadMessagesAsRead(long threadId, @Nullable DataCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                if (threadId <= 0 || !SmsRoleManager.isDefaultSmsApp(appContext)) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(0));
                    }
                    return;
                }

                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.READ, 1);
                values.put(Telephony.Sms.SEEN, 1);

                String selection = Telephony.Sms.THREAD_ID + " = ? AND " + Telephony.Sms.READ + " = 0";
                String[] selectionArgs = new String[]{String.valueOf(threadId)};

                int updated = appContext.getContentResolver().update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        selection,
                        selectionArgs
                );
                Log.d(TAG, "Marked " + updated + " messages read in thread " + threadId);
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onSuccess(updated));
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to mark thread read: " + t.getMessage(), t);
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onError(t));
                }
            }
        });
    }

    /**
     * Marks the conversation thread as unread by updating its latest message to READ=0, SEEN=0.
     */
    public void markThreadMessagesAsUnread(long threadId, @Nullable DataCallback<Boolean> callback) {
        executors.diskIO().execute(() -> {
            try {
                if (threadId <= 0 || !SmsRoleManager.isDefaultSmsApp(appContext)) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(false));
                    }
                    return;
                }

                ContentResolver resolver = appContext.getContentResolver();
                long targetMessageId = -1L;

                // First find the latest incoming message
                String selection = Telephony.Sms.THREAD_ID + " = ? AND " + Telephony.Sms.TYPE + " = ?";
                String[] selectionArgs = new String[]{String.valueOf(threadId), String.valueOf(Telephony.Sms.MESSAGE_TYPE_INBOX)};
                try (Cursor cursor = resolver.query(
                        Telephony.Sms.CONTENT_URI,
                        new String[]{Telephony.Sms._ID},
                        selection,
                        selectionArgs,
                        Telephony.Sms.DATE + " DESC LIMIT 1"
                )) {
                    if (cursor != null && cursor.moveToFirst()) {
                        targetMessageId = cursor.getLong(0);
                    }
                }

                // Fallback to any latest message in the thread if no incoming message exists
                if (targetMessageId <= 0) {
                    try (Cursor cursor = resolver.query(
                            Telephony.Sms.CONTENT_URI,
                            new String[]{Telephony.Sms._ID},
                            Telephony.Sms.THREAD_ID + " = ?",
                            new String[]{String.valueOf(threadId)},
                            Telephony.Sms.DATE + " DESC LIMIT 1"
                    )) {
                        if (cursor != null && cursor.moveToFirst()) {
                            targetMessageId = cursor.getLong(0);
                        }
                    }
                }

                if (targetMessageId <= 0) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(false));
                    }
                    return;
                }

                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.READ, 0);
                values.put(Telephony.Sms.SEEN, 0);

                Uri uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, targetMessageId);
                int rows = resolver.update(uri, values, null, null);
                Log.d(TAG, "Marked message " + targetMessageId + " as unread (rows=" + rows + ")");

                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onSuccess(rows > 0));
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to mark thread unread: " + t.getMessage(), t);
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onError(t));
                }
            }
        });
    }

    /**
     * Bulk marks multiple conversation threads as read.
     */
    public void markMultipleThreadsAsRead(@NonNull Set<Long> threadIds, @Nullable DataCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                if (threadIds.isEmpty() || !SmsRoleManager.isDefaultSmsApp(appContext)) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(0));
                    }
                    return;
                }

                List<String> placeholders = new ArrayList<>(threadIds.size());
                List<String> args = new ArrayList<>(threadIds.size());
                for (Long id : threadIds) {
                    if (id != null && id > 0) {
                        placeholders.add("?");
                        args.add(String.valueOf(id));
                    }
                }

                if (args.isEmpty()) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(0));
                    }
                    return;
                }

                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.READ, 1);
                values.put(Telephony.Sms.SEEN, 1);

                String selection = Telephony.Sms.THREAD_ID + " IN (" + TextUtils.join(",", placeholders) + ") AND " + Telephony.Sms.READ + " = 0";
                int updated = appContext.getContentResolver().update(
                        Telephony.Sms.CONTENT_URI,
                        values,
                        selection,
                        args.toArray(new String[0])
                );
                Log.d(TAG, "Bulk marked " + updated + " messages read across " + threadIds.size() + " threads");
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onSuccess(updated));
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to bulk mark threads read: " + t.getMessage(), t);
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onError(t));
                }
            }
        });
    }

    /**
     * Safely deletes a single message by its ID, checking both SMS and MMS tables.
     */
    public void deleteSingleMessage(long messageId, @Nullable DataCallback<Boolean> callback) {
        deleteSingleMessage(messageId, false, callback);
    }

    /**
     * Safely deletes a single message by its ID and type.
     */
    public void deleteSingleMessage(long messageId, boolean isMms, @Nullable DataCallback<Boolean> callback) {
        executors.diskIO().execute(() -> {
            try {
                if (messageId <= 0) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(false));
                    }
                    return;
                }
                if (!SmsRoleManager.isDefaultSmsApp(appContext)) {
                    throw new SecurityException("NextGen Messages must be default SMS app to delete messages.");
                }

                boolean success;
                if (isMms) {
                    success = MmsStorageManager.getInstance(appContext).deleteMms(messageId);
                } else {
                    Uri uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId);
                    int rows = appContext.getContentResolver().delete(uri, null, null);
                    if (rows <= 0) {
                        // Fallback check if it was an MMS ID
                        success = MmsStorageManager.getInstance(appContext).deleteMms(messageId);
                    } else {
                        success = true;
                    }
                }

                Log.d(TAG, "Deleted message " + messageId + " (isMms=" + isMms + ", success=" + success + ")");
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onSuccess(success));
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error deleting message " + messageId + ": " + t.getMessage(), t);
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onError(t));
                }
            }
        });
    }

    /**
     * Safely deletes multiple SMS and MMS messages using parameterized selection.
     */
    public void deleteMultipleMessages(@NonNull Set<Long> messageIds, @Nullable DataCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                if (messageIds.isEmpty()) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(0));
                    }
                    return;
                }
                if (!SmsRoleManager.isDefaultSmsApp(appContext)) {
                    throw new SecurityException("NextGen Messages must be default SMS app to delete messages.");
                }

                List<String> placeholders = new ArrayList<>(messageIds.size());
                List<String> args = new ArrayList<>(messageIds.size());
                for (Long id : messageIds) {
                    if (id != null && id > 0) {
                        placeholders.add("?");
                        args.add(String.valueOf(id));
                        // Also attempt deletion on MMS
                        MmsStorageManager.getInstance(appContext).deleteMms(id);
                    }
                }

                if (args.isEmpty()) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(0));
                    }
                    return;
                }

                String selection = Telephony.Sms._ID + " IN (" + TextUtils.join(",", placeholders) + ")";
                int rows = appContext.getContentResolver().delete(
                        Telephony.Sms.CONTENT_URI,
                        selection,
                        args.toArray(new String[0])
                );
                Log.d(TAG, "Bulk deleted " + rows + " messages");
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onSuccess(rows));
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error bulk deleting messages: " + t.getMessage(), t);
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onError(t));
                }
            }
        });
    }

    /**
     * Safely deletes all SMS and MMS records for an entire conversation thread.
     */
    public void deleteConversationSms(long threadId, @Nullable DataCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                if (threadId <= 0) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(0));
                    }
                    return;
                }
                if (!SmsRoleManager.isDefaultSmsApp(appContext)) {
                    throw new SecurityException("NextGen Messages must be default SMS app to delete conversations.");
                }

                String selection = Telephony.Sms.THREAD_ID + " = ?";
                String[] selectionArgs = new String[]{String.valueOf(threadId)};

                int smsRows = appContext.getContentResolver().delete(
                        Telephony.Sms.CONTENT_URI,
                        selection,
                        selectionArgs
                );
                int mmsRows = MmsStorageManager.getInstance(appContext).deleteMmsForThread(threadId);
                int total = smsRows + mmsRows;

                Log.d(TAG, "Deleted " + total + " records (" + smsRows + " SMS, " + mmsRows + " MMS) for thread " + threadId);
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onSuccess(total));
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error deleting conversation for thread " + threadId + ": " + t.getMessage(), t);
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onError(t));
                }
            }
        });
    }

    /**
     * Safely deletes all SMS and MMS records for multiple conversation threads.
     */
    public void deleteMultipleConversationsSms(@NonNull Set<Long> threadIds, @Nullable DataCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            try {
                if (threadIds.isEmpty()) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(0));
                    }
                    return;
                }
                if (!SmsRoleManager.isDefaultSmsApp(appContext)) {
                    throw new SecurityException("NextGen Messages must be default SMS app to delete conversations.");
                }

                List<String> placeholders = new ArrayList<>(threadIds.size());
                List<String> args = new ArrayList<>(threadIds.size());
                for (Long id : threadIds) {
                    if (id != null && id > 0) {
                        placeholders.add("?");
                        args.add(String.valueOf(id));
                    }
                }

                if (args.isEmpty()) {
                    if (callback != null) {
                        executors.mainThread().execute(() -> callback.onSuccess(0));
                    }
                    return;
                }

                String selection = Telephony.Sms.THREAD_ID + " IN (" + TextUtils.join(",", placeholders) + ")";
                int smsRows = appContext.getContentResolver().delete(
                        Telephony.Sms.CONTENT_URI,
                        selection,
                        args.toArray(new String[0])
                );
                int mmsRows = MmsStorageManager.getInstance(appContext).deleteMmsForMultipleThreads(threadIds);
                int total = smsRows + mmsRows;

                Log.d(TAG, "Deleted " + total + " records (" + smsRows + " SMS, " + mmsRows + " MMS) across " + threadIds.size() + " conversations");
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onSuccess(total));
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error bulk deleting conversations: " + t.getMessage(), t);
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onError(t));
                }
            }
        });
    }

    /**
     * Temporary builder structure used during conversation grouping.
     */
    private static class ConversationBuilder {
        final long threadId;
        String address;
        String snippet;
        long latestDate;
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
