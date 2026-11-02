package com.nextgen.messages.smartinbox.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object providing high-performance, indexed queries for message classifications,
 * categories, rules, sender overrides, conversation metadata (archive/pin/block/spam),
 * starred messages, and security warnings.
 */
@Dao
public interface SmartInboxDao {

    // --- Categories ---

    @Query("SELECT * FROM message_categories ORDER BY display_order ASC, name ASC")
    LiveData<List<MessageCategoryEntity>> getAllCategoriesLiveData();

    @Query("SELECT * FROM message_categories ORDER BY display_order ASC, name ASC")
    List<MessageCategoryEntity> getAllCategoriesSync();

    @Nullable
    @Query("SELECT * FROM message_categories WHERE category_key = :key LIMIT 1")
    MessageCategoryEntity getCategoryByKey(@NonNull String key);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertCategory(MessageCategoryEntity category);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertCategories(List<MessageCategoryEntity> categories);

    @Update
    void updateCategory(MessageCategoryEntity category);

    @Delete
    void deleteCategory(MessageCategoryEntity category);

    @Query("DELETE FROM message_categories WHERE category_key = :key AND is_built_in = 0")
    int deleteCustomCategoryByKey(@NonNull String key);

    // --- Message Classifications ---

    @Nullable
    @Query("SELECT * FROM message_classifications WHERE message_key = :messageKey LIMIT 1")
    MessageClassificationEntity getClassificationForMessage(@NonNull String messageKey);

    @Query("SELECT * FROM message_classifications WHERE thread_id = :threadId")
    List<MessageClassificationEntity> getClassificationsForThread(long threadId);

    @Query("SELECT * FROM message_classifications")
    List<MessageClassificationEntity> getAllClassificationsSync();

    @Query("SELECT DISTINCT thread_id FROM message_classifications WHERE category_key = :categoryKey")
    List<Long> getThreadIdsForCategory(@NonNull String categoryKey);

    @Query("SELECT message_key FROM message_classifications WHERE category_key = :categoryKey")
    List<String> getMessageKeysForCategory(@NonNull String categoryKey);

    @Query("SELECT COUNT(*) FROM message_classifications WHERE category_key = :categoryKey")
    int countMessagesForCategory(@NonNull String categoryKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertClassification(MessageClassificationEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertClassifications(List<MessageClassificationEntity> entities);

    @Query("DELETE FROM message_classifications WHERE message_key = :messageKey")
    void deleteClassification(@NonNull String messageKey);

    @Query("DELETE FROM message_classifications WHERE classification_source = 'ENGINE'")
    int clearAutomaticClassifications();

    @Query("UPDATE message_classifications SET category_key = :newCategoryKey WHERE category_key = :oldCategoryKey")
    int reassignCategory(@NonNull String oldCategoryKey, @NonNull String newCategoryKey);

    @Query("DELETE FROM message_classifications WHERE message_key NOT IN (:validKeys)")
    int purgeOrphanedClassifications(List<String> validKeys);

    // --- Classification Rules ---

    @Query("SELECT * FROM classification_rules ORDER BY priority ASC, rule_id ASC")
    LiveData<List<ClassificationRuleEntity>> getAllRulesLiveData();

    @Query("SELECT * FROM classification_rules ORDER BY priority ASC, rule_id ASC")
    List<ClassificationRuleEntity> getAllRulesSync();

    @Query("SELECT * FROM classification_rules WHERE is_enabled = 1 ORDER BY priority ASC, rule_id ASC")
    List<ClassificationRuleEntity> getEnabledRulesOrderedByPriority();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertRule(ClassificationRuleEntity rule);

    @Update
    void updateRule(ClassificationRuleEntity rule);

    @Delete
    void deleteRule(ClassificationRuleEntity rule);

    @Query("DELETE FROM classification_rules WHERE rule_id = :ruleId")
    void deleteRuleById(long ruleId);

    // --- Sender Categories ---

    @Nullable
    @Query("SELECT * FROM sender_categories WHERE sender_identifier = :senderIdentifier LIMIT 1")
    SenderCategoryEntity getSenderCategory(@NonNull String senderIdentifier);

    @Query("SELECT * FROM sender_categories")
    List<SenderCategoryEntity> getAllSenderCategories();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSenderCategory(SenderCategoryEntity entity);

    @Query("DELETE FROM sender_categories WHERE sender_identifier = :senderIdentifier")
    void deleteSenderCategory(@NonNull String senderIdentifier);

    // ==========================================
    // STEP 8: ADVANCED CONVERSATION METADATA
    // ==========================================

    @Nullable
    @Query("SELECT * FROM conversation_metadata WHERE thread_id = :threadId LIMIT 1")
    ConversationMetadataEntity getMetadataForThread(long threadId);

    @Query("SELECT * FROM conversation_metadata")
    List<ConversationMetadataEntity> getAllMetadataSync();

    @Query("SELECT * FROM conversation_metadata")
    LiveData<List<ConversationMetadataEntity>> getAllMetadataLiveData();

    @Query("SELECT thread_id FROM conversation_metadata WHERE is_pinned = 1 ORDER BY pinned_at DESC")
    List<Long> getPinnedThreadIds();

    @Query("SELECT thread_id FROM conversation_metadata WHERE is_archived = 1 ORDER BY archived_at DESC")
    List<Long> getArchivedThreadIds();

    @Query("SELECT thread_id FROM conversation_metadata WHERE is_blocked = 1")
    List<Long> getBlockedThreadIds();

    @Query("SELECT thread_id FROM conversation_metadata WHERE is_spam = 1")
    List<Long> getSpamThreadIds();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMetadata(ConversationMetadataEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMetadataList(List<ConversationMetadataEntity> entities);

    @Update
    void updateMetadata(ConversationMetadataEntity entity);

    @Query("UPDATE conversation_metadata SET is_pinned = :isPinned, pinned_at = :pinnedAt WHERE thread_id = :threadId")
    int updatePinnedStatus(long threadId, boolean isPinned, long pinnedAt);

    @Query("UPDATE conversation_metadata SET is_archived = :isArchived, archived_at = :archivedAt WHERE thread_id = :threadId")
    int updateArchivedStatus(long threadId, boolean isArchived, long archivedAt);

    @Query("UPDATE conversation_metadata SET is_blocked = :isBlocked WHERE thread_id = :threadId")
    int updateBlockedStatus(long threadId, boolean isBlocked);

    @Query("UPDATE conversation_metadata SET is_spam = :isSpam WHERE thread_id = :threadId")
    int updateSpamStatus(long threadId, boolean isSpam);

    @Query("UPDATE conversation_metadata SET muted_until = :mutedUntil WHERE thread_id = :threadId")
    int updateMuteState(long threadId, long mutedUntil);

    @Query("UPDATE conversation_metadata SET is_silent = :isSilent WHERE thread_id = :threadId")
    int updateSilentState(long threadId, boolean isSilent);

    @Query("UPDATE conversation_metadata SET custom_sound_uri = :soundUri WHERE thread_id = :threadId")
    int updateCustomSoundUri(long threadId, @Nullable String soundUri);

    @Query("UPDATE conversation_metadata SET notification_id = :notificationId, last_notified_message_key = :messageKey, last_notification_timestamp = :timestamp, is_dismissed = :isDismissed WHERE thread_id = :threadId")
    int updateNotificationState(long threadId, int notificationId, @Nullable String messageKey, long timestamp, boolean isDismissed);

    @Query("UPDATE conversation_metadata SET is_dismissed = :isDismissed WHERE thread_id = :threadId")
    int updateDismissedState(long threadId, boolean isDismissed);

    @Nullable
    @Query("SELECT muted_until FROM conversation_metadata WHERE thread_id = :threadId LIMIT 1")
    Long getMutedUntil(long threadId);

    @Query("DELETE FROM conversation_metadata WHERE thread_id = :threadId")
    void deleteMetadata(long threadId);

    // ==========================================
    // STEP 8: STARRED MESSAGES
    // ==========================================

    @Nullable
    @Query("SELECT * FROM starred_messages WHERE message_key = :messageKey LIMIT 1")
    StarredMessageEntity getStarredMessage(@NonNull String messageKey);

    @Query("SELECT COUNT(*) > 0 FROM starred_messages WHERE message_key = :messageKey")
    boolean isMessageStarred(@NonNull String messageKey);

    @Query("SELECT * FROM starred_messages ORDER BY starred_at DESC")
    LiveData<List<StarredMessageEntity>> getAllStarredMessagesLiveData();

    @Query("SELECT * FROM starred_messages ORDER BY starred_at DESC")
    List<StarredMessageEntity> getAllStarredMessagesSync();

    @Query("SELECT message_key FROM starred_messages")
    List<String> getAllStarredMessageKeys();

    @Query("SELECT message_key FROM starred_messages WHERE thread_id = :threadId")
    List<String> getStarredKeysForThread(long threadId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStarredMessage(StarredMessageEntity entity);

    @Query("DELETE FROM starred_messages WHERE message_key = :messageKey")
    void deleteStarredMessage(@NonNull String messageKey);

    @Query("DELETE FROM starred_messages WHERE thread_id = :threadId")
    void deleteStarredForThread(long threadId);

    // ==========================================
    // STEP 8: BLOCKED SENDERS
    // ==========================================

    @Query("SELECT * FROM blocked_senders ORDER BY blocked_at DESC")
    LiveData<List<BlockedSenderEntity>> getAllBlockedSendersLiveData();

    @Query("SELECT * FROM blocked_senders ORDER BY blocked_at DESC")
    List<BlockedSenderEntity> getAllBlockedSendersSync();

    @Query("SELECT normalized_identifier FROM blocked_senders")
    List<String> getAllBlockedNormalizedIdentifiers();

    @Query("SELECT COUNT(*) > 0 FROM blocked_senders WHERE normalized_identifier = :normalizedIdentifier")
    boolean isSenderBlocked(@NonNull String normalizedIdentifier);

    @Nullable
    @Query("SELECT * FROM blocked_senders WHERE normalized_identifier = :normalizedIdentifier LIMIT 1")
    BlockedSenderEntity getBlockedSenderByNormalized(@NonNull String normalizedIdentifier);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertBlockedSender(BlockedSenderEntity entity);

    @Query("DELETE FROM blocked_senders WHERE normalized_identifier = :normalizedIdentifier")
    int deleteBlockedSenderByNormalized(@NonNull String normalizedIdentifier);

    @Query("DELETE FROM blocked_senders WHERE id = :id")
    int deleteBlockedSenderById(long id);

    // ==========================================
    // STEP 8: SPAM FEEDBACK
    // ==========================================

    @Query("SELECT * FROM spam_feedback WHERE normalized_identifier = :normalizedIdentifier ORDER BY reported_at DESC")
    List<SpamFeedbackEntity> getFeedbackForSender(@NonNull String normalizedIdentifier);

    @Query("SELECT * FROM spam_feedback ORDER BY reported_at DESC")
    List<SpamFeedbackEntity> getAllFeedbackSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertSpamFeedback(SpamFeedbackEntity entity);

    @Query("DELETE FROM spam_feedback WHERE normalized_identifier = :normalizedIdentifier")
    int deleteFeedbackForSender(@NonNull String normalizedIdentifier);

    // ==========================================
    // STEP 8: SECURITY & PHISHING WARNINGS
    // ==========================================

    @Nullable
    @Query("SELECT * FROM security_warnings WHERE message_key = :messageKey LIMIT 1")
    SecurityWarningEntity getWarningForMessage(@NonNull String messageKey);

    @Query("SELECT * FROM security_warnings WHERE thread_id = :threadId AND is_dismissed = 0 ORDER BY detected_at DESC")
    List<SecurityWarningEntity> getUndismissedWarningsForThread(long threadId);

    @Query("SELECT * FROM security_warnings ORDER BY detected_at DESC")
    List<SecurityWarningEntity> getAllWarningsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSecurityWarning(SecurityWarningEntity entity);

    @Query("UPDATE security_warnings SET is_dismissed = 1 WHERE message_key = :messageKey")
    int dismissWarning(@NonNull String messageKey);

    @Query("DELETE FROM security_warnings WHERE message_key = :messageKey")
    void deleteWarningForMessage(@NonNull String messageKey);
}
