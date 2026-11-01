package com.nextgen.messages.smartinbox.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity storing sender-level categorization and manual sender overrides.
 */
@Entity(tableName = "sender_categories")
public class SenderCategoryEntity {

    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "sender_identifier")
    private String senderIdentifier; // Normalized address or alphanumeric sender ID (uppercase)

    @NonNull
    @ColumnInfo(name = "category_key")
    private String categoryKey;

    @ColumnInfo(name = "is_manual_override")
    private boolean isManualOverride;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public SenderCategoryEntity(
            @NonNull String senderIdentifier,
            @NonNull String categoryKey,
            boolean isManualOverride,
            long updatedAt
    ) {
        this.senderIdentifier = senderIdentifier;
        this.categoryKey = categoryKey;
        this.isManualOverride = isManualOverride;
        this.updatedAt = updatedAt;
    }

    @NonNull
    public String getSenderIdentifier() {
        return senderIdentifier;
    }

    public void setSenderIdentifier(@NonNull String senderIdentifier) {
        this.senderIdentifier = senderIdentifier;
    }

    @NonNull
    public String getCategoryKey() {
        return categoryKey;
    }

    public void setCategoryKey(@NonNull String categoryKey) {
        this.categoryKey = categoryKey;
    }

    public boolean isManualOverride() {
        return isManualOverride;
    }

    public void setManualOverride(boolean manualOverride) {
        isManualOverride = manualOverride;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
