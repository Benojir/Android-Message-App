package com.nextgen.messages.smartinbox.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Dedicated classifier detecting financial transactions, UPI alerts, bank credits/debits,
 * and account balances across English, Bengali, and Hindi.
 */
public final class TransactionClassifier {

    private static final String[] TXN_ACTION_EN = {
            "debited", "credited", "withdrawn", "payment of", "paid rs", "paid inr",
            "transferred", "spent on", "refund of", "refunded", "sent to", "received from",
            "upi transaction", "atm cash", "txn of", "txn successful"
    };

    private static final String[] TXN_CONTEXT_EN = {
            "a/c", "acct", "account", "vpa", "upi", "inr", "rs.", "rs ", "₹",
            "balance", "bal:", "avl bal", "card ending", "bank"
    };

    private static final String[] TXN_ACTION_BN = {
            "টাকা কাটা হয়েছে", "টাকা জমা হয়েছে", "টাকা পাঠানো হয়েছে", "ডেবিট", "ক্রেডিট",
            "উত্তোলন", "লেনদেন সফল", "পেমেন্ট সম্পন্ন", "রিফান্ড"
    };

    private static final String[] TXN_CONTEXT_BN = {
            "অ্যাকাউন্ট", "টাকা", "ব্যালেন্স", "ইউপিআই", "ব্যাংক", "কার্ড"
    };

    private static final String[] TXN_ACTION_HI = {
            "डेबिट", "क्रेडिट", "जमा", "काटे", "खाते से काटे", "खाते में जमा", "निकाले", "निकाले गए", "भुगतान", "भुगतान किया",
            "लेनदेन", "लेनदेन सफल", "रिफंड"
    };

    private static final String[] TXN_CONTEXT_HI = {
            "रु", "रुपये", "खाता", "खाते", "राशि", "बैलेंस", "यूपीआई", "बैंक", "कार्ड"
    };

    // Promotional phrases that use currency words but are marketing offers, not completed transactions
    private static final String[] PROMO_EXCLUSIONS = {
            "cashback offer", "apply for loan", "pre-approved", "chance to win", "win rs",
            "win inr", "get up to rs", "hurry", "discount", "off on your next"
    };

    private TransactionClassifier() {}

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

        // Exclude promotional offers mentioning money
        for (String promo : PROMO_EXCLUSIONS) {
            if (normalizedBody.contains(promo)) {
                return null;
            }
        }

        // 1. Check English signals
        boolean hasActionEn = false;
        for (String action : TXN_ACTION_EN) {
            if (normalizedBody.contains(action)) {
                hasActionEn = true;
                break;
            }
        }

        boolean hasContextEn = false;
        for (String ctx : TXN_CONTEXT_EN) {
            if (normalizedBody.contains(ctx)) {
                hasContextEn = true;
                break;
            }
        }

        if (hasActionEn && hasContextEn) {
            return new ClassificationResult("transactions", 0.90f, "ENGINE", "Detected banking transaction alert (English)");
        }

        // 2. Check Bengali signals
        boolean hasActionBn = false;
        for (String action : TXN_ACTION_BN) {
            if (normalizedBody.contains(action)) {
                hasActionBn = true;
                break;
            }
        }

        boolean hasContextBn = false;
        for (String ctx : TXN_CONTEXT_BN) {
            if (normalizedBody.contains(ctx)) {
                hasContextBn = true;
                break;
            }
        }

        if (hasActionBn && (hasContextBn || hasContextEn)) {
            return new ClassificationResult("transactions", 0.90f, "ENGINE", "Detected banking transaction alert (Bengali)");
        }

        // 3. Check Hindi signals
        boolean hasActionHi = false;
        for (String action : TXN_ACTION_HI) {
            if (normalizedBody.contains(action)) {
                hasActionHi = true;
                break;
            }
        }

        boolean hasContextHi = false;
        for (String ctx : TXN_CONTEXT_HI) {
            if (normalizedBody.contains(ctx)) {
                hasContextHi = true;
                break;
            }
        }

        if (hasActionHi && (hasContextHi || hasContextEn)) {
            return new ClassificationResult("transactions", 0.90f, "ENGINE", "Detected banking transaction alert (Hindi)");
        }

        return null;
    }
}
