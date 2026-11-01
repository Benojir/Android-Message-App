package com.nextgen.messages.ui.compose;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.ActivityComposeBinding;
import com.nextgen.messages.telephony.SimInfo;
import com.nextgen.messages.telephony.SimManager;
import com.nextgen.messages.telephony.SmsEncodingUtils;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.telephony.SmsSender;
import com.nextgen.messages.telephony.SmsStorageManager;
import com.nextgen.messages.ui.conversation.ConversationActivity;
import java.util.List;

/**
 * Functional SMS compose screen handling new conversations, contact picking,
 * Dual SIM switching, character/segment counting, and external SENDTO intents.
 */
public class ComposeActivity extends AppCompatActivity {

    private ActivityComposeBinding binding;
    private SmsSender smsSender;
    private SimManager simManager;
    private SmsRoleManager roleManager;
    private SmsStorageManager storageManager;

    @Nullable
    private SimInfo selectedSim;

    private final ActivityResultLauncher<Void> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.PickContact(), this::onContactPicked);

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
        simManager = SimManager.getInstance(this);
        roleManager = new SmsRoleManager(this);
        storageManager = SmsStorageManager.getInstance(this);

        setupWindowInsets();
        setupToolbar();
        setupSimSelector();
        setupRecipientField();
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

        binding.etRecipient.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
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

                if (hasText) {
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

    private void updateSendButtonState() {
        String recipient = binding.etRecipient.getText() != null ? binding.etRecipient.getText().toString().trim() : "";
        String message = binding.etMessage.getText() != null ? binding.etMessage.getText().toString().trim() : "";
        boolean canSend = !recipient.isEmpty() && !message.isEmpty();

        binding.btnSend.setEnabled(canSend);
        binding.btnSend.setAlpha(canSend ? 1.0f : 0.5f);
    }

    private void setupSendButton() {
        updateSendButtonState();

        binding.btnSend.setOnClickListener(v -> {
            if (!roleManager.isDefaultSmsApp()) {
                roleManager.requestDefaultSmsRole(roleRequestLauncher);
                return;
            }

            String recipient = binding.etRecipient.getText() != null ? binding.etRecipient.getText().toString().trim() : "";
            if (TextUtils.isEmpty(recipient)) {
                binding.tilRecipient.setError(getString(R.string.compose_missing_recipient_warning));
                return;
            }
            binding.tilRecipient.setError(null);

            String messageBody = binding.etMessage.getText() != null ? binding.etMessage.getText().toString().trim() : "";
            if (TextUtils.isEmpty(messageBody)) {
                return;
            }

            int subId = (selectedSim != null) ? selectedSim.getSubscriptionId() : -1;

            long threadId = storageManager.getOrCreateThreadId(recipient);

            smsSender.sendMessage(threadId, recipient, messageBody, subId, new SmsSender.SendCallback() {
                @Override
                public void onMessageQueued(@NonNull Uri messageUri) {
                    // Open conversation history screen
                    Intent convIntent = new Intent(ComposeActivity.this, ConversationActivity.class);
                    convIntent.putExtra(ConversationActivity.EXTRA_THREAD_ID, threadId);
                    convIntent.putExtra(ConversationActivity.EXTRA_ADDRESS, recipient);
                    startActivity(convIntent);
                    finish();
                }

                @Override
                public void onError(@NonNull String errorMessage) {
                    Toast.makeText(ComposeActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
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
                    binding.etRecipient.setText(number);
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

        if (!TextUtils.isEmpty(recipient)) {
            binding.etRecipient.setText(recipient);
        }

        if (!TextUtils.isEmpty(messageBody)) {
            binding.etMessage.setText(messageBody);
        }

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
