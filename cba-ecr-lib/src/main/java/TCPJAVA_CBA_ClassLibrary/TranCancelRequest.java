/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TCPJAVA_CBA_ClassLibrary;

import TCPJAVA_CBA_ClassLibrary.Communication.PaxPort;
import TCPJAVA_CBA_ClassLibrary.Communication.PortMatch;   // ISO8583Studio patch
import com.fazecast.jSerialComm.SerialPort;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import org.json.JSONObject;

/**
 * Transaction Cancel (dataType "tranCancel").
 *
 * Asks the terminal to terminate the transaction it is running. The command
 * carries no checksum and the terminal answers with an ACK only - there is no
 * second response - after which the cancelled transaction itself completes with
 * rspCode CN (cancelled) or ER.
 *
 * This must be called from a different thread from the transaction it cancels.
 * On USB the transaction owns the open port, so the cancel is written down that
 * same port under PaxPort.portLock and the transaction's read loop routes the
 * cancel's ACK back here through PaxPort.isCancelReply.
 *
 * Cancellation is not always possible: once the card has been tapped the
 * terminal may refuse, and if it cannot terminate the transaction it may not
 * answer the cancel at all.
 *
 * @author gimhan added
 */
public class TranCancelRequest extends Utility {

    /** How long to wait for the terminal to acknowledge a cancel. */
    public static final int CANCEL_ACK_TIMEOUT_MS = 5000;

    String strIP;
    int Port;

    TranCancelRequest(String strIP, int Port) {
        this.strIP = strIP;
        this.Port = Port;
    }

    TranCancelRequest() {
    }

    /** Builds {"data":{"function":"tranCancel"},"dataType":"tranCancel"} - no checksum. */
    private String BuildCommand() {
        JSONObject command = new JSONObject();
        JSONObject dataField = new JSONObject();
        dataField.put("function", "tranCancel");
        command.put("dataType", "tranCancel");
        command.put("data", dataField);
        return command.toString() + "\n";
    }

    private JSONObject Nak(String errorCode) {
        JSONObject error = new JSONObject();
        error.put("dataType", "NAK");
        error.put("error", errorCode);
        return error;
    }

    //usb serial communication transaction cancel
    public JSONObject TranCancel_USB() {

        String TAG = "tranCancel_USB";

        LogWrite("LogStart", TAG, "Function Starting");

        String CancelCmdStr = BuildCommand();

        //a transaction is in flight and owns the port: write the cancel down it
        synchronized (PaxPort.portLock) {

            if (PaxPort.isOpen()) {

                LogWrite("NormalLog", TAG, "Port busy with a transaction, sending cancel on the open port :" + CancelCmdStr.trim());

                PaxPort.beginCancel();

                try {
                    PaxPort.write(CancelCmdStr);
                } catch (IOException ex) {
                    LogWrite("NormalLog", TAG, "Com Port Write Exception :" + ex.getMessage());
                    return Nak("17");
                }
            } else {
                return TranCancelOnIdlePort(TAG, CancelCmdStr);
            }
        }

        //outside the lock so the transaction thread can keep reading
        String cancelReply = PaxPort.waitForCancelReply(CANCEL_ACK_TIMEOUT_MS);

        if (cancelReply != null) {
            LogWrite("NormalLog", TAG, "Terminal ACK Response :" + cancelReply);
            return new JSONObject(cancelReply);
        }

        LogWrite("NormalLog", TAG, "Cancel delivered, terminal reply not seen");

        JSONObject deliveredResponse = new JSONObject();
        JSONObject deliveredData = new JSONObject();
        deliveredData.put("function", "tranCancel");
        deliveredData.put("note", "cancel delivered, terminal reply not seen");
        deliveredResponse.put("dataType", "ACK");
        deliveredResponse.put("data", deliveredData);
        return deliveredResponse;
    }

    /** No transaction running: open the port ourselves and send the cancel. */
    private JSONObject TranCancelOnIdlePort(String TAG, String CancelCmdStr) {

        String ComPort = PaxPort.discover();

        if (!PortMatch.isUsablePortName(ComPort)) {   // ISO8583Studio patch: was ComPort.contains("COM")
            LogWrite("NormalLog", TAG, "Pax Com Port Not Found");
            return Nak("15");
        }

        SerialPort port = PaxPort.acquire(ComPort);

        if (port == null) {
            LogWrite("NormalLog", TAG, "Com Port Open Error :" + ComPort);
            return Nak("16");
        }

        try {

            LogWrite("NormalLog", TAG, "Cancel Requested cmd json format :" + CancelCmdStr.trim());

            port.flushIOBuffers();

            try {
                PaxPort.write(CancelCmdStr);
            } catch (IOException ex) {
                LogWrite("NormalLog", TAG, "Com Port Write Exception :" + ex.getMessage());
                return Nak("17");
            }

            byte[] AckReadBuffer = new byte[2048];
            boolean check = false;
            int x = 0;

            while (check == false && x < 50) {

                int numRead = port.readBytes(AckReadBuffer, AckReadBuffer.length);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }

                if (numRead > 10) {
                    check = true;
                }
                x++;
            }

            if (!check) {
                LogWrite("NormalLog", TAG, "ACK Timeout");
                return Nak("18");
            }

            String CancelAckStr;
            try {
                CancelAckStr = new String(AckReadBuffer, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                LogWrite("NormalLog", TAG, "Byte to String convert error :" + ex.getMessage());
                return Nak("19");
            }

            if (CancelAckStr.length() > 10) {
                LogWrite("NormalLog", TAG, "Terminal ACK Response :" + CancelAckStr);
                int end = CancelAckStr.lastIndexOf('}');
                return new JSONObject(CancelAckStr.substring(0, end + 1));
            }

            LogWrite("NormalLog", TAG, "Terminal Response not valid :" + CancelAckStr);
            return Nak("13");

        } finally {
            PaxPort.release();
        }
    }

    //TCP communication transaction cancel
    public JSONObject TranCancel_TCP() {

        String TAG = "tranCancel_TCP";

        LogWrite("LogStart", TAG, "Function Starting Ip: " + strIP + " Port:" + Port);

        Socket socket = null;

        try {

            String CancelCmdStr = BuildCommand();

            LogWrite("NormalLog", TAG, "Cancel Requested cmd json format :" + CancelCmdStr.trim());

            //the cancel gets its own connection, the transaction keeps its own
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(strIP, Port);
            socket.connect(socketAddress, 5000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            writer.print(CancelCmdStr);
            writer.flush();

            BufferedReader cancelAck = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] Ackbuffer = new char[2048];
            socket.setSoTimeout(CANCEL_ACK_TIMEOUT_MS);
            cancelAck.read(Ackbuffer);
            String CancelAckStr = new String(Ackbuffer);

            if (CancelAckStr.length() > 10) {
                LogWrite("NormalLog", TAG, "Terminal ACK Response :" + CancelAckStr);
                int end = CancelAckStr.lastIndexOf('}');
                return new JSONObject(CancelAckStr.substring(0, end + 1));
            }

            LogWrite("NormalLog", TAG, "Terminal Response not valid :" + CancelAckStr);
            return Nak("13");

        } catch (UnknownHostException ex) {
            LogWrite("NormalLog", TAG, "TCP UnknownHost Error :" + ex.getMessage());
            return Nak("14");
        } catch (IOException ex) {
            LogWrite("NormalLog", TAG, "TCP IOException Error :" + ex.getMessage());
            return Nak("14");
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                }
            }
        }
    }
}
