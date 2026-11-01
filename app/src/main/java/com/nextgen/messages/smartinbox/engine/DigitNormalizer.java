package com.nextgen.messages.smartinbox.engine;

import androidx.annotation.Nullable;

/**
 * Text utility normalizing multi-lingual numeral scripts (Bengali and Devanagari digits)
 * into standard Arabic digits (0-9) to enable robust multi-script number recognition.
 */
public final class DigitNormalizer {

    private DigitNormalizer() {}

    /**
     * Replaces Bengali and Devanagari numerals with ASCII digits 0-9.
     */
    @Nullable
    public static String normalize(@Nullable String text) {
        return normalizeDigits(text);
    }

    /**
     * Replaces Bengali and Devanagari numerals with ASCII digits 0-9.
     */
    @Nullable
    public static String normalizeDigits(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Bengali numerals: \u09E6 to \u09EF ('০' to '৯')
            if (c >= '\u09E6' && c <= '\u09EF') {
                sb.append((char) ('0' + (c - '\u09E6')));
            }
            // Devanagari numerals: \u0966 to \u096F ('०' to '९')
            else if (c >= '\u0966' && c <= '\u096F') {
                sb.append((char) ('0' + (c - '\u0966')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
