package com.nextgen.messages.ui.compose;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.ActivityComposeBinding;
import com.nextgen.messages.mms.storage.MmsStorageManager;
import com.nextgen.messages.mms.transport.MmsConfig;
import com.nextgen.messages.mms.transport.MmsMediaUtils;
import com.nextgen.messages.mms.transport.MmsSender;
import com.nextgen.messages.telephony.SimInfo;
import com.nextgen.messages.telephony.SimManager;
import com.nextgen.messages.telephony.SmsEncodingUtils;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.telephony.SmsSender;
import com.nextgen.messages.telephony.SmsStorageManager;
import com.nextgen.messages.ui.conversation.ConversationActivity;
import com.nextgen.messages.utils.AppExecutors;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Functional SMS/MMS/Group MMS compose screen handling single or multiple recipients,
 * multimedia attachments (images, video, audio), subject line, character/segment counting,
 * Dual SIM switching, and automatic message type classification.
 */
public class ComposeActivity extends AppCompatActivity {

    private ActivityComposeBinding binding;
    private SmsSender smsSender;
    private MmsSender mmsSender;
    private SimManager simManager;
    private SmsRoleManager roleManager;
    private SmsStorageManager storageManager;

    private final List<String> recipients = new ArrayList<>();
    private final List<Uri> attachedUris = new ArrayList<>();

    @Nullable
    private SimInfo selectedSim;

    private final ActivityResultLauncher<Void> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.PickContact(), this::onContactPicked);

    private final ActivityResultLauncher<String> mediaPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onMediaPicked);

    private final ActivityResultLauncher<Intent> roleRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                updateRoleStatusUI();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);

        binding = ActivityComposeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        smsSender = SmsSender.getInstance(this);
        mmsSender = MmsSender.getInstance(this);
        simManager = SimManager.getInstance(this);
        roleManager = new SmsRoleManager(this);
        storageManager = SmsStorageManager.getInstance(this);

        setupWindowInsets();
        setupToolbar();
        setupSimSelector();
        setupRecipientField();
        setupMediaAttachment();
        setupSubjectField();
        setupMessageInput();
        setupSendButton();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRoleStatusUI();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            view.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.composeCoordinator, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(0, 0, 0, insets.bottom);
            return windowInsets;
        });
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSimSelector() {
        List<SimInfo> activeSims = simManager.getActiveSims();
        if (activeSims.isEmpty()) {
            binding.layoutSimSelector.setVisibility(View.GONE);
            selectedSim = null;
            return;
        }

        binding.layoutSimSelector.setVisibility(View.VISIBLE);
        selectedSim = simManager.getDefaultSmsSim();
        updateSimButtonLabel();

        binding.btnSimSelect.setOnClickListener(v -> showSimChooserDialog());
    }

    private void updateSimButtonLabel() {
        if (selectedSim != null) {
            binding.btnSimSelect.setText(selectedSim.getFullLabel());
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
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void setupRecipientField() {
        binding.tilRecipient.setEndIconOnClickListener(v -> {
            try {
                contactPickerLauncher.launch(null);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open contact picker", Toast.LENGTH_SHORT).show();
            }
        });

        binding.etRecipient.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                commitCurrentRecipientInput();
                return true;
            }
            return false;
        });

        binding.etRecipient.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String str = (s != null) ? s.toString() : "";
                if (str.endsWith(",") || str.endsWith(";") || str.endsWith("\n")) {
                    commitCurrentRecipientInput();
                } else {
                    updateMessageTypeBadge();
                    updateSendButtonState();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void commitCurrentRecipientInput() {
        String text = binding.etRecipient.getText() != null ? binding.etRecipient.getText().toString().trim() : "";
        while (text.endsWith(",") || text.endsWith(";") || text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1).trim();
        }

        if (!text.isEmpty()) {
            addRecipient(text);
            binding.etRecipient.setText("");
        }
    }

    private void addRecipient(@NonNull String address) {
        String cleaned = address.trim();
        if (cleaned.isEmpty() || recipients.contains(cleaned)) {
            return;
        }

        recipients.add(cleaned);

        Chip chip = new Chip(this);
        chip.setText(cleaned);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> removeRecipient(cleaned, chip));

        binding.chipGroupRecipients.addView(chip);
        binding.chipGroupRecipients.setVisibility(View.VISIBLE);

        updateMessageTypeBadge();
        updateSendButtonState();
    }

    private void removeRecipient(@NonNull String address, @NonNull Chip chip) {
        recipients.remove(address);
        binding.chipGroupRecipients.removeView(chip);
        if (recipients.isEmpty()) {
            binding.chipGroupRecipients.setVisibility(View.GONE);
        }
        updateMessageTypeBadge();
        updateSendButtonState();
    }

    private void setupMediaAttachment() {
        binding.btnAttach.setOnClickListener(v -> {
            try {
                mediaPickerLauncher.launch("*/*");
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open media picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnRemoveAttachment.setOnClickListener(v -> removeAttachment());
    }

    private void onMediaPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }

        attachedUris.clear();
        attachedUris.add(uri);

        displayAttachmentPreview(uri);
        binding.tilSubject.setVisibility(View.VISIBLE);
        updateMessageTypeBadge();
        updateSendButtonState();
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
        if (binding.etSubject.getText() == null || binding.etSubject.getText().toString().trim().isEmpty()) {
            binding.tilSubject.setVisibility(View.GONE);
        }
        updateMessageTypeBadge();
        updateSendButtonState();
    }

    private void setupSubjectField() {
        binding.etSubject.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateMessageTypeBadge();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupMessageInput() {
        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = (s != null) ? s.toString() : "";
                boolean hasText = !text.trim().isEmpty();

                if (hasText && attachedUris.isEmpty() && getEffectiveRecipientCount() <= 1) {
                    SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(text);
                    binding.tvCounter.setText(info.getCounterLabel());
                    binding.tvCounter.setVisibility(View.VISIBLE);
                } else {
                    binding.tvCounter.setVisibility(View.GONE);
                }
                updateSendButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private int getEffectiveRecipientCount() {
        int count = recipients.size();
        String currentInput = binding.etRecipient.getText() != null ? binding.etRecipient.getText().toString().trim() : "";
        if (!currentInput.isEmpty() && !recipients.contains(currentInput)) {
            count++;
        }
        return count;
    }

    private void updateMessageTypeBadge() {
        int recipientCount = getEffectiveRecipientCount();
        boolean hasAttachment = !attachedUris.isEmpty();
        String subject = binding.etSubject.getText() != null ? binding.etSubject.getText().toString().trim() : "";
        boolean hasSubject = !subject.isEmpty();

        if (recipientCount > 1) {
            binding.tvMessageTypeBadge.setText(R.string.mms_badge_group_mms);
            binding.btnSend.setText(R.string.mms_badge_group_mms);
        } else if (hasAttachment || hasSubject) {
            binding.tvMessageTypeBadge.setText(R.string.mms_badge_mms);
            binding.btnSend.setText(R.string.mms_badge_mms);
        } else {
            binding.tvMessageTypeBadge.setText(R.string.mms_badge_sms);
            binding.btnSend.setText(R.string.compose_action_send);
        }
    }

    private void updateSendButtonState() {
        int recipientCount = getEffectiveRecipientCount();
        String message = binding.etMessage.getText() != null ? binding.etMessage.getText().toString().trim() : "";
        boolean hasContent = !message.isEmpty() || !attachedUris.isEmpty();
        boolean canSend = recipientCount > 0 && hasContent;

        binding.btnSend.setEnabled(canSend);
        binding.btnSend.setAlpha(canSend ? 1.0f : 0.5f);
    }

    private void setupSendButton() {
        updateMessageTypeBadge();
        updateSendButtonState();

        binding.btnSend.setOnClickListener(v -> {
            try {
                if (!roleManager.isDefaultSmsApp()) {
                    roleManager.requestDefaultSmsRole(roleRequestLauncher);
                    return;
                }

                // Gather recipients
                commitCurrentRecipientInput();
                List<String> sendRecipients = new ArrayList<>(recipients);
                if (sendRecipients.isEmpty()) {
                    binding.tilRecipient.setError(getString(R.string.compose_missing_recipient_warning));
                    return;
                }
                binding.tilRecipient.setError(null);

                String messageBody = binding.etMessage.getText() != null ? binding.etMessage.getText().toString().trim() : "";
                String subject = binding.etSubject.getText() != null ? binding.etSubject.getText().toString().trim() : "";
                boolean isMms = sendRecipients.size() > 1 || !attachedUris.isEmpty() || !subject.isEmpty();

                int subId = (selectedSim != null) ? selectedSim.getSubscriptionId() : -1;

                if (isMms) {
                    long targetThreadId = sendRecipients.size() > 1
                            ? storageManager.getOrCreateThreadId(new HashSet<>(sendRecipients))
                            : storageManager.getOrCreateThreadId(sendRecipients.get(0));

                    binding.btnSend.setEnabled(false);

                    mmsSender.sendMms(
                            targetThreadId,
                            sendRecipients,
                            subject.isEmpty() ? null : subject,
                            messageBody.isEmpty() ? null : messageBody,
                            attachedUris.isEmpty() ? null : attachedUris,
                            subId,
                            new MmsSender.MmsSendCallback() {
                                @Override
                                public void onMmsPrepared(long mmsId) {
                                    Intent convIntent = new Intent(ComposeActivity.this, ConversationActivity.class);
                                    convIntent.putExtra(ConversationActivity.EXTRA_THREAD_ID, targetThreadId);
                                    convIntent.putExtra(ConversationActivity.EXTRA_ADDRESS, TextUtils.join(", ", sendRecipients));
                                    startActivity(convIntent);
                                    finish();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    binding.btnSend.setEnabled(true);
                                    Toast.makeText(ComposeActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                }
                            }
                    );
                } else {
                    String singleRecipient = sendRecipients.get(0);
                    long threadId = storageManager.getOrCreateThreadId(singleRecipient);

                    binding.btnSend.setEnabled(false);

                    smsSender.sendMessage(threadId, singleRecipient, messageBody, subId, new SmsSender.SendCallback() {
                        @Override
                        public void onMessageQueued(@NonNull Uri messageUri) {
                            Intent convIntent = new Intent(ComposeActivity.this, ConversationActivity.class);
                            convIntent.putExtra(ConversationActivity.EXTRA_THREAD_ID, threadId);
                            convIntent.putExtra(ConversationActivity.EXTRA_ADDRESS, singleRecipient);
                            startActivity(convIntent);
                            finish();
                        }

                        @Override
                        public void onError(@NonNull String errorMessage) {
                            binding.btnSend.setEnabled(true);
                            Toast.makeText(ComposeActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Throwable t) {
                binding.btnSend.setEnabled(true);
                android.util.Log.e("ComposeActivity", "Error on send click", t);
                Toast.makeText(ComposeActivity.this, "Failed to send: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnSetDefaultRole.setOnClickListener(v -> {
            roleManager.requestDefaultSmsRole(roleRequestLauncher);
        });
    }

    private void updateRoleStatusUI() {
        boolean isDefault = roleManager.isDefaultSmsApp();
        binding.cardRoleWarning.setVisibility(isDefault ? View.GONE : View.VISIBLE);
    }

    private void onContactPicked(@Nullable Uri contactUri) {
        if (contactUri == null) {
            return;
        }

        try (Cursor cursor = getContentResolver().query(contactUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                int hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);

                if (idIdx >= 0) {
                    String contactId = cursor.getString(idIdx);
                    boolean hasPhoneNumber = hasPhoneIdx >= 0 && "1".equals(cursor.getString(hasPhoneIdx));

                    if (hasPhoneNumber) {
                        queryPhoneNumber(contactId);
                    } else {
                        Toast.makeText(this, "Selected contact has no phone number", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error reading contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void queryPhoneNumber(@NonNull String contactId) {
        Uri phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?";
        String[] selectionArgs = new String[]{contactId};

        try (Cursor cursor = getContentResolver().query(phoneUri, null, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                if (numberIdx >= 0) {
                    String number = cursor.getString(numberIdx);
                    addRecipient(number);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error reading phone number: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }

        String recipient = null;
        String messageBody = null;

        Uri dataUri = intent.getData();
        if (dataUri != null) {
            recipient = parseRecipientFromUri(dataUri);
            messageBody = parseBodyFromUri(dataUri);
        }

        if (TextUtils.isEmpty(messageBody)) {
            if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if (text != null) {
                    messageBody = text.toString();
                }
            } else if (intent.hasExtra("sms_body")) {
                messageBody = intent.getStringExtra("sms_body");
            }
        }

        if (intent.hasExtra(Intent.EXTRA_SUBJECT)) {
            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (subject != null && !subject.trim().isEmpty()) {
                binding.etSubject.setText(subject.trim());
                binding.tilSubject.setVisibility(View.VISIBLE);
            }
        }

        // Handle attachment from shared stream
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            try {
                Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (streamUri != null) {
                    onMediaPicked(streamUri);
                }
            } catch (Exception ignored) {}
        }

        if (!TextUtils.isEmpty(recipient)) {
            // Recipient could be multiple comma-separated
            String[] parts = recipient.split("[,;]");
            for (String part : parts) {
                if (!part.trim().isEmpty()) {
                    addRecipient(part.trim());
                }
            }
        }

        if (!TextUtils.isEmpty(messageBody)) {
            binding.etMessage.setText(messageBody);
        }

        updateMessageTypeBadge();
        updateSendButtonState();
    }

    @NonNull
    public static String parseRecipientFromUri(@NonNull Uri uri) {
        try {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            if (schemeSpecificPart == null) {
                return "";
            }

            int queryIndex = schemeSpecificPart.indexOf('?');
            if (queryIndex != -1) {
                schemeSpecificPart = schemeSpecificPart.substring(0, queryIndex);
            }

            while (schemeSpecificPart.startsWith("/")) {
                schemeSpecificPart = schemeSpecificPart.substring(1);
            }

            return Uri.decode(schemeSpecificPart).trim();
        } catch (Exception e) {
            return "";
        }
    }

    @Nullable
    public static String parseBodyFromUri(@NonNull Uri uri) {
        try {
            String body = uri.getQueryParameter("body");
            if (body != null) {
                return body;
            }

            String text = uri.getQueryParameter("text");
            if (text != null) {
                return text;
            }

            return uri.getQueryParameter("sms_body");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
