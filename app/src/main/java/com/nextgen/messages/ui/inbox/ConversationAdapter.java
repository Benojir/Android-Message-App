package com.nextgen.messages.ui.inbox;

import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.databinding.ItemConversationBinding;
import com.nextgen.messages.utils.DateFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * High-performance ListAdapter for rendering conversation summaries in the inbox.
 * Uses DiffUtil for smooth animations, and supports multi-selection and contextual actions.
 */
public class ConversationAdapter extends ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(@NonNull Conversation conversation);
    }

    public interface OnConversationLongClickListener {
        void onConversationLongClick(@NonNull Conversation conversation);
    }

    private static final DiffUtil.ItemCallback<Conversation> DIFF_CALLBACK = new DiffUtil.ItemCallback<Conversation>() {
        @Override
        public boolean areItemsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
            return oldItem.getThreadId() == newItem.getThreadId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Conversation oldItem, @NonNull Conversation newItem) {
            return oldItem.equals(newItem);
        }
    };

    private final OnConversationClickListener clickListener;
    @Nullable
    private OnConversationLongClickListener longClickListener;

    private final Set<Long> selectedThreadIds = new HashSet<>();

    public ConversationAdapter(@NonNull OnConversationClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
        setHasStableIds(true);
    }

    public void setOnConversationLongClickListener(@Nullable OnConversationLongClickListener listener) {
        this.longClickListener = listener;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getThreadId();
    }

    public boolean isInSelectionMode() {
        return !selectedThreadIds.isEmpty();
    }

    public int getSelectedCount() {
        return selectedThreadIds.size();
    }

    @NonNull
    public Set<Long> getSelectedThreadIds() {
        return Collections.unmodifiableSet(selectedThreadIds);
    }

    public boolean isSelected(long threadId) {
        return selectedThreadIds.contains(threadId);
    }

    public void toggleSelection(long threadId) {
        if (selectedThreadIds.contains(threadId)) {
            selectedThreadIds.remove(threadId);
        } else {
            selectedThreadIds.add(threadId);
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        selectedThreadIds.clear();
        for (int i = 0; i < getItemCount(); i++) {
            selectedThreadIds.add(getItem(i).getThreadId());
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        if (!selectedThreadIds.isEmpty()) {
            selectedThreadIds.clear();
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemConversationBinding binding = ItemConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ConversationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        Conversation item = getItem(position);
        boolean isSelected = selectedThreadIds.contains(item.getThreadId());
        holder.bind(item, isSelected, clickListener, longClickListener, this);
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ItemConversationBinding binding;

        ConversationViewHolder(@NonNull ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull Conversation item,
                boolean isSelected,
                @NonNull OnConversationClickListener clickListener,
                @Nullable OnConversationLongClickListener longClickListener,
                @NonNull ConversationAdapter adapter
        ) {
            binding.tvTitle.setText(item.getTitle());
            binding.tvSnippet.setText(item.getDisplaySnippet());
            binding.tvTimestamp.setText(DateFormatter.formatConversationTime(item.getDate()));

            // Unread treatment
            if (item.hasUnread()) {
                binding.tvTitle.setTypeface(null, Typeface.BOLD);
                binding.tvSnippet.setTypeface(null, Typeface.NORMAL);
                binding.tvUnreadBadge.setVisibility(View.VISIBLE);
                binding.tvUnreadBadge.setText(item.getUnreadCount() > 99 ? "99+" : String.valueOf(item.getUnreadCount()));

                String desc = itemView.getContext().getString(
                        R.string.cd_conversation_item_unread,
                        item.getTitle(),
                        item.getUnreadCount()
                );
                itemView.setContentDescription((isSelected ? "Selected. " : "") + desc);
            } else {
                binding.tvTitle.setTypeface(null, Typeface.NORMAL);
                binding.tvSnippet.setTypeface(null, Typeface.NORMAL);
                binding.tvUnreadBadge.setVisibility(View.GONE);

                String desc = itemView.getContext().getString(
                        R.string.cd_conversation_item_read,
                        item.getTitle()
                );
                itemView.setContentDescription((isSelected ? "Selected. " : "") + desc);
            }

            // Selection indicator & avatar
            if (isSelected) {
                binding.ivSelectionCheck.setVisibility(View.VISIBLE);
                binding.tvAvatarInitials.setVisibility(View.GONE);
                binding.ivAvatar.setVisibility(View.GONE);
                binding.getRoot().setBackgroundColor(Color.parseColor("#261976D2")); // 15% primary
            } else {
                binding.ivSelectionCheck.setVisibility(View.GONE);
                binding.getRoot().setBackgroundColor(Color.TRANSPARENT);

                Uri photoUri = item.getPhotoUri();
                if (photoUri != null) {
                    try {
                        binding.ivAvatar.setImageURI(photoUri);
                        binding.ivAvatar.setVisibility(View.VISIBLE);
                        binding.tvAvatarInitials.setVisibility(View.GONE);
                    } catch (Exception e) {
                        showInitialsFallback(item);
                    }
                } else {
                    showInitialsFallback(item);
                }
            }

            itemView.setOnClickListener(v -> {
                if (adapter.isInSelectionMode()) {
                    adapter.toggleSelection(item.getThreadId());
                    clickListener.onConversationClick(item);
                } else {
                    clickListener.onConversationClick(item);
                }
            });

            itemView.setOnLongClickListener(v -> {
                adapter.toggleSelection(item.getThreadId());
                if (longClickListener != null) {
                    longClickListener.onConversationLongClick(item);
                }
                return true;
            });
        }

        private void showInitialsFallback(@NonNull Conversation item) {
            binding.ivAvatar.setImageDrawable(null);
            binding.ivAvatar.setVisibility(View.GONE);
            binding.tvAvatarInitials.setVisibility(View.VISIBLE);
            binding.tvAvatarInitials.setText(item.getInitials());
        }
    }
}
