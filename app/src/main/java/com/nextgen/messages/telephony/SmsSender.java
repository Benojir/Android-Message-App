package com.nextgen.messages.telephony;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;

/**
 * Orchestrates SMS sending through Android SmsManager.
 * Enforces default SMS role verification, off-main-thread database persistence,
 * Dual SIM subscription targeting, multipart message division, and PendingIntent callbacks.
 */
public class SmsSender {

    private static final String TAG = "SmsSender";

    public interface SendCallback {
        void onMessageQueued(@NonNull Uri messageUri);
        void onError(@NonNull String errorMessage);
    }

    private static volatile SmsSender instance;

    private final Context appContext;
    private final SmsStorageManager storageManager;
    private final SimManager simManager;
    private final AppExecutors executors;

    private SmsSender(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.storageManager = SmsStorageManager.getInstance(this.appContext);
        this.simManager = SimManager.getInstance(this.appContext);
        this.executors = AppExecutors.getInstance();
    }

    public static SmsSender getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SmsSender.class) {
                if (instance == null) {
                    instance = new SmsSender(context);
                }
            }
        }
        return instance;
    }

    /**
     * Sends an SMS message to a recipient using the specified SIM subscription.
     * All database writes and SmsManager dispatching are performed off the main thread.
     */
    public void sendMessage(
            long threadId,
            @NonNull String recipient,
            @NonNull String body,
            int targetSubId,
            @Nullable SendCallback callback
    ) {
        executors.diskIO().execute(() -> {
            try {
                // 1. Validate default SMS role
                if (!SmsRoleManager.isDefaultSmsApp(appContext)) {
                    postError(callback, "NextGen Messages must be your default SMS app to send messages.");
                    return;
                }

                // 2. Validate inputs
                String cleanRecipient = recipient.trim();
                if (cleanRecipient.isEmpty()) {
                    postError(callback, "Recipient phone number cannot be empty.");
                    return;
                }

                String cleanBody = body.trim();
                if (cleanBody.isEmpty()) {
                    postError(callback, "Message text cannot be empty.");
                    return;
                }

                // 3. Resolve target SIM subscription
                SimInfo simInfo = null;
                if (targetSubId > 0) {
                    simInfo = simManager.getSimForSubscriptionId(targetSubId);
                }
                if (simInfo == null) {
                    simInfo = simManager.getEffectiveSimForRecipient(cleanRecipient);
                }

                int finalSubId = (simInfo != null) ? simInfo.getSubscriptionId() : -1;

                // 4. Persist outgoing message in Telephony.Sms with OUTBOX status
                Uri messageUri = storageManager.insertOutgoingMessage(threadId, cleanRecipient, body, finalSubId);
                if (messageUri == null) {
                    postError(callback, "Failed to persist outgoing message in telephony store.");
                    return;
                }

                // Notify UI that message has been queued/stored
                if (callback != null) {
                    executors.mainThread().execute(() -> callback.onMessageQueued(messageUri));
                }

                // 5. Resolve SmsManager instance
                SmsManager smsManager = getSmsManagerForSubId(finalSubId);

                // 6. Divide message into segments
                ArrayList<String> parts = SmsEncodingUtils.divideMessage(smsManager, body);
                int numParts = (parts != null && !parts.isEmpty()) ? parts.size() : 1;
                if (parts == null || parts.isEmpty()) {
                    parts = new ArrayList<>();
                    parts.add(body);
                }

                ArrayList<PendingIntent> sentIntents = new ArrayList<>(numParts);
                ArrayList<PendingIntent> deliveryIntents = new ArrayList<>(numParts);

                // For Android 12+ (API 31+ / API 34+), FLAG_IMMUTABLE is standard and required
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }

                for (int i = 0; i < numParts; i++) {
                    int requestCode = (int) (System.currentTimeMillis() & 0x7ffffff) + (i * 2);

                    // Sent PendingIntent with explicit package and component
                    Intent sentIntent = new Intent(appContext, SmsSentReceiver.class);
                    sentIntent.setAction(SmsSentReceiver.ACTION_SMS_SENT);
                    sentIntent.setPackage(appContext.getPackageName());
                    sentIntent.putExtra(SmsSentReceiver.EXTRA_MESSAGE_URI, messageUri.toString());
                    sentIntent.putExtra(SmsSentReceiver.EXTRA_PART_INDEX, i);
                    sentIntent.putExtra(SmsSentReceiver.EXTRA_TOTAL_PARTS, numParts);

                    PendingIntent sentPI = PendingIntent.getBroadcast(
                            appContext,
                            requestCode,
                            sentIntent,
                            flags
                    );
                    sentIntents.add(sentPI);

                    // Delivery PendingIntent with explicit package and component
                    Intent deliveryIntent = new Intent(appContext, SmsDeliveryReceiver.class);
                    deliveryIntent.setAction(SmsDeliveryReceiver.ACTION_SMS_DELIVERY);
                    deliveryIntent.setPackage(appContext.getPackageName());
                    deliveryIntent.putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_URI, messageUri.toString());

                    PendingIntent deliveryPI = PendingIntent.getBroadcast(
                            appContext,
                            requestCode + 1,
                            deliveryIntent,
                            flags
                    );
                    deliveryIntents.add(deliveryPI);
                }

                // 7. Dispatch through SmsManager
                try {
                    if (numParts == 1) {
                        smsManager.sendTextMessage(cleanRecipient, null, parts.get(0), sentIntents.get(0), deliveryIntents.get(0));
                    } else {
                        smsManager.sendMultipartTextMessage(cleanRecipient, null, parts, sentIntents, deliveryIntents);
                    }
                    Log.i(TAG, "Dispatched SMS with " + numParts + " parts to " + cleanRecipient + " via subId=" + finalSubId);
                } catch (Exception e) {
                    Log.e(TAG, "Exception during SmsManager dispatch", e);
                    storageManager.updateMessageStatus(messageUri, Telephony.Sms.MESSAGE_TYPE_FAILED, Telephony.Sms.STATUS_FAILED, -1);
                    postError(callback, "Transmission error: " + e.getMessage());
                }

            } catch (Throwable t) {
                Log.e(TAG, "Unexpected error in sendMessage", t);
                postError(callback, "Error sending SMS: " + t.getMessage());
            }
        });
    }

    /**
     * Retries sending a previously failed message.
     */
    public void retryMessage(
            @NonNull SmsMessage failedMessage,
            int targetSubId,
            @Nullable SendCallback callback
    ) {
        String recipient = failedMessage.getAddress();
        String body = failedMessage.getBody();

        if (recipient == null || body == null) {
            postError(callback, "Cannot retry message: missing recipient or body.");
            return;
        }

        // Reuse existing sendMessage flow
        sendMessage(failedMessage.getThreadId(), recipient, body, targetSubId, callback);
    }

    @NonNull
    private SmsManager getSmsManagerForSubId(int subId) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SmsManager systemSmsManager = appContext.getSystemService(SmsManager.class);
                if (systemSmsManager != null && subId > 0) {
                    return systemSmsManager.createForSubscriptionId(subId);
                } else if (systemSmsManager != null) {
                    return systemSmsManager;
                }
            } else {
                if (subId > 0) {
                    return SmsManager.getSmsManagerForSubscriptionId(subId);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get subscription SmsManager, falling back to default: " + e.getMessage());
        }
        return SmsManager.getDefault();
    }

    private void postError(@Nullable SendCallback callback, @NonNull String errorMessage) {
        if (callback != null) {
            executors.mainThread().execute(() -> callback.onError(errorMessage));
        }
    }
}
