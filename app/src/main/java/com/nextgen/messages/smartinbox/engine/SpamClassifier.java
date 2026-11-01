package com.nextgen.messages.smartinbox.engine;

import androidx.annotation.Nullable;

/**
 * Conservative classifier identifying suspicious scam, phishing, or lottery spam patterns.
 * Never flags legitimate OTP or banking alerts as spam.
 */
public final class SpamClassifier {

    private static final String[] SPAM_PATTERNS = {
            "won lottery", "lottery prize", "claim lottery", "you have won prize",
            "unclaimed reward", "unclaimed funds", "claim your millions", "congratulations! you won",
            "account will be blocked", "kyc suspended", "pan card blocked", "click link to unblock",
            "call immediately to avoid penalty", "arrest warrant issued", "urgent kyc update required",
            "download .apk to update"
    };

    private SpamClassifier() {}

    @Nullable
    public static ClassificationResult classify(@Nullable String body, @Nullable String sender) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }

        String lowerBody = body.toLowerCase();

        for (String pattern : SPAM_PATTERNS) {
            if (lowerBody.contains(pattern)) {
                return new ClassificationResult("spam", 0.80f, "ENGINE", "Conservative spam signal detected: suspicious urgency/reward pattern");
            }
        }

        // Suspicious shortened links combined with sensitive account keywords
        if ((lowerBody.contains("bit.ly/") || lowerBody.contains("tinyurl.com/") || lowerBody.contains("t.co/"))
                && (lowerBody.contains("kyc") || lowerBody.contains("bank") || lowerBody.contains("pan") || lowerBody.contains("suspend"))) {
            return new ClassificationResult("spam", 0.80f, "ENGINE", "Conservative spam signal detected: shortened link with account alert keywords");
        }

        return null;
    }
}
