package com.nextgen.messages.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;
import com.nextgen.messages.R;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.data.model.ContactInfo;
import com.nextgen.messages.data.model.MmsAttachment;
import com.nextgen.messages.data.repository.BlockedSenderRepository;
import com.nextgen.messages.data.repository.ContactRepository;
import com.nextgen.messages.data.repository.ConversationMetadataRepository;
import com.nextgen.messages.smartinbox.db.ConversationMetadataEntity;
import com.nextgen.messages.telephony.SimInfo;
import com.nextgen.messages.telephony.SimManager;
import com.nextgen.messages.ui.conversation.ConversationActivity;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated notification manager orchestrating incoming SMS/MMS alerts,
 * MessagingStyle presentation, inline Quick Reply, Mark as Read actions,
 * conversation grouping, and lock-screen privacy.
 */
public class MessageNotificationManager {

    private static final String TAG = "MessageNotificationMgr";

    public static final String GROUP_KEY_MESSAGES = "com.nextgen.messages.NOTIFICATION_GROUP";
    public static final int SUMMARY_NOTIFICATION_ID = 1;

    public static final String KEY_TEXT_REPLY = "key_text_reply";
    public static final String ACTION_QUICK_REPLY = "com.nextgen.messages.ACTION_QUICK_REPLY";
    public static final String ACTION_MARK_READ = "com.nextgen.messages.ACTION_MARK_READ";
    public static final String ACTION_DISMISS = "com.nextgen.messages.ACTION_NOTIFICATION_DISMISS";

    public static final String EXTRA_THREAD_ID = "extra_thread_id";
    public static final String EXTRA_RECIPIENT = "extra_recipient";
    public static final String EXTRA_SUBSCRIPTION_ID = "extra_subscription_id";
    public static final String EXTRA_NOTIFICATION_ID = "extra_notification_id";
    public static final String EXTRA_MESSAGE_KEY = "extra_message_key";

    private static volatile MessageNotificationManager instance;
    private static volatile long currentActiveThreadId = -1L;

    private final Context appContext;
    private final NotificationManagerCompat notificationManager;
    private final AppPreferences preferences;
    private final BlockedSenderRepository blockedRepo;
    private final ConversationMetadataRepository metadataRepo;
    private final ContactRepository contactRepo;
    private final SimManager simManager;

    // In-memory record of active notifications per thread for accurate group summaries
    private final Map<Long, ActiveConversationNotification> activeNotifications = new ConcurrentHashMap<>();

    public static class ActiveConversationNotification {
        public final long threadId;
        public final int notificationId;
        public final String title;
        public final String snippet;
        public final long timestamp;

        public ActiveConversationNotification(long threadId, int notificationId, String title, String snippet, long timestamp) {
            this.threadId = threadId;
            this.notificationId = notificationId;
            this.title = title;
            this.snippet = snippet;
            this.timestamp = timestamp;
        }
    }

    private MessageNotificationManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.notificationManager = NotificationManagerCompat.from(this.appContext);
        this.preferences = AppPreferences.getInstance(this.appContext);
        this.blockedRepo = BlockedSenderRepository.getInstance(this.appContext);
        this.metadataRepo = ConversationMetadataRepository.getInstance(this.appContext);
        this.contactRepo = ContactRepository.getInstance(this.appContext);
        this.simManager = SimManager.getInstance(this.appContext);

        NotificationChannelManager.initChannels(this.appContext);
    }

    public static MessageNotificationManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (MessageNotificationManager.class) {
                if (instance == null) {
                    instance = new MessageNotificationManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Sets the thread ID of the conversation currently displayed in the foreground.
     * When set, notifications for this thread are suppressed so the user is not disturbed.
     */
    public static void setCurrentActiveThreadId(long threadId) {
        currentActiveThreadId = threadId;
    }

    public static long getCurrentActiveThreadId() {
        return currentActiveThreadId;
    }

    /**
     * Generates a stable, collision-resistant 32-bit positive notification ID for a thread.
     */
    public static int getNotificationIdForThread(long threadId) {
        if (threadId <= 0) {
            return 999999;
        }
        return (int) ((threadId % 900000000L) + 1000L);
    }

    /**
     * Posts a notification for a newly received incoming SMS message.
     */
    public void notifyIncomingSms(
            long threadId,
            @NonNull String sender,
            @NonNull String body,
            long timestampMillis,
            int subscriptionId,
            @NonNull String messageKey
    ) {
        if (!shouldNotify(threadId, sender)) {
            return;
        }

        NotificationChannelManager.initChannels(appContext);

        ContactInfo contactInfo = contactRepo.resolveContact(sender);
        String senderName = contactInfo.getDisplayName() != null ? contactInfo.getDisplayName() : sender;

        NotificationPrivacyManager.DisplayContent content = NotificationPrivacyManager.resolveDisplayContent(
                appContext,
                senderName,
                body,
                sender,
                threadId
        );

        ConversationMetadataEntity meta = metadataRepo.getOrCreateMetadata(threadId);
        boolean isMuted = meta.isCurrentlyMuted();
        boolean isSilent = meta.isSilent();
        String channelId = NotificationChannelManager.getChannelForConversation(false, isMuted, isSilent);

        int notifId = getNotificationIdForThread(threadId);

        // 1. Build Person objects
        Person userPerson = new Person.Builder()
                .setName("Me")
                .setKey("user")
                .build();

        Person.Builder senderPersonBuilder = new Person.Builder()
                .setName(content.getTitle())
                .setKey(sender)
                .setUri(contactInfo.getLookupKey() != null ? "tel:" + sender : null);

        // Attach contact avatar if available
        if (!content.isContentMasked() && contactInfo.getPhotoUri() != null) {
            try {
                IconCompat icon = loadContactIcon(appContext, contactInfo.getPhotoUri());
                if (icon != null) {
                    senderPersonBuilder.setIcon(icon);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed loading contact photo for notification: " + e.getMessage());
            }
        }
        Person senderPerson = senderPersonBuilder.build();

        // 2. Build MessagingStyle
        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(userPerson)
                .setConversationTitle(null)
                .setGroupConversation(false)
                .addMessage(content.getBody(), timestampMillis > 0 ? timestampMillis : System.currentTimeMillis(), senderPerson);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_sms)
                .setStyle(messagingStyle)
                .setContentTitle(content.getTitle())
                .setContentText(content.getBody())
                .setWhen(timestampMillis > 0 ? timestampMillis : System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(appContext, R.color.md_theme_primary))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(GROUP_KEY_MESSAGES);

        // SubText: Dual SIM indicator
        if (preferences.isShowSimBadgeInNotifications() && simManager.isDualSimActive() && subscriptionId > 0) {
            SimInfo simInfo = simManager.getSimForSubscriptionId(subscriptionId);
            if (simInfo != null) {
                builder.setSubText(simInfo.getDisplayName());
            }
        }

        // Mute / Silent handling
        if (isMuted || isSilent) {
            builder.setSilent(true);
            builder.setOnlyAlertOnce(true);
        }

        // Lock-screen OTP and Privacy protection
        if (content.isOtp() && preferences.isHideOtpOnLockscreenEnabled()) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
            Notification publicVersion = new NotificationCompat.Builder(appContext, channelId)
                    .setSmallIcon(R.drawable.ic_sms)
                    .setContentTitle(content.getTitle())
                    .setContentText(NotificationPrivacyManager.getPublicLockscreenBody(appContext, true))
                    .setColor(ContextCompat.getColor(appContext, R.color.md_theme_primary))
                    .build();
            builder.setPublicVersion(publicVersion);
        } else if (content.isContentMasked()) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        }

        // Intents & Actions
        builder.setContentIntent(createContentIntent(threadId, sender, notifId));
        builder.setDeleteIntent(createDeleteIntent(threadId, notifId));
        boolean isPrivateConv = preferences.isConversationPrivate(threadId);
        if (!isPrivateConv) {
            builder.addAction(createQuickReplyAction(threadId, sender, subscriptionId, notifId));
        }
        builder.addAction(createMarkAsReadAction(threadId, notifId));

        // Post notification safely
        try {
            notificationManager.notify(notifId, builder.build());
            activeNotifications.put(threadId, new ActiveConversationNotification(threadId, notifId, content.getTitle(), content.getBody(), timestampMillis));
            updateGroupSummary();
            metadataRepo.updateNotificationState(threadId, notifId, messageKey, timestampMillis, false);
        } catch (SecurityException se) {
            Log.w(TAG, "Notification permission missing (POST_NOTIFICATIONS): " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error posting SMS notification for thread " + threadId, e);
        }
    }

    /**
     * Posts a notification for a newly downloaded incoming MMS message.
     */
    public void notifyIncomingMms(
            long threadId,
            @Nullable String sender,
            @Nullable String subject,
            @NonNull List<MmsAttachment> attachments,
            long timestampMillis,
            int subscriptionId,
            @NonNull String messageKey
    ) {
        String safeSender = (sender != null && !sender.trim().isEmpty()) ? sender : "Unknown";
        if (!shouldNotify(threadId, safeSender)) {
            return;
        }

        NotificationChannelManager.initChannels(appContext);

        ContactInfo contactInfo = contactRepo.resolveContact(safeSender);
        String senderName = contactInfo.getDisplayName() != null ? contactInfo.getDisplayName() : safeSender;

        // Resolve snippet description based on attachments and subject
        String mmsBody = subject != null && !subject.trim().isEmpty() ? subject : "";
        if (mmsBody.isEmpty()) {
            if (!attachments.isEmpty()) {
                MmsAttachment first = attachments.get(0);
                if (first.isImage()) {
                    mmsBody = appContext.getString(R.string.notification_mms_image);
                } else if (first.isVideo()) {
                    mmsBody = appContext.getString(R.string.notification_mms_video);
                } else if (first.isAudio()) {
                    mmsBody = appContext.getString(R.string.notification_mms_audio);
                } else {
                    mmsBody = appContext.getString(R.string.notification_new_message);
                }
            } else {
                mmsBody = appContext.getString(R.string.notification_new_message);
            }
        }

        NotificationPrivacyManager.DisplayContent content = NotificationPrivacyManager.resolveDisplayContent(
                appContext,
                senderName,
                mmsBody,
                safeSender,
                threadId
        );

        ConversationMetadataEntity meta = metadataRepo.getOrCreateMetadata(threadId);
        boolean isMuted = meta.isCurrentlyMuted();
        boolean isSilent = meta.isSilent();
        String channelId = NotificationChannelManager.getChannelForConversation(false, isMuted, isSilent);

        int notifId = getNotificationIdForThread(threadId);

        Person userPerson = new Person.Builder().setName("Me").setKey("user").build();
        Person.Builder senderPersonBuilder = new Person.Builder()
                .setName(content.getTitle())
                .setKey(safeSender)
                .setUri(contactInfo.getLookupKey() != null ? "tel:" + safeSender : null);

        Person senderPerson = senderPersonBuilder.build();

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(userPerson)
                .setGroupConversation(false)
                .addMessage(content.getBody(), timestampMillis > 0 ? timestampMillis : System.currentTimeMillis(), senderPerson);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_sms)
                .setStyle(messagingStyle)
                .setContentTitle(content.getTitle())
                .setContentText(content.getBody())
                .setWhen(timestampMillis > 0 ? timestampMillis : System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(appContext, R.color.md_theme_primary))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(GROUP_KEY_MESSAGES);

        if (preferences.isShowSimBadgeInNotifications() && simManager.isDualSimActive() && subscriptionId > 0) {
            SimInfo simInfo = simManager.getSimForSubscriptionId(subscriptionId);
            if (simInfo != null) {
                builder.setSubText(simInfo.getDisplayName());
            }
        }

        if (isMuted || isSilent) {
            builder.setSilent(true);
            builder.setOnlyAlertOnce(true);
        }

        builder.setContentIntent(createContentIntent(threadId, safeSender, notifId));
        builder.setDeleteIntent(createDeleteIntent(threadId, notifId));
        boolean isPrivateMmsConv = preferences.isConversationPrivate(threadId);
        if (!isPrivateMmsConv) {
            builder.addAction(createQuickReplyAction(threadId, safeSender, subscriptionId, notifId));
        }
        builder.addAction(createMarkAsReadAction(threadId, notifId));

        try {
            notificationManager.notify(notifId, builder.build());
            activeNotifications.put(threadId, new ActiveConversationNotification(threadId, notifId, content.getTitle(), content.getBody(), timestampMillis));
            updateGroupSummary();
            metadataRepo.updateNotificationState(threadId, notifId, messageKey, timestampMillis, false);
        } catch (SecurityException se) {
            Log.w(TAG, "Notification permission missing: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error posting MMS notification for thread " + threadId, e);
        }
    }

    /**
     * Posts an alert when an MMS message is waiting to be downloaded (e.g. roaming / auto-download disabled).
     */
    public void notifyMmsPendingDownload(
            long threadId,
            @Nullable String sender,
            long timestampMillis,
            int subscriptionId,
            @NonNull String messageKey
    ) {
        String safeSender = (sender != null && !sender.trim().isEmpty()) ? sender : "Unknown";
        if (!shouldNotify(threadId, safeSender)) {
            return;
        }

        NotificationChannelManager.initChannels(appContext);

        ContactInfo contactInfo = contactRepo.resolveContact(safeSender);
        String senderName = contactInfo.getDisplayName() != null ? contactInfo.getDisplayName() : safeSender;
        String title = senderName;
        String body = appContext.getString(R.string.notification_mms_pending);

        int notifId = getNotificationIdForThread(threadId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, NotificationChannelManager.CHANNEL_ID_MMS_STATUS)
                .setSmallIcon(R.drawable.ic_sms)
                .setContentTitle(title)
                .setContentText(body)
                .setWhen(timestampMillis > 0 ? timestampMillis : System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(appContext, R.color.md_theme_primary))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setGroup(GROUP_KEY_MESSAGES)
                .setContentIntent(createContentIntent(threadId, safeSender, notifId))
                .setDeleteIntent(createDeleteIntent(threadId, notifId));

        try {
            notificationManager.notify(notifId, builder.build());
            activeNotifications.put(threadId, new ActiveConversationNotification(threadId, notifId, title, body, timestampMillis));
            updateGroupSummary();
        } catch (Exception e) {
            Log.e(TAG, "Error posting MMS pending notification: " + e.getMessage());
        }
    }

    /**
     * Cancels an active notification for a specific conversation thread.
     */
    public void cancelConversationNotification(long threadId) {
        if (threadId <= 0) return;
        int notifId = getNotificationIdForThread(threadId);
        try {
            notificationManager.cancel(notifId);
            activeNotifications.remove(threadId);
            metadataRepo.setNotificationDismissed(threadId, true);
            updateGroupSummary();
        } catch (Exception e) {
            Log.w(TAG, "Error cancelling notification for thread " + threadId + ": " + e.getMessage());
        }
    }

    /**
     * Cancels all notifications posted by NextGen Messages.
     */
    public void cancelAll() {
        try {
            notificationManager.cancelAll();
            activeNotifications.clear();
        } catch (Exception ignored) {
        }
    }

    /**
     * Updates the summary group notification according to currently active conversation alerts.
     */
    private void updateGroupSummary() {
        if (activeNotifications.isEmpty()) {
            try {
                notificationManager.cancel(SUMMARY_NOTIFICATION_ID);
            } catch (Exception ignored) {
            }
            return;
        }

        int count = activeNotifications.size();
        String summaryTitle = appContext.getString(R.string.app_name);
        String summaryContent = count == 1
                ? appContext.getString(R.string.notification_group_summary_single, 1)
                : appContext.getString(R.string.notification_group_summary_multiple, count, count);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle()
                .setSummaryText(summaryContent);

        for (ActiveConversationNotification item : activeNotifications.values()) {
            inboxStyle.addLine(item.title + ": " + item.snippet);
        }

        Intent openAppIntent = new Intent(appContext, com.nextgen.messages.ui.main.MainActivity.class);
        PendingIntent summaryPendingIntent = PendingIntent.getActivity(
                appContext,
                SUMMARY_NOTIFICATION_ID,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(appContext, NotificationChannelManager.CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_sms)
                .setStyle(inboxStyle)
                .setContentTitle(summaryTitle)
                .setContentText(summaryContent)
                .setContentIntent(summaryPendingIntent)
                .setGroup(GROUP_KEY_MESSAGES)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(appContext, R.color.md_theme_primary));

        try {
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryBuilder.build());
        } catch (Exception ignored) {
        }
    }

    /**
     * Verifies whether an incoming message should generate a visible user notification.
     */
    private boolean shouldNotify(long threadId, @NonNull String sender) {
        if (!preferences.isNotificationsEnabled()) {
            return false;
        }

        if (!notificationManager.areNotificationsEnabled()) {
            return false;
        }

        // Suppress notification if user is actively in the conversation screen
        if (threadId > 0 && threadId == currentActiveThreadId) {
            Log.d(TAG, "Suppressing notification for active foreground thread " + threadId);
            return false;
        }

        // Blocked senders check
        if (blockedRepo.isBlocked(sender)) {
            Log.d(TAG, "Suppressing notification for blocked sender: " + sender);
            return false;
        }

        // Spam classification check
        if (threadId > 0) {
            ConversationMetadataEntity meta = metadataRepo.getOrCreateMetadata(threadId);
            if (meta.isSpam() || meta.isBlocked()) {
                Log.d(TAG, "Suppressing notification for spam/blocked thread " + threadId);
                return false;
            }
        }

        return true;
    }

    private PendingIntent createContentIntent(long threadId, String address, int notifId) {
        Intent tapIntent = new Intent(appContext, ConversationActivity.class);
        tapIntent.putExtra(ConversationActivity.EXTRA_THREAD_ID, threadId);
        tapIntent.putExtra(ConversationActivity.EXTRA_ADDRESS, address);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(appContext);
        stackBuilder.addNextIntentWithParentStack(tapIntent);

        return stackBuilder.getPendingIntent(
                notifId,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent createDeleteIntent(long threadId, int notifId) {
        Intent dismissIntent = new Intent(appContext, NotificationDismissReceiver.class);
        dismissIntent.setAction(ACTION_DISMISS);
        dismissIntent.putExtra(EXTRA_THREAD_ID, threadId);
        dismissIntent.putExtra(EXTRA_NOTIFICATION_ID, notifId);

        return PendingIntent.getBroadcast(
                appContext,
                notifId,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private NotificationCompat.Action createQuickReplyAction(long threadId, String recipient, int subscriptionId, int notifId) {
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(appContext.getString(R.string.notification_reply_hint))
                .build();

        Intent replyIntent = new Intent(appContext, QuickReplyReceiver.class);
        replyIntent.setAction(ACTION_QUICK_REPLY);
        replyIntent.putExtra(EXTRA_THREAD_ID, threadId);
        replyIntent.putExtra(EXTRA_RECIPIENT, recipient);
        replyIntent.putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId);
        replyIntent.putExtra(EXTRA_NOTIFICATION_ID, notifId);

        // RemoteInput requires FLAG_MUTABLE on Android 12+ (API 31+)
        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                appContext,
                notifId,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_send,
                appContext.getString(R.string.action_reply),
                replyPendingIntent
        )
        .addRemoteInput(remoteInput)
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        .setShowsUserInterface(false)
        .build();
    }

    private NotificationCompat.Action createMarkAsReadAction(long threadId, int notifId) {
        Intent markReadIntent = new Intent(appContext, MarkAsReadReceiver.class);
        markReadIntent.setAction(ACTION_MARK_READ);
        markReadIntent.putExtra(EXTRA_THREAD_ID, threadId);
        markReadIntent.putExtra(EXTRA_NOTIFICATION_ID, notifId);

        PendingIntent markReadPendingIntent = PendingIntent.getBroadcast(
                appContext,
                notifId,
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_mark_chat_read,
                appContext.getString(R.string.action_mark_read),
                markReadPendingIntent
        )
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
        .setShowsUserInterface(false)
        .build();
    }

    @Nullable
    private IconCompat loadContactIcon(@NonNull Context context, @NonNull Uri photoUri) {
        try (InputStream stream = context.getContentResolver().openInputStream(photoUri)) {
            if (stream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (bitmap != null) {
                    return IconCompat.createWithAdaptiveBitmap(bitmap);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
