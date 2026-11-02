package com.nextgen.messages.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.nextgen.messages.data.repository.ConversationMetadataRepository;
import com.nextgen.messages.utils.AppExecutors;

/**
 * BroadcastReceiver triggered when a conversation notification is explicitly dismissed
 * (swiped away) by the user. Updates local metadata to avoid immediately reposting unchanged
 * alerts while strictly preserving unread message states in the Telephony provider.
 */
public class NotificationDismissReceiver extends BroadcastReceiver {

    private static final String TAG = "NotifDismissReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        long threadId = intent.getLongExtra(MessageNotificationManager.EXTRA_THREAD_ID, -1L);
        if (threadId <= 0) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                Log.d(TAG, "Notification for thread " + threadId + " dismissed by user.");
                ConversationMetadataRepository.getInstance(appContext).setNotificationDismissed(threadId, true);
                MessageNotificationManager.getInstance(appContext).cancelConversationNotification(threadId);
            } catch (Exception e) {
                Log.e(TAG, "Error handling notification dismissal", e);
            } finally {
                try {
                    pendingResult.finish();
                } catch (Exception ignored) {
                }
            }
        });
    }
}
