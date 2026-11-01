package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.nextgen.messages.telephony.SmsRoleManager;
import org.junit.Test;

public class SmsRoleManagerTest {

    @Test
    public void testRoleStatusEnumValues() {
        SmsRoleManager.RoleStatus[] statuses = SmsRoleManager.RoleStatus.values();
        assertEquals("Expected 4 distinct role statuses", 4, statuses.length);

        assertNotNull("NOT_DEFAULT must be defined", SmsRoleManager.RoleStatus.valueOf("NOT_DEFAULT"));
        assertNotNull("IS_DEFAULT must be defined", SmsRoleManager.RoleStatus.valueOf("IS_DEFAULT"));
        assertNotNull("ROLE_UNAVAILABLE must be defined", SmsRoleManager.RoleStatus.valueOf("ROLE_UNAVAILABLE"));
        assertNotNull("PERMISSIONS_REQUIRED must be defined", SmsRoleManager.RoleStatus.valueOf("PERMISSIONS_REQUIRED"));
    }
}
