package com.nextgen.messages.ui.conversation;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.data.model.MessageListItem;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.data.repository.ConversationMetadataRepository;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.data.repository.StarredMessageRepository;
import com.nextgen.messages.security.SuspiciousLinkAnalyzer;
import com.nextgen.messages.smartinbox.db.ConversationMetadataEntity;
import com.nextgen.messages.smartinbox.db.SmartInboxDatabase;
import com.nextgen.messages.utils.AppExecutors;
import com.nextgen.messages.utils.DateFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ViewModel managing conversation history, message list projection with date headers,
 * read-state updates, safe undoable message/conversation deletion, and Step 8 enrichment
 * (starred states, link warning analysis, conversation spam/archive metadata).
 */
public class ConversationViewModel extends AndroidViewModel {

    private final SmsRepository smsRepository;
    private final StarredMessageRepository starredRepository;
    private final ConversationMetadataRepository metadataRepository;
    private final AppPreferences preferences;
    private long currentThreadId = -1L;

    private final List<SmsMessage> cachedRawMessages = new ArrayList<>();
    private final Set<Long> pendingDeletedMessageIds = new HashSet<>();
    private final Set<String> dismissedWarningKeys = new HashSet<>();

    private final MutableLiveData<List<MessageListItem>> messageItems = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);
    private final MutableLiveData<ConversationMetadataEntity> metadataLiveData = new MutableLiveData<>(null);

    public ConversationViewModel(@NonNull Application application) {
        super(application);
        this.smsRepository = SmsRepository.getInstance(application);
        this.starredRepository = StarredMessageRepository.getInstance(application);
        this.metadataRepository = ConversationMetadataRepository.getInstance(application);
        this.preferences = AppPreferences.getInstance(application);
    }

    public LiveData<List<MessageListItem>> getMessageItems() {
        return messageItems;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<ConversationMetadataEntity> getMetadataLiveData() {
        return metadataLiveData;
    }

    public long getCurrentThreadId() {
        return currentThreadId;
    }

    public void setThreadId(long threadId) {
        if (this.currentThreadId == threadId && !Boolean.TRUE.equals(isLoading.getValue())) {
            return;
        }
        this.currentThreadId = threadId;
        loadMessages();
        loadMetadata();
    }

    public void refresh() {
        if (currentThreadId > 0) {
            loadMessages();
            loadMetadata();
        }
    }

    public void loadMetadata() {
        if (currentThreadId > 0) {
            AppExecutors.getInstance().diskIO().execute(() -> {
                ConversationMetadataEntity meta = metadataRepository.getOrCreateMetadata(currentThreadId);
                metadataLiveData.postValue(meta);
            });
        }
    }

    /**
     * Marks all unread incoming messages in the active thread as read.
     */
    public void markThreadAsRead() {
        if (currentThreadId > 0) {
            smsRepository.markThreadMessagesAsRead(currentThreadId, null);
        }
    }

    /**
     * Marks the conversation thread as unread.
     */
    public void markThreadAsUnread(@Nullable SmsRepository.DataCallback<Boolean> callback) {
        if (currentThreadId > 0) {
            smsRepository.markThreadMessagesAsUnread(currentThreadId, callback);
        }
    }

    /**
     * Temporarily masks messages in the UI for the Undo duration.
     */
    public void markMessagesPendingDelete(@NonNull Set<Long> messageIds) {
        pendingDeletedMessageIds.addAll(messageIds);
        applyFilterAndPostItems();
    }

    /**
     * Re-shows masked messages if the user taps Undo.
     */
    public void cancelPendingDelete(@NonNull Set<Long> messageIds) {
        pendingDeletedMessageIds.removeAll(messageIds);
        applyFilterAndPostItems();
    }

    /**
     * Commits the permanent deletion of messages to the Telephony Provider.
     */
    public void commitPendingDelete(@NonNull Set<Long> messageIds, @Nullable SmsRepository.DataCallback<Integer> callback) {
        smsRepository.deleteMultipleMessages(messageIds, new SmsRepository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer rows) {
                pendingDeletedMessageIds.removeAll(messageIds);
                if (callback != null) {
                    callback.onSuccess(rows);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                // Restore items to UI on deletion failure
                pendingDeletedMessageIds.removeAll(messageIds);
                applyFilterAndPostItems();
                if (callback != null) {
                    callback.onError(throwable);
                }
            }
        });
    }

    /**
     * Deletes all SMS records for the current conversation thread.
     */
    public void deleteEntireConversation(@Nullable SmsRepository.DataCallback<Integer> callback) {
        if (currentThreadId > 0) {
            smsRepository.deleteConversationSms(currentThreadId, callback);
        }
    }

    /**
     * Toggles star status for a message.
     */
    public void toggleStar(@NonNull SmsMessage message) {
        starredRepository.toggleStar(message, this::loadMessages);
    }

    /**
     * Stars or unstars multiple selected messages.
     */
    public void toggleStarForSelected(@NonNull Set<Long> compositeIds) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            synchronized (cachedRawMessages) {
                for (SmsMessage msg : cachedRawMessages) {
                    if (compositeIds.contains(msg.getCompositeId())) {
                        if (msg.isStarred()) {
                            starredRepository.unstarMessage(msg.getMessageKey(), null);
                        } else {
                            starredRepository.starMessage(
                                    msg.getMessageKey(),
                                    msg.getThreadId(),
                                    msg.getId(),
                                    msg.isMms() ? "MMS" : "SMS",
                                    null
                            );
                        }
                    }
                }
            }
            AppExecutors.getInstance().mainThread().execute(this::loadMessages);
        });
    }

    public void dismissWarning(@NonNull SmsMessage message) {
        dismissedWarningKeys.add(message.getMessageKey());
        AppExecutors.getInstance().diskIO().execute(() -> {
            SmartInboxDatabase.getInstance(getApplication()).smartInboxDao()
                    .dismissWarning(message.getMessageKey());
        });
        applyFilterAndPostItems();
    }

    private void loadMessages() {
        if (currentThreadId <= 0) {
            messageItems.setValue(Collections.emptyList());
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);

        smsRepository.loadMessagesForThread(currentThreadId, new SmsRepository.DataCallback<List<SmsMessage>>() {
            @Override
            public void onSuccess(List<SmsMessage> messages) {
                isLoading.setValue(false);
                AppExecutors.getInstance().diskIO().execute(() -> {
                    Set<String> starredKeys = starredRepository.getStarredKeysForThreadSync(currentThreadId);
                    boolean linkWarningsEnabled = preferences.isSuspiciousLinkWarningsEnabled();

                    List<SmsMessage> enriched = new ArrayList<>(messages.size());
                    for (SmsMessage msg : messages) {
                        boolean isStarred = starredKeys.contains(msg.getMessageKey());
                        boolean hasSuspiciousLink = false;
                        String warningReason = null;

                        if (linkWarningsEnabled && !dismissedWarningKeys.contains(msg.getMessageKey())) {
                            SuspiciousLinkAnalyzer.AnalysisResult result = SuspiciousLinkAnalyzer.analyze(msg.getBody());
                            if (result.isSuspicious()) {
                                hasSuspiciousLink = true;
                                warningReason = result.getWarningReason();
                            }
                        }

                        enriched.add(msg.withStep8State(isStarred, hasSuspiciousLink, warningReason));
                    }

                    synchronized (cachedRawMessages) {
                        cachedRawMessages.clear();
                        cachedRawMessages.addAll(enriched);
                    }

                    AppExecutors.getInstance().mainThread().execute(() -> applyFilterAndPostItems());
                });
            }

            @Override
            public void onError(Throwable throwable) {
                isLoading.setValue(false);
                errorMessage.setValue(throwable.getMessage());
            }
        });
    }

    private void applyFilterAndPostItems() {
        List<SmsMessage> filtered = new ArrayList<>();
        synchronized (cachedRawMessages) {
            for (SmsMessage msg : cachedRawMessages) {
                if (!pendingDeletedMessageIds.contains(msg.getId())) {
                    filtered.add(msg);
                }
            }
        }
        List<MessageListItem> items = processMessagesWithDateHeaders(filtered);
        messageItems.setValue(items);
    }

    @NonNull
    private List<MessageListItem> processMessagesWithDateHeaders(@NonNull List<SmsMessage> messages) {
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<MessageListItem> items = new ArrayList<>(messages.size() + 8);
        long lastDayKey = -1L;

        for (SmsMessage msg : messages) {
            long dayKey = DateFormatter.getDayKey(msg.getDate());
            if (dayKey != lastDayKey) {
                String headerText = DateFormatter.formatDateHeader(msg.getDate());
                items.add(new MessageListItem.DateHeaderItem(headerText, dayKey));
                lastDayKey = dayKey;
            }
            items.add(new MessageListItem.MessageItem(msg));
        }

        return items;
    }
}
