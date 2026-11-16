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
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author HP PC
 */
public class DoSaleRequest extends Utility {

    String strIP;
    int Port;
    JSONObject DoSaleRequestJsonObjectCommand;
    Boolean EnableEncrypt;
    int intTo;
    Boolean isA920 = false;
    
    DoSaleRequest(String strIP, int Port, JSONObject DoSaleRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        this.strIP = strIP;
        this.Port = Port;
        this.DoSaleRequestJsonObjectCommand = DoSaleRequestJsonObjectCommand;
        this.EnableEncrypt = EnableEncrypt;
        this.intTo = intTo;
    }

    DoSaleRequest(JSONObject DoSaleRequestJsonObjectCommand, Boolean EnableEncrypt, int intTo) {
        this.DoSaleRequestJsonObjectCommand = DoSaleRequestJsonObjectCommand;
        this.EnableEncrypt = EnableEncrypt;
        this.intTo = intTo;
    }

    //TCP communication RequestDoSale
    public JSONObject RequestDoSale_TCP() {

        LogWrite("LogStart", "RequestDoSale_TCP", "Function Starting Ip: " + strIP + " Port:" + Port);

        Socket socket = null;
        
        try {

            String DataTypeFieldRequest = DoSaleRequestJsonObjectCommand.getString("dataType");

            if (!DataTypeFieldRequest.equals("sale")) {

                LogWrite("NormalLog", "RequestDoSale_TCP", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
                JSONObject RequestClientDataTypeError = new JSONObject();
                RequestClientDataTypeError.put("dataType", "NAK");
                RequestClientDataTypeError.put("error", "10");
                return RequestClientDataTypeError;
            }

            JSONObject DataFieldRequest = DoSaleRequestJsonObjectCommand.getJSONObject("data");

            String DataFieldEncoded = sha256(DataFieldRequest.toString());

            if (DataFieldEncoded.length() > 10) {
                DoSaleRequestJsonObjectCommand.put("checksum", DataFieldEncoded);
            } else {

                LogWrite("NormalLog", "RequestDoSale_TCP", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
                JSONObject CheckSumCalculateError = new JSONObject();
                CheckSumCalculateError.put("dataType", "NAK");
                CheckSumCalculateError.put("error", "11");
                return CheckSumCalculateError;
            }

            String tmp = DoSaleRequestJsonObjectCommand.toString();
            
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(strIP, Port);
            socket.connect(socketAddress, 5000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            
            writer.println(tmp);
            writer.flush();

            BufferedReader BinSaleReuestAck = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] Ackbuffer = new char[2048];
            String binSaleRequestAckStr = "";

            socket.setSoTimeout(3000);

            BinSaleReuestAck.read(Ackbuffer);

            binSaleRequestAckStr = new String(Ackbuffer);

            if (binSaleRequestAckStr.length() > 10) {
                
                LogWrite("NormalLog", "RequestDoSale_TCP", "Terminal ACK Response :" + binSaleRequestAckStr);
                
                JSONObject obj = new JSONObject(binSaleRequestAckStr);
                String data = obj.get("dataType").toString();

                if (data.equals("ACK")) {
                    LogWrite("NormalLog", "RequestDoSale_TCP", "Sent Command to terminal successfully");
                } else {
                    LogWrite("NormalLog", "RequestDoSale_TCP", "Terminal Side error code json found :" + binSaleRequestAckStr);
                    JSONObject JsonResponseObject = new JSONObject(binSaleRequestAckStr);
                    socket.close();
                    return JsonResponseObject;
                }
            } else {

                LogWrite("NormalLog", "RequestDoSale_TCP", "Terminal Response not valid :" + binSaleRequestAckStr);
                JSONObject AckReceiveError = new JSONObject();
                AckReceiveError.put("dataType", "NAK");
                AckReceiveError.put("error", "13");
                socket.close();
                return AckReceiveError;
            }

            DataInputStream BinSaleRequest = new DataInputStream(socket.getInputStream());

            socket.setSoTimeout(intTo * 1000);
            String BinSaleRequestStr = "";
            int c;
            do {
                c = BinSaleRequest.read();
                BinSaleRequestStr += (char) c;
            } while (BinSaleRequest.available() > 0);
            
            socket.setSoTimeout(1000);

            try {

                int d;
                do {
                    d = BinSaleRequest.read();
                    BinSaleRequestStr += (char) d;

                } while (BinSaleRequest.available() > 0);

            } catch (Exception ex) {
                
            }

            //System.out.println("Data :" + BinSaleRequestStr);

            if (BinSaleRequestStr.length() > 10) {
                
                LogWrite("NormalLog", "RequestDoSale_TCP", "Terminal Process Response :" + BinSaleRequestStr);

                boolean encryptResponeFoundFromTerminal = false;
                boolean RawResponseFoundFromTerminal = false;

                try {

                    JSONObject JsonResponseObject = new JSONObject(BinSaleRequestStr);
                    String CheckEncryptDecrypt = JsonResponseObject.getString("dataType");
                    RawResponseFoundFromTerminal = true;

                } catch (JSONException ex) {
                    encryptResponeFoundFromTerminal = true;
                }

                if (EnableEncrypt) {

                    if (encryptResponeFoundFromTerminal) {

                    } else if (RawResponseFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestDoSale_TCP", "Client requested Encrypt Response, Terminal Response Raw Response --> Terminal Response :" + BinSaleRequestStr);
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "21");
                        socket.close();
                        return ResponseEncryptDecryptError;
                    }

                } else {
                    if (encryptResponeFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestDoSale_TCP", "Client requested Raw Response, Terminal Response Encrypted Response");
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "22");
                        socket.close();
                        return ResponseEncryptDecryptError;

                    } else if (RawResponseFoundFromTerminal) {

                        JSONObject JsonResponseObject = new JSONObject(BinSaleRequestStr);
                        String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                        if (!DataTypeFieldResponse.equals("sale")) {

                            LogWrite("NormalLog", "RequestDoSale_TCP", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                            JSONObject ResponseClientDataTypeError = new JSONObject();
                            ResponseClientDataTypeError.put("dataType", "NAK");
                            ResponseClientDataTypeError.put("error", "12");
                            socket.close();
                            return ResponseClientDataTypeError;
                        }

                        String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                        JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                        String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                        JSONObject JsonResponseObject_binSale = new JSONObject(BinSaleRequestStr);
                        JsonResponseObject_binSale.remove("checksum");

                        LogWrite("NormalLog", "RequestDoSale_TCP", "Sent Terminal Response(raw) to Client successfully");
                        socket.close();
                        return JsonResponseObject_binSale;

                    }
                }

                String DecryptedKeyString = GetKeyFromLog();

                if (DecryptedKeyString.equals("")) {

                    LogWrite("NormalLog", "RequestDoSale_TCP", "Decryption error log file key");
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "23");
                    socket.close();
                    return KeyEncryptError;
                }

                DecryptedKeyString = DecryptedKeyString + DecryptedKeyString.substring(0, 16);

                byte[] resByteArray = null;

                try {
                    resByteArray = decrypt3Des(BinSaleRequestStr, DecryptedKeyString);
                } catch (Exception ex) {

                    LogWrite("NormalLog", "RequestDoSale_TCP", "Terminal Response Decryption Error --> Exception Error :" + ex.getMessage());
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    socket.close();
                    return ResponseClientDataTypeError;
                }

                if (resByteArray == null) {

                    LogWrite("NormalLog", "RequestDoSale_TCP", "Terminal Response Decryption null Error ");
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

                if (!DataTypeFieldResponse.equals("sale")) {

                    LogWrite("NormalLog", "RequestDoSale_TCP", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
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

                LogWrite("NormalLog", "RequestDoSale_TCP", "Sent Terminal Response(Encrypted) to Client successfully");
                socket.close();
                return JsonResponseObject_binSale;

            } else {

                LogWrite("NormalLog", "RequestDoSale_TCP", "Terminal Final Response Received data not valid --> Recieved data :" + BinSaleRequestStr);
                JSONObject ReceiveError = new JSONObject();
                ReceiveError.put("dataType", "NAK");
                ReceiveError.put("error", "13");
                socket.close();
                return ReceiveError;
            }

        } catch (UnknownHostException ex) {

            LogWrite("NormalLog", "RequestDoSale_TCP", "TCP UnknownHostException Error --> Recieved data :" + ex.getMessage());
            JSONObject ExceptionError = new JSONObject();
            ExceptionError.put("dataType", "NAK");
            ExceptionError.put("error", "14");
            try {
                socket.close();
            } catch (IOException ex1) {
                
            }
            return ExceptionError;

        } catch (IOException ex) {

            LogWrite("NormalLog", "RequestDoSale_TCP", "TCP IOException Error --> Recieved data :" + ex.getMessage());
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

    //usb serial communication RequestDoSale
    public JSONObject RequestDoSale_USB() {

        LogWrite("LogStart", "RequestDoSale_USB", "Function Starting");

        //fIND PAX COM PORT
        String ComPort = "";

        ComPort = PaxPort.discover();   //gimhan added - shared port owner, port stays open so a cancel can use it

        if (PortMatch.isUsablePortName(ComPort)) {   // ISO8583Studio patch: was ComPort.contains("COM")

        } else {

            LogWrite("NormalLog", "RequestDoSale_USB", "Pax Com Port Not Found");
            JSONObject PortOpenError = new JSONObject();
            PortOpenError.put("dataType", "NAK");
            PortOpenError.put("error", "15");
            return PortOpenError;
        }

        OutputStream outputStream;

        SerialPort port = PaxPort.acquire(ComPort);   //gimhan added - shared port owner, port stays open so a cancel can use it

        if (port == null) {
                LogWrite("NormalLog", "RequestDoSale_USB", "Com Port Open Error :" + ComPort);
                JSONObject PortOpenError = new JSONObject();
                PortOpenError.put("dataType", "NAK");
                PortOpenError.put("error", "16");
                return PortOpenError;

        }

        String DataTypeFieldRequest = DoSaleRequestJsonObjectCommand.getString("dataType");

        if (!DataTypeFieldRequest.equals("sale")) {
            LogWrite("NormalLog", "RequestDoSale_USB", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
            JSONObject RequestClientDataTypeError = new JSONObject();
            RequestClientDataTypeError.put("dataType", "NAK");
            RequestClientDataTypeError.put("error", "10");
            PaxPort.release();
            return RequestClientDataTypeError;
        }

        JSONObject DataFieldRequest = DoSaleRequestJsonObjectCommand.getJSONObject("data");

        String DataFieldEncoded = sha256(DataFieldRequest.toString());

        if (DataFieldEncoded.length() > 10) {
            DoSaleRequestJsonObjectCommand.put("checksum", DataFieldEncoded);
        } else {
            LogWrite("NormalLog", "RequestDoSale_USB", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
            JSONObject CheckSumCalculateError = new JSONObject();
            CheckSumCalculateError.put("dataType", "NAK");
            CheckSumCalculateError.put("error", "11");
            PaxPort.release();
            return CheckSumCalculateError;
        }

        String DoSaleRequestCmdStr1 = DoSaleRequestJsonObjectCommand.toString();
        String DoSaleRequestCmdStr = DoSaleRequestCmdStr1 + "\n";
        
        outputStream = port.getOutputStream();
        port.flushIOBuffers();
        try {
            outputStream.write(DoSaleRequestCmdStr.getBytes());
        } catch (IOException ex) {
            LogWrite("NormalLog", "RequestDoSale_USB", "Com Port Write Exception :" + ex.getMessage());
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
                Thread.sleep(50);
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
            LogWrite("NormalLog", "RequestDoSale_USB", "ACK Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "18");
            PaxPort.release();
            return TimeOutError;
        }

        String DoSaleAckStr = "";
        try {
            DoSaleAckStr = new String(AckReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {

            LogWrite("NormalLog", "RequestDoSale_USB", "Byte to String convert error :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");
            PaxPort.release();
            return TimeOutError;
        }

        //System.out.println("Received ACK -> "+ DoSaleAckStr);
        if (DoSaleAckStr.length() > 10) {
            
            LogWrite("NormalLog", "RequestDoSale_USB", "Terminal ACK Response :" + DoSaleAckStr);
            
            JSONObject obj = new JSONObject(DoSaleAckStr);
            String data = obj.get("dataType").toString();

            if (data.equals("ACK")) {
                isA920 = false;
                LogWrite("NormalLog", "RequestDoSale_USB", "Sent Command to terminal successfully(A910)");
            }else if(data.equals("ack")){
                isA920 = true;
                LogWrite("NormalLog", "RequestDoSale_USB", "Sent Command to terminal successfully(A920)");
            } else {
                LogWrite("NormalLog", "RequestDoSale_USB", "Terminal Side error code json found :" + DoSaleAckStr);
                JSONObject JsonResponseObject = new JSONObject(DoSaleAckStr);
                PaxPort.release();
                return JsonResponseObject;
            }
        } else {

            LogWrite("NormalLog", "RequestDoSale_USB", "Terminal Response not valid :" + DoSaleAckStr);
            JSONObject AckReceiveError = new JSONObject();
            AckReceiveError.put("dataType", "NAK");
            AckReceiveError.put("error", "13");

            PaxPort.release();
            return AckReceiveError;
        }

        port.flushIOBuffers();

        byte[] DoSaleReadBuffer = new byte[2048];
        boolean check1 = false;
        int y = 0;
        int numRead = 0;

        while (check1 == false && y < intTo) {

            numRead = port.readBytes(DoSaleReadBuffer,
                    DoSaleReadBuffer.length);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            if (numRead > 10) {
                //gimhan added - a cancel running on another thread owns any ACK/NAK that lands here
                if (PaxPort.isCancelReply(new String(DoSaleReadBuffer, java.nio.charset.StandardCharsets.UTF_8))) {
                    java.util.Arrays.fill(DoSaleReadBuffer, (byte) 0);
                    y++;
                    continue;
                }

                //System.out.print("Read " + numRead + " bytes -");
                check1 = true;
                
                if(isA920){
                    
                    JSONObject confirmResponse = new JSONObject();
                    confirmResponse.put("dataType", "ack");
                    
                    String confirmResponseStr1 = confirmResponse.toString();
                    String confirmResponseStr = confirmResponseStr1 + "\n";
                    
                    try {
                        outputStream.write(confirmResponseStr.getBytes());
                    } catch (IOException ex) {

                        LogWrite("NormalLog", "RequestDoSale_USB", "A920 confirm msg write exception :" + ex.getMessage());
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

        if (y == intTo) {

            LogWrite("NormalLog", "RequestDoSale_USB", "Terminal Response Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "20");
            PaxPort.release();
            return TimeOutError;
        }

        String DoSaleStr = "";
        try {
            DoSaleStr = new String(DoSaleReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {

            LogWrite("NormalLog", "RequestDoSale_USB", "Terminal Response bytes to string convert Exception :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");
            PaxPort.release();
            return TimeOutError;
        }

        //System.out.println("Received response -> "+ DoSaleStr);
        if (DoSaleStr.length() > 10) {
            
            
            if(isA920){
                
                LogWrite("NormalLog", "RequestDoSale_USB", "Terminal Process Response(A920) :" + DoSaleStr);
                
                JSONObject JsonResponseObject = new JSONObject(DoSaleStr);
                String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                if (!DataTypeFieldResponse.equals("sale")) {

                    LogWrite("NormalLog", "RequestDoSale_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "12");
                    PaxPort.release();
                    return ResponseClientDataTypeError;
                }

                JSONObject JsonResponseObject_RequestDoSale = new JSONObject(DoSaleStr);
                JsonResponseObject_RequestDoSale.put("deviceType", "A920");
                LogWrite("NormalLog", "RequestDoSale_USB", "Sent Terminal Response(raw) to Client successfully");
                PaxPort.release();
                return JsonResponseObject_RequestDoSale;
                
            }else{
                                               
                LogWrite("NormalLog", "RequestDoSale_USB", "Terminal Process Response :" + DoSaleStr);

                boolean encryptResponeFoundFromTerminal = false;
                boolean RawResponseFoundFromTerminal = false;

                try {

                    JSONObject JsonResponseObject = new JSONObject(DoSaleStr);
                    String CheckEncryptDecrypt = JsonResponseObject.getString("dataType");
                    RawResponseFoundFromTerminal = true;

                } catch (JSONException ex) {
                    encryptResponeFoundFromTerminal = true;
                }

                if (EnableEncrypt) {

                    if (encryptResponeFoundFromTerminal) {

                    } else if (RawResponseFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestDoSale_USB", "Client requested Encrypt Response, Terminal Response Raw Response --> Terminal Response :" + DoSaleStr);
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "21");
                        PaxPort.release();
                        return ResponseEncryptDecryptError;
                    }

                } else {
                    if (encryptResponeFoundFromTerminal) {

                        LogWrite("NormalLog", "RequestDoSale_USB", "Client requested Raw Response, Terminal Response Encrypted Response");
                        JSONObject ResponseEncryptDecryptError = new JSONObject();
                        ResponseEncryptDecryptError.put("dataType", "NAK");
                        ResponseEncryptDecryptError.put("error", "22");
                        PaxPort.release();
                        return ResponseEncryptDecryptError;

                    } else if (RawResponseFoundFromTerminal) {

                        JSONObject JsonResponseObject = new JSONObject(DoSaleStr);
                        String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                        if (!DataTypeFieldResponse.equals("sale")) {

                            LogWrite("NormalLog", "RequestDoSale_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                            JSONObject ResponseClientDataTypeError = new JSONObject();
                            ResponseClientDataTypeError.put("dataType", "NAK");
                            ResponseClientDataTypeError.put("error", "12");
                            PaxPort.release();
                            return ResponseClientDataTypeError;
                        }

                        String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                        JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                        String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                        JSONObject JsonResponseObject_RequestDoSale = new JSONObject(DoSaleStr);
                        JsonResponseObject_RequestDoSale.put("deviceType", "A910");
                        JsonResponseObject_RequestDoSale.remove("checksum");

                        LogWrite("NormalLog", "RequestDoSale_USB", "Sent Terminal Response(raw) to Client successfully");
                        PaxPort.release();
                        return JsonResponseObject_RequestDoSale;

                    }
                }

                String testData = byte2HexStr(DoSaleReadBuffer);

                if (testData.equals("")) {
                    LogWrite("NormalLog", "RequestDoSale_USB", "byte2HexStr Calculate Error --> Function parameter :" + testData);
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "24");
                    PaxPort.release();
                    return KeyEncryptError;
                }

                String str = new String(testData.getBytes(), 0, numRead * 2, StandardCharsets.UTF_8);

                String DecryptedKeyString = GetKeyFromLog();

                if (DecryptedKeyString.equals("")) {

                    LogWrite("NormalLog", "RequestDoSale_USB", "Decryption error log file key");
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

                    LogWrite("NormalLog", "RequestDoSale_USB", "Terminal Response Decryption Error --> Exception Error :" + ex.getMessage());
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    PaxPort.release();
                    return ResponseClientDataTypeError;
                }

                if (resByteArray == null) {

                    LogWrite("NormalLog", "RequestDoSale_USB", "Terminal Response Decryption null Error ");
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "24");
                    PaxPort.release();
                    return ResponseClientDataTypeError;
                }

                String DoSaleEncryptedRawData = new String(resByteArray);

                String WithoutEncryptData = DoSaleEncryptedRawData.substring(64, DoSaleEncryptedRawData.length());

                JSONObject JsonResponseObject = new JSONObject(WithoutEncryptData);
                String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                if (!DataTypeFieldResponse.equals("sale")) {

                    LogWrite("NormalLog", "RequestDoSale_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "12");
                    PaxPort.release();
                    return ResponseClientDataTypeError;
                }

                String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                JSONObject JsonResponseObject_RequestDoSale = new JSONObject(WithoutEncryptData);
                JsonResponseObject_RequestDoSale.put("deviceType", "A910");
                JsonResponseObject_RequestDoSale.remove("checksum");

                LogWrite("NormalLog", "RequestDoSale_USB", "Sent Terminal Response(Encrypt) to Client successfully");
                PaxPort.release();
                return JsonResponseObject_RequestDoSale;
                                     
            }

        } else {

            LogWrite("NormalLog", "RequestDoSale_USB", "Terminal Final Response Received data not valid --> Recieved data :" + DoSaleStr);
            JSONObject ReceiveError = new JSONObject();
            ReceiveError.put("dataType", "NAK");
            ReceiveError.put("error", "13");
            PaxPort.release();
            return ReceiveError;
        }

    }

}
