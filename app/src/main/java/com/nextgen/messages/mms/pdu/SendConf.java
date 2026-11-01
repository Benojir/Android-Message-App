package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * M-Send.conf confirmation PDU.
 */
public class SendConf extends GenericPdu {

    public SendConf() {
        super();
        try {
            setMessageType(PduHeaders.MESSAGE_TYPE_SEND_CONF);
        } catch (InvalidHeaderValueException ignored) {
        }
    }

    public SendConf(@NonNull PduHeaders headers) {
        super(headers);
    }

    @Nullable
    public byte[] getMessageId() {
        return mPduHeaders.getTextString(PduHeaders.MESSAGE_ID);
    }

    public int getResponseStatus() {
        return mPduHeaders.getOctet(PduHeaders.RESPONSE_STATUS);
    }

    @Nullable
    public byte[] getTransactionId() {
        return mPduHeaders.getTextString(PduHeaders.TRANSACTION_ID);
    }
}
