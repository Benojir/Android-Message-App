package com.nextgen.messages.ui.inbox;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.databinding.FragmentInboxBinding;
import com.nextgen.messages.telephony.SmsContentObserver;
import com.nextgen.messages.ui.components.ComposeBottomSheetDialogFragment;
import com.nextgen.messages.ui.conversation.ConversationActivity;
import java.util.List;

/**
 * InboxFragment displaying the real device SMS inbox, real-time updates via ContentObserver,
 * and debounced search across contacts and messages.
 */
public class InboxFragment extends Fragment {

    private FragmentInboxBinding binding;
    private InboxViewModel viewModel;
    private ConversationAdapter conversationAdapter;
    private SmsContentObserver smsObserver;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (viewModel != null) {
                    viewModel.checkPermissionAndLoad();
                }
            });

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
        setupRecyclerView();
        setupToolbar();
        setupFab();
        setupPermissionBanner();
        observeViewModel();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
    }

    private void setupRecyclerView() {
        conversationAdapter = new ConversationAdapter(this::onConversationSelected);
        binding.rvConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvConversations.setHasFixedSize(true);
        binding.rvConversations.setAdapter(conversationAdapter);
    }

    private void onConversationSelected(@NonNull Conversation conversation) {
        Intent intent = new Intent(requireContext(), ConversationActivity.class);
        intent.putExtra(ConversationActivity.EXTRA_THREAD_ID, conversation.getThreadId());
        intent.putExtra(ConversationActivity.EXTRA_ADDRESS, conversation.getAddress());
        intent.putExtra(ConversationActivity.EXTRA_DISPLAY_NAME, conversation.getTitle());
        startActivity(intent);
    }

    private void setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClicked);

        Menu menu = binding.toolbar.getMenu();
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.search_hint));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        viewModel.onSearchQueryChanged(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        viewModel.onSearchQueryChanged(newText);
                        return true;
                    }
                });

                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                        viewModel.onSearchQueryChanged("");
                        return true;
                    }
                });
            }
        }
    }

    private boolean onToolbarMenuItemClicked(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_mark_all_read) {
            Snackbar.make(binding.getRoot(), R.string.conversation_readonly_banner, Snackbar.LENGTH_SHORT).show();
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

    private void setupPermissionBanner() {
        binding.btnGrantSmsPermission.setOnClickListener(v -> requestRequiredPermissions());
    }

    private void requestRequiredPermissions() {
        permissionLauncher.launch(new String[]{
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS
        });
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.progressLoading.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });

        viewModel.getIsSmsPermissionGranted().observe(getViewLifecycleOwner(), granted -> {
            if (Boolean.FALSE.equals(granted)) {
                binding.layoutPermissionBanner.setVisibility(View.VISIBLE);
                binding.rvConversations.setVisibility(View.GONE);
                binding.layoutEmptyInbox.setVisibility(View.GONE);
                binding.layoutEmptySearch.setVisibility(View.GONE);
            } else {
                binding.layoutPermissionBanner.setVisibility(View.GONE);
            }
        });

        viewModel.getConversations().observe(getViewLifecycleOwner(), this::updateConversationList);
    }

    private void updateConversationList(@NonNull List<Conversation> list) {
        Boolean hasPermission = viewModel.getIsSmsPermissionGranted().getValue();
        if (Boolean.FALSE.equals(hasPermission)) {
            binding.layoutPermissionBanner.setVisibility(View.VISIBLE);
            binding.rvConversations.setVisibility(View.GONE);
            binding.layoutEmptyInbox.setVisibility(View.GONE);
            binding.layoutEmptySearch.setVisibility(View.GONE);
            return;
        }

        binding.layoutPermissionBanner.setVisibility(View.GONE);
        String query = viewModel.getSearchQuery().getValue();
        boolean isSearching = query != null && !query.trim().isEmpty();

        if (list.isEmpty()) {
            binding.rvConversations.setVisibility(View.GONE);
            if (isSearching) {
                binding.layoutEmptyInbox.setVisibility(View.GONE);
                binding.layoutEmptySearch.setVisibility(View.VISIBLE);
                binding.tvSearchEmptySubtitle.setText(getString(R.string.search_empty_subtitle, query));
            } else {
                binding.layoutEmptySearch.setVisibility(View.GONE);
                binding.layoutEmptyInbox.setVisibility(View.VISIBLE);
            }
        } else {
            binding.layoutEmptyInbox.setVisibility(View.GONE);
            binding.layoutEmptySearch.setVisibility(View.GONE);
            binding.rvConversations.setVisibility(View.VISIBLE);
            conversationAdapter.submitList(list);
        }
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_dialog_title)
                .setMessage(R.string.about_dialog_content)
                .setPositiveButton(R.string.dialog_button_ok, null)
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (smsObserver == null) {
            smsObserver = SmsContentObserver.createOnMainThread(() -> {
                if (isAdded() && viewModel != null) {
                    viewModel.loadConversations();
                }
            });
        }
        smsObserver.register(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.checkPermissionAndLoad();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (smsObserver != null) {
            smsObserver.unregister(requireContext());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
