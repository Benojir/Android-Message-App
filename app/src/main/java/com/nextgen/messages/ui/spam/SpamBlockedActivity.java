package com.nextgen.messages.ui.spam;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.repository.ConversationMetadataRepository;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.databinding.ActivitySpamBlockedBinding;
import com.nextgen.messages.smartinbox.db.ConversationMetadataEntity;
import com.nextgen.messages.ui.conversation.ConversationActivity;
import com.nextgen.messages.ui.inbox.ConversationAdapter;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Screen displaying filtered spam and messages from blocked senders,
 * with options to restore conversations (Mark Not Spam) or unblock.
 */
public class SpamBlockedActivity extends AppCompatActivity {

    private ActivitySpamBlockedBinding binding;
    private ConversationAdapter adapter;
    private SmsRepository smsRepo;
    private ConversationMetadataRepository metadataRepo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        com.nextgen.messages.utils.ThemeHelper.applyToActivity(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySpamBlockedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        smsRepo = SmsRepository.getInstance(this);
        metadataRepo = ConversationMetadataRepository.getInstance(this);

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
        adapter = new ConversationAdapter(conversation -> {
            Intent intent = new Intent(this, ConversationActivity.class);
            intent.putExtra(ConversationActivity.EXTRA_THREAD_ID, conversation.getThreadId());
            intent.putExtra(ConversationActivity.EXTRA_ADDRESS, conversation.getAddress());
            intent.putExtra(ConversationActivity.EXTRA_DISPLAY_NAME, conversation.getTitle());
            startActivity(intent);
        });

        adapter.setOnConversationLongClickListener(conversation -> {
            CharSequence[] options = new CharSequence[]{
                    getString(R.string.action_not_spam),
                    getString(R.string.action_delete)
            };

            new MaterialAlertDialogBuilder(this)
                    .setTitle(conversation.getTitle())
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            metadataRepo.markNotSpam(conversation.getThreadId(), conversation.getAddress(), () -> {
                                loadSpamAndBlocked();
                                Snackbar.make(binding.getRoot(), R.string.toast_marked_not_spam, Snackbar.LENGTH_SHORT).show();
                            });
                        } else if (which == 1) {
                            smsRepo.deleteConversationSms(conversation.getThreadId(), null);
                            loadSpamAndBlocked();
                            Snackbar.make(binding.getRoot(), R.string.conversation_deleted, Snackbar.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        });

        binding.rvSpamBlocked.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSpamBlocked.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSpamAndBlocked();
    }

    private void loadSpamAndBlocked() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<Conversation> all = smsRepo.getConversationsSync();
            Map<Long, ConversationMetadataEntity> metaMap = metadataRepo.getMetadataMapSync();

            List<Conversation> spamList = new ArrayList<>();
            for (Conversation c : all) {
                ConversationMetadataEntity meta = metaMap.get(c.getThreadId());
                if (meta != null && (meta.isSpam() || meta.isBlocked())) {
                    spamList.add(c.withMetadata(
                            meta.isPinned(),
                            meta.getPinnedAt(),
                            meta.isArchived(),
                            meta.getArchivedAt(),
                            meta.isBlocked(),
                            meta.isSpam()
                    ));
                }
            }

            Collections.sort(spamList, (c1, c2) -> Long.compare(c2.getDate(), c1.getDate()));

            AppExecutors.getInstance().mainThread().execute(() -> {
                binding.progressLoading.setVisibility(View.GONE);
                adapter.submitList(spamList);
                binding.layoutEmptyState.setVisibility(spamList.isEmpty() ? View.VISIBLE : View.GONE);
                binding.rvSpamBlocked.setVisibility(spamList.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
