package com.nextgen.messages.data.model;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Model representing a smart message category for categorization and inbox organization.
 */
public class CategoryItem {

    private final String id;
    private int titleRes;
    private String customTitle;
    private int descRes;
    private String customDesc;
    private final int iconRes;
    private int badgeRes;
    private String customBadgeText;
    private final int colorRes;
    private final int containerColorRes;
    private int colorInt = 0;
    private int containerColorInt = 0;
    private boolean isBuiltIn = true;
    private int messageCount = 0;
    private int unreadCount = 0;
    private int conversationCount = 0;

    public CategoryItem(
            String id,
            @StringRes int titleRes,
            @StringRes int descRes,
            @DrawableRes int iconRes,
            @StringRes int badgeRes,
            @ColorRes int colorRes,
            @ColorRes int containerColorRes
    ) {
        this.id = id;
        this.titleRes = titleRes;
        this.descRes = descRes;
        this.iconRes = iconRes;
        this.badgeRes = badgeRes;
        this.colorRes = colorRes;
        this.containerColorRes = containerColorRes;
        this.isBuiltIn = true;
    }

    public CategoryItem(
            @NonNull String id,
            @NonNull String customTitle,
            @Nullable String customDesc,
            @DrawableRes int iconRes,
            int colorInt,
            int containerColorInt,
            boolean isBuiltIn
    ) {
        this.id = id;
        this.customTitle = customTitle;
        this.customDesc = customDesc;
        this.iconRes = iconRes;
        this.colorRes = 0;
        this.containerColorRes = 0;
        this.colorInt = colorInt;
        this.containerColorInt = containerColorInt;
        this.isBuiltIn = isBuiltIn;
    }

    public String getId() {
        return id;
    }

    public int getTitleRes() {
        return titleRes;
    }

    public String getCustomTitle() {
        return customTitle;
    }

    public int getDescRes() {
        return descRes;
    }

    public String getCustomDesc() {
        return customDesc;
    }

    public int getIconRes() {
        return iconRes;
    }

    public int getBadgeRes() {
        return badgeRes;
    }

    public String getCustomBadgeText() {
        return customBadgeText;
    }

    public void setCustomBadgeText(String customBadgeText) {
        this.customBadgeText = customBadgeText;
    }

    public int getColorRes() {
        return colorRes;
    }

    public int getContainerColorRes() {
        return containerColorRes;
    }

    public int getColorInt() {
        return colorInt;
    }

    public int getContainerColorInt() {
        return containerColorInt;
    }

    public boolean isBuiltIn() {
        return isBuiltIn;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public int getConversationCount() {
        return conversationCount;
    }

    public void setConversationCount(int conversationCount) {
        this.conversationCount = conversationCount;
    }
}
