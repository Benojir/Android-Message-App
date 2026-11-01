package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.nextgen.messages.utils.DateFormatter;
import java.util.Calendar;
import java.util.Locale;
import org.junit.Test;

/**
 * Unit tests for DateFormatter timestamp and date header logic.
 */
public class DateFormatterTest {

    @Test
    public void testFormatConversationTime_Today() {
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.set(2026, Calendar.NOVEMBER, 1, 14, 30, 0);
        long now = cal.getTimeInMillis();

        // 2 hours earlier on the same day (12:30 PM)
        cal.set(2026, Calendar.NOVEMBER, 1, 12, 30, 0);
        long messageTime = cal.getTimeInMillis();

        String formatted = DateFormatter.formatConversationTime(messageTime, now, Locale.US);
        assertEquals("12:30 PM", formatted);
    }

    @Test
    public void testFormatConversationTime_Yesterday() {
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.set(2026, Calendar.NOVEMBER, 1, 14, 30, 0);
        long now = cal.getTimeInMillis();

        // Yesterday at 10:00 AM
        cal.set(2026, Calendar.OCTOBER, 31, 10, 0, 0);
        long messageTime = cal.getTimeInMillis();

        String formatted = DateFormatter.formatConversationTime(messageTime, now, Locale.US);
        assertEquals("Yesterday", formatted);
    }

    @Test
    public void testFormatConversationTime_PastWeekday() {
        Calendar cal = Calendar.getInstance(Locale.US);
        // Friday Nov 6, 2026
        cal.set(2026, Calendar.NOVEMBER, 6, 12, 0, 0);
        long now = cal.getTimeInMillis();

        // Tuesday Nov 3, 2026 (3 days ago)
        cal.set(2026, Calendar.NOVEMBER, 3, 9, 15, 0);
        long messageTime = cal.getTimeInMillis();

        String formatted = DateFormatter.formatConversationTime(messageTime, now, Locale.US);
        assertEquals("Tuesday", formatted);
    }

    @Test
    public void testFormatConversationTime_SameYear() {
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.set(2026, Calendar.NOVEMBER, 1, 12, 0, 0);
        long now = cal.getTimeInMillis();

        // August 15, 2026 (several months ago in same year)
        cal.set(2026, Calendar.AUGUST, 15, 10, 0, 0);
        long messageTime = cal.getTimeInMillis();

        String formatted = DateFormatter.formatConversationTime(messageTime, now, Locale.US);
        assertEquals("Aug 15", formatted);
    }

    @Test
    public void testFormatConversationTime_DifferentYear() {
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.set(2026, Calendar.NOVEMBER, 1, 12, 0, 0);
        long now = cal.getTimeInMillis();

        // December 25, 2024 (prior year)
        cal.set(2024, Calendar.DECEMBER, 25, 18, 0, 0);
        long messageTime = cal.getTimeInMillis();

        String formatted = DateFormatter.formatConversationTime(messageTime, now, Locale.US);
        assertEquals("Dec 25, 2024", formatted);
    }

    @Test
    public void testFormatDateHeader_TodayAndYesterday() {
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.set(2026, Calendar.NOVEMBER, 1, 14, 0, 0);
        long now = cal.getTimeInMillis();

        assertEquals("Today", DateFormatter.formatDateHeader(now, now, Locale.US));

        cal.set(2026, Calendar.OCTOBER, 31, 12, 0, 0);
        assertEquals("Yesterday", DateFormatter.formatDateHeader(cal.getTimeInMillis(), now, Locale.US));
    }

    @Test
    public void testGetDayKey_SeparatesDifferentDays() {
        Calendar cal1 = Calendar.getInstance(Locale.US);
        cal1.set(2026, Calendar.NOVEMBER, 1, 9, 0, 0);

        Calendar cal2 = Calendar.getInstance(Locale.US);
        cal2.set(2026, Calendar.NOVEMBER, 1, 23, 59, 59);

        Calendar cal3 = Calendar.getInstance(Locale.US);
        cal3.set(2026, Calendar.NOVEMBER, 2, 0, 1, 0);

        assertEquals(DateFormatter.getDayKey(cal1.getTimeInMillis()), DateFormatter.getDayKey(cal2.getTimeInMillis()));
        assertFalse(DateFormatter.getDayKey(cal1.getTimeInMillis()) == DateFormatter.getDayKey(cal3.getTimeInMillis()));
    }
}
