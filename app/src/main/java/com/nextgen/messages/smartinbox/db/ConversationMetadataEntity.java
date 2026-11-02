package com.nextgen.messages.smartinbox.db;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity storing thread-level metadata such as pinning, archiving, blocking,
 * spam classification, mute periods, and notification tracking states.
 * Stored locally without mutating system Telephony provider rows.
 */
@Entity(
        tableName = "conversation_metadata",
        indices = {
                @Index(value = {"is_pinned"}),
                @Index(value = {"is_archived"}),
                @Index(value = {"is_blocked"}),
                @Index(value = {"is_spam"})
        }
)
public class ConversationMetadataEntity {

    @PrimaryKey
    @ColumnInfo(name = "thread_id")
    private long threadId;

    @ColumnInfo(name = "is_pinned")
    private boolean isPinned;

    @ColumnInfo(name = "pinned_at")
    private long pinnedAt;

    @ColumnInfo(name = "is_archived")
    private boolean isArchived;

    @ColumnInfo(name = "archived_at")
    private long archivedAt;

    @ColumnInfo(name = "is_blocked")
    private boolean isBlocked;

    @ColumnInfo(name = "is_spam")
    private boolean isSpam;

    @ColumnInfo(name = "muted_until", defaultValue = "0")
    private long mutedUntil;

    @ColumnInfo(name = "is_silent", defaultValue = "0")
    private boolean isSilent;

    @Nullable
    @ColumnInfo(name = "custom_sound_uri")
    private String customSoundUri;

    @ColumnInfo(name = "notification_id", defaultValue = "0")
    private int notificationId;

    @Nullable
    @ColumnInfo(name = "last_notified_message_key")
    private String lastNotifiedMessageKey;

    @ColumnInfo(name = "last_notification_timestamp", defaultValue = "0")
    private long lastNotificationTimestamp;

    @ColumnInfo(name = "is_dismissed", defaultValue = "0")
    private boolean isDismissed;

    public ConversationMetadataEntity(
            long threadId,
            boolean isPinned,
            long pinnedAt,
            boolean isArchived,
            long archivedAt,
            boolean isBlocked,
            boolean isSpam,
            long mutedUntil,
            boolean isSilent,
            @Nullable String customSoundUri,
            int notificationId,
            @Nullable String lastNotifiedMessageKey,
            long lastNotificationTimestamp,
            boolean isDismissed
    ) {
        this.threadId = threadId;
        this.isPinned = isPinned;
        this.pinnedAt = pinnedAt;
        this.isArchived = isArchived;
        this.archivedAt = archivedAt;
        this.isBlocked = isBlocked;
        this.isSpam = isSpam;
        this.mutedUntil = mutedUntil;
        this.isSilent = isSilent;
        this.customSoundUri = customSoundUri;
        this.notificationId = notificationId;
        this.lastNotifiedMessageKey = lastNotifiedMessageKey;
        this.lastNotificationTimestamp = lastNotificationTimestamp;
        this.isDismissed = isDismissed;
    }

    @Ignore
    public ConversationMetadataEntity(
            long threadId,
            boolean isPinned,
            long pinnedAt,
            boolean isArchived,
            long archivedAt,
            boolean isBlocked,
            boolean isSpam
    ) {
        this(threadId, isPinned, pinnedAt, isArchived, archivedAt, isBlocked, isSpam, 0L, false, null, 0, null, 0L, false);
    }

    public static ConversationMetadataEntity defaultForThread(long threadId) {
        return new ConversationMetadataEntity(threadId, false, 0L, false, 0L, false, false, 0L, false, null, 0, null, 0L, false);
    }

    public boolean isCurrentlyMuted() {
        if (mutedUntil == -1L) {
            return true;
        }
        return mutedUntil > System.currentTimeMillis();
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public long getPinnedAt() {
        return pinnedAt;
    }

    public void setPinnedAt(long pinnedAt) {
        this.pinnedAt = pinnedAt;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public void setArchived(boolean archived) {
        isArchived = archived;
    }

    public long getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(long archivedAt) {
        this.archivedAt = archivedAt;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    public boolean isSpam() {
        return isSpam;
    }

    public void setSpam(boolean spam) {
        isSpam = spam;
    }

    public long getMutedUntil() {
        return mutedUntil;
    }

    public void setMutedUntil(long mutedUntil) {
        this.mutedUntil = mutedUntil;
    }

    public boolean isSilent() {
        return isSilent;
    }

    public void setSilent(boolean silent) {
        isSilent = silent;
    }

    @Nullable
    public String getCustomSoundUri() {
        return customSoundUri;
    }

    public void setCustomSoundUri(@Nullable String customSoundUri) {
        this.customSoundUri = customSoundUri;
    }

    public int getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(int notificationId) {
        this.notificationId = notificationId;
    }

    @Nullable
    public String getLastNotifiedMessageKey() {
        return lastNotifiedMessageKey;
    }

    public void setLastNotifiedMessageKey(@Nullable String lastNotifiedMessageKey) {
        this.lastNotifiedMessageKey = lastNotifiedMessageKey;
    }

    public long getLastNotificationTimestamp() {
        return lastNotificationTimestamp;
    }

    public void setLastNotificationTimestamp(long lastNotificationTimestamp) {
        this.lastNotificationTimestamp = lastNotificationTimestamp;
    }

    public boolean isDismissed() {
        return isDismissed;
    }

    public void setDismissed(boolean dismissed) {
        isDismissed = dismissed;
    }
}
