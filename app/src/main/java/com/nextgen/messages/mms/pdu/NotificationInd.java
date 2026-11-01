package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * M-Notification.ind PDU delivered via WAP PUSH DELIVER broadcast when a new MMS is waiting on MMSC.
 */
public class NotificationInd extends GenericPdu {

    public NotificationInd() {
        super();
        try {
            setMessageType(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND);
        } catch (InvalidHeaderValueException ignored) {
        }
    }

    public NotificationInd(@NonNull PduHeaders headers) {
        super(headers);
    }

    @Nullable
    public byte[] getContentLocation() {
        return mPduHeaders.getTextString(PduHeaders.CONTENT_LOCATION);
    }

    public void setContentLocation(@NonNull byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.CONTENT_LOCATION);
    }

    public long getExpiry() {
        return mPduHeaders.getLongInt(PduHeaders.EXPIRY);
    }

    public void setExpiry(long value) {
        mPduHeaders.setLongInt(value, PduHeaders.EXPIRY);
    }

    public long getMessageSize() {
        return mPduHeaders.getLongInt(PduHeaders.MESSAGE_SIZE);
    }

    public void setMessageSize(long value) {
        mPduHeaders.setLongInt(value, PduHeaders.MESSAGE_SIZE);
    }

    @Nullable
    public byte[] getTransactionId() {
        return mPduHeaders.getTextString(PduHeaders.TRANSACTION_ID);
    }

    public void setTransactionId(@NonNull byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.TRANSACTION_ID);
    }

    @Nullable
    public EncodedStringValue getSubject() {
        return mPduHeaders.getEncodedStringValue(PduHeaders.SUBJECT);
    }

    public void setSubject(@Nullable EncodedStringValue value) {
        if (value != null) {
            mPduHeaders.setEncodedStringValue(value, PduHeaders.SUBJECT);
        }
    }
}
