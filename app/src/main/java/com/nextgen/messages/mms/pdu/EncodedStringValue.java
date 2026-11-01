package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Encapsulates encoded string values with their respective MIBenum character sets in MMS PDUs.
 */
public class EncodedStringValue implements Cloneable {

    private int charset;
    private byte[] data;

    public EncodedStringValue(int charset, @NonNull byte[] data) {
        if (data == null) {
            throw new NullPointerException("EncodedStringValue: data cannot be null");
        }
        this.charset = charset;
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    public EncodedStringValue(@NonNull byte[] data) {
        this(CharacterSets.UTF_8, data);
    }

    public EncodedStringValue(@NonNull String data) {
        this.charset = CharacterSets.UTF_8;
        this.data = data.getBytes(StandardCharsets.UTF_8);
    }

    public int getCharset() {
        return charset;
    }

    public void setCharset(int charset) {
        this.charset = charset;
    }

    @NonNull
    public byte[] getTextString() {
        byte[] copy = new byte[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);
        return copy;
    }

    public void setTextString(@NonNull byte[] textString) {
        if (textString == null) {
            throw new NullPointerException("EncodedStringValue: textString cannot be null");
        }
        this.data = new byte[textString.length];
        System.arraycopy(textString, 0, this.data, 0, textString.length);
    }

    @NonNull
    public String getString() {
        if (charset == 0) {
            return new String(data, StandardCharsets.UTF_8);
        }
        try {
            String name = CharacterSets.getMimeName(charset);
            return new String(data, name);
        } catch (UnsupportedEncodingException e) {
            try {
                return new String(data, CharacterSets.MIMENAME_ISO_8859_1);
            } catch (UnsupportedEncodingException e2) {
                return new String(data, StandardCharsets.UTF_8);
            }
        }
    }

    public void appendTextString(@NonNull byte[] textString) {
        if (textString == null) {
            throw new NullPointerException("EncodedStringValue: textString cannot be null");
        }
        if (data == null) {
            this.data = new byte[textString.length];
            System.arraycopy(textString, 0, this.data, 0, textString.length);
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(data, 0, data.length);
            baos.write(textString, 0, textString.length);
            this.data = baos.toByteArray();
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        super.clone();
        int len = data.length;
        byte[] dst = new byte[len];
        System.arraycopy(data, 0, dst, 0, len);
        try {
            return new EncodedStringValue(charset, dst);
        } catch (Exception e) {
            throw new CloneNotSupportedException(e.getMessage());
        }
    }

    @NonNull
    public static EncodedStringValue[] encodeStrings(@Nullable String[] array) {
        if (array == null || array.length == 0) {
            return new EncodedStringValue[0];
        }
        int count = array.length;
        EncodedStringValue[] encoded = new EncodedStringValue[count];
        for (int i = 0; i < count; i++) {
            encoded[i] = new EncodedStringValue(array[i] != null ? array[i] : "");
        }
        return encoded;
    }

    @NonNull
    public static String[] decodeStrings(@Nullable EncodedStringValue[] array) {
        if (array == null || array.length == 0) {
            return new String[0];
        }
        int count = array.length;
        String[] decoded = new String[count];
        for (int i = 0; i < count; i++) {
            decoded[i] = (array[i] != null) ? array[i].getString() : "";
        }
        return decoded;
    }

    @NonNull
    @Override
    public String toString() {
        return getString();
    }
}
