package com.nextgen.messages.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 100% on-device heuristic analyzer for detecting suspicious URLs, IP-address hosts,
 * punycode deception, and phishing credential-harvesting threats.
 */
public class SuspiciousLinkAnalyzer {

    public static final String WARNING_IP_ADDRESS = "IP_HOST";
    public static final String WARNING_PUNYCODE = "PUNYCODE_DOMAIN";
    public static final String WARNING_PHISHING_URGENCY = "PHISHING_SHORTENER";
    public static final String WARNING_SUSPICIOUS_TLD = "SUSPICIOUS_TLD";

    // Regex to match URLs (both http/https and bare www.)
    private static final Pattern URL_PATTERN = Pattern.compile(
            "\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,63}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))",
            Pattern.CASE_INSENSITIVE
    );

    // IPv4 host pattern e.g., http://192.168.1.1
    private static final Pattern IP_HOST_PATTERN = Pattern.compile(
            "^https?://(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?(/.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    // High-risk top-level domains frequently abused in SMS phishing
    private static final List<String> HIGH_RISK_TLDS = Arrays.asList(
            ".xyz", ".top", ".tk", ".ml", ".ga", ".cf", ".gq", ".buzz",
            ".work", ".rest", ".click", ".link", ".icu", ".cam", ".live"
    );

    // Generic URL shorteners often used to mask malicious destinations
    private static final List<String> SHORTENERS = Arrays.asList(
            "bit.ly", "tinyurl.com", "is.gd", "cutt.ly", "rb.gy", "t.co", "ow.ly", "shorturl.at"
    );

    // Urgent / Credential-harvesting phrases (English, Bengali, Hindi)
    private static final List<String> PHISHING_SIGNALS = Arrays.asList(
            "kyc", "pan card", "account blocked", "account suspended", "deactivated",
            "verify account", "verification required", "urgent action", "claim lottery",
            "reward points", "expire today", "click link to unlock", "enter otp",
            "enter password", "tax refund", "won prize", "lottery prize", "ebill pending",
            "electricity bill overdue", "power will be disconnected",
            // Bengali
            "কেওয়াইসি", "অ্যাকাউন্ট বন্ধ", "লটারি", "পুরস্কার", "ওটিপি", "টাকা ফেরত", "বিদ্যুৎ বিল",
            // Hindi
            "केवाईसी", "खाता बंद", "लॉटरी", "इनाम", "ओटीपी", "रिफंड", "बिजली बिल"
    );

    public static class AnalysisResult {
        private final boolean isSuspicious;
        @Nullable
        private final String detectedUrl;
        @Nullable
        private final String warningType;
        @Nullable
        private final String warningReason;

        public AnalysisResult(
                boolean isSuspicious,
                @Nullable String detectedUrl,
                @Nullable String warningType,
                @Nullable String warningReason
        ) {
            this.isSuspicious = isSuspicious;
            this.detectedUrl = detectedUrl;
            this.warningType = warningType;
            this.warningReason = warningReason;
        }

        public static AnalysisResult safe() {
            return new AnalysisResult(false, null, null, null);
        }

        public static AnalysisResult suspicious(
                @NonNull String url,
                @NonNull String type,
                @NonNull String reason
        ) {
            return new AnalysisResult(true, url, type, reason);
        }

        public boolean isSuspicious() {
            return isSuspicious;
        }

        public boolean hasDetectedUrl() {
            return detectedUrl != null && !detectedUrl.trim().isEmpty();
        }

        @Nullable
        public String getDetectedUrl() {
            return detectedUrl;
        }

        @Nullable
        public String getWarningType() {
            return warningType;
        }

        @Nullable
        public String getWarningReason() {
            return warningReason;
        }
    }

    /**
     * Extracts all URLs from the given text.
     */
    @NonNull
    public static List<String> extractUrls(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }
        return urls;
    }

    /**
     * Evaluates a message body for suspicious links and returns an analysis result.
     */
    @NonNull
    public static AnalysisResult analyze(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return AnalysisResult.safe();
        }

        List<String> urls = extractUrls(text);
        if (urls.isEmpty()) {
            return AnalysisResult.safe();
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        boolean hasPhishingPhrases = false;
        String matchedPhrase = null;
        for (String phrase : PHISHING_SIGNALS) {
            if (lowerText.contains(phrase)) {
                hasPhishingPhrases = true;
                matchedPhrase = phrase;
                break;
            }
        }

        for (String rawUrl : urls) {
            String url = rawUrl.toLowerCase(Locale.ROOT);
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }

            // 1. IP address as host check (e.g., http://192.168.1.1)
            if (IP_HOST_PATTERN.matcher(url).matches()) {
                return AnalysisResult.suspicious(
                        rawUrl,
                        "IP_HOST",
                        "This link uses a raw IP address instead of a recognized domain name."
                );
            }

            // 2. Punycode check (internationalized domain name deception)
            if (url.contains("xn--")) {
                return AnalysisResult.suspicious(
                        rawUrl,
                        "PUNYCODE_DOMAIN",
                        "This link uses deceptive lookalike characters (Punycode)."
                );
            }

            // 3. Shortener combined with urgency/phishing phrases
            for (String shortener : SHORTENERS) {
                if (url.contains(shortener)) {
                    if (hasPhishingPhrases) {
                        return AnalysisResult.suspicious(
                                rawUrl,
                                "PHISHING_SHORTENER",
                                "Shortened link combined with urgent account verification or payment request (" + matchedPhrase + ")."
                        );
                    }
                }
            }

            // 4. High-risk TLD check
            for (String tld : HIGH_RISK_TLDS) {
                if (url.contains(tld + "/") || url.endsWith(tld)) {
                    if (hasPhishingPhrases) {
                        return AnalysisResult.suspicious(
                                rawUrl,
                                "SUSPICIOUS_TLD",
                                "Potentially dangerous domain (" + tld + ") requesting urgent action."
                        );
                    }
                }
            }

            // 5. Deceptive subdomain spoofing (e.g. sbi.co.in.fake-site.com)
            if (url.contains(".bank.") || url.contains("login.") || url.contains("signin.") || url.contains("secure.")) {
                if (hasPhishingPhrases) {
                    return AnalysisResult.suspicious(
                            rawUrl,
                            "MISLEADING_DOMAIN",
                            "This link may be an unauthorized site imitating a financial or secure service."
                    );
                }
            }
        }

        return AnalysisResult.safe();
    }
}
