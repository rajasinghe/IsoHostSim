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

    /**
     * The Linux fallback's choice among several candidate ports: the lowest-numbered one.
     * A PAX terminal exposes several CDC ports (an A910S shows ttyACM0..2); the ECR port is the
     * first interface, and jSerialComm lists them newest first, so "first in the list" would pick
     * the wrong one. Returns null when there is no candidate.
     */
    public static String pickLinuxPort(java.util.List<String> names) {
        String best = null;
        long bestKey = Long.MAX_VALUE;
        for (String name : names) {
            if (!isLinuxUsbSerialName(name)) {
                continue;
            }
            String base = name.startsWith("/dev/") ? name.substring("/dev/".length()) : name;
            // ttyACM* before ttyUSB*, then by number.
            long group = base.startsWith("ttyACM") ? 0 : 1;
            long number;
            try {
                number = Long.parseLong(base.substring(6));
            } catch (NumberFormatException e) {
                number = Integer.MAX_VALUE;
            }
            long key = group * 1_000_000_000L + number;
            if (key < bestKey) {
                bestKey = key;
                best = name;
            }
        }
        return best;
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
