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
import java.io.DataInputStream;
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
 * Settlement Request (dataType "settleRequest").
 *
 * The command carries no checksum - like "active" and unlike sale/void - and
 * the terminal can take up to 180 seconds to answer, so the response wait is
 * fixed at 180 seconds rather than taken from the caller. Responses are raw
 * JSON only; an encrypted settlement response is reported as NAK 22.
 *
 * LastSettleRequest reuses this class with a different dataType.
 *
 * @author gimhan added
 */
public class SettleRequest extends Utility {

    /** The terminal may take this long to settle every acquirer. */
    public static final int SETTLE_RESPONSE_TIMEOUT_SECONDS = 180;

    String strIP;
    int Port;
    Boolean EnableEncrypt;
    String settleDataType;

    SettleRequest(String strIP, int Port, Boolean EnableEncrypt) {
        this(strIP, Port, EnableEncrypt, "settleRequest");
    }

    SettleRequest() {
        this("settleRequest");
    }

    SettleRequest(String strIP, int Port, Boolean EnableEncrypt, String settleDataType) {
        this.strIP = strIP;
        this.Port = Port;
        this.EnableEncrypt = EnableEncrypt;
        this.settleDataType = settleDataType;
    }

    SettleRequest(String settleDataType) {
        this.settleDataType = settleDataType;
    }

    /** Builds {"data":{"function":"dataType"},"dataType":"dataType"} - no checksum. */
    private String BuildCommand() {
        JSONObject command = new JSONObject();
        JSONObject dataField = new JSONObject();
        dataField.put("function", settleDataType);
        command.put("dataType", settleDataType);
        command.put("data", dataField);
        return command.toString() + "\n";
    }

    private JSONObject Nak(String errorCode) {
        JSONObject error = new JSONObject();
        error.put("dataType", "NAK");
        error.put("error", errorCode);
        return error;
    }

    //usb serial communication settlement
    public JSONObject Settle_USB() {

        String TAG = settleDataType + "_USB";

        LogWrite("LogStart", TAG, "Function Starting");

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

            String SettleCmdStr = BuildCommand();

            LogWrite("NormalLog", TAG, "Settle Requested cmd json format :" + SettleCmdStr.trim());

            port.flushIOBuffers();

            try {
                PaxPort.write(SettleCmdStr);
            } catch (IOException ex) {
                LogWrite("NormalLog", TAG, "Com Port Write Exception :" + ex.getMessage());
                return Nak("17");
            }

            //ACK, up to 5 seconds
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

            String SettleAckStr;
            try {
                SettleAckStr = new String(AckReadBuffer, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                LogWrite("NormalLog", TAG, "Byte to String convert error :" + ex.getMessage());
                return Nak("19");
            }

            if (SettleAckStr.length() > 10) {

                LogWrite("NormalLog", TAG, "Terminal ACK Response :" + SettleAckStr);

                JSONObject obj = new JSONObject(SettleAckStr);
                String data = obj.get("dataType").toString();

                //A910 answers ACK, A920 answers ack
                if (data.equalsIgnoreCase("ACK")) {
                    LogWrite("NormalLog", TAG, "Sent Command to terminal successfully");
                } else {
                    LogWrite("NormalLog", TAG, "Terminal Side error code json found :" + SettleAckStr);
                    return new JSONObject(SettleAckStr);
                }
            } else {
                LogWrite("NormalLog", TAG, "Terminal Response not valid :" + SettleAckStr);
                return Nak("13");
            }

            port.flushIOBuffers();

            //settlement result, up to 180 seconds
            byte[] SettleReadBuffer = new byte[4096];
            boolean check1 = false;
            int y = 0;
            String SettleStr = "";

            while (check1 == false && y < SETTLE_RESPONSE_TIMEOUT_SECONDS) {

                int numRead = port.readBytes(SettleReadBuffer, SettleReadBuffer.length);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }

                if (numRead > 10) {

                    try {
                        SettleStr = new String(SettleReadBuffer, "UTF-8");
                    } catch (UnsupportedEncodingException ex) {
                        LogWrite("NormalLog", TAG, "Byte to String convert error :" + ex.getMessage());
                        return Nak("19");
                    }

                    //a cancel running on another thread owns any ACK/NAK that lands here
                    if (PaxPort.isCancelReply(SettleStr)) {
                        LogWrite("NormalLog", TAG, "Cancel reply routed to the cancel caller, still waiting for the settlement result");
                        SettleStr = "";
                    } else {
                        check1 = true;
                    }
                }
                y++;
            }

            if (!check1) {
                LogWrite("NormalLog", TAG, "Terminal Response Timeout");
                return Nak("20");
            }

            LogWrite("NormalLog", TAG, "Terminal Process Response :" + SettleStr);

            return ParseSettleResponse(TAG, SettleStr);

        } finally {
            PaxPort.release();
        }
    }

    //TCP communication settlement
    public JSONObject Settle_TCP() {

        String TAG = settleDataType + "_TCP";

        LogWrite("LogStart", TAG, "Function Starting Ip: " + strIP + " Port:" + Port);

        Socket socket = null;

        try {

            String SettleCmdStr = BuildCommand();

            LogWrite("NormalLog", TAG, "Settle Requested cmd json format :" + SettleCmdStr.trim());

            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(strIP, Port);
            socket.connect(socketAddress, 5000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            writer.print(SettleCmdStr);
            writer.flush();

            BufferedReader SettleAck = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] Ackbuffer = new char[2048];
            socket.setSoTimeout(5000);
            SettleAck.read(Ackbuffer);
            String SettleAckStr = new String(Ackbuffer);

            if (SettleAckStr.length() > 10) {

                LogWrite("NormalLog", TAG, "Terminal ACK Response :" + SettleAckStr);

                JSONObject obj = new JSONObject(SettleAckStr);
                String data = obj.get("dataType").toString();

                if (data.equalsIgnoreCase("ACK")) {
                    LogWrite("NormalLog", TAG, "Sent Command to terminal successfully");
                } else {
                    LogWrite("NormalLog", TAG, "Terminal Side error code json found :" + SettleAckStr);
                    return new JSONObject(SettleAckStr);
                }
            } else {
                LogWrite("NormalLog", TAG, "Terminal Response not valid :" + SettleAckStr);
                return Nak("13");
            }

            DataInputStream inputStream = new DataInputStream(socket.getInputStream());

            socket.setSoTimeout(SETTLE_RESPONSE_TIMEOUT_SECONDS * 1000);
            String SettleStr = "";

            int c;
            do {
                c = inputStream.read();
                SettleStr += (char) c;
            } while (inputStream.available() > 0);

            socket.setSoTimeout(1000);

            try {
                int d;
                do {
                    d = inputStream.read();
                    SettleStr += (char) d;
                } while (inputStream.available() > 0);
            } catch (Exception ex) {
            }

            if (SettleStr.length() > 10) {

                LogWrite("NormalLog", TAG, "Terminal Process Response :" + SettleStr);

                return ParseSettleResponse(TAG, SettleStr);
            }

            LogWrite("NormalLog", TAG, "Terminal Response not valid :" + SettleStr);
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

    /** Raw JSON only: verify the dataType, drop the checksum, hand the rest back. */
    private JSONObject ParseSettleResponse(String TAG, String SettleStr) {

        JSONObject JsonResponseObject;

        try {
            int end = SettleStr.lastIndexOf('}');
            JsonResponseObject = new JSONObject(SettleStr.substring(0, end + 1));
        } catch (Exception ex) {
            LogWrite("NormalLog", TAG, "Client requested Raw Response, Terminal Response not raw json :" + ex.getMessage());
            return Nak("22");
        }

        String DataTypeFieldResponse = JsonResponseObject.get("dataType").toString();

        if (!DataTypeFieldResponse.equals(settleDataType)) {
            LogWrite("NormalLog", TAG, "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
            return Nak("12");
        }

        JsonResponseObject.remove("checksum");
        return JsonResponseObject;
    }
}
