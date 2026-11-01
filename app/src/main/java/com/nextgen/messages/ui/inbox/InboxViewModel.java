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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ViewModel managing the Inbox state, querying real SMS conversations,
 * handling search queries, permission status, and multi-conversation management.
 */
public class InboxViewModel extends AndroidViewModel {

    private final SmsRepository smsRepository;
    private final com.nextgen.messages.smartinbox.SmartInboxRepository smartInboxRepository;
    private final com.nextgen.messages.data.local.AppPreferences appPreferences;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private final MutableLiveData<List<Conversation>> conversations = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isSmsPermissionGranted = new MutableLiveData<>(false);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);
    private final MutableLiveData<String> selectedCategory = new MutableLiveData<>("all");

    private final Set<Long> pendingDeletedThreadIds = new HashSet<>();
    private final Set<Long> categoryFilteredThreadIds = new HashSet<>();
    private List<Conversation> rawConversations = Collections.emptyList();

    public InboxViewModel(@NonNull Application application) {
        super(application);
        this.smsRepository = SmsRepository.getInstance(application);
        this.smartInboxRepository = com.nextgen.messages.smartinbox.SmartInboxRepository.getInstance(application);
        this.appPreferences = com.nextgen.messages.data.local.AppPreferences.getInstance(application);
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
            rawConversations = Collections.emptyList();
            publishFilteredConversations();
            isLoading.setValue(false);
        }
    }

    /**
     * Explicitly loads or reloads conversations from the repository.
     */
    public void loadConversations() {
        if (!smsRepository.hasReadSmsPermission()) {
            isSmsPermissionGranted.setValue(false);
            rawConversations = Collections.emptyList();
            publishFilteredConversations();
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
                rawConversations = result != null ? result : Collections.emptyList();

                if (appPreferences.isSmartInboxEnabled() && !appPreferences.isInitialScanCompleted()) {
                    smartInboxRepository.scanAndClassifyExistingMessages(new com.nextgen.messages.smartinbox.SmartInboxRepository.ScanProgressListener() {
                        @Override
                        public void onProgress(int processed, int total) {}

                        @Override
                        public void onCompleted(int totalClassified) {
                            appPreferences.setInitialScanCompleted(true);
                            String cat = selectedCategory.getValue();
                            if (cat != null && !"all".equals(cat)) {
                                setSelectedCategory(cat);
                            }
                        }

                        @Override
                        public void onError(Throwable error) {}
                    });
                }

                String currentCat = selectedCategory.getValue();
                if (currentCat != null && !"all".equals(currentCat)) {
                    setSelectedCategory(currentCat);
                } else {
                    publishFilteredConversations();
                }
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
                rawConversations = result != null ? result : Collections.emptyList();

                if (appPreferences.isSmartInboxEnabled() && !appPreferences.isInitialScanCompleted()) {
                    smartInboxRepository.scanAndClassifyExistingMessages(new com.nextgen.messages.smartinbox.SmartInboxRepository.ScanProgressListener() {
                        @Override
                        public void onProgress(int processed, int total) {}

                        @Override
                        public void onCompleted(int totalClassified) {
                            appPreferences.setInitialScanCompleted(true);
                            String cat = selectedCategory.getValue();
                            if (cat != null && !"all".equals(cat)) {
                                setSelectedCategory(cat);
                            }
                        }

                        @Override
                        public void onError(Throwable error) {}
                    });
                }

                String currentCat = selectedCategory.getValue();
                if (currentCat != null && !"all".equals(currentCat)) {
                    setSelectedCategory(currentCat);
                } else {
                    publishFilteredConversations();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                isLoading.setValue(false);
                errorMessage.setValue(throwable.getMessage());
            }
        });
    }

    /**
     * Masks conversation threads temporarily pending user confirmation or undo timeout.
     */
    public void markConversationsPendingDelete(@NonNull Set<Long> threadIds) {
        pendingDeletedThreadIds.addAll(threadIds);
        publishFilteredConversations();
    }

    /**
     * Restores conversation threads if user taps "Undo".
     */
    public void cancelPendingDelete(@NonNull Set<Long> threadIds) {
        pendingDeletedThreadIds.removeAll(threadIds);
        publishFilteredConversations();
    }

    /**
     * Permanently deletes conversation threads after undo window expires.
     */
    public void commitPendingDelete(@NonNull Set<Long> threadIds, @Nullable SmsRepository.DataCallback<Integer> callback) {
        smsRepository.deleteMultipleConversationsSms(threadIds, new SmsRepository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                pendingDeletedThreadIds.removeAll(threadIds);
                loadConversations();
                if (callback != null) {
                    callback.onSuccess(result);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                pendingDeletedThreadIds.removeAll(threadIds);
                publishFilteredConversations();
                if (callback != null) {
                    callback.onError(throwable);
                }
            }
        });
    }

    /**
     * Bulk marks conversation threads as read.
     */
    public void markConversationsAsRead(@NonNull Set<Long> threadIds, @Nullable SmsRepository.DataCallback<Integer> callback) {
        smsRepository.markMultipleThreadsAsRead(threadIds, new SmsRepository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                loadConversations();
                if (callback != null) {
                    callback.onSuccess(result);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (callback != null) {
                    callback.onError(throwable);
                }
            }
        });
    }

    /**
     * Marks a conversation thread as unread.
     */
    public void markConversationAsUnread(long threadId, @Nullable SmsRepository.DataCallback<Boolean> callback) {
        smsRepository.markThreadMessagesAsUnread(threadId, new SmsRepository.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadConversations();
                if (callback != null) {
                    callback.onSuccess(result);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (callback != null) {
                    callback.onError(throwable);
                }
            }
        });
    }

    public LiveData<String> getSelectedCategory() {
        return selectedCategory;
    }

    public void setSelectedCategory(@Nullable String categoryKey) {
        String clean = categoryKey != null ? categoryKey : "all";
        selectedCategory.setValue(clean);

        if ("all".equals(clean)) {
            categoryFilteredThreadIds.clear();
            publishFilteredConversations();
        } else {
            com.nextgen.messages.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                List<Long> threadIds = smartInboxRepository.getThreadIdsForCategory(clean);
                categoryFilteredThreadIds.clear();
                categoryFilteredThreadIds.addAll(threadIds);
                com.nextgen.messages.utils.AppExecutors.getInstance().mainThread().execute(this::publishFilteredConversations);
            });
        }
    }

    private void publishFilteredConversations() {
        String cat = selectedCategory.getValue();
        boolean filterCategory = cat != null && !"all".equals(cat);

        List<Conversation> filtered = new ArrayList<>();
        for (Conversation conv : rawConversations) {
            if (pendingDeletedThreadIds.contains(conv.getThreadId())) {
                continue;
            }
            if (filterCategory && !categoryFilteredThreadIds.contains(conv.getThreadId())) {
                continue;
            }
            filtered.add(conv);
        }
        conversations.setValue(filtered);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }
}
