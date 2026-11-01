package com.nextgen.messages.telephony;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import com.nextgen.messages.mms.storage.MmsStorageManager;
import java.io.File;

/**
 * BroadcastReceiver invoked when carrier MMS transmission completes.
 */
public class MmsSentReceiver extends BroadcastReceiver {

    private static final String TAG = "MmsSentReceiver";

    public static final String ACTION_MMS_SENT = "com.nextgen.messages.mms.ACTION_MMS_SENT";
    public static final String EXTRA_MMS_ID = "com.nextgen.messages.mms.EXTRA_MMS_ID";
    public static final String EXTRA_FILE_PATH = "com.nextgen.messages.mms.EXTRA_FILE_PATH";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        long mmsId = intent.getLongExtra(EXTRA_MMS_ID, -1L);
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        int resultCode = getResultCode();

        Log.i(TAG, "MMS sent callback for MMS " + mmsId + ", resultCode=" + resultCode);

        MmsStorageManager storageManager = MmsStorageManager.getInstance(context);

        if (resultCode == Activity.RESULT_OK) {
            storageManager.markMmsSent(mmsId);
            Log.i(TAG, "MMS " + mmsId + " successfully sent via carrier MMSC.");
        } else {
            storageManager.markMmsFailed(mmsId);
            int httpStatusCode = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1);
            Log.e(TAG, "MMS " + mmsId + " transmission failed. resultCode=" + resultCode + ", httpStatusCode=" + httpStatusCode);
        }

        // Clean up temporary PDU file
        if (filePath != null) {
            try {
                File file = new File(filePath);
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception ignored) {
            }
        }

        // Notify content observers
        try {
            context.getContentResolver().notifyChange(Telephony.Mms.CONTENT_URI, null);
        } catch (Exception ignored) {
        }
    }
}
