package com.nextgen.messages.telephony;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.annotation.Nullable;

/**
 * Service required by Android framework for default SMS applications to handle
 * quick text responses when declining an incoming phone call ("Respond via message").
 * Registered for "android.intent.action.RESPOND_VIA_MESSAGE" and protected by
 * permission "android.permission.SEND_RESPOND_VIA_MESSAGE".
 *
 * <p>In Step 2, this provides safe lifecycle handling. Actual message transmission
 * for call declines will be implemented in Step 4.
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
                Log.i(TAG, "Received RESPOND_VIA_MESSAGE for URI: " + dataUri
                        + " (hasText: " + (messageText != null && !messageText.isEmpty()) + ")");

                // NOTE FOR STEP 4:
                // SMS transmission when declining an incoming phone call will be
                // implemented in Step 4. Currently, we safely terminate the service.
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
