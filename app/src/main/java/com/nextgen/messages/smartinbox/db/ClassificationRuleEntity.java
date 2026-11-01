package com.nextgen.messages.smartinbox.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity storing user-created or default classification rules.
 */
@Entity(tableName = "classification_rules")
public class ClassificationRuleEntity {

    public static final String TYPE_SENDER_EQUALS = "SENDER_EQUALS";
    public static final String TYPE_SENDER_CONTAINS = "SENDER_CONTAINS";
    public static final String TYPE_BODY_CONTAINS = "BODY_CONTAINS";
    public static final String TYPE_BODY_STARTS_WITH = "BODY_STARTS_WITH";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rule_id")
    private long ruleId;

    @NonNull
    @ColumnInfo(name = "rule_name")
    private String ruleName;

    @NonNull
    @ColumnInfo(name = "rule_type")
    private String ruleType; // SENDER_EQUALS, SENDER_CONTAINS, BODY_CONTAINS, BODY_STARTS_WITH

    @NonNull
    @ColumnInfo(name = "match_value")
    private String matchValue;

    @NonNull
    @ColumnInfo(name = "target_category_key")
    private String targetCategoryKey;

    @ColumnInfo(name = "priority")
    private int priority; // Lower number = higher priority

    @ColumnInfo(name = "is_enabled")
    private boolean isEnabled;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public ClassificationRuleEntity(
            long ruleId,
            @NonNull String ruleName,
            @NonNull String ruleType,
            @NonNull String matchValue,
            @NonNull String targetCategoryKey,
            int priority,
            boolean isEnabled,
            long createdAt,
            long updatedAt
    ) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.ruleType = ruleType;
        this.matchValue = matchValue;
        this.targetCategoryKey = targetCategoryKey;
        this.priority = priority;
        this.isEnabled = isEnabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ClassificationRuleEntity create(
            @NonNull String name,
            @NonNull String type,
            @NonNull String value,
            @NonNull String targetCategoryKey,
            int priority
    ) {
        long now = System.currentTimeMillis();
        return new ClassificationRuleEntity(0, name, type, value, targetCategoryKey, priority, true, now, now);
    }

    public long getRuleId() {
        return ruleId;
    }

    public long getId() {
        return ruleId;
    }

    public void setRuleId(long ruleId) {
        this.ruleId = ruleId;
    }

    @NonNull
    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(@NonNull String ruleName) {
        this.ruleName = ruleName;
    }

    @NonNull
    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(@NonNull String ruleType) {
        this.ruleType = ruleType;
    }

    @NonNull
    public String getMatchValue() {
        return matchValue;
    }

    public void setMatchValue(@NonNull String matchValue) {
        this.matchValue = matchValue;
    }

    @NonNull
    public String getTargetCategoryKey() {
        return targetCategoryKey;
    }

    public void setTargetCategoryKey(@NonNull String targetCategoryKey) {
        this.targetCategoryKey = targetCategoryKey;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
