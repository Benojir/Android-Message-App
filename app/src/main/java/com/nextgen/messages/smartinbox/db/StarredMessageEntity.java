package com.nextgen.messages.smartinbox.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity tracking messages that have been starred by the user.
 * Identified by composite key ("SMS_<id>" or "MMS_<id>") to prevent cross-type ID collisions.
 */
@Entity(
        tableName = "starred_messages",
        indices = {
                @Index(value = {"thread_id"}),
                @Index(value = {"starred_at"})
        }
)
public class StarredMessageEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "message_key")
    private String messageKey;

    @ColumnInfo(name = "thread_id")
    private long threadId;

    @ColumnInfo(name = "raw_message_id")
    private long rawMessageId;

    @NonNull
    @ColumnInfo(name = "message_type")
    private String messageType; // "SMS" or "MMS"

    @ColumnInfo(name = "starred_at")
    private long starredAt;

    public StarredMessageEntity(
            @NonNull String messageKey,
            long threadId,
            long rawMessageId,
            @NonNull String messageType,
            long starredAt
    ) {
        this.messageKey = messageKey;
        this.threadId = threadId;
        this.rawMessageId = rawMessageId;
        this.messageType = messageType;
        this.starredAt = starredAt;
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

    public long getStarredAt() {
        return starredAt;
    }

    public void setStarredAt(long starredAt) {
        this.starredAt = starredAt;
    }
}
