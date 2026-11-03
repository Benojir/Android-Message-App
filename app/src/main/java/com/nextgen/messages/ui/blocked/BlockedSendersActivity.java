package com.nextgen.messages.ui.blocked;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nextgen.messages.R;
import com.nextgen.messages.data.repository.BlockedSenderRepository;
import com.nextgen.messages.databinding.ActivityBlockedSendersBinding;
import com.nextgen.messages.smartinbox.db.BlockedSenderEntity;
import java.util.List;

/**
 * Screen for managing blocked senders and numbers, allowing manual addition and unblocking.
 */
public class BlockedSendersActivity extends AppCompatActivity {

    private ActivityBlockedSendersBinding binding;
    private BlockedSendersAdapter adapter;
    private BlockedSenderRepository blockedRepo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        com.nextgen.messages.utils.ThemeHelper.applyToActivity(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivityBlockedSendersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        blockedRepo = BlockedSenderRepository.getInstance(this);

        setupWindowInsets();
        setupToolbar();
        setupRecyclerView();
        setupFab();
        observeBlockedSenders();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new BlockedSendersAdapter(entity -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.dialog_unblock_title, entity.getSenderIdentifier()))
                    .setMessage(R.string.dialog_unblock_message)
                    .setPositiveButton(R.string.action_unblock, (dialog, which) -> {
                        blockedRepo.unblockSender(entity.getSenderIdentifier(), () -> {
                            Snackbar.make(binding.getRoot(), R.string.toast_sender_unblocked, Snackbar.LENGTH_SHORT).show();
                        });
                    })
                    .setNegativeButton(R.string.dialog_button_cancel, null)
                    .show();
        });

        binding.rvBlocked.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBlocked.setAdapter(adapter);
    }

    private void setupFab() {
        binding.fabAddBlocked.setOnClickListener(v -> showAddBlockedDialog());
    }

    private void showAddBlockedDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.dialog_add_blocked_hint);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_add_blocked_title)
                .setView(input)
                .setPositiveButton(R.string.action_block, (dialog, which) -> {
                    String number = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!number.isEmpty()) {
                        blockedRepo.blockSender(number, "Manually added by user", () -> {
                            Snackbar.make(binding.getRoot(), R.string.toast_sender_blocked, Snackbar.LENGTH_SHORT).show();
                        });
                    }
                })
                .setNegativeButton(R.string.dialog_button_cancel, null)
                .show();
    }

    private void observeBlockedSenders() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        blockedRepo.getAllBlockedSendersLiveData().observe(this, list -> {
            binding.progressLoading.setVisibility(View.GONE);
            adapter.submitList(list);
            boolean empty = list == null || list.isEmpty();
            binding.layoutEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvBlocked.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
