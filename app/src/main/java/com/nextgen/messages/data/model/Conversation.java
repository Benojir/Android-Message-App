package com.nextgen.messages.data.model;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable data model representing a conversation thread summary in the inbox.
 * Supports both individual (1-on-1) and group MMS conversations.
 */
public class Conversation {

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

    public boolean hasUnread() {
        return unreadCount > 0;
    }

    public boolean isGroup() {
        return isGroup;
    }

    @NonNull
    public List<String> getRecipients() {
        return Collections.unmodifiableList(recipients);
    }

    @NonNull
    public String getTitle() {
        if (contactName != null && !contactName.trim().isEmpty()) {
            return contactName.trim();
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
        String[] parts = title.split("\\s+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        } else {
            return title.substring(0, Math.min(2, title.length())).toUpperCase();
        }
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
                && Objects.equals(address, that.address)
                && Objects.equals(contactName, that.contactName)
                && Objects.equals(photoUri, that.photoUri)
                && Objects.equals(snippet, that.snippet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId, address, contactName, photoUri, snippet, date, unreadCount, messageCount);
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
                '}';
    }
}
