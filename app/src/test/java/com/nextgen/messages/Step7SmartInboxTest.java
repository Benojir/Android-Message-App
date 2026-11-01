package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.nextgen.messages.data.model.ContactInfo;
import com.nextgen.messages.smartinbox.db.ClassificationRuleEntity;
import com.nextgen.messages.smartinbox.db.MessageCategoryEntity;
import com.nextgen.messages.smartinbox.db.MessageClassificationEntity;
import com.nextgen.messages.smartinbox.db.SenderCategoryEntity;
import com.nextgen.messages.smartinbox.engine.BusinessClassifier;
import com.nextgen.messages.smartinbox.engine.ClassificationEngine;
import com.nextgen.messages.smartinbox.engine.ClassificationResult;
import com.nextgen.messages.smartinbox.engine.DigitNormalizer;
import com.nextgen.messages.smartinbox.engine.OtpClassifier;
import com.nextgen.messages.smartinbox.engine.PersonalClassifier;
import com.nextgen.messages.smartinbox.engine.PromotionsClassifier;
import com.nextgen.messages.smartinbox.engine.SpamClassifier;
import com.nextgen.messages.smartinbox.engine.TransactionClassifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests verifying Step 7 Smart Inbox classification pipeline,
 * multilingual numeral normalization (Bengali/Devanagari),
 * deterministic priority execution, custom rules, and category model mechanics.
 */
public class Step7SmartInboxTest {

    // --- Digit Normalization ---

    @Test
    public void testBengaliDigitNormalization() {
        String bengali = "আপনার ওটিপি হলো ১২৩৪৫৬ এবং পরিমাণ ₹৭৮৯০";
        String normalized = DigitNormalizer.normalize(bengali);

        assertTrue("Normalized text must contain ASCII 123456", normalized.contains("123456"));
        assertTrue("Normalized text must contain ASCII 7890", normalized.contains("7890"));
        assertFalse("Normalized text must not contain Bengali numerals", normalized.contains("১২৩৪৫৬"));
    }

    @Test
    public void testDevanagariDigitNormalization() {
        String devanagari = "ओटीपी कोड ९८७६५४ है और राशि रु ५०००";
        String normalized = DigitNormalizer.normalize(devanagari);

        assertTrue("Normalized text must contain ASCII 987654", normalized.contains("987654"));
        assertTrue("Normalized text must contain ASCII 5000", normalized.contains("5000"));
        assertFalse("Normalized text must not contain Devanagari numerals", normalized.contains("९८७६५४"));
    }

    @Test
    public void testDigitNormalizerNullSafety() {
        assertNull("Null input returns null", DigitNormalizer.normalize(null));
        assertEquals("Empty string returns empty", "", DigitNormalizer.normalize(""));
        assertEquals("Standard digits untouched", "12345", DigitNormalizer.normalize("12345"));
    }

    // --- OTP Classifier (English, Bengali, Hindi) ---

    @Test
    public void testEnglishOtpClassification() {
        String body = "Your Google verification code is 482910. Do not share with anyone.";
        ClassificationResult result = OtpClassifier.classify(body, "GOOGLE");

        assertNotNull(result);
        assertEquals("otp", result.getCategoryKey());
        assertTrue("Confidence must be >= 0.90", result.getConfidence() >= 0.90f);
        assertTrue(result.getReason().contains("OTP"));
    }

    @Test
    public void testBengaliOtpClassificationWithNativeDigits() {
        String body = "আপনার ওটিপি (OTP) হলো ৫৪৩২১০. এই পিন কাউকে শেয়ার করবেন না।";
        ClassificationResult result = OtpClassifier.classify(body, "VK-BANK");

        assertNotNull(result);
        assertEquals("otp", result.getCategoryKey());
        assertTrue(result.getReason().contains("OTP"));
    }

    @Test
    public void testHindiOtpClassificationWithNativeDigits() {
        String body = "खाता लॉगिन के लिए आपका ओटीपी (OTP) ६५४३२१ है। किसी के साथ साझा न करें।";
        ClassificationResult result = OtpClassifier.classify(body, "AD-GOVT");

        assertNotNull(result);
        assertEquals("otp", result.getCategoryKey());
        assertTrue(result.getReason().contains("OTP"));
    }

    @Test
    public void testNegativeOtpNotClassified() {
        String body = "Hey are we still meeting today at 5pm? Call me back.";
        ClassificationResult result = OtpClassifier.classify(body, "+919876543210");
        assertNull("Casual message should not classify as OTP", result);
    }

    // --- Transaction Classifier (English, Bengali, Hindi) ---

    @Test
    public void testEnglishBankDebitClassification() {
        String body = "Rs 2,450.00 debited from A/c XX4021 on 01-Nov-26 via UPI Ref 98214. Avail Bal: Rs 18,300.";
        ClassificationResult result = TransactionClassifier.classify(body, "HDFCBK");

        assertNotNull(result);
        assertEquals("transactions", result.getCategoryKey());
        assertTrue(result.getConfidence() >= 0.90f);
    }

    @Test
    public void testEnglishBankCreditClassification() {
        String body = "INR 25,000.00 credited to your A/c XX8910 on 01-11-2026 by salary transfer.";
        ClassificationResult result = TransactionClassifier.classify(body, "SBIBNK");

        assertNotNull(result);
        assertEquals("transactions", result.getCategoryKey());
    }

    @Test
    public void testBengaliBankTransactionClassification() {
        String body = "আপনার ব্যাঙ্ক একাউন্ট থেকে ₹১,৫০০ টাকা ডেবিট করা হয়েছে। বর্তমান ব্যালেন্স ₹৮,৫০০।";
        ClassificationResult result = TransactionClassifier.classify(body, "AXISBK");

        assertNotNull(result);
        assertEquals("transactions", result.getCategoryKey());
    }

    @Test
    public void testHindiBankTransactionClassification() {
        String body = "आपके खाते में रु ३,००० जमा (क्रेडिट) किए गए हैं। शेष राशि रु १२,००० है।";
        ClassificationResult result = TransactionClassifier.classify(body, "PNBBNK");

        assertNotNull(result);
        assertEquals("transactions", result.getCategoryKey());
    }

    @Test
    public void testPromotionalCashbackExcludedFromTransactions() {
        String body = "Get ₹500 cashback discount when you buy groceries today! Use coupon SHOP50.";
        ClassificationResult txnResult = TransactionClassifier.classify(body, "BIGBSK");
        assertNull("Promotional offers with currency should not be classified as real transactions", txnResult);

        ClassificationResult promoResult = PromotionsClassifier.classify(body, "BIGBSK");
        assertNotNull("Should be classified as promotion", promoResult);
        assertEquals("promotions", promoResult.getCategoryKey());
    }

    // --- Promotions Classifier ---

    @Test
    public void testEnglishPromotionsClassification() {
        String body = "Diwali Mega Sale! Flat 40% OFF on fashion and electronics. Shop now at https://deals.shop";
        ClassificationResult result = PromotionsClassifier.classify(body, "VM-MYNTRA");

        assertNotNull(result);
        assertEquals("promotions", result.getCategoryKey());
    }

    @Test
    public void testBengaliPromotionsClassification() {
        String body = "পুজোর বিশেষ অফার! সমস্ত কেনাকাটায় পান ৫০% ছাড় এবং ক্যাশব্যাক।";
        ClassificationResult result = PromotionsClassifier.classify(body, "SHOPPING");

        assertNotNull(result);
        assertEquals("promotions", result.getCategoryKey());
    }

    @Test
    public void testHindiPromotionsClassification() {
        String body = "ग्रैंड फेस्टिवल सेल! सभी उत्पादों पर भारी छूट और डिस्काउंट का लाभ उठाएं।";
        ClassificationResult result = PromotionsClassifier.classify(body, "OFFERS");

        assertNotNull(result);
        assertEquals("promotions", result.getCategoryKey());
    }

    // --- Spam Classifier ---

    @Test
    public void testSpamLotteryAndPhishing() {
        String body = "Congratulations! You won 1,000,000 cash prize in our international lottery! Claim prize now at http://win.xyz";
        ClassificationResult result = SpamClassifier.classify(body, "+19001234567");

        assertNotNull(result);
        assertEquals("spam", result.getCategoryKey());
    }

    @Test
    public void testSpamConservativeSafety() {
        // Legitimate bank message should never be classified as spam
        String body = "Rs 500 debited from your A/c XX1234. If not done by you, call bank helpline.";
        ClassificationResult result = SpamClassifier.classify(body, "ICICIB");
        assertNull("Legitimate bank SMS must not be classified as spam", result);
    }

    // --- Business Classifier ---

    @Test
    public void testBusinessShipmentAndPnr() {
        String body = "Your Amazon order 402-1234567 has been dispatched and will arrive today by 6 PM.";
        ClassificationResult result = BusinessClassifier.classify(body, "AMAZON");

        assertNotNull(result);
        assertEquals("business", result.getCategoryKey());
    }

    @Test
    public void testFlightPnrClassification() {
        String body = "IndiGo Flight 6E 402 PNR: M8K9L2 web check-in is now open. Departs 14:30.";
        ClassificationResult result = BusinessClassifier.classify(body, "INDIGO");

        assertNotNull(result);
        assertEquals("business", result.getCategoryKey());
    }

    // --- Personal Classifier ---

    @Test
    public void testPersonalContactSignal() {
        ContactInfo contact = new ContactInfo("John Doe", null, "lookup_john", "+1234567890");
        ClassificationResult result = PersonalClassifier.classify("Hey John, are we meeting for dinner tonight?", "+1234567890", contact);

        assertNotNull(result);
        assertEquals("personal", result.getCategoryKey());
    }

    @Test
    public void testPersonalStandardPhoneNumberSender() {
        ClassificationResult result = PersonalClassifier.classify("See you tomorrow morning at 10.", "+919876543210", null);

        assertNotNull(result);
        assertEquals("personal", result.getCategoryKey());
    }

    // --- Classification Engine Priority Tiers ---

    @Test
    public void testManualMessageOverrideHasHighestPriority() {
        MessageClassificationEntity manual = new MessageClassificationEntity(
                1L, "SMS_123", 10L, 123L, "SMS", "custom_work",
                MessageClassificationEntity.SOURCE_MANUAL, 1.0f, "Manual override",
                System.currentTimeMillis(), System.currentTimeMillis()
        );

        ClassificationResult result = ClassificationEngine.classify(
                "Your Google verification OTP is 123456",
                "GOOGLE",
                null,
                manual,
                null,
                null
        );

        assertEquals("custom_work", result.getCategoryKey());
        assertEquals("MANUAL", result.getSource());
    }

    @Test
    public void testSenderOverrideHasPriorityOverEngine() {
        SenderCategoryEntity senderOverride = new SenderCategoryEntity("GOOGLE", "business", true, System.currentTimeMillis());

        ClassificationResult result = ClassificationEngine.classify(
                "Your OTP is 123456",
                "GOOGLE",
                null,
                null,
                senderOverride,
                null
        );

        assertEquals("business", result.getCategoryKey());
        assertEquals("MANUAL", result.getSource());
    }

    @Test
    public void testCustomRulePriority() {
        ClassificationRuleEntity rule = ClassificationRuleEntity.create(
                "Uber to Business",
                ClassificationRuleEntity.TYPE_SENDER_CONTAINS,
                "UBER",
                "business",
                1
        );

        ClassificationResult result = ClassificationEngine.classify(
                "Your Uber ride receipt for Rs 250",
                "UBER_TRIP",
                null,
                null,
                null,
                Collections.singletonList(rule)
        );

        assertEquals("business", result.getCategoryKey());
        assertEquals("RULE", result.getSource());
    }

    @Test
    public void testFallbackToOtherForUnclassified() {
        ClassificationResult result = ClassificationEngine.classify(
                "System broadcast information status notice",
                "NOTIFY",
                null,
                null,
                null,
                null
        );

        assertEquals("other", result.getCategoryKey());
        assertEquals("ENGINE", result.getSource());
    }

    // --- Entity Models and Composite Keys ---

    @Test
    public void testCompositeMessageKeyFormatting() {
        assertEquals("SMS_1001", MessageClassificationEntity.buildMessageKey(false, 1001L));
        assertEquals("MMS_2002", MessageClassificationEntity.buildMessageKey(true, 2002L));
    }

    @Test
    public void testCustomCategoryEntityCreation() {
        MessageCategoryEntity custom = MessageCategoryEntity.createCustom("Work Projects", "ic_category_other", "#3F51B5");

        assertTrue(custom.getCategoryKey().startsWith("custom_"));
        assertEquals("Work Projects", custom.getDisplayName());
        assertEquals("#3F51B5", custom.getColorHex());
        assertFalse("Must not be built-in", custom.isBuiltIn());
        assertTrue("Must be active", custom.isActive());
    }
}
