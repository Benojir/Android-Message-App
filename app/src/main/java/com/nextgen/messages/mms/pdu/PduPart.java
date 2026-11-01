package com.nextgen.messages.mms.pdu;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * An individual component/part of a multipart MMS PDU (e.g., text, image, audio, video, or SMIL).
 */
public class PduPart {

    public static final int P_CHARSET = 0x81;
    public static final int P_CONTENT_TYPE = 0x91;
    public static final int P_CONTENT_LOCATION = 0x8E;
    public static final int P_CONTENT_ID = 0xC0;
    public static final int P_CONTENT_DISPOSITION = 0xC5;
    public static final int P_NAME = 0x85;
    public static final int P_FILENAME = 0x86;

    public static final String CONTENT_DISPOSITION_INLINE = "inline";
    public static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment";

    private final Map<Integer, Object> mPartHeader = new HashMap<>();
    private Uri mUri;
    private byte[] mPartData;

    public PduPart() {}

    public void setData(@Nullable byte[] data) {
        if (data == null) {
            mPartData = null;
            return;
        }
        mPartData = new byte[data.length];
        System.arraycopy(data, 0, mPartData, 0, data.length);
    }

    @Nullable
    public byte[] getData() {
        if (mPartData == null) {
            return null;
        }
        byte[] copy = new byte[mPartData.length];
        System.arraycopy(mPartData, 0, copy, 0, mPartData.length);
        return copy;
    }

    public int getDataLength() {
        return mPartData != null ? mPartData.length : 0;
    }

    @Nullable
    public Uri getDataUri() {
        return mUri;
    }

    public void setDataUri(@Nullable Uri uri) {
        mUri = uri;
    }

    public void setContentType(@NonNull byte[] contentType) {
        mPartHeader.put(P_CONTENT_TYPE, contentType);
    }

    @Nullable
    public byte[] getContentType() {
        return (byte[]) mPartHeader.get(P_CONTENT_TYPE);
    }

    public void setContentLocation(@NonNull byte[] contentLocation) {
        mPartHeader.put(P_CONTENT_LOCATION, contentLocation);
    }

    @Nullable
    public byte[] getContentLocation() {
        return (byte[]) mPartHeader.get(P_CONTENT_LOCATION);
    }

    public void setContentId(@NonNull byte[] contentId) {
        mPartHeader.put(P_CONTENT_ID, contentId);
    }

    @Nullable
    public byte[] getContentId() {
        return (byte[]) mPartHeader.get(P_CONTENT_ID);
    }

    public void setContentDisposition(@NonNull byte[] disposition) {
        mPartHeader.put(P_CONTENT_DISPOSITION, disposition);
    }

    @Nullable
    public byte[] getContentDisposition() {
        return (byte[]) mPartHeader.get(P_CONTENT_DISPOSITION);
    }

    public void setFilename(@NonNull byte[] filename) {
        mPartHeader.put(P_FILENAME, filename);
    }

    @Nullable
    public byte[] getFilename() {
        return (byte[]) mPartHeader.get(P_FILENAME);
    }

    public void setName(@NonNull byte[] name) {
        mPartHeader.put(P_NAME, name);
    }

    @Nullable
    public byte[] getName() {
        return (byte[]) mPartHeader.get(P_NAME);
    }

    public void setCharset(int charset) {
        mPartHeader.put(P_CHARSET, charset);
    }

    public int getCharset() {
        Integer val = (Integer) mPartHeader.get(P_CHARSET);
        return val != null ? val : 0;
    }
}
