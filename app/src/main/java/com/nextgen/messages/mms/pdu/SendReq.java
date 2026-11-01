package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * M-Send.req PDU for transmitting outgoing multimedia messages.
 */
public class SendReq extends MultimediaMessagePdu {

    public SendReq() {
        super();
        try {
            setMessageType(PduHeaders.MESSAGE_TYPE_SEND_REQ);
            setMmsVersion(PduHeaders.CURRENT_MMS_VERSION);
            setContentType("application/vnd.wap.multipart.related".getBytes());
            setTransactionId(("T" + Long.toHexString(System.currentTimeMillis())).getBytes());
        } catch (InvalidHeaderValueException ignored) {
        }
    }

    public SendReq(@NonNull PduHeaders headers, @Nullable PduBody body) {
        super(headers, body);
    }

    @Nullable
    public byte[] getTransactionId() {
        return mPduHeaders.getTextString(PduHeaders.TRANSACTION_ID);
    }

    public void setTransactionId(@NonNull byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.TRANSACTION_ID);
    }

    @Nullable
    public byte[] getContentType() {
        return mPduHeaders.getTextString(PduHeaders.CONTENT_TYPE);
    }

    public void setContentType(@NonNull byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.CONTENT_TYPE);
    }

    public int getDeliveryReport() {
        return mPduHeaders.getOctet(PduHeaders.DELIVERY_REPORT);
    }

    public void setDeliveryReport(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.DELIVERY_REPORT);
    }

    public int getReadReport() {
        return mPduHeaders.getOctet(PduHeaders.READ_REPORT);
    }

    public void setReadReport(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.READ_REPORT);
    }

    public long getMessageSize() {
        return mPduHeaders.getLongInt(PduHeaders.MESSAGE_SIZE);
    }

    public void setMessageSize(long value) {
        mPduHeaders.setLongInt(value, PduHeaders.MESSAGE_SIZE);
    }

    public long getExpiry() {
        return mPduHeaders.getLongInt(PduHeaders.EXPIRY);
    }

    public void setExpiry(long value) {
        mPduHeaders.setLongInt(value, PduHeaders.EXPIRY);
    }
}
