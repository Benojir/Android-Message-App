package com.nextgen.messages.mms.transport;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import com.nextgen.messages.mms.storage.MmsStorageManager;
import com.nextgen.messages.telephony.MmsDownloadedReceiver;
import com.nextgen.messages.utils.AppExecutors;
import java.io.File;

/**
 * Orchestrates downloading of incoming carrier MMS messages via SmsManager.downloadMultimediaMessage().
 */
public class MmsDownloadManager {

    private static final String TAG = "MmsDownloadManager";

    private static volatile MmsDownloadManager instance;
    private final Context appContext;
    private final AppExecutors executors;
    private final MmsStorageManager storageManager;

    private MmsDownloadManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.executors = AppExecutors.getInstance();
        this.storageManager = MmsStorageManager.getInstance(context);
    }

    public static MmsDownloadManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (MmsDownloadManager.class) {
                if (instance == null) {
                    instance = new MmsDownloadManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Triggers the carrier download for an incoming MMS message.
     */
    public void downloadMms(
            long mmsId,
            long threadId,
            @NonNull String contentLocation,
            int subscriptionId,
            @Nullable String sender
    ) {
        executors.diskIO().execute(() -> {
            try {
                if (contentLocation == null || contentLocation.trim().isEmpty()) {
                    Log.e(TAG, "Cannot download MMS " + mmsId + ": missing Content-Location URL");
                    return;
                }

                // 1. Prepare destination file
                File mmsDir = new File(appContext.getCacheDir(), "mms");
                if (!mmsDir.exists()) {
                    mmsDir.mkdirs();
                }
                File pduFile = new File(mmsDir, "recv_" + mmsId + "_" + System.currentTimeMillis() + ".pdu");

                // 2. Obtain FileProvider URI
                String authority = appContext.getPackageName() + ".fileprovider";
                Uri contentUri = FileProvider.getUriForFile(appContext, authority, pduFile);

                // 3. Grant write permission to com.android.phone telephony process
                appContext.grantUriPermission("com.android.phone", contentUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // 4. Update status to Downloading (129)
                storageManager.updateDownloadStatus(mmsId, 129);

                // 5. Create PendingIntent for MmsDownloadedReceiver
                Intent intent = new Intent(appContext, MmsDownloadedReceiver.class);
                intent.setAction(MmsDownloadedReceiver.ACTION_MMS_DOWNLOADED);
                intent.putExtra(MmsDownloadedReceiver.EXTRA_MMS_ID, mmsId);
                intent.putExtra(MmsDownloadedReceiver.EXTRA_THREAD_ID, threadId);
                intent.putExtra(MmsDownloadedReceiver.EXTRA_FILE_PATH, pduFile.getAbsolutePath());
                intent.putExtra(MmsDownloadedReceiver.EXTRA_SENDER, sender);

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        (int) (mmsId & 0x7FFFFFFF),
                        intent,
                        flags
                );

                // 6. Invoke SmsManager.downloadMultimediaMessage
                SmsManager smsManager = (subscriptionId > 0)
                        ? SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                        : SmsManager.getDefault();

                Log.i(TAG, "Requesting carrier MMS download for ID " + mmsId + " from " + contentLocation);
                smsManager.downloadMultimediaMessage(appContext, contentLocation, contentUri, null, pendingIntent);

            } catch (Throwable t) {
                Log.e(TAG, "Error initiating MMS download for " + mmsId + ": " + t.getMessage(), t);
                storageManager.updateDownloadStatus(mmsId, android.provider.Telephony.Sms.STATUS_FAILED);
            }
        });
    }
}
