package com.nextgen.messages;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class PermissionsAndManifestTest {

    @Test
    public void testManifestContainsNoSmsPermissions() throws Exception {
        File manifestFile = new File("src/main/AndroidManifest.xml");
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists());

        String manifestContent = new String(Files.readAllBytes(manifestFile.toPath()));

        // Explicitly assert that no telephony/SMS permissions are requested in Step 1
        assertFalse("Must NOT request READ_SMS in Step 1", manifestContent.contains("android.permission.READ_SMS"));
        assertFalse("Must NOT request SEND_SMS in Step 1", manifestContent.contains("android.permission.SEND_SMS"));
        assertFalse("Must NOT request RECEIVE_SMS in Step 1", manifestContent.contains("android.permission.RECEIVE_SMS"));
        assertFalse("Must NOT request RECEIVE_MMS in Step 1", manifestContent.contains("android.permission.RECEIVE_MMS"));
        assertFalse("Must NOT request READ_CONTACTS in Step 1", manifestContent.contains("android.permission.READ_CONTACTS"));
    }
}
