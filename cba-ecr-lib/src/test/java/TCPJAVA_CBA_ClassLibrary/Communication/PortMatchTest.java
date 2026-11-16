package TCPJAVA_CBA_ClassLibrary.Communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PortMatchTest {

    @AfterEach
    void clearPreferred() {
        PaxPort.setPreferredPortName(null);
    }

    @Test
    void windowsComPortWithPaxDescriptionMatches() {
        assertTrue(PortMatch.isPaxPort("COM5", "USB Virtual Serial Port (PAX) (COM5)"));
        assertTrue(PortMatch.isUsablePortName("COM5"));
    }

    @Test
    void linuxAcmAndUsbNamesMatch() {
        assertTrue(PortMatch.isPaxPort("/dev/ttyACM0", null));
        assertTrue(PortMatch.isPaxPort("ttyACM0", "USB-Based Serial Port"));
        assertTrue(PortMatch.isPaxPort("/dev/ttyUSB1", ""));
        assertTrue(PortMatch.isUsablePortName("/dev/ttyACM0"));
    }

    @Test
    void onboardSerialDoesNotMatch() {
        assertFalse(PortMatch.isPaxPort("/dev/ttyS0", "Physical Port S0"));
        assertFalse(PortMatch.isUsablePortName("/dev/ttyS0"));
        assertFalse(PortMatch.isUsablePortName(""));
        assertFalse(PortMatch.isUsablePortName(null));
    }

    @Test
    void preferredPortOverridesDiscovery() {
        PaxPort.setPreferredPortName("/dev/ttyACM3");
        assertEquals("/dev/ttyACM3", PaxPort.discover());
        PaxPort.setPreferredPortName("  ");
        assertNull(PaxPort.getPreferredPortName());
    }
}
