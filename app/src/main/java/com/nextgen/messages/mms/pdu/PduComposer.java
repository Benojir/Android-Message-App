package com.nextgen.messages.mms.pdu;

import android.content.ContentResolver;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Encodes MMS PDU data models (like SendReq) into standards-compliant WAP-209 binary MMS PDU bytes.
 */
public class PduComposer {

    private static final int PDU_PHONE_NUMBER_MARKER = 0x2B; // '+'
    private static final int PDU_EMAIL_MARKER = 0x40; // '@'

    @Nullable
    private final Context mContext;
    private final GenericPdu mPdu;
    private final PduHeaders mPduHeader;
    private ByteArrayOutputStream mMessage;
    private int mPosition = 0;

    public PduComposer(@NonNull GenericPdu pdu) {
        this(null, pdu);
    }

    public PduComposer(@Nullable Context context, @NonNull GenericPdu pdu) {
        mContext = context != null ? context.getApplicationContext() : null;
        mPdu = pdu;
        mPduHeader = pdu.getPduHeaders();
    }

    @Nullable
    public byte[] make() {
        mMessage = new ByteArrayOutputStream();
        mPosition = 0;

        int msgType = mPdu.getMessageType();
        switch (msgType) {
            case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                if (makeSendReqPdu() != 0) {
                    return null;
                }
                break;
            case PduHeaders.MESSAGE_TYPE_NOTIFYRESP_IND:
                if (makeNotifyRespIndPdu() != 0) {
                    return null;
                }
                break;
            case PduHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND:
                if (makeAckIndPdu() != 0) {
                    return null;
                }
                break;
            default:
                return null;
        }

        return mMessage.toByteArray();
    }

    private int makeSendReqPdu() {
        if (mMessage == null) {
            mMessage = new ByteArrayOutputStream();
            mPosition = 0;
        }

        // 1. Message Type (mandatory)
        appendOctet(PduHeaders.MESSAGE_TYPE);
        appendOctet(PduHeaders.MESSAGE_TYPE_SEND_REQ);

        // 2. Transaction ID (mandatory)
        byte[] trId = mPduHeader.getTextString(PduHeaders.TRANSACTION_ID);
        if (trId == null) {
            trId = ("T" + Long.toHexString(System.currentTimeMillis())).getBytes();
        }
        appendOctet(PduHeaders.TRANSACTION_ID);
        appendTextString(trId);

        // 3. MMS Version (mandatory)
        int version = mPduHeader.getOctet(PduHeaders.MMS_VERSION);
        if (version == 0) {
            version = PduHeaders.CURRENT_MMS_VERSION;
        }
        appendOctet(PduHeaders.MMS_VERSION);
        appendShortInteger(version);

        // 4. Date (optional)
        long date = mPduHeader.getLongInt(PduHeaders.DATE);
        if (date > 0) {
            appendOctet(PduHeaders.DATE);
            appendLongInteger(date);
        }

        // 5. From (mandatory for SendReq)
        EncodedStringValue from = mPduHeader.getEncodedStringValue(PduHeaders.FROM);
        appendOctet(PduHeaders.FROM);
        if (from != null && from.getTextString().length > 0) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(PduHeaders.FROM_ADDRESS_PRESENT_TOKEN);
            appendEncodedStringToStream(baos, from);
            byte[] fromBytes = baos.toByteArray();
            appendValueLength(fromBytes.length);
            arrayCopy(fromBytes);
        } else {
            // Insert-address-token (carrier inserts sender MSISDN)
            appendValueLength(1);
            appendOctet(PduHeaders.FROM_INSERT_ADDRESS_TOKEN);
        }

        // 6. To (mandatory: at least one recipient)
        EncodedStringValue[] toList = mPduHeader.getEncodedStringValues(PduHeaders.TO);
        if (toList != null) {
            for (EncodedStringValue to : toList) {
                appendOctet(PduHeaders.TO);
                appendEncodedString2(to);
            }
        }

        // 7. Subject (optional)
        EncodedStringValue subject = mPduHeader.getEncodedStringValue(PduHeaders.SUBJECT);
        if (subject != null) {
            appendOctet(PduHeaders.SUBJECT);
            appendEncodedString2(subject);
        }

        // 8. Priority (optional)
        int priority = mPduHeader.getOctet(PduHeaders.PRIORITY);
        if (priority != 0) {
            appendOctet(PduHeaders.PRIORITY);
            appendOctet(priority);
        }

        // 9. Delivery report (optional)
        int dr = mPduHeader.getOctet(PduHeaders.DELIVERY_REPORT);
        if (dr != 0) {
            appendOctet(PduHeaders.DELIVERY_REPORT);
            appendOctet(dr);
        }

        // 10. Read report (optional)
        int rr = mPduHeader.getOctet(PduHeaders.READ_REPORT);
        if (rr != 0) {
            appendOctet(PduHeaders.READ_REPORT);
            appendOctet(rr);
        }

        // 11. Content-Type and Body (mandatory)
        appendOctet(PduHeaders.CONTENT_TYPE);
        return makeMessageBody();
    }

    private int makeNotifyRespIndPdu() {
        appendOctet(PduHeaders.MESSAGE_TYPE);
        appendOctet(PduHeaders.MESSAGE_TYPE_NOTIFYRESP_IND);

        byte[] trId = mPduHeader.getTextString(PduHeaders.TRANSACTION_ID);
        if (trId != null) {
            appendOctet(PduHeaders.TRANSACTION_ID);
            appendTextString(trId);
        }

        int version = mPduHeader.getOctet(PduHeaders.MMS_VERSION);
        if (version == 0) version = PduHeaders.CURRENT_MMS_VERSION;
        appendOctet(PduHeaders.MMS_VERSION);
        appendShortInteger(version);

        int status = mPduHeader.getOctet(PduHeaders.STATUS);
        if (status != 0) {
            appendOctet(PduHeaders.STATUS);
            appendOctet(status);
        }
        return 0;
    }

    private int makeAckIndPdu() {
        appendOctet(PduHeaders.MESSAGE_TYPE);
        appendOctet(PduHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND);

        byte[] trId = mPduHeader.getTextString(PduHeaders.TRANSACTION_ID);
        if (trId != null) {
            appendOctet(PduHeaders.TRANSACTION_ID);
            appendTextString(trId);
        }

        int version = mPduHeader.getOctet(PduHeaders.MMS_VERSION);
        if (version == 0) version = PduHeaders.CURRENT_MMS_VERSION;
        appendOctet(PduHeaders.MMS_VERSION);
        appendShortInteger(version);
        return 0;
    }

    private int makeMessageBody() {
        PduBody body = ((MultimediaMessagePdu) mPdu).getBody();
        if (body == null || body.getPartsNum() == 0) {
            // Empty body with 0 parts
            appendUintvarInteger(0);
            return 0;
        }

        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        int nParts = body.getPartsNum();
        appendUintvarInteger(bodyStream, nParts);

        for (int i = 0; i < nParts; i++) {
            PduPart part = body.getPart(i);
            ByteArrayOutputStream partHeaderStream = new ByteArrayOutputStream();

            // Content-Type (mandatory for part)
            byte[] partContentType = part.getContentType();
            if (partContentType == null) {
                partContentType = "application/octet-stream".getBytes();
            }

            int ctIndex = findContentTypeIndex(new String(partContentType));
            if (ctIndex >= 0) {
                partHeaderStream.write(ctIndex | 0x80);
            } else {
                appendNullTerminatedString(partHeaderStream, partContentType);
            }

            // Charset parameter for text
            if (part.getCharset() != 0) {
                partHeaderStream.write(0x81); // Charset param token
                partHeaderStream.write(part.getCharset() | 0x80);
            }

            // Content-ID
            byte[] cid = part.getContentId();
            if (cid != null) {
                partHeaderStream.write(0xC0); // Content-ID token
                appendQuotedString(partHeaderStream, cid);
            }

            // Content-Location
            byte[] cl = part.getContentLocation();
            if (cl != null) {
                partHeaderStream.write(0x8E); // Content-Location token
                appendNullTerminatedString(partHeaderStream, cl);
            }

            // Content-Disposition
            byte[] cd = part.getContentDisposition();
            if (cd != null) {
                partHeaderStream.write(0xC5); // Content-Disposition token
                appendNullTerminatedString(partHeaderStream, cd);
            }

            // Name
            byte[] name = part.getName();
            if (name != null) {
                partHeaderStream.write(0x85); // Name token
                appendNullTerminatedString(partHeaderStream, name);
            }

            // Filename
            byte[] fn = part.getFilename();
            if (fn != null) {
                partHeaderStream.write(0x86); // Filename token
                appendNullTerminatedString(partHeaderStream, fn);
            }

            byte[] partData = part.getData();
            if (partData == null && part.getDataUri() != null) {
                partData = readUriData(part.getDataUri());
            }
            int dataLength = (partData != null) ? partData.length : 0;
            byte[] headerBytes = partHeaderStream.toByteArray();

            // Part structure: HeaderLength (uintvar), DataLength (uintvar), Headers, Data
            appendUintvarInteger(bodyStream, headerBytes.length);
            appendUintvarInteger(bodyStream, dataLength);
            bodyStream.write(headerBytes, 0, headerBytes.length);
            if (partData != null && dataLength > 0) {
                bodyStream.write(partData, 0, dataLength);
            }
        }

        // Write multipart/related content-type header with length and body
        byte[] bodyBytes = bodyStream.toByteArray();
        ByteArrayOutputStream ctStream = new ByteArrayOutputStream();
        ctStream.write(0x23 | 0x80); // application/vnd.wap.multipart.related (0x23)
        byte[] ctBytes = ctStream.toByteArray();

        appendValueLength(ctBytes.length);
        arrayCopy(ctBytes);
        arrayCopy(bodyBytes);
        return 0;
    }

    @Nullable
    private byte[] readUriData(@NonNull android.net.Uri uri) {
        if (mContext == null) return null;
        try (InputStream is = mContext.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (is == null) return null;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private int findContentTypeIndex(String contentType) {
        if (contentType == null) return -1;
        for (int i = 0; i < PduContentTypes.contentTypes.length; i++) {
            if (PduContentTypes.contentTypes[i].equalsIgnoreCase(contentType)) {
                return i;
            }
        }
        return -1;
    }

    private void appendOctet(int octet) {
        mMessage.write(octet);
        mPosition++;
    }

    private void appendShortInteger(int value) {
        mMessage.write(value | 0x80);
        mPosition++;
    }

    private void appendLongInteger(long value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        long temp = value;
        while (temp > 0) {
            baos.write((int) (temp & 0xFF));
            temp >>>= 8;
        }
        byte[] raw = baos.toByteArray();
        int len = raw.length;
        if (len == 0) len = 1;

        appendOctet(len);
        for (int i = len - 1; i >= 0; i--) {
            if (i < raw.length) {
                appendOctet(raw[i]);
            } else {
                appendOctet(0);
            }
        }
    }

    private void appendTextString(byte[] text) {
        if (text != null && text.length > 0) {
            mMessage.write(text, 0, text.length);
            mPosition += text.length;
        }
        mMessage.write(0);
        mPosition++;
    }

    private void appendEncodedString2(EncodedStringValue ev) {
        byte[] text = ev.getTextString();
        int charset = ev.getCharset();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (charset != 0) {
            baos.write(charset | 0x80);
        }
        baos.write(text, 0, text.length);
        baos.write(0);
        byte[] data = baos.toByteArray();

        appendValueLength(data.length);
        arrayCopy(data);
    }

    private void appendEncodedStringToStream(ByteArrayOutputStream os, EncodedStringValue ev) {
        byte[] text = ev.getTextString();
        int charset = ev.getCharset();
        if (charset != 0) {
            os.write(charset | 0x80);
        }
        os.write(text, 0, text.length);
        os.write(0);
    }

    private void appendValueLength(long value) {
        if (value < 31) {
            appendOctet((int) value);
        } else {
            appendOctet(0x1F);
            appendUintvarInteger(value);
        }
    }

    private void appendUintvarInteger(long value) {
        appendUintvarInteger(mMessage, value);
    }

    private void appendUintvarInteger(ByteArrayOutputStream os, long value) {
        long temp = value;
        byte[] bytes = new byte[8];
        int count = 0;
        bytes[count++] = (byte) (temp & 0x7F);
        temp >>>= 7;
        while (temp > 0) {
            bytes[count++] = (byte) ((temp & 0x7F) | 0x80);
            temp >>>= 7;
        }
        for (int i = count - 1; i >= 0; i--) {
            os.write(bytes[i]);
            mPosition++;
        }
    }

    private void appendNullTerminatedString(ByteArrayOutputStream os, byte[] bytes) {
        os.write(bytes, 0, bytes.length);
        os.write(0);
    }

    private void appendQuotedString(ByteArrayOutputStream os, byte[] bytes) {
        os.write('"');
        os.write(bytes, 0, bytes.length);
        os.write('"');
        os.write(0);
    }

    private void arrayCopy(byte[] src) {
        mMessage.write(src, 0, src.length);
        mPosition += src.length;
    }
}
