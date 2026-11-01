package com.nextgen.messages.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.FragmentSettingsBinding;
import com.nextgen.messages.telephony.SmsPermissionManager;
import com.nextgen.messages.telephony.SmsRoleManager;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.mms.transport.MmsConfig;
import com.nextgen.messages.utils.ThemeHelper;

/**
 * SettingsFragment providing default SMS app management, theme preferences,
 * dynamic colors toggle, application information, and phased feature roadmap.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    private ActivityResultLauncher<Intent> roleRequestLauncher;
    private ActivityResultLauncher<String[]> permissionRequestLauncher;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register ActivityResultLauncher for RoleManager.createRequestRoleIntent()
        roleRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Per requirements: Do not rely exclusively on ActivityResult result codes.
                    // Always verify actual role state directly from RoleManager / Telephony.
                    viewModel.refreshRoleStatus();
                    boolean isDefault = viewModel.getSmsRoleManager().isDefaultSmsApp();
                    if (isDefault) {
                        Snackbar.make(binding.getRoot(), R.string.role_request_success, Snackbar.LENGTH_LONG).show();
                    } else {
                        Snackbar.make(binding.getRoot(), R.string.role_request_declined, Snackbar.LENGTH_LONG).show();
                    }
                }
        );

        // Register ActivityResultLauncher for runtime permissions
        permissionRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                results -> viewModel.refreshRoleStatus()
        );
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        setupWindowInsets();
        setupDefaultSmsSection();
        setupAppearanceSection();
        setupMmsSection();
        setupSmartInboxSection();
        setupGeneralSection();
        setupFutureSection();
        observeViewModel();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Detect if user changed default SMS app outside the app in Android Settings
        viewModel.refreshRoleStatus();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
    }

    private void setupDefaultSmsSection() {
        binding.btnSetDefaultSms.setOnClickListener(v -> handleDefaultSmsAction());
    }

    private void handleDefaultSmsAction() {
        SmsRoleManager roleManager = viewModel.getSmsRoleManager();
        SmsRoleManager.RoleStatus status = roleManager.getRoleStatus();

        if (status == SmsRoleManager.RoleStatus.IS_DEFAULT) {
            Snackbar.make(binding.getRoot(), R.string.role_request_already_default, Snackbar.LENGTH_SHORT).show();
        } else if (status == SmsRoleManager.RoleStatus.PERMISSIONS_REQUIRED) {
            SmsPermissionManager permissionManager = new SmsPermissionManager(requireContext());
            String[] missingPermissions = permissionManager.getMissingSmsPermissions();
            if (missingPermissions.length > 0) {
                permissionRequestLauncher.launch(missingPermissions);
            }
        } else if (status == SmsRoleManager.RoleStatus.ROLE_UNAVAILABLE) {
            Snackbar.make(binding.getRoot(), R.string.role_request_unavailable, Snackbar.LENGTH_LONG).show();
        } else {
            // NOT_DEFAULT -> Request the official role
            Intent roleIntent = roleManager.createRequestRoleIntent();
            if (roleIntent != null) {
                try {
                    roleRequestLauncher.launch(roleIntent);
                } catch (Exception e) {
                    // Fallback to opening system settings
                    startActivity(roleManager.createDefaultAppsSettingsIntent());
                }
            } else {
                Snackbar.make(binding.getRoot(), R.string.role_request_unavailable, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private void setupAppearanceSection() {
        binding.rowThemeMode.setOnClickListener(v -> showThemeSelectionDialog());

        boolean isDynamicColorSupported = ThemeHelper.isDynamicColorSupported();
        binding.switchDynamicColors.setEnabled(isDynamicColorSupported);
        if (!isDynamicColorSupported) {
            binding.tvDynamicColorSummary.setText(R.string.settings_dynamic_color_summary);
        }

        binding.switchDynamicColors.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                viewModel.setDynamicColorsEnabled(isChecked);
                Snackbar.make(binding.getRoot(),
                        isChecked ? "Material You dynamic colors enabled." : "Material You dynamic colors disabled.",
                        Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void showThemeSelectionDialog() {
        final int[] themeOptions = {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES
        };

        final String[] themeNames = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };

        int currentMode = viewModel.getThemeMode().getValue() != null
                ? viewModel.getThemeMode().getValue()
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

        int selectedIndex = 0;
        for (int i = 0; i < themeOptions.length; i++) {
            if (themeOptions[i] == currentMode) {
                selectedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.theme_dialog_title)
                .setSingleChoiceItems(themeNames, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    viewModel.setThemeMode(themeOptions[which]);
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void setupMmsSection() {
        AppPreferences prefs = AppPreferences.getInstance(requireContext());

        binding.switchMmsAutoDownload.setChecked(prefs.isMmsAutoDownloadEnabled());
        binding.switchMmsAutoDownload.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                prefs.setMmsAutoDownloadEnabled(isChecked);
            }
        });

        binding.switchMmsRoaming.setChecked(prefs.isMmsRoamingAutoDownloadEnabled());
        binding.switchMmsRoaming.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                prefs.setMmsRoamingAutoDownloadEnabled(isChecked);
            }
        });

        binding.switchMmsDeliveryReports.setChecked(prefs.isMmsDeliveryReportsEnabled());
        binding.switchMmsDeliveryReports.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                prefs.setMmsDeliveryReportsEnabled(isChecked);
            }
        });

        binding.switchMmsReadReports.setChecked(prefs.isMmsReadReportsEnabled());
        binding.switchMmsReadReports.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                prefs.setMmsReadReportsEnabled(isChecked);
            }
        });

        int maxSize = MmsConfig.getMaxMessageSize(requireContext(), -1);
        binding.tvMmsMaxSizeSummary.setText(getString(R.string.pref_mms_max_size_summary, maxSize / 1024));
    }

    private void setupSmartInboxSection() {
        AppPreferences prefs = AppPreferences.getInstance(requireContext());
        com.nextgen.messages.smartinbox.SmartInboxRepository smartRepo =
                com.nextgen.messages.smartinbox.SmartInboxRepository.getInstance(requireContext());

        binding.switchSmartInboxEnabled.setChecked(prefs.isSmartInboxEnabled());
        binding.switchSmartInboxEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                prefs.setSmartInboxEnabled(isChecked);
                Snackbar.make(binding.getRoot(),
                        isChecked ? "Automatic categorization enabled" : "Automatic categorization disabled",
                        Snackbar.LENGTH_SHORT).show();
            }
        });

        binding.rowScanMessages.setOnClickListener(v -> {
            View progressView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_scan_progress, null, false);
            android.widget.TextView tvProgress = progressView.findViewById(R.id.tv_scan_progress_status);
            com.google.android.material.progressindicator.LinearProgressIndicator progressBar =
                    progressView.findViewById(R.id.progress_scan);

            androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.scan_dialog_title)
                    .setView(progressView)
                    .setCancelable(false)
                    .create();
            progressDialog.show();

            smartRepo.scanAndClassifyExistingMessages(new com.nextgen.messages.smartinbox.SmartInboxRepository.ScanProgressListener() {
                @Override
                public void onProgress(int processed, int total) {
                    if (isAdded()) {
                        tvProgress.setText(getString(R.string.scan_dialog_message, processed, total));
                        if (total > 0) {
                            progressBar.setIndeterminate(false);
                            progressBar.setMax(total);
                            progressBar.setProgress(processed);
                        }
                    }
                }

                @Override
                public void onCompleted(int totalClassified) {
                    if (isAdded()) {
                        prefs.setInitialScanCompleted(true);
                        progressDialog.dismiss();
                        Snackbar.make(binding.getRoot(), getString(R.string.toast_scan_completed, totalClassified), Snackbar.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onError(Throwable error) {
                    if (isAdded()) {
                        progressDialog.dismiss();
                        Snackbar.make(binding.getRoot(), "Scan error: " + error.getMessage(), Snackbar.LENGTH_SHORT).show();
                    }
                }
            });
        });

        binding.rowReclassifyMessages.setOnClickListener(v -> {
            View progressView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_scan_progress, null, false);
            android.widget.TextView tvProgress = progressView.findViewById(R.id.tv_scan_progress_status);
            tvProgress.setText("Reclassifying all messages…");

            androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_smart_inbox_reclassify_title)
                    .setView(progressView)
                    .setCancelable(false)
                    .create();
            progressDialog.show();

            smartRepo.reclassifyAll(() -> {
                if (isAdded()) {
                    progressDialog.dismiss();
                    Snackbar.make(binding.getRoot(), R.string.toast_reclassify_completed, Snackbar.LENGTH_SHORT).show();
                }
            });
        });

        binding.rowManageRules.setOnClickListener(v -> {
            com.nextgen.messages.ui.categories.RulesManagerDialog.show(requireContext(), getViewLifecycleOwner());
        });

        binding.rowResetClassifications.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_reset_confirm_title)
                    .setMessage(R.string.dialog_reset_confirm_message)
                    .setPositiveButton(R.string.action_reset, (dialog, which) -> {
                        smartRepo.resetAutomaticClassifications(() -> {
                            if (isAdded()) {
                                prefs.setInitialScanCompleted(false);
                                Snackbar.make(binding.getRoot(), R.string.toast_reset_success, Snackbar.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton(R.string.dialog_button_cancel, null)
                    .show();
        });
    }

    private void setupGeneralSection() {
        binding.rowAbout.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_dialog_title)
                .setMessage(R.string.about_dialog_content)
                .setPositiveButton(R.string.dialog_button_ok, null)
                .show());
    }

    private void setupFutureSection() {
        binding.rowFutureNotifications.setOnClickListener(v -> showFutureFeatureDialog(
                getString(R.string.settings_notifications_title),
                getString(R.string.badge_phase4)
        ));

        binding.rowFuturePrivacy.setOnClickListener(v -> showFutureFeatureDialog(
                getString(R.string.settings_privacy_title),
                getString(R.string.badge_phase5)
        ));

        binding.rowFutureBackup.setOnClickListener(v -> showFutureFeatureDialog(
                getString(R.string.settings_backup_title),
                getString(R.string.badge_phase5)
        ));
    }

    private void showFutureFeatureDialog(String featureName, String phaseName) {
        String message = getString(R.string.future_feature_dialog_message, featureName, phaseName);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.future_feature_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_button_ok, null)
                .show();
    }

    private void observeViewModel() {
        viewModel.getRoleStatus().observe(getViewLifecycleOwner(), this::updateDefaultSmsUI);

        viewModel.getThemeMode().observe(getViewLifecycleOwner(), mode -> {
            binding.tvThemeSummary.setText(ThemeHelper.getThemeName(requireContext(), mode));
        });

        viewModel.getDynamicColorsEnabled().observe(getViewLifecycleOwner(), enabled -> {
            binding.switchDynamicColors.setChecked(enabled != null && enabled);
        });
    }

    private void updateDefaultSmsUI(@NonNull SmsRoleManager.RoleStatus status) {
        switch (status) {
            case IS_DEFAULT:
                binding.tvDefaultSmsTitle.setText(R.string.default_sms_state2_title);
                binding.tvDefaultSmsDesc.setText(R.string.default_sms_state2_desc);
                binding.btnSetDefaultSms.setVisibility(View.GONE);
                binding.layoutDefaultSmsActiveBadge.setVisibility(View.VISIBLE);
                binding.ivDefaultSmsStatus.setImageResource(R.drawable.ic_verified);
                break;

            case NOT_DEFAULT:
                binding.tvDefaultSmsTitle.setText(R.string.default_sms_state1_title);
                binding.tvDefaultSmsDesc.setText(R.string.default_sms_state1_desc);
                binding.btnSetDefaultSms.setVisibility(View.VISIBLE);
                binding.btnSetDefaultSms.setEnabled(true);
                binding.btnSetDefaultSms.setText(R.string.default_sms_action_set_default);
                binding.layoutDefaultSmsActiveBadge.setVisibility(View.GONE);
                binding.ivDefaultSmsStatus.setImageResource(R.drawable.ic_sms);
                break;

            case ROLE_UNAVAILABLE:
                binding.tvDefaultSmsTitle.setText(R.string.default_sms_state3_title);
                binding.tvDefaultSmsDesc.setText(R.string.default_sms_state3_desc);
                binding.btnSetDefaultSms.setVisibility(View.VISIBLE);
                binding.btnSetDefaultSms.setEnabled(false);
                binding.btnSetDefaultSms.setText(R.string.default_sms_action_unavailable);
                binding.layoutDefaultSmsActiveBadge.setVisibility(View.GONE);
                binding.ivDefaultSmsStatus.setImageResource(R.drawable.ic_info);
                break;

            case PERMISSIONS_REQUIRED:
                binding.tvDefaultSmsTitle.setText(R.string.default_sms_state4_title);
                binding.tvDefaultSmsDesc.setText(R.string.default_sms_state4_desc);
                binding.btnSetDefaultSms.setVisibility(View.VISIBLE);
                binding.btnSetDefaultSms.setEnabled(true);
                binding.btnSetDefaultSms.setText(R.string.default_sms_action_grant);
                binding.layoutDefaultSmsActiveBadge.setVisibility(View.GONE);
                binding.ivDefaultSmsStatus.setImageResource(R.drawable.ic_shield);
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
