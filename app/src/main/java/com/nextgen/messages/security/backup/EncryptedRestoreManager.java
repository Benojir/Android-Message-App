package com.nextgen.messages.security.backup;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.data.repository.BlockedSenderRepository;
import com.nextgen.messages.data.repository.CategoryRepository;
import com.nextgen.messages.data.repository.ConversationMetadataRepository;
import com.nextgen.messages.data.repository.StarredMessageRepository;
import com.nextgen.messages.smartinbox.db.BlockedSenderEntity;
import com.nextgen.messages.smartinbox.db.ClassificationRuleEntity;
import com.nextgen.messages.smartinbox.db.MessageCategoryEntity;
import com.nextgen.messages.smartinbox.db.SmartInboxDao;
import com.nextgen.messages.smartinbox.db.SmartInboxDatabase;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.utils.AppExecutors;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Handles encrypted backup restoration with default SMS role verification,
 * authenticated AES-256-GCM verification, and conservative duplicate detection.
 */
public class EncryptedRestoreManager {

    public static class RestoreResult {
        public final int smsRestored;
        public final int smsDuplicates;
        public final int mmsRestored;
        public final int mmsDuplicates;
        public final int attachmentsRestored;
        public final int metadataRestored;

        public RestoreResult(
                int smsRestored,
                int smsDuplicates,
                int mmsRestored,
                int mmsDuplicates,
                int attachmentsRestored,
                int metadataRestored
        ) {
            this.smsRestored = smsRestored;
            this.smsDuplicates = smsDuplicates;
            this.mmsRestored = mmsRestored;
            this.mmsDuplicates = mmsDuplicates;
            this.attachmentsRestored = attachmentsRestored;
            this.metadataRestored = metadataRestored;
        }
    }

    public interface RestoreProgressListener {
        void onPhaseStarted(@NonNull String phaseName);
        void onProgress(int current, int total, @NonNull String statusMessage);
        void onComplete(@NonNull RestoreResult result);
        void onError(@NonNull Exception error);
    }

    public interface PreviewCallback {
        void onPreviewLoaded(@NonNull BackupManifest manifest);
        void onError(@NonNull Exception error);
    }

    private final Context context;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public EncryptedRestoreManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public void cancel() {
        isCancelled.set(true);
    }

    /**
     * Previews backup manifest without performing any modifications.
     */
    public void previewBackup(
            @NonNull InputStream inputStream,
            @NonNull String password,
            @NonNull PreviewCallback callback
    ) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                BackupCrypto.HeaderMetadata header = BackupCrypto.readHeader(inputStream);
                SecretKey secretKey = BackupCrypto.deriveKey(password, header.salt, header.iterations);
                Cipher cipher = BackupCrypto.createDecryptCipher(secretKey, header.iv);

                CipherInputStream cis = new CipherInputStream(inputStream, cipher);
                ZipInputStream zis = new ZipInputStream(cis);

                ZipEntry entry;
                BackupManifest manifest = null;

                while ((entry = zis.getNextEntry()) != null) {
                    if (BackupConstants.ENTRY_MANIFEST.equals(entry.getName())) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            baos.write(buffer, 0, len);
                        }
                        String json = baos.toString(StandardCharsets.UTF_8.name());
                        manifest = BackupManifest.fromJsonString(json);
                        break;
                    }
                }

                zis.close();
                cis.close();

                if (manifest != null) {
                    final BackupManifest res = manifest;
                    AppExecutors.getInstance().mainThread().execute(() -> callback.onPreviewLoaded(res));
                } else {
                    throw new IllegalArgumentException("Backup archive is missing manifest information.");
                }

            } catch (Exception e) {
                AppExecutors.getInstance().mainThread().execute(() -> callback.onError(e));
            }
        });
    }

    /**
     * Executes restore operation in Merge mode, skipping duplicates safely.
     */
    public void restoreBackup(
            @NonNull InputStream inputStream,
            @NonNull String password,
            @NonNull RestoreProgressListener listener
    ) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                if (!SmsRoleManager.isDefaultSmsApp(context)) {
                    throw new SecurityException("NextGen Messages must be set as your default SMS app before restoring messages.");
                }

                listener.onPhaseStarted("Verifying and Decrypting Backup");

                BackupCrypto.HeaderMetadata header = BackupCrypto.readHeader(inputStream);
                SecretKey secretKey = BackupCrypto.deriveKey(password, header.salt, header.iterations);
                Cipher cipher = BackupCrypto.createDecryptCipher(secretKey, header.iv);

                CipherInputStream cis = new CipherInputStream(inputStream, cipher);
                ZipInputStream zis = new ZipInputStream(cis);

                int smsRestored = 0;
                int smsDuplicates = 0;
                int mmsRestored = 0;
                int mmsDuplicates = 0;
                int attachmentsRestored = 0;
                int metadataRestored = 0;

                Map<String, byte[]> attachmentFiles = new HashMap<>();
                String smsJson = null;
                String mmsJson = null;
                String metadataJson = null;

                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (isCancelled.get()) throw new InterruptedException("Restoration cancelled by user.");

                    String name = entry.getName();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }

                    if (BackupConstants.ENTRY_SMS.equals(name)) {
                        smsJson = baos.toString(StandardCharsets.UTF_8.name());
                    } else if (BackupConstants.ENTRY_MMS.equals(name)) {
                        mmsJson = baos.toString(StandardCharsets.UTF_8.name());
                    } else if (BackupConstants.ENTRY_METADATA.equals(name)) {
                        metadataJson = baos.toString(StandardCharsets.UTF_8.name());
                    } else if (name.startsWith(BackupConstants.ATTACHMENTS_DIR)) {
                        attachmentFiles.put(name, baos.toByteArray());
                    }
                }

                zis.close();
                cis.close();

                // 1. Restore SMS Records
                if (smsJson != null) {
                    listener.onPhaseStarted("Restoring SMS Messages");
                    JSONArray smsArray = new JSONArray(smsJson);
                    int total = smsArray.length();
                    for (int i = 0; i < total; i++) {
                        if (isCancelled.get()) throw new InterruptedException("Restoration cancelled.");

                        JSONObject obj = smsArray.getJSONObject(i);
                        String address = obj.optString("address", "");
                        String body = obj.optString("body", "");
                        long date = obj.optLong("date", 0L);
                        int type = obj.optInt("type", 1);
                        int read = obj.optInt("read", 1);
                        int status = obj.optInt("status", -1);
                        long dateSent = obj.optLong("dateSent", 0L);
                        int subId = obj.optInt("subId", -1);

                        if (isSmsDuplicate(address, body, date, type)) {
                            smsDuplicates++;
                        } else {
                            insertSmsRecord(address, body, date, dateSent, type, read, status, subId);
                            smsRestored++;
                        }

                        if ((i + 1) % 50 == 0 || i == total - 1) {
                            final int current = i + 1;
                            AppExecutors.getInstance().mainThread().execute(
                                    () -> listener.onProgress(current, total, "Processed " + current + " / " + total + " SMS")
                            );
                        }
                    }
                }

                // 2. Restore MMS Records & Attachments
                if (mmsJson != null) {
                    listener.onPhaseStarted("Restoring MMS Messages & Media");
                    JSONArray mmsArray = new JSONArray(mmsJson);
                    int total = mmsArray.length();
                    for (int i = 0; i < total; i++) {
                        if (isCancelled.get()) throw new InterruptedException("Restoration cancelled.");

                        JSONObject obj = mmsArray.getJSONObject(i);
                        long date = obj.optLong("date", 0L);
                        int msgBox = obj.optInt("msgBox", 1);
                        int subId = obj.optInt("subId", -1);
                        int read = obj.optInt("read", 1);
                        int mType = obj.optInt("mType", 128);
                        String subject = obj.optString("subject", "");

                        if (isMmsDuplicate(date, msgBox, subject)) {
                            mmsDuplicates++;
                        } else {
                            JSONArray addrs = obj.optJSONArray("addresses");
                            JSONArray parts = obj.optJSONArray("parts");
                            int partsSaved = insertMmsRecord(date, msgBox, subId, read, mType, subject, addrs, parts, attachmentFiles);
                            mmsRestored++;
                            attachmentsRestored += partsSaved;
                        }

                        if ((i + 1) % 10 == 0 || i == total - 1) {
                            final int current = i + 1;
                            AppExecutors.getInstance().mainThread().execute(
                                    () -> listener.onProgress(current, total, "Processed " + current + " / " + total + " MMS")
                            );
                        }
                    }
                }

                // 3. Restore App Metadata
                if (metadataJson != null) {
                    listener.onPhaseStarted("Restoring Metadata & Preferences");
                    metadataRestored = restoreMetadata(metadataJson);
                }

                // 4. Notify content resolver to refresh conversation views
                context.getContentResolver().notifyChange(Telephony.MmsSms.CONTENT_CONVERSATIONS_URI, null);

                RestoreResult result = new RestoreResult(
                        smsRestored,
                        smsDuplicates,
                        mmsRestored,
                        mmsDuplicates,
                        attachmentsRestored,
                        metadataRestored
                );

                AppExecutors.getInstance().mainThread().execute(() -> listener.onComplete(result));

            } catch (Exception e) {
                if (!isCancelled.get()) {
                    AppExecutors.getInstance().mainThread().execute(() -> listener.onError(e));
                }
            }
        });
    }

    private boolean isSmsDuplicate(@NonNull String address, @NonNull String body, long date, int type) {
        ContentResolver cr = context.getContentResolver();
        String selection = Telephony.Sms.ADDRESS + " = ? AND " +
                Telephony.Sms.TYPE + " = ? AND " +
                Telephony.Sms.DATE + " BETWEEN ? AND ? AND " +
                Telephony.Sms.BODY + " = ?";
        String[] args = new String[]{
                address,
                String.valueOf(type),
                String.valueOf(date - 2000L),
                String.valueOf(date + 2000L),
                body
        };

        try (Cursor c = cr.query(Telephony.Sms.CONTENT_URI, new String[]{Telephony.Sms._ID}, selection, args, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    private void insertSmsRecord(
            @NonNull String address,
            @NonNull String body,
            long date,
            long dateSent,
            int type,
            int read,
            int status,
            int subId
    ) {
        ContentValues cv = new ContentValues();
        cv.put(Telephony.Sms.ADDRESS, address);
        cv.put(Telephony.Sms.BODY, body);
        cv.put(Telephony.Sms.DATE, date);
        cv.put(Telephony.Sms.DATE_SENT, dateSent);
        cv.put(Telephony.Sms.TYPE, type);
        cv.put(Telephony.Sms.READ, read);
        cv.put(Telephony.Sms.STATUS, status);
        if (subId > 0) {
            cv.put(Telephony.Sms.SUBSCRIPTION_ID, subId);
        }

        try {
            long threadId = Telephony.Threads.getOrCreateThreadId(context, address);
            if (threadId > 0) {
                cv.put(Telephony.Sms.THREAD_ID, threadId);
            }
            context.getContentResolver().insert(Telephony.Sms.CONTENT_URI, cv);
        } catch (Exception e) {
            // Log & continue
        }
    }

    private boolean isMmsDuplicate(long date, int msgBox, @Nullable String subject) {
        ContentResolver cr = context.getContentResolver();
        String selection = Telephony.Mms.MESSAGE_BOX + " = ? AND " +
                Telephony.Mms.DATE + " BETWEEN ? AND ?";
        String[] args = new String[]{
                String.valueOf(msgBox),
                String.valueOf(date - 2L),
                String.valueOf(date + 2L)
        };

        try (Cursor c = cr.query(Telephony.Mms.CONTENT_URI, new String[]{Telephony.Mms._ID}, selection, args, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    private int insertMmsRecord(
            long date,
            int msgBox,
            int subId,
            int read,
            int mType,
            @Nullable String subject,
            @Nullable JSONArray addrs,
            @Nullable JSONArray parts,
            @NonNull Map<String, byte[]> attachmentFiles
    ) {
        ContentResolver cr = context.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(Telephony.Mms.DATE, date);
        cv.put(Telephony.Mms.MESSAGE_BOX, msgBox);
        cv.put(Telephony.Mms.READ, read);
        cv.put(Telephony.Mms.MESSAGE_TYPE, mType);
        if (subject != null) cv.put(Telephony.Mms.SUBJECT, subject);
        if (subId > 0) cv.put(Telephony.Mms.SUBSCRIPTION_ID, subId);

        Uri mmsUri = cr.insert(Telephony.Mms.CONTENT_URI, cv);
        if (mmsUri == null) return 0;

        long newMmsId = ContentUris.parseId(mmsUri);
        int partsSaved = 0;

        // Insert Addresses
        if (addrs != null) {
            for (int i = 0; i < addrs.length(); i++) {
                JSONObject a = addrs.optJSONObject(i);
                if (a != null) {
                    ContentValues addrCv = new ContentValues();
                    addrCv.put("type", a.optInt("type", 151));
                    addrCv.put("address", a.optString("address", ""));
                    addrCv.put("charset", 106);
                    cr.insert(Uri.parse("content://mms/" + newMmsId + "/addr"), addrCv);
                }
            }
        }

        // Insert Parts
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject p = parts.optJSONObject(i);
                if (p != null) {
                    long oldPartId = p.optLong("partId", 0L);
                    String contentType = p.optString("contentType", "text/plain");
                    String name = p.optString("name", "");
                    String text = p.optString("text", null);

                    ContentValues partCv = new ContentValues();
                    partCv.put(Telephony.Mms.Part.CONTENT_TYPE, contentType);
                    partCv.put(Telephony.Mms.Part.NAME, name);
                    if (text != null) {
                        partCv.put(Telephony.Mms.Part.TEXT, text);
                    }

                    Uri partUri = cr.insert(Uri.parse("content://mms/" + newMmsId + "/part"), partCv);
                    if (partUri != null) {
                        partsSaved++;
                        // If binary attachment exists in archive, stream bytes to new part URI
                        String attachKey = BackupConstants.ATTACHMENTS_DIR + "part_" + oldPartId + ".dat";
                        byte[] fileBytes = attachmentFiles.get(attachKey);
                        if (fileBytes != null) {
                            try (OutputStream out = cr.openOutputStream(partUri)) {
                                if (out != null) {
                                    out.write(fileBytes);
                                }
                            } catch (Exception e) {
                                // Ignore part stream failure
                            }
                        }
                    }
                }
            }
        }
        return partsSaved;
    }

    private int restoreMetadata(@NonNull String json) {
        int restoredCount = 0;
        try {
            JSONObject root = new JSONObject(json);
            SmartInboxDao dao = SmartInboxDatabase.getInstance(context).smartInboxDao();

            // 1. Blocked Senders
            JSONArray blocked = root.optJSONArray("blockedSenders");
            if (blocked != null) {
                for (int i = 0; i < blocked.length(); i++) {
                    JSONObject b = blocked.getJSONObject(i);
                    String address = b.optString("address", "");
                    String normalized = b.optString("normalized", "");
                    if (normalized.isEmpty()) {
                        normalized = BlockedSenderRepository.normalizeIdentifier(address);
                    }
                    if (!normalized.isEmpty()) {
                        BlockedSenderEntity entity = BlockedSenderEntity.create(
                                address,
                                normalized,
                                b.optLong("blockedAt", System.currentTimeMillis()),
                                b.optString("reason", "Restored from backup")
                        );
                        dao.insertBlockedSender(entity);
                        restoredCount++;
                    }
                }
            }

            // 2. Categories & Rules
            JSONArray categories = root.optJSONArray("categories");
            if (categories != null) {
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject c = categories.getJSONObject(i);
                    if (!c.optBoolean("system", false)) {
                        String name = c.optString("name", "");
                        if (!name.isEmpty()) {
                            MessageCategoryEntity cat = MessageCategoryEntity.createCustom(name, "ic_category_other", "#6750A4");
                            dao.insertCategory(cat);
                            restoredCount++;
                        }
                    }
                }
            }

            JSONArray rules = root.optJSONArray("rules");
            if (rules != null) {
                for (int i = 0; i < rules.length(); i++) {
                    JSONObject r = rules.getJSONObject(i);
                    String name = r.optString("name", "Rule");
                    String type = r.optString("ruleType", ClassificationRuleEntity.TYPE_BODY_CONTAINS);
                    String match = r.optString("matchValue", "");
                    String targetCat = r.optString("categoryKey", "other");
                    int priority = r.optInt("priority", 100);
                    if (!match.isEmpty()) {
                        ClassificationRuleEntity rule = ClassificationRuleEntity.create(name, type, match, targetCat, priority);
                        dao.insertRule(rule);
                        restoredCount++;
                    }
                }
            }

            // 3. Private Threads
            JSONArray privates = root.optJSONArray("privateThreads");
            if (privates != null) {
                AppPreferences prefs = AppPreferences.getInstance(context);
                Set<String> set = new HashSet<>(prefs.getPrivateThreadIds());
                for (int i = 0; i < privates.length(); i++) {
                    set.add(privates.getString(i));
                    restoredCount++;
                }
                prefs.setPrivateThreadIds(set);
            }

            // 4. Preferences
            JSONObject prefsObj = root.optJSONObject("preferences");
            if (prefsObj != null) {
                AppPreferences prefs = AppPreferences.getInstance(context);
                if (prefsObj.has("bubbleStyle")) prefs.setBubbleStyle(prefsObj.getString("bubbleStyle"));
                if (prefsObj.has("bubbleColor")) prefs.setBubbleColorPreset(prefsObj.getString("bubbleColor"));
                if (prefsObj.has("fontSize")) prefs.setFontSize(prefsObj.getString("fontSize"));
                if (prefsObj.has("chatDensity")) prefs.setChatDensity(prefsObj.getString("chatDensity"));
                if (prefsObj.has("inboxLayout")) prefs.setInboxLayout(prefsObj.getString("inboxLayout"));
            }

        } catch (Exception e) {
            // Ignore metadata restore error
        }
        return restoredCount;
    }
}
