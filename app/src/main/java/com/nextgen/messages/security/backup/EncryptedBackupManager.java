package com.nextgen.messages.security.backup;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.JsonWriter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.data.repository.BlockedSenderRepository;
import com.nextgen.messages.data.repository.CategoryRepository;
import com.nextgen.messages.data.repository.ConversationMetadataRepository;
import com.nextgen.messages.data.repository.StarredMessageRepository;
import com.nextgen.messages.smartinbox.db.BlockedSenderEntity;
import com.nextgen.messages.smartinbox.db.ClassificationRuleEntity;
import com.nextgen.messages.smartinbox.db.ConversationMetadataEntity;
import com.nextgen.messages.smartinbox.db.MessageCategoryEntity;
import com.nextgen.messages.smartinbox.db.SmartInboxDao;
import com.nextgen.messages.smartinbox.db.SmartInboxDatabase;
import com.nextgen.messages.smartinbox.db.StarredMessageEntity;
import com.nextgen.messages.utils.AppExecutors;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Handles encrypted backup export.
 * Streams SMS records, MMS messages, multimedia attachments, and Room database metadata
 * directly into an authenticated AES-256-GCM encrypted ZIP archive with bounded memory usage.
 */
public class EncryptedBackupManager {

    public interface BackupProgressListener {
        void onPhaseStarted(@NonNull String phaseName);
        void onProgress(int current, int total, @NonNull String statusMessage);
        void onComplete(@NonNull BackupManifest manifest);
        void onError(@NonNull Exception error);
    }

    private final Context context;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public EncryptedBackupManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public void cancel() {
        isCancelled.set(true);
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }

    /**
     * Executes backup export on a background thread.
     */
    public void createBackup(
            @NonNull OutputStream outputStream,
            @NonNull String password,
            @NonNull BackupProgressListener listener
    ) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                if (!BackupCrypto.isPasswordStrongEnough(password)) {
                    throw new IllegalArgumentException("Backup password must be at least 8 characters long.");
                }

                listener.onPhaseStarted("Initializing Cryptography");

                byte[] salt = BackupCrypto.generateSalt();
                byte[] iv = BackupCrypto.generateNonce();
                SecretKey secretKey = BackupCrypto.deriveKey(password, salt, BackupConstants.KDF_ITERATIONS);

                // Write format header
                BackupCrypto.writeHeader(outputStream, salt, BackupConstants.KDF_ITERATIONS, iv);

                Cipher cipher = BackupCrypto.createEncryptCipher(secretKey, iv);
                CipherOutputStream cos = new CipherOutputStream(outputStream, cipher);
                ZipOutputStream zos = new ZipOutputStream(cos);

                // 1. Export SMS Records
                if (isCancelled.get()) throw new InterruptedException("Backup cancelled by user.");
                listener.onPhaseStarted("Backing up SMS Messages");
                int smsCount = exportSmsMessages(zos, listener);

                // 2. Export MMS Records & collect media parts
                if (isCancelled.get()) throw new InterruptedException("Backup cancelled by user.");
                listener.onPhaseStarted("Backing up MMS Messages");
                List<Long> mediaPartIds = new ArrayList<>();
                int mmsCount = exportMmsMessages(zos, mediaPartIds, listener);

                // 3. Export MMS Media Attachments
                if (isCancelled.get()) throw new InterruptedException("Backup cancelled by user.");
                listener.onPhaseStarted("Backing up Media Attachments");
                int attachmentCount = exportAttachments(zos, mediaPartIds, listener);

                // 4. Export App Metadata
                if (isCancelled.get()) throw new InterruptedException("Backup cancelled by user.");
                listener.onPhaseStarted("Backing up App Metadata");
                int metadataCount = exportMetadata(zos);

                // 5. Write Manifest
                BackupManifest manifest = new BackupManifest(
                        BackupConstants.CURRENT_FORMAT_VERSION,
                        "1.3.0",
                        System.currentTimeMillis(),
                        smsCount,
                        mmsCount,
                        attachmentCount,
                        metadataCount
                );

                zos.putNextEntry(new ZipEntry(BackupConstants.ENTRY_MANIFEST));
                zos.write(manifest.toJsonString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.finish();
                cos.close();

                AppExecutors.getInstance().mainThread().execute(() -> listener.onComplete(manifest));

            } catch (Exception e) {
                if (!isCancelled.get()) {
                    AppExecutors.getInstance().mainThread().execute(() -> listener.onError(e));
                }
            }
        });
    }

    private int exportSmsMessages(ZipOutputStream zos, BackupProgressListener listener) throws Exception {
        zos.putNextEntry(new ZipEntry(BackupConstants.ENTRY_SMS));
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(zos, StandardCharsets.UTF_8));
        writer.beginArray();

        ContentResolver cr = context.getContentResolver();
        String[] projection = new String[]{
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.DATE_SENT,
                Telephony.Sms.READ,
                Telephony.Sms.STATUS,
                Telephony.Sms.TYPE,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.SUBSCRIPTION_ID
        };

        int count = 0;
        try (Cursor c = cr.query(Telephony.Sms.CONTENT_URI, projection, null, null, Telephony.Sms.DATE + " ASC")) {
            if (c != null) {
                int total = c.getCount();
                int idxAddress = c.getColumnIndex(Telephony.Sms.ADDRESS);
                int idxBody = c.getColumnIndex(Telephony.Sms.BODY);
                int idxDate = c.getColumnIndex(Telephony.Sms.DATE);
                int idxDateSent = c.getColumnIndex(Telephony.Sms.DATE_SENT);
                int idxRead = c.getColumnIndex(Telephony.Sms.READ);
                int idxStatus = c.getColumnIndex(Telephony.Sms.STATUS);
                int idxType = c.getColumnIndex(Telephony.Sms.TYPE);
                int idxThread = c.getColumnIndex(Telephony.Sms.THREAD_ID);
                int idxSub = c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID);

                while (c.moveToNext()) {
                    if (isCancelled.get()) break;

                    writer.beginObject();
                    writer.name("address").value(idxAddress != -1 ? c.getString(idxAddress) : "");
                    writer.name("body").value(idxBody != -1 ? c.getString(idxBody) : "");
                    writer.name("date").value(idxDate != -1 ? c.getLong(idxDate) : 0L);
                    writer.name("dateSent").value(idxDateSent != -1 ? c.getLong(idxDateSent) : 0L);
                    writer.name("read").value(idxRead != -1 ? c.getInt(idxRead) : 1);
                    writer.name("status").value(idxStatus != -1 ? c.getInt(idxStatus) : -1);
                    writer.name("type").value(idxType != -1 ? c.getInt(idxType) : 1);
                    writer.name("threadId").value(idxThread != -1 ? c.getLong(idxThread) : 0L);
                    writer.name("subId").value(idxSub != -1 ? c.getInt(idxSub) : -1);
                    writer.endObject();

                    count++;
                    if (count % 100 == 0) {
                        final int current = count;
                        AppExecutors.getInstance().mainThread().execute(
                                () -> listener.onProgress(current, total, "Exported " + current + " / " + total + " SMS")
                        );
                    }
                }
            }
        }

        writer.endArray();
        writer.flush();
        zos.closeEntry();
        return count;
    }

    private int exportMmsMessages(ZipOutputStream zos, List<Long> outMediaPartIds, BackupProgressListener listener) throws Exception {
        zos.putNextEntry(new ZipEntry(BackupConstants.ENTRY_MMS));
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(zos, StandardCharsets.UTF_8));
        writer.beginArray();

        ContentResolver cr = context.getContentResolver();
        String[] projection = new String[]{
                Telephony.Mms._ID,
                Telephony.Mms.DATE,
                Telephony.Mms.SUBSCRIPTION_ID,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.READ,
                Telephony.Mms.MESSAGE_TYPE,
                Telephony.Mms.SUBJECT,
                Telephony.Mms.THREAD_ID
        };

        int count = 0;
        try (Cursor c = cr.query(Telephony.Mms.CONTENT_URI, projection, null, null, Telephony.Mms.DATE + " ASC")) {
            if (c != null) {
                int total = c.getCount();
                int idxId = c.getColumnIndex(Telephony.Mms._ID);
                int idxDate = c.getColumnIndex(Telephony.Mms.DATE);
                int idxSub = c.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID);
                int idxBox = c.getColumnIndex(Telephony.Mms.MESSAGE_BOX);
                int idxRead = c.getColumnIndex(Telephony.Mms.READ);
                int idxMType = c.getColumnIndex(Telephony.Mms.MESSAGE_TYPE);
                int idxSubj = c.getColumnIndex(Telephony.Mms.SUBJECT);
                int idxThread = c.getColumnIndex(Telephony.Mms.THREAD_ID);

                while (c.moveToNext()) {
                    if (isCancelled.get()) break;

                    long mmsId = c.getLong(idxId);
                    writer.beginObject();
                    writer.name("mmsId").value(mmsId);
                    writer.name("date").value(idxDate != -1 ? c.getLong(idxDate) : 0L);
                    writer.name("subId").value(idxSub != -1 ? c.getInt(idxSub) : -1);
                    writer.name("msgBox").value(idxBox != -1 ? c.getInt(idxBox) : 1);
                    writer.name("read").value(idxRead != -1 ? c.getInt(idxRead) : 1);
                    writer.name("mType").value(idxMType != -1 ? c.getInt(idxMType) : 128);
                    writer.name("subject").value(idxSubj != -1 ? c.getString(idxSubj) : "");
                    writer.name("threadId").value(idxThread != -1 ? c.getLong(idxThread) : 0L);

                    // Addresses
                    writer.name("addresses");
                    writer.beginArray();
                    Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
                    try (Cursor addrCursor = cr.query(addrUri, null, null, null, null)) {
                        if (addrCursor != null) {
                            int aTypeIdx = addrCursor.getColumnIndex("type");
                            int aAddrIdx = addrCursor.getColumnIndex("address");
                            while (addrCursor.moveToNext()) {
                                writer.beginObject();
                                writer.name("type").value(aTypeIdx != -1 ? addrCursor.getInt(aTypeIdx) : 151);
                                writer.name("address").value(aAddrIdx != -1 ? addrCursor.getString(aAddrIdx) : "");
                                writer.endObject();
                            }
                        }
                    }
                    writer.endArray();

                    // Parts
                    writer.name("parts");
                    writer.beginArray();
                    Uri partUri = Uri.parse("content://mms/" + mmsId + "/part");
                    try (Cursor partCursor = cr.query(partUri, null, null, null, null)) {
                        if (partCursor != null) {
                            int pIdIdx = partCursor.getColumnIndex(Telephony.Mms.Part._ID);
                            int pCtIdx = partCursor.getColumnIndex(Telephony.Mms.Part.CONTENT_TYPE);
                            int pNameIdx = partCursor.getColumnIndex(Telephony.Mms.Part.NAME);
                            int pTextIdx = partCursor.getColumnIndex(Telephony.Mms.Part.TEXT);

                            while (partCursor.moveToNext()) {
                                long partId = partCursor.getLong(pIdIdx);
                                String contentType = pCtIdx != -1 ? partCursor.getString(pCtIdx) : "text/plain";
                                String name = pNameIdx != -1 ? partCursor.getString(pNameIdx) : "";
                                String text = pTextIdx != -1 ? partCursor.getString(pTextIdx) : null;

                                writer.beginObject();
                                writer.name("partId").value(partId);
                                writer.name("contentType").value(contentType);
                                writer.name("name").value(name != null ? name : "");
                                writer.name("text").value(text);
                                writer.endObject();

                                // If binary attachment, record for streaming export
                                if (contentType != null && !contentType.startsWith("text/") && !"application/smil".equalsIgnoreCase(contentType)) {
                                    outMediaPartIds.add(partId);
                                }
                            }
                        }
                    }
                    writer.endArray();

                    writer.endObject();
                    count++;

                    if (count % 20 == 0) {
                        final int current = count;
                        AppExecutors.getInstance().mainThread().execute(
                                () -> listener.onProgress(current, total, "Exported " + current + " / " + total + " MMS")
                        );
                    }
                }
            }
        }

        writer.endArray();
        writer.flush();
        zos.closeEntry();
        return count;
    }

    private int exportAttachments(ZipOutputStream zos, List<Long> mediaPartIds, BackupProgressListener listener) throws Exception {
        ContentResolver cr = context.getContentResolver();
        byte[] buffer = new byte[8192];
        int count = 0;
        int total = mediaPartIds.size();

        for (Long partId : mediaPartIds) {
            if (isCancelled.get()) break;

            Uri partUri = ContentUris.withAppendedId(Uri.parse("content://mms/part"), partId);
            try (InputStream in = cr.openInputStream(partUri)) {
                if (in != null) {
                    zos.putNextEntry(new ZipEntry(BackupConstants.ATTACHMENTS_DIR + "part_" + partId + ".dat"));
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                    count++;

                    final int current = count;
                    AppExecutors.getInstance().mainThread().execute(
                            () -> listener.onProgress(current, total, "Streamed " + current + " / " + total + " attachments")
                    );
                }
            } catch (Exception e) {
                // Ignore missing local carrier attachment file, keep backup intact
            }
        }
        return count;
    }

    private int exportMetadata(ZipOutputStream zos) throws Exception {
        zos.putNextEntry(new ZipEntry(BackupConstants.ENTRY_METADATA));
        JSONObject root = new JSONObject();

        // 1. Pinned & Archived Conversations
        List<ConversationMetadataEntity> metaList = ConversationMetadataRepository.getInstance(context).getAllMetadataSync();
        JSONArray metaArray = new JSONArray();
        for (ConversationMetadataEntity m : metaList) {
            JSONObject obj = new JSONObject();
            obj.put("threadId", m.getThreadId());
            obj.put("isPinned", m.isPinned());
            obj.put("pinnedAt", m.getPinnedAt());
            obj.put("isArchived", m.isArchived());
            obj.put("archivedAt", m.getArchivedAt());
            metaArray.put(obj);
        }
        root.put("conversationMetadata", metaArray);

        // 2. Blocked Senders
        SmartInboxDao dao = SmartInboxDatabase.getInstance(context).smartInboxDao();
        List<BlockedSenderEntity> blocked = dao.getAllBlockedSendersSync();
        JSONArray blockedArray = new JSONArray();
        for (BlockedSenderEntity b : blocked) {
            JSONObject obj = new JSONObject();
            obj.put("address", b.getSenderIdentifier());
            obj.put("normalized", b.getNormalizedIdentifier());
            obj.put("reason", b.getBlockReason());
            obj.put("blockedAt", b.getBlockedAt());
            blockedArray.put(obj);
        }
        root.put("blockedSenders", blockedArray);

        // 3. Starred Messages
        List<StarredMessageEntity> starred = StarredMessageRepository.getInstance(context).getAllStarredMessagesSync();
        JSONArray starredArray = new JSONArray();
        for (StarredMessageEntity s : starred) {
            JSONObject obj = new JSONObject();
            obj.put("messageKey", s.getMessageKey());
            obj.put("threadId", s.getThreadId());
            obj.put("starredAt", s.getStarredAt());
            starredArray.put(obj);
        }
        root.put("starredMessages", starredArray);

        // 4. Categories & Custom Rules
        List<MessageCategoryEntity> categories = dao.getAllCategoriesSync();
        JSONArray catArray = new JSONArray();
        for (MessageCategoryEntity cat : categories) {
            JSONObject obj = new JSONObject();
            obj.put("key", cat.getCategoryKey());
            obj.put("name", cat.getName());
            obj.put("system", cat.isBuiltIn());
            catArray.put(obj);
        }
        root.put("categories", catArray);

        List<ClassificationRuleEntity> rules = dao.getAllRulesSync();
        JSONArray ruleArray = new JSONArray();
        for (ClassificationRuleEntity r : rules) {
            JSONObject obj = new JSONObject();
            obj.put("name", r.getRuleName());
            obj.put("categoryKey", r.getTargetCategoryKey());
            obj.put("ruleType", r.getRuleType());
            obj.put("matchValue", r.getMatchValue());
            obj.put("priority", r.getPriority());
            ruleArray.put(obj);
        }
        root.put("rules", ruleArray);

        // 5. Private Threads
        AppPreferences prefs = AppPreferences.getInstance(context);
        JSONArray privateArray = new JSONArray(prefs.getPrivateThreadIds());
        root.put("privateThreads", privateArray);

        // 6. User Personalization Preferences
        JSONObject prefObj = new JSONObject();
        prefObj.put("themeMode", prefs.getThemeMode());
        prefObj.put("dynamicColors", prefs.isDynamicColorsEnabled());
        prefObj.put("bubbleStyle", prefs.getBubbleStyle());
        prefObj.put("bubbleColor", prefs.getBubbleColorPreset());
        prefObj.put("fontSize", prefs.getFontSize());
        prefObj.put("chatDensity", prefs.getChatDensity());
        prefObj.put("inboxLayout", prefs.getInboxLayout());
        root.put("preferences", prefObj);

        zos.write(root.toString().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
        return metaList.size() + blocked.size() + starred.size();
    }
}
