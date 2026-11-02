package com.nextgen.messages.smartinbox.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity recording local user feedback (Report Spam / Mark Not Spam)
 * to personalize future local heuristic spam evaluations.
 */
@Entity(
        tableName = "spam_feedback",
        indices = {
                @Index(value = {"normalized_identifier"}),
                @Index(value = {"feedback_type"}),
                @Index(value = {"reported_at"})
        }
)
public class SpamFeedbackEntity {

    public static final String TYPE_REPORT_SPAM = "USER_REPORTED_SPAM";
    public static final String TYPE_NOT_SPAM = "USER_MARKED_NOT_SPAM";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "sender_identifier")
    private String senderIdentifier;

    @NonNull
    @ColumnInfo(name = "normalized_identifier")
    private String normalizedIdentifier;

    @ColumnInfo(name = "thread_id")
    private long threadId;

    @Nullable
    @ColumnInfo(name = "message_key")
    private String messageKey;

    @NonNull
    @ColumnInfo(name = "feedback_type")
    private String feedbackType; // TYPE_REPORT_SPAM or TYPE_NOT_SPAM

    @ColumnInfo(name = "reported_at")
    private long reportedAt;

    public SpamFeedbackEntity(
            long id,
            @NonNull String senderIdentifier,
            @NonNull String normalizedIdentifier,
            long threadId,
            @Nullable String messageKey,
            @NonNull String feedbackType,
            long reportedAt
    ) {
        this.id = id;
        this.senderIdentifier = senderIdentifier;
        this.normalizedIdentifier = normalizedIdentifier;
        this.threadId = threadId;
        this.messageKey = messageKey;
        this.feedbackType = feedbackType;
        this.reportedAt = reportedAt;
    }

    public static SpamFeedbackEntity create(
            @NonNull String senderIdentifier,
            @NonNull String normalizedIdentifier,
            long threadId,
            @Nullable String messageKey,
            @NonNull String feedbackType,
            long reportedAt
    ) {
        return new SpamFeedbackEntity(0, senderIdentifier, normalizedIdentifier, threadId, messageKey, feedbackType, reportedAt);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getSenderIdentifier() {
        return senderIdentifier;
    }

    public void setSenderIdentifier(@NonNull String senderIdentifier) {
        this.senderIdentifier = senderIdentifier;
    }

    @NonNull
    public String getNormalizedIdentifier() {
        return normalizedIdentifier;
    }

    public void setNormalizedIdentifier(@NonNull String normalizedIdentifier) {
        this.normalizedIdentifier = normalizedIdentifier;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    @Nullable
    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(@Nullable String messageKey) {
        this.messageKey = messageKey;
    }

    @NonNull
    public String getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(@NonNull String feedbackType) {
        this.feedbackType = feedbackType;
    }

    public long getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(long reportedAt) {
        this.reportedAt = reportedAt;
    }
}
