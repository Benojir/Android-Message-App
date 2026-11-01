package com.nextgen.messages.telephony;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.util.Log;
import com.nextgen.messages.mms.pdu.GenericPdu;
import com.nextgen.messages.mms.pdu.PduParser;
import com.nextgen.messages.mms.pdu.RetrieveConf;
import com.nextgen.messages.mms.storage.MmsStorageManager;
import java.io.File;
import java.io.FileInputStream;

/**
 * BroadcastReceiver invoked when carrier MMS download completes.
 */
public class MmsDownloadedReceiver extends BroadcastReceiver {

    private static final String TAG = "MmsDownloadedReceiver";

    public static final String ACTION_MMS_DOWNLOADED = "com.nextgen.messages.mms.ACTION_MMS_DOWNLOADED";
    public static final String EXTRA_MMS_ID = "com.nextgen.messages.mms.EXTRA_MMS_ID";
    public static final String EXTRA_THREAD_ID = "com.nextgen.messages.mms.EXTRA_THREAD_ID";
    public static final String EXTRA_FILE_PATH = "com.nextgen.messages.mms.EXTRA_FILE_PATH";
    public static final String EXTRA_SENDER = "com.nextgen.messages.mms.EXTRA_SENDER";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        long mmsId = intent.getLongExtra(EXTRA_MMS_ID, -1L);
        long threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L);
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        String sender = intent.getStringExtra(EXTRA_SENDER);
        int resultCode = getResultCode();

        Log.i(TAG, "MMS download callback for MMS " + mmsId + ", resultCode=" + resultCode);

        MmsStorageManager storageManager = MmsStorageManager.getInstance(context);

        if (resultCode == Activity.RESULT_OK && filePath != null) {
            try {
                File pduFile = new File(filePath);
                if (pduFile.exists()) {
                    byte[] pduBytes = new byte[(int) pduFile.length()];
                    try (FileInputStream fis = new FileInputStream(pduFile)) {
                        fis.read(pduBytes);
                    }

                    PduParser parser = new PduParser(pduBytes);
                    GenericPdu genericPdu = parser.parse();
                    if (genericPdu instanceof RetrieveConf) {
                        storageManager.updateDownloadedMms(mmsId, threadId, (RetrieveConf) genericPdu, sender);
                        Log.i(TAG, "MMS " + mmsId + " successfully downloaded, parsed, and saved.");

                        if (com.nextgen.messages.data.local.AppPreferences.getInstance(context).isSmartInboxEnabled()) {
                            try {
                                java.util.List<com.nextgen.messages.data.model.SmsMessage> msgs = storageManager.getMmsMessagesForThread(threadId);
                                for (com.nextgen.messages.data.model.SmsMessage m : msgs) {
                                    if (m.getId() == mmsId) {
                                        com.nextgen.messages.smartinbox.SmartInboxRepository.getInstance(context).classifyMessageSync(m);
                                        break;
                                    }
                                }
                            } catch (Exception ex) {
                                Log.w(TAG, "Failed to classify downloaded MMS: " + ex.getMessage());
                            }
                        }
                    } else {
                        Log.e(TAG, "Downloaded PDU is not RetrieveConf: " + (genericPdu != null ? genericPdu.getClass().getSimpleName() : "null"));
                        storageManager.updateDownloadStatus(mmsId, Telephony.Sms.STATUS_FAILED);
                    }
                    pduFile.delete();
                } else {
                    Log.e(TAG, "Downloaded PDU file not found: " + filePath);
                    storageManager.updateDownloadStatus(mmsId, Telephony.Sms.STATUS_FAILED);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing downloaded MMS PDU: " + e.getMessage(), e);
                storageManager.updateDownloadStatus(mmsId, Telephony.Sms.STATUS_FAILED);
            }
        } else {
            Log.e(TAG, "MMS download failed with resultCode=" + resultCode);
            storageManager.updateDownloadStatus(mmsId, Telephony.Sms.STATUS_FAILED);
        }

        // Notify content observers
        try {
            context.getContentResolver().notifyChange(Telephony.Mms.CONTENT_URI, null);
        } catch (Exception ignored) {
        }
    }
}
