package com.nextgen.messages.ui.archived;

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
import com.nextgen.messages.databinding.ActivityArchivedBinding;
import com.nextgen.messages.smartinbox.db.ConversationMetadataEntity;
import com.nextgen.messages.ui.conversation.ConversationActivity;
import com.nextgen.messages.ui.inbox.ConversationAdapter;
import com.nextgen.messages.utils.AppExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Screen displaying archived conversation threads safely secluded from the main inbox.
 * Allows viewing conversations, unarchiving, and full thread access.
 */
public class ArchivedActivity extends AppCompatActivity {

    private ActivityArchivedBinding binding;
    private ConversationAdapter adapter;
    private SmsRepository smsRepository;
    private ConversationMetadataRepository metadataRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityArchivedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        smsRepository = SmsRepository.getInstance(this);
        metadataRepository = ConversationMetadataRepository.getInstance(this);

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
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.action_unarchive)
                    .setMessage(getString(R.string.dialog_block_message))
                    .setPositiveButton(R.string.action_unarchive, (d, w) -> {
                        metadataRepository.setArchived(conversation.getThreadId(), false, () -> {
                            loadArchived();
                            Snackbar.make(binding.getRoot(), R.string.toast_unarchived, Snackbar.LENGTH_SHORT).show();
                        });
                    })
                    .setNegativeButton(R.string.dialog_button_cancel, null)
                    .show();
        });

        binding.rvArchived.setLayoutManager(new LinearLayoutManager(this));
        binding.rvArchived.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadArchived();
    }

    private void loadArchived() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<Conversation> all = smsRepository.getConversationsSync();
            Map<Long, ConversationMetadataEntity> metaMap = metadataRepository.getMetadataMapSync();

            List<Conversation> archived = new ArrayList<>();
            for (Conversation c : all) {
                ConversationMetadataEntity meta = metaMap.get(c.getThreadId());
                if (meta != null && meta.isArchived()) {
                    archived.add(c.withMetadata(
                            meta.isPinned(),
                            meta.getPinnedAt(),
                            true,
                            meta.getArchivedAt(),
                            meta.isBlocked(),
                            meta.isSpam()
                    ));
                }
            }

            Collections.sort(archived, (c1, c2) -> Long.compare(c2.getDate(), c1.getDate()));

            AppExecutors.getInstance().mainThread().execute(() -> {
                binding.progressLoading.setVisibility(View.GONE);
                adapter.submitList(archived);
                binding.layoutEmptyState.setVisibility(archived.isEmpty() ? View.VISIBLE : View.GONE);
                binding.rvArchived.setVisibility(archived.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
