package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Standard WAP MMS PDU Header fields, constants, and typed storage.
 */
public class PduHeaders {

    public static final int BCC = 0x81;
    public static final int CC = 0x82;
    public static final int CONTENT_LOCATION = 0x83;
    public static final int CONTENT_TYPE = 0x84;
    public static final int DATE = 0x85;
    public static final int DELIVERY_REPORT = 0x86;
    public static final int DELIVERY_TIME = 0x87;
    public static final int EXPIRY = 0x88;
    public static final int FROM = 0x89;
    public static final int MESSAGE_CLASS = 0x8A;
    public static final int MESSAGE_ID = 0x8B;
    public static final int MESSAGE_TYPE = 0x8C;
    public static final int MMS_VERSION = 0x8D;
    public static final int MESSAGE_SIZE = 0x8E;
    public static final int PRIORITY = 0x8F;
    public static final int READ_REPORT = 0x90;
    public static final int REPORT_ALLOWED = 0x91;
    public static final int RESPONSE_STATUS = 0x92;
    public static final int RESPONSE_TEXT = 0x93;
    public static final int SENDER_VISIBILITY = 0x94;
    public static final int STATUS = 0x95;
    public static final int SUBJECT = 0x96;
    public static final int TO = 0x97;
    public static final int TRANSACTION_ID = 0x98;
    public static final int RETRIEVE_STATUS = 0x99;
    public static final int RETRIEVE_TEXT = 0x9A;
    public static final int READ_STATUS = 0x9B;
    public static final int REPLY_CHARGING = 0x9C;
    public static final int REPLY_CHARGING_DEADLINE = 0x9D;
    public static final int REPLY_CHARGING_ID = 0x9E;
    public static final int REPLY_CHARGING_SIZE = 0x9F;
    public static final int PREVIOUSLY_SENT_BY = 0xA0;
    public static final int PREVIOUSLY_SENT_DATE = 0xA1;

    // Message Types
    public static final int MESSAGE_TYPE_SEND_REQ = 0x80;
    public static final int MESSAGE_TYPE_SEND_CONF = 0x81;
    public static final int MESSAGE_TYPE_NOTIFICATION_IND = 0x82;
    public static final int MESSAGE_TYPE_NOTIFYRESP_IND = 0x83;
    public static final int MESSAGE_TYPE_RETRIEVE_CONF = 0x84;
    public static final int MESSAGE_TYPE_ACKNOWLEDGE_IND = 0x85;
    public static final int MESSAGE_TYPE_DELIVERY_IND = 0x86;
    public static final int MESSAGE_TYPE_READ_REC_IND = 0x87;
    public static final int MESSAGE_TYPE_READ_ORIG_IND = 0x88;

    // MMS Version
    public static final int MMS_VERSION_1_0 = 0x90;
    public static final int MMS_VERSION_1_1 = 0x91;
    public static final int MMS_VERSION_1_2 = 0x92;
    public static final int MMS_VERSION_1_3 = 0x93;
    public static final int CURRENT_MMS_VERSION = MMS_VERSION_1_2;

    // From constants
    public static final int FROM_ADDRESS_PRESENT_TOKEN = 0x80;
    public static final int FROM_INSERT_ADDRESS_TOKEN = 0x81;

    // Delivery report values
    public static final int VALUE_YES = 0x80;
    public static final int VALUE_NO = 0x81;

    // Priority values
    public static final int PRIORITY_LOW = 0x80;
    public static final int PRIORITY_NORMAL = 0x81;
    public static final int PRIORITY_HIGH = 0x82;

    // Status / Response values
    public static final int RESPONSE_STATUS_OK = 0x80;
    public static final int RESPONSE_STATUS_ERROR_UNSPECIFIED = 0x81;
    public static final int RESPONSE_STATUS_ERROR_SERVICE_DENIED = 0x82;
    public static final int RESPONSE_STATUS_ERROR_MESSAGE_FORMAT_CORRUPT = 0x83;
    public static final int RESPONSE_STATUS_ERROR_SENDING_ADDRESS_UNRESOLVED = 0x84;
    public static final int RESPONSE_STATUS_ERROR_MESSAGE_NOT_FOUND = 0x85;
    public static final int RESPONSE_STATUS_ERROR_NETWORK_PROBLEM = 0x86;
    public static final int RESPONSE_STATUS_ERROR_CONTENT_NOT_ACCEPTED = 0x87;
    public static final int RESPONSE_STATUS_ERROR_UNSUPPORTED_MESSAGE = 0x88;

    private final HashMap<Integer, Object> headerMap = new HashMap<>();

    public PduHeaders() {}

    public int getOctet(int field) {
        Integer octet = (Integer) headerMap.get(field);
        if (octet == null) {
            return 0;
        }
        return octet;
    }

    public void setOctet(int value, int field) throws InvalidHeaderValueException {
        switch (field) {
            case REPORT_ALLOWED:
            case DELIVERY_REPORT:
            case READ_STATUS:
            case READ_REPORT:
            case SENDER_VISIBILITY:
                if (value != VALUE_YES && value != VALUE_NO) {
                    throw new InvalidHeaderValueException("Invalid Octet value: " + value);
                }
                break;
            case PRIORITY:
                if (value < PRIORITY_LOW || value > PRIORITY_HIGH) {
                    throw new InvalidHeaderValueException("Invalid Priority value: " + value);
                }
                break;
            case STATUS:
                break;
            case MESSAGE_TYPE:
                if (value < MESSAGE_TYPE_SEND_REQ || value > MESSAGE_TYPE_READ_ORIG_IND) {
                    throw new InvalidHeaderValueException("Invalid Message Type: " + value);
                }
                break;
            case MMS_VERSION:
                if (value < MMS_VERSION_1_0 || value > MMS_VERSION_1_3) {
                    value = CURRENT_MMS_VERSION;
                }
                break;
            default:
                break;
        }
        headerMap.put(field, value);
    }

    @Nullable
    public byte[] getTextString(int field) {
        return (byte[]) headerMap.get(field);
    }

    public void setTextString(@NonNull byte[] value, int field) {
        if (value == null) {
            throw new NullPointerException();
        }
        headerMap.put(field, value);
    }

    @Nullable
    public EncodedStringValue getEncodedStringValue(int field) {
        return (EncodedStringValue) headerMap.get(field);
    }

    @Nullable
    public EncodedStringValue[] getEncodedStringValues(int field) {
        @SuppressWarnings("unchecked")
        ArrayList<EncodedStringValue> list = (ArrayList<EncodedStringValue>) headerMap.get(field);
        if (list == null) {
            return null;
        }
        return list.toArray(new EncodedStringValue[0]);
    }

    public void setEncodedStringValue(@NonNull EncodedStringValue value, int field) {
        if (value == null) {
            throw new NullPointerException();
        }
        headerMap.put(field, value);
    }

    public void setEncodedStringValues(@NonNull EncodedStringValue[] value, int field) {
        if (value == null) {
            throw new NullPointerException();
        }
        ArrayList<EncodedStringValue> list = new ArrayList<>();
        for (EncodedStringValue v : value) {
            list.add(v);
        }
        headerMap.put(field, list);
    }

    public void appendEncodedStringValue(@NonNull EncodedStringValue value, int field) {
        if (value == null) {
            throw new NullPointerException();
        }
        @SuppressWarnings("unchecked")
        ArrayList<EncodedStringValue> list = (ArrayList<EncodedStringValue>) headerMap.get(field);
        if (list == null) {
            list = new ArrayList<>();
            headerMap.put(field, list);
        }
        list.add(value);
    }

    public long getLongInt(int field) {
        Long val = (Long) headerMap.get(field);
        if (val == null) {
            return -1L;
        }
        return val;
    }

    public void setLongInt(long value, int field) {
        headerMap.put(field, value);
    }
}
