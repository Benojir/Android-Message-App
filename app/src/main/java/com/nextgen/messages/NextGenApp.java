package com.nextgen.messages;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.color.DynamicColors;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.security.lock.AppLockManager;
import com.nextgen.messages.security.ui.AuthLockActivity;
import com.nextgen.messages.ui.conversation.ConversationActivity;
import com.nextgen.messages.utils.ThemeHelper;

/**
 * Custom Application class for NextGen Messages.
 * Initializes Material You Dynamic Colors, themes, notification channels,
 * and centralized security lifecycle gate for auto-lock and window privacy.
 */
public class NextGenApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        AppPreferences preferences = AppPreferences.getInstance(this);

        // Apply dynamic colors if enabled and available
        if (preferences.isDynamicColorsEnabled() && ThemeHelper.isDynamicColorSupported()) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }

        // Apply saved theme mode
        ThemeHelper.applyTheme(preferences.getThemeMode());

        // Initialize notification channels for Android 8.0+
        com.nextgen.messages.notification.NotificationChannelManager.initChannels(this);

        // Centralized security lifecycle listener
        registerSecurityLifecycle();
    }

    private void registerSecurityLifecycle() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private int runningActivities = 0;

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (runningActivities == 0) {
                    AppLockManager.getInstance(NextGenApp.this).onAppForegrounded();
                }
                runningActivities++;
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                // Enforce configured screenshot / recents privacy flags
                applyWindowPrivacyFlags(activity);

                // Enforce authentication gate
                if (!(activity instanceof AuthLockActivity)) {
                    if (AppLockManager.getInstance(NextGenApp.this).isLocked()) {
                        AuthLockActivity.start(activity);
                    }
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                runningActivities = Math.max(0, runningActivities - 1);
                if (runningActivities == 0) {
                    AppLockManager.getInstance(NextGenApp.this).setChangingConfigurations(activity.isChangingConfigurations());
                    AppLockManager.getInstance(NextGenApp.this).onAppBackgrounded();
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    private void applyWindowPrivacyFlags(@NonNull Activity activity) {
        if (activity instanceof AuthLockActivity) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            return;
        }

        AppPreferences prefs = AppPreferences.getInstance(this);
        String protection = prefs.getScreenshotProtection();

        if (AppPreferences.SCREENSHOT_ALL.equals(protection)) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else if (AppPreferences.SCREENSHOT_PRIVATE_ONLY.equals(protection)) {
            boolean isPrivateTarget = false;
            if (activity instanceof ConversationActivity) {
                long threadId = activity.getIntent().getLongExtra("thread_id", -1L);
                if (threadId > 0 && prefs.isConversationPrivate(threadId)) {
                    isPrivateTarget = true;
                }
            } else if (activity.getClass().getSimpleName().contains("Viewer")) {
                isPrivateTarget = true;
            }

            if (isPrivateTarget) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        } else {
            // Disabled
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
