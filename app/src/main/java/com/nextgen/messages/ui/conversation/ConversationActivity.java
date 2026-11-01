package com.nextgen.messages.ui.conversation;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.databinding.ActivityConversationBinding;
import com.nextgen.messages.telephony.SimInfo;
import com.nextgen.messages.telephony.SimManager;
import com.nextgen.messages.telephony.SmsContentObserver;
import com.nextgen.messages.telephony.SmsEncodingUtils;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.telephony.SmsSender;
import com.nextgen.messages.telephony.SmsStorageManager;
import java.util.List;

/**
 * Modern Conversation History and Active SMS Messaging screen.
 * Handles real-time message sending via Android SmsManager, Dual SIM switching,
 * automatic draft preservation, delivery status tracking, and manual retry for failed messages.
 */
public class ConversationActivity extends AppCompatActivity {

    public static final String EXTRA_THREAD_ID = "com.nextgen.messages.extra.THREAD_ID";
    public static final String EXTRA_ADDRESS = "com.nextgen.messages.extra.ADDRESS";
    public static final String EXTRA_DISPLAY_NAME = "com.nextgen.messages.extra.DISPLAY_NAME";

    private ActivityConversationBinding binding;
    private ConversationViewModel viewModel;
    private MessageAdapter messageAdapter;
    private SmsContentObserver smsObserver;

    private SmsSender smsSender;
    private SmsStorageManager storageManager;
    private SimManager simManager;
    private SmsRoleManager roleManager;

    private long threadId = -1L;
    @Nullable
    private String address;
    @Nullable
    private String displayName;

    @Nullable
    private SimInfo selectedSim;

    private final ActivityResultLauncher<Intent> roleRequestLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                updateRoleStatusUI();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        smsSender = SmsSender.getInstance(this);
        storageManager = SmsStorageManager.getInstance(this);
        simManager = SimManager.getInstance(this);
        roleManager = new SmsRoleManager(this);

        extractIntentExtras();
        setupWindowInsets();
        setupToolbar();
        setupRecyclerView();
        setupSimSelector();
        setupComposer();
        restoreDraft();

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.composerContainer, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom + 8);
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
        messageAdapter = new MessageAdapter(this::onRetryMessageRequested);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);

        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(messageAdapter);
    }

    private void setupSimSelector() {
        List<SimInfo> activeSims = simManager.getActiveSims();
        if (activeSims.isEmpty()) {
            binding.btnSimSelect.setVisibility(View.GONE);
            selectedSim = null;
            return;
        }

        binding.btnSimSelect.setVisibility(View.VISIBLE);
        selectedSim = simManager.getEffectiveSimForRecipient(address);
        if (selectedSim == null) {
            selectedSim = activeSims.get(0);
        }

        updateSimButtonLabel();

        binding.btnSimSelect.setOnClickListener(v -> showSimChooserDialog());
    }

    private void updateSimButtonLabel() {
        if (selectedSim != null) {
            binding.btnSimSelect.setText(selectedSim.getLabel());
        }
    }

    private void showSimChooserDialog() {
        List<SimInfo> sims = simManager.getActiveSims();
        if (sims.isEmpty()) {
            return;
        }

        String[] simLabels = new String[sims.size()];
        int selectedIndex = 0;
        for (int i = 0; i < sims.size(); i++) {
            simLabels[i] = sims.get(i).getFullLabel();
            if (selectedSim != null && sims.get(i).getSubscriptionId() == selectedSim.getSubscriptionId()) {
                selectedIndex = i;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sim_dialog_title)
                .setSingleChoiceItems(simLabels, selectedIndex, (dialog, which) -> {
                    selectedSim = sims.get(which);
                    updateSimButtonLabel();
                    if (address != null && !address.trim().isEmpty()) {
                        simManager.setPreferredSimForRecipient(address, selectedSim.getSubscriptionId());
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void setupComposer() {
        // Send button disabled initially
        binding.btnSendMessage.setEnabled(false);
        binding.btnSendMessage.setAlpha(0.4f);

        binding.etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = (s != null) ? s.toString() : "";
                boolean hasText = !text.trim().isEmpty();

                binding.btnSendMessage.setEnabled(hasText);
                binding.btnSendMessage.setAlpha(hasText ? 1.0f : 0.4f);

                if (hasText) {
                    SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(text);
                    binding.tvCounter.setText(info.getCounterLabel());
                    binding.tvCounter.setVisibility(View.VISIBLE);
                } else {
                    binding.tvCounter.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnSendMessage.setOnClickListener(v -> onSendMessageClicked());

        binding.btnSetDefaultRole.setOnClickListener(v -> {
            roleManager.requestDefaultSmsRole(roleRequestLauncher);
        });
    }

    private void onSendMessageClicked() {
        if (!roleManager.isDefaultSmsApp()) {
            roleManager.requestDefaultSmsRole(roleRequestLauncher);
            return;
        }

        String recipient = (address != null && !address.trim().isEmpty()) ? address.trim() : "";
        if (recipient.isEmpty()) {
            Toast.makeText(this, R.string.compose_missing_recipient_warning, Toast.LENGTH_SHORT).show();
            return;
        }

        String messageBody = binding.etMessageInput.getText() != null
                ? binding.etMessageInput.getText().toString().trim()
                : "";

        if (messageBody.isEmpty()) {
            return;
        }

        int subId = (selectedSim != null) ? selectedSim.getSubscriptionId() : -1;

        // Clear input and clear draft
        binding.etMessageInput.setText("");
        if (threadId > 0) {
            storageManager.clearDraft(threadId);
        }

        // Send via SmsSender
        smsSender.sendMessage(threadId, recipient, messageBody, subId, new SmsSender.SendCallback() {
            @Override
            public void onMessageQueued(@NonNull android.net.Uri messageUri) {
                // UI automatically refreshes via SmsContentObserver
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                Toast.makeText(ConversationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onRetryMessageRequested(@NonNull SmsMessage failedMessage) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.retry_dialog_title)
                .setMessage(R.string.retry_dialog_message)
                .setPositiveButton(R.string.retry_button, (dialog, which) -> {
                    int subId = (selectedSim != null) ? selectedSim.getSubscriptionId() : failedMessage.getSubscriptionId();
                    smsSender.retryMessage(failedMessage, subId, new SmsSender.SendCallback() {
                        @Override
                        public void onMessageQueued(@NonNull android.net.Uri messageUri) {}

                        @Override
                        public void onError(@NonNull String errorMessage) {
                            Toast.makeText(ConversationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void restoreDraft() {
        if (threadId > 0) {
            String draft = storageManager.getDraft(threadId);
            if (!draft.isEmpty()) {
                binding.etMessageInput.setText(draft);
                binding.etMessageInput.setSelection(draft.length());
            }
        }
    }

    private void saveDraft() {
        if (threadId > 0 && binding.etMessageInput.getText() != null) {
            String currentText = binding.etMessageInput.getText().toString();
            storageManager.saveDraft(threadId, currentText);
        }
    }

    private void updateRoleStatusUI() {
        boolean isDefault = roleManager.isDefaultSmsApp();
        binding.cardRoleWarning.setVisibility(isDefault ? View.GONE : View.VISIBLE);
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
    protected void onResume() {
        super.onResume();
        updateRoleStatusUI();
        if (viewModel != null) {
            viewModel.refresh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveDraft();
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
