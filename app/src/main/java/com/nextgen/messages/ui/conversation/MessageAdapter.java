package com.nextgen.messages.ui.conversation;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.data.model.MessageListItem;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.databinding.ItemMessageDateHeaderBinding;
import com.nextgen.messages.databinding.ItemMessageIncomingBinding;
import com.nextgen.messages.databinding.ItemMessageOutgoingBinding;
import com.nextgen.messages.utils.DateFormatter;

/**
 * Multi-type ListAdapter rendering date headers, incoming message bubbles,
 * and outgoing message bubbles with delivery tracking and retry actions.
 */
public class MessageAdapter extends ListAdapter<MessageListItem, RecyclerView.ViewHolder> {

    public interface OnMessageRetryListener {
        void onRetryMessage(@NonNull SmsMessage message);
    }

    private static final DiffUtil.ItemCallback<MessageListItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<MessageListItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull MessageListItem oldItem, @NonNull MessageListItem newItem) {
            return oldItem.getItemType() == newItem.getItemType()
                    && oldItem.getItemId() == newItem.getItemId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull MessageListItem oldItem, @NonNull MessageListItem newItem) {
            return oldItem.equals(newItem);
        }
    };

    @Nullable
    private final OnMessageRetryListener retryListener;

    public MessageAdapter(@Nullable OnMessageRetryListener retryListener) {
        super(DIFF_CALLBACK);
        this.retryListener = retryListener;
    }

    public MessageAdapter() {
        this(null);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getItemType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case MessageListItem.TYPE_DATE_HEADER:
                return new DateHeaderViewHolder(ItemMessageDateHeaderBinding.inflate(inflater, parent, false));
            case MessageListItem.TYPE_MESSAGE_OUTGOING:
                return new OutgoingViewHolder(ItemMessageOutgoingBinding.inflate(inflater, parent, false), retryListener);
            case MessageListItem.TYPE_MESSAGE_INCOMING:
            default:
                return new IncomingViewHolder(ItemMessageIncomingBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageListItem item = getItem(position);
        if (holder instanceof DateHeaderViewHolder && item instanceof MessageListItem.DateHeaderItem) {
            ((DateHeaderViewHolder) holder).bind((MessageListItem.DateHeaderItem) item);
        } else if (holder instanceof IncomingViewHolder && item instanceof MessageListItem.MessageItem) {
            ((IncomingViewHolder) holder).bind(((MessageListItem.MessageItem) item).getMessage());
        } else if (holder instanceof OutgoingViewHolder && item instanceof MessageListItem.MessageItem) {
            ((OutgoingViewHolder) holder).bind(((MessageListItem.MessageItem) item).getMessage());
        }
    }

    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageDateHeaderBinding binding;

        DateHeaderViewHolder(@NonNull ItemMessageDateHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull MessageListItem.DateHeaderItem item) {
            binding.tvDateHeader.setText(item.getDateText());
        }
    }

    static class IncomingViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageIncomingBinding binding;

        IncomingViewHolder(@NonNull ItemMessageIncomingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull SmsMessage message) {
            binding.tvMessageBody.setText(message.getDisplayBody());
            binding.tvMessageTime.setText(DateFormatter.formatMessageBubbleTime(message.getDate()));
        }
    }

    static class OutgoingViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageOutgoingBinding binding;
        @Nullable
        private final OnMessageRetryListener retryListener;

        OutgoingViewHolder(@NonNull ItemMessageOutgoingBinding binding, @Nullable OnMessageRetryListener retryListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.retryListener = retryListener;
        }

        void bind(@NonNull SmsMessage message) {
            binding.tvMessageBody.setText(message.getDisplayBody());
            binding.tvMessageTime.setText(DateFormatter.formatMessageBubbleTime(message.getDate()));

            String statusLabel = message.getDeliveryStatusLabel();
            if (!statusLabel.isEmpty()) {
                binding.tvMessageStatus.setVisibility(View.VISIBLE);
                binding.tvMessageStatus.setText(statusLabel);

                if (message.isFailed()) {
                    binding.tvMessageStatus.setTextColor(Color.parseColor("#B00020")); // Error red
                    binding.bubbleContainer.setOnClickListener(v -> {
                        if (retryListener != null) {
                            retryListener.onRetryMessage(message);
                        }
                    });
                } else {
                    binding.tvMessageStatus.setTextColor(binding.tvMessageTime.getTextColors());
                    binding.bubbleContainer.setOnClickListener(null);
                }
            } else {
                binding.tvMessageStatus.setVisibility(View.GONE);
                binding.bubbleContainer.setOnClickListener(null);
            }
        }
    }
}
