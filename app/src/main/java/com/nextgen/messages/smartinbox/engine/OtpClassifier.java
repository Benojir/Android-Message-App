package com.nextgen.messages.smartinbox.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dedicated classifier detecting authentication verification codes and OTP patterns
 * in English, Bengali, and Hindi with multi-script digit support.
 */
public final class OtpClassifier {

    // Common authentication and OTP keywords across English, Bengali, and Hindi
    private static final String[] OTP_KEYWORDS_EN = {
            "otp", "verification code", "security code", "passcode", "one time password",
            "login code", "auth code", "valid for", "do not share", "never share your code",
            "is your code", "your code is", "secret code"
    };

    private static final String[] OTP_KEYWORDS_BN = {
            "ওটিপি", "otp", "ভেরিফিকেশন কোড", "পিন", "কোড", "কাউকে জানাবেন না", "বৈধ",
            "লগইন কোড", "ওয়ান টাইম পাসওয়ার্ড", "গোপন কোড", "শেয়ার করবেন না"
    };

    private static final String[] OTP_KEYWORDS_HI = {
            "ओटीपी", "otp", "सत्यापन कोड", "सुरक्षा कोड", "पासकोड", "साझा न करें", "वैध",
            "लॉगिन कोड", "वन टाइम पासवर्ड", "गुप्त कोड", "किसी के साथ साझा न करें"
    };

    // Regex matching 4 to 8 consecutive digits (after digit normalization)
    private static final Pattern CODE_PATTERN = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)");

    private OtpClassifier() {}

    @Nullable
    public static ClassificationResult classify(@Nullable String body, @Nullable String sender) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }

        String lowerBody = body.toLowerCase();
        String normalizedBody = DigitNormalizer.normalizeDigits(lowerBody);
        if (normalizedBody == null) {
            return null;
        }

        // 1. Check for OTP keywords
        boolean hasKeyword = false;
        String matchedLanguage = "English";

        for (String kw : OTP_KEYWORDS_EN) {
            if (normalizedBody.contains(kw)) {
                hasKeyword = true;
                break;
            }
        }

        if (!hasKeyword) {
            for (String kw : OTP_KEYWORDS_BN) {
                if (normalizedBody.contains(kw)) {
                    hasKeyword = true;
                    matchedLanguage = "Bengali";
                    break;
                }
            }
        }

        if (!hasKeyword) {
            for (String kw : OTP_KEYWORDS_HI) {
                if (normalizedBody.contains(kw)) {
                    hasKeyword = true;
                    matchedLanguage = "Hindi";
                    break;
                }
            }
        }

        if (!hasKeyword) {
            return null;
        }

        // 2. Check for numeric verification code presence (4-8 digits)
        Matcher matcher = CODE_PATTERN.matcher(normalizedBody);
        boolean hasCode = matcher.find();

        if (hasCode) {
            String explanation = "Detected OTP authentication pattern (" + matchedLanguage + ")";
            return new ClassificationResult("otp", 0.95f, "ENGINE", explanation);
        }

        return null;
    }
}
