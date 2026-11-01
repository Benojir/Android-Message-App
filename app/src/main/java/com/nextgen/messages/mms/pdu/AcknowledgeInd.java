package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * M-Acknowledge.ind PDU.
 */
public class AcknowledgeInd extends GenericPdu {

    public AcknowledgeInd(@NonNull byte[] transactionId) throws InvalidHeaderValueException {
        super();
        setMessageType(PduHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND);
        setMmsVersion(PduHeaders.CURRENT_MMS_VERSION);
        mPduHeaders.setTextString(transactionId, PduHeaders.TRANSACTION_ID);
    }

    public AcknowledgeInd(@NonNull PduHeaders headers) {
        super(headers);
    }

    @Nullable
    public byte[] getTransactionId() {
        return mPduHeaders.getTextString(PduHeaders.TRANSACTION_ID);
    }
}
