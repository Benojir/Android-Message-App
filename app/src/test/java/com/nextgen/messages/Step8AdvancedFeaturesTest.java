package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.data.repository.BlockedSenderRepository;
import com.nextgen.messages.search.SearchFilter;
import com.nextgen.messages.search.SearchHighlightUtils;
import com.nextgen.messages.security.SuspiciousLinkAnalyzer;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class Step8AdvancedFeaturesTest {

    @Test
    public void testNormalizedIdentifierPhoneAndAlphanumeric() {
        // Phone numbers with country code, formatting, spaces
        assertEquals("9876543210", BlockedSenderRepository.normalizeIdentifier("9876543210"));
        assertEquals("919876543210", BlockedSenderRepository.normalizeIdentifier("+91 98765-43210"));
        assertEquals("1234567890", BlockedSenderRepository.normalizeIdentifier("(123) 456-7890"));

        // Alphanumeric Sender IDs (e.g. Indian commercial headers, corporate SMS)
        assertEquals("VK-HDFCBK", BlockedSenderRepository.normalizeIdentifier("VK-HDFCBK"));
        assertEquals("AM-GOOGLE", BlockedSenderRepository.normalizeIdentifier("am-google"));
        assertEquals("AXISBK", BlockedSenderRepository.normalizeIdentifier(" axisbk "));
    }

    @Test
    public void testSuspiciousLinkAnalyzerSafeUrls() {
        SuspiciousLinkAnalyzer.AnalysisResult r1 = SuspiciousLinkAnalyzer.analyze("Check our website at https://google.com for more info.");
        assertFalse(r1.isSuspicious());

        SuspiciousLinkAnalyzer.AnalysisResult r2 = SuspiciousLinkAnalyzer.analyze("Read the documentation at https://developer.android.com/guide.");
        assertFalse(r2.isSuspicious());

        SuspiciousLinkAnalyzer.AnalysisResult r3 = SuspiciousLinkAnalyzer.analyze("No links present in this normal conversation message.");
        assertFalse(r3.isSuspicious());
    }

    @Test
    public void testSuspiciousLinkAnalyzerIpAddresses() {
        SuspiciousLinkAnalyzer.AnalysisResult r1 = SuspiciousLinkAnalyzer.analyze("Urgent: Login to verify your account at http://192.168.1.100/login immediately.");
        assertTrue(r1.isSuspicious());
        assertEquals(SuspiciousLinkAnalyzer.WARNING_IP_ADDRESS, r1.getWarningType());
        assertTrue(r1.hasDetectedUrl());
        assertEquals("http://192.168.1.100/login", r1.getDetectedUrl());

        SuspiciousLinkAnalyzer.AnalysisResult r2 = SuspiciousLinkAnalyzer.analyze("Click http://45.33.32.156:8080/portal to claim prize.");
        assertTrue(r2.isSuspicious());
    }

    @Test
    public void testSuspiciousLinkAnalyzerPunycode() {
        SuspiciousLinkAnalyzer.AnalysisResult r = SuspiciousLinkAnalyzer.analyze("Visit your bank portal: https://xn--pple-43d.com/secure to verify identity.");
        assertTrue(r.isSuspicious());
        assertEquals(SuspiciousLinkAnalyzer.WARNING_PUNYCODE, r.getWarningType());
    }

    @Test
    public void testSuspiciousLinkAnalyzerPhishingUrgencyEnglish() {
        SuspiciousLinkAnalyzer.AnalysisResult r = SuspiciousLinkAnalyzer.analyze(
                "Dear customer, your bank account will be blocked within 24 hours. Update your KYC urgently at http://bit.ly/bank-kyc-update"
        );
        assertTrue(r.isSuspicious());
        assertEquals(SuspiciousLinkAnalyzer.WARNING_PHISHING_URGENCY, r.getWarningType());
    }

    @Test
    public void testSuspiciousLinkAnalyzerPhishingUrgencyBengali() {
        SuspiciousLinkAnalyzer.AnalysisResult r = SuspiciousLinkAnalyzer.analyze(
                "জরুরি বিজ্ঞপ্তি: আপনার একাউন্ট সাসপেন্ড করা হবে। অবিলম্বে কেওয়াইসি আপডেট করতে লিঙ্কে ক্লিক করুন https://tinyurl.com/update-bengali"
        );
        assertTrue(r.isSuspicious());
        assertEquals(SuspiciousLinkAnalyzer.WARNING_PHISHING_URGENCY, r.getWarningType());
    }

    @Test
    public void testSuspiciousLinkAnalyzerPhishingUrgencyHindi() {
        SuspiciousLinkAnalyzer.AnalysisResult r = SuspiciousLinkAnalyzer.analyze(
                "अति आवश्यक सूचना: आपका बैंक खाता बंद कर दिया गया है। तुरंत पैन लिंक करने हेतु यहां क्लिक करें http://is.gd/verify-pan"
        );
        assertTrue(r.isSuspicious());
        assertEquals(SuspiciousLinkAnalyzer.WARNING_PHISHING_URGENCY, r.getWarningType());
    }

    @Test
    public void testSearchFilterActiveDetection() {
        SearchFilter filter = new SearchFilter();
        assertFalse(filter.hasActiveFilters());

        filter.setQuery("test query");
        assertTrue(filter.hasActiveFilters());

        filter.setQuery(null);
        assertFalse(filter.hasActiveFilters());

        filter.setOnlyStarred(true);
        assertTrue(filter.hasActiveFilters());

        filter.setOnlyStarred(false);
        filter.setStartDate(1000L);
        assertTrue(filter.hasActiveFilters());
    }

    @Test
    public void testSearchHighlightingEnglish() {
        String text = "Your verification OTP is 482910. Do not share your OTP with anyone.";
        String query = "OTP";

        List<int[]> matches = SearchHighlightUtils.findMatchIndices(text, query);
        assertEquals(2, matches.size());
        assertEquals(18, matches.get(0)[0]);
        assertEquals(21, matches.get(0)[1]);
        assertEquals("OTP", text.substring(matches.get(0)[0], matches.get(0)[1]));

        assertEquals(51, matches.get(1)[0]);
        assertEquals(54, matches.get(1)[1]);
        assertEquals("OTP", text.substring(matches.get(1)[0], matches.get(1)[1]));
    }

    @Test
    public void testSearchHighlightingBengali() {
        String text = "আপনার কেওয়াইসি সফলভাবে সম্পন্ন হয়েছে।";
        String query = "কেওয়াইসি";

        List<int[]> matches = SearchHighlightUtils.findMatchIndices(text, query);
        assertEquals(1, matches.size());
        assertEquals("কেওয়াইসি", text.substring(matches.get(0)[0], matches.get(0)[1]));
    }

    @Test
    public void testSearchHighlightingHindi() {
        String text = "प्रिय ग्राहक, आपका खाता सक्रिय है।";
        String query = "खाता";

        List<int[]> matches = SearchHighlightUtils.findMatchIndices(text, query);
        assertEquals(1, matches.size());
        assertEquals("खाता", text.substring(matches.get(0)[0], matches.get(0)[1]));
    }

    @Test
    public void testContextualSnippetGeneration() {
        String longText = "This is a long introductory text that leads to an important transaction ID: TXN123456789 which was processed successfully yesterday.";
        String snippet = SearchHighlightUtils.getContextualSnippet(longText, "TXN123456789", 40);

        assertNotNull(snippet);
        assertTrue(snippet.contains("TXN123456789"));
    }

    @Test
    public void testConversationMetadataEnrichment() {
        Conversation conv = new Conversation(
                101L,
                "+919876543210",
                "Alice Smith",
                null,
                "Hello Alice",
                1700000000L,
                0,
                5
        );

        assertFalse(conv.isPinned());
        assertFalse(conv.isArchived());
        assertFalse(conv.isBlocked());
        assertFalse(conv.isSpam());

        Conversation enriched = conv.withMetadata(true, 1700000100L, false, 0L, false, false);
        assertTrue(enriched.isPinned());
        assertEquals(1700000100L, enriched.getPinnedAt());
        assertFalse(enriched.isArchived());

        Conversation archived = conv.withMetadata(false, 0L, true, 1700000200L, false, false);
        assertFalse(archived.isPinned());
        assertTrue(archived.isArchived());
        assertEquals(1700000200L, archived.getArchivedAt());
    }

    @Test
    public void testSmsMessageCompositeKeysAndStep8State() {
        SmsMessage sms = new SmsMessage(
                42L,
                10L,
                "+919876543210",
                "Normal message",
                1700000000L,
                1700000000L,
                SmsMessage.TYPE_INCOMING,
                true,
                true,
                SmsMessage.STATUS_COMPLETE,
                0,
                1
        );

        assertEquals("SMS_42", sms.getMessageKey());
        assertFalse(sms.isStarred());
        assertFalse(sms.hasSuspiciousLink());

        SmsMessage enriched = sms.withStep8State(true, true, "IP address detected in message body");
        assertEquals("SMS_42", enriched.getMessageKey());
        assertTrue(enriched.isStarred());
        assertTrue(enriched.hasSuspiciousLink());
        assertEquals("IP address detected in message body", enriched.getWarningReason());
    }
}
