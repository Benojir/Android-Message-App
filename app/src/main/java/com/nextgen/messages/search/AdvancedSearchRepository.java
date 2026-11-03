package com.nextgen.messages.search;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.data.repository.ContactRepository;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.data.repository.StarredMessageRepository;
import com.nextgen.messages.mms.storage.MmsStorageManager;
import com.nextgen.messages.security.SuspiciousLinkAnalyzer;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Advanced search engine querying across SMS and MMS messages, contacts,
 * starred messages, multimedia attachments, links, and date ranges.
 */
public class AdvancedSearchRepository {

    private static final String TAG = "AdvancedSearchRepo";
    private static volatile AdvancedSearchRepository instance;

    private final Context context;
    private final ContactRepository contactRepo;
    private final StarredMessageRepository starredRepo;
    private final SmsRepository smsRepo;

    private AdvancedSearchRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.contactRepo = ContactRepository.getInstance(this.context);
        this.starredRepo = StarredMessageRepository.getInstance(this.context);
        this.smsRepo = SmsRepository.getInstance(this.context);
    }

    public static AdvancedSearchRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AdvancedSearchRepository.class) {
                if (instance == null) {
                    instance = new AdvancedSearchRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public interface SearchCallback {
        void onResults(@NonNull List<SearchResult> results);
    }

    public void searchAsync(@NonNull SearchFilter filter, @NonNull SearchCallback callback) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<SearchResult> results = searchSync(filter);
            AppExecutors.getInstance().mainThread().execute(() -> callback.onResults(results));
        });
    }

    @NonNull
    public List<SearchResult> searchSync(@NonNull SearchFilter filter) {
        if (!filter.hasActiveFilters()) {
            return Collections.emptyList();
        }

        List<SearchResult> results = new ArrayList<>();
        Set<String> starredKeys = starredRepo.getAllStarredKeysSync();

        // Map thread IDs to conversation summaries (for contact name / title lookup)
        List<Conversation> conversations = smsRepo.getConversationsSync();
        Map<Long, Conversation> convMap = new HashMap<>();
        for (Conversation c : conversations) {
            convMap.put(c.getThreadId(), c);
        }

        String rawQuery = filter.getQuery() != null ? filter.getQuery().trim() : "";
        String lowerQuery = rawQuery.toLowerCase(Locale.ROOT);

        boolean isAppLocked = com.nextgen.messages.security.lock.AppLockManager.getInstance(context).isLocked();
        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(context);

        // Scan conversation threads
        for (Conversation conv : conversations) {
            long threadId = conv.getThreadId();
            if (prefs.isConversationPrivate(threadId) && isAppLocked) {
                // Exclude private conversations from search previews when locked
                continue;
            }
            List<SmsMessage> threadMessages = smsRepo.getMessagesForThreadSync(threadId);

            for (SmsMessage msg : threadMessages) {
                // 1. Starred filter
                if (filter.isFilterStarred()) {
                    if (!starredKeys.contains(msg.getMessageKey())) {
                        continue;
                    }
                }

                // 2. Media filter
                if (filter.isFilterMedia()) {
                    if (!msg.isMms() && !msg.hasAttachments()) {
                        continue;
                    }
                }

                // 3. Links filter
                if (filter.isFilterLinks()) {
                    List<String> urls = SuspiciousLinkAnalyzer.extractUrls(msg.getBody());
                    if (urls.isEmpty()) {
                        continue;
                    }
                }

                // 4. Date range filter
                if (filter.getStartDate() != null && msg.getDate() < filter.getStartDate()) {
                    continue;
                }
                if (filter.getEndDate() != null && msg.getDate() > filter.getEndDate()) {
                    continue;
                }

                // 5. Query matching (body, address, contact name)
                if (!lowerQuery.isEmpty()) {
                    String body = msg.getBody() != null ? msg.getBody().toLowerCase(Locale.ROOT) : "";
                    String address = msg.getAddress() != null ? msg.getAddress().toLowerCase(Locale.ROOT) : "";
                    String title = conv.getTitle().toLowerCase(Locale.ROOT);

                    boolean matchesBody = body.contains(lowerQuery);
                    boolean matchesAddress = address.contains(lowerQuery);
                    boolean matchesTitle = title.contains(lowerQuery);

                    if (!matchesBody && !matchesAddress && !matchesTitle) {
                        continue;
                    }
                }

                String snippet = SearchHighlightUtils.getContextualSnippet(
                        msg.getDisplayBody(),
                        rawQuery,
                        80
                );

                results.add(new SearchResult(
                        msg,
                        conv.getTitle(),
                        conv.getAddress(),
                        threadId,
                        snippet
                ));
            }
        }

        // Sort results by date descending (most recent first)
        Collections.sort(results, (r1, r2) -> Long.compare(r2.getMessage().getDate(), r1.getMessage().getDate()));
        return results;
    }
}
