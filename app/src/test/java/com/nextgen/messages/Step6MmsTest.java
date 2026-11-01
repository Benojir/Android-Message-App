package com.nextgen.messages;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import com.nextgen.messages.data.model.MessageListItem;
import com.nextgen.messages.data.model.MmsAttachment;
import com.nextgen.messages.data.model.SmsMessage;
import com.nextgen.messages.mms.pdu.EncodedStringValue;
import com.nextgen.messages.mms.pdu.GenericPdu;
import com.nextgen.messages.mms.pdu.NotificationInd;
import com.nextgen.messages.mms.pdu.PduBody;
import com.nextgen.messages.mms.pdu.PduComposer;
import com.nextgen.messages.mms.pdu.PduHeaders;
import com.nextgen.messages.mms.pdu.PduParser;
import com.nextgen.messages.mms.pdu.PduPart;
import com.nextgen.messages.mms.pdu.SendReq;
import com.nextgen.messages.mms.pdu.SmilHelper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Comprehensive Unit Tests for Step 6: Complete MMS & Group Messaging.
 * Verifies pure Java WAP-209 PDU binary encoding/decoding, SMIL layout generation,
 * NotificationInd parsing, MmsAttachment categorization, and composite SMS/MMS item uniqueness.
 */
public class Step6MmsTest {

    @Test
    public void testPduEncodingAndDecoding_SendReq() throws Exception {
        SendReq sendReq = new SendReq();
        sendReq.setDate(1700000000L);
        sendReq.setTransactionId("tx12345".getBytes(StandardCharsets.US_ASCII));
        sendReq.addTo(new EncodedStringValue("+15551234567"));
        sendReq.setSubject(new EncodedStringValue("Hello MMS"));
        sendReq.setContentType("application/vnd.wap.multipart.related".getBytes(StandardCharsets.US_ASCII));

        // Create Body with Text part and Image part
        PduBody body = new PduBody();

        PduPart textPart = new PduPart();
        textPart.setContentType("text/plain".getBytes(StandardCharsets.US_ASCII));
        textPart.setContentId("<text0>".getBytes(StandardCharsets.US_ASCII));
        textPart.setContentLocation("text0.txt".getBytes(StandardCharsets.US_ASCII));
        textPart.setData("This is test MMS body text".getBytes(StandardCharsets.UTF_8));
        body.addPart(textPart);

        PduPart imgPart = new PduPart();
        imgPart.setContentType("image/jpeg".getBytes(StandardCharsets.US_ASCII));
        imgPart.setContentId("<image0>".getBytes(StandardCharsets.US_ASCII));
        imgPart.setContentLocation("photo.jpg".getBytes(StandardCharsets.US_ASCII));
        byte[] dummyJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
        imgPart.setData(dummyJpeg);
        body.addPart(imgPart);

        sendReq.setBody(body);

        // 1. Serialize using PduComposer
        PduComposer composer = new PduComposer(sendReq);
        byte[] pduBytes = composer.make();
        assertNotNull("PduComposer must output non-null byte array", pduBytes);
        assertTrue("PduComposer output must have content", pduBytes.length > 0);

        // 2. Deserialize using PduParser
        PduParser parser = new PduParser(pduBytes);
        GenericPdu parsedPdu = parser.parse();
        assertNotNull("PduParser must parse serialized PDU", parsedPdu);
        assertTrue("Parsed PDU must be SendReq instance", parsedPdu instanceof SendReq);

        SendReq parsedReq = (SendReq) parsedPdu;
        assertEquals(PduHeaders.MESSAGE_TYPE_SEND_REQ, parsedReq.getMessageType());
        assertNotNull(parsedReq.getSubject());
        assertEquals("Hello MMS", parsedReq.getSubject().getString());

        EncodedStringValue[] toRecipients = parsedReq.getTo();
        assertNotNull("Recipients must not be null", toRecipients);
        assertEquals(1, toRecipients.length);
        assertEquals("+15551234567", toRecipients[0].getString());

        PduBody parsedBody = parsedReq.getBody();
        assertNotNull("Body must not be null", parsedBody);
        assertEquals(2, parsedBody.getPartsNum());

        PduPart p0 = parsedBody.getPart(0);
        assertEquals("text/plain", new String(p0.getContentType(), StandardCharsets.US_ASCII));
        assertEquals("This is test MMS body text", new String(p0.getData(), StandardCharsets.UTF_8));

        PduPart p1 = parsedBody.getPart(1);
        assertEquals("image/jpeg", new String(p1.getContentType(), StandardCharsets.US_ASCII));
        assertArrayEquals(dummyJpeg, p1.getData());
    }

    @Test
    public void testSmilHelper_GeneratesValidSmil() {
        PduPart smilPart = SmilHelper.createSmilPart("text_01.txt", "photo.jpg", "image/jpeg");
        assertNotNull(smilPart);
        assertNotNull(smilPart.getData());

        byte[] smilBytes = smilPart.getData();
        String smilString = new String(smilBytes, StandardCharsets.UTF_8);

        assertTrue(smilString.contains("<smil>"));
        assertTrue(smilString.contains("</smil>"));
        assertTrue(smilString.contains("<region id=\"Image\""));
        assertTrue(smilString.contains("<region id=\"Text\""));
        assertTrue(smilString.contains("<img src=\"photo.jpg\""));
        assertTrue(smilString.contains("<text src=\"text_01.txt\""));
    }

    @Test
    public void testNotificationInd_PduHeaders() {
        NotificationInd notif = new NotificationInd();
        notif.setTransactionId("notif_tx_999".getBytes(StandardCharsets.US_ASCII));
        notif.setContentLocation("http://mmsc.carrier.com/mms/payload/999".getBytes(StandardCharsets.US_ASCII));
        notif.setMessageSize(125400L);
        notif.setExpiry(86400L);

        assertEquals(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND, notif.getMessageType());
        assertEquals("http://mmsc.carrier.com/mms/payload/999", new String(notif.getContentLocation(), StandardCharsets.US_ASCII));
        assertEquals(125400L, notif.getMessageSize());
        assertEquals(86400L, notif.getExpiry());
    }

    @Test
    public void testMmsAttachment_TypeClassification() {
        MmsAttachment imgAtt = new MmsAttachment(101L, 1L, null, "image/jpeg", "vacation.jpg", 154000L);
        assertTrue(imgAtt.isImage());
        assertFalse(imgAtt.isVideo());
        assertFalse(imgAtt.isAudio());
        assertEquals("150 KB", imgAtt.getFormattedSize());

        MmsAttachment vidAtt = new MmsAttachment(102L, 1L, null, "video/mp4", "clip.mp4", 450000L);
        assertFalse(vidAtt.isImage());
        assertTrue(vidAtt.isVideo());
        assertFalse(vidAtt.isAudio());
        assertEquals("439 KB", vidAtt.getFormattedSize());

        MmsAttachment audAtt = new MmsAttachment(103L, 1L, null, "audio/aac", "voice.aac", 25000L);
        assertFalse(audAtt.isImage());
        assertFalse(audAtt.isVideo());
        assertTrue(audAtt.isAudio());
    }

    @Test
    public void testCompositeId_CollisionPreventionBetweenSmsAndMms() {
        long sameRawId = 42L;

        SmsMessage smsMessage = new SmsMessage(
                sameRawId, 100L, "+15551112222", "SMS text",
                1700000000000L, 0L, SmsMessage.TYPE_INCOMING,
                true, true, SmsMessage.STATUS_NONE, 0, 1
        );

        SmsMessage mmsMessage = SmsMessage.createMms(
                sameRawId, 100L, "+15551112222", "MMS text",
                1700000000000L, SmsMessage.TYPE_INCOMING,
                null, null
        );

        assertEquals(42L, smsMessage.getId());
        assertEquals(42L, mmsMessage.getId());

        // Composite IDs must never collide
        long smsCompositeId = smsMessage.getCompositeId();
        long mmsCompositeId = mmsMessage.getCompositeId();
        assertTrue("SMS and MMS composite IDs must be different even with identical raw database IDs",
                smsCompositeId != mmsCompositeId);

        Set<Long> idSet = new HashSet<>();
        idSet.add(smsCompositeId);
        idSet.add(mmsCompositeId);
        assertEquals("Set must contain both unique IDs", 2, idSet.size());
    }

    @Test
    public void testMessageListItem_MmsTypesAndEquality() {
        SmsMessage incomingMms = SmsMessage.createMms(
                10L, 50L, "+15553334444", "Photo attached",
                1700000000000L, SmsMessage.TYPE_INCOMING,
                null, null
        );

        MessageListItem itemIncoming = MessageListItem.fromMessage(incomingMms);
        assertEquals(MessageListItem.TYPE_MMS_INCOMING, itemIncoming.getItemType());
        assertEquals(incomingMms.getCompositeId(), itemIncoming.getItemId());

        SmsMessage outgoingMms = SmsMessage.createMms(
                11L, 50L, "+15553334444", "Here is your picture",
                1700000005000L, SmsMessage.TYPE_SENT,
                null, null
        );

        MessageListItem itemOutgoing = MessageListItem.fromMessage(outgoingMms);
        assertEquals(MessageListItem.TYPE_MMS_OUTGOING, itemOutgoing.getItemType());
        assertEquals(outgoingMms.getCompositeId(), itemOutgoing.getItemId());
    }
}
