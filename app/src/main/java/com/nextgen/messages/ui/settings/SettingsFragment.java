package com.nextgen.messages.ui.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.nextgen.messages.utils.ThemeHelper;

/**
 * SettingsFragment providing theme management, dynamic colors toggle,
 * application information, and clearly marked roadmap placeholders.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
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
        setupAppearanceSection();
        setupGeneralSection();
        setupFutureSection();
        observeViewModel();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
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

    private void setupGeneralSection() {
        binding.rowAbout.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.about_dialog_title)
                    .setMessage(R.string.about_dialog_content)
                    .setPositiveButton(R.string.dialog_button_ok, null)
                    .show();
        });
    }

    private void setupFutureSection() {
        binding.rowFutureDefaultSms.setOnClickListener(v -> showFutureFeatureDialog(
                getString(R.string.settings_default_sms_title),
                getString(R.string.badge_phase2)
        ));

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
        viewModel.getThemeMode().observe(getViewLifecycleOwner(), mode -> {
            binding.tvThemeSummary.setText(ThemeHelper.getThemeName(requireContext(), mode));
        });

        viewModel.getDynamicColorsEnabled().observe(getViewLifecycleOwner(), enabled -> {
            binding.switchDynamicColors.setChecked(enabled != null && enabled);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
