package com.nextgen.messages.smartinbox.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity recording suspicious URLs, phishing warnings, and user dismissal states
 * for individual SMS or MMS messages.
 */
@Entity(
        tableName = "security_warnings",
        indices = {
                @Index(value = {"message_key"}, unique = true),
                @Index(value = {"thread_id"}),
                @Index(value = {"detected_at"})
        }
)
public class SecurityWarningEntity {

    public static final String TYPE_SUSPICIOUS_LINK = "SUSPICIOUS_LINK";
    public static final String TYPE_PHISHING = "PHISHING";
    public static final String TYPE_IP_HOST = "IP_HOST";
    public static final String TYPE_MISLEADING_DOMAIN = "MISLEADING_DOMAIN";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "message_key")
    private String messageKey;

    @ColumnInfo(name = "thread_id")
    private long threadId;

    @NonNull
    @ColumnInfo(name = "detected_url")
    private String detectedUrl;

    @NonNull
    @ColumnInfo(name = "warning_type")
    private String warningType;

    @NonNull
    @ColumnInfo(name = "warning_reason")
    private String warningReason;

    @ColumnInfo(name = "is_dismissed")
    private boolean isDismissed;

    @ColumnInfo(name = "detected_at")
    private long detectedAt;

    public SecurityWarningEntity(
            long id,
            @NonNull String messageKey,
            long threadId,
            @NonNull String detectedUrl,
            @NonNull String warningType,
            @NonNull String warningReason,
            boolean isDismissed,
            long detectedAt
    ) {
        this.id = id;
        this.messageKey = messageKey;
        this.threadId = threadId;
        this.detectedUrl = detectedUrl;
        this.warningType = warningType;
        this.warningReason = warningReason;
        this.isDismissed = isDismissed;
        this.detectedAt = detectedAt;
    }

    public static SecurityWarningEntity create(
            @NonNull String messageKey,
            long threadId,
            @NonNull String detectedUrl,
            @NonNull String warningType,
            @NonNull String warningReason,
            long detectedAt
    ) {
        return new SecurityWarningEntity(0, messageKey, threadId, detectedUrl, warningType, warningReason, false, detectedAt);
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

    @NonNull
    public String getDetectedUrl() {
        return detectedUrl;
    }

    public void setDetectedUrl(@NonNull String detectedUrl) {
        this.detectedUrl = detectedUrl;
    }

    @NonNull
    public String getWarningType() {
        return warningType;
    }

    public void setWarningType(@NonNull String warningType) {
        this.warningType = warningType;
    }

    @NonNull
    public String getWarningReason() {
        return warningReason;
    }

    public void setWarningReason(@NonNull String warningReason) {
        this.warningReason = warningReason;
    }

    public boolean isDismissed() {
        return isDismissed;
    }

    public void setDismissed(boolean dismissed) {
        isDismissed = dismissed;
    }

    public long getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(long detectedAt) {
        this.detectedAt = detectedAt;
    }
}
