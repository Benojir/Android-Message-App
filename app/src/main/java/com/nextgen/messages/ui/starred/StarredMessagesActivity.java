package com.nextgen.messages.ui.starred;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.data.repository.StarredMessageRepository;
import com.nextgen.messages.databinding.ActivityStarredMessagesBinding;
import com.nextgen.messages.smartinbox.db.StarredMessageEntity;
import com.nextgen.messages.ui.conversation.ConversationActivity;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Screen displaying all starred SMS and MMS messages with click-to-locate navigation
 * and unstarring support.
 */
public class StarredMessagesActivity extends AppCompatActivity {

    private ActivityStarredMessagesBinding binding;
    private StarredMessagesAdapter adapter;
    private StarredMessageRepository starredRepo;
    private SmsRepository smsRepo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityStarredMessagesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        starredRepo = StarredMessageRepository.getInstance(this);
        smsRepo = SmsRepository.getInstance(this);

        setupWindowInsets();
        setupToolbar();
        setupRecyclerView();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new StarredMessagesAdapter(
                item -> {
                    Intent intent = new Intent(this, ConversationActivity.class);
                    intent.putExtra(ConversationActivity.EXTRA_THREAD_ID, item.getThreadId());
                    intent.putExtra(ConversationActivity.EXTRA_ADDRESS, item.getAddress());
                    intent.putExtra(ConversationActivity.EXTRA_DISPLAY_NAME, item.getSenderTitle());
                    intent.putExtra(ConversationActivity.EXTRA_HIGHLIGHT_MESSAGE_KEY, item.getMessage().getMessageKey());
                    startActivity(intent);
                },
                item -> {
                    starredRepo.unstarMessage(item.getMessage().getMessageKey(), () -> {
                        loadStarredMessages();
                        Snackbar.make(binding.getRoot(), R.string.toast_message_unstarred, Snackbar.LENGTH_SHORT).show();
                    });
                }
        );

        binding.rvStarred.setLayoutManager(new LinearLayoutManager(this));
        binding.rvStarred.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStarredMessages();
    }

    private void loadStarredMessages() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<StarredMessageEntity> entities = starredRepo.getAllStarredMessagesSync();
            if (entities.isEmpty()) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    adapter.submitList(Collections.emptyList());
                    binding.layoutEmptyState.setVisibility(View.VISIBLE);
                    binding.rvStarred.setVisibility(View.GONE);
                });
                return;
            }

            // Map thread IDs to conversation summaries
            List<Conversation> conversations = smsRepo.getConversationsSync();
            Map<Long, Conversation> convMap = new HashMap<>();
            for (Conversation c : conversations) {
                convMap.put(c.getThreadId(), c);
            }

            List<StarredMessagesAdapter.StarredItem> starredItems = new ArrayList<>();
            for (StarredMessageEntity entity : entities) {
                long threadId = entity.getThreadId();
                Conversation conv = convMap.get(threadId);
                String senderTitle = conv != null ? conv.getTitle() : "Message";
                String address = conv != null ? conv.getAddress() : null;

                List<SmsMessage> threadMessages = smsRepo.getMessagesForThreadSync(threadId);
                SmsMessage matchedMsg = null;
                for (SmsMessage m : threadMessages) {
                    if (m.getMessageKey().equals(entity.getMessageKey())) {
                        matchedMsg = m;
                        break;
                    }
                }

                if (matchedMsg != null) {
                    starredItems.add(new StarredMessagesAdapter.StarredItem(
                            matchedMsg.withStep8State(true, false, null),
                            senderTitle,
                            address,
                            threadId
                    ));
                }
            }

            Collections.sort(starredItems, (s1, s2) -> Long.compare(s2.getMessage().getDate(), s1.getMessage().getDate()));

            AppExecutors.getInstance().mainThread().execute(() -> {
                binding.progressLoading.setVisibility(View.GONE);
                adapter.submitList(starredItems);
                binding.layoutEmptyState.setVisibility(starredItems.isEmpty() ? View.VISIBLE : View.GONE);
                binding.rvStarred.setVisibility(starredItems.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
