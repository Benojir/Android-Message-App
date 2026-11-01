package com.nextgen.messages.smartinbox.engine;

import androidx.annotation.Nullable;
import com.nextgen.messages.data.model.ContactInfo;

/**
 * Classifier identifying messages from personal contacts or ordinary phone numbers.
 */
public final class PersonalClassifier {

    private PersonalClassifier() {}

    @Nullable
    public static ClassificationResult classify(
            @Nullable String body,
            @Nullable String sender,
            @Nullable ContactInfo contactInfo
    ) {
        // 1. Saved contact signal
        if (contactInfo != null && contactInfo.hasName()) {
            return new ClassificationResult("personal", 0.85f, "ENGINE", "Sender is in saved contacts (" + contactInfo.getDisplayName() + ")");
        }

        // 2. Standard phone number signal (digits, no corporate alphanumeric header like "AD-SBIINB")
        if (sender != null) {
            String cleanSender = sender.replaceAll("[\\s\\-\\(\\)\\+]", "");
            boolean isAllDigits = cleanSender.matches("\\d+");
            if (isAllDigits && cleanSender.length() >= 7) {
                return new ClassificationResult("personal", 0.70f, "ENGINE", "Sender is an ordinary phone number");
            }
        }

        return null;
    }
}
