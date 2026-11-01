package com.nextgen.messages.data.model;

import android.provider.Telephony;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * Immutable data model representing a single SMS message stored in the Telephony provider.
 * Strictly read-only for Step 3.
 */
public class SmsMessage {

    private final long id;
    private final long threadId;
    @Nullable
    private final String address;
    @Nullable
    private final String body;
    private final long date;
    private final long dateSent;
    private final int type;
    private final boolean isRead;
    private final boolean isSeen;
    private final int status;
    private final int errorCode;
    private final int subscriptionId;

    public SmsMessage(
            long id,
            long threadId,
            @Nullable String address,
            @Nullable String body,
            long date,
            long dateSent,
            int type,
            boolean isRead,
            boolean isSeen,
            int status,
            int errorCode,
            int subscriptionId
    ) {
        this.id = id;
        this.threadId = threadId;
        this.address = address;
        this.body = body;
        this.date = date;
        this.dateSent = dateSent;
        this.type = type;
        this.isRead = isRead;
        this.isSeen = isSeen;
        this.status = status;
        this.errorCode = errorCode;
        this.subscriptionId = subscriptionId;
    }

    public long getId() {
        return id;
    }

    public long getThreadId() {
        return threadId;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    @NonNull
    public String getDisplayAddress() {
        return address != null && !address.trim().isEmpty() ? address : "Unknown";
    }

    @Nullable
    public String getBody() {
        return body;
    }

    @NonNull
    public String getDisplayBody() {
        return body != null ? body : "";
    }

    public long getDate() {
        return date;
    }

    public long getDateSent() {
        return dateSent;
    }

    public int getType() {
        return type;
    }

    public boolean isRead() {
        return isRead;
    }

    public boolean isSeen() {
        return isSeen;
    }

    public int getStatus() {
        return status;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public boolean isIncoming() {
        return type == Telephony.Sms.MESSAGE_TYPE_INBOX;
    }

    public boolean isOutgoing() {
        return type == Telephony.Sms.MESSAGE_TYPE_SENT
                || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
                || type == Telephony.Sms.MESSAGE_TYPE_QUEUED
                || type == Telephony.Sms.MESSAGE_TYPE_FAILED
                || type == Telephony.Sms.MESSAGE_TYPE_DRAFT;
    }

    public boolean isDraft() {
        return type == Telephony.Sms.MESSAGE_TYPE_DRAFT;
    }

    public boolean isOutbox() {
        return type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
                || type == Telephony.Sms.MESSAGE_TYPE_QUEUED;
    }

    public boolean isSent() {
        return type == Telephony.Sms.MESSAGE_TYPE_SENT;
    }

    public boolean isDelivered() {
        return status == Telephony.Sms.STATUS_COMPLETE;
    }

    public boolean isFailed() {
        return type == Telephony.Sms.MESSAGE_TYPE_FAILED
                || status == Telephony.Sms.STATUS_FAILED;
    }

    @NonNull
    public String getDeliveryStatusLabel() {
        if (isFailed()) {
            return "Failed • Tap to retry";
        } else if (isOutbox()) {
            return "Sending…";
        } else if (isDelivered()) {
            return "Delivered";
        } else if (isSent()) {
            return "Sent";
        }
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmsMessage that = (SmsMessage) o;
        return id == that.id
                && threadId == that.threadId
                && date == that.date
                && dateSent == that.dateSent
                && type == that.type
                && isRead == that.isRead
                && isSeen == that.isSeen
                && status == that.status
                && errorCode == that.errorCode
                && subscriptionId == that.subscriptionId
                && Objects.equals(address, that.address)
                && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, threadId, address, body, date, dateSent, type, isRead, isSeen, status, errorCode, subscriptionId);
    }

    @NonNull
    @Override
    public String toString() {
        return "SmsMessage{" +
                "id=" + id +
                ", threadId=" + threadId +
                ", address='" + address + '\'' +
                ", date=" + date +
                ", type=" + type +
                ", isRead=" + isRead +
                '}';
    }
}
