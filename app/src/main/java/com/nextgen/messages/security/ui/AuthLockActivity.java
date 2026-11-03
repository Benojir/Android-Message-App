package com.nextgen.messages.security.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.ActivityAuthLockBinding;
import com.nextgen.messages.security.lock.AppLockManager;
import com.nextgen.messages.utils.ThemeHelper;
import java.util.concurrent.Executor;

/**
 * Fullscreen secure authentication gate requiring 6-digit PIN or device biometrics.
 * Protected with WindowManager.LayoutParams.FLAG_SECURE to prevent preview leaks in Recents.
 */
public class AuthLockActivity extends AppCompatActivity {

    public static final String EXTRA_PROMPT_BIOMETRIC = "extra_prompt_biometric";
    private static final int PIN_LENGTH = 6;

    private ActivityAuthLockBinding binding;
    private AppLockManager lockManager;
    private final StringBuilder pinBuffer = new StringBuilder();
    private CountDownTimer lockoutTimer;
    private ImageView[] dotViews;

    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, AuthLockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Enforce FLAG_SECURE on the authentication window
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ThemeHelper.applyToActivity(this);
        super.onCreate(savedInstanceState);

        binding = ActivityAuthLockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lockManager = AppLockManager.getInstance(this);

        dotViews = new ImageView[]{
                binding.dot1,
                binding.dot2,
                binding.dot3,
                binding.dot4,
                binding.dot5,
                binding.dot6
        };

        setupKeypad();
        setupAccessibility();

        // Handle Back navigation: move task to background rather than bypassing the gate
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });

        binding.btnCancel.setOnClickListener(v -> moveTaskToBack(true));

        checkLockoutStatus();

        // Automatically prompt biometrics if supported and not locked out
        boolean shouldPromptBiometric = getIntent().getBooleanExtra(EXTRA_PROMPT_BIOMETRIC, true);
        if (shouldPromptBiometric && lockManager.canAuthenticateWithBiometrics() && lockManager.getRemainingLockoutSeconds() == 0) {
            showBiometricPrompt();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkLockoutStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockoutTimer != null) {
            lockoutTimer.cancel();
            lockoutTimer = null;
        }
    }

    private void checkLockoutStatus() {
        long remaining = lockManager.getRemainingLockoutSeconds();
        if (remaining > 0) {
            startLockoutCountdown(remaining);
        } else {
            enableKeypad(true);
            binding.tvLockoutWarning.setVisibility(View.GONE);
        }
    }

    private void startLockoutCountdown(long seconds) {
        enableKeypad(false);
        binding.tvLockoutWarning.setVisibility(View.VISIBLE);
        clearPin();

        if (lockoutTimer != null) {
            lockoutTimer.cancel();
        }

        lockoutTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = (millisUntilFinished + 999) / 1000;
                binding.tvLockoutWarning.setText(getString(R.string.auth_lock_rate_limited, sec));
            }

            @Override
            public void onFinish() {
                binding.tvLockoutWarning.setVisibility(View.GONE);
                enableKeypad(true);
            }
        }.start();
    }

    private void setupKeypad() {
        View.OnClickListener numListener = v -> {
            if (pinBuffer.length() < PIN_LENGTH) {
                String digit = ((com.google.android.material.button.MaterialButton) v).getText().toString();
                pinBuffer.append(digit);
                updateDots();

                // Announce progress to accessibility services without leaking the actual digit
                binding.dotsContainer.announceForAccessibility(
                        getString(R.string.a11y_pin_digit_entered, pinBuffer.length(), PIN_LENGTH)
                );

                if (pinBuffer.length() == PIN_LENGTH) {
                    processPinSubmission();
                }
            }
        };

        binding.btnKey0.setOnClickListener(numListener);
        binding.btnKey1.setOnClickListener(numListener);
        binding.btnKey2.setOnClickListener(numListener);
        binding.btnKey3.setOnClickListener(numListener);
        binding.btnKey4.setOnClickListener(numListener);
        binding.btnKey5.setOnClickListener(numListener);
        binding.btnKey6.setOnClickListener(numListener);
        binding.btnKey7.setOnClickListener(numListener);
        binding.btnKey8.setOnClickListener(numListener);
        binding.btnKey9.setOnClickListener(numListener);

        binding.btnKeyBackspace.setOnClickListener(v -> {
            if (pinBuffer.length() > 0) {
                pinBuffer.deleteCharAt(pinBuffer.length() - 1);
                updateDots();
                binding.dotsContainer.announceForAccessibility(
                        getString(R.string.a11y_pin_digit_deleted, pinBuffer.length(), PIN_LENGTH)
                );
            }
        });

        binding.btnKeyBackspace.setOnLongClickListener(v -> {
            clearPin();
            binding.dotsContainer.announceForAccessibility(getString(R.string.a11y_pin_cleared));
            return true;
        });

        if (lockManager.canAuthenticateWithBiometrics()) {
            binding.btnKeyBiometric.setVisibility(View.VISIBLE);
            binding.btnKeyBiometric.setOnClickListener(v -> showBiometricPrompt());
        } else {
            binding.btnKeyBiometric.setVisibility(View.INVISIBLE);
        }
    }

    private void processPinSubmission() {
        String enteredPin = pinBuffer.toString();
        AppLockManager.AuthResult result = lockManager.verifyPin(enteredPin);

        if (result.isSuccess()) {
            setResult(RESULT_OK);
            finish();
        } else if (result.getStatus() == AppLockManager.AuthResult.Status.LOCKED_OUT) {
            startLockoutCountdown(result.getLockoutRemainingSeconds());
        } else {
            // Incorrect PIN
            binding.tvLockoutWarning.setVisibility(View.VISIBLE);
            int attempts = result.getFailedAttempts();
            int remainingBeforeDelay = 5 - attempts;
            if (remainingBeforeDelay > 0) {
                binding.tvLockoutWarning.setText(getString(R.string.auth_lock_incorrect_pin_warn, remainingBeforeDelay));
            } else {
                binding.tvLockoutWarning.setText(getString(R.string.auth_lock_incorrect_pin));
            }
            clearPin();
            binding.dotsContainer.announceForAccessibility(getString(R.string.auth_lock_incorrect_pin));
        }
    }

    private void updateDots() {
        int length = pinBuffer.length();
        for (int i = 0; i < PIN_LENGTH; i++) {
            if (i < length) {
                dotViews[i].setImageResource(R.drawable.ic_pin_dot_filled);
            } else {
                dotViews[i].setImageResource(R.drawable.ic_pin_dot_empty);
            }
        }
    }

    private void clearPin() {
        pinBuffer.setLength(0);
        updateDots();
    }

    private void enableKeypad(boolean enabled) {
        binding.btnKey0.setEnabled(enabled);
        binding.btnKey1.setEnabled(enabled);
        binding.btnKey2.setEnabled(enabled);
        binding.btnKey3.setEnabled(enabled);
        binding.btnKey4.setEnabled(enabled);
        binding.btnKey5.setEnabled(enabled);
        binding.btnKey6.setEnabled(enabled);
        binding.btnKey7.setEnabled(enabled);
        binding.btnKey8.setEnabled(enabled);
        binding.btnKey9.setEnabled(enabled);
        binding.btnKeyBackspace.setEnabled(enabled);
        binding.btnKeyBiometric.setEnabled(enabled && lockManager.canAuthenticateWithBiometrics());
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // Fall back cleanly to PIN on error or cancel
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        lockManager.unlockSession();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        binding.tvLockoutWarning.setVisibility(View.VISIBLE);
                        binding.tvLockoutWarning.setText(R.string.auth_lock_biometric_failed);
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setNegativeButtonText(getString(R.string.biometric_use_pin))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void setupAccessibility() {
        binding.btnKeyBackspace.setContentDescription(getString(R.string.btn_backspace_desc));
        binding.btnKeyBiometric.setContentDescription(getString(R.string.btn_biometric_desc));
        binding.btnCancel.setContentDescription(getString(R.string.btn_exit_app));
    }
}
