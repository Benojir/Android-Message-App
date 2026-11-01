package com.nextgen.messages.ui.inbox;

import android.graphics.Typeface;
import android.net.Uri;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.databinding.ItemConversationBinding;
import com.nextgen.messages.utils.DateFormatter;

/**
 * High-performance ListAdapter for rendering conversation summaries in the inbox.
 * Uses DiffUtil for smooth animations and minimal layout recalculations.
 */
public class ConversationAdapter extends ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(@NonNull Conversation conversation);
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

    public ConversationAdapter(@NonNull OnConversationClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
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
        holder.bind(getItem(position), clickListener);
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final ItemConversationBinding binding;

        ConversationViewHolder(@NonNull ItemConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Conversation item, @NonNull OnConversationClickListener listener) {
            binding.tvTitle.setText(item.getTitle());
            binding.tvSnippet.setText(item.getDisplaySnippet());
            binding.tvTimestamp.setText(DateFormatter.formatConversationTime(item.getDate()));

            // Unread treatment: bold title, high contrast snippet, and numerical badge
            if (item.hasUnread()) {
                binding.tvTitle.setTypeface(null, Typeface.BOLD);
                binding.tvSnippet.setTypeface(null, Typeface.NORMAL);
                binding.tvUnreadBadge.setVisibility(View.VISIBLE);
                binding.tvUnreadBadge.setText(item.getUnreadCount() > 99 ? "99+" : String.valueOf(item.getUnreadCount()));

                // Set accessibility description
                String desc = itemView.getContext().getString(
                        R.string.cd_conversation_item_unread,
                        item.getTitle(),
                        item.getUnreadCount()
                );
                itemView.setContentDescription(desc);
            } else {
                binding.tvTitle.setTypeface(null, Typeface.NORMAL);
                binding.tvSnippet.setTypeface(null, Typeface.NORMAL);
                binding.tvUnreadBadge.setVisibility(View.GONE);

                String desc = itemView.getContext().getString(
                        R.string.cd_conversation_item_read,
                        item.getTitle()
                );
                itemView.setContentDescription(desc);
            }

            // Contact photo or initials fallback
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

            itemView.setOnClickListener(v -> listener.onConversationClick(item));
        }

        private void showInitialsFallback(@NonNull Conversation item) {
            binding.ivAvatar.setImageDrawable(null);
            binding.ivAvatar.setVisibility(View.GONE);
            binding.tvAvatarInitials.setVisibility(View.VISIBLE);
            binding.tvAvatarInitials.setText(item.getInitials());
        }
    }
}
