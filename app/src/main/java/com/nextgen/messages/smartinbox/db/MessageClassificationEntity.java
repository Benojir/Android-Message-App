package com.nextgen.messages.smartinbox.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity storing the classification status, source, confidence, and transparent explanation
 * for an individual SMS or MMS message record.
 */
@Entity(
        tableName = "message_classifications",
        indices = {
                @Index(value = {"message_key"}, unique = true),
                @Index(value = {"category_key"}),
                @Index(value = {"thread_id"})
        }
)
public class MessageClassificationEntity {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_RULE = "RULE";
    public static final String SOURCE_ENGINE = "ENGINE";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "message_key")
    private String messageKey; // e.g. "SMS_123" or "MMS_456"

    @ColumnInfo(name = "thread_id")
    private long threadId;

    @ColumnInfo(name = "raw_message_id")
    private long rawMessageId;

    @NonNull
    @ColumnInfo(name = "message_type")
    private String messageType; // "SMS" or "MMS"

    @NonNull
    @ColumnInfo(name = "category_key")
    private String categoryKey;

    @NonNull
    @ColumnInfo(name = "classification_source")
    private String classificationSource; // "MANUAL", "RULE", "ENGINE"

    @ColumnInfo(name = "confidence")
    private float confidence;

    @Nullable
    @ColumnInfo(name = "reason")
    private String reason;

    @ColumnInfo(name = "classified_at")
    private long classifiedAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public MessageClassificationEntity(
            long id,
            @NonNull String messageKey,
            long threadId,
            long rawMessageId,
            @NonNull String messageType,
            @NonNull String categoryKey,
            @NonNull String classificationSource,
            float confidence,
            @Nullable String reason,
            long classifiedAt,
            long updatedAt
    ) {
        this.id = id;
        this.messageKey = messageKey;
        this.threadId = threadId;
        this.rawMessageId = rawMessageId;
        this.messageType = messageType;
        this.categoryKey = categoryKey;
        this.classificationSource = classificationSource;
        this.confidence = confidence;
        this.reason = reason;
        this.classifiedAt = classifiedAt;
        this.updatedAt = updatedAt;
    }

    public static String buildMessageKey(boolean isMms, long rawId) {
        return (isMms ? "MMS_" : "SMS_") + rawId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(@NonNull String messageKey) {
        this.messageKey = messageKey;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public long getRawMessageId() {
        return rawMessageId;
    }

    public void setRawMessageId(long rawMessageId) {
        this.rawMessageId = rawMessageId;
    }

    @NonNull
    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(@NonNull String messageType) {
        this.messageType = messageType;
    }

    @NonNull
    public String getCategoryKey() {
        return categoryKey;
    }

    public void setCategoryKey(@NonNull String categoryKey) {
        this.categoryKey = categoryKey;
    }

    @NonNull
    public String getClassificationSource() {
        return classificationSource;
    }

    public void setClassificationSource(@NonNull String classificationSource) {
        this.classificationSource = classificationSource;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    public void setReason(@Nullable String reason) {
        this.reason = reason;
    }

    public long getClassifiedAt() {
        return classifiedAt;
    }

    public void setClassifiedAt(long classifiedAt) {
        this.classifiedAt = classifiedAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
