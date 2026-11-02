package com.nextgen.messages.data.model;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Model representing an aggregated SMS conversation thread.
 * Contains thread identification, contact metadata, latest snippet,
 * unread count, MMS/Group flags, pin, archive, block, spam, and mute states.
 */
public class Conversation implements Serializable {

    private final long threadId;
    @Nullable
    private final String address;
    @Nullable
    private final String contactName;
    @Nullable
    private final Uri photoUri;
    @Nullable
    private final String snippet;
    private final long date;
    private final int unreadCount;
    private final int messageCount;
    private final boolean isGroup;
    @NonNull
    private final List<String> recipients;

    // Step 8 metadata
    private final boolean isPinned;
    private final long pinnedAt;
    private final boolean isArchived;
    private final long archivedAt;
    private final boolean isBlocked;
    private final boolean isSpam;

    // Step 9 metadata
    private final long mutedUntil;
    private final boolean isSilent;

    public Conversation(
            long threadId,
            @Nullable String address,
            @Nullable String contactName,
            @Nullable Uri photoUri,
            @Nullable String snippet,
            long date,
            int unreadCount,
            int messageCount
    ) {
        this(threadId, address, contactName, photoUri, snippet, date, unreadCount, messageCount, false, Collections.emptyList());
    }

    public Conversation(
            long threadId,
            @Nullable String address,
            @Nullable String contactName,
            @Nullable Uri photoUri,
            @Nullable String snippet,
            long date,
            int unreadCount,
            int messageCount,
            boolean isGroup,
            @Nullable List<String> recipients
    ) {
        this(threadId, address, contactName, photoUri, snippet, date, unreadCount, messageCount, isGroup, recipients,
                false, 0L, false, 0L, false, false, 0L, false);
    }

    public Conversation(
            long threadId,
            @Nullable String address,
            @Nullable String contactName,
            @Nullable Uri photoUri,
            @Nullable String snippet,
            long date,
            int unreadCount,
            int messageCount,
            boolean isGroup,
            @Nullable List<String> recipients,
            boolean isPinned,
            long pinnedAt,
            boolean isArchived,
            long archivedAt,
            boolean isBlocked,
            boolean isSpam
    ) {
        this(threadId, address, contactName, photoUri, snippet, date, unreadCount, messageCount, isGroup, recipients,
                isPinned, pinnedAt, isArchived, archivedAt, isBlocked, isSpam, 0L, false);
    }

    public Conversation(
            long threadId,
            @Nullable String address,
            @Nullable String contactName,
            @Nullable Uri photoUri,
            @Nullable String snippet,
            long date,
            int unreadCount,
            int messageCount,
            boolean isGroup,
            @Nullable List<String> recipients,
            boolean isPinned,
            long pinnedAt,
            boolean isArchived,
            long archivedAt,
            boolean isBlocked,
            boolean isSpam,
            long mutedUntil,
            boolean isSilent
    ) {
        this.threadId = threadId;
        this.address = address;
        this.contactName = contactName;
        this.photoUri = photoUri;
        this.snippet = snippet;
        this.date = date;
        this.unreadCount = unreadCount;
        this.messageCount = messageCount;
        this.isGroup = isGroup;
        this.recipients = recipients != null ? new ArrayList<>(recipients) : Collections.emptyList();
        this.isPinned = isPinned;
        this.pinnedAt = pinnedAt;
        this.isArchived = isArchived;
        this.archivedAt = archivedAt;
        this.isBlocked = isBlocked;
        this.isSpam = isSpam;
        this.mutedUntil = mutedUntil;
        this.isSilent = isSilent;
    }

    public long getThreadId() {
        return threadId;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    @Nullable
    public String getContactName() {
        return contactName;
    }

    @Nullable
    public Uri getPhotoUri() {
        return photoUri;
    }

    @Nullable
    public String getSnippet() {
        return snippet;
    }

    @NonNull
    public String getDisplaySnippet() {
        return snippet != null ? snippet : "";
    }

    public long getDate() {
        return date;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public boolean isGroup() {
        return isGroup;
    }

    @NonNull
    public List<String> getRecipients() {
        return Collections.unmodifiableList(recipients);
    }

    public boolean isPinned() {
        return isPinned;
    }

    public long getPinnedAt() {
        return pinnedAt;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public long getArchivedAt() {
        return archivedAt;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public boolean isSpam() {
        return isSpam;
    }

    public long getMutedUntil() {
        return mutedUntil;
    }

    public boolean isCurrentlyMuted() {
        if (mutedUntil == -1L) {
            return true;
        }
        return mutedUntil > System.currentTimeMillis();
    }

    public boolean isSilent() {
        return isSilent;
    }

    public boolean hasUnread() {
        return unreadCount > 0;
    }

    @NonNull
    public String getTitle() {
        if (contactName != null && !contactName.trim().isEmpty()) {
            return contactName.trim();
        }
        if (isGroup && !recipients.isEmpty()) {
            return "Group (" + recipients.size() + ")";
        }
        if (address != null && !address.trim().isEmpty()) {
            return address.trim();
        }
        return "Unknown";
    }

    @NonNull
    public String getInitials() {
        String title = getTitle();
        if (title.isEmpty() || title.equals("Unknown")) {
            return "?";
        }
        String[] parts = title.trim().split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        } else {
            return title.substring(0, Math.min(2, title.length())).toUpperCase();
        }
    }

    public Conversation withMetadata(
            boolean isPinned,
            long pinnedAt,
            boolean isArchived,
            long archivedAt,
            boolean isBlocked,
            boolean isSpam,
            long mutedUntil,
            boolean isSilent
    ) {
        return new Conversation(
                threadId,
                address,
                contactName,
                photoUri,
                snippet,
                date,
                unreadCount,
                messageCount,
                isGroup,
                recipients,
                isPinned,
                pinnedAt,
                isArchived,
                archivedAt,
                isBlocked,
                isSpam,
                mutedUntil,
                isSilent
        );
    }

    public Conversation withMetadata(
            boolean isPinned,
            long pinnedAt,
            boolean isArchived,
            long archivedAt,
            boolean isBlocked,
            boolean isSpam
    ) {
        return withMetadata(isPinned, pinnedAt, isArchived, archivedAt, isBlocked, isSpam, 0L, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Conversation that = (Conversation) o;
        return threadId == that.threadId
                && date == that.date
                && unreadCount == that.unreadCount
                && messageCount == that.messageCount
                && isPinned == that.isPinned
                && isArchived == that.isArchived
                && isBlocked == that.isBlocked
                && isSpam == that.isSpam
                && mutedUntil == that.mutedUntil
                && isSilent == that.isSilent
                && Objects.equals(address, that.address)
                && Objects.equals(contactName, that.contactName)
                && Objects.equals(photoUri, that.photoUri)
                && Objects.equals(snippet, that.snippet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId, address, contactName, photoUri, snippet, date, unreadCount, messageCount,
                isPinned, isArchived, isBlocked, isSpam, mutedUntil, isSilent);
    }

    @NonNull
    @Override
    public String toString() {
        return "Conversation{" +
                "threadId=" + threadId +
                ", title='" + getTitle() + '\'' +
                ", snippet='" + snippet + '\'' +
                ", date=" + date +
                ", unread=" + unreadCount +
                ", pinned=" + isPinned +
                ", archived=" + isArchived +
                ", blocked=" + isBlocked +
                ", spam=" + isSpam +
                ", muted=" + isCurrentlyMuted() +
                '}';
    }
}
