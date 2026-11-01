package com.nextgen.messages.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.util.Log;
import com.nextgen.messages.utils.AppExecutors;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BroadcastReceiver required by Android framework for default SMS applications.
 * Registered for {@link Telephony.Sms.Intents#SMS_DELIVER_ACTION} ("android.provider.Telephony.SMS_DELIVER")
 * and protected by permission "android.permission.BROADCAST_SMS".
 *
 * <p>Under Android default SMS app architecture, this receiver is responsible for
 * persisting incoming SMS messages into the Telephony Provider.
 * Uses {@link #goAsync()} to perform database writes safely on a background thread.
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

        final PendingResult pendingResult = goAsync();
        final Context applicationContext = context.getApplicationContext();

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (messages == null || messages.length == 0) {
                    Log.w(TAG, "No SMS messages extracted from SMS_DELIVER intent");
                    return;
                }

                // Extract subscription ID from intent extras if present
                int subId = intent.getIntExtra("subscription", -1);
                if (subId <= 0) {
                    subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                }

                // Group message segments by originating address to safely concatenate multipart SMS
                Map<String, IncomingMessageAccumulator> accumulatedMap = new LinkedHashMap<>();

                for (SmsMessage message : messages) {
                    if (message != null) {
                        String sender = message.getDisplayOriginatingAddress();
                        if (sender == null || sender.trim().isEmpty()) {
                            sender = message.getOriginatingAddress();
                        }
                        if (sender == null) {
                            sender = "Unknown";
                        }

                        long timestamp = message.getTimestampMillis();
                        String bodyPart = message.getDisplayMessageBody();
                        if (bodyPart == null) {
                            bodyPart = "";
                        }

                        IncomingMessageAccumulator acc = accumulatedMap.get(sender);
                        if (acc == null) {
                            acc = new IncomingMessageAccumulator(sender, timestamp);
                            accumulatedMap.put(sender, acc);
                        }
                        acc.append(bodyPart);
                    }
                }

                SmsStorageManager storageManager = SmsStorageManager.getInstance(applicationContext);

                for (IncomingMessageAccumulator item : accumulatedMap.values()) {
                    String fullBody = item.getBody();
                    Log.i(TAG, "Persisting incoming SMS from: " + item.sender + " (length: " + fullBody.length() + ")");
                    storageManager.insertIncomingMessage(item.sender, fullBody, item.timestamp, subId);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error processing incoming SMS_DELIVER intent", e);
            } finally {
                try {
                    pendingResult.finish();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static class IncomingMessageAccumulator {
        final String sender;
        final long timestamp;
        final StringBuilder bodyBuilder = new StringBuilder();

        IncomingMessageAccumulator(String sender, long timestamp) {
            this.sender = sender;
            this.timestamp = timestamp;
        }

        void append(String part) {
            bodyBuilder.append(part);
        }

        String getBody() {
            return bodyBuilder.toString();
        }
    }
}
