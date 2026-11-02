package com.nextgen.messages.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import com.nextgen.messages.R;

/**
 * Manages system notification channels for Android 8.0+ (API 26+).
 * Creates and maintains stable channels with clear user-facing descriptions,
 * respecting system authority and user-modified channel configurations.
 */
public class NotificationChannelManager {

    public static final String CHANNEL_ID_MESSAGES = "channel_nextgen_messages";
    public static final String CHANNEL_ID_GROUP_MESSAGES = "channel_nextgen_group_messages";
    public static final String CHANNEL_ID_MMS_STATUS = "channel_nextgen_mms_status";
    public static final String CHANNEL_ID_SILENT = "channel_nextgen_silent";

    /**
     * Initializes all required notification channels.
     * Safe to invoke multiple times (e.g. at app startup). Existing user preferences are preserved.
     */
    public static void initChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        // 1. Standard Messages Channel (High Importance)
        NotificationChannel messagesChannel = new NotificationChannel(
                CHANNEL_ID_MESSAGES,
                context.getString(R.string.channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        messagesChannel.setDescription(context.getString(R.string.channel_messages_desc));
        messagesChannel.enableLights(true);
        messagesChannel.setLightColor(Color.BLUE);
        messagesChannel.enableVibration(true);
        messagesChannel.setVibrationPattern(new long[]{0, 250, 250, 250});
        messagesChannel.setShowBadge(true);

        // 2. Group Messages Channel (High Importance)
        NotificationChannel groupChannel = new NotificationChannel(
                CHANNEL_ID_GROUP_MESSAGES,
                context.getString(R.string.channel_group_messages_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        groupChannel.setDescription(context.getString(R.string.channel_group_messages_desc));
        groupChannel.enableLights(true);
        groupChannel.setLightColor(Color.CYAN);
        groupChannel.enableVibration(true);
        groupChannel.setVibrationPattern(new long[]{0, 250, 250, 250});
        groupChannel.setShowBadge(true);

        // 3. MMS Status & Downloads Channel (Default Importance)
        NotificationChannel mmsChannel = new NotificationChannel(
                CHANNEL_ID_MMS_STATUS,
                context.getString(R.string.channel_mms_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        mmsChannel.setDescription(context.getString(R.string.channel_mms_desc));
        mmsChannel.setShowBadge(true);

        // 4. Silent / Muted Messages Channel (Low Importance)
        NotificationChannel silentChannel = new NotificationChannel(
                CHANNEL_ID_SILENT,
                context.getString(R.string.channel_silent_name),
                NotificationManager.IMPORTANCE_LOW
        );
        silentChannel.setDescription(context.getString(R.string.channel_silent_desc));
        silentChannel.setSound(null, null);
        silentChannel.enableVibration(false);
        silentChannel.setShowBadge(true);

        manager.createNotificationChannel(messagesChannel);
        manager.createNotificationChannel(groupChannel);
        manager.createNotificationChannel(mmsChannel);
        manager.createNotificationChannel(silentChannel);
    }

    /**
     * Resolves the appropriate channel ID based on conversation state.
     */
    @NonNull
    public static String getChannelForConversation(boolean isGroup, boolean isMuted, boolean isSilent) {
        if (isMuted || isSilent) {
            return CHANNEL_ID_SILENT;
        }
        if (isGroup) {
            return CHANNEL_ID_GROUP_MESSAGES;
        }
        return CHANNEL_ID_MESSAGES;
    }

    /**
     * Launches the system settings screen for a specific notification channel.
     */
    public static void openChannelSettings(@NonNull Context context, @NonNull String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                openAppNotificationSettings(context);
            }
        } else {
            openAppNotificationSettings(context);
        }
    }

    /**
     * Launches the system notification settings screen for the app.
     */
    public static void openAppNotificationSettings(@NonNull Context context) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
