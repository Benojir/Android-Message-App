package com.nextgen.messages.smartinbox.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity storing blocked phone numbers and alphanumeric sender IDs.
 */
@Entity(
        tableName = "blocked_senders",
        indices = {
                @Index(value = {"normalized_identifier"}, unique = true),
                @Index(value = {"blocked_at"})
        }
)
public class BlockedSenderEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "sender_identifier")
    private String senderIdentifier;

    @NonNull
    @ColumnInfo(name = "normalized_identifier")
    private String normalizedIdentifier;

    @ColumnInfo(name = "blocked_at")
    private long blockedAt;

    @Nullable
    @ColumnInfo(name = "block_reason")
    private String blockReason;

    public BlockedSenderEntity(
            long id,
            @NonNull String senderIdentifier,
            @NonNull String normalizedIdentifier,
            long blockedAt,
            @Nullable String blockReason
    ) {
        this.id = id;
        this.senderIdentifier = senderIdentifier;
        this.normalizedIdentifier = normalizedIdentifier;
        this.blockedAt = blockedAt;
        this.blockReason = blockReason;
    }

    public static BlockedSenderEntity create(
            @NonNull String senderIdentifier,
            @NonNull String normalizedIdentifier,
            long blockedAt,
            @Nullable String blockReason
    ) {
        return new BlockedSenderEntity(0, senderIdentifier, normalizedIdentifier, blockedAt, blockReason);
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

    public long getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(long blockedAt) {
        this.blockedAt = blockedAt;
    }

    @Nullable
    public String getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(@Nullable String blockReason) {
        this.blockReason = blockReason;
    }
}
