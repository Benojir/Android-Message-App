package com.nextgen.messages.mms.storage;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.model.MmsAttachment;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.mms.pdu.CharacterSets;
import com.nextgen.messages.mms.pdu.NotificationInd;
import com.nextgen.messages.mms.pdu.PduBody;
import com.nextgen.messages.mms.pdu.PduHeaders;
import com.nextgen.messages.mms.pdu.PduPart;
import com.nextgen.messages.mms.pdu.RetrieveConf;
import com.nextgen.messages.mms.pdu.SendReq;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages persistence and querying of MMS messages, parts, and participant addresses
 * within Android's Telephony Provider (Telephony.Mms, Telephony.Mms.Part, Telephony.Mms.Addr).
 */
public class MmsStorageManager {

    private static final String TAG = "MmsStorageManager";

    public static final int PDU_HEADER_FROM = 137;
    public static final int PDU_HEADER_TO = 151;
    public static final int PDU_HEADER_CC = 130;
    public static final int PDU_HEADER_BCC = 129;

    private static volatile MmsStorageManager instance;
    private final Context appContext;

    private MmsStorageManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static MmsStorageManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (MmsStorageManager.class) {
                if (instance == null) {
                    instance = new MmsStorageManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Persists an outgoing SendReq MMS into Telephony.Mms.Outbox along with its parts and recipients.
     * Returns the created MMS database ID, or -1 on failure.
     */
    public long insertOutgoingMms(
            long threadId,
            @NonNull SendReq sendReq,
            int subscriptionId,
            long messageSize
    ) {
        ContentResolver resolver = appContext.getContentResolver();
        try {
            ContentValues values = new ContentValues();
            values.put(Telephony.Mms.THREAD_ID, threadId);
            values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
            values.put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX);
            values.put(Telephony.Mms.READ, 1);
            values.put(Telephony.Mms.SEEN, 1);
            values.put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId);
            values.put(Telephony.Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ);
            values.put(Telephony.Mms.MMS_VERSION, PduHeaders.CURRENT_MMS_VERSION);
            values.put(Telephony.Mms.PRIORITY, PduHeaders.PRIORITY_NORMAL);
            values.put(Telephony.Mms.MESSAGE_SIZE, messageSize);
            values.put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related");

            byte[] trId = sendReq.getTransactionId();
            if (trId != null) {
                values.put(Telephony.Mms.TRANSACTION_ID, new String(trId, StandardCharsets.US_ASCII));
            }

            if (sendReq.getSubject() != null) {
                values.put(Telephony.Mms.SUBJECT, sendReq.getSubject().getString());
                values.put(Telephony.Mms.SUBJECT_CHARSET, sendReq.getSubject().getCharset());
            }

            Uri mmsUri = resolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values);
            if (mmsUri == null) {
                Log.e(TAG, "Failed to insert MMS into Outbox");
                return -1L;
            }

            long mmsId = ContentUris.parseId(mmsUri);

            // Insert Parts
            PduBody body = sendReq.getBody();
            if (body != null) {
                for (int i = 0; i < body.getPartsNum(); i++) {
                    insertPart(mmsId, body.getPart(i));
                }
            }

            // Insert Recipients (TO)
            com.nextgen.messages.mms.pdu.EncodedStringValue[] toList = sendReq.getTo();
            if (toList != null) {
                for (com.nextgen.messages.mms.pdu.EncodedStringValue to : toList) {
                    insertAddress(mmsId, to.getString(), PDU_HEADER_TO);
                }
            }

            // Insert Sender (FROM)
            if (sendReq.getFrom() != null && sendReq.getFrom().getTextString().length > 0) {
                insertAddress(mmsId, sendReq.getFrom().getString(), PDU_HEADER_FROM);
            } else {
                insertAddress(mmsId, "insert-address-token", PDU_HEADER_FROM);
            }

            Log.i(TAG, "Successfully inserted outgoing MMS " + mmsId + " for thread " + threadId);
            return mmsId;
        } catch (Exception e) {
            Log.e(TAG, "Error inserting outgoing MMS: " + e.getMessage(), e);
            return -1L;
        }
    }

    /**
     * Persists an incoming NotificationInd PDU into Telephony.Mms.Inbox.
     */
    public long insertIncomingNotification(
            long threadId,
            @NonNull NotificationInd notification,
            int subscriptionId,
            @Nullable String sender
    ) {
        ContentResolver resolver = appContext.getContentResolver();
        try {
            ContentValues values = new ContentValues();
            values.put(Telephony.Mms.THREAD_ID, threadId);
            values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
            values.put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX);
            values.put(Telephony.Mms.READ, 0);
            values.put(Telephony.Mms.SEEN, 0);
            values.put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId);
            values.put(Telephony.Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND);
            values.put(Telephony.Mms.MMS_VERSION, notification.getMmsVersion());
            values.put(Telephony.Mms.MESSAGE_SIZE, notification.getMessageSize());
            values.put(Telephony.Mms.EXPIRY, notification.getExpiry());
            values.put(Telephony.Mms.STATUS, 128); // Download pending

            byte[] trId = notification.getTransactionId();
            if (trId != null) {
                values.put(Telephony.Mms.TRANSACTION_ID, new String(trId, StandardCharsets.US_ASCII));
            }

            byte[] ctLocation = notification.getContentLocation();
            if (ctLocation != null) {
                values.put(Telephony.Mms.CONTENT_LOCATION, new String(ctLocation, StandardCharsets.US_ASCII));
            }

            if (notification.getSubject() != null) {
                values.put(Telephony.Mms.SUBJECT, notification.getSubject().getString());
            }

            Uri uri = resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values);
            if (uri == null) {
                Log.e(TAG, "Failed to insert MMS NotificationInd into Inbox");
                return -1L;
            }

            long mmsId = ContentUris.parseId(uri);
            if (sender != null && !sender.isEmpty()) {
                insertAddress(mmsId, sender, PDU_HEADER_FROM);
            }

            Log.i(TAG, "Inserted incoming MMS notification " + mmsId + " for thread " + threadId);
            return mmsId;
        } catch (Exception e) {
            Log.e(TAG, "Error inserting MMS notification: " + e.getMessage(), e);
            return -1L;
        }
    }

    /**
     * Updates an MMS record after successful download with RetrieveConf data and parts.
     */
    public boolean updateDownloadedMms(
            long mmsId,
            long threadId,
            @NonNull RetrieveConf retrieveConf,
            @Nullable String sender
    ) {
        ContentResolver resolver = appContext.getContentResolver();
        try {
            ContentValues values = new ContentValues();
            values.put(Telephony.Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);
            values.put(Telephony.Mms.STATUS, 132); // Downloaded
            values.put(Telephony.Mms.READ, 0);

            long date = retrieveConf.getDate();
            if (date > 0) {
                values.put(Telephony.Mms.DATE, date);
            }

            if (retrieveConf.getSubject() != null) {
                values.put(Telephony.Mms.SUBJECT, retrieveConf.getSubject().getString());
            }

            Uri mmsUri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsId);
            resolver.update(mmsUri, values, null, null);

            // Delete previous stub parts if any
            Uri partUri = Uri.parse("content://mms/" + mmsId + "/part");
            resolver.delete(partUri, null, null);

            // Insert new downloaded parts
            PduBody body = retrieveConf.getBody();
            if (body != null) {
                for (int i = 0; i < body.getPartsNum(); i++) {
                    insertPart(mmsId, body.getPart(i));
                }
            }

            // Update addresses if provided
            if (sender != null && !sender.isEmpty()) {
                insertAddress(mmsId, sender, PDU_HEADER_FROM);
            }
            com.nextgen.messages.mms.pdu.EncodedStringValue[] toList = retrieveConf.getTo();
            if (toList != null) {
                for (com.nextgen.messages.mms.pdu.EncodedStringValue to : toList) {
                    insertAddress(mmsId, to.getString(), PDU_HEADER_TO);
                }
            }

            Log.i(TAG, "Successfully updated downloaded MMS " + mmsId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error updating downloaded MMS " + mmsId + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Marks an MMS as sent and moves it to the Sent mailbox.
     */
    public void markMmsSent(long mmsId) {
        try {
            ContentValues values = new ContentValues();
            values.put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT);
            values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
            Uri uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsId);
            appContext.getContentResolver().update(uri, values, null, null);
            Log.d(TAG, "Marked MMS " + mmsId + " as SENT");
        } catch (Exception e) {
            Log.e(TAG, "Error marking MMS sent: " + e.getMessage(), e);
        }
    }

    /**
     * Marks an MMS as failed.
     */
    public void markMmsFailed(long mmsId) {
        try {
            ContentValues values = new ContentValues();
            values.put(Telephony.Mms.STATUS, Telephony.Sms.STATUS_FAILED);
            Uri uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsId);
            appContext.getContentResolver().update(uri, values, null, null);
            Log.d(TAG, "Marked MMS " + mmsId + " as FAILED");
        } catch (Exception e) {
            Log.e(TAG, "Error marking MMS failed: " + e.getMessage(), e);
        }
    }

    /**
     * Updates download status of an MMS message (e.g. DOWNLOADING, FAILED).
     */
    public void updateDownloadStatus(long mmsId, int status) {
        try {
            ContentValues values = new ContentValues();
            values.put(Telephony.Mms.STATUS, status);
            Uri uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsId);
            appContext.getContentResolver().update(uri, values, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error updating download status: " + e.getMessage(), e);
        }
    }

    private void insertPart(long mmsId, @NonNull PduPart part) {
        ContentResolver resolver = appContext.getContentResolver();
        try {
            Uri partUri = Uri.parse("content://mms/" + mmsId + "/part");
            ContentValues values = new ContentValues();

            byte[] ct = part.getContentType();
            if (ct != null) {
                values.put(Telephony.Mms.Part.CONTENT_TYPE, new String(ct, StandardCharsets.US_ASCII));
            }
            if (part.getName() != null) {
                values.put(Telephony.Mms.Part.NAME, new String(part.getName()));
            }
            if (part.getFilename() != null) {
                values.put(Telephony.Mms.Part.FILENAME, new String(part.getFilename()));
            }
            if (part.getContentId() != null) {
                values.put(Telephony.Mms.Part.CONTENT_ID, new String(part.getContentId()));
            }
            if (part.getContentLocation() != null) {
                values.put(Telephony.Mms.Part.CONTENT_LOCATION, new String(part.getContentLocation()));
            }
            if (part.getCharset() != 0) {
                values.put(Telephony.Mms.Part.CHARSET, part.getCharset());
            }

            // If text/plain, store in TEXT column
            byte[] data = part.getData();
            String ctStr = ct != null ? new String(ct, StandardCharsets.US_ASCII) : "";
            if (ctStr.startsWith("text/plain") && data != null) {
                values.put(Telephony.Mms.Part.TEXT, new String(data, StandardCharsets.UTF_8));
            }

            Uri insertedPartUri = resolver.insert(partUri, values);
            if (insertedPartUri != null && data != null && !ctStr.startsWith("text/plain")) {
                try (OutputStream os = resolver.openOutputStream(insertedPartUri)) {
                    if (os != null) {
                        os.write(data);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error inserting MMS part: " + e.getMessage(), e);
        }
    }

    private void insertAddress(long mmsId, @NonNull String address, int type) {
        ContentResolver resolver = appContext.getContentResolver();
        try {
            Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
            ContentValues values = new ContentValues();
            values.put(Telephony.Mms.Addr.ADDRESS, address);
            values.put(Telephony.Mms.Addr.TYPE, type);
            values.put(Telephony.Mms.Addr.CHARSET, CharacterSets.UTF_8);
            resolver.insert(addrUri, values);
        } catch (Exception e) {
            Log.e(TAG, "Error inserting MMS address: " + e.getMessage(), e);
        }
    }

    /**
     * Loads all MMS messages for a given thread ID and maps them into SmsMessage objects with isMms = true.
     */
    @NonNull
    public List<SmsMessage> getMmsMessagesForThread(long threadId) {
        if (threadId <= 0) {
            return Collections.emptyList();
        }

        List<SmsMessage> result = new ArrayList<>();
        ContentResolver resolver = appContext.getContentResolver();

        String[] projection = new String[]{
                Telephony.Mms._ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.DATE_SENT,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.READ,
                Telephony.Mms.SEEN,
                Telephony.Mms.SUBSCRIPTION_ID,
                Telephony.Mms.SUBJECT,
                Telephony.Mms.MESSAGE_TYPE,
                Telephony.Mms.MESSAGE_SIZE,
                Telephony.Mms.STATUS,
                Telephony.Mms.CONTENT_LOCATION,
                Telephony.Mms.TRANSACTION_ID
        };

        String selection = Telephony.Mms.THREAD_ID + " = ?";
        String[] selectionArgs = new String[]{String.valueOf(threadId)};

        try (Cursor cursor = resolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                Telephony.Mms.DATE + " ASC"
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return Collections.emptyList();
            }

            int idIdx = cursor.getColumnIndex(Telephony.Mms._ID);
            int dateIdx = cursor.getColumnIndex(Telephony.Mms.DATE);
            int dateSentIdx = cursor.getColumnIndex(Telephony.Mms.DATE_SENT);
            int boxIdx = cursor.getColumnIndex(Telephony.Mms.MESSAGE_BOX);
            int readIdx = cursor.getColumnIndex(Telephony.Mms.READ);
            int seenIdx = cursor.getColumnIndex(Telephony.Mms.SEEN);
            int subIdIdx = cursor.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID);
            int subjIdx = cursor.getColumnIndex(Telephony.Mms.SUBJECT);
            int typeIdx = cursor.getColumnIndex(Telephony.Mms.MESSAGE_TYPE);
            int sizeIdx = cursor.getColumnIndex(Telephony.Mms.MESSAGE_SIZE);
            int statusIdx = cursor.getColumnIndex(Telephony.Mms.STATUS);
            int ctLocIdx = cursor.getColumnIndex(Telephony.Mms.CONTENT_LOCATION);

            do {
                long mmsId = idIdx >= 0 ? cursor.getLong(idIdx) : -1L;
                if (mmsId <= 0) continue;

                long dateSec = dateIdx >= 0 ? cursor.getLong(dateIdx) : 0L;
                long dateMs = dateSec * 1000L;
                long dateSentSec = dateSentIdx >= 0 ? cursor.getLong(dateSentIdx) : 0L;
                long dateSentMs = dateSentSec * 1000L;

                int box = boxIdx >= 0 ? cursor.getInt(boxIdx) : Telephony.Mms.MESSAGE_BOX_INBOX;
                boolean isRead = readIdx >= 0 && cursor.getInt(readIdx) == 1;
                boolean isSeen = seenIdx >= 0 && cursor.getInt(seenIdx) == 1;
                int subId = subIdIdx >= 0 ? cursor.getInt(subIdIdx) : -1;
                String subject = subjIdx >= 0 ? cursor.getString(subjIdx) : null;
                int mmsType = typeIdx >= 0 ? cursor.getInt(typeIdx) : PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF;
                long mSize = sizeIdx >= 0 ? cursor.getLong(sizeIdx) : 0L;
                int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : 0;
                String ctLocation = ctLocIdx >= 0 ? cursor.getString(ctLocIdx) : null;

                // Load parts and text body
                MmsPartsResult partsResult = loadPartsForMms(mmsId);
                String body = partsResult.textBody;
                if (body == null || body.isEmpty()) {
                    body = subject;
                }

                // Load Addresses
                MmsAddressResult addressResult = loadAddressesForMms(mmsId);
                String senderAddress = addressResult.fromAddress;

                // Determine download status
                int downloadStatus = SmsMessage.MMS_DOWNLOAD_STATUS_NONE;
                if (box == Telephony.Mms.MESSAGE_BOX_INBOX) {
                    if (mmsType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
                        if (status == 129) {
                            downloadStatus = SmsMessage.MMS_DOWNLOAD_STATUS_DOWNLOADING;
                        } else if (status == Telephony.Sms.STATUS_FAILED) {
                            downloadStatus = SmsMessage.MMS_DOWNLOAD_STATUS_FAILED;
                        } else {
                            downloadStatus = SmsMessage.MMS_DOWNLOAD_STATUS_PENDING;
                        }
                    } else {
                        downloadStatus = SmsMessage.MMS_DOWNLOAD_STATUS_DOWNLOADED;
                    }
                }

                SmsMessage mmsMessage = new SmsMessage(
                        mmsId,
                        threadId,
                        senderAddress,
                        body,
                        dateMs,
                        dateSentMs,
                        box,
                        isRead,
                        isSeen,
                        status,
                        0,
                        subId,
                        true,
                        subject,
                        partsResult.attachments,
                        downloadStatus,
                        ctLocation,
                        mSize,
                        addressResult.recipients
                );

                result.add(mmsMessage);
            } while (cursor.moveToNext());

        } catch (Exception e) {
            Log.e(TAG, "Error querying MMS for thread " + threadId + ": " + e.getMessage(), e);
        }

        return result;
    }

    private static class MmsPartsResult {
        String textBody;
        final List<MmsAttachment> attachments = new ArrayList<>();
    }

    @NonNull
    private MmsPartsResult loadPartsForMms(long mmsId) {
        MmsPartsResult res = new MmsPartsResult();
        ContentResolver resolver = appContext.getContentResolver();
        Uri partUri = Uri.parse("content://mms/" + mmsId + "/part");

        String[] projection = new String[]{
                Telephony.Mms.Part._ID,
                Telephony.Mms.Part.CONTENT_TYPE,
                Telephony.Mms.Part.NAME,
                Telephony.Mms.Part.FILENAME,
                Telephony.Mms.Part.TEXT,
                Telephony.Mms.Part.CHARSET
        };

        try (Cursor cursor = resolver.query(partUri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return res;
            }

            int idIdx = cursor.getColumnIndex(Telephony.Mms.Part._ID);
            int ctIdx = cursor.getColumnIndex(Telephony.Mms.Part.CONTENT_TYPE);
            int nameIdx = cursor.getColumnIndex(Telephony.Mms.Part.NAME);
            int fnIdx = cursor.getColumnIndex(Telephony.Mms.Part.FILENAME);
            int textIdx = cursor.getColumnIndex(Telephony.Mms.Part.TEXT);

            do {
                long partId = idIdx >= 0 ? cursor.getLong(idIdx) : -1L;
                if (partId <= 0) continue;

                String ct = ctIdx >= 0 ? cursor.getString(ctIdx) : null;
                if (ct == null) ct = "application/octet-stream";

                String name = nameIdx >= 0 ? cursor.getString(nameIdx) : null;
                String filename = fnIdx >= 0 ? cursor.getString(fnIdx) : null;
                String text = textIdx >= 0 ? cursor.getString(textIdx) : null;

                if (ct.startsWith("text/plain")) {
                    if (text != null) {
                        res.textBody = text;
                    } else {
                        // Read from stream
                        res.textBody = readStringFromPartStream(partId);
                    }
                } else if (!ct.equalsIgnoreCase("application/smil")) {
                    Uri dataUri = ContentUris.withAppendedId(Uri.parse("content://mms/part"), partId);
                    long partSize = getPartSize(dataUri);
                    res.attachments.add(new MmsAttachment(
                            partId,
                            mmsId,
                            dataUri,
                            ct,
                            filename,
                            name,
                            partSize
                    ));
                }
            } while (cursor.moveToNext());

        } catch (Exception e) {
            Log.e(TAG, "Error loading parts for MMS " + mmsId + ": " + e.getMessage(), e);
        }
        return res;
    }

    private static class MmsAddressResult {
        String fromAddress;
        final List<String> recipients = new ArrayList<>();
    }

    @NonNull
    private MmsAddressResult loadAddressesForMms(long mmsId) {
        MmsAddressResult res = new MmsAddressResult();
        ContentResolver resolver = appContext.getContentResolver();
        Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");

        String[] projection = new String[]{
                Telephony.Mms.Addr.ADDRESS,
                Telephony.Mms.Addr.TYPE
        };

        try (Cursor cursor = resolver.query(addrUri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return res;
            }

            int addrIdx = cursor.getColumnIndex(Telephony.Mms.Addr.ADDRESS);
            int typeIdx = cursor.getColumnIndex(Telephony.Mms.Addr.TYPE);

            do {
                String address = addrIdx >= 0 ? cursor.getString(addrIdx) : null;
                int type = typeIdx >= 0 ? cursor.getInt(typeIdx) : 0;
                if (address == null || address.isEmpty() || address.equals("insert-address-token")) {
                    continue;
                }

                if (type == PDU_HEADER_FROM) {
                    res.fromAddress = address;
                } else if (type == PDU_HEADER_TO || type == PDU_HEADER_CC) {
                    res.recipients.add(address);
                }
            } while (cursor.moveToNext());

        } catch (Exception e) {
            Log.e(TAG, "Error loading addresses for MMS " + mmsId + ": " + e.getMessage(), e);
        }
        return res;
    }

    @Nullable
    private String readStringFromPartStream(long partId) {
        Uri uri = ContentUris.withAppendedId(Uri.parse("content://mms/part"), partId);
        try (InputStream is = appContext.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (is == null) return null;
            byte[] buf = new byte[4096];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long getPartSize(@NonNull Uri partUri) {
        try (InputStream is = appContext.getContentResolver().openInputStream(partUri)) {
            if (is == null) return 0L;
            return is.available();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /**
     * Deletes an MMS record and its attached parts.
     */
    public boolean deleteMms(long mmsId) {
        try {
            Uri uri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsId);
            int rows = appContext.getContentResolver().delete(uri, null, null);
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting MMS " + mmsId + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Deletes all MMS records for a given thread ID.
     */
    public int deleteMmsForThread(long threadId) {
        try {
            String selection = Telephony.Mms.THREAD_ID + " = ?";
            String[] args = new String[]{String.valueOf(threadId)};
            return appContext.getContentResolver().delete(Telephony.Mms.CONTENT_URI, selection, args);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting MMS for thread " + threadId + ": " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Bulk deletes MMS records for multiple threads.
     */
    public int deleteMmsForMultipleThreads(@NonNull Set<Long> threadIds) {
        if (threadIds.isEmpty()) return 0;
        try {
            List<String> placeholders = new ArrayList<>(threadIds.size());
            List<String> args = new ArrayList<>(threadIds.size());
            for (Long id : threadIds) {
                if (id != null && id > 0) {
                    placeholders.add("?");
                    args.add(String.valueOf(id));
                }
            }
            if (args.isEmpty()) return 0;

            String selection = Telephony.Mms.THREAD_ID + " IN (" + TextUtils.join(",", placeholders) + ")";
            return appContext.getContentResolver().delete(Telephony.Mms.CONTENT_URI, selection, args.toArray(new String[0]));
        } catch (Exception e) {
            Log.e(TAG, "Error bulk deleting MMS: " + e.getMessage(), e);
            return 0;
        }
    }
}
