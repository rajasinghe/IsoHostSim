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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 */
public class LastTranDataRequest extends Utility {

    String strIP;
    int Port;
    JSONObject DoRequestLastJsonObjectCommand;
    Boolean EnableEncrypt;
    int intTo;

    LastTranDataRequest(String strIP, int Port, JSONObject DoRequestLastJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        this.strIP = strIP;
        this.Port = Port;
        this.DoRequestLastJsonObjectCommand = DoRequestLastJsonObjectCommand;
        this.EnableEncrypt = EnableEncrypt;
        this.intTo = intTo;
    }

    LastTranDataRequest(JSONObject DoRequestLastJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        this.DoRequestLastJsonObjectCommand = DoRequestLastJsonObjectCommand;
        this.EnableEncrypt = EnableEncrypt;
        this.intTo = intTo;
    }

    //TCP communication RequestLast
    public JSONObject RequestLast_TCP() {

        LogWrite("LogStart", "RequestLast_TCP", "Function Starting Ip: " + strIP + " Port:" + Port);
        
        Socket socket = null;
        
        try {

            String DataTypeFieldRequest = DoRequestLastJsonObjectCommand.getString("dataType");

            if (!DataTypeFieldRequest.equals("RequestLast")) {

                LogWrite("NormalLog", "RequestLast_TCP", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
                JSONObject RequestClientDataTypeError = new JSONObject();
                RequestClientDataTypeError.put("dataType", "NAK");
                RequestClientDataTypeError.put("error", "10");
                return RequestClientDataTypeError;
            }

            JSONObject DataFieldRequest = DoRequestLastJsonObjectCommand.getJSONObject("data");

            String DataFieldEncoded = sha256(DataFieldRequest.toString());

            if (DataFieldEncoded.length() > 10) {
                DoRequestLastJsonObjectCommand.put("checksum", DataFieldEncoded);
            } else {

                LogWrite("NormalLog", "RequestLast_TCP", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
                JSONObject CheckSumCalculateError = new JSONObject();
                CheckSumCalculateError.put("dataType", "NAK");
                CheckSumCalculateError.put("error", "11");
                return CheckSumCalculateError;
            }

            String tmp = DoRequestLastJsonObjectCommand.toString();
                       
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(strIP, Port);
            socket.connect(socketAddress, 5000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            
            writer.println(tmp);
            writer.flush();

            BufferedReader RequestLastReuestAck = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] Ackbuffer = new char[2048];
            String RequestLastAckStr = "";

            socket.setSoTimeout(3000);

            RequestLastReuestAck.read(Ackbuffer);

            RequestLastAckStr = new String(Ackbuffer);

            if (RequestLastAckStr.length() > 10) {
                
                LogWrite("NormalLog", "RequestLast_TCP", "Terminal ACK Response :" + RequestLastAckStr);
                           
                JSONObject obj = new JSONObject(RequestLastAckStr);
                String data = obj.get("dataType").toString();

                if (data.equals("ACK")) {
                    LogWrite("NormalLog", "RequestLast_TCP", "Sent Command to terminal successfully");
                } else {
                    LogWrite("NormalLog", "RequestLast_TCP", "Terminal Side error code json found :" + RequestLastAckStr);
                    JSONObject JsonResponseObject = new JSONObject(RequestLastAckStr);
                    socket.close();
                    return JsonResponseObject;
                }
            } else {

                LogWrite("NormalLog", "RequestLast_TCP", "Terminal Response not valid :" + RequestLastAckStr);
                JSONObject AckReceiveError = new JSONObject();
                AckReceiveError.put("dataType", "NAK");
                AckReceiveError.put("error", "13");
                socket.close();
                return AckReceiveError;
            }

            DataInputStream bufferedReader = new DataInputStream(socket.getInputStream());

            socket.setSoTimeout(intTo * 1000);
            String RequestLastRequestStr = "";

            int c;
            do {
                c = bufferedReader.read();
                RequestLastRequestStr += (char) c;

            } while (bufferedReader.available() > 0);

            socket.setSoTimeout(1000);

            try {

                int d;
                do {
                    d = bufferedReader.read();
                    RequestLastRequestStr += (char) d;

                } while (bufferedReader.available() > 0);

            } catch (Exception ex) {

            }

            //System.out.println("Data :" + RequestLastRequestStr);

            if (RequestLastRequestStr.length() > 10) {

                LogWrite("NormalLog", "RequestLast_TCP", "Terminal Process Response :" + RequestLastRequestStr);
                
                boolean encryptResponeFoundFromTerminal = false;
                boolean RawResponseFoundFromTerminal = false;

                try {

                    JSONObject JsonResponseObject = new JSONObject(RequestLastRequestStr);
                    String CheckEncryptDecrypt = JsonResponseObject.getString("dataType");
                    RawResponseFoundFromTerminal = true;

                } catch (JSONException ex) {
                    encryptResponeFoundFromTerminal = true;
                }

                if (EnableEncrypt) {

                    if (encryptResponeFoundFromTerminal) {

                    } else if (RawResponseFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestLast_TCP", "Client requested Encrypt Response, Terminal Response Raw Response --> Terminal Response :" + RequestLastRequestStr);
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "21");
                        socket.close();
                        return ResponseEncryptDecryptError;
                    }

                } else {
                    if (encryptResponeFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestLast_TCP", "Client requested Raw Response, Terminal Response Encrypted Response");
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "22");
                        socket.close();
                        return ResponseEncryptDecryptError;

                    } else if (RawResponseFoundFromTerminal) {

                        JSONObject JsonResponseObject = new JSONObject(RequestLastRequestStr);
                        String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                        if (!DataTypeFieldResponse.equals("RequestLast")) {

                            LogWrite("NormalLog", "RequestLast_TCP", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                            JSONObject ResponseClientDataTypeError = new JSONObject();
                            ResponseClientDataTypeError.put("dataType", "NAK");
                            ResponseClientDataTypeError.put("error", "12");
                            socket.close();
                            return ResponseClientDataTypeError;
                        }

                        String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                        JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                        String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                        JSONObject JsonResponseObject_binSale = new JSONObject(RequestLastRequestStr);
                        JsonResponseObject_binSale.remove("checksum");

                        LogWrite("NormalLog", "RequestLast_TCP", "Sent Terminal Response(raw) to Client successfully");
                        socket.close();
                        return JsonResponseObject_binSale;

                    }
                }

                String DecryptedKeyString = GetKeyFromLog();

                if (DecryptedKeyString.equals("")) {

                    LogWrite("NormalLog", "RequestLast_TCP", "Decryption error log file key");
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "23");
                    socket.close();
                    return KeyEncryptError;
                }

                DecryptedKeyString = DecryptedKeyString + DecryptedKeyString.substring(0, 16);

                byte[] resByteArray = null;

                try {
                    resByteArray = decrypt3Des(RequestLastRequestStr, DecryptedKeyString);
                } catch (Exception ex) {

                    LogWrite("NormalLog", "RequestLast_TCP", "Terminal Response Decryption Error --> Exception Error :" + ex.getMessage());
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    socket.close();
                    return ResponseClientDataTypeError;
                }

                if (resByteArray == null) {

                    LogWrite("NormalLog", "RequestLast_TCP", "Terminal Response Decryption null Error ");
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

                if (!DataTypeFieldResponse.equals("RequestLast")) {

                    LogWrite("NormalLog", "RequestLast_TCP", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
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

                LogWrite("NormalLog", "RequestLast_TCP", "Sent Terminal Response(Encrypted) to Client successfully");
                socket.close();
                return JsonResponseObject_binSale;

            } else {

                LogWrite("NormalLog", "RequestLast_TCP", "Terminal Final Response Received data not valid --> Recieved data :" + RequestLastRequestStr);
                JSONObject ReceiveError = new JSONObject();
                ReceiveError.put("dataType", "NAK");
                ReceiveError.put("error", "13");
                socket.close();
                return ReceiveError;
            }

        } catch (UnknownHostException ex) {

            LogWrite("NormalLog", "RequestLast_TCP", "TCP UnknownHostException Error --> Recieved data :" + ex.getMessage());
            JSONObject ExceptionError = new JSONObject();
            ExceptionError.put("dataType", "NAK");
            ExceptionError.put("error", "14");
            try {
                socket.close();
            } catch (IOException ex1) {
                
            }
            return ExceptionError;

        } catch (IOException ex) {

            LogWrite("NormalLog", "RequestLast_TCP", "TCP IOException Error --> Recieved data :" + ex.getMessage());
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

    //usb serial communication RequestLast
    public JSONObject RequestLast_USB() {

        LogWrite("LogStart", "RequestLast_USB", "Function Starting");

        //fIND PAX COM PORT
        String ComPort = "";

        ComPort = PaxPort.discover();   // shared port owner, port stays open so a cancel can use it

        if (PortMatch.isUsablePortName(ComPort)) {   // POS Testing Platform patch: was ComPort.contains("COM")

        } else {

            LogWrite("NormalLog", "RequestLast_USB", "Pax Com Port Not Found");
            JSONObject PortOpenError = new JSONObject();
            PortOpenError.put("dataType", "NAK");
            PortOpenError.put("error", "15");
            return PortOpenError;
        }

        OutputStream outputStream;

        SerialPort port = PaxPort.acquire(ComPort);   // shared port owner, port stays open so a cancel can use it

        if (port == null) {

                LogWrite("NormalLog", "RequestLast_USB", "Com Port Open Error :" + ComPort);
                JSONObject PortOpenError = new JSONObject();
                PortOpenError.put("dataType", "NAK");
                PortOpenError.put("error", "16");
                return PortOpenError;

        }

        String DataTypeFieldRequest = DoRequestLastJsonObjectCommand.getString("dataType");

        if (!DataTypeFieldRequest.equals("RequestLast")) {

            LogWrite("NormalLog", "RequestLast_USB", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
            JSONObject RequestClientDataTypeError = new JSONObject();
            RequestClientDataTypeError.put("dataType", "NAK");
            RequestClientDataTypeError.put("error", "10");
            PaxPort.release();
            return RequestClientDataTypeError;
        }

        JSONObject DataFieldRequest = DoRequestLastJsonObjectCommand.getJSONObject("data");

        String DataFieldEncoded = sha256(DataFieldRequest.toString());

        if (DataFieldEncoded.length() > 10) {
            DoRequestLastJsonObjectCommand.put("checksum", DataFieldEncoded);
        } else {

            LogWrite("NormalLog", "RequestLast_USB", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
            JSONObject CheckSumCalculateError = new JSONObject();
            CheckSumCalculateError.put("dataType", "NAK");
            CheckSumCalculateError.put("error", "11");
            PaxPort.release();
            return CheckSumCalculateError;
        }

        String RequestLastCmdStr = DoRequestLastJsonObjectCommand.toString();

        outputStream = port.getOutputStream();
        port.flushIOBuffers();

        try {
            outputStream.write(RequestLastCmdStr.getBytes());
        } catch (IOException ex) {

            LogWrite("NormalLog", "RequestLast_USB", "Com Port Write Exception :" + ex.getMessage());
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

            LogWrite("NormalLog", "RequestLast_USB", "ACK Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "18");
            PaxPort.release();
            return TimeOutError;
        }

        String RequestLastAckStr;
        try {
            RequestLastAckStr = new String(AckReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {

            LogWrite("NormalLog", "RequestLast_USB", "Byte to String convert error :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");
            PaxPort.release();
            return TimeOutError;
        }

        //System.out.println("Received -> "+ RequestLastAckStr);
        if (RequestLastAckStr.length() > 10) {
            
            LogWrite("NormalLog", "RequestLast_USB", "Terminal ACK Response :" + RequestLastAckStr);
            
            JSONObject obj = new JSONObject(RequestLastAckStr);
            String data = obj.get("dataType").toString();

            if (data.equals("ACK")) {
                LogWrite("NormalLog", "RequestLast_USB", "Sent Command to terminal successfully");
            } else {
                LogWrite("NormalLog", "RequestLast_USB", "Terminal Side error code json found :" + RequestLastAckStr);
                JSONObject JsonResponseObject = new JSONObject(RequestLastAckStr);
                PaxPort.release();
                return JsonResponseObject;
            }
        } else {

            LogWrite("NormalLog", "RequestLast_USB", "Terminal Response not valid :" + RequestLastAckStr);
            JSONObject AckReceiveError = new JSONObject();
            AckReceiveError.put("dataType", "NAK");
            AckReceiveError.put("error", "13");
            PaxPort.release();
            return AckReceiveError;
        }

        port.flushIOBuffers();

        byte[] RequestLastReadBuffer = new byte[2048];
        boolean check1 = false;
        int y = 0;
        int numRead = 0;

        while (check1 == false && y < intTo) {

            numRead = port.readBytes(RequestLastReadBuffer,
                    RequestLastReadBuffer.length);
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

            LogWrite("NormalLog", "RequestLast_USB", "Terminal Response Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "20");
            PaxPort.release();
            return TimeOutError;
        }

        String RequestLastStr;
        try {
            RequestLastStr = new String(RequestLastReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {

            LogWrite("NormalLog", "RequestLast_USB", "Terminal Response bytes to string convert Exception :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");
            PaxPort.release();
            return TimeOutError;
        }

        if (RequestLastStr.length() > 10) {
            
            LogWrite("NormalLog", "RequestLast_USB", "Terminal Process Response :" + RequestLastStr);

            boolean encryptResponeFoundFromTerminal = false;
            boolean RawResponseFoundFromTerminal = false;

            try {

                JSONObject JsonResponseObject = new JSONObject(RequestLastStr);
                String CheckEncryptDecrypt = JsonResponseObject.getString("dataType");
                RawResponseFoundFromTerminal = true;

            } catch (JSONException ex) {
                encryptResponeFoundFromTerminal = true;
            }

            if (EnableEncrypt) {

                if (encryptResponeFoundFromTerminal) {

                } else if (RawResponseFoundFromTerminal) {

                    LogWrite("NormalLog", "RequestLast_USB", "Client requested Encrypt Response, Terminal Response Raw Response --> Terminal Response :" + RequestLastStr);
                    JSONObject ResponseEncryptDecryptError = new JSONObject();
                    ResponseEncryptDecryptError.put("dataType", "NAK");
                    ResponseEncryptDecryptError.put("error", "21");
                    PaxPort.release();
                    return ResponseEncryptDecryptError;
                }

            } else {
                if (encryptResponeFoundFromTerminal) {

                    LogWrite("NormalLog", "RequestLast_USB", "Client requested Raw Response, Terminal Response Encrypted Response");
                    JSONObject ResponseEncryptDecryptError = new JSONObject();
                    ResponseEncryptDecryptError.put("dataType", "NAK");
                    ResponseEncryptDecryptError.put("error", "22");
                    PaxPort.release();
                    return ResponseEncryptDecryptError;

                } else if (RawResponseFoundFromTerminal) {

                    JSONObject JsonResponseObject = new JSONObject(RequestLastStr);
                    String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                    if (!DataTypeFieldResponse.equals("RequestLast")) {

                        LogWrite("NormalLog", "RequestLast_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                        JSONObject ResponseClientDataTypeError = new JSONObject();
                        ResponseClientDataTypeError.put("dataType", "NAK");
                        ResponseClientDataTypeError.put("error", "12");
                        PaxPort.release();
                        return ResponseClientDataTypeError;
                    }

                    String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                    JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                    String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                    JSONObject JsonResponseObject_RequestLast = new JSONObject(RequestLastStr);
                    JsonResponseObject_RequestLast.put("deviceType", "A910");
                    JsonResponseObject_RequestLast.remove("checksum");

                    LogWrite("NormalLog", "RequestLast_USB", "Sent Terminal Response(raw) to Client successfully");
                    PaxPort.release();
                    return JsonResponseObject_RequestLast;

                }
            }

            String testData = byte2HexStr(RequestLastReadBuffer);

            if (testData.equals("")) {
                LogWrite("NormalLog", "RequestLast_USB", "byte2HexStr Calculate Error --> Function parameter :" + testData);
                JSONObject KeyEncryptError = new JSONObject();
                KeyEncryptError.put("dataType", "NAK");
                KeyEncryptError.put("error", "24");
                PaxPort.release();
                return KeyEncryptError;
            }

            String str = new String(testData.getBytes(), 0, numRead * 2, StandardCharsets.UTF_8);

            String DecryptedKeyString = GetKeyFromLog();

            if (DecryptedKeyString.equals("")) {

                LogWrite("NormalLog", "RequestLast_USB", "Decryption error log file key");
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

                LogWrite("NormalLog", "RequestLast_USB", "Terminal Response Decryption Error --> Exception Error :" + ex.getMessage());
                Logger.getLogger(TCPJAVA_CBA_ClassLibrary.class.getName()).log(Level.SEVERE, null, ex);
                JSONObject ResponseClientDataTypeError = new JSONObject();
                ResponseClientDataTypeError.put("dataType", "NAK");
                ResponseClientDataTypeError.put("error", "24");
                PaxPort.release();
                return ResponseClientDataTypeError;
            }

            if (resByteArray == null) {

                LogWrite("NormalLog", "RequestLast_USB", "Terminal Response Decryption null Error ");
                JSONObject ResponseClientDataTypeError = new JSONObject();
                ResponseClientDataTypeError.put("dataType", "NAK");
                ResponseClientDataTypeError.put("error", "24");
                PaxPort.release();
                return ResponseClientDataTypeError;
            }

            String RequestLastEncryptedRawData = new String(resByteArray);

            String WithoutEncryptData = RequestLastEncryptedRawData.substring(64, RequestLastEncryptedRawData.length());

            JSONObject JsonResponseObject = new JSONObject(WithoutEncryptData);

            String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

            if (!DataTypeFieldResponse.equals("RequestLast")) {

                LogWrite("NormalLog", "RequestLast_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                JSONObject ResponseClientDataTypeError = new JSONObject();
                ResponseClientDataTypeError.put("dataType", "NAK");
                ResponseClientDataTypeError.put("error", "12");
                PaxPort.release();
                return ResponseClientDataTypeError;
            }

            String ChecksumFieldResponse = JsonResponseObject.getString("checksum");

            JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

            String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

            JSONObject JsonResponseObject_RequestLast = new JSONObject(WithoutEncryptData);
            JsonResponseObject_RequestLast.put("deviceType", "A910");
            JsonResponseObject_RequestLast.remove("checksum");

            LogWrite("NormalLog", "RequestLast_USB", "Sent Terminal Response(Encrypt) to Client successfully");
            PaxPort.release();
            return JsonResponseObject_RequestLast;

        } else {

            LogWrite("NormalLog", "RequestLast_USB", "Terminal Final Response Received data not valid --> Recieved data :" + RequestLastStr);
            JSONObject ReceiveError = new JSONObject();
            ReceiveError.put("dataType", "NAK");
            ReceiveError.put("error", "13");
            PaxPort.release();
            return ReceiveError;
        }

    }

}
