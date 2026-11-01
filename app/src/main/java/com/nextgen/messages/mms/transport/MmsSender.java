package com.nextgen.messages.mms.transport;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.mms.pdu.EncodedStringValue;
import com.nextgen.messages.mms.pdu.PduBody;
import com.nextgen.messages.mms.pdu.PduComposer;
import com.nextgen.messages.mms.pdu.PduHeaders;
import com.nextgen.messages.mms.pdu.PduPart;
import com.nextgen.messages.mms.pdu.SendReq;
import com.nextgen.messages.mms.pdu.SmilHelper;
import com.nextgen.messages.mms.storage.MmsStorageManager;
import com.nextgen.messages.telephony.MmsSentReceiver;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.utils.AppExecutors;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates genuine carrier MMS message composition, compression, PDU generation,
 * and transmission through Android's SmsManager.sendMultimediaMessage() API.
 */
public class MmsSender {

    private static final String TAG = "MmsSender";

    private static volatile MmsSender instance;
    private final Context appContext;
    private final AppExecutors executors;
    private final MmsStorageManager storageManager;

    public interface MmsSendCallback {
        void onMmsPrepared(long mmsId);
        void onError(String message);
    }

    private MmsSender(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.executors = AppExecutors.getInstance();
        this.storageManager = MmsStorageManager.getInstance(context);
    }

    public static MmsSender getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (MmsSender.class) {
                if (instance == null) {
                    instance = new MmsSender(context);
                }
            }
        }
        return instance;
    }

    /**
     * Prepares and transmits a carrier MMS message asynchronously.
     */
    public void sendMms(
            long threadId,
            @NonNull List<String> recipients,
            @Nullable String subject,
            @Nullable String textBody,
            @Nullable List<Uri> attachmentUris,
            int subscriptionId,
            @Nullable MmsSendCallback callback
    ) {
        executors.diskIO().execute(() -> {
            try {
                if (!SmsRoleManager.isDefaultSmsApp(appContext)) {
                    notifyError(callback, "NextGen Messages must be default SMS app to send MMS.");
                    return;
                }

                if (recipients.isEmpty()) {
                    notifyError(callback, "At least one recipient is required.");
                    return;
                }

                int maxSize = MmsConfig.getMaxMessageSize(appContext, subscriptionId);

                // 1. Build SendReq PDU
                SendReq sendReq = new SendReq();
                sendReq.setDate(System.currentTimeMillis() / 1000L);

                // Add recipients
                for (String recipient : recipients) {
                    if (recipient != null && !recipient.trim().isEmpty()) {
                        sendReq.addTo(new EncodedStringValue(recipient.trim()));
                    }
                }

                // Add subject
                if (subject != null && !subject.trim().isEmpty()) {
                    sendReq.setSubject(new EncodedStringValue(subject.trim()));
                }

                // Add delivery report preference
                if (AppPreferences.getInstance(appContext).isMmsDeliveryReportsEnabled()) {
                    sendReq.setDeliveryReport(PduHeaders.VALUE_YES);
                }

                // Add read report preference
                if (AppPreferences.getInstance(appContext).isMmsReadReportsEnabled()) {
                    sendReq.setReadReport(PduHeaders.VALUE_YES);
                }

                // 2. Build Multipart Body
                PduBody pduBody = new PduBody();
                String firstMediaSrc = null;
                String firstMediaType = null;
                String textSrc = null;
                int partCounter = 1;

                // Add media attachments
                if (attachmentUris != null) {
                    for (Uri uri : attachmentUris) {
                        String mimeType = MmsMediaUtils.getMediaMimeType(appContext, uri);
                        String fileName = MmsMediaUtils.getFileName(appContext, uri);

                        byte[] data;
                        if (mimeType.startsWith("image/")) {
                            data = MmsMediaUtils.compressImageIfNeeded(appContext, uri, maxSize - 10240); // reserve 10KB for headers
                        } else {
                            data = MmsMediaUtils.readMediaBytes(appContext, uri);
                        }

                        if (data == null || data.length == 0) {
                            continue;
                        }

                        if (data.length > maxSize) {
                            notifyError(callback, "Attachment " + fileName + " exceeds carrier limit (" + (maxSize / 1024) + " KB).");
                            return;
                        }

                        PduPart mediaPart = new PduPart();
                        mediaPart.setContentType(mimeType.getBytes(StandardCharsets.US_ASCII));
                        mediaPart.setName(fileName.getBytes(StandardCharsets.UTF_8));
                        mediaPart.setFilename(fileName.getBytes(StandardCharsets.UTF_8));
                        String contentLocation = "part_" + partCounter + "_" + fileName;
                        mediaPart.setContentLocation(contentLocation.getBytes(StandardCharsets.US_ASCII));
                        mediaPart.setContentId(("<" + contentLocation + ">").getBytes(StandardCharsets.US_ASCII));
                        mediaPart.setData(data);

                        pduBody.addPart(mediaPart);

                        if (firstMediaSrc == null) {
                            firstMediaSrc = contentLocation;
                            firstMediaType = mimeType;
                        }
                        partCounter++;
                    }
                }

                // Add text part if textBody is present
                if (textBody != null && !textBody.trim().isEmpty()) {
                    byte[] textBytes = textBody.getBytes(StandardCharsets.UTF_8);
                    textSrc = "text_0.txt";

                    PduPart textPart = new PduPart();
                    textPart.setContentType("text/plain".getBytes(StandardCharsets.US_ASCII));
                    textPart.setCharset(com.nextgen.messages.mms.pdu.CharacterSets.UTF_8);
                    textPart.setContentLocation(textSrc.getBytes(StandardCharsets.US_ASCII));
                    textPart.setContentId(("<" + textSrc + ">").getBytes(StandardCharsets.US_ASCII));
                    textPart.setData(textBytes);

                    pduBody.addPart(0, textPart); // Text usually first or alongside SMIL
                }

                // Add SMIL layout part
                PduPart smilPart = SmilHelper.createSmilPart(textSrc, firstMediaSrc, firstMediaType);
                pduBody.addPart(0, smilPart);

                sendReq.setBody(pduBody);

                // 3. Compose binary MMS PDU
                PduComposer composer = new PduComposer(appContext, sendReq);
                byte[] pduBytes = composer.make();
                if (pduBytes == null || pduBytes.length == 0) {
                    notifyError(callback, "Failed to encode binary MMS PDU.");
                    return;
                }

                if (pduBytes.length > maxSize) {
                    notifyError(callback, "Encoded MMS size (" + (pduBytes.length / 1024) + " KB) exceeds carrier maximum (" + (maxSize / 1024) + " KB).");
                    return;
                }

                // 4. Save to Outbox
                long mmsId = storageManager.insertOutgoingMms(threadId, sendReq, subscriptionId, pduBytes.length);
                if (mmsId <= 0) {
                    notifyError(callback, "Failed to save MMS record to provider.");
                    return;
                }

                // 5. Write PDU to cache file for telephony transport
                File mmsDir = new File(appContext.getCacheDir(), "mms");
                if (!mmsDir.exists()) {
                    mmsDir.mkdirs();
                }
                File pduFile = new File(mmsDir, "send_" + mmsId + "_" + System.currentTimeMillis() + ".pdu");
                try (FileOutputStream fos = new FileOutputStream(pduFile)) {
                    fos.write(pduBytes);
                    fos.flush();
                }

                // 6. Create FileProvider content URI and grant permission to com.android.phone
                String authority = appContext.getPackageName() + ".fileprovider";
                Uri contentUri = FileProvider.getUriForFile(appContext, authority, pduFile);
                appContext.grantUriPermission("com.android.phone", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // 7. Create PendingIntent for MmsSentReceiver
                Intent sentIntent = new Intent(appContext, MmsSentReceiver.class);
                sentIntent.setAction(MmsSentReceiver.ACTION_MMS_SENT);
                sentIntent.putExtra(MmsSentReceiver.EXTRA_MMS_ID, mmsId);
                sentIntent.putExtra(MmsSentReceiver.EXTRA_FILE_PATH, pduFile.getAbsolutePath());

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pendingSentIntent = PendingIntent.getBroadcast(
                        appContext,
                        (int) (mmsId & 0x7FFFFFFF),
                        sentIntent,
                        flags
                );

                // 8. Dispatch via SmsManager
                SmsManager smsManager = getSmsManagerForSubId(subscriptionId);
                Log.i(TAG, "Dispatching carrier MMS " + mmsId + " (" + pduBytes.length + " bytes) for sub " + subscriptionId);
                smsManager.sendMultimediaMessage(appContext, contentUri, null, null, pendingSentIntent);

                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onMmsPrepared(mmsId));
                }

            } catch (Throwable t) {
                Log.e(TAG, "Error in sendMms: " + t.getMessage(), t);
                notifyError(callback, "MMS send error: " + t.getMessage());
            }
        });
    }

    private SmsManager getSmsManagerForSubId(int subId) {
        if (subId > 0) {
            return SmsManager.getSmsManagerForSubscriptionId(subId);
        }
        return SmsManager.getDefault();
    }

    private void notifyError(@Nullable MmsSendCallback callback, @NonNull String message) {
        if (callback != null) {
            executors.mainThread().execute(() -> callback.onError(message));
        }
    }
}
