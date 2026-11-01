package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.provider.Telephony;
import com.nextgen.messages.data.model.ContactInfo;
import com.nextgen.messages.data.model.Conversation;
import com.nextgen.messages.data.model.MessageListItem;
import com.nextgen.messages.data.model.SmsMessage;
import org.junit.Test;

/**
 * Unit tests verifying models, initials generation, unread tracking,
 * and polymorphic message list items.
 */
public class SmsModelAndGroupingTest {

    @Test
    public void testConversationTitleAndInitialsFallback() {
        // Case 1: Contact name present with first and last name
        Conversation c1 = new Conversation(1L, "+1234567890", "Alice Smith", null, "Hello", 1000L, 0, 1);
        assertEquals("Alice Smith", c1.getTitle());
        assertEquals("AS", c1.getInitials());

        // Case 2: Contact name with single word
        Conversation c2 = new Conversation(2L, "+1234567890", "Bob", null, "Hi", 1000L, 2, 5);
        assertEquals("Bob", c2.getTitle());
        assertEquals("BO", c2.getInitials());
        assertTrue(c2.hasUnread());
        assertEquals(2, c2.getUnreadCount());

        // Case 3: No contact name, phone number fallback
        Conversation c3 = new Conversation(3L, "+1987654321", null, null, "Test", 1000L, 0, 1);
        assertEquals("+1987654321", c3.getTitle());
        assertEquals("+1", c3.getInitials());

        // Case 4: No contact name and no address
        Conversation c4 = new Conversation(4L, null, null, null, "Test", 1000L, 0, 1);
        assertEquals("Unknown", c4.getTitle());
        assertEquals("?", c4.getInitials());
    }

    @Test
    public void testSmsMessageTypes() {
        SmsMessage incoming = new SmsMessage(
                101L, 1L, "+1234567890", "Incoming message",
                1000L, 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX,
                false, false, -1, 0, 1
        );
        assertTrue(incoming.isIncoming());
        assertFalse(incoming.isOutgoing());
        assertFalse(incoming.isRead());

        SmsMessage sent = new SmsMessage(
                102L, 1L, "+1234567890", "Outgoing message",
                1005L, 1005L, Telephony.Sms.MESSAGE_TYPE_SENT,
                true, true, -1, 0, 1
        );
        assertTrue(sent.isOutgoing());
        assertFalse(sent.isIncoming());
        assertTrue(sent.isRead());
    }

    @Test
    public void testContactInfoFallback() {
        ContactInfo fallback = ContactInfo.fallback("+1234567890");
        assertNull(fallback.getDisplayName());
        assertNull(fallback.getPhotoUri());
        assertFalse(fallback.hasName());
        assertEquals("+1234567890", fallback.getNormalizedPhoneNumber());
    }

    @Test
    public void testMessageListItemPolymorphism() {
        MessageListItem.DateHeaderItem header = new MessageListItem.DateHeaderItem("Today", 20261101L);
        assertEquals(MessageListItem.TYPE_DATE_HEADER, header.getItemType());
        assertEquals("Today", header.getDateText());

        SmsMessage msg = new SmsMessage(
                201L, 5L, "+1234567890", "Testing bubble",
                1000L, 1000L, Telephony.Sms.MESSAGE_TYPE_INBOX,
                true, true, -1, 0, 1
        );
        MessageListItem.MessageItem messageItem = new MessageListItem.MessageItem(msg);
        assertEquals(MessageListItem.TYPE_MESSAGE_INCOMING, messageItem.getItemType());
        assertEquals(201L, messageItem.getItemId());
    }
}
