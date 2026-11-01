package com.nextgen.messages.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.util.Log;

/**
 * BroadcastReceiver required by Android framework for default SMS/MMS applications.
 * Registered for {@link Telephony.Sms.Intents#WAP_PUSH_DELIVER_ACTION} ("android.provider.Telephony.WAP_PUSH_DELIVER")
 * with MIME type "application/vnd.wap.mms-message" and protected by permission "android.permission.BROADCAST_WAP_PUSH".
 *
 * <p>In Step 2, this provides the required framework declaration and safe intake.
 * Full MMS PDU parsing, attachment downloading, and storage remain incomplete until Step 6.
 */
public class MmsDeliverReceiver extends BroadcastReceiver {

    private static final String TAG = "MmsDeliverReceiver";
    public static final String MMS_MIME_TYPE = "application/vnd.wap.mms-message";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            Log.w(TAG, "onReceive called with null context or intent");
            return;
        }

        String action = intent.getAction();
        if (!Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(action)) {
            Log.w(TAG, "Ignoring unexpected action: " + action);
            return;
        }

        String type = intent.getType();
        if (!MMS_MIME_TYPE.equalsIgnoreCase(type)) {
            Log.w(TAG, "Ignoring unexpected MIME type: " + type);
            return;
        }

        try {
            byte[] pushData = intent.getByteArrayExtra("data");
            int dataLength = (pushData != null) ? pushData.length : 0;
            Log.i(TAG, "Safely received MMS WAP_PUSH_DELIVER intent (data length: " + dataLength + " bytes)");

            // NOTE FOR STEP 6:
            // Full MMS parsing, carrier APN retrieval, and multipart downloading
            // will be implemented in Step 6.
        } catch (Exception e) {
            Log.e(TAG, "Error processing incoming WAP_PUSH_DELIVER intent", e);
        }
    }
}
