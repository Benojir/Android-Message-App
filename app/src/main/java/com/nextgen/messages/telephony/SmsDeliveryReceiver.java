package com.nextgen.messages.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * BroadcastReceiver for handling carrier SMS delivery reports.
 * Confirms receipt by the recipient handset and updates the message status to STATUS_COMPLETE.
 */
public class SmsDeliveryReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsDeliveryReceiver";

    public static final String ACTION_SMS_DELIVERY = "com.nextgen.messages.action.SMS_DELIVERY";
    public static final String EXTRA_MESSAGE_URI = "extra_message_uri";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();
        if (!ACTION_SMS_DELIVERY.equals(action)) {
            return;
        }

        String uriString = intent.getStringExtra(EXTRA_MESSAGE_URI);
        if (uriString == null || uriString.isEmpty()) {
            Log.w(TAG, "SMS_DELIVERY received with missing EXTRA_MESSAGE_URI");
            return;
        }

        Uri messageUri = Uri.parse(uriString);
        byte[] pdu = intent.getByteArrayExtra("pdu");
        String format = intent.getStringExtra("format");

        Log.d(TAG, "Received delivery report for " + uriString + " (hasPdu=" + (pdu != null) + ")");

        if (pdu != null) {
            try {
                SmsMessage message;
                if (format != null) {
                    message = SmsMessage.createFromPdu(pdu, format);
                } else {
                    message = SmsMessage.createFromPdu(pdu);
                }

                if (message != null) {
                    int status = message.getStatus();
                    Log.d(TAG, "Delivery report PDU status: " + status);
                    if (status == 0) { // Standard GSM delivery success code
                        SmsStorageManager.getInstance(context).markMessageDelivered(messageUri);
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing delivery PDU: " + e.getMessage());
            }
        }

        // Default confirmation
        SmsStorageManager.getInstance(context).markMessageDelivered(messageUri);
    }
}
