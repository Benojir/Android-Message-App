package com.nextgen.messages.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.annotation.NonNull;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.mms.pdu.GenericPdu;
import com.nextgen.messages.mms.pdu.NotificationInd;
import com.nextgen.messages.mms.pdu.PduParser;
import com.nextgen.messages.mms.storage.MmsStorageManager;
import com.nextgen.messages.mms.transport.MmsDownloadManager;
import java.nio.charset.StandardCharsets;

/**
 * BroadcastReceiver required by Android framework for default SMS/MMS applications.
 * Handles incoming carrier WAP push notifications (WAP_PUSH_DELIVER_ACTION), parses
 * NotificationInd PDUs, stores metadata in Telephony.Mms.Inbox, and triggers auto-download
 * when configured by user preferences.
 */
public class MmsDeliverReceiver extends BroadcastReceiver {

    private static final String TAG = "MmsDeliverReceiver";
    public static final String MMS_MIME_TYPE = "application/vnd.wap.mms-message";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            Log.w(TAG, "onReceive called with null context or intent");
            return;
        }

        String action = intent.getAction();
        if (!Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(action)) {
            Log.w(TAG, "Ignoring unexpected action: " + action);
            return;
        }

        String type = intent.getType();
        if (!MMS_MIME_TYPE.equalsIgnoreCase(type)) {
            Log.w(TAG, "Ignoring unexpected MIME type: " + type);
            return;
        }

        try {
            byte[] pushData = intent.getByteArrayExtra("data");
            if (pushData == null || pushData.length == 0) {
                Log.w(TAG, "Received empty push data payload.");
                return;
            }

            int subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1);
            Log.i(TAG, "Processing incoming MMS WAP_PUSH_DELIVER intent (" + pushData.length + " bytes, subId=" + subId + ")");

            PduParser parser = new PduParser(pushData);
            GenericPdu genericPdu = parser.parse();

            if (!(genericPdu instanceof NotificationInd)) {
                Log.w(TAG, "Incoming PDU is not NotificationInd: " + (genericPdu != null ? genericPdu.getClass().getSimpleName() : "null"));
                return;
            }

            NotificationInd notification = (NotificationInd) genericPdu;
            String sender = (notification.getFrom() != null) ? notification.getFrom().getString() : null;
            if ("insert-address-token".equalsIgnoreCase(sender)) {
                sender = null;
            }

            long threadId = 0L;
            if (sender != null && !sender.isEmpty()) {
                try {
                    threadId = Telephony.Threads.getOrCreateThreadId(context, sender);
                } catch (Exception e) {
                    Log.w(TAG, "Could not resolve thread ID for sender: " + e.getMessage());
                }
            }

            // 1. Store NotificationInd record in Telephony.Mms.Inbox
            MmsStorageManager storageManager = MmsStorageManager.getInstance(context);
            long mmsId = storageManager.insertIncomingNotification(threadId, notification, subId, sender);
            if (mmsId <= 0) {
                Log.e(TAG, "Failed to persist MMS notification to database.");
                return;
            }

            // 2. Check Auto-download conditions
            AppPreferences prefs = AppPreferences.getInstance(context);
            boolean autoDownload = prefs.isMmsAutoDownloadEnabled();
            boolean isRoaming = isNetworkRoaming(context, subId);

            if (isRoaming && !prefs.isMmsRoamingAutoDownloadEnabled()) {
                Log.i(TAG, "Device is roaming; skipping automatic MMS download per preferences.");
                autoDownload = false;
            }

            // 3. Initiate MMS download if eligible
            byte[] ctLocBytes = notification.getContentLocation();
            if (autoDownload && ctLocBytes != null && ctLocBytes.length > 0) {
                String contentLocation = new String(ctLocBytes, StandardCharsets.US_ASCII);
                Log.i(TAG, "Auto-downloading MMS " + mmsId + " from " + contentLocation);
                MmsDownloadManager.getInstance(context).downloadMms(mmsId, threadId, contentLocation, subId, sender);
            } else {
                Log.i(TAG, "MMS " + mmsId + " marked as pending manual download.");
            }

            // 4. Notify content observers to refresh UI
            try {
                context.getContentResolver().notifyChange(Telephony.Mms.CONTENT_URI, null);
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing incoming WAP_PUSH_DELIVER intent", e);
        }
    }

    private boolean isNetworkRoaming(@NonNull Context context, int subId) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                if (subId > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    TelephonyManager subTm = tm.createForSubscriptionId(subId);
                    return subTm.isNetworkRoaming();
                }
                return tm.isNetworkRoaming();
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
