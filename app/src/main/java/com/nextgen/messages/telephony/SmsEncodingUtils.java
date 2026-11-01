package com.nextgen.messages.telephony;

import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import androidx.annotation.NonNull;
import java.util.ArrayList;

/**
 * Utility for analyzing SMS text encoding (GSM 7-bit vs 16-bit UCS-2 / Unicode)
 * and calculating segment limits and remaining characters.
 */
public final class SmsEncodingUtils {

    public static final int GSM_SINGLE_PART_LIMIT = 160;
    public static final int GSM_MULTI_PART_LIMIT = 153;
    public static final int UNICODE_SINGLE_PART_LIMIT = 70;
    public static final int UNICODE_MULTI_PART_LIMIT = 67;

    private static final String GSM_7BIT_ALPHABET =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ" +
            " !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿" +
            "abcdefghijklmnopqrstuvwxyzäöñüà";

    private static final String GSM_7BIT_EXTENDED = "^{}\\[~]|€";

    private SmsEncodingUtils() {
        // Utility
    }

    public static class SegmentInfo {
        public final int codePointCount;
        public final int segmentCount;
        public final int remainingInSegment;
        public final boolean isUnicode;

        public SegmentInfo(int codePointCount, int segmentCount, int remainingInSegment, boolean isUnicode) {
            this.codePointCount = codePointCount;
            this.segmentCount = segmentCount;
            this.remainingInSegment = remainingInSegment;
            this.isUnicode = isUnicode;
        }

        @NonNull
        public String getCounterLabel() {
            return remainingInSegment + "/" + segmentCount + (isUnicode ? " (Unicode)" : "");
        }
    }

    /**
     * Determines whether the given text can be encoded purely in GSM 7-bit.
     */
    public static boolean isGsm7Bit(@NonNull String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (GSM_7BIT_ALPHABET.indexOf(c) == -1 && GSM_7BIT_EXTENDED.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calculates the segment count and remaining characters for the given text.
     */
    @NonNull
    public static SegmentInfo calculateSegments(@NonNull String text) {
        if (text.isEmpty()) {
            return new SegmentInfo(0, 1, GSM_SINGLE_PART_LIMIT, false);
        }

        boolean isUnicode = !isGsm7Bit(text);
        int totalUnits = 0;

        if (isUnicode) {
            // In Unicode / UCS-2, UTF-16 code units (or characters) count towards the 70 / 67 limits
            totalUnits = text.length();
            if (totalUnits <= UNICODE_SINGLE_PART_LIMIT) {
                return new SegmentInfo(totalUnits, 1, UNICODE_SINGLE_PART_LIMIT - totalUnits, true);
            } else {
                int segments = (int) Math.ceil((double) totalUnits / UNICODE_MULTI_PART_LIMIT);
                int remaining = (segments * UNICODE_MULTI_PART_LIMIT) - totalUnits;
                return new SegmentInfo(totalUnits, segments, remaining, true);
            }
        } else {
            // In GSM 7-bit, extended characters count as 2 units
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (GSM_7BIT_EXTENDED.indexOf(c) != -1) {
                    totalUnits += 2;
                } else {
                    totalUnits += 1;
                }
            }

            if (totalUnits <= GSM_SINGLE_PART_LIMIT) {
                return new SegmentInfo(totalUnits, 1, GSM_SINGLE_PART_LIMIT - totalUnits, false);
            } else {
                int segments = (int) Math.ceil((double) totalUnits / GSM_MULTI_PART_LIMIT);
                int remaining = (segments * GSM_MULTI_PART_LIMIT) - totalUnits;
                return new SegmentInfo(totalUnits, segments, remaining, false);
            }
        }
    }

    /**
     * Safely splits the message using Android's SmsManager.divideMessage API.
     */
    @NonNull
    public static ArrayList<String> divideMessage(@NonNull SmsManager smsManager, @NonNull String text) {
        try {
            ArrayList<String> parts = smsManager.divideMessage(text);
            if (parts != null && !parts.isEmpty()) {
                return parts;
            }
        } catch (Exception ignored) {
        }
        ArrayList<String> fallback = new ArrayList<>();
        fallback.add(text);
        return fallback;
    }
}
