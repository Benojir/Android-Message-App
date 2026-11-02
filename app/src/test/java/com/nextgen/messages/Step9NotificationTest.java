package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.notification.MessageNotificationManager;
import com.nextgen.messages.notification.NotificationChannelManager;
import com.nextgen.messages.notification.NotificationPrivacyManager;
import com.nextgen.messages.smartinbox.db.ConversationMetadataEntity;
import org.junit.Test;

/**
 * Unit tests for Step 9: Real-Time SMS/MMS Notifications, Channels, Privacy & Quick Reply logic.
 */
public class Step9NotificationTest {

    @Test
    public void testNotificationIdForThreadDeterminismAndRange() {
        // Zero or negative threadId fallback
        assertEquals(999999, MessageNotificationManager.getNotificationIdForThread(0));
        assertEquals(999999, MessageNotificationManager.getNotificationIdForThread(-1));

        // Normal positive thread IDs
        int id1 = MessageNotificationManager.getNotificationIdForThread(1);
        int id2 = MessageNotificationManager.getNotificationIdForThread(2);
        int id100 = MessageNotificationManager.getNotificationIdForThread(100);
        int idLarge = MessageNotificationManager.getNotificationIdForThread(123456789L);

        // Verification of deterministic output
        assertEquals(id1, MessageNotificationManager.getNotificationIdForThread(1));
        assertEquals(id2, MessageNotificationManager.getNotificationIdForThread(2));
        assertEquals(id100, MessageNotificationManager.getNotificationIdForThread(100));

        // Ensure distinct IDs for distinct threads
        assertNotEquals(id1, id2);
        assertNotEquals(id1, id100);

        // Ensure IDs are strictly positive and greater than summary ID (1)
        assertTrue(id1 >= 1000);
        assertTrue(id2 >= 1000);
        assertTrue(id100 >= 1000);
        assertTrue(idLarge >= 1000);
        assertTrue(idLarge < 1000000000);
    }

    @Test
    public void testActiveConversationTracking() {
        MessageNotificationManager.setCurrentActiveThreadId(42L);
        assertEquals(42L, MessageNotificationManager.getCurrentActiveThreadId());

        MessageNotificationManager.setCurrentActiveThreadId(0L);
        assertEquals(0L, MessageNotificationManager.getCurrentActiveThreadId());
    }

    @Test
    public void testNotificationChannelRouting() {
        // 1. Standard 1-on-1 SMS conversation
        String normalChannel = NotificationChannelManager.getChannelForConversation(false, false, false);
        assertEquals(NotificationChannelManager.CHANNEL_ID_MESSAGES, normalChannel);

        // 2. Group conversation
        String groupChannel = NotificationChannelManager.getChannelForConversation(true, false, false);
        assertEquals(NotificationChannelManager.CHANNEL_ID_GROUP_MESSAGES, groupChannel);

        // 3. Muted 1-on-1 conversation
        String mutedChannel = NotificationChannelManager.getChannelForConversation(false, true, false);
        assertEquals(NotificationChannelManager.CHANNEL_ID_SILENT, mutedChannel);

        // 4. Silent 1-on-1 conversation
        String silentChannel = NotificationChannelManager.getChannelForConversation(false, false, true);
        assertEquals(NotificationChannelManager.CHANNEL_ID_SILENT, silentChannel);

        // 5. Muted group conversation routes to silent channel
        String mutedGroupChannel = NotificationChannelManager.getChannelForConversation(true, true, false);
        assertEquals(NotificationChannelManager.CHANNEL_ID_SILENT, mutedGroupChannel);
    }

    @Test
    public void testMuteStateCalculation() {
        long now = System.currentTimeMillis();

        // Conversation Model - unmuted
        Conversation cUnmuted = new Conversation(1L, "123", "Alice", null, "Hi", now, 0, 1, false, null, false, 0L, false, 0L, false, false, 0L, false);
        assertFalse(cUnmuted.isCurrentlyMuted());

        // Conversation Model - indefinitely muted (-1)
        Conversation cIndefinite = new Conversation(2L, "123", "Alice", null, "Hi", now, 0, 1, false, null, false, 0L, false, 0L, false, false, -1L, false);
        assertTrue(cIndefinite.isCurrentlyMuted());

        // Conversation Model - muted for 1 hour in the future
        Conversation cFuture = new Conversation(3L, "123", "Alice", null, "Hi", now, 0, 1, false, null, false, 0L, false, 0L, false, false, now + 3600000L, false);
        assertTrue(cFuture.isCurrentlyMuted());

        // Conversation Model - mute expired
        Conversation cExpired = new Conversation(4L, "123", "Alice", null, "Hi", now, 0, 1, false, null, false, 0L, false, 0L, false, false, now - 1000L, false);
        assertFalse(cExpired.isCurrentlyMuted());

        // ConversationMetadataEntity - unmuted
        ConversationMetadataEntity eUnmuted = ConversationMetadataEntity.defaultForThread(10L);
        eUnmuted.setMutedUntil(0L);
        assertFalse(eUnmuted.isCurrentlyMuted());

        // ConversationMetadataEntity - indefinitely muted
        ConversationMetadataEntity eIndefinite = ConversationMetadataEntity.defaultForThread(11L);
        eIndefinite.setMutedUntil(-1L);
        assertTrue(eIndefinite.isCurrentlyMuted());

        // ConversationMetadataEntity - muted in future
        ConversationMetadataEntity eFuture = ConversationMetadataEntity.defaultForThread(12L);
        eFuture.setMutedUntil(now + 8 * 3600000L);
        assertTrue(eFuture.isCurrentlyMuted());

        // ConversationMetadataEntity - mute expired
        ConversationMetadataEntity eExpired = ConversationMetadataEntity.defaultForThread(13L);
        eExpired.setMutedUntil(now - 5000L);
        assertFalse(eExpired.isCurrentlyMuted());
    }

    @Test
    public void testOtpClassificationForPrivacyProtection() {
        // High confidence OTP messages across supported languages
        String englishOtp = "Your Google verification code is 482910. Do not share it with anyone.";
        assertTrue(NotificationPrivacyManager.isOtpMessage(englishOtp, "GOOGLE"));

        String bankOtp = "Use OTP 948123 to authenticate your transaction of INR 1,500 at Amazon. Valid for 10 minutes.";
        assertTrue(NotificationPrivacyManager.isOtpMessage(bankOtp, "HDFCBK"));

        String bengaliOtp = "আপনার ওটিপি কোড হল 891234। এটি কারও সাথে শেয়ার করবেন না।";
        assertTrue(NotificationPrivacyManager.isOtpMessage(bengaliOtp, "BKASH"));

        String hindiOtp = "आपका सत्यापन कोड 654321 है। कृपया इसे किसी के साथ साझा न करें।";
        assertTrue(NotificationPrivacyManager.isOtpMessage(hindiOtp, "SBIBNK"));

        // Regular non-OTP messages
        assertFalse(NotificationPrivacyManager.isOtpMessage("Hey, are we still meeting for lunch today?", "+1234567890"));
        assertFalse(NotificationPrivacyManager.isOtpMessage("Your package has been delivered to your front door.", "AMAZON"));
        assertFalse(NotificationPrivacyManager.isOtpMessage("", "GOOGLE"));
        assertFalse(NotificationPrivacyManager.isOtpMessage(null, null));
    }
}
