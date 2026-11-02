package com.nextgen.messages.ui.starred;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.databinding.ItemStarredMessageBinding;
import com.nextgen.messages.utils.DateFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter rendering starred SMS and MMS message cards with one-click locate and unstar actions.
 */
public class StarredMessagesAdapter extends RecyclerView.Adapter<StarredMessagesAdapter.ViewHolder> {

    public static class StarredItem {
        private final SmsMessage message;
        private final String senderTitle;
        private final String address;
        private final long threadId;

        public StarredItem(SmsMessage message, String senderTitle, String address, long threadId) {
            this.message = message;
            this.senderTitle = senderTitle;
            this.address = address;
            this.threadId = threadId;
        }

        public SmsMessage getMessage() {
            return message;
        }

        public String getSenderTitle() {
            return senderTitle;
        }

        public String getAddress() {
            return address;
        }

        public long getThreadId() {
            return threadId;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(@NonNull StarredItem item);
    }

    public interface OnUnstarClickListener {
        void onUnstarClick(@NonNull StarredItem item);
    }

    private final List<StarredItem> items = new ArrayList<>();
    private final OnItemClickListener clickListener;
    private final OnUnstarClickListener unstarListener;

    public StarredMessagesAdapter(
            @NonNull OnItemClickListener clickListener,
            @NonNull OnUnstarClickListener unstarListener
    ) {
        this.clickListener = clickListener;
        this.unstarListener = unstarListener;
    }

    public void submitList(List<StarredItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStarredMessageBinding binding = ItemStarredMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), clickListener, unstarListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStarredMessageBinding binding;

        ViewHolder(@NonNull ItemStarredMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull StarredItem item,
                @NonNull OnItemClickListener clickListener,
                @NonNull OnUnstarClickListener unstarListener
        ) {
            binding.tvSender.setText(item.getSenderTitle());
            binding.tvDate.setText(DateFormatter.formatConversationTime(item.getMessage().getDate()));
            binding.tvBody.setText(item.getMessage().getDisplayBody());
            binding.tvMessageTypeBadge.setText(item.getMessage().isMms() ? "MMS" : "SMS");

            itemView.setOnClickListener(v -> clickListener.onItemClick(item));
            binding.btnUnstar.setOnClickListener(v -> unstarListener.onUnstarClick(item));
        }
    }
}
