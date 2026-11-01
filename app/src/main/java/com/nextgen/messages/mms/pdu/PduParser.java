package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Parses binary WAP-209 MMS PDU bytes into structured PDU models (e.g. NotificationInd, RetrieveConf).
 */
public class PduParser {

    private final ByteArrayInputStream mPduDataStream;
    private PduHeaders mHeaders;
    private PduBody mBody;

    public PduParser(@NonNull byte[] pduData) {
        mPduDataStream = new ByteArrayInputStream(pduData);
    }

    @Nullable
    public GenericPdu parse() {
        if (mPduDataStream == null) {
            return null;
        }

        mHeaders = parseHeaders();
        if (mHeaders == null) {
            return null;
        }

        int messageType = mHeaders.getOctet(PduHeaders.MESSAGE_TYPE);
        switch (messageType) {
            case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                mBody = parseParts();
                return new SendReq(mHeaders, mBody);
            case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                return new NotificationInd(mHeaders);
            case PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF:
                mBody = parseParts();
                return new RetrieveConf(mHeaders, mBody);
            case PduHeaders.MESSAGE_TYPE_DELIVERY_IND:
                return new DeliveryInd(mHeaders);
            case PduHeaders.MESSAGE_TYPE_SEND_CONF:
                return new SendConf(mHeaders);
            case PduHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND:
                return new AcknowledgeInd(mHeaders);
            default:
                return new GenericPdu(mHeaders);
        }
    }

    @Nullable
    private PduHeaders parseHeaders() {
        PduHeaders headers = new PduHeaders();
        boolean keepParsing = true;

        while (keepParsing && mPduDataStream.available() > 0) {
            int headerField = mPduDataStream.read();
            if (headerField == -1) break;

            try {
                switch (headerField) {
                    case PduHeaders.MESSAGE_TYPE:
                        int msgType = mPduDataStream.read();
                        headers.setOctet(msgType, PduHeaders.MESSAGE_TYPE);
                        break;
                    case PduHeaders.TRANSACTION_ID:
                        byte[] trId = parseTextString();
                        if (trId != null) headers.setTextString(trId, PduHeaders.TRANSACTION_ID);
                        break;
                    case PduHeaders.MMS_VERSION:
                        int version = parseShortInteger();
                        headers.setOctet(version, PduHeaders.MMS_VERSION);
                        break;
                    case PduHeaders.DATE:
                        long date = parseLongInteger();
                        headers.setLongInt(date, PduHeaders.DATE);
                        break;
                    case PduHeaders.FROM:
                        parseValueLength(); // skip length
                        int fromToken = mPduDataStream.read();
                        if (fromToken == PduHeaders.FROM_ADDRESS_PRESENT_TOKEN) {
                            EncodedStringValue fromStr = parseEncodedStringValue();
                            if (fromStr != null) headers.setEncodedStringValue(fromStr, PduHeaders.FROM);
                        } else {
                            headers.setEncodedStringValue(new EncodedStringValue(""), PduHeaders.FROM);
                        }
                        break;
                    case PduHeaders.TO:
                        EncodedStringValue toStr = parseEncodedStringValue();
                        if (toStr != null) headers.appendEncodedStringValue(toStr, PduHeaders.TO);
                        break;
                    case PduHeaders.CC:
                        EncodedStringValue ccStr = parseEncodedStringValue();
                        if (ccStr != null) headers.appendEncodedStringValue(ccStr, PduHeaders.CC);
                        break;
                    case PduHeaders.SUBJECT:
                        EncodedStringValue subj = parseEncodedStringValue();
                        if (subj != null) headers.setEncodedStringValue(subj, PduHeaders.SUBJECT);
                        break;
                    case PduHeaders.MESSAGE_CLASS:
                        int mClass = mPduDataStream.read();
                        break;
                    case PduHeaders.EXPIRY:
                        parseValueLength();
                        int expToken = mPduDataStream.read();
                        long exp = parseLongInteger();
                        headers.setLongInt(exp, PduHeaders.EXPIRY);
                        break;
                    case PduHeaders.PRIORITY:
                        int priority = mPduDataStream.read();
                        headers.setOctet(priority, PduHeaders.PRIORITY);
                        break;
                    case PduHeaders.MESSAGE_SIZE:
                        long size = parseLongInteger();
                        headers.setLongInt(size, PduHeaders.MESSAGE_SIZE);
                        break;
                    case PduHeaders.CONTENT_LOCATION:
                        byte[] ctLocation = parseTextString();
                        if (ctLocation != null) headers.setTextString(ctLocation, PduHeaders.CONTENT_LOCATION);
                        break;
                    case PduHeaders.CONTENT_TYPE:
                        parseContentType(headers);
                        keepParsing = false; // Body follows Content-Type header
                        break;
                    case PduHeaders.STATUS:
                        int status = mPduDataStream.read();
                        headers.setOctet(status, PduHeaders.STATUS);
                        break;
                    case PduHeaders.RESPONSE_STATUS:
                        int respStatus = mPduDataStream.read();
                        headers.setOctet(respStatus, PduHeaders.RESPONSE_STATUS);
                        break;
                    case PduHeaders.MESSAGE_ID:
                        byte[] msgId = parseTextString();
                        if (msgId != null) headers.setTextString(msgId, PduHeaders.MESSAGE_ID);
                        break;
                    default:
                        // Unknown or unhandled header: attempt basic string or octet skip
                        if ((headerField & 0x80) != 0) {
                            // Well-known header
                            int next = mPduDataStream.read();
                            if (next >= 0x80) {
                                // Short integer
                            } else if (next > 0 && next <= 30) {
                                mPduDataStream.skip(next);
                            }
                        }
                        break;
                }
            } catch (Exception ignored) {
            }
        }
        return headers;
    }

    private void parseContentType(PduHeaders headers) {
        int length = parseValueLength();
        int first = mPduDataStream.read();
        String ct = "application/vnd.wap.multipart.related";

        if ((first & 0x80) != 0) {
            int index = first & 0x7F;
            if (index < PduContentTypes.contentTypes.length) {
                ct = PduContentTypes.contentTypes[index];
            }
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(first);
            int b;
            while ((b = mPduDataStream.read()) != 0 && b != -1) {
                baos.write(b);
            }
            ct = new String(baos.toByteArray(), StandardCharsets.US_ASCII);
        }
        headers.setTextString(ct.getBytes(StandardCharsets.US_ASCII), PduHeaders.CONTENT_TYPE);
    }

    @Nullable
    private PduBody parseParts() {
        if (mPduDataStream.available() <= 0) {
            return null;
        }

        int count = (int) parseUintvarInteger();
        PduBody body = new PduBody();

        for (int i = 0; i < count; i++) {
            if (mPduDataStream.available() <= 0) break;

            int headerLength = (int) parseUintvarInteger();
            int dataLength = (int) parseUintvarInteger();

            byte[] headerBytes = new byte[headerLength];
            int readHeader = mPduDataStream.read(headerBytes, 0, headerLength);

            byte[] dataBytes = new byte[dataLength];
            int readData = mPduDataStream.read(dataBytes, 0, dataLength);

            PduPart part = new PduPart();
            part.setData(dataBytes);
            parsePartHeaders(headerBytes, part);
            body.addPart(part);
        }
        return body;
    }

    private void parsePartHeaders(byte[] headerBytes, PduPart part) {
        ByteArrayInputStream is = new ByteArrayInputStream(headerBytes);
        int ct = is.read();
        String contentType = "application/octet-stream";

        if ((ct & 0x80) != 0) {
            int index = ct & 0x7F;
            if (index < PduContentTypes.contentTypes.length) {
                contentType = PduContentTypes.contentTypes[index];
            }
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(ct);
            int b;
            while ((b = is.read()) != 0 && b != -1) {
                baos.write(b);
            }
            contentType = new String(baos.toByteArray(), StandardCharsets.US_ASCII);
        }
        part.setContentType(contentType.getBytes(StandardCharsets.US_ASCII));

        // Read remaining part headers (Content-ID, Content-Location, etc.)
        while (is.available() > 0) {
            int tag = is.read();
            if (tag == -1) break;

            if (tag == 0x81) {
                // Charset
                int charset = is.read() & 0x7F;
                part.setCharset(charset);
            } else if (tag == 0xC0) {
                // Content-ID
                byte[] cid = readQuotedOrTextString(is);
                part.setContentId(cid);
            } else if (tag == 0x8E) {
                // Content-Location
                byte[] cl = readTextStringFromStream(is);
                part.setContentLocation(cl);
            } else if (tag == 0x85) {
                // Name
                byte[] name = readTextStringFromStream(is);
                part.setName(name);
            } else if (tag == 0x86) {
                // Filename
                byte[] fn = readTextStringFromStream(is);
                part.setFilename(fn);
            } else if (tag == 0xC5) {
                // Content-Disposition
                byte[] cd = readTextStringFromStream(is);
                part.setContentDisposition(cd);
            }
        }
    }

    private byte[] readTextStringFromStream(ByteArrayInputStream is) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = is.read()) != 0 && b != -1) {
            baos.write(b);
        }
        return baos.toByteArray();
    }

    private byte[] readQuotedOrTextString(ByteArrayInputStream is) {
        int first = is.read();
        if (first == '"') {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int b;
            while ((b = is.read()) != '"' && b != -1) {
                baos.write(b);
            }
            is.read(); // null terminator if present
            return baos.toByteArray();
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (first != -1 && first != 0) baos.write(first);
            int b;
            while ((b = is.read()) != 0 && b != -1) {
                baos.write(b);
            }
            return baos.toByteArray();
        }
    }

    private int parseShortInteger() {
        int val = mPduDataStream.read();
        return val & 0x7F;
    }

    private long parseLongInteger() {
        int count = mPduDataStream.read();
        long result = 0;
        for (int i = 0; i < count; i++) {
            result = (result << 8) | (mPduDataStream.read() & 0xFF);
        }
        return result;
    }

    private long parseUintvarInteger() {
        long result = 0;
        int b;
        while ((b = mPduDataStream.read()) != -1) {
            result = (result << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return result;
    }

    private int parseValueLength() {
        int first = mPduDataStream.read();
        if (first < 31) {
            return first;
        } else if (first == 31) {
            return (int) parseUintvarInteger();
        }
        return first & 0x7F;
    }

    @Nullable
    private byte[] parseTextString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = mPduDataStream.read()) != 0 && b != -1) {
            baos.write(b);
        }
        return baos.toByteArray();
    }

    @Nullable
    private EncodedStringValue parseEncodedStringValue() {
        int first = mPduDataStream.read();
        if (first == -1) return null;

        int length = first;
        int charset = 0;

        if (first >= 32) {
            // Unquoted string without explicit length prefix
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(first);
            int b;
            while ((b = mPduDataStream.read()) != 0 && b != -1) {
                baos.write(b);
            }
            return new EncodedStringValue(CharacterSets.UTF_8, baos.toByteArray());
        }

        // Value-length prefixed
        int second = mPduDataStream.read();
        if ((second & 0x80) != 0) {
            charset = second & 0x7F;
        } else {
            // first byte was part of the text
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (second != -1 && second != 0) baos.write(second);
            int b;
            while ((b = mPduDataStream.read()) != 0 && b != -1) {
                baos.write(b);
            }
            return new EncodedStringValue(CharacterSets.UTF_8, baos.toByteArray());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = mPduDataStream.read()) != 0 && b != -1) {
            baos.write(b);
        }
        return new EncodedStringValue(charset != 0 ? charset : CharacterSets.UTF_8, baos.toByteArray());
    }
}
