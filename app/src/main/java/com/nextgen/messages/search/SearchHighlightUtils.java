package com.nextgen.messages.search;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.nextgen.messages.R;
import java.util.Locale;

/**
 * Utility for applying search query highlight spans across English, Bengali, Hindi,
 * and Unicode text with Material Design 3 theme compatibility.
 */
public class SearchHighlightUtils {

    @ColorInt
    public static int getDefaultHighlightColor(@NonNull Context context) {
        // Vibrant yellow-amber for search match highlight
        return Color.parseColor("#FFE082");
    }

    @ColorInt
    public static int getDefaultTextColor(@NonNull Context context) {
        return Color.parseColor("#1C1B1F");
    }

    /**
     * Finds the start and end indices of all occurrences of query in sourceText.
     */
    @NonNull
    public static java.util.List<int[]> findMatchIndices(@Nullable CharSequence sourceText, @Nullable String query) {
        java.util.List<int[]> matches = new java.util.ArrayList<>();
        if (sourceText == null || query == null || query.trim().isEmpty()) {
            return matches;
        }
        String rawSource = sourceText.toString();
        String lowerSource = rawSource.toLowerCase(Locale.ROOT);
        String lowerQuery = query.trim().toLowerCase(Locale.ROOT);

        int queryLength = lowerQuery.length();
        int startIndex = 0;

        while ((startIndex = lowerSource.indexOf(lowerQuery, startIndex)) >= 0) {
            int endIndex = startIndex + queryLength;
            matches.add(new int[]{startIndex, endIndex});
            startIndex = endIndex;
        }
        return matches;
    }

    /**
     * Highlights all occurrences of the query inside the source text.
     * Preserves original Unicode character casing, diacritics, and complex scripts (Bengali, Hindi).
     */
    @NonNull
    public static SpannableString highlight(
            @Nullable CharSequence sourceText,
            @Nullable String query,
            @ColorInt int highlightBgColor,
            @ColorInt int highlightTextColor
    ) {
        if (sourceText == null) {
            return new SpannableString("");
        }
        SpannableString spannable = new SpannableString(sourceText);
        java.util.List<int[]> matches = findMatchIndices(sourceText, query);
        for (int[] match : matches) {
            spannable.setSpan(
                    new BackgroundColorSpan(highlightBgColor),
                    match[0],
                    match[1],
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            spannable.setSpan(
                    new ForegroundColorSpan(highlightTextColor),
                    match[0],
                    match[1],
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return spannable;
    }

    /**
     * Extracts a contextual snippet around the first occurrence of query in long texts.
     */
    @NonNull
    public static String getContextualSnippet(@NonNull String body, @Nullable String query, int maxSnippetLength) {
        if (query == null || query.trim().isEmpty() || body.length() <= maxSnippetLength) {
            return body;
        }

        String lowerBody = body.toLowerCase(Locale.ROOT);
        String lowerQuery = query.trim().toLowerCase(Locale.ROOT);
        int matchIndex = lowerBody.indexOf(lowerQuery);

        if (matchIndex < 0) {
            return body.length() > maxSnippetLength ? body.substring(0, maxSnippetLength) + "…" : body;
        }

        int half = maxSnippetLength / 2;
        int start = Math.max(0, matchIndex - half);
        int end = Math.min(body.length(), matchIndex + lowerQuery.length() + half);

        String prefix = start > 0 ? "…" : "";
        String suffix = end < body.length() ? "…" : "";

        return prefix + body.substring(start, end).trim() + suffix;
    }
}
