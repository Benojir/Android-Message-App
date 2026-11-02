package com.nextgen.messages.data.repository;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.smartinbox.db.ConversationMetadataEntity;
import com.nextgen.messages.smartinbox.db.SmartInboxDao;
import com.nextgen.messages.smartinbox.db.SmartInboxDatabase;
import com.nextgen.messages.smartinbox.db.SpamFeedbackEntity;
import com.nextgen.messages.utils.AppExecutors;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository coordinating thread-level state: pinned, archived, blocked, and spam classification.
 * Stored safely in local Room metadata without altering Android Telephony provider rows.
 */
public class ConversationMetadataRepository {

    private static volatile ConversationMetadataRepository instance;
    private final Context context;
    private final SmartInboxDao dao;
    private final AppPreferences preferences;
    private final BlockedSenderRepository blockedRepo;

    private ConversationMetadataRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.dao = SmartInboxDatabase.getInstance(this.context).smartInboxDao();
        this.preferences = AppPreferences.getInstance(this.context);
        this.blockedRepo = BlockedSenderRepository.getInstance(this.context);
    }

    public static ConversationMetadataRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ConversationMetadataRepository.class) {
                if (instance == null) {
                    instance = new ConversationMetadataRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public LiveData<List<ConversationMetadataEntity>> getAllMetadataLiveData() {
        return dao.getAllMetadataLiveData();
    }

    public List<ConversationMetadataEntity> getAllMetadataSync() {
        return dao.getAllMetadataSync();
    }

    @NonNull
    public Map<Long, ConversationMetadataEntity> getMetadataMapSync() {
        List<ConversationMetadataEntity> list = dao.getAllMetadataSync();
        Map<Long, ConversationMetadataEntity> map = new HashMap<>();
        for (ConversationMetadataEntity entity : list) {
            map.put(entity.getThreadId(), entity);
        }
        return map;
    }

    @NonNull
    public ConversationMetadataEntity getOrCreateMetadata(long threadId) {
        ConversationMetadataEntity meta = dao.getMetadataForThread(threadId);
        if (meta == null) {
            meta = ConversationMetadataEntity.defaultForThread(threadId);
            dao.insertMetadata(meta);
        }
        return meta;
    }

    public void setPinned(long threadId, boolean isPinned, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ConversationMetadataEntity meta = getOrCreateMetadata(threadId);
            meta.setPinned(isPinned);
            meta.setPinnedAt(isPinned ? System.currentTimeMillis() : 0L);
            dao.insertMetadata(meta);
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    public void setArchived(long threadId, boolean isArchived, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ConversationMetadataEntity meta = getOrCreateMetadata(threadId);
            meta.setArchived(isArchived);
            meta.setArchivedAt(isArchived ? System.currentTimeMillis() : 0L);
            // If archiving a pinned conversation, unpin it
            if (isArchived && meta.isPinned()) {
                meta.setPinned(false);
                meta.setPinnedAt(0L);
            }
            dao.insertMetadata(meta);
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    public void blockConversation(long threadId, @Nullable String address, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ConversationMetadataEntity meta = getOrCreateMetadata(threadId);
            meta.setBlocked(true);
            meta.setSpam(true);
            dao.insertMetadata(meta);

            if (address != null && !address.trim().isEmpty()) {
                blockedRepo.blockSender(address, "User blocked conversation", null);
            }
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    public void unblockConversation(long threadId, @Nullable String address, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ConversationMetadataEntity meta = getOrCreateMetadata(threadId);
            meta.setBlocked(false);
            meta.setSpam(false);
            dao.insertMetadata(meta);

            if (address != null && !address.trim().isEmpty()) {
                blockedRepo.unblockSender(address, null);
            }
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    public void reportSpam(long threadId, @Nullable String address, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ConversationMetadataEntity meta = getOrCreateMetadata(threadId);
            meta.setSpam(true);
            dao.insertMetadata(meta);

            if (address != null && !address.trim().isEmpty()) {
                String normalized = BlockedSenderRepository.normalizeIdentifier(address);
                SpamFeedbackEntity feedback = SpamFeedbackEntity.create(
                        address,
                        normalized,
                        threadId,
                        null,
                        SpamFeedbackEntity.TYPE_REPORT_SPAM,
                        System.currentTimeMillis()
                );
                dao.insertSpamFeedback(feedback);
            }
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    public void markNotSpam(long threadId, @Nullable String address, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ConversationMetadataEntity meta = getOrCreateMetadata(threadId);
            meta.setSpam(false);
            meta.setBlocked(false);
            dao.insertMetadata(meta);

            if (address != null && !address.trim().isEmpty()) {
                String normalized = BlockedSenderRepository.normalizeIdentifier(address);
                SpamFeedbackEntity feedback = SpamFeedbackEntity.create(
                        address,
                        normalized,
                        threadId,
                        null,
                        SpamFeedbackEntity.TYPE_NOT_SPAM,
                        System.currentTimeMillis()
                );
                dao.insertSpamFeedback(feedback);
                blockedRepo.unblockSender(address, null);
            }
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    /**
     * Checks if the thread is archived and automatically unarchives it if auto-unarchive is enabled.
     * Called when a new incoming message arrives.
     *
     * @return true if the conversation was unarchived, false otherwise.
     */
    public boolean checkAndAutoUnarchive(long threadId) {
        if (!preferences.isAutoUnarchiveEnabled()) {
            return false;
        }
        ConversationMetadataEntity meta = dao.getMetadataForThread(threadId);
        if (meta != null && meta.isArchived()) {
            meta.setArchived(false);
            meta.setArchivedAt(0L);
            dao.insertMetadata(meta);
            return true;
        }
        return false;
    }

    /**
     * Checks if a conversation thread is currently muted.
     */
    public boolean isConversationMuted(long threadId) {
        if (threadId <= 0) return false;
        ConversationMetadataEntity meta = dao.getMetadataForThread(threadId);
        return meta != null && meta.isCurrentlyMuted();
    }

    /**
     * Mutes a conversation thread for a given duration in milliseconds (or Long.MAX_VALUE for indefinite).
     */
    public void muteConversation(long threadId, long durationMillis, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            long mutedUntil = (durationMillis == Long.MAX_VALUE) ? Long.MAX_VALUE : (System.currentTimeMillis() + durationMillis);
            ConversationMetadataEntity meta = getOrCreateMetadata(threadId);
            meta.setMutedUntil(mutedUntil);
            dao.insertMetadata(meta);
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    /**
     * Unmutes a conversation thread.
     */
    public void unmuteConversation(long threadId, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ConversationMetadataEntity meta = getOrCreateMetadata(threadId);
            meta.setMutedUntil(0L);
            dao.insertMetadata(meta);
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    /**
     * Sets whether a conversation thread is silent (suppressing sound/vibration).
     */
    public void setConversationSilent(long threadId, boolean isSilent, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ConversationMetadataEntity meta = getOrCreateMetadata(threadId);
            meta.setSilent(isSilent);
            dao.insertMetadata(meta);
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    /**
     * Updates notification tracking state for a thread.
     */
    public void updateNotificationState(long threadId, int notifId, @Nullable String messageKey, long timestamp, boolean isDismissed) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            dao.updateNotificationState(threadId, notifId, messageKey, timestamp, isDismissed);
        });
    }

    /**
     * Updates dismissed state when user swipes notification away.
     */
    public void setNotificationDismissed(long threadId, boolean isDismissed) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            dao.updateDismissedState(threadId, isDismissed);
        });
    }
}
