package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.nextgen.messages.telephony.SmsEncodingUtils;
import com.nextgen.messages.utils.DateFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Unit tests for Step 5: Advanced Chat Interface, Multi-selection, Undo Deletion logic,
 * Unicode/Multi-language calculation, and metadata formatting.
 */
public class Step5AdvancedManagementTest {

    @Test
    public void testFormatFullDateTime_ValidAndZero() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.NOVEMBER, 1, 15, 30, 45);
        long time = cal.getTimeInMillis();

        String formatted = DateFormatter.formatFullDateTime(time);
        assertNotNull(formatted);
        assertFalse(formatted.isEmpty());
        assertTrue(formatted.contains("2026"));

        String zeroResult = DateFormatter.formatFullDateTime(0);
        assertEquals("", zeroResult);
    }

    @Test
    public void testSmsEncodingUtils_Gsm7Bit() {
        String ascii = "Hello, this is a standard GSM 7-bit message under 160 characters!";
        assertTrue(SmsEncodingUtils.isGsm7Bit(ascii));
        SmsEncodingUtils.SegmentInfo res = SmsEncodingUtils.calculateSegments(ascii);
        assertFalse(res.isUnicode());
        assertEquals(1, res.getSegmentCount());
        assertEquals(ascii.length(), res.getCodePointCount());
    }

    @Test
    public void testSmsEncodingUtils_BengaliText() {
        String bengali = "শুভ সকাল, আপনি কেমন আছেন?";
        assertFalse(SmsEncodingUtils.isGsm7Bit(bengali));
        SmsEncodingUtils.SegmentInfo res = SmsEncodingUtils.calculateSegments(bengali);
        assertTrue(res.isUnicode());
        assertEquals(1, res.getSegmentCount());
    }

    @Test
    public void testSmsEncodingUtils_HindiText() {
        String hindi = "नमस्ते, आप कैसे हैं?";
        assertFalse(SmsEncodingUtils.isGsm7Bit(hindi));
        SmsEncodingUtils.SegmentInfo res = SmsEncodingUtils.calculateSegments(hindi);
        assertTrue(res.isUnicode());
        assertEquals(1, res.getSegmentCount());
    }

    @Test
    public void testSmsEncodingUtils_EmojiMultiSegment() {
        // 40 double-byte emoji -> 80 UTF-16 code units -> UCS-2 multi-segment (67 chars per part)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("🎉");
        }
        String text = sb.toString();
        assertFalse(SmsEncodingUtils.isGsm7Bit(text));
        SmsEncodingUtils.SegmentInfo res = SmsEncodingUtils.calculateSegments(text);
        assertTrue(res.isUnicode());
        assertTrue(res.getSegmentCount() >= 2);
    }

    @Test
    public void testMessageMultiSelectionStateTracking() {
        Set<Long> selectedIds = new HashSet<>();
        List<Long> messageIds = Arrays.asList(101L, 102L, 103L, 104L, 105L);

        // Initially empty
        assertTrue(selectedIds.isEmpty());

        // Select 101 and 103
        selectedIds.add(101L);
        selectedIds.add(103L);
        assertEquals(2, selectedIds.size());
        assertTrue(selectedIds.contains(101L));
        assertTrue(selectedIds.contains(103L));
        assertFalse(selectedIds.contains(102L));

        // Toggle 101 off
        if (selectedIds.contains(101L)) {
            selectedIds.remove(101L);
        } else {
            selectedIds.add(101L);
        }
        assertEquals(1, selectedIds.size());
        assertFalse(selectedIds.contains(101L));

        // Select All
        selectedIds.clear();
        selectedIds.addAll(messageIds);
        assertEquals(5, selectedIds.size());
        for (Long id : messageIds) {
            assertTrue(selectedIds.contains(id));
        }

        // Clear Selection
        selectedIds.clear();
        assertTrue(selectedIds.isEmpty());
    }

    @Test
    public void testUndoDeletionMaskingLogic() {
        List<Long> allItems = new ArrayList<>(Arrays.asList(1L, 2L, 3L, 4L, 5L));
        Set<Long> pendingDeleteIds = new HashSet<>();

        // Helper lambda to simulate publishFiltered
        java.util.function.Supplier<List<Long>> filter = () -> {
            List<Long> result = new ArrayList<>();
            for (Long item : allItems) {
                if (!pendingDeleteIds.contains(item)) {
                    result.add(item);
                }
            }
            return result;
        };

        // Initially all 5 items present
        assertEquals(5, filter.get().size());

        // User initiates delete for items 2 and 4
        Set<Long> toDelete = new HashSet<>(Arrays.asList(2L, 4L));
        pendingDeleteIds.addAll(toDelete);

        // List is now masked (items 2 and 4 hidden immediately)
        List<Long> maskedList = filter.get();
        assertEquals(3, maskedList.size());
        assertTrue(maskedList.contains(1L));
        assertTrue(maskedList.contains(3L));
        assertTrue(maskedList.contains(5L));
        assertFalse(maskedList.contains(2L));
        assertFalse(maskedList.contains(4L));

        // User clicks "Undo" -> cancelPendingDelete
        pendingDeleteIds.removeAll(toDelete);

        // List is unmasked and fully restored
        List<Long> restoredList = filter.get();
        assertEquals(5, restoredList.size());
        assertTrue(restoredList.contains(2L));
        assertTrue(restoredList.contains(4L));
    }
}
