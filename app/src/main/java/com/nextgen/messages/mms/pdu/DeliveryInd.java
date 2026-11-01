package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * M-Delivery.ind PDU indicating recipient carrier delivered the MMS.
 */
public class DeliveryInd extends GenericPdu {

    public DeliveryInd() {
        super();
        try {
            setMessageType(PduHeaders.MESSAGE_TYPE_DELIVERY_IND);
        } catch (InvalidHeaderValueException ignored) {
        }
    }

    public DeliveryInd(@NonNull PduHeaders headers) {
        super(headers);
    }

    public long getDate() {
        return mPduHeaders.getLongInt(PduHeaders.DATE);
    }

    @Nullable
    public byte[] getMessageId() {
        return mPduHeaders.getTextString(PduHeaders.MESSAGE_ID);
    }

    public int getStatus() {
        return mPduHeaders.getOctet(PduHeaders.STATUS);
    }

    @Nullable
    public EncodedStringValue[] getTo() {
        return mPduHeaders.getEncodedStringValues(PduHeaders.TO);
    }
}
