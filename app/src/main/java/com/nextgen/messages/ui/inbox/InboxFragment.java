package com.nextgen.messages.ui.inbox;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.databinding.FragmentInboxBinding;
import com.nextgen.messages.ui.components.ComposeBottomSheetDialogFragment;

/**
 * InboxFragment displaying the primary messages list or empty state.
 */
public class InboxFragment extends Fragment {

    private FragmentInboxBinding binding;
    private InboxViewModel viewModel;

    public static InboxFragment newInstance() {
        return new InboxFragment();
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentInboxBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(InboxViewModel.class);

        setupWindowInsets();
        setupToolbar();
        setupFab();
        observeViewModel();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
    }

    private void setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClicked);
    }

    private boolean onToolbarMenuItemClicked(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            Snackbar.make(binding.getRoot(), R.string.search_placeholder_notice, Snackbar.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_mark_all_read) {
            Snackbar.make(binding.getRoot(), R.string.empty_inbox_title, Snackbar.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_archived || id == R.id.action_starred) {
            Snackbar.make(binding.getRoot(), item.getTitle() + " - " + getString(R.string.badge_phase2), Snackbar.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return false;
    }

    private void setupFab() {
        binding.fabCompose.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            ComposeBottomSheetDialogFragment.newInstance()
                    .show(getChildFragmentManager(), ComposeBottomSheetDialogFragment.TAG);
        });
    }

    private void observeViewModel() {
        viewModel.getConversations().observe(getViewLifecycleOwner(), conversations -> {
            // For Step 1, conversations list is empty; professional empty state is visible
            binding.ivEmptyState.setVisibility(View.VISIBLE);
            binding.tvEmptyTitle.setVisibility(View.VISIBLE);
            binding.tvEmptySubtitle.setVisibility(View.VISIBLE);
            binding.cardPrivacyBadge.setVisibility(View.VISIBLE);
        });
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_dialog_title)
                .setMessage(R.string.about_dialog_content)
                .setPositiveButton(R.string.dialog_button_ok, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
