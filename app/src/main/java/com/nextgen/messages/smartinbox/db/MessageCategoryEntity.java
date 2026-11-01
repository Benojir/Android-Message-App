package com.nextgen.messages.smartinbox.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a message category (built-in or user-created custom category).
 */
@Entity(
        tableName = "message_categories",
        indices = {@Index(value = {"category_key"}, unique = true)}
)
public class MessageCategoryEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "category_id")
    private long categoryId;

    @NonNull
    @ColumnInfo(name = "category_key")
    private String categoryKey;

    @NonNull
    @ColumnInfo(name = "name")
    private String name;

    @Nullable
    @ColumnInfo(name = "description")
    private String description;

    @NonNull
    @ColumnInfo(name = "category_type")
    private String categoryType; // e.g. "ALL", "PERSONAL", "TRANSACTIONS", "OTP", "PROMOTIONS", "BUSINESS", "SPAM", "OTHER", "CUSTOM"

    @ColumnInfo(name = "is_built_in")
    private boolean isBuiltIn;

    @NonNull
    @ColumnInfo(name = "icon_res_name")
    private String iconResName;

    @NonNull
    @ColumnInfo(name = "color_hex")
    private String colorHex;

    @ColumnInfo(name = "display_order")
    private int displayOrder;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public MessageCategoryEntity(
            long categoryId,
            @NonNull String categoryKey,
            @NonNull String name,
            @Nullable String description,
            @NonNull String categoryType,
            boolean isBuiltIn,
            @NonNull String iconResName,
            @NonNull String colorHex,
            int displayOrder,
            long createdAt,
            long updatedAt
    ) {
        this.categoryId = categoryId;
        this.categoryKey = categoryKey;
        this.name = name;
        this.description = description;
        this.categoryType = categoryType;
        this.isBuiltIn = isBuiltIn;
        this.iconResName = iconResName;
        this.colorHex = colorHex;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MessageCategoryEntity createBuiltIn(
            @NonNull String key,
            @NonNull String name,
            @Nullable String description,
            @NonNull String categoryType,
            @NonNull String iconResName,
            @NonNull String colorHex,
            int displayOrder
    ) {
        long now = System.currentTimeMillis();
        return new MessageCategoryEntity(0, key, name, description, categoryType, true, iconResName, colorHex, displayOrder, now, now);
    }

    public static MessageCategoryEntity createCustom(
            @NonNull String name,
            @NonNull String iconResName,
            @NonNull String colorHex
    ) {
        String slug = "custom_" + name.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_") + "_" + System.currentTimeMillis();
        long now = System.currentTimeMillis();
        return new MessageCategoryEntity(0, slug, name.trim(), "Custom category", "CUSTOM", false, iconResName, colorHex, 100, now, now);
    }

    public long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(long categoryId) {
        this.categoryId = categoryId;
    }

    @NonNull
    public String getCategoryKey() {
        return categoryKey;
    }

    public void setCategoryKey(@NonNull String categoryKey) {
        this.categoryKey = categoryKey;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getDisplayName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    @NonNull
    public String getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(@NonNull String categoryType) {
        this.categoryType = categoryType;
    }

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    public boolean isActive() {
        return true;
    }

    public void setBuiltIn(boolean builtIn) {
        isBuiltIn = builtIn;
    }

    @NonNull
    public String getIconResName() {
        return iconResName;
    }

    public void setIconResName(@NonNull String iconResName) {
        this.iconResName = iconResName;
    }

    @NonNull
    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(@NonNull String colorHex) {
        this.colorHex = colorHex;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
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
