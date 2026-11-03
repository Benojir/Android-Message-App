package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.provider.Telephony;
import com.nextgen.messages.data.model.Conversation;
import java.util.Collections;
import org.junit.Test;

/**
 * Unit test suite for Step 12: Final Production Audit, Reliability & Release Verification.
 */
public class Step12ProductionAuditTest {

    @Test
    public void testConversationDisplaySnippetIncoming() {
        Conversation incoming = new Conversation(
                1L,
                "+1234567890",
                "Alice",
                null,
                "Hey! Are you available?",
                1730600000000L,
                1,
                5,
                false,
                Collections.emptyList(),
                false,
                0L,
                false,
                0L,
                false,
                false,
                0L,
                false,
                Telephony.Sms.MESSAGE_TYPE_INBOX,
                Telephony.Sms.STATUS_NONE
        );

        assertFalse(incoming.isLatestOutgoing());
        assertFalse(incoming.isLatestFailed());
        assertFalse(incoming.isLatestDraft());
        // Incoming snippet must show raw message body without "You:" or "Not sent:"
        assertEquals("Hey! Are you available?", incoming.getDisplaySnippet());
    }

    @Test
    public void testConversationDisplaySnippetOutgoingSent() {
        Conversation sent = new Conversation(
                2L,
                "+1234567890",
                "Alice",
                null,
                "Yes, I will be there soon.",
                1730600010000L,
                0,
                6,
                false,
                Collections.emptyList(),
                false,
                0L,
                false,
                0L,
                false,
                false,
                0L,
                false,
                Telephony.Sms.MESSAGE_TYPE_SENT,
                Telephony.Sms.STATUS_COMPLETE
        );

        assertTrue(sent.isLatestOutgoing());
        assertFalse(sent.isLatestFailed());
        assertFalse(sent.isLatestDraft());
        // Outgoing sent snippet must prefix "You: "
        assertEquals("You: Yes, I will be there soon.", sent.getDisplaySnippet());
    }

    @Test
    public void testConversationDisplaySnippetFailed() {
        Conversation failed = new Conversation(
                3L,
                "+1987654321",
                "Bob",
                null,
                "Can you check this doc?",
                1730600020000L,
                0,
                2,
                false,
                Collections.emptyList(),
                false,
                0L,
                false,
                0L,
                false,
                false,
                0L,
                false,
                Telephony.Sms.MESSAGE_TYPE_FAILED,
                Telephony.Sms.STATUS_FAILED
        );

        assertTrue(failed.isLatestOutgoing());
        assertTrue(failed.isLatestFailed());
        assertFalse(failed.isLatestDraft());
        // Failed message must clearly show "Not sent: "
        assertEquals("Not sent: Can you check this doc?", failed.getDisplaySnippet());
    }

    @Test
    public void testConversationDisplaySnippetDraft() {
        Conversation draft = new Conversation(
                4L,
                "+1555555555",
                "Charlie",
                null,
                "Meeting at 5 pm",
                1730600030000L,
                0,
                1,
                false,
                Collections.emptyList(),
                false,
                0L,
                false,
                0L,
                false,
                false,
                0L,
                false,
                Telephony.Sms.MESSAGE_TYPE_DRAFT,
                -1
        );

        assertTrue(draft.isLatestOutgoing());
        assertFalse(draft.isLatestFailed());
        assertTrue(draft.isLatestDraft());
        // Draft message must clearly show "Draft: "
        assertEquals("Draft: Meeting at 5 pm", draft.getDisplaySnippet());
    }

    @Test
    public void testConversationDisplaySnippetQueued() {
        Conversation queued = new Conversation(
                5L,
                "+1444444444",
                "Dana",
                null,
                "Sending photo attachment",
                1730600040000L,
                0,
                3,
                false,
                Collections.emptyList(),
                false,
                0L,
                false,
                0L,
                false,
                false,
                0L,
                false,
                Telephony.Sms.MESSAGE_TYPE_QUEUED,
                -1
        );

        assertTrue(queued.isLatestOutgoing());
        assertFalse(queued.isLatestFailed());
        assertFalse(queued.isLatestDraft());
        assertEquals("Sending… Sending photo attachment", queued.getDisplaySnippet());
    }

    @Test
    public void testConversationWithMetadataPreservesStatus() {
        Conversation original = new Conversation(
                10L,
                "+1000000000",
                "Contact",
                null,
                "Test message body",
                1730600050000L,
                0,
                1,
                false,
                Collections.emptyList(),
                false,
                0L,
                false,
                0L,
                false,
                false,
                0L,
                false,
                Telephony.Sms.MESSAGE_TYPE_FAILED,
                Telephony.Sms.STATUS_FAILED
        );

        Conversation enriched = original.withMetadata(
                true,
                1730600060000L,
                false,
                0L,
                false,
                false,
                0L,
                false
        );

        assertTrue(enriched.isPinned());
        assertEquals(Telephony.Sms.MESSAGE_TYPE_FAILED, enriched.getLatestType());
        assertEquals(Telephony.Sms.STATUS_FAILED, enriched.getLatestStatus());
        assertTrue(enriched.isLatestFailed());
        assertEquals("Not sent: Test message body", enriched.getDisplaySnippet());
    }

    @Test
    public void testDuplicateTimestampToleranceWindow() {
        long baseTimestamp = 1730600000000L;

        // Exact match -> duplicate
        assertTrue(isDuplicateWithinTolerance(baseTimestamp, baseTimestamp, 2000L));

        // +1500ms -> duplicate (within 2000ms window)
        assertTrue(isDuplicateWithinTolerance(baseTimestamp, baseTimestamp + 1500L, 2000L));

        // -1999ms -> duplicate (within 2000ms window)
        assertTrue(isDuplicateWithinTolerance(baseTimestamp, baseTimestamp - 1999L, 2000L));

        // +2001ms -> distinct (outside 2000ms window)
        assertFalse(isDuplicateWithinTolerance(baseTimestamp, baseTimestamp + 2001L, 2000L));

        // -5000ms -> distinct (outside 2000ms window)
        assertFalse(isDuplicateWithinTolerance(baseTimestamp, baseTimestamp - 5000L, 2000L));
    }

    private static boolean isDuplicateWithinTolerance(long t1, long t2, long toleranceMs) {
        return Math.abs(t1 - t2) <= toleranceMs;
    }
}
