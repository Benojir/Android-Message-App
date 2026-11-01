package com.nextgen.messages.ui.inbox;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.repository.SmsRepository;
import java.util.Collections;
import java.util.List;

/**
 * ViewModel managing the Inbox state, querying real SMS conversations,
 * and handling search queries and permission status.
 */
public class InboxViewModel extends AndroidViewModel {

    private final SmsRepository smsRepository;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private final MutableLiveData<List<Conversation>> conversations = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isSmsPermissionGranted = new MutableLiveData<>(false);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    public InboxViewModel(@NonNull Application application) {
        super(application);
        this.smsRepository = SmsRepository.getInstance(application);
        checkPermissionAndLoad();
    }

    public LiveData<List<Conversation>> getConversations() {
        return conversations;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getIsSmsPermissionGranted() {
        return isSmsPermissionGranted;
    }

    public LiveData<String> getSearchQuery() {
        return searchQuery;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Checks runtime SMS permission and triggers load if granted.
     */
    public void checkPermissionAndLoad() {
        boolean hasPermission = smsRepository.hasReadSmsPermission();
        isSmsPermissionGranted.setValue(hasPermission);
        if (hasPermission) {
            loadConversations();
        } else {
            conversations.setValue(Collections.emptyList());
            isLoading.setValue(false);
        }
    }

    /**
     * Explicitly loads or reloads conversations from the repository.
     */
    public void loadConversations() {
        if (!smsRepository.hasReadSmsPermission()) {
            isSmsPermissionGranted.setValue(false);
            conversations.setValue(Collections.emptyList());
            return;
        }

        isSmsPermissionGranted.setValue(true);
        String currentQuery = searchQuery.getValue();
        if (currentQuery != null && !currentQuery.trim().isEmpty()) {
            performSearch(currentQuery.trim());
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);

        smsRepository.loadConversations(new SmsRepository.DataCallback<List<Conversation>>() {
            @Override
            public void onSuccess(List<Conversation> result) {
                isLoading.setValue(false);
                conversations.setValue(result);
            }

            @Override
            public void onError(Throwable throwable) {
                isLoading.setValue(false);
                errorMessage.setValue(throwable.getMessage());
            }
        });
    }

    /**
     * Schedules a debounced search (300ms) across conversations.
     */
    public void onSearchQueryChanged(@Nullable String query) {
        String clean = query != null ? query : "";
        searchQuery.setValue(clean);

        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }

        searchRunnable = () -> {
            if (clean.trim().isEmpty()) {
                loadConversations();
            } else {
                performSearch(clean.trim());
            }
        };
        searchHandler.postDelayed(searchRunnable, 300L);
    }

    private void performSearch(@NonNull String query) {
        if (!smsRepository.hasReadSmsPermission()) {
            return;
        }
        isLoading.setValue(true);
        errorMessage.setValue(null);

        smsRepository.searchConversations(query, new SmsRepository.DataCallback<List<Conversation>>() {
            @Override
            public void onSuccess(List<Conversation> result) {
                isLoading.setValue(false);
                conversations.setValue(result);
            }

            @Override
            public void onError(Throwable throwable) {
                isLoading.setValue(false);
                errorMessage.setValue(throwable.getMessage());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }
}
