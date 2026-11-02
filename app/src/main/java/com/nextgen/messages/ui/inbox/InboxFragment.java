package com.nextgen.messages.ui.inbox;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.OnBackPressedCallback;
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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.repository.SmsRepository;
import com.nextgen.messages.databinding.FragmentInboxBinding;
import com.nextgen.messages.telephony.SmsContentObserver;
import com.nextgen.messages.ui.compose.ComposeActivity;
import com.nextgen.messages.ui.conversation.ConversationActivity;
import com.nextgen.messages.ui.archived.ArchivedActivity;
import com.nextgen.messages.ui.starred.StarredMessagesActivity;
import com.nextgen.messages.ui.spam.SpamBlockedActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * InboxFragment displaying the real device SMS inbox, real-time updates via ContentObserver,
 * swipe gestures for quick actions, multi-conversation selection CAB, and debounced search.
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
        setupSelectionToolbar();
        setupCategoryChips();
        setupBackNavigation();
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
        conversationAdapter.setOnConversationLongClickListener(conversation -> {
            binding.rvConversations.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            updateSelectionUi();
        });

        binding.rvConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvConversations.setHasFixedSize(true);
        binding.rvConversations.setAdapter(conversationAdapter);

        // Attach Swipe Gestures (Swipe Right: Mark Read/Unread, Swipe Left: Delete)
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ConversationSwipeCallback(
                requireContext(),
                conversationAdapter,
                new ConversationSwipeCallback.SwipeListener() {
                    @Override
                    public void onSwipeToggleRead(int position, @NonNull Conversation conversation) {
                        handleSwipeToggleRead(position, conversation);
                    }

                    @Override
                    public void onSwipeDelete(int position, @NonNull Conversation conversation) {
                        handleSwipeDelete(position, conversation);
                    }
                }
        ));
        itemTouchHelper.attachToRecyclerView(binding.rvConversations);
    }

    private void onConversationSelected(@NonNull Conversation conversation) {
        if (conversationAdapter.isInSelectionMode()) {
            updateSelectionUi();
            return;
        }

        Intent intent = new Intent(requireContext(), ConversationActivity.class);
        intent.putExtra(ConversationActivity.EXTRA_THREAD_ID, conversation.getThreadId());
        intent.putExtra(ConversationActivity.EXTRA_ADDRESS, conversation.getAddress());
        intent.putExtra(ConversationActivity.EXTRA_DISPLAY_NAME, conversation.getTitle());
        startActivity(intent);
    }

    private void handleSwipeToggleRead(int position, @NonNull Conversation conversation) {
        conversationAdapter.notifyItemChanged(position);
        boolean isCurrentlyUnread = conversation.hasUnread();

        if (isCurrentlyUnread) {
            viewModel.markConversationsAsRead(Collections.singleton(conversation.getThreadId()), null);
            Snackbar.make(binding.getRoot(), R.string.toast_mark_read_success, Snackbar.LENGTH_SHORT).show();
        } else {
            viewModel.markConversationAsUnread(conversation.getThreadId(), null);
            Snackbar.make(binding.getRoot(), R.string.toast_mark_unread_success, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void handleSwipeDelete(int position, @NonNull Conversation conversation) {
        conversationAdapter.notifyItemChanged(position);
        showDeleteConfirmationDialog(Collections.singleton(conversation.getThreadId()));
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

    private void setupSelectionToolbar() {
        binding.toolbarSelection.setNavigationOnClickListener(v -> {
            conversationAdapter.clearSelection();
            updateSelectionUi();
        });

        binding.toolbarSelection.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_pin) {
                Set<Long> selected = new HashSet<>(conversationAdapter.getSelectedThreadIds());
                if (!selected.isEmpty()) {
                    viewModel.togglePinConversations(selected, () -> {
                        conversationAdapter.clearSelection();
                        updateSelectionUi();
                    });
                }
                return true;
            } else if (id == R.id.action_archive) {
                Set<Long> selected = new HashSet<>(conversationAdapter.getSelectedThreadIds());
                if (!selected.isEmpty()) {
                    viewModel.archiveConversations(selected, () -> {
                        conversationAdapter.clearSelection();
                        updateSelectionUi();
                        Snackbar.make(binding.getRoot(), R.string.toast_archived, Snackbar.LENGTH_SHORT).show();
                    });
                }
                return true;
            } else if (id == R.id.action_block) {
                List<Conversation> selectedConvs = getSelectedConversations();
                if (!selectedConvs.isEmpty()) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.action_block)
                            .setMessage(R.string.dialog_block_message)
                            .setPositiveButton(R.string.action_block, (d, w) -> {
                                viewModel.blockSelectedConversations(selectedConvs, () -> {
                                    conversationAdapter.clearSelection();
                                    updateSelectionUi();
                                    Snackbar.make(binding.getRoot(), R.string.toast_sender_blocked, Snackbar.LENGTH_SHORT).show();
                                });
                            })
                            .setNegativeButton(R.string.dialog_button_cancel, null)
                            .show();
                }
                return true;
            } else if (id == R.id.action_report_spam) {
                List<Conversation> selectedConvs = getSelectedConversations();
                if (!selectedConvs.isEmpty()) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.action_report_spam)
                            .setMessage(R.string.dialog_report_spam_message)
                            .setPositiveButton(R.string.action_report_spam, (d, w) -> {
                                viewModel.reportSpamSelectedConversations(selectedConvs, () -> {
                                    conversationAdapter.clearSelection();
                                    updateSelectionUi();
                                    Snackbar.make(binding.getRoot(), R.string.toast_sender_blocked, Snackbar.LENGTH_SHORT).show();
                                });
                            })
                            .setNegativeButton(R.string.dialog_button_cancel, null)
                            .show();
                }
                return true;
            } else if (id == R.id.action_mark_read) {
                Set<Long> selected = new HashSet<>(conversationAdapter.getSelectedThreadIds());
                if (!selected.isEmpty()) {
                    viewModel.markConversationsAsRead(selected, null);
                    conversationAdapter.clearSelection();
                    updateSelectionUi();
                    Snackbar.make(binding.getRoot(), R.string.toast_mark_read_success, Snackbar.LENGTH_SHORT).show();
                }
                return true;
            } else if (id == R.id.action_delete) {
                Set<Long> selected = new HashSet<>(conversationAdapter.getSelectedThreadIds());
                if (!selected.isEmpty()) {
                    showDeleteConfirmationDialog(selected);
                }
                return true;
            } else if (id == R.id.action_move_category) {
                Set<Long> selected = new HashSet<>(conversationAdapter.getSelectedThreadIds());
                if (!selected.isEmpty()) {
                    showBulkMoveCategoryDialog(selected);
                }
                return true;
            } else if (id == R.id.action_select_all) {
                conversationAdapter.selectAll();
                updateSelectionUi();
                return true;
            }
            return false;
        });
    }

    private void updateSelectionUi() {
        if (conversationAdapter.isInSelectionMode()) {
            binding.toolbar.setVisibility(View.GONE);
            binding.toolbarSelection.setVisibility(View.VISIBLE);
            int count = conversationAdapter.getSelectedCount();
            binding.toolbarSelection.setTitle(getString(R.string.selected_count, count));
        } else {
            binding.toolbarSelection.setVisibility(View.GONE);
            binding.toolbar.setVisibility(View.VISIBLE);
        }
    }

    private void setupBackNavigation() {
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (conversationAdapter != null && conversationAdapter.isInSelectionMode()) {
                    conversationAdapter.clearSelection();
                    updateSelectionUi();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private boolean onToolbarMenuItemClicked(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_mark_all_read) {
            List<Conversation> list = conversationAdapter.getCurrentList();
            if (list != null && !list.isEmpty()) {
                Set<Long> allThreads = new HashSet<>();
                for (Conversation c : list) {
                    if (c.hasUnread()) {
                        allThreads.add(c.getThreadId());
                    }
                }
                if (!allThreads.isEmpty()) {
                    viewModel.markConversationsAsRead(allThreads, null);
                    Snackbar.make(binding.getRoot(), R.string.toast_mark_read_success, Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(binding.getRoot(), R.string.toast_mark_read_success, Snackbar.LENGTH_SHORT).show();
                }
            }
            return true;
        } else if (id == R.id.action_archived) {
            startActivity(new Intent(requireContext(), ArchivedActivity.class));
            return true;
        } else if (id == R.id.action_starred) {
            startActivity(new Intent(requireContext(), StarredMessagesActivity.class));
            return true;
        } else if (id == R.id.action_spam_blocked) {
            startActivity(new Intent(requireContext(), SpamBlockedActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return false;
    }

    @NonNull
    private List<Conversation> getSelectedConversations() {
        Set<Long> ids = conversationAdapter.getSelectedThreadIds();
        List<Conversation> currentList = conversationAdapter.getCurrentList();
        List<Conversation> result = new ArrayList<>();
        if (currentList != null) {
            for (Conversation c : currentList) {
                if (ids.contains(c.getThreadId())) {
                    result.add(c);
                }
            }
        }
        return result;
    }

    private void showDeleteConfirmationDialog(@NonNull Set<Long> threadIds) {
        if (threadIds.isEmpty()) {
            return;
        }

        int count = threadIds.size();
        String title = count == 1
                ? getString(R.string.dialog_delete_conversation_title)
                : getString(R.string.dialog_delete_conversations_title, count);
        String desc = count == 1
                ? getString(R.string.dialog_delete_conversation_desc)
                : getString(R.string.dialog_delete_conversations_desc, count);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(desc)
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    conversationAdapter.clearSelection();
                    updateSelectionUi();
                    executeDelayedConversationDelete(threadIds);
                })
                .show();
    }

    private void executeDelayedConversationDelete(@NonNull Set<Long> threadIds) {
        viewModel.markConversationsPendingDelete(threadIds);

        int count = threadIds.size();
        String snackbarText = count == 1
                ? getString(R.string.conversation_deleted)
                : getString(R.string.conversations_deleted, count);

        Snackbar snackbar = Snackbar.make(binding.getRoot(), snackbarText, 4000);
        snackbar.setAction(R.string.undo, v -> {
            viewModel.cancelPendingDelete(threadIds);
        });

        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    viewModel.commitPendingDelete(threadIds, new SmsRepository.DataCallback<Integer>() {
                        @Override
                        public void onSuccess(Integer result) {
                            // Deletion completed
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            if (isAdded()) {
                                Snackbar.make(binding.getRoot(), R.string.toast_delete_failed, Snackbar.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });

        snackbar.show();
    }

    private void setupFab() {
        binding.fabCompose.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            startActivity(new Intent(requireContext(), ComposeActivity.class));
        });
    }

    private void setupPermissionBanner() {
        binding.btnGrantSmsPermission.setOnClickListener(v -> requestRequiredPermissions());
    }

    private void requestRequiredPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.READ_SMS);
        perms.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        permissionLauncher.launch(perms.toArray(new String[0]));
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

    private void setupCategoryChips() {
        binding.chipGroupInboxCategories.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds == null || checkedIds.isEmpty()) {
                binding.chipCatAll.setChecked(true);
                return;
            }
            int checkedId = checkedIds.get(0);
            View chip = group.findViewById(checkedId);
            if (chip != null && chip.getTag() != null) {
                viewModel.setSelectedCategory(chip.getTag().toString());
            }
        });

        // Observe custom categories from Room to add dynamic chips
        com.nextgen.messages.smartinbox.SmartInboxRepository.getInstance(requireContext())
                .getAllCategoriesLiveData()
                .observe(getViewLifecycleOwner(), categories -> {
                    if (categories == null || binding == null) return;
                    for (com.nextgen.messages.smartinbox.db.MessageCategoryEntity cat : categories) {
                        if (!cat.isBuiltIn()) {
                            String key = cat.getCategoryKey();
                            boolean exists = false;
                            for (int i = 0; i < binding.chipGroupInboxCategories.getChildCount(); i++) {
                                View child = binding.chipGroupInboxCategories.getChildAt(i);
                                if (key.equals(child.getTag())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                com.google.android.material.chip.Chip customChip = new com.google.android.material.chip.Chip(requireContext());
                                customChip.setText(cat.getDisplayName());
                                customChip.setTag(key);
                                customChip.setCheckable(true);
                                binding.chipGroupInboxCategories.addView(customChip);
                            }
                        }
                    }
                });
    }

    /**
     * Selects and scrolls to the specified category filter chip.
     */
    public void setSelectedCategory(@NonNull String categoryKey) {
        if (binding == null) return;
        for (int i = 0; i < binding.chipGroupInboxCategories.getChildCount(); i++) {
            View child = binding.chipGroupInboxCategories.getChildAt(i);
            if (child instanceof com.google.android.material.chip.Chip && categoryKey.equals(child.getTag())) {
                ((com.google.android.material.chip.Chip) child).setChecked(true);
                binding.scrollCategoryChips.post(() -> binding.scrollCategoryChips.smoothScrollTo(child.getLeft(), 0));
                break;
            }
        }
    }

    private void showBulkMoveCategoryDialog(@NonNull Set<Long> threadIds) {
        com.nextgen.messages.smartinbox.SmartInboxRepository repo =
                com.nextgen.messages.smartinbox.SmartInboxRepository.getInstance(requireContext());

        com.nextgen.messages.utils.AppExecutors.getInstance().diskIO().execute(() -> {
            List<com.nextgen.messages.smartinbox.db.MessageCategoryEntity> categories = repo.getAllCategoriesSync();
            categories.removeIf(c -> "all".equals(c.getCategoryKey()));

            CharSequence[] items = new CharSequence[categories.size()];
            for (int i = 0; i < categories.size(); i++) {
                items[i] = com.nextgen.messages.ui.conversation.MessageDetailsDialog.getLocalizedCategoryName(
                        requireContext(), categories.get(i).getCategoryKey());
            }

            com.nextgen.messages.utils.AppExecutors.getInstance().mainThread().execute(() -> {
                final int[] selected = {0};
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dialog_change_category_title)
                        .setSingleChoiceItems(items, 0, (dialog, which) -> selected[0] = which)
                        .setPositiveButton(R.string.dialog_button_ok, (dialog, which) -> {
                            if (selected[0] >= 0 && selected[0] < categories.size()) {
                                String targetKey = categories.get(selected[0]).getCategoryKey();
                                com.nextgen.messages.utils.AppExecutors.getInstance().diskIO().execute(() -> {
                                    for (Long tid : threadIds) {
                                        List<com.nextgen.messages.data.model.SmsMessage> msgs =
                                                SmsRepository.getInstance(requireContext()).getMessagesForThreadSync(tid);
                                        for (com.nextgen.messages.data.model.SmsMessage m : msgs) {
                                            repo.setManualMessageCategory(tid, m.getId(), m.isMms(), targetKey, null);
                                        }
                                    }
                                    com.nextgen.messages.utils.AppExecutors.getInstance().mainThread().execute(() -> {
                                        conversationAdapter.clearSelection();
                                        updateSelectionUi();
                                        viewModel.setSelectedCategory(viewModel.getSelectedCategory().getValue());
                                        Snackbar.make(binding.getRoot(), R.string.toast_category_updated, Snackbar.LENGTH_SHORT).show();
                                    });
                                });
                            }
                        })
                        .setNegativeButton(R.string.dialog_button_cancel, null)
                        .show();
            });
        });
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
