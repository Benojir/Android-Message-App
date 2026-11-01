package com.nextgen.messages.data.model;

import android.provider.Telephony;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Unified data model representing an SMS or MMS message stored in the Telephony provider.
 */
public class SmsMessage {

    public static final int MMS_DOWNLOAD_STATUS_NONE = 0;
    public static final int MMS_DOWNLOAD_STATUS_PENDING = 1;
    public static final int MMS_DOWNLOAD_STATUS_DOWNLOADING = 2;
    public static final int MMS_DOWNLOAD_STATUS_DOWNLOADED = 3;
    public static final int MMS_DOWNLOAD_STATUS_FAILED = 4;

    public static final int DOWNLOAD_STATE_NONE = MMS_DOWNLOAD_STATUS_NONE;
    public static final int DOWNLOAD_STATE_PENDING = MMS_DOWNLOAD_STATUS_PENDING;
    public static final int DOWNLOAD_STATE_DOWNLOADING = MMS_DOWNLOAD_STATUS_DOWNLOADING;
    public static final int DOWNLOAD_STATE_DOWNLOADED = MMS_DOWNLOAD_STATUS_DOWNLOADED;
    public static final int DOWNLOAD_STATE_FAILED = MMS_DOWNLOAD_STATUS_FAILED;

    public static final int TYPE_INCOMING = Telephony.Sms.MESSAGE_TYPE_INBOX;
    public static final int TYPE_SENT = Telephony.Sms.MESSAGE_TYPE_SENT;
    public static final int TYPE_OUTBOX = Telephony.Sms.MESSAGE_TYPE_OUTBOX;
    public static final int TYPE_FAILED = Telephony.Sms.MESSAGE_TYPE_FAILED;
    public static final int TYPE_DRAFT = Telephony.Sms.MESSAGE_TYPE_DRAFT;

    public static final int STATUS_NONE = -1;
    public static final int STATUS_COMPLETE = Telephony.Sms.STATUS_COMPLETE;
    public static final int STATUS_FAILED = Telephony.Sms.STATUS_FAILED;

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

    // MMS extensions
    private final boolean isMms;
    @Nullable
    private final String subject;
    @NonNull
    private final List<MmsAttachment> attachments;
    private final int mmsDownloadStatus;
    @Nullable
    private final String mmsContentLocation;
    private final long mmsMessageSize;
    @NonNull
    private final List<String> recipients;

    /**
     * Standard constructor for SMS messages.
     */
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
        this(id, threadId, address, body, date, dateSent, type, isRead, isSeen, status, errorCode, subscriptionId,
                false, null, Collections.emptyList(), MMS_DOWNLOAD_STATUS_NONE, null, 0L, Collections.emptyList());
    }

    /**
     * Full constructor supporting both SMS and MMS records.
     */
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
            int subscriptionId,
            boolean isMms,
            @Nullable String subject,
            @Nullable List<MmsAttachment> attachments,
            int mmsDownloadStatus,
            @Nullable String mmsContentLocation,
            long mmsMessageSize,
            @Nullable List<String> recipients
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
        this.isMms = isMms;
        this.subject = subject;
        this.attachments = attachments != null ? new ArrayList<>(attachments) : Collections.emptyList();
        this.mmsDownloadStatus = mmsDownloadStatus;
        this.mmsContentLocation = mmsContentLocation;
        this.mmsMessageSize = mmsMessageSize;
        this.recipients = recipients != null ? new ArrayList<>(recipients) : Collections.emptyList();
    }

    public long getId() {
        return id;
    }

    /**
     * Returns a stable composite identifier that never collides between SMS and MMS IDs.
     */
    public long getCompositeId() {
        return isMms ? -(id * 1000L + 7L) : id;
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

    public boolean isMms() {
        return isMms;
    }

    @Nullable
    public String getSubject() {
        return subject;
    }

    @NonNull
    public List<MmsAttachment> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    @NonNull
    public List<MmsAttachment> getMediaAttachments() {
        List<MmsAttachment> media = new ArrayList<>();
        for (MmsAttachment att : attachments) {
            if (att.isMedia()) {
                media.add(att);
            }
        }
        return media;
    }

    public int getMmsDownloadStatus() {
        return mmsDownloadStatus;
    }

    public int getMmsDownloadState() {
        return mmsDownloadStatus;
    }

    public boolean isDownloadPending() {
        return isMms && (mmsDownloadStatus == MMS_DOWNLOAD_STATUS_PENDING || mmsDownloadStatus == MMS_DOWNLOAD_STATUS_FAILED);
    }

    public boolean isDownloading() {
        return isMms && mmsDownloadStatus == MMS_DOWNLOAD_STATUS_DOWNLOADING;
    }

    public boolean isDownloadFailed() {
        return isMms && mmsDownloadStatus == MMS_DOWNLOAD_STATUS_FAILED;
    }

    public boolean isNotificationInd() {
        return isDownloadPending() || isDownloading() || isDownloadFailed();
    }

    public boolean isGroupMms() {
        return isMms && recipients.size() > 1;
    }

    @Nullable
    public String getMmsContentLocation() {
        return mmsContentLocation;
    }

    @Nullable
    public String getContentLocation() {
        return mmsContentLocation;
    }

    public long getMmsMessageSize() {
        return mmsMessageSize;
    }

    public long getMessageSize() {
        return mmsMessageSize;
    }

    public static SmsMessage createMms(
            long id,
            long threadId,
            @Nullable String address,
            @Nullable String body,
            long date,
            int type,
            @Nullable String subject,
            @Nullable List<MmsAttachment> attachments
    ) {
        return new SmsMessage(
                id, threadId, address, body, date, 0L, type, true, true, 0, 0, 1,
                true, subject, attachments, MMS_DOWNLOAD_STATUS_NONE, null, 0L, null
        );
    }

    @NonNull
    public List<String> getRecipients() {
        return Collections.unmodifiableList(recipients);
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
        if (isMms) {
            if (isDownloadFailed()) {
                return "Download failed • Tap to retry";
            } else if (isDownloading()) {
                return "Downloading…";
            } else if (isDownloadPending()) {
                return "Tap to download";
            }
        }
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
                && isMms == that.isMms
                && mmsDownloadStatus == that.mmsDownloadStatus
                && Objects.equals(address, that.address)
                && Objects.equals(body, that.body)
                && Objects.equals(subject, that.subject)
                && Objects.equals(attachments, that.attachments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, threadId, address, body, date, dateSent, type, isRead, isSeen, status, errorCode, subscriptionId, isMms, subject, attachments, mmsDownloadStatus);
    }

    @NonNull
    @Override
    public String toString() {
        return "SmsMessage{" +
                "id=" + id +
                ", threadId=" + threadId +
                ", isMms=" + isMms +
                ", address='" + address + '\'' +
                ", date=" + date +
                ", type=" + type +
                ", isRead=" + isRead +
                '}';
    }
}
