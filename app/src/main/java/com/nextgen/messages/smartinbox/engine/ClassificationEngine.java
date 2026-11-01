package com.nextgen.messages.smartinbox.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.model.ContactInfo;
import com.nextgen.messages.smartinbox.db.ClassificationRuleEntity;
import com.nextgen.messages.smartinbox.db.MessageClassificationEntity;
import com.nextgen.messages.smartinbox.db.SenderCategoryEntity;
import java.util.List;

/**
 * Deterministic on-device classification pipeline orchestrating manual overrides,
 * user-defined rules, multilingual regex/keyword matchers, and sender heuristics.
 */
public final class ClassificationEngine {

    private ClassificationEngine() {}

    @NonNull
    public static ClassificationResult classify(
            @Nullable String body,
            @Nullable String sender,
            @Nullable ContactInfo contactInfo,
            @Nullable MessageClassificationEntity manualOverride,
            @Nullable SenderCategoryEntity senderOverride,
            @Nullable List<ClassificationRuleEntity> customRules
    ) {
        // Priority 1: User manual override on the specific message
        if (manualOverride != null && MessageClassificationEntity.SOURCE_MANUAL.equals(manualOverride.getClassificationSource())) {
            return new ClassificationResult(
                    manualOverride.getCategoryKey(),
                    1.0f,
                    "MANUAL",
                    "User manual classification override"
            );
        }

        // Priority 2: User sender-level manual override
        if (senderOverride != null && senderOverride.isManualOverride()) {
            return new ClassificationResult(
                    senderOverride.getCategoryKey(),
                    1.0f,
                    "MANUAL",
                    "Assigned via sender rule (" + senderOverride.getSenderIdentifier() + ")"
            );
        }

        // Priority 3: User custom classification rules (evaluated in ascending priority order)
        if (customRules != null && !customRules.isEmpty()) {
            for (ClassificationRuleEntity rule : customRules) {
                if (!rule.isEnabled()) continue;
                if (matchesRule(rule, body, sender)) {
                    return new ClassificationResult(
                            rule.getTargetCategoryKey(),
                            0.95f,
                            "RULE",
                            "Matched custom rule: " + rule.getRuleName()
                    );
                }
            }
        }

        // Priority 4: High-confidence OTP detection
        ClassificationResult otpResult = OtpClassifier.classify(body, sender);
        if (otpResult != null) {
            return otpResult;
        }

        // Priority 5: High-confidence Transaction detection
        ClassificationResult txnResult = TransactionClassifier.classify(body, sender);
        if (txnResult != null) {
            return txnResult;
        }

        // Priority 6: Conservative Spam detection
        ClassificationResult spamResult = SpamClassifier.classify(body, sender);
        if (spamResult != null) {
            return spamResult;
        }

        // Priority 7: Promotional detection
        ClassificationResult promoResult = PromotionsClassifier.classify(body, sender);
        if (promoResult != null) {
            return promoResult;
        }

        // Priority 8: Business service alerts
        ClassificationResult bizResult = BusinessClassifier.classify(body, sender);
        if (bizResult != null) {
            return bizResult;
        }

        // Priority 9: Personal contact signals
        ClassificationResult personalResult = PersonalClassifier.classify(body, sender, contactInfo);
        if (personalResult != null) {
            return personalResult;
        }

        // Priority 10: Fallback to "other"
        return new ClassificationResult("other", 0.50f, "ENGINE", "Unclassified message");
    }

    private static boolean matchesRule(
            @NonNull ClassificationRuleEntity rule,
            @Nullable String body,
            @Nullable String sender
    ) {
        String type = rule.getRuleType();
        String matchValue = rule.getMatchValue().trim().toLowerCase();

        if (ClassificationRuleEntity.TYPE_SENDER_EQUALS.equals(type)) {
            if (sender == null) return false;
            return sender.trim().equalsIgnoreCase(rule.getMatchValue().trim());
        } else if (ClassificationRuleEntity.TYPE_SENDER_CONTAINS.equals(type)) {
            if (sender == null) return false;
            return sender.toLowerCase().contains(matchValue);
        } else if (ClassificationRuleEntity.TYPE_BODY_CONTAINS.equals(type)) {
            if (body == null) return false;
            return body.toLowerCase().contains(matchValue);
        } else if (ClassificationRuleEntity.TYPE_BODY_STARTS_WITH.equals(type)) {
            if (body == null) return false;
            return body.toLowerCase().trim().startsWith(matchValue);
        }
        return false;
    }
}
