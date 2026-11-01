package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Base class for MMS PDUs that carry a multipart body (such as SendReq and RetrieveConf).
 */
public class MultimediaMessagePdu extends GenericPdu {

    private PduBody mMessageBody;

    public MultimediaMessagePdu() {
        super();
    }

    public MultimediaMessagePdu(@NonNull PduHeaders headers, @Nullable PduBody body) {
        super(headers);
        mMessageBody = body;
    }

    @Nullable
    public PduBody getBody() {
        return mMessageBody;
    }

    public void setBody(@Nullable PduBody body) {
        mMessageBody = body;
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

    @Nullable
    public EncodedStringValue[] getTo() {
        return mPduHeaders.getEncodedStringValues(PduHeaders.TO);
    }

    public void addTo(@NonNull EncodedStringValue value) {
        mPduHeaders.appendEncodedStringValue(value, PduHeaders.TO);
    }

    public long getDate() {
        return mPduHeaders.getLongInt(PduHeaders.DATE);
    }

    public void setDate(long value) {
        mPduHeaders.setLongInt(value, PduHeaders.DATE);
    }

    public int getPriority() {
        return mPduHeaders.getOctet(PduHeaders.PRIORITY);
    }

    public void setPriority(int value) throws InvalidHeaderValueException {
        mPduHeaders.setOctet(value, PduHeaders.PRIORITY);
    }
}
