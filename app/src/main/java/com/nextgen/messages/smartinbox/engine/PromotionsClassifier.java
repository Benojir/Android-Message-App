package com.nextgen.messages.smartinbox.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Dedicated classifier identifying marketing offers, sales, discounts, and promotional campaigns
 * across English, Bengali, and Hindi.
 */
public final class PromotionsClassifier {

    private static final String[] PROMO_PATTERNS_EN = {
            "% off", "percent off", "discount", "sale", "flat off", "coupon code",
            "promo code", "hurry", "limited period offer", "limited time offer", "cashback offer",
            "buy 1 get 1", "shop now", "free delivery", "exclusive deal", "special offer",
            "festive offer", "mega sale", "save up to", "use code"
    };

    private static final String[] PROMO_PATTERNS_BN = {
            "ছাড়", "অফার", "ডিসকাউন্ট", "সেল", "কুপন", "সীমিত সময়ের অফার", "কেনাকাটা করুন", "বিশেষ ছাড়"
    };

    private static final String[] PROMO_PATTERNS_HI = {
            "छूट", "ऑफर", "डिस्काउंट", "सेल", "कूपन", "सीमित समय का ऑफर", "खरीदारी करें", "विशेष छूट"
    };

    private PromotionsClassifier() {}

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

        for (String pattern : PROMO_PATTERNS_EN) {
            if (normalizedBody.contains(pattern)) {
                return new ClassificationResult("promotions", 0.85f, "ENGINE", "Detected promotional discount or offer (English)");
            }
        }

        for (String pattern : PROMO_PATTERNS_BN) {
            if (normalizedBody.contains(pattern)) {
                return new ClassificationResult("promotions", 0.85f, "ENGINE", "Detected promotional discount or offer (Bengali)");
            }
        }

        for (String pattern : PROMO_PATTERNS_HI) {
            if (normalizedBody.contains(pattern)) {
                return new ClassificationResult("promotions", 0.85f, "ENGINE", "Detected promotional discount or offer (Hindi)");
            }
        }

        return null;
    }
}
