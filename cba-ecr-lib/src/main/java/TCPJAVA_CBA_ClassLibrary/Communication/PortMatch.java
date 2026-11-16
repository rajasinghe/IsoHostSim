package TCPJAVA_CBA_ClassLibrary.Communication;

/**
 * ISO8583Studio patch: pure helpers that decide whether a serial port is the
 * PAX terminal. Windows behaviour is unchanged (the PAX description and the
 * "COM" name check are exactly what the original code tested); Linux device
 * names (ttyACM* / ttyUSB*, with or without /dev/) are accepted in addition.
 */
public final class PortMatch {

    private PortMatch() {
    }

    /** True when the port looks like the PAX terminal: PAX description, or a Linux USB serial name. */
    public static boolean isPaxPort(String name, String description) {
        if (description != null && description.contains(PaxPort.PAX_PORT_DESCRIPTION)) {
            return true;
        }
        return isLinuxUsbSerialName(name);
    }

    /**
     * Replacement for the original {@code ComPort.contains("COM")} checks made
     * after discovery: a Windows COM name, or a Linux USB serial name.
     */
    public static boolean isUsablePortName(String name) {
        if (name == null) {
            return false;
        }
        return name.contains("COM") || isLinuxUsbSerialName(name);
    }

    /** ttyACM0, /dev/ttyACM0, ttyUSB0, /dev/ttyUSB0 ... */
    public static boolean isLinuxUsbSerialName(String name) {
        if (name == null) {
            return false;
        }
        String base = name.startsWith("/dev/") ? name.substring("/dev/".length()) : name;
        return base.startsWith("ttyACM") || base.startsWith("ttyUSB");
    }
}
