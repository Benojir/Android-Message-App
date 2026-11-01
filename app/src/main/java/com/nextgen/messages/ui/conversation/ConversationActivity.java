package com.nextgen.messages.ui.conversation;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.ActivityConversationBinding;
import com.nextgen.messages.telephony.SmsContentObserver;

/**
 * Modern, read-only Conversation History screen displaying chronological message history
 * with incoming and outgoing Material Design 3 bubbles and date headers.
 * Strictly READ-ONLY for Step 3: does NOT mark messages as read, send, or delete.
 */
public class ConversationActivity extends AppCompatActivity {

    public static final String EXTRA_THREAD_ID = "com.nextgen.messages.extra.THREAD_ID";
    public static final String EXTRA_ADDRESS = "com.nextgen.messages.extra.ADDRESS";
    public static final String EXTRA_DISPLAY_NAME = "com.nextgen.messages.extra.DISPLAY_NAME";

    private ActivityConversationBinding binding;
    private ConversationViewModel viewModel;
    private MessageAdapter messageAdapter;
    private SmsContentObserver smsObserver;

    private long threadId = -1L;
    @Nullable
    private String address;
    @Nullable
    private String displayName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        extractIntentExtras();
        setupWindowInsets();
        setupToolbar();
        setupRecyclerView();

        viewModel = new ViewModelProvider(this).get(ConversationViewModel.class);
        observeViewModel();

        if (threadId > 0) {
            viewModel.setThreadId(threadId);
        }
    }

    private void extractIntentExtras() {
        if (getIntent() != null) {
            threadId = getIntent().getLongExtra(EXTRA_THREAD_ID, -1L);
            address = getIntent().getStringExtra(EXTRA_ADDRESS);
            displayName = getIntent().getStringExtra(EXTRA_DISPLAY_NAME);
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.footerReadonlyCard, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.bottomMargin = insets.bottom + 12;
            v.setLayoutParams(lp);
            return windowInsets;
        });
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String title = displayName != null && !displayName.trim().isEmpty() ? displayName : address;
        if (title == null || title.trim().isEmpty()) {
            title = getString(R.string.conversation_title_unknown);
        }
        binding.toolbar.setTitle(title);

        if (address != null && !address.trim().isEmpty() && !address.equals(displayName)) {
            binding.toolbar.setSubtitle(address.trim());
        } else {
            binding.toolbar.setSubtitle(null);
        }
    }

    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);

        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(messageAdapter);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            binding.progressLoading.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });

        viewModel.getMessageItems().observe(this, items -> {
            messageAdapter.submitList(items, () -> {
                if (items != null && !items.isEmpty()) {
                    binding.rvMessages.scrollToPosition(items.size() - 1);
                }
            });
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (smsObserver == null) {
            smsObserver = SmsContentObserver.createOnMainThread(() -> {
                if (viewModel != null) {
                    viewModel.refresh();
                }
            });
        }
        smsObserver.register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (smsObserver != null) {
            smsObserver.unregister(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
