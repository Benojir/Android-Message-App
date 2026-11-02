package com.nextgen.messages.security;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nextgen.messages.data.local.AppPreferences;
import com.nextgen.messages.data.repository.BlockedSenderRepository;
import com.nextgen.messages.smartinbox.db.SmartInboxDao;
import com.nextgen.messages.smartinbox.db.SmartInboxDatabase;
import com.nextgen.messages.smartinbox.db.SpamFeedbackEntity;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 100% on-device, privacy-preserving spam protection engine.
 * Combines user blocklists, user feedback history, link security analysis,
 * and conservative text heuristics without sending any message content off-device.
 */
public class SpamProtectionEngine {

    private static volatile SpamProtectionEngine instance;
    private final Context context;
    private final SmartInboxDao dao;
    private final BlockedSenderRepository blockedRepo;
    private final AppPreferences preferences;

    private static final List<String> HIGH_CONFIDENCE_SPAM_TRIGGERS = Arrays.asList(
            "won lottery", "congratulations you won", "claim prize", "claim your prize",
            "cash reward", "free bonus", "instant loan approved without documents",
            "urgent kyc update required or account will be suspended",
            "electricity bill overdue power disconnected", "credit card limit increased click",
            "earn money working from home daily", "rummy bonus", "casino bonus",
            // Bengali
            "লটারি জিতেছেন", "পুরস্কার দাবি করুন", "বিনামূল্যে বোনাস", "বিদ্যুৎ বিল বকেয়া",
            // Hindi
            "लॉटरी जीती", "इनाम पाएं", "मुफ्त बोनस", "बिजली बिल बकाया", "घर बैठे पैसे कमाएं"
    );

    public static class Evaluation {
        private final boolean isSpam;
        private final float score;
        @NonNull
        private final String reason;
        private final boolean hasSuspiciousLink;
        @Nullable
        private final String linkReason;

        public Evaluation(
                boolean isSpam,
                float score,
                @NonNull String reason,
                boolean hasSuspiciousLink,
                @Nullable String linkReason
        ) {
            this.isSpam = isSpam;
            this.score = score;
            this.reason = reason;
            this.hasSuspiciousLink = hasSuspiciousLink;
            this.linkReason = linkReason;
        }

        public static Evaluation notSpam(@NonNull String reason) {
            return new Evaluation(false, 0.0f, reason, false, null);
        }

        public static Evaluation spam(float score, @NonNull String reason, boolean hasSuspiciousLink, @Nullable String linkReason) {
            return new Evaluation(true, score, reason, hasSuspiciousLink, linkReason);
        }

        public boolean isSpam() {
            return isSpam;
        }

        public float getScore() {
            return score;
        }

        @NonNull
        public String getReason() {
            return reason;
        }

        public boolean hasSuspiciousLink() {
            return hasSuspiciousLink;
        }

        @Nullable
        public String getLinkReason() {
            return linkReason;
        }
    }

    private SpamProtectionEngine(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.dao = SmartInboxDatabase.getInstance(this.context).smartInboxDao();
        this.blockedRepo = BlockedSenderRepository.getInstance(this.context);
        this.preferences = AppPreferences.getInstance(this.context);
    }

    public static SpamProtectionEngine getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SpamProtectionEngine.class) {
                if (instance == null) {
                    instance = new SpamProtectionEngine(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Evaluates whether an incoming or existing conversation / message constitutes spam.
     */
    @NonNull
    public Evaluation evaluate(@Nullable String address, @Nullable String body) {
        if (!preferences.isSpamProtectionEnabled()) {
            return Evaluation.notSpam("Spam protection disabled in settings");
        }

        String normalizedAddress = BlockedSenderRepository.normalizeIdentifier(address);

        // 1. Direct blocklist check (highest precedence)
        if (blockedRepo.isBlocked(address)) {
            return Evaluation.spam(1.0f, "Sender is blocked by you", false, null);
        }

        // 2. User feedback check (explicit manual report vs not-spam override)
        if (!normalizedAddress.isEmpty()) {
            List<SpamFeedbackEntity> feedbackList = dao.getFeedbackForSender(normalizedAddress);
            if (!feedbackList.isEmpty()) {
                SpamFeedbackEntity latest = feedbackList.get(0);
                if (SpamFeedbackEntity.TYPE_NOT_SPAM.equals(latest.getFeedbackType())) {
                    return Evaluation.notSpam("Explicitly marked as not spam by user");
                } else if (SpamFeedbackEntity.TYPE_REPORT_SPAM.equals(latest.getFeedbackType())) {
                    return Evaluation.spam(0.95f, "Previously reported as spam", false, null);
                }
            }
        }

        // 3. Suspicious link & phishing analysis
        SuspiciousLinkAnalyzer.AnalysisResult linkResult = SuspiciousLinkAnalyzer.analyze(body);
        if (linkResult.isSuspicious()) {
            return Evaluation.spam(
                    0.90f,
                    linkResult.getWarningReason() != null ? linkResult.getWarningReason() : "Suspicious link detected",
                    true,
                    linkResult.getWarningReason()
            );
        }

        // 4. Text heuristic triggers
        if (body != null && !body.trim().isEmpty()) {
            String lowerBody = body.toLowerCase(Locale.ROOT);
            for (String trigger : HIGH_CONFIDENCE_SPAM_TRIGGERS) {
                if (lowerBody.contains(trigger)) {
                    return Evaluation.spam(0.85f, "Detected known scam/spam pattern: " + trigger, false, null);
                }
            }

            // Balanced sensitivity checks
            String sensitivity = preferences.getSpamSensitivity();
            if ("BALANCED".equalsIgnoreCase(sensitivity)) {
                if (lowerBody.contains("lottery") || lowerBody.contains("urgent kyc") || lowerBody.contains("claim reward")) {
                    return Evaluation.spam(0.75f, "Suspicious promotional or credential trigger", false, null);
                }
            }
        }

        return Evaluation.notSpam("No spam signals detected");
    }
}
