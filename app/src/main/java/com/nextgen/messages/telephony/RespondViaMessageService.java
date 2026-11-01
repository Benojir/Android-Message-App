package com.nextgen.messages.telephony;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.annotation.Nullable;
import com.nextgen.messages.ui.compose.ComposeActivity;

/**
 * Service required by Android framework for default SMS applications to handle
 * quick text responses when declining an incoming phone call ("Respond via message").
 * Registered for "android.intent.action.RESPOND_VIA_MESSAGE" and protected by
 * permission "android.permission.SEND_RESPOND_VIA_MESSAGE".
 */
public class RespondViaMessageService extends Service {

    private static final String TAG = "RespondViaMessage";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(action)) {
                Uri dataUri = intent.getData();
                String messageText = intent.getStringExtra(Intent.EXTRA_TEXT);

                if (dataUri != null && messageText != null && !messageText.trim().isEmpty()) {
                    String recipient = ComposeActivity.parseRecipientFromUri(dataUri);
                    if (!recipient.isEmpty()) {
                        Log.i(TAG, "Sending quick decline SMS to: " + recipient);
                        SmsSender.getInstance(this).sendMessage(-1L, recipient, messageText, -1, null);
                    }
                }
            }
        }

        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
