package com.nextgen.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nextgen.messages.telephony.SimInfo;
import org.junit.Test;

/**
 * Unit tests for SimInfo data model and display labels.
 */
public class SimInfoTest {

    @Test
    public void testSimInfoSlotAndLabels() {
        SimInfo sim1 = new SimInfo(10, 0, "Jio 4G", "Jio", "in", true);
        assertEquals(10, sim1.getSubscriptionId());
        assertEquals(0, sim1.getSimSlotIndex());
        assertEquals(1, sim1.getSlotNumber());
        assertEquals("SIM 1", sim1.getLabel());
        assertEquals("SIM 1 (Jio 4G)", sim1.getFullLabel());
        assertTrue(sim1.isDefaultSms());

        SimInfo sim2 = new SimInfo(11, 1, "Airtel", "Airtel", "in", false);
        assertEquals(11, sim2.getSubscriptionId());
        assertEquals(1, sim2.getSimSlotIndex());
        assertEquals(2, sim2.getSlotNumber());
        assertEquals("SIM 2", sim2.getLabel());
        assertEquals("SIM 2 (Airtel)", sim2.getFullLabel());
        assertFalse(sim2.isDefaultSms());
    }

    @Test
    public void testSimInfoEquality() {
        SimInfo simA = new SimInfo(1, 0, "SIM 1", "Carrier", "us", true);
        SimInfo simB = new SimInfo(1, 0, "SIM 1", "Carrier", "us", true);
        SimInfo simC = new SimInfo(2, 1, "SIM 2", "Carrier", "us", false);

        assertEquals(simA, simB);
        assertFalse(simA.equals(simC));
    }
}
