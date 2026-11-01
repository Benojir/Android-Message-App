package com.nextgen.messages;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

/**
 * Validates that AndroidManifest.xml declares all required components, intent-filters,
 * and permissions mandated by Android to qualify as an eligible default SMS application.
 */
public class PermissionsAndManifestTest {

    @Test
    public void testManifestContainsRequiredSmsPermissions() throws Exception {
        File manifestFile = new File("src/main/AndroidManifest.xml");
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists());

        String manifestContent = new String(Files.readAllBytes(manifestFile.toPath()));

        // In Step 2, the app must declare standard SMS/MMS permissions
        assertTrue("Must declare READ_SMS in Step 2", manifestContent.contains("android.permission.READ_SMS"));
        assertTrue("Must declare RECEIVE_SMS in Step 2", manifestContent.contains("android.permission.RECEIVE_SMS"));
        assertTrue("Must declare SEND_SMS in Step 2", manifestContent.contains("android.permission.SEND_SMS"));
        assertTrue("Must declare RECEIVE_MMS in Step 2", manifestContent.contains("android.permission.RECEIVE_MMS"));
        assertTrue("Must declare READ_CONTACTS in Step 2", manifestContent.contains("android.permission.READ_CONTACTS"));
    }

    @Test
    public void testManifestContainsDefaultSmsComponents() throws Exception {
        File manifestFile = new File("src/main/AndroidManifest.xml");
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists());

        String manifestContent = new String(Files.readAllBytes(manifestFile.toPath()));

        // 1. SMS_DELIVER Receiver
        assertTrue("Must declare SmsDeliverReceiver", manifestContent.contains("SmsDeliverReceiver"));
        assertTrue("Must handle SMS_DELIVER", manifestContent.contains("android.provider.Telephony.SMS_DELIVER"));
        assertTrue("Must require BROADCAST_SMS", manifestContent.contains("android.permission.BROADCAST_SMS"));

        // 2. WAP_PUSH_DELIVER Receiver
        assertTrue("Must declare MmsDeliverReceiver", manifestContent.contains("MmsDeliverReceiver"));
        assertTrue("Must handle WAP_PUSH_DELIVER", manifestContent.contains("android.provider.Telephony.WAP_PUSH_DELIVER"));
        assertTrue("Must specify MMS MIME type", manifestContent.contains("application/vnd.wap.mms-message"));
        assertTrue("Must require BROADCAST_WAP_PUSH", manifestContent.contains("android.permission.BROADCAST_WAP_PUSH"));

        // 3. SENDTO Activity
        assertTrue("Must declare ComposeActivity", manifestContent.contains("ComposeActivity"));
        assertTrue("Must handle ACTION_SENDTO", manifestContent.contains("android.intent.action.SENDTO"));
        assertTrue("Must handle sms scheme", manifestContent.contains("android:scheme=\"sms\""));
        assertTrue("Must handle smsto scheme", manifestContent.contains("android:scheme=\"smsto\""));
        assertTrue("Must handle mms scheme", manifestContent.contains("android:scheme=\"mms\""));
        assertTrue("Must handle mmsto scheme", manifestContent.contains("android:scheme=\"mmsto\""));

        // 4. RESPOND_VIA_MESSAGE Service
        assertTrue("Must declare RespondViaMessageService", manifestContent.contains("RespondViaMessageService"));
        assertTrue("Must handle RESPOND_VIA_MESSAGE", manifestContent.contains("android.intent.action.RESPOND_VIA_MESSAGE"));
        assertTrue("Must require SEND_RESPOND_VIA_MESSAGE", manifestContent.contains("android.permission.SEND_RESPOND_VIA_MESSAGE"));

        // 5. ConversationActivity (Step 3)
        assertTrue("Must declare ConversationActivity", manifestContent.contains("ConversationActivity"));
        assertTrue("ConversationActivity must not be exported", manifestContent.contains("android:name=\".ui.conversation.ConversationActivity\"\n            android:exported=\"false\""));
    }
}
