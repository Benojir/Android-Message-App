package com.nextgen.messages.data.repository;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.smartinbox.db.SmartInboxDao;
import com.nextgen.messages.smartinbox.db.SmartInboxDatabase;
import com.nextgen.messages.smartinbox.db.StarredMessageEntity;
import com.nextgen.messages.utils.AppExecutors;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Repository managing starred messages using composite message keys ("SMS_<id>", "MMS_<id>")
 * to support both SMS and MMS without cross-type ID collisions.
 */
public class StarredMessageRepository {

    private static volatile StarredMessageRepository instance;
    private final SmartInboxDao dao;

    private StarredMessageRepository(@NonNull Context context) {
        this.dao = SmartInboxDatabase.getInstance(context).smartInboxDao();
    }

    public static StarredMessageRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (StarredMessageRepository.class) {
                if (instance == null) {
                    instance = new StarredMessageRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public boolean isStarred(@NonNull String messageKey) {
        return dao.isMessageStarred(messageKey);
    }

    public Set<String> getStarredKeysForThreadSync(long threadId) {
        return new HashSet<>(dao.getStarredKeysForThread(threadId));
    }

    public Set<String> getAllStarredKeysSync() {
        return new HashSet<>(dao.getAllStarredMessageKeys());
    }

    public LiveData<List<StarredMessageEntity>> getAllStarredMessagesLiveData() {
        return dao.getAllStarredMessagesLiveData();
    }

    public List<StarredMessageEntity> getAllStarredMessagesSync() {
        return dao.getAllStarredMessagesSync();
    }

    public void starMessage(
            @NonNull String messageKey,
            long threadId,
            long rawMessageId,
            @NonNull String messageType,
            @Nullable Runnable onComplete
    ) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            StarredMessageEntity entity = new StarredMessageEntity(
                    messageKey,
                    threadId,
                    rawMessageId,
                    messageType,
                    System.currentTimeMillis()
            );
            dao.insertStarredMessage(entity);
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    public void unstarMessage(@NonNull String messageKey, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            dao.deleteStarredMessage(messageKey);
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }

    public void toggleStar(@NonNull SmsMessage message, @Nullable Runnable onComplete) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String key = message.getMessageKey();
            if (dao.isMessageStarred(key)) {
                dao.deleteStarredMessage(key);
            } else {
                StarredMessageEntity entity = new StarredMessageEntity(
                        key,
                        message.getThreadId(),
                        message.getId(),
                        message.isMms() ? "MMS" : "SMS",
                        System.currentTimeMillis()
                );
                dao.insertStarredMessage(entity);
            }
            if (onComplete != null) {
                AppExecutors.getInstance().mainThread().execute(onComplete);
            }
        });
    }
}
