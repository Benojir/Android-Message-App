package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * M-Retrieve.conf PDU returned after downloading an incoming MMS from MMSC.
 */
public class RetrieveConf extends MultimediaMessagePdu {

    public RetrieveConf() {
        super();
        try {
            setMessageType(PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);
        } catch (InvalidHeaderValueException ignored) {
        }
    }

    public RetrieveConf(@NonNull PduHeaders headers, @Nullable PduBody body) {
        super(headers, body);
    }

    @Nullable
    public byte[] getContentType() {
        return mPduHeaders.getTextString(PduHeaders.CONTENT_TYPE);
    }

    public void setContentType(@NonNull byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.CONTENT_TYPE);
    }

    @Nullable
    public byte[] getTransactionId() {
        return mPduHeaders.getTextString(PduHeaders.TRANSACTION_ID);
    }

    public void setTransactionId(@NonNull byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.TRANSACTION_ID);
    }

    @Nullable
    public byte[] getMessageId() {
        return mPduHeaders.getTextString(PduHeaders.MESSAGE_ID);
    }

    public void setMessageId(@NonNull byte[] value) {
        mPduHeaders.setTextString(value, PduHeaders.MESSAGE_ID);
    }

    @Nullable
    public EncodedStringValue[] getCc() {
        return mPduHeaders.getEncodedStringValues(PduHeaders.CC);
    }

    public void addCc(@NonNull EncodedStringValue value) {
        mPduHeaders.appendEncodedStringValue(value, PduHeaders.CC);
    }

    public int getRetrieveStatus() {
        return mPduHeaders.getOctet(PduHeaders.RETRIEVE_STATUS);
    }

    public void setRetrieveStatus(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.RETRIEVE_STATUS);
    }
}
