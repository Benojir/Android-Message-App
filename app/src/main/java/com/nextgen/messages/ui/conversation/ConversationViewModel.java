package com.nextgen.messages.ui.conversation;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.nextgen.messages.data.model.MessageListItem;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.utils.DateFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ViewModel managing conversation history for a specific thread.
 * Strictly READ-ONLY for Step 3: does NOT mark messages as read or seen.
 */
public class ConversationViewModel extends AndroidViewModel {

    private final SmsRepository smsRepository;
    private long currentThreadId = -1L;

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
                List<MessageListItem> items = processMessagesWithDateHeaders(messages);
                messageItems.setValue(items);
            }

            @Override
            public void onError(Throwable throwable) {
                isLoading.setValue(false);
                errorMessage.setValue(throwable.getMessage());
            }
        });
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
