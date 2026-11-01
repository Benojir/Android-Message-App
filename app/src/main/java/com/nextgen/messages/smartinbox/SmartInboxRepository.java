package com.nextgen.messages.smartinbox;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.data.model.ContactInfo;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.data.repository.ContactRepository;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.smartinbox.db.ClassificationRuleEntity;
import com.nextgen.messages.smartinbox.db.MessageCategoryEntity;
import com.nextgen.messages.smartinbox.db.MessageClassificationEntity;
import com.nextgen.messages.smartinbox.db.SenderCategoryEntity;
import com.nextgen.messages.smartinbox.db.SmartInboxDao;
import com.nextgen.messages.smartinbox.db.SmartInboxDatabase;
import com.nextgen.messages.smartinbox.engine.ClassificationEngine;
import com.nextgen.messages.smartinbox.engine.ClassificationResult;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Primary repository coordinating Room persistence, classification engine execution,
 * category counts, and batch background scans for the Smart Inbox.
 */
public class SmartInboxRepository {

    private static volatile SmartInboxRepository instance;
    private final Context appContext;
    private final SmartInboxDao dao;
    private final ContactRepository contactRepository;
    private final SmsRepository smsRepository;
    private final AppPreferences appPreferences;
    private final AppExecutors executors;

    public interface ScanProgressListener {
        void onProgress(int processed, int total);
        void onCompleted(int totalClassified);
        void onError(Throwable error);
    }

    private SmartInboxRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = SmartInboxDatabase.getInstance(context).smartInboxDao();
        this.contactRepository = ContactRepository.getInstance(context);
        this.smsRepository = SmsRepository.getInstance(context);
        this.appPreferences = AppPreferences.getInstance(context);
        this.executors = AppExecutors.getInstance();
    }

    public static SmartInboxRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SmartInboxRepository.class) {
                if (instance == null) {
                    instance = new SmartInboxRepository(context);
                }
            }
        }
        return instance;
    }

    public SmartInboxDao getDao() {
        return dao;
    }

    // --- Message Classification ---

    @NonNull
    public ClassificationResult classifyMessageSync(@NonNull SmsMessage message) {
        String messageKey = MessageClassificationEntity.buildMessageKey(message.isMms(), message.getId());

        // Check if message already has a manual override in Room
        MessageClassificationEntity existing = dao.getClassificationForMessage(messageKey);
        if (existing != null && MessageClassificationEntity.SOURCE_MANUAL.equals(existing.getClassificationSource())) {
            return new ClassificationResult(existing.getCategoryKey(), existing.getConfidence(), existing.getClassificationSource(), existing.getReason());
        }

        // Fetch sender override if any
        String sender = message.getAddress();
        SenderCategoryEntity senderOverride = null;
        if (sender != null && !sender.trim().isEmpty()) {
            senderOverride = dao.getSenderCategory(sender.trim().toUpperCase());
        }

        // Fetch user custom rules
        List<ClassificationRuleEntity> rules = dao.getEnabledRulesOrderedByPriority();

        // Resolve contact
        ContactInfo contactInfo = sender != null ? contactRepository.resolveContact(sender) : null;

        // Run engine
        ClassificationResult result = ClassificationEngine.classify(
                message.getBody(),
                sender,
                contactInfo,
                existing,
                senderOverride,
                rules
        );

        // Persist to Room
        long now = System.currentTimeMillis();
        MessageClassificationEntity entity = new MessageClassificationEntity(
                existing != null ? existing.getId() : 0,
                messageKey,
                message.getThreadId(),
                message.getId(),
                message.isMms() ? "MMS" : "SMS",
                result.getCategoryKey(),
                result.getSource(),
                result.getConfidence(),
                result.getReason(),
                now,
                now
        );
        dao.insertClassification(entity);

        return result;
    }

    public void classifyBatchSync(@NonNull List<SmsMessage> messages) {
        if (messages.isEmpty()) return;

        List<ClassificationRuleEntity> rules = dao.getEnabledRulesOrderedByPriority();
        List<SenderCategoryEntity> senderOverrides = dao.getAllSenderCategories();
        Map<String, SenderCategoryEntity> senderMap = new HashMap<>();
        for (SenderCategoryEntity so : senderOverrides) {
            senderMap.put(so.getSenderIdentifier(), so);
        }

        List<MessageClassificationEntity> entitiesToSave = new ArrayList<>(messages.size());
        long now = System.currentTimeMillis();

        for (SmsMessage msg : messages) {
            String messageKey = MessageClassificationEntity.buildMessageKey(msg.isMms(), msg.getId());
            MessageClassificationEntity existing = dao.getClassificationForMessage(messageKey);
            if (existing != null && MessageClassificationEntity.SOURCE_MANUAL.equals(existing.getClassificationSource())) {
                continue; // Do not overwrite manual overrides
            }

            String sender = msg.getAddress();
            SenderCategoryEntity sOverride = (sender != null) ? senderMap.get(sender.trim().toUpperCase()) : null;
            ContactInfo cInfo = (sender != null) ? contactRepository.resolveContact(sender) : null;

            ClassificationResult result = ClassificationEngine.classify(
                    msg.getBody(),
                    sender,
                    cInfo,
                    existing,
                    sOverride,
                    rules
            );

            entitiesToSave.add(new MessageClassificationEntity(
                    existing != null ? existing.getId() : 0,
                    messageKey,
                    msg.getThreadId(),
                    msg.getId(),
                    msg.isMms() ? "MMS" : "SMS",
                    result.getCategoryKey(),
                    result.getSource(),
                    result.getConfidence(),
                    result.getReason(),
                    now,
                    now
            ));
        }

        if (!entitiesToSave.isEmpty()) {
            dao.insertClassifications(entitiesToSave);
        }
    }

    public void scanAndClassifyExistingMessages(@Nullable ScanProgressListener listener) {
        executors.diskIO().execute(() -> {
            try {
                List<Conversation> conversations = smsRepository.getConversationsSync();
                int totalThreads = conversations.size();
                int processedThreads = 0;
                int totalClassified = 0;

                for (Conversation conv : conversations) {
                    List<SmsMessage> messages = smsRepository.getMessagesForThreadSync(conv.getThreadId());
                    classifyBatchSync(messages);
                    totalClassified += messages.size();
                    processedThreads++;

                    if (listener != null) {
                        final int p = processedThreads;
                        final int t = totalThreads;
                        executors.mainThread().execute(() -> listener.onProgress(p, t));
                    }
                }

                if (listener != null) {
                    final int count = totalClassified;
                    executors.mainThread().execute(() -> listener.onCompleted(count));
                }
            } catch (Throwable t) {
                if (listener != null) {
                    executors.mainThread().execute(() -> listener.onError(t));
                }
            }
        });
    }

    public void reclassifyAll(@Nullable Runnable onComplete) {
        executors.diskIO().execute(() -> {
            dao.clearAutomaticClassifications();
            scanAndClassifyExistingMessages(new ScanProgressListener() {
                @Override
                public void onProgress(int processed, int total) {}

                @Override
                public void onCompleted(int totalClassified) {
                    if (onComplete != null) {
                        executors.mainThread().execute(onComplete);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    if (onComplete != null) {
                        executors.mainThread().execute(onComplete);
                    }
                }
            });
        });
    }

    public void resetAutomaticClassifications(@Nullable Runnable onComplete) {
        executors.diskIO().execute(() -> {
            dao.clearAutomaticClassifications();
            if (onComplete != null) {
                executors.mainThread().execute(onComplete);
            }
        });
    }

    // --- Manual Overrides ---

    public void setManualMessageCategory(
            long threadId,
            long rawId,
            boolean isMms,
            @NonNull String categoryKey,
            @Nullable Runnable onComplete
    ) {
        executors.diskIO().execute(() -> {
            String messageKey = MessageClassificationEntity.buildMessageKey(isMms, rawId);
            long now = System.currentTimeMillis();
            MessageClassificationEntity existing = dao.getClassificationForMessage(messageKey);

            MessageClassificationEntity entity = new MessageClassificationEntity(
                    existing != null ? existing.getId() : 0,
                    messageKey,
                    threadId,
                    rawId,
                    isMms ? "MMS" : "SMS",
                    categoryKey,
                    MessageClassificationEntity.SOURCE_MANUAL,
                    1.0f,
                    "User manual category override",
                    now,
                    now
            );
            dao.insertClassification(entity);

            if (onComplete != null) {
                executors.mainThread().execute(onComplete);
            }
        });
    }

    public void setManualSenderCategory(@NonNull String sender, @NonNull String categoryKey, @Nullable Runnable onComplete) {
        executors.diskIO().execute(() -> {
            String identifier = sender.trim().toUpperCase();
            SenderCategoryEntity entity = new SenderCategoryEntity(identifier, categoryKey, true, System.currentTimeMillis());
            dao.insertSenderCategory(entity);

            if (onComplete != null) {
                executors.mainThread().execute(onComplete);
            }
        });
    }

    // --- Custom Categories Management ---

    public LiveData<List<MessageCategoryEntity>> getAllCategoriesLiveData() {
        return dao.getAllCategoriesLiveData();
    }

    public List<MessageCategoryEntity> getAllCategoriesSync() {
        return dao.getAllCategoriesSync();
    }

    public void createCustomCategory(@NonNull String name, @NonNull String iconRes, @NonNull String colorHex, @Nullable Runnable onComplete) {
        executors.diskIO().execute(() -> {
            MessageCategoryEntity entity = MessageCategoryEntity.createCustom(name, iconRes, colorHex);
            dao.insertCategory(entity);
            if (onComplete != null) {
                executors.mainThread().execute(onComplete);
            }
        });
    }

    public void deleteCustomCategory(@NonNull String categoryKey, @Nullable Runnable onComplete) {
        executors.diskIO().execute(() -> {
            // Safe deletion: reassign all classified messages from this custom category to "other"
            dao.reassignCategory(categoryKey, "other");
            dao.deleteCustomCategoryByKey(categoryKey);
            if (onComplete != null) {
                executors.mainThread().execute(onComplete);
            }
        });
    }

    // --- Classification Rules ---

    public LiveData<List<ClassificationRuleEntity>> getAllRulesLiveData() {
        return dao.getAllRulesLiveData();
    }

    public void addRule(
            @NonNull String name,
            @NonNull String type,
            @NonNull String value,
            @NonNull String targetCategoryKey,
            int priority,
            @Nullable Runnable onComplete
    ) {
        executors.diskIO().execute(() -> {
            ClassificationRuleEntity rule = ClassificationRuleEntity.create(name, type, value, targetCategoryKey, priority);
            dao.insertRule(rule);
            if (onComplete != null) {
                executors.mainThread().execute(onComplete);
            }
        });
    }

    public void deleteRule(long ruleId, @Nullable Runnable onComplete) {
        executors.diskIO().execute(() -> {
            dao.deleteRuleById(ruleId);
            if (onComplete != null) {
                executors.mainThread().execute(onComplete);
            }
        });
    }

    public void toggleRule(@NonNull ClassificationRuleEntity rule, boolean enabled, @Nullable Runnable onComplete) {
        executors.diskIO().execute(() -> {
            rule.setEnabled(enabled);
            rule.setUpdatedAt(System.currentTimeMillis());
            dao.updateRule(rule);
            if (onComplete != null) {
                executors.mainThread().execute(onComplete);
            }
        });
    }

    // --- Thread & Category Queries ---

    @NonNull
    public List<Long> getThreadIdsForCategory(@NonNull String categoryKey) {
        return dao.getThreadIdsForCategory(categoryKey);
    }

    @Nullable
    public MessageClassificationEntity getClassificationForMessage(@NonNull String messageKey) {
        return dao.getClassificationForMessage(messageKey);
    }

    /**
     * Computes live total messages and unread counts for all categories.
     */
    @NonNull
    public Map<String, CategoryCounts> getCategoryCountsSync() {
        Map<String, CategoryCounts> counts = new HashMap<>();
        List<MessageCategoryEntity> categories = dao.getAllCategoriesSync();
        for (MessageCategoryEntity cat : categories) {
            counts.put(cat.getCategoryKey(), new CategoryCounts(0, 0, 0));
        }

        List<Conversation> conversations = smsRepository.getConversationsSync();
        int totalInboxMessages = 0;
        int totalInboxUnread = 0;

        for (Conversation conv : conversations) {
            totalInboxMessages += conv.getMessageCount();
            totalInboxUnread += conv.getUnreadCount();

            List<MessageClassificationEntity> list = dao.getClassificationsForThread(conv.getThreadId());
            Set<String> threadCategories = new HashSet<>();
            for (MessageClassificationEntity ce : list) {
                threadCategories.add(ce.getCategoryKey());
                CategoryCounts cc = counts.get(ce.getCategoryKey());
                if (cc != null) {
                    cc.messageCount++;
                }
            }

            // Distribute conversation and unread counts across matched categories
            for (String catKey : threadCategories) {
                CategoryCounts cc = counts.get(catKey);
                if (cc != null) {
                    cc.conversationCount++;
                    if (conv.hasUnread()) {
                        cc.unreadCount += conv.getUnreadCount();
                    }
                }
            }
        }

        // Set 'all' category metrics
        CategoryCounts allCounts = counts.get("all");
        if (allCounts != null) {
            allCounts.messageCount = totalInboxMessages;
            allCounts.unreadCount = totalInboxUnread;
            allCounts.conversationCount = conversations.size();
        }

        return counts;
    }

    public static class CategoryCounts {
        public int messageCount;
        public int unreadCount;
        public int conversationCount;

        public CategoryCounts(int messageCount, int unreadCount, int conversationCount) {
            this.messageCount = messageCount;
            this.unreadCount = unreadCount;
            this.conversationCount = conversationCount;
        }
    }
}
