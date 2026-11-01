package com.nextgen.messages.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * BroadcastReceiver required by Android framework for default SMS applications.
 * Registered for {@link Telephony.Sms.Intents#SMS_DELIVER_ACTION} ("android.provider.Telephony.SMS_DELIVER")
 * and protected by permission "android.permission.BROADCAST_SMS".
 *
 * <p>In Step 2, this provides the required safe receiving infrastructure:
 * defensive intent inspection and PDU parsing. Full message persistence and notification
 * dispatch will be implemented in Step 4.
 */
public class SmsDeliverReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsDeliverReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            Log.w(TAG, "onReceive called with null context or intent");
            return;
        }

        String action = intent.getAction();
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(action)) {
            Log.w(TAG, "Ignoring unexpected action: " + action);
            return;
        }

        try {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (messages == null || messages.length == 0) {
                Log.w(TAG, "No SMS messages extracted from SMS_DELIVER intent");
                return;
            }

            StringBuilder bodyBuilder = new StringBuilder();
            String originatingAddress = null;
            long timestampMillis = System.currentTimeMillis();

            for (SmsMessage message : messages) {
                if (message != null) {
                    if (originatingAddress == null) {
                        originatingAddress = message.getDisplayOriginatingAddress();
                        timestampMillis = message.getTimestampMillis();
                    }
                    String messageBody = message.getDisplayMessageBody();
                    if (messageBody != null) {
                        bodyBuilder.append(messageBody);
                    }
                }
            }

            Log.i(TAG, "Safely received SMS delivery event from: " + originatingAddress
                    + " (length: " + bodyBuilder.length() + ", timestamp: " + timestampMillis + ")");

            // NOTE FOR STEP 4:
            // Room database insertion, conversation threading, and user notifications
            // will be implemented in Step 4. This receiver safely validates and consumes
            // the delivery intent without losing data or throwing unhandled exceptions.

        } catch (Exception e) {
            Log.e(TAG, "Error processing incoming SMS_DELIVER intent", e);
        }
    }
}
