package com.nextgen.messages.notification;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.util.Log;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.utils.AppExecutors;

/**
 * BroadcastReceiver executing the "Mark as read" notification action.
 * Updates both SMS and MMS read states in the Telephony provider and
 * cancels the corresponding conversation notification without opening the app.
 */
public class MarkAsReadReceiver extends BroadcastReceiver {

    private static final String TAG = "MarkAsReadReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();

        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                long threadId = intent.getLongExtra(MessageNotificationManager.EXTRA_THREAD_ID, -1L);
                if (threadId <= 0) {
                    Log.w(TAG, "Invalid threadId for Mark As Read: " + threadId);
                    return;
                }

                if (!SmsRoleManager.isDefaultSmsApp(appContext)) {
                    Log.w(TAG, "App is not the default SMS app; cannot mark messages read in Telephony provider.");
                    return;
                }

                Log.i(TAG, "Marking thread " + threadId + " as read from notification action");

                // 1. Mark SMS messages read
                SmsRepository.getInstance(appContext).markThreadMessagesAsRead(threadId, null);

                // 2. Mark MMS messages read
                try {
                    ContentValues mmsValues = new ContentValues();
                    mmsValues.put(Telephony.Mms.READ, 1);
                    mmsValues.put(Telephony.Mms.SEEN, 1);
                    appContext.getContentResolver().update(
                            Telephony.Mms.CONTENT_URI,
                            mmsValues,
                            Telephony.Mms.THREAD_ID + " = ? AND " + Telephony.Mms.READ + " = 0",
                            new String[]{String.valueOf(threadId)}
                    );
                } catch (Exception e) {
                    Log.w(TAG, "Failed to update MMS read flag: " + e.getMessage());
                }

                // 3. Cancel the notification
                MessageNotificationManager.getInstance(appContext).cancelConversationNotification(threadId);

            } catch (Exception e) {
                Log.e(TAG, "Error marking thread as read from notification", e);
            } finally {
                try {
                    pendingResult.finish();
                } catch (Exception ignored) {
                }
            }
        });
    }
}
