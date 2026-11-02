package com.nextgen.messages.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.model.SmsMessage;

/**
 * Model representing a single matching message result from an advanced search query.
 */
public class SearchResult {

    @NonNull
    private final SmsMessage message;
    @NonNull
    private final String conversationTitle;
    @Nullable
    private final String conversationAddress;
    private final long threadId;
    @NonNull
    private final String snippet;

    public SearchResult(
            @NonNull SmsMessage message,
            @NonNull String conversationTitle,
            @Nullable String conversationAddress,
            long threadId,
            @NonNull String snippet
    ) {
        this.message = message;
        this.conversationTitle = conversationTitle;
        this.conversationAddress = conversationAddress;
        this.threadId = threadId;
        this.snippet = snippet;
    }

    @NonNull
    public SmsMessage getMessage() {
        return message;
    }

    @NonNull
    public String getConversationTitle() {
        return conversationTitle;
    }

    @Nullable
    public String getConversationAddress() {
        return conversationAddress;
    }

    public long getThreadId() {
        return threadId;
    }

    @NonNull
    public String getSnippet() {
        return snippet;
    }
}
