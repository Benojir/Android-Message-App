package com.nextgen.messages.utils;

import android.content.Context;
import androidx.annotation.NonNull;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Utility for formatting timestamps for conversations, chat bubbles, and date headers.
 * Supports deterministic testing by allowing injected reference times.
 */
public final class DateFormatter {

    private DateFormatter() {
        // Utility class
    }

    /**
     * Formats conversation snippet timestamp in the inbox.
     * - Today: "10:45 AM"
     * - Yesterday: "Yesterday"
     * - Past 6 days: Day name ("Monday")
     * - Same year: "MMM d" (e.g. "Oct 24")
     * - Other year: "MMM d, yyyy" (e.g. "Oct 24, 2023")
     */
    @NonNull
    public static String formatConversationTime(long timestampMs) {
        return formatConversationTime(timestampMs, System.currentTimeMillis(), Locale.getDefault());
    }

    @NonNull
    public static String formatConversationTime(long timestampMs, long nowMs, @NonNull Locale locale) {
        if (timestampMs <= 0) {
            return "";
        }

        Calendar then = Calendar.getInstance(locale);
        then.setTimeInMillis(timestampMs);

        Calendar now = Calendar.getInstance(locale);
        now.setTimeInMillis(nowMs);

        int thenYear = then.get(Calendar.YEAR);
        int nowYear = now.get(Calendar.YEAR);
        int thenDayOfYear = then.get(Calendar.DAY_OF_YEAR);
        int nowDayOfYear = now.get(Calendar.DAY_OF_YEAR);

        if (thenYear == nowYear && thenDayOfYear == nowDayOfYear) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", locale);
            return timeFormat.format(new Date(timestampMs));
        }

        long diffMs = nowMs - timestampMs;
        long oneDayMs = 24L * 60 * 60 * 1000;

        // Check if yesterday
        Calendar yesterday = Calendar.getInstance(locale);
        yesterday.setTimeInMillis(nowMs - oneDayMs);
        if (thenYear == yesterday.get(Calendar.YEAR) && thenDayOfYear == yesterday.get(Calendar.DAY_OF_YEAR)) {
            return "Yesterday";
        }

        // Within the past 6 days
        if (diffMs > 0 && diffMs < 6 * oneDayMs) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", locale);
            return dayFormat.format(new Date(timestampMs));
        }

        // Within same calendar year
        if (thenYear == nowYear) {
            SimpleDateFormat monthDayFormat = new SimpleDateFormat("MMM d", locale);
            return monthDayFormat.format(new Date(timestampMs));
        }

        // Different year
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("MMM d, yyyy", locale);
        return fullDateFormat.format(new Date(timestampMs));
    }

    /**
     * Formats timestamp for individual chat bubbles (e.g., "10:45 AM").
     */
    @NonNull
    public static String formatMessageBubbleTime(long timestampMs) {
        return formatMessageBubbleTime(timestampMs, Locale.getDefault());
    }

    @NonNull
    public static String formatMessageBubbleTime(long timestampMs, @NonNull Locale locale) {
        if (timestampMs <= 0) {
            return "";
        }
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", locale);
        return timeFormat.format(new Date(timestampMs));
    }

    /**
     * Formats date header text separating message groups (e.g., "Today", "Yesterday", "Monday, Oct 24, 2024").
     */
    @NonNull
    public static String formatDateHeader(long timestampMs) {
        return formatDateHeader(timestampMs, System.currentTimeMillis(), Locale.getDefault());
    }

    @NonNull
    public static String formatDateHeader(long timestampMs, long nowMs, @NonNull Locale locale) {
        if (timestampMs <= 0) {
            return "";
        }

        Calendar then = Calendar.getInstance(locale);
        then.setTimeInMillis(timestampMs);

        Calendar now = Calendar.getInstance(locale);
        now.setTimeInMillis(nowMs);

        int thenYear = then.get(Calendar.YEAR);
        int nowYear = now.get(Calendar.YEAR);
        int thenDayOfYear = then.get(Calendar.DAY_OF_YEAR);
        int nowDayOfYear = now.get(Calendar.DAY_OF_YEAR);

        if (thenYear == nowYear && thenDayOfYear == nowDayOfYear) {
            return "Today";
        }

        long oneDayMs = 24L * 60 * 60 * 1000;
        Calendar yesterday = Calendar.getInstance(locale);
        yesterday.setTimeInMillis(nowMs - oneDayMs);
        if (thenYear == yesterday.get(Calendar.YEAR) && thenDayOfYear == yesterday.get(Calendar.DAY_OF_YEAR)) {
            return "Yesterday";
        }

        SimpleDateFormat headerFormat;
        if (thenYear == nowYear) {
            headerFormat = new SimpleDateFormat("EEEE, MMM d", locale);
        } else {
            headerFormat = new SimpleDateFormat("EEEE, MMM d, yyyy", locale);
        }
        return headerFormat.format(new Date(timestampMs));
    }

    /**
     * Returns a normalized day key (YYYYMMDD) for grouping messages by day.
     */
    public static long getDayKey(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return year * 10000L + month * 100L + day;
    }
}
