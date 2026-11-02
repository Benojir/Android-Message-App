package com.nextgen.messages.search;

import androidx.annotation.Nullable;

/**
 * Filter configuration for advanced multi-attribute message search.
 */
public class SearchFilter {

    private String query;
    private boolean filterMedia;
    private boolean filterLinks;
    private boolean filterStarred;
    @Nullable
    private Long startDate;
    @Nullable
    private Long endDate;

    public SearchFilter() {
        this.query = "";
        this.filterMedia = false;
        this.filterLinks = false;
        this.filterStarred = false;
        this.startDate = null;
        this.endDate = null;
    }

    public SearchFilter(
            String query,
            boolean filterMedia,
            boolean filterLinks,
            boolean filterStarred,
            @Nullable Long startDate,
            @Nullable Long endDate
    ) {
        this.query = query != null ? query : "";
        this.filterMedia = filterMedia;
        this.filterLinks = filterLinks;
        this.filterStarred = filterStarred;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query != null ? query : "";
    }

    public boolean isFilterMedia() {
        return filterMedia;
    }

    public void setFilterMedia(boolean filterMedia) {
        this.filterMedia = filterMedia;
    }

    public boolean isFilterLinks() {
        return filterLinks;
    }

    public void setFilterLinks(boolean filterLinks) {
        this.filterLinks = filterLinks;
    }

    public boolean isFilterStarred() {
        return filterStarred;
    }

    public void setFilterStarred(boolean filterStarred) {
        this.filterStarred = filterStarred;
    }

    public boolean isOnlyStarred() {
        return filterStarred;
    }

    public void setOnlyStarred(boolean onlyStarred) {
        this.filterStarred = onlyStarred;
    }

    @Nullable
    public Long getStartDate() {
        return startDate;
    }

    public void setStartDate(@Nullable Long startDate) {
        this.startDate = startDate;
    }

    @Nullable
    public Long getEndDate() {
        return endDate;
    }

    public void setEndDate(@Nullable Long endDate) {
        this.endDate = endDate;
    }

    public boolean hasActiveFilters() {
        return (query != null && !query.trim().isEmpty())
                || filterMedia
                || filterLinks
                || filterStarred
                || startDate != null
                || endDate != null;
    }
}
