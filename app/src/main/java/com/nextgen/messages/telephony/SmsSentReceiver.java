package com.nextgen.messages.telephony;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BroadcastReceiver for handling Android SmsManager SMS sent callbacks.
 * Correlates single and multipart SMS sent events, updates the Telephony.Sms
 * state to MESSAGE_TYPE_SENT on success or MESSAGE_TYPE_FAILED on transmission errors.
 */
public class SmsSentReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsSentReceiver";

    public static final String ACTION_SMS_SENT = "com.nextgen.messages.action.SMS_SENT";
    public static final String EXTRA_MESSAGE_URI = "extra_message_uri";
    public static final String EXTRA_PART_INDEX = "extra_part_index";
    public static final String EXTRA_TOTAL_PARTS = "extra_total_parts";

    // Tracks remaining expected parts for multipart messages: key = messageUriString, value = count of successful parts
    private static final ConcurrentHashMap<String, AtomicInteger> MULTIPART_SUCCESS_TRACKER = new ConcurrentHashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();
        if (!ACTION_SMS_SENT.equals(action)) {
            return;
        }

        String uriString = intent.getStringExtra(EXTRA_MESSAGE_URI);
        if (uriString == null || uriString.isEmpty()) {
            Log.w(TAG, "SMS_SENT received with missing EXTRA_MESSAGE_URI");
            return;
        }

        Uri messageUri = Uri.parse(uriString);
        int partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0);
        int totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1);
        int resultCode = getResultCode();

        Log.d(TAG, "SMS_SENT callback for part " + (partIndex + 1) + "/" + totalParts + ", resultCode=" + resultCode + ", uri=" + uriString);

        SmsStorageManager storageManager = SmsStorageManager.getInstance(context);

        if (resultCode == Activity.RESULT_OK) {
            if (totalParts <= 1) {
                // Single part message sent successfully
                storageManager.updateMessageStatus(messageUri, Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_NONE, 0);
            } else {
                // Multipart message: increment success count
                AtomicInteger successfulParts = MULTIPART_SUCCESS_TRACKER.computeIfAbsent(uriString, k -> new AtomicInteger(0));
                int currentSuccess = successfulParts.incrementAndGet();
                if (currentSuccess >= totalParts) {
                    MULTIPART_SUCCESS_TRACKER.remove(uriString);
                    storageManager.updateMessageStatus(messageUri, Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_NONE, 0);
                    Log.i(TAG, "All " + totalParts + " parts sent successfully for " + uriString);
                }
            }
        } else {
            // Any part failure marks the message as failed
            MULTIPART_SUCCESS_TRACKER.remove(uriString);
            storageManager.updateMessageStatus(messageUri, Telephony.Sms.MESSAGE_TYPE_FAILED, Telephony.Sms.STATUS_FAILED, resultCode);
            Log.e(TAG, "SMS send failed for " + uriString + " with error code: " + getErrorMessage(resultCode));
        }
    }

    @NonNull
    public static String getErrorMessage(int resultCode) {
        switch (resultCode) {
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                return "Generic failure (Carrier or device issue)";
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                return "No network service or emergency calls only";
            case SmsManager.RESULT_ERROR_NULL_PDU:
                return "Null PDU";
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                return "Radio off or Airplane mode active";
            case SmsManager.RESULT_ERROR_LIMIT_EXCEEDED:
                return "SMS rate limit exceeded";
            default:
                return "Error code " + resultCode;
        }
    }
}
