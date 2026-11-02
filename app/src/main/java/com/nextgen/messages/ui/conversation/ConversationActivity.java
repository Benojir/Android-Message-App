package com.nextgen.messages.ui.conversation;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.ContactInfo;
import com.nextgen.messages.data.model.MessageListItem;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.data.repository.ContactRepository;
import com.nextgen.messages.data.repository.ConversationMetadataRepository;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.notification.MessageNotificationManager;
import com.nextgen.messages.databinding.ActivityConversationBinding;
import com.nextgen.messages.telephony.SimInfo;
import com.nextgen.messages.telephony.SimManager;
import com.nextgen.messages.telephony.SmsContentObserver;
import com.nextgen.messages.telephony.SmsEncodingUtils;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.telephony.SmsSender;
import com.nextgen.messages.telephony.SmsStorageManager;
import com.nextgen.messages.ui.compose.ComposeActivity;
import com.nextgen.messages.mms.transport.MmsSender;
import com.nextgen.messages.mms.transport.MmsDownloadManager;
import com.nextgen.messages.mms.transport.MmsMediaUtils;
import com.nextgen.messages.data.model.MmsAttachment;
import com.nextgen.messages.ui.viewers.ImageViewerActivity;
import com.nextgen.messages.ui.viewers.VideoViewerActivity;
import com.nextgen.messages.utils.AppExecutors;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.format.Formatter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Modern Conversation History and Active SMS Messaging screen.
 * Handles real-time message sending, multi-selection, copying, forwarding,
 * safe undoable deletion, message details dialog, Dual SIM, and auto-scrolling.
 */
public class ConversationActivity extends AppCompatActivity {

    public static final String EXTRA_THREAD_ID = "com.nextgen.messages.extra.THREAD_ID";
    public static final String EXTRA_ADDRESS = "com.nextgen.messages.extra.ADDRESS";
    public static final String EXTRA_DISPLAY_NAME = "com.nextgen.messages.extra.DISPLAY_NAME";
    public static final String EXTRA_HIGHLIGHT_MESSAGE_KEY = "extra_highlight_message_key";

    private ActivityConversationBinding binding;
    private ConversationViewModel viewModel;
    private MessageAdapter messageAdapter;
    private SmsContentObserver smsObserver;

    private SmsSender smsSender;
    private MmsSender mmsSender;
    private SmsStorageManager storageManager;
    private SimManager simManager;
    private SmsRoleManager roleManager;
    private ContactRepository contactRepository;

    private long threadId = -1L;
    @Nullable
    private String address;
    @Nullable
    private String displayName;
    @Nullable
    private String highlightMessageKey;

    @Nullable
    private SimInfo selectedSim;

    private final List<Uri> attachedUris = new ArrayList<>();

    private final ActivityResultLauncher<String> mediaPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onMediaPicked);

    private final ActivityResultLauncher<Intent> roleRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                updateRoleStatusUI();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        binding = ActivityConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        smsSender = SmsSender.getInstance(this);
        mmsSender = MmsSender.getInstance(this);
        storageManager = SmsStorageManager.getInstance(this);
        simManager = SimManager.getInstance(this);
        roleManager = new SmsRoleManager(this);
        contactRepository = ContactRepository.getInstance(this);

        extractIntentExtras();
        resolveAddressIfMissing();
        setupWindowInsets();
        setupToolbar();
        setupSelectionToolbar();
        setupRecyclerView();
        setupScrollToBottomFab();
        setupSimSelector();
        setupComposer();
        setupBackPressHandler();
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
            highlightMessageKey = getIntent().getStringExtra(EXTRA_HIGHLIGHT_MESSAGE_KEY);
        }
    }

    private void resolveAddressIfMissing() {
        if ((address == null || address.trim().isEmpty()) && threadId > 0) {
            com.nextgen.messages.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                String[] projection = new String[]{android.provider.Telephony.Sms.ADDRESS};
                String selection = android.provider.Telephony.Sms.THREAD_ID + " = ? AND " + android.provider.Telephony.Sms.ADDRESS + " IS NOT NULL";
                String[] args = new String[]{String.valueOf(threadId)};
                try (android.database.Cursor cursor = getContentResolver().query(
                        android.provider.Telephony.Sms.CONTENT_URI,
                        projection,
                        selection,
                        args,
                        android.provider.Telephony.Sms.DATE + " DESC"
                )) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int addrIdx = cursor.getColumnIndex(android.provider.Telephony.Sms.ADDRESS);
                        if (addrIdx >= 0) {
                            String found = cursor.getString(addrIdx);
                            if (found != null && !found.trim().isEmpty()) {
                                address = found.trim();
                                com.nextgen.messages.utils.AppExecutors.getInstance().mainThread().execute(this::setupToolbar);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            });
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
        binding.toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClick);

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

    private boolean onToolbarMenuItemClick(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_call) {
            onCallContactClicked();
            return true;
        } else if (id == R.id.action_mute_conversation) {
            showMuteDialog();
            return true;
        } else if (id == R.id.action_archive_conversation) {
            onArchiveConversationClicked();
            return true;
        } else if (id == R.id.action_block_sender) {
            onBlockSenderClicked();
            return true;
        } else if (id == R.id.action_report_spam) {
            onReportSpamClicked();
            return true;
        } else if (id == R.id.action_view_contact) {
            onViewContactClicked();
            return true;
        } else if (id == R.id.action_copy_number) {
            onCopyNumberClicked();
            return true;
        } else if (id == R.id.action_mark_unread) {
            onMarkConversationUnreadClicked();
            return true;
        } else if (id == R.id.action_delete_conversation) {
            onDeleteConversationClicked();
            return true;
        }
        return false;
    }

    private void setupSelectionToolbar() {
        binding.toolbarSelection.setNavigationOnClickListener(v -> clearSelection());
        binding.toolbarSelection.setOnMenuItemClickListener(this::onSelectionMenuItemClick);
    }

    private boolean onSelectionMenuItemClick(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_star) {
            viewModel.toggleStarForSelected(messageAdapter.getSelectedIds());
            clearSelection();
            return true;
        } else if (id == R.id.action_copy) {
            onCopySelectedMessages();
            return true;
        } else if (id == R.id.action_forward) {
            onForwardSelectedMessage();
            return true;
        } else if (id == R.id.action_details) {
            onShowMessageDetails();
            return true;
        } else if (id == R.id.action_delete) {
            onDeleteSelectedMessagesClicked();
            return true;
        } else if (id == R.id.action_select_all) {
            messageAdapter.selectAll();
            updateSelectionUI();
            return true;
        }
        return false;
    }

    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter(this::onRetryMessageRequested);
        if (highlightMessageKey != null) {
            messageAdapter.setHighlightedMessageKey(highlightMessageKey);
        }
        messageAdapter.setOnLinkClickListener((url, isSuspicious, reason) -> {
            showSafeLinkConfirmationDialog(url, isSuspicious, reason);
        });
        messageAdapter.setOnMessageClickListener((message, position) -> updateSelectionUI());
        messageAdapter.setOnMessageLongClickListener((message, position) -> updateSelectionUI());
        messageAdapter.setOnAttachmentClickListener(new MessageAdapter.OnAttachmentClickListener() {
            @Override
            public void onImageClick(@NonNull MmsAttachment attachment) {
                ImageViewerActivity.launch(ConversationActivity.this, attachment.getPartUri(), attachment.getContentType(), attachment.getName());
            }

            @Override
            public void onVideoClick(@NonNull MmsAttachment attachment) {
                VideoViewerActivity.launch(ConversationActivity.this, attachment.getPartUri(), attachment.getName());
            }
        });
        messageAdapter.setOnDownloadMmsListener(message -> {
            if (message.getContentLocation() != null) {
                Toast.makeText(ConversationActivity.this, R.string.action_download_mms, Toast.LENGTH_SHORT).show();
                MmsDownloadManager.getInstance(ConversationActivity.this).downloadMms(
                        message.getId(),
                        message.getThreadId(),
                        message.getContentLocation(),
                        message.getSubscriptionId(),
                        message.getAddress()
                );
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);

        binding.rvMessages.setLayoutManager(layoutManager);
        binding.rvMessages.setAdapter(messageAdapter);

        binding.rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                int totalItems = messageAdapter.getItemCount();
                if (totalItems > 0 && lastVisible < totalItems - 3) {
                    binding.fabScrollToBottom.show();
                } else {
                    binding.fabScrollToBottom.hide();
                }
            }
        });
    }

    private void setupScrollToBottomFab() {
        binding.fabScrollToBottom.setOnClickListener(v -> {
            int total = messageAdapter.getItemCount();
            if (total > 0) {
                binding.rvMessages.smoothScrollToPosition(total - 1);
            }
        });
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (messageAdapter != null && messageAdapter.isInSelectionMode()) {
                    clearSelection();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void updateSelectionUI() {
        int count = messageAdapter.getSelectedCount();
        if (count > 0) {
            binding.toolbar.setVisibility(View.GONE);
            binding.toolbarSelection.setVisibility(View.VISIBLE);
            binding.toolbarSelection.setTitle(getString(R.string.selected_count, count));

            Menu menu = binding.toolbarSelection.getMenu();
            MenuItem forwardItem = menu.findItem(R.id.action_forward);
            MenuItem detailsItem = menu.findItem(R.id.action_details);

            if (forwardItem != null) forwardItem.setVisible(count == 1);
            if (detailsItem != null) detailsItem.setVisible(count == 1);
        } else {
            binding.toolbarSelection.setVisibility(View.GONE);
            binding.toolbar.setVisibility(View.VISIBLE);
        }
    }

    private void clearSelection() {
        messageAdapter.clearSelection();
        updateSelectionUI();
    }

    private void onCopySelectedMessages() {
        List<SmsMessage> selected = messageAdapter.getSelectedMessages();
        if (selected.isEmpty()) {
            clearSelection();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            String body = selected.get(i).getBody();
            if (body != null && !body.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(body);
            }
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("NextGen SMS", sb.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
        }
        clearSelection();
    }

    private void onForwardSelectedMessage() {
        List<SmsMessage> selected = messageAdapter.getSelectedMessages();
        if (selected.isEmpty()) {
            clearSelection();
            return;
        }

        SmsMessage target = selected.get(0);
        String body = target.getDisplayBody();

        Intent forwardIntent = new Intent(this, ComposeActivity.class);
        forwardIntent.putExtra(Intent.EXTRA_TEXT, body);
        forwardIntent.putExtra("sms_body", body);
        startActivity(forwardIntent);

        clearSelection();
    }

    private void onShowMessageDetails() {
        List<SmsMessage> selected = messageAdapter.getSelectedMessages();
        if (selected.isEmpty()) {
            clearSelection();
            return;
        }

        SmsMessage target = selected.get(0);
        ContactInfo contactInfo = (address != null) ? contactRepository.resolveContact(address) : null;
        MessageDetailsDialog.show(this, target, contactInfo);
        clearSelection();
    }

    private void onDeleteSelectedMessagesClicked() {
        if (!roleManager.isDefaultSmsApp()) {
            Toast.makeText(this, R.string.role_required_for_deletion, Toast.LENGTH_LONG).show();
            roleManager.requestDefaultSmsRole(roleRequestLauncher);
            return;
        }

        final Set<Long> targets = new HashSet<>(messageAdapter.getSelectedIds());
        if (targets.isEmpty()) {
            clearSelection();
            return;
        }

        int count = targets.size();
        String title = (count == 1)
                ? getString(R.string.dialog_delete_message_title)
                : getString(R.string.dialog_delete_messages_title, count);
        String message = (count == 1)
                ? getString(R.string.dialog_delete_message_desc)
                : getString(R.string.dialog_delete_messages_desc, count);

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.delete_button, (dialog, which) -> {
                    clearSelection();
                    performDelayedDeleteMessages(targets);
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void performDelayedDeleteMessages(@NonNull Set<Long> messageIds) {
        viewModel.markMessagesPendingDelete(messageIds);

        int count = messageIds.size();
        String snackbarText = (count == 1)
                ? getString(R.string.message_deleted)
                : getString(R.string.messages_deleted, count);

        Snackbar snackbar = Snackbar.make(binding.getRoot(), snackbarText, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v -> {
            viewModel.cancelPendingDelete(messageIds);
        });

        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    viewModel.commitPendingDelete(messageIds, new SmsRepository.DataCallback<Integer>() {
                        @Override
                        public void onSuccess(Integer rows) {}

                        @Override
                        public void onError(Throwable throwable) {
                            Toast.makeText(ConversationActivity.this, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
        snackbar.show();
    }

    private void onCallContactClicked() {
        if (address != null && !address.trim().isEmpty()) {
            Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + address.trim()));
            try {
                startActivity(callIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not launch dialer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onViewContactClicked() {
        if (address == null || address.trim().isEmpty()) {
            Toast.makeText(this, R.string.contact_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        ContactInfo info = contactRepository.resolveContact(address);
        Intent contactIntent;
        if (info.getPhotoUri() != null) {
            contactIntent = new Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(info.getNormalizedPhoneNumber())));
        } else {
            contactIntent = new Intent(ContactsContract.Intents.SHOW_OR_CREATE_CONTACT, Uri.fromParts("tel", address.trim(), null));
        }

        try {
            startActivity(contactIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onCopyNumberClicked() {
        if (address != null && !address.trim().isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("Phone Number", address.trim());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.number_copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onMarkConversationUnreadClicked() {
        viewModel.markThreadAsUnread(new SmsRepository.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                Toast.makeText(ConversationActivity.this, R.string.toast_mark_unread_success, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(Throwable throwable) {
                Toast.makeText(ConversationActivity.this, "Error marking unread: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onDeleteConversationClicked() {
        if (!roleManager.isDefaultSmsApp()) {
            Toast.makeText(this, R.string.role_required_for_deletion, Toast.LENGTH_LONG).show();
            roleManager.requestDefaultSmsRole(roleRequestLauncher);
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_conversation_title)
                .setMessage(R.string.dialog_delete_conversation_desc)
                .setPositiveButton(R.string.delete_button, (dialog, which) -> {
                    viewModel.deleteEntireConversation(new SmsRepository.DataCallback<Integer>() {
                        @Override
                        public void onSuccess(Integer rows) {
                            Toast.makeText(ConversationActivity.this, R.string.conversation_deleted, Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            Toast.makeText(ConversationActivity.this, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
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
        updateComposerState();

        binding.etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateComposerState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnAttach.setOnClickListener(v -> {
            try {
                mediaPickerLauncher.launch("*/*");
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open media picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnRemoveAttachment.setOnClickListener(v -> removeAttachment());
        binding.btnSendMessage.setOnClickListener(v -> onSendMessageClicked());
        binding.btnSetDefaultRole.setOnClickListener(v -> roleManager.requestDefaultSmsRole(roleRequestLauncher));
    }

    private void onMediaPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }

        attachedUris.clear();
        attachedUris.add(uri);

        displayAttachmentPreview(uri);
        updateComposerState();
    }

    private void displayAttachmentPreview(@NonNull Uri uri) {
        binding.layoutAttachmentPreview.setVisibility(View.VISIBLE);

        String fileName = MmsMediaUtils.getFileName(this, uri);
        String mimeType = MmsMediaUtils.getMediaMimeType(this, uri);
        long fileSize = MmsMediaUtils.getFileSize(this, uri);

        binding.tvAttachmentTitle.setText(fileName);
        if (fileSize > 0) {
            binding.tvAttachmentSize.setText(Formatter.formatFileSize(this, fileSize) + " (MMS)");
        } else {
            binding.tvAttachmentSize.setText("MMS Attachment");
        }

        if (mimeType.startsWith("image/")) {
            AppExecutors.getInstance().diskIO().execute(() -> {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap thumb = BitmapFactory.decodeStream(is, null, options);
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        if (thumb != null) {
                            binding.ivAttachmentPreview.setImageBitmap(thumb);
                        } else {
                            binding.ivAttachmentPreview.setImageResource(R.drawable.ic_image);
                        }
                    });
                } catch (Exception e) {
                    AppExecutors.getInstance().mainThread().execute(() -> {
                        binding.ivAttachmentPreview.setImageResource(R.drawable.ic_image);
                    });
                }
            });
        } else if (mimeType.startsWith("video/")) {
            binding.ivAttachmentPreview.setImageResource(R.drawable.ic_videocam);
        } else if (mimeType.startsWith("audio/")) {
            binding.ivAttachmentPreview.setImageResource(R.drawable.ic_audiotrack);
        } else {
            binding.ivAttachmentPreview.setImageResource(R.drawable.ic_attach_file);
        }
    }

    private void removeAttachment() {
        attachedUris.clear();
        binding.layoutAttachmentPreview.setVisibility(View.GONE);
        binding.ivAttachmentPreview.setImageDrawable(null);
        updateComposerState();
    }

    private void updateComposerState() {
        String text = (binding.etMessageInput.getText() != null)
                ? binding.etMessageInput.getText().toString().trim()
                : "";
        boolean hasText = !text.isEmpty();
        boolean hasAttachment = !attachedUris.isEmpty();
        boolean canSend = hasText || hasAttachment;

        binding.btnSendMessage.setEnabled(canSend);
        binding.btnSendMessage.setAlpha(canSend ? 1.0f : 0.4f);

        boolean isGroup = address != null && (address.contains(",") || address.contains(";"));
        if (hasText && !hasAttachment && !isGroup) {
            SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(text);
            binding.tvCounter.setText(info.getCounterLabel());
            binding.tvCounter.setVisibility(View.VISIBLE);
        } else {
            binding.tvCounter.setVisibility(View.GONE);
        }
    }

    private void onSendMessageClicked() {
        try {
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

            boolean hasAttachment = !attachedUris.isEmpty();
            if (messageBody.isEmpty() && !hasAttachment) {
                return;
            }

            int subId = (selectedSim != null) ? selectedSim.getSubscriptionId() : -1;

            // Check if group MMS or has attachment -> send MMS
            List<String> recipientList = new ArrayList<>();
            String[] splitAddresses = recipient.split("[,;]");
            for (String r : splitAddresses) {
                if (!r.trim().isEmpty()) {
                    recipientList.add(r.trim());
                }
            }
            if (recipientList.isEmpty()) {
                recipientList.add(recipient);
            }

            boolean isMms = hasAttachment || recipientList.size() > 1;

            final String textToSend = messageBody;
            final List<Uri> attachmentsToSend = new ArrayList<>(attachedUris);

            removeAttachment();
            binding.etMessageInput.setText("");
            if (threadId > 0) {
                storageManager.clearDraft(threadId);
            }

            if (isMms) {
                mmsSender.sendMms(
                        threadId,
                        recipientList,
                        null,
                        textToSend.isEmpty() ? null : textToSend,
                        attachmentsToSend.isEmpty() ? null : attachmentsToSend,
                        subId,
                        new MmsSender.MmsSendCallback() {
                            @Override
                            public void onMmsPrepared(long mmsId) {
                                // MMS record persisted and sent to system
                            }

                            @Override
                            public void onError(String errorMessage) {
                                binding.etMessageInput.setText(textToSend);
                                binding.etMessageInput.setSelection(textToSend.length());
                                Toast.makeText(ConversationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        }
                );
            } else {
                smsSender.sendMessage(threadId, recipient, textToSend, subId, new SmsSender.SendCallback() {
                    @Override
                    public void onMessageQueued(@NonNull android.net.Uri messageUri) {}

                    @Override
                    public void onError(@NonNull String errorMessage) {
                        binding.etMessageInput.setText(textToSend);
                        binding.etMessageInput.setSelection(textToSend.length());
                        Toast.makeText(ConversationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Failed to send: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onRetryMessageRequested(@NonNull SmsMessage failedMessage) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.retry_dialog_title)
                .setMessage(R.string.retry_dialog_message)
                .setPositiveButton(R.string.retry_button, (dialog, which) -> {
                    int subId = (selectedSim != null) ? selectedSim.getSubscriptionId() : failedMessage.getSubscriptionId();
                    String retryRecipient = failedMessage.getAddress();
                    if ((retryRecipient == null || retryRecipient.trim().isEmpty()) && address != null) {
                        retryRecipient = address.trim();
                    }
                    if (retryRecipient != null && !retryRecipient.isEmpty() && failedMessage.getBody() != null) {
                        smsSender.sendMessage(failedMessage.getThreadId(), retryRecipient, failedMessage.getBody(), subId, new SmsSender.SendCallback() {
                            @Override
                            public void onMessageQueued(@NonNull android.net.Uri messageUri) {}

                            @Override
                            public void onError(@NonNull String errorMessage) {
                                Toast.makeText(ConversationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        smsSender.retryMessage(failedMessage, subId, new SmsSender.SendCallback() {
                            @Override
                            public void onMessageQueued(@NonNull android.net.Uri messageUri) {}

                            @Override
                            public void onError(@NonNull String errorMessage) {
                                Toast.makeText(ConversationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
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
            LinearLayoutManager lm = (LinearLayoutManager) binding.rvMessages.getLayoutManager();
            boolean wasNearBottom = lm != null && lm.findLastVisibleItemPosition() >= messageAdapter.getItemCount() - 2;

            messageAdapter.submitList(items, () -> {
                if (highlightMessageKey != null && items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        MessageListItem item = items.get(i);
                        if (item instanceof MessageListItem.MessageItem) {
                            SmsMessage msg = ((MessageListItem.MessageItem) item).getMessage();
                            if (highlightMessageKey.equals(msg.getMessageKey())) {
                                binding.rvMessages.scrollToPosition(i);
                                break;
                            }
                        }
                    }
                } else if (items != null && !items.isEmpty() && wasNearBottom) {
                    binding.rvMessages.scrollToPosition(items.size() - 1);
                }
            });
        });

        binding.btnNotSpam.setOnClickListener(v -> {
            ConversationMetadataRepository.getInstance(this).markNotSpam(threadId, address, () -> {
                Toast.makeText(this, R.string.toast_marked_not_spam, Toast.LENGTH_SHORT).show();
                binding.cardSpamBanner.setVisibility(View.GONE);
            });
        });

        if (threadId > 0) {
            AppExecutors.getInstance().diskIO().execute(() -> {
                boolean isSpam = ConversationMetadataRepository.getInstance(this).getOrCreateMetadata(threadId).isSpam();
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (binding != null) {
                        binding.cardSpamBanner.setVisibility(isSpam ? View.VISIBLE : View.GONE);
                    }
                });
            });
        }

    }


    private void showMuteDialog() {
        if (threadId <= 0) return;
        boolean isMuted = ConversationMetadataRepository.getInstance(this).isConversationMuted(threadId);
        if (isMuted) {
            ConversationMetadataRepository.getInstance(this).unmuteConversation(threadId, () -> {
                Toast.makeText(this, R.string.toast_conversation_unmuted, Toast.LENGTH_SHORT).show();
            });
            return;
        }

        String[] options = new String[]{
                getString(R.string.mute_duration_1_hour),
                getString(R.string.mute_duration_8_hours),
                getString(R.string.mute_duration_24_hours),
                getString(R.string.mute_duration_indefinite)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_mute_title)
                .setItems(options, (dialog, which) -> {
                    long durationMillis;
                    switch (which) {
                        case 0:
                            durationMillis = 3600_000L;
                            break;
                        case 1:
                            durationMillis = 8 * 3600_000L;
                            break;
                        case 2:
                            durationMillis = 24 * 3600_000L;
                            break;
                        case 3:
                        default:
                            durationMillis = Long.MAX_VALUE;
                            break;
                    }
                    ConversationMetadataRepository.getInstance(this).muteConversation(threadId, durationMillis, () -> {
                        Toast.makeText(this, R.string.toast_conversation_muted, Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onArchiveConversationClicked() {
        if (threadId <= 0) return;
        ConversationMetadataRepository.getInstance(this).setArchived(threadId, true, () -> {
            Toast.makeText(this, R.string.toast_archived, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void onBlockSenderClicked() {
        if (address == null || address.trim().isEmpty()) return;
        String display = (displayName != null && !displayName.isEmpty()) ? displayName : address;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_block_title, display))
                .setMessage(R.string.dialog_block_message)
                .setPositiveButton(R.string.action_block, (d, w) -> {
                    ConversationMetadataRepository.getInstance(this).blockConversation(threadId, address, () -> {
                        Toast.makeText(this, R.string.toast_sender_blocked, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void onReportSpamClicked() {
        if (threadId <= 0) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_report_spam_title)
                .setMessage(R.string.dialog_report_spam_message)
                .setPositiveButton(R.string.action_report_spam, (d, w) -> {
                    ConversationMetadataRepository.getInstance(this).reportSpam(threadId, address, () -> {
                        Toast.makeText(this, R.string.toast_sender_blocked, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void showSafeLinkConfirmationDialog(@NonNull String url, boolean isSuspicious, @Nullable String reason) {
        String title = getString(R.string.dialog_safe_link_title);
        String message = getString(R.string.dialog_safe_link_message, url);
        if (isSuspicious && reason != null && !reason.trim().isEmpty()) {
            message = reason + "\n\n" + message;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(isSuspicious ? R.drawable.ic_warning : R.drawable.ic_link)
                .setPositiveButton(R.string.action_open_link, (d, w) -> {
                    try {
                        String target = url.startsWith("http://") || url.startsWith("https://") ? url : "http://" + url;
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
                        startActivity(browserIntent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Unable to open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.action_copy_link, (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("URL", url));
                        Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
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
        MessageNotificationManager.setCurrentActiveThreadId(threadId);
        MessageNotificationManager.getInstance(this).cancelConversationNotification(threadId);
        updateRoleStatusUI();
        if (viewModel != null) {
            viewModel.refresh();
            // Automatically mark conversation messages as read when viewed
            viewModel.markThreadAsRead();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MessageNotificationManager.setCurrentActiveThreadId(-1L);
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
