package com.nextgen.messages.ui.inbox;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.Collections;
import java.util.List;

/**
 * ViewModel managing the Inbox UI state.
 * Emits an empty list of conversations in Step 1 to drive the clean empty state.
 */
public class InboxViewModel extends ViewModel {

    private final MutableLiveData<List<Object>> conversations = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public LiveData<List<Object>> getConversations() {
        return conversations;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
}
