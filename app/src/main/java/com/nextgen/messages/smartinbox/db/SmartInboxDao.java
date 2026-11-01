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
 * categories, rules, and sender overrides.
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
}
