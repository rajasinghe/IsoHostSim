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
import java.io.InputStream;
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
 * @author HP PC
 */
public class CheckStatusRequest extends Utility {

    String strIP;
    int Port;

    Boolean isA920 = false;

    CheckStatusRequest() {

    }

    CheckStatusRequest(String strIP, int Port) {
        this.strIP = strIP;
        this.Port = Port;
    }

    //TCP communication check status
    public JSONObject CheckStatus_TCP() {

        //LogWrite("LogStart", "CheckStatus_TCP", "Function Starting Ip: " + strIP + " Port:" + Port);

        Socket socket = null;

        try {

            LogWrite("LogStart", "CheckStatus_TCP", "Function Starting Ip: " + strIP + " Port:" + Port);

            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(strIP, Port);
            socket.connect(socketAddress, 5000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            JSONObject obj = new JSONObject();
            obj.put("dataType", "active");

            String tmp = obj.toString();
            writer.println(tmp);
            writer.flush();

            BufferedReader checkstatus = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] buffer = new char[2048];
            String checkStatusStr = "";

            socket.setSoTimeout(3000);

            checkstatus.read(buffer);

            checkStatusStr = new String(buffer);
            if (checkStatusStr.length() > 10) {

                LogWrite("NormalLog", "CheckStatus_TCP", "Terminal ACK Response :" + checkStatusStr);

                JSONObject JsonResponseObject = new JSONObject(checkStatusStr);
                String data = JsonResponseObject.get("dataType").toString();
                if (data.equals("ACK")) {
                    LogWrite("NormalLog", "CheckStatus_TCP", "Terminal Status Ok");
                } else {
                    LogWrite("NormalLog", "CheckStatus_TCP", "Terminal Side error code json found :" + checkStatusStr);
                }
                socket.close();
                return JsonResponseObject;

            } else {

                LogWrite("NormalLog", "CheckStatus_TCP", "Terminal Response not valid :" + checkStatusStr);
                JSONObject ReceiveError = new JSONObject();
                ReceiveError.put("dataType", "NAK");
                ReceiveError.put("error", "13");
                socket.close();
                return ReceiveError;
            }

        } catch (UnknownHostException ex) {

            LogWrite("NormalLog", "CheckStatus_TCP", "TCP UnknownHostException Error --> Recieved data :" + ex.getMessage());
            JSONObject ExceptionError = new JSONObject();
            ExceptionError.put("dataType", "NAK");
            ExceptionError.put("error", "14");
            try {
                socket.close();
            } catch (IOException ex1) {

            }
            return ExceptionError;

        } catch (IOException ex) {

            LogWrite("NormalLog", "CheckStatus_TCP", "TCP IOException Error --> Recieved data :" + ex.getMessage());
            JSONObject ExceptionError = new JSONObject();
            ExceptionError.put("dataType", "NAK");
            ExceptionError.put("error", "14");
            try {
                socket.close();
            } catch (IOException ex1) {

            }
            return ExceptionError;
        }

    }

    //usb serial communication check status
    public JSONObject CheckStatus_USB() {

        LogWrite("LogStart", "CheckStatus_USB", "Function Starting");

        //fIND PAX COM PORT
        String ComPort = "";

        ComPort = PaxPort.discover();   //gimhan added - shared port owner, port stays open so a cancel can use it

        if (PortMatch.isUsablePortName(ComPort)) {   // ISO8583Studio patch: was ComPort.contains("COM")
        } else {
            LogWrite("NormalLog", "CheckStatus_USB", "Pax Com Port Not Found");
            JSONObject PortOpenError = new JSONObject();
            PortOpenError.put("dataType", "NAK");
            PortOpenError.put("error", "15");
            return PortOpenError;
        }

        OutputStream outputStream;

        SerialPort port = PaxPort.acquire(ComPort);   //gimhan added - shared port owner, port stays open so a cancel can use it

        if (port == null) {

                LogWrite("NormalLog", "CheckStatus_USB", "Com Port Open Error :" + ComPort);
                JSONObject PortOpenError = new JSONObject();
                PortOpenError.put("dataType", "NAK");
                PortOpenError.put("error", "16");
                return PortOpenError;

        }

        port.flushIOBuffers();

        outputStream = port.getOutputStream();

        port.flushIOBuffers();

        JSONObject obj = new JSONObject();
        obj.put("dataType", "active");

        String CheckStatusCmdStr1 = obj.toString();
        String CheckStatusCmdStr = CheckStatusCmdStr1 + "\n";

        try {
            outputStream.write(CheckStatusCmdStr.getBytes());
        } catch (IOException ex) {
            LogWrite("NormalLog", "CheckStatus_USB", "Com Port Write Exception :" + ex.getMessage());
            JSONObject ExceptionError = new JSONObject();
            ExceptionError.put("dataType", "NAK");
            ExceptionError.put("error", "17");
            PaxPort.release();
            return ExceptionError;
        }

        InputStream in = port.getInputStream();

        byte[] readBuffer = new byte[2048];
        boolean check = false;
        int x = 0;

        while (check == false && x < 50) {

            int numRead = port.readBytes(readBuffer,
                    readBuffer.length);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            if (numRead > 10) {
                //System.out.print("Read " + numRead + " bytes -");
                check = true;
            }
            x++;
        }

        if (x == 50) {
            LogWrite("NormalLog", "CheckStatus_USB", "ACK Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "18");
            PaxPort.release();
            return TimeOutError;
        }

        String CheckStatusStr = "";
        try {
            CheckStatusStr = new String(readBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            LogWrite("NormalLog", "CheckStatus_USB", "Byte to String convert error :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");
            PaxPort.release();
            return TimeOutError;
        }

        if (CheckStatusStr.length() > 10) {

            LogWrite("NormalLog", "CheckStatus_USB", "Terminal ACK Response :" + CheckStatusStr);

            JSONObject JsonResponseObject = new JSONObject(CheckStatusStr);
            String data = JsonResponseObject.get("dataType").toString();

            if (data.equals("ACK")) {
                isA920 = false;
                LogWrite("NormalLog", "CheckStatus_USB", "Terminal Status Ok(A920)");

                JSONObject checkstatusResponse = new JSONObject();
                checkstatusResponse.put("dataType", "ACK");

                PaxPort.release();
                return checkstatusResponse;

            } else if (data.equals("ack")) {
                isA920 = true;
                LogWrite("NormalLog", "CheckStatus_USB", "Terminal Status Ok(A910)");

                JSONObject checkstatusResponse = new JSONObject();
                checkstatusResponse.put("dataType", "ACK");

                PaxPort.release();
                return checkstatusResponse;

            } else {

                LogWrite("NormalLog", "CheckStatus_USB", "Terminal Side error code json found :" + CheckStatusStr);

            }

            JSONObject JsonResponseObject_CheckStatus = new JSONObject(CheckStatusStr);
            PaxPort.release();
            return JsonResponseObject_CheckStatus;

        } else {
            LogWrite("NormalLog", "CheckStatus_USB", "Terminal Response not valid :" + CheckStatusStr);
            JSONObject AckReceiveError = new JSONObject();
            AckReceiveError.put("dataType", "NAK");
            AckReceiveError.put("error", "13");
            PaxPort.release();
            return AckReceiveError;
        }

    }

}
