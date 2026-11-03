package com.nextgen.messages.security.privacy;

import android.content.Context;
import androidx.annotation.NonNull;
import com.nextgen.messages.data.local.AppPreferences;
import java.util.Set;

/**
 * Manages private conversation access controls.
 *
 * NOTE (Section 10 Privacy Notice):
 * Private Conversation is an application-level privacy feature that requires authentication to open,
 * hides message snippets in the inbox, and suppresses previews in notifications.
 * Underlying SMS/MMS records remain within the Android Telephony Provider according to Android
 * permission and default SMS rules.
 */
public class PrivateConversationManager {

    private static volatile PrivateConversationManager instance;
    private final AppPreferences preferences;

    private PrivateConversationManager(@NonNull Context context) {
        this.preferences = AppPreferences.getInstance(context.getApplicationContext());
    }

    public static PrivateConversationManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (PrivateConversationManager.class) {
                if (instance == null) {
                    instance = new PrivateConversationManager(context);
                }
            }
        }
        return instance;
    }

    public boolean isPrivate(long threadId) {
        return preferences.isConversationPrivate(threadId);
    }

    public void setPrivate(long threadId, boolean isPrivate) {
        preferences.setConversationPrivate(threadId, isPrivate);
    }

    @NonNull
    public Set<String> getPrivateThreadIds() {
        return preferences.getPrivateThreadIds();
    }

    public int getPrivateConversationCount() {
        return preferences.getPrivateThreadIds().size();
    }
}
