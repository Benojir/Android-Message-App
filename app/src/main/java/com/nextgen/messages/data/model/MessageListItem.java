package com.nextgen.messages.data.model;

import androidx.annotation.NonNull;
import java.util.Objects;

/**
 * Base polymorphic item for displaying messages and date headers in ConversationActivity.
 */
public abstract class MessageListItem {

    public static final int TYPE_DATE_HEADER = 0;
    public static final int TYPE_MESSAGE_INCOMING = 1;
    public static final int TYPE_MESSAGE_OUTGOING = 2;

    public abstract int getItemType();

    public abstract long getItemId();

    /**
     * Date separator header in the message list (e.g., "Today", "Yesterday", "Oct 24, 2024").
     */
    public static class DateHeaderItem extends MessageListItem {
        private final String dateText;
        private final long dayKey;

        public DateHeaderItem(@NonNull String dateText, long dayKey) {
            this.dateText = dateText;
            this.dayKey = dayKey;
        }

        @NonNull
        public String getDateText() {
            return dateText;
        }

        public long getDayKey() {
            return dayKey;
        }

        @Override
        public int getItemType() {
            return TYPE_DATE_HEADER;
        }

        @Override
        public long getItemId() {
            return -Math.abs(dayKey);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DateHeaderItem that = (DateHeaderItem) o;
            return dayKey == that.dayKey && Objects.equals(dateText, that.dateText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dateText, dayKey);
        }
    }

    /**
     * SMS message item in the message list.
     */
    public static class MessageItem extends MessageListItem {
        private final SmsMessage message;

        public MessageItem(@NonNull SmsMessage message) {
            this.message = message;
        }

        @NonNull
        public SmsMessage getMessage() {
            return message;
        }

        @Override
        public int getItemType() {
            return message.isIncoming() ? TYPE_MESSAGE_INCOMING : TYPE_MESSAGE_OUTGOING;
        }

        @Override
        public long getItemId() {
            return message.getId();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageItem that = (MessageItem) o;
            return Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message);
        }
    }
}
