package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Abstract base class for all MMS PDU representations.
 */
public class GenericPdu {

    protected PduHeaders mPduHeaders;

    public GenericPdu() {
        mPduHeaders = new PduHeaders();
    }

    public GenericPdu(@NonNull PduHeaders headers) {
        mPduHeaders = headers;
    }

    @NonNull
    public PduHeaders getPduHeaders() {
        return mPduHeaders;
    }

    public int getMessageType() {
        return mPduHeaders.getOctet(PduHeaders.MESSAGE_TYPE);
    }

    public void setMessageType(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.MESSAGE_TYPE);
    }

    public int getMmsVersion() {
        return mPduHeaders.getOctet(PduHeaders.MMS_VERSION);
    }

    public void setMmsVersion(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.MMS_VERSION);
    }

    @Nullable
    public EncodedStringValue getFrom() {
        return mPduHeaders.getEncodedStringValue(PduHeaders.FROM);
    }

    public void setFrom(@NonNull EncodedStringValue value) {
        mPduHeaders.setEncodedStringValue(value, PduHeaders.FROM);
    }
}
