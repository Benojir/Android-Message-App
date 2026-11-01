package com.nextgen.messages.ui.compose;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.ActivityComposeBinding;

/**
 * Activity handling external and internal SMS/MMS compose requests (ACTION_SENDTO).
 * Parses recipient numbers and pre-filled message bodies safely from standard URIs
 * (sms:, smsto:, mms:, mmsto:) and intent extras.
 */
public class ComposeActivity extends AppCompatActivity {

    private ActivityComposeBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityComposeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupWindowInsets();
        setupToolbar();
        setupSendButton();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            view.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.composeCoordinator, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            binding.btnSend.setPadding(0, 0, 0, 0);
            return windowInsets;
        });
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSendButton() {
        binding.btnSend.setOnClickListener(v -> {
            String recipient = binding.etRecipient.getText() != null
                    ? binding.etRecipient.getText().toString().trim()
                    : "";
            if (TextUtils.isEmpty(recipient)) {
                binding.tilRecipient.setError(getString(R.string.compose_missing_recipient_warning));
                return;
            }
            binding.tilRecipient.setError(null);

            Snackbar.make(binding.getRoot(), R.string.compose_send_disabled_notice, Snackbar.LENGTH_LONG)
                    .setAction(R.string.dialog_button_ok, view -> {})
                    .show();
        });
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

        // Check fallback intent extras
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
    }

    @NonNull
    public static String parseRecipientFromUri(@NonNull Uri uri) {
        try {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            if (schemeSpecificPart == null) {
                return "";
            }

            // If there's a query parameter (e.g. ?body=...), isolate the recipient number
            int queryIndex = schemeSpecificPart.indexOf('?');
            if (queryIndex != -1) {
                schemeSpecificPart = schemeSpecificPart.substring(0, queryIndex);
            }

            // Remove any leading slashes from smsto:// or similar formats
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

            // Some third-party apps pass ?text= or ?sms_body=
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
