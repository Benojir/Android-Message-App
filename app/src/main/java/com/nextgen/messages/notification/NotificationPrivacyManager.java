package com.nextgen.messages.notification;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.R;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.smartinbox.engine.ClassificationResult;
import com.nextgen.messages.smartinbox.engine.OtpClassifier;

/**
 * Handles privacy controls for message notifications.
 * Manages full preview, content masking, lock-screen visibility,
 * and confidential OTP protection.
 */
public class NotificationPrivacyManager {

    public static final String PRIVACY_FULL = "FULL";
    public static final String PRIVACY_HIDE_BODY = "HIDE_BODY";
    public static final String PRIVACY_HIDE_ALL = "HIDE_ALL";

    public static class DisplayContent {
        private final String title;
        private final String body;
        private final boolean isContentMasked;
        private final boolean isOtp;

        public DisplayContent(
                @NonNull String title,
                @NonNull String body,
                boolean isContentMasked,
                boolean isOtp
        ) {
            this.title = title;
            this.body = body;
            this.isContentMasked = isContentMasked;
            this.isOtp = isOtp;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getBody() {
            return body;
        }

        public boolean isContentMasked() {
            return isContentMasked;
        }

        public boolean isOtp() {
            return isOtp;
        }
    }

    /**
     * Resolves the presentation content based on user preferences and OTP classification.
     */
    @NonNull
    public static DisplayContent resolveDisplayContent(
            @NonNull Context context,
            @NonNull String senderDisplayName,
            @Nullable String rawBody,
            @Nullable String senderAddress
    ) {
        return resolveDisplayContent(context, senderDisplayName, rawBody, senderAddress, -1L);
    }

    /**
     * Resolves presentation content with thread-level private conversation awareness.
     */
    @NonNull
    public static DisplayContent resolveDisplayContent(
            @NonNull Context context,
            @NonNull String senderDisplayName,
            @Nullable String rawBody,
            @Nullable String senderAddress,
            long threadId
    ) {
        AppPreferences prefs = AppPreferences.getInstance(context);
        boolean isPrivate = threadId > 0 && prefs.isConversationPrivate(threadId);

        if (isPrivate) {
            return new DisplayContent(
                    senderDisplayName,
                    context.getString(R.string.notification_private_message_received),
                    true,
                    false
            );
        }

        String privacyLevel = prefs.getNotificationPrivacy();

        boolean isOtp = isOtpMessage(rawBody, senderAddress);
        String cleanBody = rawBody != null ? rawBody.trim() : "";

        // Level 3: Hide all sensitive info (Generic app name + generic message)
        if (PRIVACY_HIDE_ALL.equalsIgnoreCase(privacyLevel)) {
            return new DisplayContent(
                    context.getString(R.string.app_name),
                    context.getString(R.string.notification_generic_received),
                    true,
                    isOtp
            );
        }

        // Level 2: Hide message body (Sender shown, body masked)
        if (PRIVACY_HIDE_BODY.equalsIgnoreCase(privacyLevel)) {
            String maskedBody = isOtp
                    ? context.getString(R.string.notification_otp_hidden)
                    : context.getString(R.string.notification_hidden_content);
            return new DisplayContent(
                    senderDisplayName,
                    maskedBody,
                    true,
                    isOtp
            );
        }

        // Level 1: Full preview
        return new DisplayContent(
                senderDisplayName,
                cleanBody.isEmpty() ? context.getString(R.string.notification_new_message) : cleanBody,
                false,
                isOtp
        );
    }

    /**
     * Checks if a message body and sender match high-confidence OTP criteria.
     */
    public static boolean isOtpMessage(@Nullable String body, @Nullable String sender) {
        if (body == null || body.trim().isEmpty()) {
            return false;
        }
        ClassificationResult result = OtpClassifier.classify(body, sender);
        return result != null && "otp".equalsIgnoreCase(result.getCategoryKey());
    }

    /**
     * Generates a safe public version of the notification for display on the lock screen
     * when OTP hiding is enabled or content privacy is active.
     */
    @NonNull
    public static String getPublicLockscreenBody(@NonNull Context context, boolean isOtp) {
        if (isOtp) {
            return context.getString(R.string.notification_otp_hidden);
        }
        return context.getString(R.string.notification_generic_received);
    }
}
