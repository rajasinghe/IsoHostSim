/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TCPJAVA_CBA_ClassLibrary;

import TCPJAVA_CBA_ClassLibrary.Communication.PaxPort;
import TCPJAVA_CBA_ClassLibrary.Communication.PortMatch;   // POS Testing Platform patch
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
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 */
public class BinRequest extends Utility {

    String strIP;
    int Port;
    JSONObject BinRequestJsonObjectCommand;
    Boolean EnableEncrypt;
    int intTo;
    Boolean isA920 = false;

    BinRequest(String strIP, int Port, JSONObject BinRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        this.strIP = strIP;
        this.Port = Port;
        this.BinRequestJsonObjectCommand = BinRequestJsonObjectCommand;
        this.EnableEncrypt = EnableEncrypt;
        this.intTo = intTo;
    }

    BinRequest(JSONObject BinRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        this.BinRequestJsonObjectCommand = BinRequestJsonObjectCommand;
        this.EnableEncrypt = EnableEncrypt;
        this.intTo = intTo;
    }

    //TCP communication RequestBin
    public JSONObject RequestBin_TCP() {

        LogWrite("LogStart", "RequestBin_TCP", "Function Starting Ip: " + strIP + " Port:" + Port);
        
        Socket socket = null;

        try {

            String DataTypeFieldRequest = BinRequestJsonObjectCommand.getString("dataType");

            if (!DataTypeFieldRequest.equals("binRequest")) {

                LogWrite("NormalLog", "RequestBin_TCP", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
                JSONObject RequestClientDataTypeError = new JSONObject();
                RequestClientDataTypeError.put("dataType", "NAK");
                RequestClientDataTypeError.put("error", "10");
                return RequestClientDataTypeError;
            }

            JSONObject DataFieldRequest = BinRequestJsonObjectCommand.getJSONObject("data");

            String DataFieldEncoded = sha256(DataFieldRequest.toString());

            if (DataFieldEncoded.length() > 10) {
                BinRequestJsonObjectCommand.put("checksum", DataFieldEncoded);
            } else {

                LogWrite("NormalLog", "RequestBin_TCP", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
                JSONObject CheckSumCalculateError = new JSONObject();
                CheckSumCalculateError.put("dataType", "NAK");
                CheckSumCalculateError.put("error", "11");
                return CheckSumCalculateError;
            }

            String tmp = BinRequestJsonObjectCommand.toString();
            
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(strIP, Port);
            socket.connect(socketAddress, 5000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
                           
            writer.println(tmp);
            writer.flush();

            BufferedReader binReuestAck = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] Ackbuffer = new char[2048];
            String binReuestAckStr = "";

            socket.setSoTimeout(3000);

            binReuestAck.read(Ackbuffer);

            binReuestAckStr = new String(Ackbuffer);

            if (binReuestAckStr.length() > 10) {
                            
                LogWrite("NormalLog", "RequestBin_TCP", "Terminal ACK Response :" + binReuestAckStr);
                
                JSONObject obj = new JSONObject(binReuestAckStr);
                String data = obj.get("dataType").toString();

                if (data.equals("ACK")) {
                    LogWrite("NormalLog", "RequestBin_TCP", "Sent Command to terminal successfully");
                } else {

                    LogWrite("NormalLog", "RequestBin_TCP", "Terminal Side error code json found :" + binReuestAckStr);
                    JSONObject JsonResponseObject = new JSONObject(binReuestAckStr);
                    socket.close();
                    return JsonResponseObject;
                }
            } else {

                LogWrite("NormalLog", "RequestBin_TCP", "Terminal Response not valid :" + binReuestAckStr);
                JSONObject AckReceiveError = new JSONObject();
                AckReceiveError.put("dataType", "NAK");
                AckReceiveError.put("error", "13");
                socket.close();
                return AckReceiveError;
            }

            DataInputStream BinRequest = new DataInputStream(socket.getInputStream());

            socket.setSoTimeout(intTo * 1000);
            String BinRequestStr = "";
            int c;
            do {
                c = BinRequest.read();
                BinRequestStr += (char) c;
            } while (BinRequest.available() > 0);

            socket.setSoTimeout(1000);

            // POS Testing Platform patch: the original always made one more read with a 1 s timeout here,
            // so every BIN response came back ~1 s late. Skip it once a complete JSON object has
            // arrived, and otherwise keep reading (1 s of silence ends it, as before) until it is
            // complete. Encrypted (non-JSON) responses still get the original 1 s drain.
            try {

                int d = 0;
                while (!JsonFrame.isCompleteJson(BinRequestStr) && c != -1 && d != -1) {
                    do {
                        d = BinRequest.read();
                        BinRequestStr += (char) d;

                    } while (d != -1 && BinRequest.available() > 0);
                    if (!BinRequestStr.trim().startsWith("{")) {
                        break;   // encrypted response: one drain read, exactly like the original
                    }
                }

            } catch (Exception ex) {

            }

            //System.out.println("Data :" + BinRequestStr);

            if (BinRequestStr.length() > 10) {
                
                LogWrite("NormalLog", "RequestBin_TCP", "Terminal Process Response :" + BinRequestStr);

                boolean encryptResponeFoundFromTerminal = false;
                boolean RawResponseFoundFromTerminal = false;

                try {

                    JSONObject JsonResponseObject = new JSONObject(BinRequestStr);
                    String CheckEncryptDecrypt = JsonResponseObject.getString("dataType");
                    RawResponseFoundFromTerminal = true;

                } catch (JSONException ex) {
                    encryptResponeFoundFromTerminal = true;
                }

                if (EnableEncrypt) {

                    if (encryptResponeFoundFromTerminal) {

                    } else if (RawResponseFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestBin_TCP", "Client requested Encrypt Response, Terminal Response Raw Response --> Terminal Response :" + BinRequestStr);
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "21");
                        socket.close();
                        return ResponseEncryptDecryptError;
                    }

                } else {
                    if (encryptResponeFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestBin_TCP", "Client requested Raw Response, Terminal Response Encrypted Response");
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "22");
                        socket.close();
                        return ResponseEncryptDecryptError;

                    } else if (RawResponseFoundFromTerminal) {

                        JSONObject JsonResponseObject = new JSONObject(BinRequestStr);
                        String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                        if (!DataTypeFieldResponse.equals("binRequest")) {

                            LogWrite("NormalLog", "RequestBin_TCP", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                            JSONObject ResponseClientDataTypeError = new JSONObject();
                            ResponseClientDataTypeError.put("dataType", "NAK");
                            ResponseClientDataTypeError.put("error", "12");
                            socket.close();
                            return ResponseClientDataTypeError;
                        }

                        String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                        JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                        String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                        JSONObject JsonResponseObject_bin = new JSONObject(BinRequestStr);
                        JsonResponseObject_bin.remove("checksum");

                        LogWrite("NormalLog", "RequestBin_TCP", "Sent Terminal Response(raw) to Client successfully");
                        socket.close();
                        return JsonResponseObject_bin;

                    }
                }

                String DecryptedKeyString = GetKeyFromLog();

                if (DecryptedKeyString.equals("")) {

                    LogWrite("NormalLog", "RequestBin_USB", "Decryption error log file key");
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "23");
                    socket.close();
                    return KeyEncryptError;
                }

                DecryptedKeyString = DecryptedKeyString + DecryptedKeyString.substring(0, 16);

                byte[] resByteArray = null;

                try {
                    resByteArray = decrypt3Des(BinRequestStr, DecryptedKeyString);
                } catch (Exception ex) {

                    LogWrite("NormalLog", "RequestBin_USB", "Terminal Response Decryption Error --> Exception Error :" + ex.getMessage());
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    socket.close();
                    return ResponseClientDataTypeError;
                }

                if (resByteArray == null) {

                    LogWrite("NormalLog", "RequestBin_USB", "Terminal Response Decryption null Error ");
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    socket.close();
                    return ResponseClientDataTypeError;
                }

                String BinRequestEncryptedRawData = new String(resByteArray);

                String WithoutEncryptData = BinRequestEncryptedRawData.substring(64, BinRequestEncryptedRawData.length());

                JSONObject JsonResponseObject = new JSONObject(WithoutEncryptData);
                String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                if (!DataTypeFieldResponse.equals("binRequest")) {

                    LogWrite("NormalLog", "RequestBin_TCP", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "12");
                    socket.close();
                    return ResponseClientDataTypeError;
                }

                String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                JSONObject JsonResponseObject_bin = new JSONObject(WithoutEncryptData);
                JsonResponseObject_bin.remove("checksum");

                LogWrite("NormalLog", "RequestBin_TCP", "Sent Terminal Response(Encrypted) to Client successfully");
                socket.close();
                return JsonResponseObject_bin;

            } else {
                LogWrite("NormalLog", "RequestBin_TCP", "Terminal Final Response Received data not valid --> Recieved data :" + BinRequestStr);
                JSONObject ReceiveError = new JSONObject();
                ReceiveError.put("dataType", "NAK");
                ReceiveError.put("error", "13");
                socket.close();
                return ReceiveError;
            }

        } catch (UnknownHostException ex) {

            LogWrite("NormalLog", "RequestBin_TCP", "TCP UnknownHostException Error --> Recieved data :" + ex.getMessage());
            JSONObject ExceptionError = new JSONObject();
            ExceptionError.put("dataType", "NAK");
            ExceptionError.put("error", "14");
            try {
                socket.close();
            } catch (IOException ex1) {
                
            }
            return ExceptionError;

        } catch (IOException ex) {

            LogWrite("NormalLog", "RequestBin_TCP", "TCP IOException Error --> Recieved data :" + ex.getMessage());
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

    //usb serial communication RequestBin
    public JSONObject RequestBin_USB() {
        LogWrite("LogStart", "RequestBin_USB", "Function Starting");

        //fIND PAX COM PORT
        String ComPort = "";

        ComPort = PaxPort.discover();   // shared port owner, port stays open so a cancel can use it

        if (PortMatch.isUsablePortName(ComPort)) {   // POS Testing Platform patch: was ComPort.contains("COM")

        } else {
            LogWrite("NormalLog", "RequestBin_USB", "Pax Com Port Not Found");
            JSONObject PortOpenError = new JSONObject();
            PortOpenError.put("dataType", "NAK");
            PortOpenError.put("error", "15");
            return PortOpenError;
        }

        OutputStream outputStream;

        SerialPort port = PaxPort.acquire(ComPort);   // shared port owner, port stays open so a cancel can use it

        if (port == null) {

                LogWrite("NormalLog", "RequestBin_USB", "Com Port Open Error :" + ComPort);
                JSONObject PortOpenError = new JSONObject();
                PortOpenError.put("dataType", "NAK");
                PortOpenError.put("error", "16");
                return PortOpenError;

        }

        String DataTypeFieldRequest = BinRequestJsonObjectCommand.getString("dataType");

        if (!DataTypeFieldRequest.equals("binRequest")) {

            LogWrite("NormalLog", "RequestBin_USB", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
            JSONObject RequestClientDataTypeError = new JSONObject();
            RequestClientDataTypeError.put("dataType", "NAK");
            RequestClientDataTypeError.put("error", "10");
            PaxPort.release();
            return RequestClientDataTypeError;
        }

        JSONObject DataFieldRequest = BinRequestJsonObjectCommand.getJSONObject("data");

        String DataFieldEncoded = sha256(DataFieldRequest.toString());

        if (DataFieldEncoded.length() > 10) {
            BinRequestJsonObjectCommand.put("checksum", DataFieldEncoded);
        } else {

            LogWrite("NormalLog", "RequestBin_USB", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
            JSONObject CheckSumCalculateError = new JSONObject();
            CheckSumCalculateError.put("dataType", "NAK");
            CheckSumCalculateError.put("error", "11");
            PaxPort.release();
            return CheckSumCalculateError;
        }

        String BinRequestCmdStr1 = BinRequestJsonObjectCommand.toString();
        String BinRequestCmdStr = BinRequestCmdStr1 + "\n";

        outputStream = port.getOutputStream();
        port.flushIOBuffers();

        try {
            outputStream.write(BinRequestCmdStr.getBytes());
        } catch (IOException ex) {

            LogWrite("NormalLog", "RequestBin_USB", "Com Port Write Exception :" + ex.getMessage());
            JSONObject ExceptionError = new JSONObject();
            ExceptionError.put("dataType", "NAK");
            ExceptionError.put("error", "17");
            PaxPort.release();
            return ExceptionError;
        }

        byte[] AckReadBuffer = new byte[2048];
        boolean check = false;
        int x = 0;

        while (check == false && x < 50) {

            int numRead = port.readBytes(AckReadBuffer,
                    AckReadBuffer.length);
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

            LogWrite("NormalLog", "RequestBin_USB", "ACK Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "18");
            PaxPort.release();
            return TimeOutError;
        }

        String BinRequestAckStr = "";
        try {
            BinRequestAckStr = new String(AckReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            LogWrite("NormalLog", "RequestBin_USB", "Byte to String convert error :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");
            PaxPort.release();
            return TimeOutError;
        }

        //System.out.println("Received -> "+ BinRequestAckStr);
        if (BinRequestAckStr.length() > 10) {
            
            LogWrite("NormalLog", "RequestBin_USB", "Terminal ACK Response :" + BinRequestAckStr);
            
            JSONObject obj = new JSONObject(BinRequestAckStr);
            String data = obj.get("dataType").toString();

            if (data.equals("ACK")) {
                isA920 = false;
                LogWrite("NormalLog", "RequestBin_USB", "Sent Command to terminal successfully(A920)");                
            }else if(data.equals("ack")){   
                isA920 = true;
                LogWrite("NormalLog", "RequestBin_USB", "Sent Command to terminal successfully(A910)"); 
            } else {
                LogWrite("NormalLog", "RequestBin_USB", "Terminal Side error code json found :" + BinRequestAckStr);
                JSONObject JsonResponseObject = new JSONObject(BinRequestAckStr);
                PaxPort.release();
                return JsonResponseObject;
            }
        } else {

            LogWrite("NormalLog", "RequestBin_USB", "Terminal Response not valid :" + BinRequestAckStr);
            JSONObject AckReceiveError = new JSONObject();
            AckReceiveError.put("dataType", "NAK");
            AckReceiveError.put("error", "13");
            PaxPort.release();
            return AckReceiveError;
        }

        port.flushIOBuffers();

        byte[] BinRequestReadBuffer = new byte[2048];
        boolean check1 = false;
        int y = 0;
        int numRead = 0;

        // POS Testing Platform patch: the original read once a second and then always slept 1 s before
        // looking at the data, adding 1-2 s to every BIN response. Now the port is polled every
        // 100 ms, chunks are appended, and the loop ends as soon as a complete JSON object is in
        // the buffer, or 1 s after the first data arrived (the original wait, used for encrypted
        // or incomplete responses). The overall limit is still intTo seconds.
        byte[] BinRequestChunk = new byte[2048];
        int BinRequestTotal = 0;
        int ticksSinceFirstData = -1;
        final int ticksPerSecond = 10;

        while (check1 == false && y < intTo * ticksPerSecond) {

            numRead = port.readBytes(BinRequestChunk, BinRequestChunk.length);
            if (numRead > 0) {
                int copy = Math.min(numRead, BinRequestReadBuffer.length - BinRequestTotal);
                System.arraycopy(BinRequestChunk, 0, BinRequestReadBuffer, BinRequestTotal, copy);
                BinRequestTotal += copy;
            }

            if (BinRequestTotal > 10) {
                String received = new String(BinRequestReadBuffer, 0, BinRequestTotal, java.nio.charset.StandardCharsets.UTF_8);

                // a cancel running on another thread owns any ACK/NAK that lands here
                if (PaxPort.isCancelReply(received)) {
                    java.util.Arrays.fill(BinRequestReadBuffer, (byte) 0);
                    BinRequestTotal = 0;
                    ticksSinceFirstData = -1;
                    y++;
                    continue;
                }

                if (ticksSinceFirstData < 0) {
                    ticksSinceFirstData = 0;
                }
                if (JsonFrame.isCompleteJson(received) || ticksSinceFirstData >= ticksPerSecond
                        || BinRequestTotal >= BinRequestReadBuffer.length) {
                    check1 = true;
                }
            }

            if (!check1) {
                try {
                    Thread.sleep(1000 / ticksPerSecond);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                if (ticksSinceFirstData >= 0) {
                    ticksSinceFirstData++;
                }
                y++;
                continue;
            }

            {
                //System.out.print("Read " + numRead + " bytes -");

                if(isA920){
                    
                    JSONObject confirmResponse = new JSONObject();
                    confirmResponse.put("dataType", "ack");
                    
                    String confirmResponseStr1 = confirmResponse.toString();
                    String confirmResponseStr = confirmResponseStr1 + "\n";
                    
                    try {
                        outputStream.write(confirmResponseStr.getBytes());
                    } catch (IOException ex) {

                        LogWrite("NormalLog", "RequestBin_USB", "A920 confirm msg write exception :" + ex.getMessage());
                        JSONObject ExceptionError = new JSONObject();
                        ExceptionError.put("dataType", "NAK");
                        ExceptionError.put("error", "17");
                        PaxPort.release();
                        return ExceptionError;
                    }                  
                }               
            }
            y++;
        }

        if (!check1) {   // POS Testing Platform patch: was (y == intTo), which also flagged a response read on the last poll as a timeout

            LogWrite("NormalLog", "RequestBin_USB", "Terminal Response Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "20");

            PaxPort.release();
            return TimeOutError;
        }

        String BinRequestStr = "";
        try {
            BinRequestStr = new String(BinRequestReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {

            LogWrite("NormalLog", "RequestBin_USB", "Terminal Response bytes to string convert Exception :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");

            PaxPort.release();
            return TimeOutError;
        }

        //System.out.println("Received -> "+ BinRequestStr);
        if (BinRequestStr.length() > 10) {
            
            if(isA920){
                
                LogWrite("NormalLog", "RequestBin_USB", "Terminal Process Response(A920) :" + BinRequestStr);
                
                JSONObject JsonResponseObject = new JSONObject(BinRequestStr);
                String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                if (!DataTypeFieldResponse.equals("binRequest")) {

                    LogWrite("NormalLog", "RequestBin_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "12");
                    PaxPort.release();
                    return ResponseClientDataTypeError;
                }

                JSONObject JsonResponseObject_Requestbin = new JSONObject(BinRequestStr);
                JsonResponseObject_Requestbin.put("deviceType", "A920");
                LogWrite("NormalLog", "RequestBin_USB", "Sent Terminal Response(raw) to Client successfully(A920)");
                PaxPort.release();
                return JsonResponseObject_Requestbin;
                              
            }else{
                                            
                LogWrite("NormalLog", "RequestBin_USB", "Terminal Process Response(A910) :" + BinRequestStr);

                boolean encryptResponeFoundFromTerminal = false;
                boolean RawResponseFoundFromTerminal = false;

                try {

                    JSONObject JsonResponseObject = new JSONObject(BinRequestStr);
                    String CheckEncryptDecrypt = JsonResponseObject.getString("dataType");
                    RawResponseFoundFromTerminal = true;

                } catch (JSONException ex) {
                    encryptResponeFoundFromTerminal = true;
                }

                if (EnableEncrypt) {

                    if (encryptResponeFoundFromTerminal) {

                    } else if (RawResponseFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestBin_USB", "Client requested Encrypt Response, Terminal Response Raw Response --> Terminal Response :" + BinRequestStr);
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "21");
                        PaxPort.release();
                        return ResponseEncryptDecryptError;
                    }

                } else {
                    if (encryptResponeFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestBin_USB", "Client requested Raw Response, Terminal Response Encrypted Response");
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "22");
                        PaxPort.release();
                        return ResponseEncryptDecryptError;

                    } else if (RawResponseFoundFromTerminal) {

                        JSONObject JsonResponseObject = new JSONObject(BinRequestStr);
                        String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                        if (!DataTypeFieldResponse.equals("binRequest")) {

                            LogWrite("NormalLog", "RequestBin_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                            JSONObject ResponseClientDataTypeError = new JSONObject();
                            ResponseClientDataTypeError.put("dataType", "NAK");
                            ResponseClientDataTypeError.put("error", "12");
                            PaxPort.release();
                            return ResponseClientDataTypeError;
                        }

                        String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                        JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                        String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                        JSONObject JsonResponseObject_Requestbin = new JSONObject(BinRequestStr);
                        JsonResponseObject_Requestbin.put("deviceType", "A910");
                        JsonResponseObject_Requestbin.remove("checksum");

                        LogWrite("NormalLog", "RequestBin_USB", "Sent Terminal Response(raw) to Client successfully");
                        PaxPort.release();
                        return JsonResponseObject_Requestbin;

                    }
                }

                String testData = byte2HexStr(BinRequestReadBuffer);

                if (testData.equals("")) {
                    LogWrite("NormalLog", "RequestBin_USB", "byte2HexStr Calculate Error --> Function parameter :" + testData);
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "24");
                    PaxPort.release();
                    return KeyEncryptError;
                }

                String str = new String(testData.getBytes(), 0, numRead * 2, StandardCharsets.UTF_8);

                String DecryptedKeyString = GetKeyFromLog();

                if (DecryptedKeyString.equals("")) {

                    LogWrite("NormalLog", "RequestBin_USB", "Decryption error log file key");
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "23");
                    PaxPort.release();
                    return KeyEncryptError;
                }

                DecryptedKeyString = DecryptedKeyString + DecryptedKeyString.substring(0, 16);

                byte[] resByteArray = null;

                try {
                    resByteArray = decrypt3Des(str, DecryptedKeyString);
                } catch (Exception ex) {

                    LogWrite("NormalLog", "RequestBin_USB", "Terminal Response Decryption Error --> Exception Error :" + ex.getMessage());
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    PaxPort.release();
                    return ResponseClientDataTypeError;
                }

                if (resByteArray == null) {

                    LogWrite("NormalLog", "RequestBin_USB", "Terminal Response Decryption null Error ");
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    PaxPort.release();
                    return ResponseClientDataTypeError;
                }

                String BinRequestEncryptedRawData = new String(resByteArray);

                String WithoutEncryptData = BinRequestEncryptedRawData.substring(64, BinRequestEncryptedRawData.length());

                JSONObject JsonResponseObject = new JSONObject(WithoutEncryptData);
                String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                if (!DataTypeFieldResponse.equals("binRequest")) {

                    LogWrite("NormalLog", "RequestBin_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "12");
                    PaxPort.release();
                    return ResponseClientDataTypeError;
                }

                String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                JSONObject JsonResponseObject_Requestbin = new JSONObject(WithoutEncryptData);
                JsonResponseObject_Requestbin.put("deviceType", "A910");
                JsonResponseObject_Requestbin.remove("checksum");

                LogWrite("NormalLog", "RequestBin_USB", "Sent Terminal Response(Encrypt) to Client successfully");
                PaxPort.release();
                return JsonResponseObject_Requestbin;
                                   
            }          
        } else {

            LogWrite("NormalLog", "RequestBin_USB", "Terminal Final Response Received data not valid --> Recieved data :" + BinRequestStr);
            JSONObject ReceiveError = new JSONObject();
            ReceiveError.put("dataType", "NAK");
            ReceiveError.put("error", "13");
            PaxPort.release();
            return ReceiveError;
        }

    }

}
