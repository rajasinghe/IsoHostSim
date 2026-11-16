/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TCPJAVA_CBA_ClassLibrary.Communication;

import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.io.OutputStream;
import org.json.JSONObject;

/**
 * Process wide owner of the single PAX USB virtual serial port.
 *
 * The legacy request classes each opened and closed their own SerialPort, so a
 * second command could never share the port with a running transaction. This
 * class keeps one open port for the whole process and adds the cancel routing
 * that Transaction Cancel needs: while a cancel is pending, an ACK/NAK payload
 * that arrives on the port belongs to the cancel and not to the transaction
 * that is still waiting for its own result.
 *
 * Supersedes USbConnector for anything that has to co-operate with a cancel.
 *
 * @author gimhan added
 */
public class PaxPort {

    public static final String PAX_PORT_DESCRIPTION = "USB Virtual Serial Port (PAX)";

    /** Held while the port is opened, written to or closed. */
    public static final Object portLock = new Object();

    private static SerialPort port = null;
    private static int users = 0;

    private static volatile boolean cancelPending = false;
    private static volatile String cancelReplyPayload = null;

    private PaxPort() {
    }

    // ISO8583Studio patch: optional forced port (e.g. "/dev/ttyACM0" or "COM5"); null/empty = auto-discover.
    private static volatile String preferredPortName = null;

    // ISO8583Studio patch: force discover() to return this port name. Pass null or "" to go back to auto-discovery.
    public static void setPreferredPortName(String name) {
        preferredPortName = (name == null || name.trim().isEmpty()) ? null : name.trim();
    }

    // ISO8583Studio patch
    public static String getPreferredPortName() {
        return preferredPortName;
    }

    /** @return the COM port name of the PAX device, or an empty string when it is not present. */
    public static String discover() {
        // ISO8583Studio patch: a forced port wins over discovery.
        String preferred = preferredPortName;
        if (preferred != null) {
            return preferred;
        }
        String comPort = "";
        SerialPort[] portList = SerialPort.getCommPorts();
        for (SerialPort candidate : portList) {
            if (candidate.getDescriptivePortName().contains(PAX_PORT_DESCRIPTION)) {
                comPort = candidate.getSystemPortName();
            }
        }
        // ISO8583Studio patch: on Linux the PAX description is not reported, so fall back to
        // the first ttyACM*/ttyUSB* port. Only reached when no PAX-described port exists, so
        // Windows discovery is unchanged.
        if (comPort.isEmpty()) {
            for (SerialPort candidate : portList) {
                if (PortMatch.isPaxPort(candidate.getSystemPortName(), candidate.getDescriptivePortName())) {
                    comPort = candidate.getSystemPortName();
                    break;
                }
            }
        }
        return comPort;
    }

    /**
     * Open the port if it is not open already and register one more user.
     *
     * @return the open port, or null when it could not be opened.
     */
    public static SerialPort acquire(String comPort) {
        synchronized (portLock) {
            if (port != null && port.isOpen()) {
                users++;
                return port;
            }

            SerialPort candidate = SerialPort.getCommPort(comPort);
            candidate.setComPortParameters(9600, 8, 1, 0);
            candidate.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, (int) 0, (int) 3000);

            if (!candidate.openPort()) {
                candidate.closePort();
                if (!candidate.openPort()) {
                    return null;
                }
            }

            candidate.flushIOBuffers();
            port = candidate;
            users = 1;
            return port;
        }
    }

    /**
     * Give up this caller's use of the port. The port stays open so the next
     * command - or a cancel - can use it; call shutdown() to close it.
     */
    public static void release() {
        synchronized (portLock) {
            if (users > 0) {
                users--;
            }
        }
    }

    /** Close the port for good. Nothing in a normal transaction flow calls this. */
    public static void shutdown() {
        synchronized (portLock) {
            if (port != null) {
                port.closePort();
            }
            port = null;
            users = 0;
            cancelPending = false;
            cancelReplyPayload = null;
        }
    }

    public static boolean isOpen() {
        synchronized (portLock) {
            return port != null && port.isOpen();
        }
    }

    public static SerialPort current() {
        synchronized (portLock) {
            return port;
        }
    }

    /** Write a command down the open port. */
    public static void write(String command) throws IOException {
        synchronized (portLock) {
            if (port == null || !port.isOpen()) {
                throw new IOException("PAX port is not open");
            }
            OutputStream outputStream = port.getOutputStream();
            outputStream.write(command.getBytes("UTF-8"));
            outputStream.flush();
        }
    }

    public static void beginCancel() {
        cancelReplyPayload = null;
        cancelPending = true;
    }

    public static boolean isCancelPending() {
        return cancelPending;
    }

    /**
     * Called by a transaction read loop for every payload it receives. When a
     * cancel is waiting for its reply and this payload is an ACK or a NAK, the
     * payload is handed to the canceller and true is returned so the
     * transaction keeps waiting for its real response.
     */
    public static boolean isCancelReply(String payload) {
        if (!cancelPending || payload == null) {
            return false;
        }

        try {
            String trimmed = payload.trim();
            int end = trimmed.lastIndexOf('}');
            if (end < 0) {
                return false;
            }
            JSONObject parsed = new JSONObject(trimmed.substring(0, end + 1));
            String dataType = parsed.get("dataType").toString();

            if (dataType.equalsIgnoreCase("ACK") || dataType.equalsIgnoreCase("NAK")) {
                cancelReplyPayload = parsed.toString();
                cancelPending = false;
                return true;
            }
        } catch (Exception ex) {
            return false;
        }

        return false;
    }

    /** Wait up to timeoutMs for the terminal's answer to a cancel. */
    public static String waitForCancelReply(int timeoutMs) {
        int waited = 0;
        while (waited < timeoutMs) {
            String reply = cancelReplyPayload;
            if (reply != null) {
                cancelReplyPayload = null;
                return reply;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            waited += 100;
        }
        cancelPending = false;
        return null;
    }
}
