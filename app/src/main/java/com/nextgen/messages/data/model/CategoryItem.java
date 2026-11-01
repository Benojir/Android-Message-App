package com.nextgen.messages.data.model;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * Model representing a smart message category for categorization roadmap.
 */
public class CategoryItem {

    private final String id;
    private final int titleRes;
    private final int descRes;
    private final int iconRes;
    private final int badgeRes;
    private final int colorRes;
    private final int containerColorRes;

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
    }

    public String getId() {
        return id;
    }

    public int getTitleRes() {
        return titleRes;
    }

    public int getDescRes() {
        return descRes;
    }

    public int getIconRes() {
        return iconRes;
    }

    public int getBadgeRes() {
        return badgeRes;
    }

    public int getColorRes() {
        return colorRes;
    }

    public int getContainerColorRes() {
        return containerColorRes;
    }
}
