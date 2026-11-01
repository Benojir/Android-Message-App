package com.nextgen.messages.mms.pdu;

/**
 * Thrown when an invalid MMS header value is encountered during PDU construction.
 */
public class InvalidHeaderValueException extends Exception {

    public InvalidHeaderValueException() {
        super();
    }

    public InvalidHeaderValueException(String message) {
        super(message);
    }
}
