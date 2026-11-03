package com.nextgen.messages.ui.settings;

import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.nextgen.messages.security.lock.AppLockManager;
import com.nextgen.messages.security.lock.PinCryptoUtils;
import com.nextgen.messages.security.backup.BackupConstants;
import com.nextgen.messages.security.backup.BackupCrypto;
import com.nextgen.messages.security.backup.BackupManifest;
import com.nextgen.messages.security.backup.EncryptedBackupManager;
import com.nextgen.messages.security.backup.EncryptedRestoreManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
import com.nextgen.messages.notification.NotificationChannelManager;
import com.nextgen.messages.notification.NotificationPrivacyManager;


/**
 * SettingsFragment providing default SMS app management, theme preferences,
 * dynamic colors toggle, application information, and phased feature roadmap.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    private final ActivityResultLauncher<Intent> backupCreateLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri uri = result.getData().getData();
                    showBackupPasswordDialog(uri);
                }
            });

    private final ActivityResultLauncher<Intent> restoreOpenLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri uri = result.getData().getData();
                    showRestorePasswordDialog(uri);
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<String> globalWallpaperPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                    }
                    com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext())
                            .setWallpaperType(com.nextgen.messages.data.local.AppPreferences.WALLPAPER_TYPE_IMAGE);
                    com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext())
                            .setWallpaperValue(uri.toString());
                    updateChatCustomizationSummaries();
                    com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), R.string.wallpaper_applied_toast, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                }
            });

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
        setupChatCustomizationSection();
        setupInboxLayoutSection();
        setupMmsSection();
        setupSmartInboxSection();
        setupSpamSecuritySection();
        setupNotificationsSection();
        setupGeneralSection();
        setupFutureSection();
        setupPrivacySecuritySection();
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
        binding.rowAccentColor.setOnClickListener(v -> showAccentColorDialog());
        updateAccentSummary();

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
                AppCompatDelegate.MODE_NIGHT_YES,
                com.nextgen.messages.utils.ThemeHelper.THEME_AMOLED
        };

        final String[] themeNames = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_amoled)
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

    
    private void updateAccentSummary() {
        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
        String accent = prefs.getAccentColor();
        binding.tvAccentSummary.setText(accent.substring(0, 1).toUpperCase() + accent.substring(1).toLowerCase());
    }

    private void showAccentColorDialog() {
        final String[] accentKeys = {
                com.nextgen.messages.data.local.AppPreferences.ACCENT_DEFAULT,
                com.nextgen.messages.data.local.AppPreferences.ACCENT_BLUE,
                com.nextgen.messages.data.local.AppPreferences.ACCENT_PURPLE,
                com.nextgen.messages.data.local.AppPreferences.ACCENT_GREEN,
                com.nextgen.messages.data.local.AppPreferences.ACCENT_TEAL,
                com.nextgen.messages.data.local.AppPreferences.ACCENT_ORANGE,
                com.nextgen.messages.data.local.AppPreferences.ACCENT_RED,
                com.nextgen.messages.data.local.AppPreferences.ACCENT_PINK
        };

        final String[] accentNames = {
                "Default (Brand Blue)", "Ocean Blue", "Royal Purple", "Forest Green", "Teal", "Warm Orange", "Crimson Red", "Rose Pink"
        };

        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
        String current = prefs.getAccentColor();
        int selected = 0;
        for (int i = 0; i < accentKeys.length; i++) {
            if (accentKeys[i].equalsIgnoreCase(current)) {
                selected = i;
                break;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_accent_title)
                .setSingleChoiceItems(accentNames, selected, (dialog, which) -> {
                    dialog.dismiss();
                    prefs.setAccentColor(accentKeys[which]);
                    updateAccentSummary();
                    requireActivity().recreate();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void setupChatCustomizationSection() {
        binding.rowBubbleStyle.setOnClickListener(v -> showBubbleStyleDialog());
        binding.rowBubbleColors.setOnClickListener(v -> showBubbleColorDialog());
        binding.rowWallpaper.setOnClickListener(v -> showGlobalWallpaperDialog());
        binding.rowFontSize.setOnClickListener(v -> showFontSizeDialog());
        binding.rowChatDensity.setOnClickListener(v -> showChatDensityDialog());
        updateChatCustomizationSummaries();
    }

    private void updateChatCustomizationSummaries() {
        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
        binding.tvBubbleStyleSummary.setText(prefs.getBubbleStyle());
        binding.tvBubbleColorsSummary.setText(prefs.getBubbleColorPreset());
        binding.tvWallpaperSummary.setText("Type: " + prefs.getWallpaperType());
        binding.tvFontSizeSummary.setText(prefs.getFontSize());
        binding.tvChatDensitySummary.setText(prefs.getChatDensity());
    }

    private void showBubbleStyleDialog() {
        final String[] styles = {
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_STYLE_MODERN,
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_STYLE_CLASSIC,
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_STYLE_ROUNDED,
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_STYLE_MINIMAL
        };
        final String[] names = {
                "Modern (Asymmetric MD3)", "Classic (Subtle Tail)", "Rounded (Pill)", "Minimal (Clean Box)"
        };

        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
        String current = prefs.getBubbleStyle();
        int selected = 0;
        for (int i = 0; i < styles.length; i++) {
            if (styles[i].equalsIgnoreCase(current)) {
                selected = i;
                break;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_bubble_style_title)
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    dialog.dismiss();
                    prefs.setBubbleStyle(styles[which]);
                    updateChatCustomizationSummaries();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void showBubbleColorDialog() {
        final String[] presets = {
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_COLOR_DEFAULT,
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_COLOR_BLUE,
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_COLOR_GREEN,
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_COLOR_PURPLE,
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_COLOR_ORANGE,
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_COLOR_ROSE,
                com.nextgen.messages.data.local.AppPreferences.BUBBLE_COLOR_SLATE
        };
        final String[] names = {
                "Default (Theme Adaptive)", "Ocean Blue", "Forest Green", "Royal Purple", "Sunset Orange", "Rose Pink", "Slate Monochrome"
        };

        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
        String current = prefs.getBubbleColorPreset();
        int selected = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equalsIgnoreCase(current)) {
                selected = i;
                break;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_bubble_color_title)
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    dialog.dismiss();
                    prefs.setBubbleColorPreset(presets[which]);
                    updateChatCustomizationSummaries();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void showGlobalWallpaperDialog() {
        String[] options = new String[]{
                getString(R.string.wallpaper_default),
                getString(R.string.wallpaper_solid_amoled),
                getString(R.string.wallpaper_solid_slate),
                getString(R.string.wallpaper_solid_light),
                getString(R.string.wallpaper_gradient_twilight),
                getString(R.string.wallpaper_gradient_dawn),
                getString(R.string.wallpaper_gradient_forest),
                getString(R.string.wallpaper_gradient_ocean),
                getString(R.string.wallpaper_custom_image)
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_wallpaper_title)
                .setItems(options, (dialog, which) -> {
                    com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
                    switch (which) {
                        case 0:
                            prefs.setWallpaperType(com.nextgen.messages.data.local.AppPreferences.WALLPAPER_TYPE_NONE);
                            prefs.setWallpaperValue(null);
                            break;
                        case 1:
                            prefs.setWallpaperType(com.nextgen.messages.data.local.AppPreferences.WALLPAPER_TYPE_SOLID);
                            prefs.setWallpaperValue("#000000");
                            break;
                        case 2:
                            prefs.setWallpaperType(com.nextgen.messages.data.local.AppPreferences.WALLPAPER_TYPE_SOLID);
                            prefs.setWallpaperValue("#1E1E24");
                            break;
                        case 3:
                            prefs.setWallpaperType(com.nextgen.messages.data.local.AppPreferences.WALLPAPER_TYPE_SOLID);
                            prefs.setWallpaperValue("#ECEFF1");
                            break;
                        case 4:
                            prefs.setWallpaperType(com.nextgen.messages.data.local.AppPreferences.WALLPAPER_TYPE_GRADIENT);
                            prefs.setWallpaperValue("twilight");
                            break;
                        case 5:
                            prefs.setWallpaperType(com.nextgen.messages.data.local.AppPreferences.WALLPAPER_TYPE_GRADIENT);
                            prefs.setWallpaperValue("dawn");
                            break;
                        case 6:
                            prefs.setWallpaperType(com.nextgen.messages.data.local.AppPreferences.WALLPAPER_TYPE_GRADIENT);
                            prefs.setWallpaperValue("forest");
                            break;
                        case 7:
                            prefs.setWallpaperType(com.nextgen.messages.data.local.AppPreferences.WALLPAPER_TYPE_GRADIENT);
                            prefs.setWallpaperValue("ocean");
                            break;
                        case 8:
                            globalWallpaperPickerLauncher.launch("image/*");
                            return;
                    }
                    updateChatCustomizationSummaries();
                    com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), R.string.wallpaper_applied_toast, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void showFontSizeDialog() {
        final String[] sizes = {
                com.nextgen.messages.data.local.AppPreferences.FONT_SIZE_SMALL,
                com.nextgen.messages.data.local.AppPreferences.FONT_SIZE_DEFAULT,
                com.nextgen.messages.data.local.AppPreferences.FONT_SIZE_LARGE,
                com.nextgen.messages.data.local.AppPreferences.FONT_SIZE_XLARGE
        };
        final String[] names = {
                getString(R.string.font_size_small),
                getString(R.string.font_size_default),
                getString(R.string.font_size_large),
                getString(R.string.font_size_xlarge)
        };

        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
        String current = prefs.getFontSize();
        int selected = 1;
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i].equalsIgnoreCase(current)) {
                selected = i;
                break;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_font_size_title)
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    dialog.dismiss();
                    prefs.setFontSize(sizes[which]);
                    updateChatCustomizationSummaries();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void showChatDensityDialog() {
        final String[] densities = {
                com.nextgen.messages.data.local.AppPreferences.DENSITY_COMPACT,
                com.nextgen.messages.data.local.AppPreferences.DENSITY_COMFORTABLE,
                com.nextgen.messages.data.local.AppPreferences.DENSITY_SPACIOUS
        };
        final String[] names = {
                getString(R.string.density_compact),
                getString(R.string.density_comfortable),
                getString(R.string.density_spacious)
        };

        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
        String current = prefs.getChatDensity();
        int selected = 1;
        for (int i = 0; i < densities.length; i++) {
            if (densities[i].equalsIgnoreCase(current)) {
                selected = i;
                break;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_density_title)
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    dialog.dismiss();
                    prefs.setChatDensity(densities[which]);
                    updateChatCustomizationSummaries();
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void setupInboxLayoutSection() {
        binding.rowInboxLayout.setOnClickListener(v -> showInboxLayoutDialog());
        updateInboxLayoutSummary();
    }

    private void updateInboxLayoutSummary() {
        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
        boolean isCompact = com.nextgen.messages.data.local.AppPreferences.INBOX_LAYOUT_COMPACT.equalsIgnoreCase(prefs.getInboxLayout());
        binding.tvInboxLayoutSummary.setText(isCompact ? R.string.inbox_layout_compact : R.string.inbox_layout_comfortable);
    }

    private void showInboxLayoutDialog() {
        final String[] layouts = {
                com.nextgen.messages.data.local.AppPreferences.INBOX_LAYOUT_COMFORTABLE,
                com.nextgen.messages.data.local.AppPreferences.INBOX_LAYOUT_COMPACT
        };
        final String[] names = {
                getString(R.string.inbox_layout_comfortable),
                getString(R.string.inbox_layout_compact)
        };

        com.nextgen.messages.data.local.AppPreferences prefs = com.nextgen.messages.data.local.AppPreferences.getInstance(requireContext());
        String current = prefs.getInboxLayout();
        int selected = com.nextgen.messages.data.local.AppPreferences.INBOX_LAYOUT_COMPACT.equalsIgnoreCase(current) ? 1 : 0;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_inbox_layout_title)
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    dialog.dismiss();
                    prefs.setInboxLayout(layouts[which]);
                    updateInboxLayoutSummary();
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

    private void setupSpamSecuritySection() {
        AppPreferences prefs = AppPreferences.getInstance(requireContext());

        // Toggle: Spam Protection
        binding.switchSpamProtection.setChecked(prefs.isSpamProtectionEnabled());
        binding.switchSpamProtection.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.setSpamProtectionEnabled(isChecked);
        });

        // Toggle: Suspicious Links
        binding.switchSuspiciousLinks.setChecked(prefs.isSuspiciousLinkWarningsEnabled());
        binding.switchSuspiciousLinks.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.setSuspiciousLinkWarningsEnabled(isChecked);
        });

        // Toggle: Auto-Unarchive
        binding.switchAutoUnarchive.setChecked(prefs.isAutoUnarchiveEnabled());
        binding.switchAutoUnarchive.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.setAutoUnarchiveEnabled(isChecked);
        });

        // Sensitivity Dialog
        updateSensitivitySummary(prefs.getSpamSensitivity());
        binding.layoutSpamSensitivity.setOnClickListener(v -> {
            CharSequence[] options = new CharSequence[]{
                    getString(R.string.settings_spam_sensitivity_conservative),
                    getString(R.string.settings_spam_sensitivity_balanced)
            };
            int checkedItem = "BALANCED".equalsIgnoreCase(prefs.getSpamSensitivity()) ? 1 : 0;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_spam_sensitivity_title)
                    .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                        String selected = which == 1 ? "BALANCED" : "CONSERVATIVE";
                        prefs.setSpamSensitivity(selected);
                        updateSensitivitySummary(selected);
                        dialog.dismiss();
                    })
                    .show();
        });

        // Screen links
        binding.layoutBlockedSenders.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.nextgen.messages.ui.blocked.BlockedSendersActivity.class));
        });

        binding.layoutSpamScreen.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.nextgen.messages.ui.spam.SpamBlockedActivity.class));
        });

        binding.layoutArchivedScreen.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.nextgen.messages.ui.archived.ArchivedActivity.class));
        });

        binding.layoutStarredScreen.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.nextgen.messages.ui.starred.StarredMessagesActivity.class));
        });
    }

    private void updateSensitivitySummary(String sensitivity) {
        if ("BALANCED".equalsIgnoreCase(sensitivity)) {
            binding.tvSpamSensitivityValue.setText(R.string.settings_spam_sensitivity_balanced);
        } else {
            binding.tvSpamSensitivityValue.setText(R.string.settings_spam_sensitivity_conservative);
        }
    }

    private void setupGeneralSection() {
        binding.rowAbout.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_dialog_title)
                .setMessage(R.string.about_dialog_content)
                .setPositiveButton(R.string.dialog_button_ok, null)
                .show());
    }

    private void setupFutureSection() {
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


    private void setupNotificationsSection() {
        AppPreferences preferences = AppPreferences.getInstance(requireContext());

        // Master notification toggle
        binding.switchNotifications.setChecked(preferences.isNotificationsEnabled());
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setNotificationsEnabled(isChecked);
        });

        // Privacy Level
        updateNotificationPrivacySummary(preferences);
        binding.rowNotificationPrivacy.setOnClickListener(v -> showNotificationPrivacyDialog(preferences));

        // Hide OTP on lock screen
        binding.switchHideOtpLockscreen.setChecked(preferences.isHideOtpOnLockscreenEnabled());
        binding.switchHideOtpLockscreen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setHideOtpOnLockscreenEnabled(isChecked);
        });

        // Show SIM badge
        binding.switchShowSimBadge.setChecked(preferences.isShowSimBadgeInNotifications());
        binding.switchShowSimBadge.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setShowSimBadgeInNotifications(isChecked);
        });

        // System Channel Settings
        binding.rowSystemChannelSettings.setOnClickListener(v -> {
            NotificationChannelManager.openAppNotificationSettings(requireContext());
        });
    }

    private void updateNotificationPrivacySummary(@NonNull AppPreferences preferences) {
        String level = preferences.getNotificationPrivacy();
        if (NotificationPrivacyManager.PRIVACY_HIDE_ALL.equalsIgnoreCase(level)) {
            binding.tvNotificationPrivacySummary.setText(R.string.settings_privacy_hide_all);
        } else if (NotificationPrivacyManager.PRIVACY_HIDE_BODY.equalsIgnoreCase(level)) {
            binding.tvNotificationPrivacySummary.setText(R.string.settings_privacy_hide_body);
        } else {
            binding.tvNotificationPrivacySummary.setText(R.string.settings_privacy_full);
        }
    }

    private void showNotificationPrivacyDialog(@NonNull AppPreferences preferences) {
        String current = preferences.getNotificationPrivacy();
        int selectedIndex = 0;
        if (NotificationPrivacyManager.PRIVACY_HIDE_BODY.equalsIgnoreCase(current)) {
            selectedIndex = 1;
        } else if (NotificationPrivacyManager.PRIVACY_HIDE_ALL.equalsIgnoreCase(current)) {
            selectedIndex = 2;
        }

        String[] options = new String[]{
                getString(R.string.settings_privacy_full),
                getString(R.string.settings_privacy_hide_body),
                getString(R.string.settings_privacy_hide_all)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_notification_privacy_title)
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    String chosen;
                    switch (which) {
                        case 1:
                            chosen = NotificationPrivacyManager.PRIVACY_HIDE_BODY;
                            break;
                        case 2:
                            chosen = NotificationPrivacyManager.PRIVACY_HIDE_ALL;
                            break;
                        case 0:
                        default:
                            chosen = NotificationPrivacyManager.PRIVACY_FULL;
                            break;
                    }
                    preferences.setNotificationPrivacy(chosen);
                    updateNotificationPrivacySummary(preferences);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setupPrivacySecuritySection() {
        AppPreferences prefs = AppPreferences.getInstance(requireContext());
        AppLockManager lockManager = AppLockManager.getInstance(requireContext());

        // App Lock Switch
        binding.switchAppLock.setChecked(lockManager.isAppLockEnabled());
        binding.switchAppLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                if (isChecked) {
                    if (prefs.hasConfiguredPin()) {
                        prefs.setAppLockEnabled(true);
                    } else {
                        binding.switchAppLock.setChecked(false);
                        showPinSetupDialog();
                    }
                } else {
                    binding.switchAppLock.setChecked(true); // Keep checked until verified
                    showPinDisableDialog();
                }
            }
        });

        // Biometric Switch
        binding.switchBiometric.setChecked(prefs.isBiometricEnabled());
        binding.switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                if (isChecked && !lockManager.canAuthenticateWithBiometrics()) {
                    binding.switchBiometric.setChecked(false);
                    Snackbar.make(binding.getRoot(), "Biometrics not available or not enrolled on this device", Snackbar.LENGTH_LONG).show();
                    return;
                }
                prefs.setBiometricEnabled(isChecked);
            }
        });

        // Change PIN
        binding.rowChangePin.setOnClickListener(v -> {
            if (!prefs.hasConfiguredPin()) {
                showPinSetupDialog();
            } else {
                showChangePinDialog();
            }
        });

        // Auto-Lock Timeout
        updateAutoLockSummary();
        binding.rowAutoLock.setOnClickListener(v -> showAutoLockDialog());

        // Screenshot Protection
        updateScreenshotSummary();
        binding.rowScreenshotProtection.setOnClickListener(v -> showScreenshotProtectionDialog());

        // Private Conversations
        updatePrivateChatsSummary();
        binding.rowPrivateConversations.setOnClickListener(v -> showPrivateConversationsDialog());

        // Create Encrypted Backup
        binding.rowCreateBackup.setOnClickListener(v -> launchBackupCreate());

        // Restore Encrypted Backup
        binding.rowRestoreBackup.setOnClickListener(v -> launchRestoreOpen());

        // Security Info
        binding.rowSecurityInfo.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.security_info_dialog_title)
                    .setMessage(R.string.security_info_dialog_content)
                    .setPositiveButton(R.string.dialog_button_ok, null)
                    .show();
        });
    }

    private void updateAutoLockSummary() {
        int timeout = AppPreferences.getInstance(requireContext()).getAutoLockTimeoutSeconds();
        int resId = R.string.auto_lock_immediately;
        if (timeout == AppPreferences.AUTO_LOCK_30_SECONDS) resId = R.string.auto_lock_30_sec;
        else if (timeout == AppPreferences.AUTO_LOCK_1_MINUTE) resId = R.string.auto_lock_1_min;
        else if (timeout == AppPreferences.AUTO_LOCK_5_MINUTES) resId = R.string.auto_lock_5_min;
        else if (timeout == AppPreferences.AUTO_LOCK_15_MINUTES) resId = R.string.auto_lock_15_min;
        binding.tvAutoLockSummary.setText(resId);
    }

    private void showAutoLockDialog() {
        AppPreferences prefs = AppPreferences.getInstance(requireContext());
        int current = prefs.getAutoLockTimeoutSeconds();
        int selected = 0;
        if (current == AppPreferences.AUTO_LOCK_30_SECONDS) selected = 1;
        else if (current == AppPreferences.AUTO_LOCK_1_MINUTE) selected = 2;
        else if (current == AppPreferences.AUTO_LOCK_5_MINUTES) selected = 3;
        else if (current == AppPreferences.AUTO_LOCK_15_MINUTES) selected = 4;

        String[] options = new String[]{
                getString(R.string.auto_lock_immediately),
                getString(R.string.auto_lock_30_sec),
                getString(R.string.auto_lock_1_min),
                getString(R.string.auto_lock_5_min),
                getString(R.string.auto_lock_15_min)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_auto_lock_title)
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    int sec = 0;
                    if (which == 1) sec = AppPreferences.AUTO_LOCK_30_SECONDS;
                    else if (which == 2) sec = AppPreferences.AUTO_LOCK_1_MINUTE;
                    else if (which == 3) sec = AppPreferences.AUTO_LOCK_5_MINUTES;
                    else if (which == 4) sec = AppPreferences.AUTO_LOCK_15_MINUTES;

                    prefs.setAutoLockTimeoutSeconds(sec);
                    updateAutoLockSummary();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateScreenshotSummary() {
        String mode = AppPreferences.getInstance(requireContext()).getScreenshotProtection();
        int resId = R.string.screenshot_private_only;
        if (AppPreferences.SCREENSHOT_DISABLED.equals(mode)) resId = R.string.screenshot_disabled;
        else if (AppPreferences.SCREENSHOT_ALL.equals(mode)) resId = R.string.screenshot_all;
        binding.tvScreenshotSummary.setText(resId);
    }

    private void showScreenshotProtectionDialog() {
        AppPreferences prefs = AppPreferences.getInstance(requireContext());
        String current = prefs.getScreenshotProtection();
        int selected = 1;
        if (AppPreferences.SCREENSHOT_DISABLED.equals(current)) selected = 0;
        else if (AppPreferences.SCREENSHOT_ALL.equals(current)) selected = 2;

        String[] options = new String[]{
                getString(R.string.screenshot_disabled),
                getString(R.string.screenshot_private_only),
                getString(R.string.screenshot_all)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_screenshot_protection_title)
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    String mode = AppPreferences.SCREENSHOT_PRIVATE_ONLY;
                    if (which == 0) mode = AppPreferences.SCREENSHOT_DISABLED;
                    else if (which == 2) mode = AppPreferences.SCREENSHOT_ALL;

                    prefs.setScreenshotProtection(mode);
                    updateScreenshotSummary();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updatePrivateChatsSummary() {
        int count = AppPreferences.getInstance(requireContext()).getPrivateThreadIds().size();
        binding.tvPrivateChatsSummary.setText(getString(R.string.settings_private_chats_summary, count));
    }

    private void showPrivateConversationsDialog() {
        AppPreferences prefs = AppPreferences.getInstance(requireContext());
        int count = prefs.getPrivateThreadIds().size();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_private_chats_title)
                .setMessage(getString(R.string.settings_private_chats_summary, count) +
                        "\n\nYou can mark any conversation as private directly from its menu inside the chat screen.")
                .setPositiveButton(R.string.dialog_button_ok, null);

        if (count > 0) {
            builder.setNeutralButton("Clear All Private", (dialog, which) -> {
                prefs.setPrivateThreadIds(new java.util.HashSet<>());
                updatePrivateChatsSummary();
                Snackbar.make(binding.getRoot(), "All private conversation flags cleared", Snackbar.LENGTH_SHORT).show();
            });
        }
        builder.show();
    }

    private void showPinSetupDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);

        TextInputLayout tilPin1 = new TextInputLayout(requireContext());
        TextInputEditText etPin1 = new TextInputEditText(requireContext());
        etPin1.setHint(R.string.dialog_pin_setup_enter);
        etPin1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        tilPin1.addView(etPin1);
        layout.addView(tilPin1);

        TextInputLayout tilPin2 = new TextInputLayout(requireContext());
        TextInputEditText etPin2 = new TextInputEditText(requireContext());
        etPin2.setHint(R.string.dialog_pin_setup_confirm);
        etPin2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        tilPin2.addView(etPin2);
        layout.addView(tilPin2);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_pin_setup_title)
                .setView(layout)
                .setPositiveButton(R.string.dialog_button_ok, (dialog, which) -> {
                    String p1 = etPin1.getText() != null ? etPin1.getText().toString().trim() : "";
                    String p2 = etPin2.getText() != null ? etPin2.getText().toString().trim() : "";

                    if (!PinCryptoUtils.isValidPinFormat(p1)) {
                        Snackbar.make(binding.getRoot(), R.string.dialog_pin_invalid, Snackbar.LENGTH_LONG).show();
                        binding.switchAppLock.setChecked(false);
                        return;
                    }

                    if (!p1.equals(p2)) {
                        Snackbar.make(binding.getRoot(), R.string.dialog_pin_mismatch, Snackbar.LENGTH_LONG).show();
                        binding.switchAppLock.setChecked(false);
                        return;
                    }

                    boolean success = AppLockManager.getInstance(requireContext()).setupPin(p1);
                    if (success) {
                        binding.switchAppLock.setChecked(true);
                        Snackbar.make(binding.getRoot(), R.string.dialog_pin_success, Snackbar.LENGTH_SHORT).show();
                    } else {
                        binding.switchAppLock.setChecked(false);
                        Snackbar.make(binding.getRoot(), "Failed to configure PIN", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    binding.switchAppLock.setChecked(AppLockManager.getInstance(requireContext()).isAppLockEnabled());
                })
                .setCancelable(false)
                .show();
    }

    private void showPinDisableDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);

        TextInputLayout tilPin = new TextInputLayout(requireContext());
        TextInputEditText etPin = new TextInputEditText(requireContext());
        etPin.setHint("Enter 6-digit PIN");
        etPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        tilPin.addView(etPin);
        layout.addView(tilPin);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_pin_disable_confirm)
                .setView(layout)
                .setPositiveButton(R.string.dialog_button_ok, (dialog, which) -> {
                    String pin = etPin.getText() != null ? etPin.getText().toString().trim() : "";
                    boolean success = AppLockManager.getInstance(requireContext()).disableAppLock(pin);
                    if (success) {
                        binding.switchAppLock.setChecked(false);
                        Snackbar.make(binding.getRoot(), R.string.dialog_pin_disabled_toast, Snackbar.LENGTH_SHORT).show();
                    } else {
                        binding.switchAppLock.setChecked(true);
                        Snackbar.make(binding.getRoot(), R.string.auth_lock_incorrect_pin, Snackbar.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    binding.switchAppLock.setChecked(true);
                })
                .setCancelable(false)
                .show();
    }

    private void showChangePinDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);

        TextInputLayout tilOld = new TextInputLayout(requireContext());
        TextInputEditText etOld = new TextInputEditText(requireContext());
        etOld.setHint(R.string.dialog_change_pin_current);
        etOld.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        tilOld.addView(etOld);
        layout.addView(tilOld);

        TextInputLayout tilNew = new TextInputLayout(requireContext());
        TextInputEditText etNew = new TextInputEditText(requireContext());
        etNew.setHint(R.string.dialog_change_pin_new);
        etNew.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        tilNew.addView(etNew);
        layout.addView(tilNew);

        TextInputLayout tilConfirm = new TextInputLayout(requireContext());
        TextInputEditText etConfirm = new TextInputEditText(requireContext());
        etConfirm.setHint(R.string.dialog_pin_setup_confirm);
        etConfirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        tilConfirm.addView(etConfirm);
        layout.addView(tilConfirm);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_change_pin_title)
                .setView(layout)
                .setPositiveButton(R.string.dialog_button_ok, (dialog, which) -> {
                    String oldPin = etOld.getText() != null ? etOld.getText().toString().trim() : "";
                    String newPin = etNew.getText() != null ? etNew.getText().toString().trim() : "";
                    String confirmPin = etConfirm.getText() != null ? etConfirm.getText().toString().trim() : "";

                    if (!PinCryptoUtils.isValidPinFormat(newPin)) {
                        Snackbar.make(binding.getRoot(), R.string.dialog_pin_invalid, Snackbar.LENGTH_LONG).show();
                        return;
                    }

                    if (!newPin.equals(confirmPin)) {
                        Snackbar.make(binding.getRoot(), R.string.dialog_pin_mismatch, Snackbar.LENGTH_LONG).show();
                        return;
                    }

                    boolean success = AppLockManager.getInstance(requireContext()).changePin(oldPin, newPin);
                    if (success) {
                        Snackbar.make(binding.getRoot(), R.string.dialog_pin_changed_toast, Snackbar.LENGTH_SHORT).show();
                    } else {
                        Snackbar.make(binding.getRoot(), R.string.auth_lock_incorrect_pin, Snackbar.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void launchBackupCreate() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(BackupConstants.MIME_TYPE_BACKUP);
        String filename = "NextGen_Messages_Backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + BackupConstants.BACKUP_FILE_EXTENSION;
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        backupCreateLauncher.launch(intent);
    }

    private void showBackupPasswordDialog(@NonNull Uri destinationUri) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);

        TextView tvDisclaimer = new TextView(requireContext());
        tvDisclaimer.setText(R.string.dialog_backup_disclaimer);
        tvDisclaimer.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        tvDisclaimer.setPadding(0, 0, 0, 24);
        layout.addView(tvDisclaimer);

        TextInputLayout tilPass = new TextInputLayout(requireContext());
        TextInputEditText etPass = new TextInputEditText(requireContext());
        etPass.setHint(R.string.dialog_backup_password_hint);
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilPass.addView(etPass);
        layout.addView(tilPass);

        TextInputLayout tilConfirm = new TextInputLayout(requireContext());
        TextInputEditText etConfirm = new TextInputEditText(requireContext());
        etConfirm.setHint(R.string.dialog_backup_password_confirm_hint);
        etConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilConfirm.addView(etConfirm);
        layout.addView(tilConfirm);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_backup_title)
                .setView(layout)
                .setPositiveButton(R.string.dialog_backup_start, (dialog, which) -> {
                    String pass = etPass.getText() != null ? etPass.getText().toString() : "";
                    String confirm = etConfirm.getText() != null ? etConfirm.getText().toString() : "";

                    if (!BackupCrypto.isPasswordStrongEnough(pass)) {
                        Snackbar.make(binding.getRoot(), "Password must be at least 8 characters", Snackbar.LENGTH_LONG).show();
                        return;
                    }

                    if (!pass.equals(confirm)) {
                        Snackbar.make(binding.getRoot(), "Passwords do not match", Snackbar.LENGTH_LONG).show();
                        return;
                    }

                    executeBackup(destinationUri, pass);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void executeBackup(@NonNull Uri destinationUri, @NonNull String password) {
        LinearLayout progressLayout = new LinearLayout(requireContext());
        progressLayout.setOrientation(LinearLayout.VERTICAL);
        progressLayout.setPadding(60, 30, 60, 30);

        ProgressBar progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressLayout.addView(progressBar);

        TextView tvStatus = new TextView(requireContext());
        tvStatus.setText("Preparing backup...");
        tvStatus.setPadding(0, 20, 0, 0);
        progressLayout.addView(tvStatus);

        androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Creating Encrypted Backup")
                .setView(progressLayout)
                .setCancelable(false)
                .show();

        try {
            java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(destinationUri);
            if (out == null) throw new java.io.IOException("Cannot open output stream for selected file");

            EncryptedBackupManager backupManager = new EncryptedBackupManager(requireContext());
            backupManager.createBackup(out, password, new EncryptedBackupManager.BackupProgressListener() {
                @Override
                public void onPhaseStarted(@NonNull String phaseName) {
                    tvStatus.setText(phaseName);
                }

                @Override
                public void onProgress(int current, int total, @NonNull String statusMessage) {
                    tvStatus.setText(statusMessage);
                    if (total > 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setMax(total);
                        progressBar.setProgress(current);
                    }
                }

                @Override
                public void onComplete(@NonNull BackupManifest manifest) {
                    progressDialog.dismiss();
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.toast_backup_complete)
                            .setMessage("Backed up " + manifest.getSmsCount() + " SMS messages, " +
                                    manifest.getMmsCount() + " MMS messages, and " +
                                    manifest.getAttachmentCount() + " attachments safely with AES-256-GCM encryption.")
                            .setPositiveButton(R.string.dialog_button_ok, null)
                            .show();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    progressDialog.dismiss();
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Backup Failed")
                            .setMessage(error.getMessage() != null ? error.getMessage() : "Unknown error")
                            .setPositiveButton(R.string.dialog_button_ok, null)
                            .show();
                }
            });

        } catch (Exception e) {
            progressDialog.dismiss();
            Snackbar.make(binding.getRoot(), "Failed to create backup: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    private void launchRestoreOpen() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        restoreOpenLauncher.launch(intent);
    }

    private void showRestorePasswordDialog(@NonNull Uri backupUri) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);

        TextInputLayout tilPass = new TextInputLayout(requireContext());
        TextInputEditText etPass = new TextInputEditText(requireContext());
        etPass.setHint("Backup Password");
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilPass.addView(etPass);
        layout.addView(tilPass);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_restore_title)
                .setView(layout)
                .setPositiveButton("Verify & Preview", (dialog, which) -> {
                    String password = etPass.getText() != null ? etPass.getText().toString() : "";
                    previewAndConfirmRestore(backupUri, password);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void previewAndConfirmRestore(@NonNull Uri backupUri, @NonNull String password) {
        try {
            java.io.InputStream in = requireContext().getContentResolver().openInputStream(backupUri);
            if (in == null) throw new java.io.IOException("Cannot open selected backup file");

            EncryptedRestoreManager restoreManager = new EncryptedRestoreManager(requireContext());
            restoreManager.previewBackup(in, password, new EncryptedRestoreManager.PreviewCallback() {
                @Override
                public void onPreviewLoaded(@NonNull BackupManifest manifest) {
                    String dateStr = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date(manifest.getTimestamp()));
                    String summary = getString(
                            R.string.dialog_restore_preview_summary,
                            dateStr,
                            manifest.getSmsCount(),
                            manifest.getMmsCount(),
                            manifest.getAttachmentCount()
                    );

                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Confirm Restore")
                            .setMessage(summary)
                            .setPositiveButton(R.string.dialog_restore_confirm, (d, w) -> {
                                executeRestore(backupUri, password);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Decryption Failed")
                            .setMessage("Incorrect password or corrupted/tampered backup archive.")
                            .setPositiveButton(R.string.dialog_button_ok, null)
                            .show();
                }
            });

        } catch (Exception e) {
            Snackbar.make(binding.getRoot(), "Failed to open backup: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    private void executeRestore(@NonNull Uri backupUri, @NonNull String password) {
        if (!viewModel.getSmsRoleManager().isDefaultSmsApp()) {
            Snackbar.make(binding.getRoot(), "Default SMS app role required to restore messages", Snackbar.LENGTH_LONG).show();
            Intent roleIntent = viewModel.getSmsRoleManager().createRequestRoleIntent();
            if (roleIntent != null) {
                roleRequestLauncher.launch(roleIntent);
            }
            return;
        }

        LinearLayout progressLayout = new LinearLayout(requireContext());
        progressLayout.setOrientation(LinearLayout.VERTICAL);
        progressLayout.setPadding(60, 30, 60, 30);

        ProgressBar progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressLayout.addView(progressBar);

        TextView tvStatus = new TextView(requireContext());
        tvStatus.setText("Starting restore...");
        tvStatus.setPadding(0, 20, 0, 0);
        progressLayout.addView(tvStatus);

        androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Restoring Messages")
                .setView(progressLayout)
                .setCancelable(false)
                .show();

        try {
            java.io.InputStream in = requireContext().getContentResolver().openInputStream(backupUri);
            if (in == null) throw new java.io.IOException("Cannot open input stream for restore");

            EncryptedRestoreManager restoreManager = new EncryptedRestoreManager(requireContext());
            restoreManager.restoreBackup(in, password, new EncryptedRestoreManager.RestoreProgressListener() {
                @Override
                public void onPhaseStarted(@NonNull String phaseName) {
                    tvStatus.setText(phaseName);
                }

                @Override
                public void onProgress(int current, int total, @NonNull String statusMessage) {
                    tvStatus.setText(statusMessage);
                    if (total > 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setMax(total);
                        progressBar.setProgress(current);
                    }
                }

                @Override
                public void onComplete(@NonNull EncryptedRestoreManager.RestoreResult result) {
                    progressDialog.dismiss();
                    String msg = getString(
                            R.string.toast_restore_complete,
                            result.smsRestored,
                            result.mmsRestored,
                            result.attachmentsRestored,
                            result.smsDuplicates + result.mmsDuplicates
                    );
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Restore Complete")
                            .setMessage(msg)
                            .setPositiveButton(R.string.dialog_button_ok, null)
                            .show();
                    updatePrivateChatsSummary();
                }

                @Override
                public void onError(@NonNull Exception error) {
                    progressDialog.dismiss();
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Restore Failed")
                            .setMessage(error.getMessage() != null ? error.getMessage() : "Unknown error")
                            .setPositiveButton(R.string.dialog_button_ok, null)
                            .show();
                }
            });

        } catch (Exception e) {
            progressDialog.dismiss();
            Snackbar.make(binding.getRoot(), "Failed to restore: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
