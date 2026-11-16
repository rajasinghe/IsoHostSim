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
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author HP PC
 */
public class VoidRequest extends Utility {

    String strIP;
    int Port;
    JSONObject DoVoidRequestJsonObjectCommand;
    Boolean EnableEncrypt;
    int intTo;

    VoidRequest(String strIP, int Port, JSONObject DoVoidRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        this.strIP = strIP;
        this.Port = Port;
        this.DoVoidRequestJsonObjectCommand = DoVoidRequestJsonObjectCommand;
        this.EnableEncrypt = EnableEncrypt;
        this.intTo = intTo;
    }

    VoidRequest(JSONObject DoVoidRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        this.DoVoidRequestJsonObjectCommand = DoVoidRequestJsonObjectCommand;
        this.EnableEncrypt = EnableEncrypt;
        this.intTo = intTo;
    }

    //TCP communication DoVoid
    public JSONObject DoVoid_TCP() {

        LogWrite("LogStart", "DoVoid_TCP", "Function Starting Ip: " + strIP + " Port:" + Port);
        
        Socket socket = null;

        try {

            String DataTypeFieldRequest = DoVoidRequestJsonObjectCommand.getString("dataType");

            if (!DataTypeFieldRequest.equals("void")) {

                LogWrite("NormalLog", "DoVoid_TCP", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
                JSONObject RequestClientDataTypeError = new JSONObject();
                RequestClientDataTypeError.put("dataType", "NAK");
                RequestClientDataTypeError.put("error", "10");
                return RequestClientDataTypeError;
            }

            JSONObject DataFieldRequest = DoVoidRequestJsonObjectCommand.getJSONObject("data");

            String DataFieldEncoded = sha256(DataFieldRequest.toString());

            if (DataFieldEncoded.length() > 10) {
                DoVoidRequestJsonObjectCommand.put("checksum", DataFieldEncoded);
            } else {

                LogWrite("NormalLog", "DoVoid_TCP", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
                JSONObject CheckSumCalculateError = new JSONObject();
                CheckSumCalculateError.put("dataType", "NAK");
                CheckSumCalculateError.put("error", "11");
                return CheckSumCalculateError;
            }

            String tmp = DoVoidRequestJsonObjectCommand.toString();
            
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(strIP, Port);
            socket.connect(socketAddress, 5000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            
            writer.println(tmp);
            writer.flush();

            BufferedReader DoVoidRequestAck = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] Ackbuffer = new char[2048];
            String DoVoidAckStr = "";

            socket.setSoTimeout(3000);

            DoVoidRequestAck.read(Ackbuffer);

            DoVoidAckStr = new String(Ackbuffer);

            if (DoVoidAckStr.length() > 10) {
                
                LogWrite("NormalLog", "DoVoid_TCP", "Terminal ACK Response :" + DoVoidAckStr);
                
                JSONObject obj = new JSONObject(DoVoidAckStr);
                String data = obj.get("dataType").toString();

                if (data.equals("ACK")) {
                    LogWrite("NormalLog", "DoVoid_TCP", "Sent Command to terminal successfully");
                } else {

                    LogWrite("NormalLog", "DoVoid_TCP", "Terminal Side error code json found :" + DoVoidAckStr);
                    JSONObject JsonResponseObject = new JSONObject(DoVoidAckStr);
                    socket.close();
                    return JsonResponseObject;
                }
            } else {

                LogWrite("NormalLog", "DoVoid_TCP", "Terminal Response not valid :" + DoVoidAckStr);
                JSONObject AckReceiveError = new JSONObject();
                AckReceiveError.put("dataType", "NAK");
                AckReceiveError.put("error", "13");
                socket.close();
                return AckReceiveError;
            }

            DataInputStream DoVoidRequest = new DataInputStream(socket.getInputStream());

            socket.setSoTimeout(intTo * 1000);
            String DoVoidRequestStr = "";
            int c;
            do {
                c = DoVoidRequest.read();
                DoVoidRequestStr += (char) c;
            } while (DoVoidRequest.available() > 0);
            
            socket.setSoTimeout(1000);

            try {

                int d;
                do {
                    d = DoVoidRequest.read();
                    DoVoidRequestStr += (char) d;

                } while (DoVoidRequest.available() > 0);

            } catch (Exception ex) {
                
            }

            System.out.println("Data :" + DoVoidRequestStr);

            if (DoVoidRequestStr.length() > 10) {
                
                LogWrite("NormalLog", "DoVoid_TCP", "Terminal Process Response :" + DoVoidRequestStr);

                boolean encryptResponeFoundFromTerminal = false;
                boolean RawResponseFoundFromTerminal = false;

                try {

                    JSONObject JsonResponseObject = new JSONObject(DoVoidRequestStr);
                    String CheckEncryptDecrypt = JsonResponseObject.getString("dataType");
                    RawResponseFoundFromTerminal = true;

                } catch (JSONException ex) {
                    encryptResponeFoundFromTerminal = true;
                }

                if (EnableEncrypt) {

                    if (encryptResponeFoundFromTerminal) {

                    } else if (RawResponseFoundFromTerminal) {

                        LogWrite("NormalLog", "DoVoid_TCP", "Client requested Encrypt Response, Terminal Response Raw Response --> Terminal Response :" + DoVoidRequestStr);
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "21");
                        socket.close();
                        return ResponseEncryptDecryptError;
                    }

                } else {
                    if (encryptResponeFoundFromTerminal) {

                        LogWrite("NormalLog", "DoVoid_TCP", "Client requested Raw Response, Terminal Response Encrypted Response");
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "22");
                        socket.close();
                        return ResponseEncryptDecryptError;

                    } else if (RawResponseFoundFromTerminal) {

                        //System.out.println(BinRequestStr);
                        JSONObject JsonResponseObject = new JSONObject(DoVoidRequestStr);
                        String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                        if (!DataTypeFieldResponse.equals("void")) {

                            LogWrite("NormalLog", "DoVoid_TCP", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                            JSONObject ResponseClientDataTypeError = new JSONObject();
                            ResponseClientDataTypeError.put("dataType", "NAK");
                            ResponseClientDataTypeError.put("error", "12");
                            socket.close();
                            return ResponseClientDataTypeError;
                        }

                        String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                        JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                        String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                        JSONObject JsonResponseObject_binSale = new JSONObject(DoVoidRequestStr);
                        JsonResponseObject_binSale.remove("checksum");

                        LogWrite("NormalLog", "DoVoid_TCP", "Sent Terminal Response(raw) to Client successfully");
                        socket.close();
                        return JsonResponseObject_binSale;

                    }
                }

                String DecryptedKeyString = GetKeyFromLog();

                if (DecryptedKeyString.equals("")) {

                    LogWrite("NormalLog", "DoVoid_TCP", "Decryption error log file key");
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "23");
                    socket.close();
                    return KeyEncryptError;
                }

                DecryptedKeyString = DecryptedKeyString + DecryptedKeyString.substring(0, 16);

                byte[] resByteArray = null;

                try {
                    resByteArray = decrypt3Des(DoVoidRequestStr, DecryptedKeyString);
                } catch (Exception ex) {

                    LogWrite("NormalLog", "DoVoid_TCP", "Terminal Response Decryption Error --> Exception Error :" + ex.getMessage());
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    socket.close();
                    return ResponseClientDataTypeError;
                }

                if (resByteArray == null) {

                    LogWrite("NormalLog", "DoVoid_TCP", "Terminal Response Decryption null Error ");
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    socket.close();
                    return ResponseClientDataTypeError;
                }

                String BinRequestEncryptedRawData = new String(resByteArray);

                String WithoutEncryptData = BinRequestEncryptedRawData.substring(64, BinRequestEncryptedRawData.length());

                //System.out.println(BinRequestStr);
                JSONObject JsonResponseObject = new JSONObject(WithoutEncryptData);
                String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                if (!DataTypeFieldResponse.equals("void")) {

                    LogWrite("NormalLog", "DoVoid_TCP", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "12");
                    socket.close();
                    return ResponseClientDataTypeError;
                }

                String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                JSONObject JsonResponseObject_binSale = new JSONObject(WithoutEncryptData);
                JsonResponseObject_binSale.remove("checksum");

                LogWrite("NormalLog", "DoVoid_TCP", "Sent Terminal Response(Encrypted) to Client successfully");
                socket.close();
                return JsonResponseObject_binSale;

            } else {

                LogWrite("NormalLog", "DoVoid_TCP", "Terminal Final Response Received data not valid --> Recieved data :" + DoVoidRequestStr);
                JSONObject ReceiveError = new JSONObject();
                ReceiveError.put("dataType", "NAK");
                ReceiveError.put("error", "13");
                socket.close();
                return ReceiveError;
            }

        } catch (UnknownHostException ex) {

            LogWrite("NormalLog", "DoVoid_TCP", "TCP UnknownHostException Error --> Recieved data :" + ex.getMessage());
            JSONObject ExceptionError = new JSONObject();
            ExceptionError.put("dataType", "NAK");
            ExceptionError.put("error", "14");
            try {
                socket.close();
            } catch (IOException ex1) {
                
            }
            return ExceptionError;

        } catch (IOException ex) {

            LogWrite("NormalLog", "DoVoid_TCP", "TCP IOException Error --> Recieved data :" + ex.getMessage());
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

    //usb serial communication DoVoid
    public JSONObject DoVoid_USB() {

        LogWrite("LogStart", "DoVoid_USB", "Function Starting");
        
        //fIND PAX COM PORT
        String ComPort = "";

        ComPort = PaxPort.discover();   //gimhan added - shared port owner, port stays open so a cancel can use it

        if (PortMatch.isUsablePortName(ComPort)) {   // ISO8583Studio patch: was ComPort.contains("COM")

        } else {
            
            LogWrite("NormalLog", "DoVoid_USB", "Pax Com Port Not Found");
            JSONObject PortOpenError = new JSONObject();
            PortOpenError.put("dataType", "NAK");
            PortOpenError.put("error", "15");
            return PortOpenError;
        }

        OutputStream outputStream;

        SerialPort port = PaxPort.acquire(ComPort);   //gimhan added - shared port owner, port stays open so a cancel can use it

        if (port == null) {

                LogWrite("NormalLog", "DoVoid_USB", "Com Port Open Error :" + ComPort);
                JSONObject PortOpenError = new JSONObject();
                PortOpenError.put("dataType", "NAK");
                PortOpenError.put("error", "16");
                return PortOpenError;

        }

        String DataTypeFieldRequest = DoVoidRequestJsonObjectCommand.getString("dataType");

        if (!DataTypeFieldRequest.equals("void")) {

            LogWrite("NormalLog", "DoVoid_USB", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
            JSONObject RequestClientDataTypeError = new JSONObject();
            RequestClientDataTypeError.put("dataType", "NAK");
            RequestClientDataTypeError.put("error", "10");
            PaxPort.release();
            return RequestClientDataTypeError;
        }

        JSONObject DataFieldRequest = DoVoidRequestJsonObjectCommand.getJSONObject("data");

        String DataFieldEncoded = sha256(DataFieldRequest.toString());

        if (DataFieldEncoded.length() > 10) {
            DoVoidRequestJsonObjectCommand.put("checksum", DataFieldEncoded);
        } else {
            
            LogWrite("NormalLog", "DoVoid_USB", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
            JSONObject CheckSumCalculateError = new JSONObject();
            CheckSumCalculateError.put("dataType", "NAK");
            CheckSumCalculateError.put("error", "11");
            PaxPort.release();
            return CheckSumCalculateError;
        }

        String DoVoidCmdStr = DoVoidRequestJsonObjectCommand.toString();

        outputStream = port.getOutputStream();
        port.flushIOBuffers();

        try {
            outputStream.write(DoVoidCmdStr.getBytes());
        } catch (IOException ex) {

            LogWrite("NormalLog", "DoVoid_USB", "Com Port Write Exception :" + ex.getMessage());
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

            LogWrite("NormalLog", "DoVoid_USB", "ACK Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "18");
            PaxPort.release();
            return TimeOutError;
        }

        String DoVoidAckStr = "";
        try {
            DoVoidAckStr = new String(AckReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {

            LogWrite("NormalLog", "DoVoid_USB", "Byte to String convert error :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");
            PaxPort.release();
            return TimeOutError;
        }

        //System.out.println("Received -> "+ DoVoidAckStr);
        if (DoVoidAckStr.length() > 10) {
            
            LogWrite("NormalLog", "DoVoid_USB", "Terminal ACK Response :" + DoVoidAckStr);
            
            JSONObject obj = new JSONObject(DoVoidAckStr);
            String data = obj.get("dataType").toString();

            if (data.equals("ACK")) {
                LogWrite("NormalLog", "DoVoid_USB", "Sent Command to terminal successfully");
            } else {
                LogWrite("NormalLog", "DoVoid_USB", "Terminal Side error code json found :" + DoVoidAckStr);
                JSONObject JsonResponseObject = new JSONObject(DoVoidAckStr);
                PaxPort.release();
                return JsonResponseObject;
            }
        } else {

            LogWrite("NormalLog", "DoVoid_USB", "Terminal Response not valid :" + DoVoidAckStr);
            JSONObject AckReceiveError = new JSONObject();
            AckReceiveError.put("dataType", "NAK");
            AckReceiveError.put("error", "13");

            PaxPort.release();
            return AckReceiveError;
        }

        port.flushIOBuffers();

        byte[] DoVoidReadBuffer = new byte[2048];
        boolean check1 = false;
        int y = 0;
        int numRead = 0;

        while (check1 == false && y < intTo) {

            numRead = port.readBytes(DoVoidReadBuffer,
                    DoVoidReadBuffer.length);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            if (numRead > 10) {
                //System.out.print("Read " + numRead + " bytes -");
                check1 = true;
            }
            y++;
        }

        if (y == intTo) {

            LogWrite("NormalLog", "DoVoid_USB", "Terminal Response Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "20");

            PaxPort.release();
            return TimeOutError;
        }

        String DoVoidStr = "";
        try {
            DoVoidStr = new String(DoVoidReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {

            LogWrite("NormalLog", "DoVoid_USB", "Terminal Response bytes to string convert Exception :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");

            PaxPort.release();
            return TimeOutError;
        }

        //System.out.println("Received -> "+ DoVoidStr);
        if (DoVoidStr.length() > 10) {
            
            LogWrite("NormalLog", "DoVoid_USB", "Terminal Process Response :" + DoVoidStr);

            boolean encryptResponeFoundFromTerminal = false;
            boolean RawResponseFoundFromTerminal = false;

            try {

                JSONObject JsonResponseObject = new JSONObject(DoVoidStr);
                String CheckEncryptDecrypt = JsonResponseObject.getString("dataType");
                RawResponseFoundFromTerminal = true;

            } catch (JSONException ex) {
                encryptResponeFoundFromTerminal = true;
            }

            if (EnableEncrypt) {

                if (encryptResponeFoundFromTerminal) {

                } else if (RawResponseFoundFromTerminal) {

                    LogWrite("NormalLog", "DoVoid_USB", "Client requested Encrypt Response, Terminal Response Raw Response --> Terminal Response :" + DoVoidStr);
                    JSONObject ResponseEncryptDecryptError = new JSONObject();
                    ResponseEncryptDecryptError.put("dataType", "NAK");
                    ResponseEncryptDecryptError.put("error", "21");
                    PaxPort.release();
                    return ResponseEncryptDecryptError;
                }

            } else {
                if (encryptResponeFoundFromTerminal) {

                    LogWrite("NormalLog", "DoVoid_USB", "Client requested Raw Response, Terminal Response Encrypted Response");
                    JSONObject ResponseEncryptDecryptError = new JSONObject();
                    ResponseEncryptDecryptError.put("dataType", "NAK");
                    ResponseEncryptDecryptError.put("error", "22");
                    PaxPort.release();
                    return ResponseEncryptDecryptError;

                } else if (RawResponseFoundFromTerminal) {

                    JSONObject JsonResponseObject = new JSONObject(DoVoidStr);
                    String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                    if (!DataTypeFieldResponse.equals("void")) {
                        
                        LogWrite("NormalLog", "DoVoid_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                        JSONObject ResponseClientDataTypeError = new JSONObject();
                        ResponseClientDataTypeError.put("dataType", "NAK");
                        ResponseClientDataTypeError.put("error", "12");
                        PaxPort.release();
                        return ResponseClientDataTypeError;
                    }

                    String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                    JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                    String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                    JSONObject JsonResponseObject_DoVoid = new JSONObject(DoVoidStr);
                    JsonResponseObject_DoVoid.put("deviceType", "A910");
                    JsonResponseObject_DoVoid.remove("checksum");

                    LogWrite("NormalLog", "DoVoid_USB", "Sent Terminal Response(raw) to Client successfully");
                    PaxPort.release();
                    return JsonResponseObject_DoVoid;

                }
            }

            String testData = byte2HexStr(DoVoidReadBuffer);
            
            if (testData.equals("")) {
                LogWrite("NormalLog", "DoVoid_USB", "byte2HexStr Calculate Error --> Function parameter :" + testData);
                JSONObject KeyEncryptError = new JSONObject();
                KeyEncryptError.put("dataType", "NAK");
                KeyEncryptError.put("error", "24");
                PaxPort.release();
                return KeyEncryptError;
            }
            
            String str = new String(testData.getBytes(), 0, numRead * 2, StandardCharsets.UTF_8);

            String DecryptedKeyString = GetKeyFromLog();
            
            if (DecryptedKeyString.equals("")) {

                LogWrite("NormalLog", "DoVoid_USB", "Decryption error log file key");
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

                LogWrite("NormalLog", "DoVoid_USB", "Terminal Response Decryption Error --> Exception Error :" + ex.getMessage());
                Logger.getLogger(TCPJAVA_CBA_ClassLibrary.class.getName()).log(Level.SEVERE, null, ex);
                JSONObject ResponseClientDataTypeError = new JSONObject();
                ResponseClientDataTypeError.put("dataType", "NAK");
                ResponseClientDataTypeError.put("error", "24");
                PaxPort.release();
                return ResponseClientDataTypeError;
            }
            
            if (resByteArray == null) {

                LogWrite("NormalLog", "DoVoid_USB", "Terminal Response Decryption null Error ");
                JSONObject ResponseClientDataTypeError = new JSONObject();
                ResponseClientDataTypeError.put("dataType", "NAK");
                ResponseClientDataTypeError.put("error", "24");
                PaxPort.release();
                return ResponseClientDataTypeError;
            }

            String DoVoidEncryptedRawData = new String(resByteArray);

            String WithoutEncryptData = DoVoidEncryptedRawData.substring(64, DoVoidEncryptedRawData.length());

            JSONObject JsonResponseObject = new JSONObject(WithoutEncryptData);
            String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

            if (!DataTypeFieldResponse.equals("void")) {
                
                LogWrite("NormalLog", "DoVoid_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                JSONObject ResponseClientDataTypeError = new JSONObject();
                ResponseClientDataTypeError.put("dataType", "NAK");
                ResponseClientDataTypeError.put("error", "12");
                PaxPort.release();
                return ResponseClientDataTypeError;
            }

            String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
            JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

            String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

            JSONObject JsonResponseObject_DoVoid = new JSONObject(WithoutEncryptData);
            JsonResponseObject_DoVoid.put("deviceType", "A910");
            JsonResponseObject_DoVoid.remove("checksum");

            LogWrite("NormalLog", "DoVoid_USB", "Sent Terminal Response(Encrypt) to Client successfully");
            PaxPort.release();
            return JsonResponseObject_DoVoid;

        } else {

            LogWrite("NormalLog", "DoVoid_USB", "Terminal Final Response Received data not valid --> Recieved data :" + DoVoidStr);
            JSONObject ReceiveError = new JSONObject();
            ReceiveError.put("dataType", "NAK");
            ReceiveError.put("error", "13");
            PaxPort.release();
            return ReceiveError;
        }

    }
}
