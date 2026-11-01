package com.nextgen.messages.smartinbox.engine;

import androidx.annotation.Nullable;

/**
 * Classifier identifying operational, service, logistics, appointment, and business messages.
 */
public final class BusinessClassifier {

    private static final String[] BUSINESS_PATTERNS_EN = {
            "out for delivery", "order confirmed", "package arriving", "has been shipped",
            "tracking number", "courier", "booking confirmed", "appointment scheduled",
            "flight", "pnr", "bill generated", "due date is", "recharge successful",
            "plan activated", "service request", "ticket confirmed", "arriving today",
            "dispatched", "shipped", "will arrive", "delivered", "delivery"
    };

    private static final String[] BUSINESS_PATTERNS_BN = {
            "ডেলিভারি", "অর্ডার নিশ্চিত", "ট্র্যাকিং", "বিল প্রস্তুত", "রিচার্জ সফল", "বুকিং নিশ্চিত"
    };

    private static final String[] BUSINESS_PATTERNS_HI = {
            "डिलीवरी", "ऑर्डर कन्फर्म", "ट्रैकिंग", "बिल", "रिचार्ज सफल", "बुकिंग कन्फर्म"
    };

    private BusinessClassifier() {}

    @Nullable
    public static ClassificationResult classify(@Nullable String body, @Nullable String sender) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }

        String lowerBody = body.toLowerCase();

        for (String pattern : BUSINESS_PATTERNS_EN) {
            if (lowerBody.contains(pattern)) {
                return new ClassificationResult("business", 0.75f, "ENGINE", "Detected business or service notification (English)");
            }
        }

        for (String pattern : BUSINESS_PATTERNS_BN) {
            if (lowerBody.contains(pattern)) {
                return new ClassificationResult("business", 0.75f, "ENGINE", "Detected business or service notification (Bengali)");
            }
        }

        for (String pattern : BUSINESS_PATTERNS_HI) {
            if (lowerBody.contains(pattern)) {
                return new ClassificationResult("business", 0.75f, "ENGINE", "Detected business or service notification (Hindi)");
            }
        }

        return null;
    }
}
