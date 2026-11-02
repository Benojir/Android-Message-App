package com.nextgen.messages.ui.blocked;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.databinding.ItemBlockedSenderBinding;
import com.nextgen.messages.smartinbox.db.BlockedSenderEntity;
import com.nextgen.messages.utils.DateFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter rendering blocked numbers and sender IDs with unblocking support.
 */
public class BlockedSendersAdapter extends RecyclerView.Adapter<BlockedSendersAdapter.ViewHolder> {

    public interface OnUnblockClickListener {
        void onUnblockClick(@NonNull BlockedSenderEntity entity);
    }

    private final List<BlockedSenderEntity> items = new ArrayList<>();
    private final OnUnblockClickListener unblockListener;

    public BlockedSendersAdapter(@NonNull OnUnblockClickListener unblockListener) {
        this.unblockListener = unblockListener;
    }

    public void submitList(List<BlockedSenderEntity> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBlockedSenderBinding binding = ItemBlockedSenderBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), unblockListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBlockedSenderBinding binding;

        ViewHolder(@NonNull ItemBlockedSenderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull BlockedSenderEntity item, @NonNull OnUnblockClickListener unblockListener) {
            binding.tvIdentifier.setText(item.getSenderIdentifier());
            binding.tvBlockedDate.setText("Blocked " + DateFormatter.formatConversationTime(item.getBlockedAt()));

            if (item.getBlockReason() != null && !item.getBlockReason().trim().isEmpty()) {
                binding.tvReason.setVisibility(View.VISIBLE);
                binding.tvReason.setText(item.getBlockReason().trim());
            } else {
                binding.tvReason.setVisibility(View.GONE);
            }

            binding.btnUnblock.setOnClickListener(v -> unblockListener.onUnblockClick(item));
        }
    }
}
