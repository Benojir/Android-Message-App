package com.nextgen.messages.ui.conversation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.nextgen.messages.R;
import com.nextgen.messages.data.model.MessageListItem;
import com.nextgen.messages.data.model.MmsAttachment;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.databinding.ItemMessageDateHeaderBinding;
import com.nextgen.messages.databinding.ItemMessageIncomingBinding;
import com.nextgen.messages.databinding.ItemMessageMmsIncomingBinding;
import com.nextgen.messages.databinding.ItemMessageMmsOutgoingBinding;
import com.nextgen.messages.databinding.ItemMessageOutgoingBinding;
import com.nextgen.messages.utils.AppExecutors;
import com.nextgen.messages.utils.DateFormatter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multi-type ListAdapter rendering date headers, incoming/outgoing SMS bubbles,
 * and incoming/outgoing multimedia MMS bubbles with media previews, audio playback,
 * manual download buttons, and multi-selection support.
 */
public class MessageAdapter extends ListAdapter<MessageListItem, RecyclerView.ViewHolder> {

    public interface OnMessageRetryListener {
        void onRetryMessage(@NonNull SmsMessage message);
    }

    public interface OnMessageClickListener {
        void onMessageClick(@NonNull SmsMessage message, int position);
    }

    public interface OnMessageLongClickListener {
        void onMessageLongClick(@NonNull SmsMessage message, int position);
    }

    public interface OnAttachmentClickListener {
        void onImageClick(@NonNull MmsAttachment attachment);
        void onVideoClick(@NonNull MmsAttachment attachment);
    }

    public interface OnDownloadMmsListener {
        void onDownloadMms(@NonNull SmsMessage message);
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
    @Nullable
    private OnMessageClickListener clickListener;
    @Nullable
    private OnMessageLongClickListener longClickListener;
    @Nullable
    private OnAttachmentClickListener attachmentClickListener;
    @Nullable
    private OnDownloadMmsListener downloadMmsListener;

    private final Set<Long> selectedIds = new HashSet<>();

    // Shared inline audio player state
    @Nullable
    private static MediaPlayer activeMediaPlayer;
    @Nullable
    private static Runnable activeAudioProgressRunnable;
    private static final Handler audioHandler = new Handler(Looper.getMainLooper());
    private static long activeAudioMessageId = -1L;

    public MessageAdapter(@Nullable OnMessageRetryListener retryListener) {
        super(DIFF_CALLBACK);
        this.retryListener = retryListener;
        setHasStableIds(true);
    }

    public MessageAdapter() {
        this(null);
    }

    public void setOnMessageClickListener(@Nullable OnMessageClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnMessageLongClickListener(@Nullable OnMessageLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnAttachmentClickListener(@Nullable OnAttachmentClickListener listener) {
        this.attachmentClickListener = listener;
    }

    public void setOnDownloadMmsListener(@Nullable OnDownloadMmsListener listener) {
        this.downloadMmsListener = listener;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getItemId();
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getItemType();
    }

    public boolean isInSelectionMode() {
        return !selectedIds.isEmpty();
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    @NonNull
    public Set<Long> getSelectedIds() {
        return Collections.unmodifiableSet(selectedIds);
    }

    public boolean isSelected(long compositeId) {
        return selectedIds.contains(compositeId);
    }

    public void toggleSelection(long compositeId) {
        if (selectedIds.contains(compositeId)) {
            selectedIds.remove(compositeId);
        } else {
            selectedIds.add(compositeId);
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        selectedIds.clear();
        for (int i = 0; i < getItemCount(); i++) {
            MessageListItem item = getItem(i);
            if (item instanceof MessageListItem.MessageItem) {
                selectedIds.add(((MessageListItem.MessageItem) item).getMessage().getCompositeId());
            }
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        if (!selectedIds.isEmpty()) {
            selectedIds.clear();
            notifyDataSetChanged();
        }
    }

    @NonNull
    public List<SmsMessage> getSelectedMessages() {
        List<SmsMessage> result = new ArrayList<>();
        for (int i = 0; i < getItemCount(); i++) {
            MessageListItem item = getItem(i);
            if (item instanceof MessageListItem.MessageItem) {
                SmsMessage msg = ((MessageListItem.MessageItem) item).getMessage();
                if (selectedIds.contains(msg.getCompositeId())) {
                    result.add(msg);
                }
            }
        }
        return result;
    }

    public static void stopAudioPlayback() {
        if (activeMediaPlayer != null) {
            try {
                if (activeMediaPlayer.isPlaying()) {
                    activeMediaPlayer.stop();
                }
                activeMediaPlayer.release();
            } catch (Exception ignored) {
            }
            activeMediaPlayer = null;
        }
        if (activeAudioProgressRunnable != null) {
            audioHandler.removeCallbacks(activeAudioProgressRunnable);
            activeAudioProgressRunnable = null;
        }
        activeAudioMessageId = -1L;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case MessageListItem.TYPE_DATE_HEADER:
                return new DateHeaderViewHolder(ItemMessageDateHeaderBinding.inflate(inflater, parent, false));
            case MessageListItem.TYPE_MMS_INCOMING:
                return new IncomingMmsViewHolder(
                        ItemMessageMmsIncomingBinding.inflate(inflater, parent, false),
                        attachmentClickListener,
                        downloadMmsListener
                );
            case MessageListItem.TYPE_MMS_OUTGOING:
                return new OutgoingMmsViewHolder(
                        ItemMessageMmsOutgoingBinding.inflate(inflater, parent, false),
                        attachmentClickListener,
                        retryListener
                );
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
        } else if (holder instanceof IncomingMmsViewHolder && item instanceof MessageListItem.MessageItem) {
            SmsMessage msg = ((MessageListItem.MessageItem) item).getMessage();
            boolean isSelected = selectedIds.contains(msg.getCompositeId());
            ((IncomingMmsViewHolder) holder).bind(msg, isSelected);
            setupItemInteractions(holder.itemView, msg, position);
        } else if (holder instanceof OutgoingMmsViewHolder && item instanceof MessageListItem.MessageItem) {
            SmsMessage msg = ((MessageListItem.MessageItem) item).getMessage();
            boolean isSelected = selectedIds.contains(msg.getCompositeId());
            ((OutgoingMmsViewHolder) holder).bind(msg, isSelected);
            setupItemInteractions(holder.itemView, msg, position);
        } else if (holder instanceof IncomingViewHolder && item instanceof MessageListItem.MessageItem) {
            SmsMessage msg = ((MessageListItem.MessageItem) item).getMessage();
            boolean isSelected = selectedIds.contains(msg.getCompositeId());
            ((IncomingViewHolder) holder).bind(msg, isSelected);
            setupItemInteractions(holder.itemView, msg, position);
        } else if (holder instanceof OutgoingViewHolder && item instanceof MessageListItem.MessageItem) {
            SmsMessage msg = ((MessageListItem.MessageItem) item).getMessage();
            boolean isSelected = selectedIds.contains(msg.getCompositeId());
            ((OutgoingViewHolder) holder).bind(msg, isSelected);
            setupItemInteractions(holder.itemView, msg, position);
        }
    }

    private void setupItemInteractions(@NonNull View itemView, @NonNull SmsMessage message, int position) {
        itemView.setOnClickListener(v -> {
            if (isInSelectionMode()) {
                toggleSelection(message.getCompositeId());
                if (clickListener != null) {
                    clickListener.onMessageClick(message, position);
                }
            } else if (clickListener != null) {
                clickListener.onMessageClick(message, position);
            }
        });

        itemView.setOnLongClickListener(v -> {
            toggleSelection(message.getCompositeId());
            if (longClickListener != null) {
                longClickListener.onMessageLongClick(message, position);
            }
            return true;
        });
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

        void bind(@NonNull SmsMessage message, boolean isSelected) {
            binding.tvMessageBody.setText(message.getDisplayBody());
            binding.tvMessageTime.setText(DateFormatter.formatMessageBubbleTime(message.getDate()));

            if (isSelected) {
                itemView.setBackgroundColor(Color.parseColor("#331976D2"));
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            String contentDescription = (isSelected ? "Selected. " : "") +
                    "Incoming message: " + message.getDisplayBody() +
                    ", at " + DateFormatter.formatMessageBubbleTime(message.getDate());
            itemView.setContentDescription(contentDescription);
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

        void bind(@NonNull SmsMessage message, boolean isSelected) {
            binding.tvMessageBody.setText(message.getDisplayBody());
            binding.tvMessageTime.setText(DateFormatter.formatMessageBubbleTime(message.getDate()));

            if (isSelected) {
                itemView.setBackgroundColor(Color.parseColor("#331976D2"));
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            String statusLabel = message.getDeliveryStatusLabel();
            if (!statusLabel.isEmpty()) {
                binding.tvMessageStatus.setVisibility(View.VISIBLE);
                binding.tvMessageStatus.setText(statusLabel);

                if (message.isFailed()) {
                    binding.tvMessageStatus.setTextColor(Color.parseColor("#B00020"));
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

            String contentDescription = (isSelected ? "Selected. " : "") +
                    "Outgoing message: " + message.getDisplayBody() +
                    (statusLabel.isEmpty() ? "" : ", status: " + statusLabel) +
                    ", at " + DateFormatter.formatMessageBubbleTime(message.getDate());
            itemView.setContentDescription(contentDescription);
        }
    }

    static class IncomingMmsViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageMmsIncomingBinding binding;
        @Nullable
        private final OnAttachmentClickListener attachmentClickListener;
        @Nullable
        private final OnDownloadMmsListener downloadMmsListener;

        IncomingMmsViewHolder(
                @NonNull ItemMessageMmsIncomingBinding binding,
                @Nullable OnAttachmentClickListener attachmentClickListener,
                @Nullable OnDownloadMmsListener downloadMmsListener
        ) {
            super(binding.getRoot());
            this.binding = binding;
            this.attachmentClickListener = attachmentClickListener;
            this.downloadMmsListener = downloadMmsListener;
        }

        void bind(@NonNull SmsMessage message, boolean isSelected) {
            if (isSelected) {
                itemView.setBackgroundColor(Color.parseColor("#331976D2"));
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            // Sender name in Group MMS
            if (!message.getRecipients().isEmpty() && message.getAddress() != null) {
                binding.tvSenderName.setVisibility(View.VISIBLE);
                binding.tvSenderName.setText(message.getAddress());
            } else {
                binding.tvSenderName.setVisibility(View.GONE);
            }

            // Subject
            if (message.getSubject() != null && !message.getSubject().trim().isEmpty()) {
                binding.tvMmsSubject.setVisibility(View.VISIBLE);
                binding.tvMmsSubject.setText(message.getSubject().trim());
            } else {
                binding.tvMmsSubject.setVisibility(View.GONE);
            }

            // Body
            String body = message.getBody();
            if (body != null && !body.trim().isEmpty() && !body.equals(message.getSubject())) {
                binding.tvMessageBody.setVisibility(View.VISIBLE);
                binding.tvMessageBody.setText(body);
            } else {
                binding.tvMessageBody.setVisibility(View.GONE);
            }

            binding.tvMessageTime.setText(DateFormatter.formatMessageBubbleTime(message.getDate()));

            // Download pending state
            if (message.isDownloadPending() || message.isDownloading() || message.isDownloadFailed()) {
                binding.layoutDownloadMms.setVisibility(View.VISIBLE);
                binding.layoutMediaContainer.setVisibility(View.GONE);
                binding.layoutAudioPlayer.setVisibility(View.GONE);

                if (message.isDownloading()) {
                    binding.btnDownloadMms.setEnabled(false);
                    binding.progressDownloading.setVisibility(View.VISIBLE);
                } else {
                    binding.btnDownloadMms.setEnabled(true);
                    binding.progressDownloading.setVisibility(View.GONE);
                    String btnText = message.isDownloadFailed()
                            ? itemView.getContext().getString(R.string.retry_download_mms)
                            : itemView.getContext().getString(R.string.action_download_mms);
                    binding.btnDownloadMms.setText(btnText);
                    binding.btnDownloadMms.setOnClickListener(v -> {
                        if (downloadMmsListener != null) {
                            downloadMmsListener.onDownloadMms(message);
                        }
                    });
                }
            } else {
                binding.layoutDownloadMms.setVisibility(View.GONE);
                bindAttachments(message);
            }
        }

        private void bindAttachments(@NonNull SmsMessage message) {
            List<MmsAttachment> media = message.getMediaAttachments();
            if (media.isEmpty()) {
                binding.layoutMediaContainer.setVisibility(View.GONE);
                binding.layoutAudioPlayer.setVisibility(View.GONE);
                return;
            }

            MmsAttachment first = media.get(0);
            if (first.isImage()) {
                binding.layoutMediaContainer.setVisibility(View.VISIBLE);
                binding.ivPlayVideo.setVisibility(View.GONE);
                binding.layoutAudioPlayer.setVisibility(View.GONE);
                loadThumbnail(binding.ivAttachmentImage, first.getUri());

                binding.layoutMediaContainer.setOnClickListener(v -> {
                    if (attachmentClickListener != null) {
                        attachmentClickListener.onImageClick(first);
                    }
                });
            } else if (first.isVideo()) {
                binding.layoutMediaContainer.setVisibility(View.VISIBLE);
                binding.ivPlayVideo.setVisibility(View.VISIBLE);
                binding.layoutAudioPlayer.setVisibility(View.GONE);
                loadThumbnail(binding.ivAttachmentImage, first.getUri());

                binding.layoutMediaContainer.setOnClickListener(v -> {
                    if (attachmentClickListener != null) {
                        attachmentClickListener.onVideoClick(first);
                    }
                });
            } else if (first.isAudio()) {
                binding.layoutMediaContainer.setVisibility(View.GONE);
                binding.layoutAudioPlayer.setVisibility(View.VISIBLE);
                bindAudioPlayer(message.getId(), first, binding.btnPlayAudio, binding.progressAudio, binding.tvAudioDuration);
            }
        }
    }

    static class OutgoingMmsViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageMmsOutgoingBinding binding;
        @Nullable
        private final OnAttachmentClickListener attachmentClickListener;
        @Nullable
        private final OnMessageRetryListener retryListener;

        OutgoingMmsViewHolder(
                @NonNull ItemMessageMmsOutgoingBinding binding,
                @Nullable OnAttachmentClickListener attachmentClickListener,
                @Nullable OnMessageRetryListener retryListener
        ) {
            super(binding.getRoot());
            this.binding = binding;
            this.attachmentClickListener = attachmentClickListener;
            this.retryListener = retryListener;
        }

        void bind(@NonNull SmsMessage message, boolean isSelected) {
            if (isSelected) {
                itemView.setBackgroundColor(Color.parseColor("#331976D2"));
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            // Subject
            if (message.getSubject() != null && !message.getSubject().trim().isEmpty()) {
                binding.tvMmsSubject.setVisibility(View.VISIBLE);
                binding.tvMmsSubject.setText(message.getSubject().trim());
            } else {
                binding.tvMmsSubject.setVisibility(View.GONE);
            }

            // Body
            String body = message.getBody();
            if (body != null && !body.trim().isEmpty() && !body.equals(message.getSubject())) {
                binding.tvMessageBody.setVisibility(View.VISIBLE);
                binding.tvMessageBody.setText(body);
            } else {
                binding.tvMessageBody.setVisibility(View.GONE);
            }

            binding.tvMessageTime.setText(DateFormatter.formatMessageBubbleTime(message.getDate()));

            String statusLabel = message.getDeliveryStatusLabel();
            if (!statusLabel.isEmpty()) {
                binding.tvMessageStatus.setVisibility(View.VISIBLE);
                binding.tvMessageStatus.setText(statusLabel);

                if (message.isFailed()) {
                    binding.tvMessageStatus.setTextColor(Color.parseColor("#B00020"));
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

            bindAttachments(message);
        }

        private void bindAttachments(@NonNull SmsMessage message) {
            List<MmsAttachment> media = message.getMediaAttachments();
            if (media.isEmpty()) {
                binding.layoutMediaContainer.setVisibility(View.GONE);
                binding.layoutAudioPlayer.setVisibility(View.GONE);
                return;
            }

            MmsAttachment first = media.get(0);
            if (first.isImage()) {
                binding.layoutMediaContainer.setVisibility(View.VISIBLE);
                binding.ivPlayVideo.setVisibility(View.GONE);
                binding.layoutAudioPlayer.setVisibility(View.GONE);
                loadThumbnail(binding.ivAttachmentImage, first.getUri());

                binding.layoutMediaContainer.setOnClickListener(v -> {
                    if (attachmentClickListener != null) {
                        attachmentClickListener.onImageClick(first);
                    }
                });
            } else if (first.isVideo()) {
                binding.layoutMediaContainer.setVisibility(View.VISIBLE);
                binding.ivPlayVideo.setVisibility(View.VISIBLE);
                binding.layoutAudioPlayer.setVisibility(View.GONE);
                loadThumbnail(binding.ivAttachmentImage, first.getUri());

                binding.layoutMediaContainer.setOnClickListener(v -> {
                    if (attachmentClickListener != null) {
                        attachmentClickListener.onVideoClick(first);
                    }
                });
            } else if (first.isAudio()) {
                binding.layoutMediaContainer.setVisibility(View.GONE);
                binding.layoutAudioPlayer.setVisibility(View.VISIBLE);
                bindAudioPlayer(message.getId(), first, binding.btnPlayAudio, binding.progressAudio, binding.tvAudioDuration);
            }
        }
    }

    private static void loadThumbnail(@NonNull ImageView imageView, @NonNull Uri uri) {
        Context context = imageView.getContext();
        imageView.setTag(uri);
        AppExecutors.getInstance().diskIO().execute(() -> {
            Bitmap thumbnail = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        BitmapFactory.decodeStream(is, null, options);
                    }
                }

                int reqWidth = 440;
                int reqHeight = 360;
                int inSampleSize = 1;
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    final int halfHeight = options.outHeight / 2;
                    final int halfWidth = options.outWidth / 2;
                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2;
                    }
                }

                options.inSampleSize = inSampleSize;
                options.inJustDecodeBounds = false;

                try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        thumbnail = BitmapFactory.decodeStream(is, null, options);
                    }
                }
            } catch (Exception ignored) {
            }

            final Bitmap resBitmap = thumbnail;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (uri.equals(imageView.getTag())) {
                    if (resBitmap != null) {
                        imageView.setImageBitmap(resBitmap);
                    } else {
                        imageView.setImageResource(R.drawable.ic_image);
                    }
                }
            });
        });
    }

    private static void bindAudioPlayer(
            long messageId,
            @NonNull MmsAttachment attachment,
            @NonNull com.google.android.material.button.MaterialButton btnPlay,
            @NonNull android.widget.ProgressBar progressBar,
            @NonNull android.widget.TextView tvDuration
    ) {
        Context context = btnPlay.getContext();
        boolean isPlayingThis = (activeMediaPlayer != null && activeAudioMessageId == messageId && activeMediaPlayer.isPlaying());

        btnPlay.setIconResource(isPlayingThis ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

        btnPlay.setOnClickListener(v -> {
            if (activeAudioMessageId == messageId && activeMediaPlayer != null && activeMediaPlayer.isPlaying()) {
                stopAudioPlayback();
                btnPlay.setIconResource(R.drawable.ic_play_arrow);
            } else {
                stopAudioPlayback();
                try {
                    MediaPlayer mp = new MediaPlayer();
                    mp.setAudioAttributes(new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());
                    mp.setDataSource(context, attachment.getUri());
                    mp.prepare();
                    mp.start();

                    activeMediaPlayer = mp;
                    activeAudioMessageId = messageId;
                    btnPlay.setIconResource(R.drawable.ic_pause);

                    activeAudioProgressRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (activeMediaPlayer != null && activeAudioMessageId == messageId && activeMediaPlayer.isPlaying()) {
                                int current = activeMediaPlayer.getCurrentPosition();
                                int duration = activeMediaPlayer.getDuration();
                                if (duration > 0) {
                                    progressBar.setProgress((current * 100) / duration);
                                    tvDuration.setText(formatDuration(current) + " / " + formatDuration(duration));
                                }
                                audioHandler.postDelayed(this, 200L);
                            }
                        }
                    };
                    audioHandler.post(activeAudioProgressRunnable);

                    mp.setOnCompletionListener(mediaPlayer -> {
                        stopAudioPlayback();
                        btnPlay.setIconResource(R.drawable.ic_play_arrow);
                        progressBar.setProgress(0);
                    });

                } catch (Exception e) {
                    stopAudioPlayback();
                    btnPlay.setIconResource(R.drawable.ic_play_arrow);
                }
            }
        });
    }

    private static String formatDuration(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60));
        return String.format("%d:%02d", minutes, seconds);
    }
}
