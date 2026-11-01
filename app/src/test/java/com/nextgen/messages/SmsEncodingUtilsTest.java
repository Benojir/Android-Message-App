package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nextgen.messages.telephony.SmsEncodingUtils;
import org.junit.Test;

/**
 * Unit tests verifying GSM 7-bit vs 16-bit UCS-2 Unicode detection,
 * Bengali, Hindi, and emoji handling, and SMS segment calculations.
 */
public class SmsEncodingUtilsTest {

    @Test
    public void testGsm7Bit_StandardEnglish() {
        String asciiText = "Hello world, this is a standard English SMS message 1234567890!";
        assertTrue(SmsEncodingUtils.isGsm7Bit(asciiText));

        SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(asciiText);
        assertFalse(info.isUnicode);
        assertEquals(1, info.segmentCount);
        assertEquals(SmsEncodingUtils.GSM_SINGLE_PART_LIMIT - asciiText.length(), info.remainingInSegment);
    }

    @Test
    public void testGsm7Bit_ExtendedCharacters() {
        // Extended characters (^, {, }, [, ], ~, |, €, \) count as 2 units in GSM 7-bit
        String text = "Cost is 10€ [Special]";
        assertTrue(SmsEncodingUtils.isGsm7Bit(text));

        SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(text);
        assertFalse(info.isUnicode);
        assertEquals(1, info.segmentCount);
    }

    @Test
    public void testUnicode_BengaliDetection() {
        String bengaliText = "শুভ সকাল! কেমন আছেন?";
        assertFalse(SmsEncodingUtils.isGsm7Bit(bengaliText));

        SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(bengaliText);
        assertTrue(info.isUnicode);
        assertEquals(1, info.segmentCount); // under 70 chars
        assertEquals(SmsEncodingUtils.UNICODE_SINGLE_PART_LIMIT - bengaliText.length(), info.remainingInSegment);
    }

    @Test
    public void testUnicode_HindiDetection() {
        String hindiText = "नमस्ते! आज मौसम कैसा है?";
        assertFalse(SmsEncodingUtils.isGsm7Bit(hindiText));

        SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(hindiText);
        assertTrue(info.isUnicode);
        assertEquals(1, info.segmentCount);
    }

    @Test
    public void testUnicode_EmojiDetection() {
        String emojiText = "Meeting at 3 PM 👍🎉";
        assertFalse(SmsEncodingUtils.isGsm7Bit(emojiText));

        SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(emojiText);
        assertTrue(info.isUnicode);
        assertEquals(1, info.segmentCount);
    }

    @Test
    public void testGsm7Bit_MultipartThreshold() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 161; i++) {
            sb.append("A");
        }
        String longText = sb.toString();

        SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(longText);
        assertFalse(info.isUnicode);
        assertEquals(2, info.segmentCount);
        // In multipart GSM, each part holds 153 chars: 2 * 153 = 306 max, 306 - 161 = 145 remaining
        assertEquals(306 - 161, info.remainingInSegment);
    }

    @Test
    public void testUnicode_MultipartThreshold() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 71; i++) {
            sb.append("ক"); // Bengali character 'Ka'
        }
        String longUnicode = sb.toString();

        SmsEncodingUtils.SegmentInfo info = SmsEncodingUtils.calculateSegments(longUnicode);
        assertTrue(info.isUnicode);
        assertEquals(2, info.segmentCount);
        // In multipart Unicode, each part holds 67 chars: 2 * 67 = 134 max, 134 - 71 = 63 remaining
        assertEquals(134 - 71, info.remainingInSegment);
    }
}
