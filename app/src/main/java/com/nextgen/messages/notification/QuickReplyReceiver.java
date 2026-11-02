package com.nextgen.messages.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.RemoteInput;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.telephony.SimInfo;
import com.nextgen.messages.telephony.SimManager;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.telephony.SmsSender;
import com.nextgen.messages.utils.AppExecutors;

/**
 * BroadcastReceiver handling inline Quick Reply actions triggered via {@link RemoteInput}
 * directly from Android notifications. Reuses the existing {@link SmsSender} pipeline
 * and validates default SMS role, SIM subscription availability, and conversation identity.
 */
public class QuickReplyReceiver extends BroadcastReceiver {

    private static final String TAG = "QuickReplyReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                Bundle remoteInputResults = RemoteInput.getResultsFromIntent(intent);
                CharSequence replySequence = remoteInputResults != null
                        ? remoteInputResults.getCharSequence(MessageNotificationManager.KEY_TEXT_REPLY)
                        : null;

                if (replySequence == null || replySequence.toString().trim().isEmpty()) {
                    Log.w(TAG, "Empty quick reply text received.");
                    return;
                }

                String replyText = replySequence.toString().trim();
                long threadId = intent.getLongExtra(MessageNotificationManager.EXTRA_THREAD_ID, -1L);
                String recipient = intent.getStringExtra(MessageNotificationManager.EXTRA_RECIPIENT);
                int subId = intent.getIntExtra(MessageNotificationManager.EXTRA_SUBSCRIPTION_ID, -1);

                if (recipient == null || recipient.trim().isEmpty()) {
                    Log.e(TAG, "Missing recipient address for quick reply.");
                    return;
                }

                if (!SmsRoleManager.isDefaultSmsApp(appContext)) {
                    Log.e(TAG, "App is not the default SMS app; cannot dispatch quick reply.");
                    return;
                }

                // Resolve target SIM subscription
                SimManager simManager = SimManager.getInstance(appContext);
                int targetSubId = subId;
                SimInfo simInfo = targetSubId > 0 ? simManager.getSimForSubscriptionId(targetSubId) : null;
                if (simInfo == null) {
                    SimInfo effectiveSim = simManager.getEffectiveSimForRecipient(recipient);
                    targetSubId = effectiveSim != null ? effectiveSim.getSubscriptionId() : -1;
                }

                Log.i(TAG, "Dispatching Quick Reply to: " + recipient + " (threadId=" + threadId + ", subId=" + targetSubId + ")");

                // Send reply via existing verified SmsSender
                SmsSender.getInstance(appContext).sendMessage(
                        threadId,
                        recipient,
                        replyText,
                        targetSubId,
                        new SmsSender.SendCallback() {
                            @Override
                            public void onMessageQueued(@NonNull android.net.Uri messageUri) {
                                Log.i(TAG, "Quick Reply successfully queued/sent: " + messageUri);
                            }

                            @Override
                            public void onError(@NonNull String errorMessage) {
                                Log.e(TAG, "Failed sending quick reply: " + errorMessage);
                            }
                        }
                );

                // Mark previous unread messages in the thread as read since the user engaged
                if (threadId > 0) {
                    SmsRepository.getInstance(appContext).markThreadMessagesAsRead(threadId, null);
                }

                // Dismiss notification now that the user has replied
                MessageNotificationManager.getInstance(appContext).cancelConversationNotification(threadId);

            } catch (Exception e) {
                Log.e(TAG, "Error executing quick reply", e);
            } finally {
                try {
                    pendingResult.finish();
                } catch (Exception ignored) {
                }
            }
        });
    }
}
