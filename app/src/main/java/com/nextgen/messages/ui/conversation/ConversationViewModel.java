package com.nextgen.messages.ui.conversation;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.nextgen.messages.data.model.MessageListItem;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.utils.DateFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ViewModel managing conversation history, message list projection with date headers,
 * read-state updates, and safe undoable message/conversation deletion.
 */
public class ConversationViewModel extends AndroidViewModel {

    private final SmsRepository smsRepository;
    private long currentThreadId = -1L;

    private final List<SmsMessage> cachedRawMessages = new ArrayList<>();
    private final Set<Long> pendingDeletedMessageIds = new HashSet<>();

    private final MutableLiveData<List<MessageListItem>> messageItems = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    public ConversationViewModel(@NonNull Application application) {
        super(application);
        this.smsRepository = SmsRepository.getInstance(application);
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

    public long getCurrentThreadId() {
        return currentThreadId;
    }

    public void setThreadId(long threadId) {
        if (this.currentThreadId == threadId && !Boolean.TRUE.equals(isLoading.getValue())) {
            return;
        }
        this.currentThreadId = threadId;
        loadMessages();
    }

    public void refresh() {
        if (currentThreadId > 0) {
            loadMessages();
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
                synchronized (cachedRawMessages) {
                    cachedRawMessages.clear();
                    cachedRawMessages.addAll(messages);
                }
                applyFilterAndPostItems();
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
