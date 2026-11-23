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
 *
 */
public class TransferKeyRequest extends Utility {

    String strIP;
    int Port;
    JSONObject TransferKeyJsonObjectCommand;
    int intTo;

    TransferKeyRequest(String strIP, int Port, JSONObject TransferKeyJsonObjectCommand, int intTo) {
        this.strIP = strIP;
        this.Port = Port;
        this.TransferKeyJsonObjectCommand = TransferKeyJsonObjectCommand;
        this.intTo = intTo;
    }

    TransferKeyRequest(JSONObject TransferKeyJsonObjectCommand, int intTo) {
        this.TransferKeyJsonObjectCommand = TransferKeyJsonObjectCommand;
        this.intTo = intTo;
    }

    //TCP serial communication TransferKey
    public JSONObject RequestTransferKey_TCP() {

        LogWrite("LogStart", "RequestTransferKey_TCP", "Function Starting Ip: " + strIP + " Port:" + Port);

        Socket socket = null;
        
        try {

            String DataTypeFieldRequest = TransferKeyJsonObjectCommand.getString("dataType");

            if (!DataTypeFieldRequest.equals("KeyTransfer")) {
                LogWrite("NormalLog", "RequestTransferKey_TCP", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
                JSONObject RequestClientDataTypeError = new JSONObject();
                RequestClientDataTypeError.put("dataType", "NAK");
                RequestClientDataTypeError.put("error", "10");
                return RequestClientDataTypeError;
            }

            JSONObject DataFieldRequest = TransferKeyJsonObjectCommand.getJSONObject("data");

            String KeyFieldRequest = DataFieldRequest.getString("Key");
                      
            if (KeyFieldRequest.equals("") || KeyFieldRequest.length() != 32) {

                LogWrite("NormalLog", "RequestTransferKey_TCP", "Client Key length not valid --> Key Requested :" + KeyFieldRequest);
                JSONObject RequestClientKeyError = new JSONObject();
                RequestClientKeyError.put("dataType", "NAK");
                RequestClientKeyError.put("error", "25");
                return RequestClientKeyError;
            }            

            String DataFieldEncoded = sha256(DataFieldRequest.toString());

            if (DataFieldEncoded.length() > 10) {
                TransferKeyJsonObjectCommand.put("checksum", DataFieldEncoded);
            } else {
                LogWrite("NormalLog", "RequestTransferKey_TCP", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
                JSONObject CheckSumCalculateError = new JSONObject();
                CheckSumCalculateError.put("dataType", "NAK");
                CheckSumCalculateError.put("error", "11");
                return CheckSumCalculateError;
            }
            
            String tmp = TransferKeyJsonObjectCommand.toString();
            
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(strIP, Port);
            socket.connect(socketAddress, 5000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);   
                       
            writer.println(tmp);
            writer.flush();

            BufferedReader KeyTransferAck = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] Ackbuffer = new char[2048];
            String KeyTransferAckAckStr = "";

            socket.setSoTimeout(3000);

            KeyTransferAck.read(Ackbuffer);

            KeyTransferAckAckStr = new String(Ackbuffer);

            if (KeyTransferAckAckStr.length() > 10) {
                JSONObject obj = new JSONObject(KeyTransferAckAckStr);
                String data = obj.get("dataType").toString();

                if (data.equals("ACK")) {
                    LogWrite("NormalLog", "RequestTransferKey_TCP", "Sent Command to terminal successfully");
                } else {
                    LogWrite("NormalLog", "RequestTransferKey_TCP", "Terminal Side error code json found :" + KeyTransferAckAckStr);
                    JSONObject JsonResponseObject = new JSONObject(KeyTransferAckAckStr);
                    socket.close();
                    return JsonResponseObject;
                }
            } else {
                LogWrite("NormalLog", "RequestTransferKey_TCP", "Terminal Response not valid :" + KeyTransferAckAckStr);
                JSONObject AckReceiveError = new JSONObject();
                AckReceiveError.put("dataType", "NAK");
                AckReceiveError.put("error", "13");
                socket.close();
                return AckReceiveError;
            }

            BufferedReader KeyTransfer = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            char[] KeyTransferbuffer = new char[2048];
            String KeyTransferStr = "";

            socket.setSoTimeout(intTo * 1000);

            KeyTransfer.read(KeyTransferbuffer);

            KeyTransferStr = new String(KeyTransferbuffer);

            if (KeyTransferStr.length() > 10) {

                //System.out.println(BinRequestStr);
                JSONObject JsonResponseObject = new JSONObject(KeyTransferStr);
                String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

                if (!DataTypeFieldResponse.equals("KeyTransfer")) {
                    LogWrite("NormalLog", "RequestTransferKey_TCP", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                    JSONObject ResponseClientDataTypeError = new JSONObject();
                    ResponseClientDataTypeError.put("dataType", "NAK");
                    ResponseClientDataTypeError.put("error", "12");
                    socket.close();
                    return ResponseClientDataTypeError;
                }

                String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
                JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

                String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

                String KeyTransferResponseFieldResponse = DataFieldResponse.getString("response");

                if (KeyTransferResponseFieldResponse.equals("KIS")) {

                    byte[] EnryptedKeyBytes;
                    try {
                        EnryptedKeyBytes = Keyencrypt(KeyFieldRequest, KeyEncrypt_Key);
                    } catch (Exception ex) {
                        LogWrite("NormalLog", "RequestTransferKey_TCP", "Client Key Encryption Error --> Exception Error :" + ex.getMessage());
                        JSONObject KeyEncryptError = new JSONObject();
                        KeyEncryptError.put("dataType", "NAK");
                        KeyEncryptError.put("error", "26");
                        socket.close();
                        return KeyEncryptError;
                    }

                    if (EnryptedKeyBytes == null) {
                        LogWrite("NormalLog", "RequestTransferKey_TCP", "Encrypyted Key null Error");
                        JSONObject KeyEncryptError = new JSONObject();
                        KeyEncryptError.put("dataType", "NAK");
                        KeyEncryptError.put("error", "26");
                        socket.close();
                        return KeyEncryptError;
                    }

                    String testData = byte2HexStr(EnryptedKeyBytes);

                    if (testData.equals("")) {
                        LogWrite("NormalLog", "RequestTransferKey_TCP", "byte2HexStr Calculate Error --> Function parameter :" + testData);
                        JSONObject KeyEncryptError = new JSONObject();
                        KeyEncryptError.put("dataType", "NAK");
                        KeyEncryptError.put("error", "27");
                        socket.close();
                        return KeyEncryptError;
                    }

                    boolean KeyWrite = KeyLogWrite("KeyTransfer", testData);

                    if (!KeyWrite) {
                        LogWrite("NormalLog", "RequestTransferKey_TCP", "Encrypted Key write error to key file --> Key Write :" + KeyWrite);
                        JSONObject KeyEncryptError = new JSONObject();
                        KeyEncryptError.put("dataType", "NAK");
                        KeyEncryptError.put("error", "27");
                        socket.close();
                        return KeyEncryptError;
                    }

                    String DecryptedKeyString = GetKeyFromLog();

                    if (DecryptedKeyString.equals("")) {
                        LogWrite("NormalLog", "RequestTransferKey_TCP", "Decryption error log file key");
                        JSONObject KeyEncryptError = new JSONObject();
                        KeyEncryptError.put("dataType", "NAK");
                        KeyEncryptError.put("error", "28");
                        socket.close();
                        return KeyEncryptError;
                    }

                    if (DecryptedKeyString.equals(KeyFieldRequest)) {

                        JSONObject JsonResponseObject_KeyTransfer = new JSONObject(KeyTransferStr);
                        JsonResponseObject_KeyTransfer.remove("checksum");
                        LogWrite("NormalLog", "RequestTransferKey_TCP", "KeyTrnasfer succeed");
                        socket.close();
                        return JsonResponseObject_KeyTransfer;

                    } else {

                        LogWrite("NormalLog", "RequestTransferKey_TCP", "KeyTrnasfer Failed(Log Write) Key mismatch");
                        LogWrite("NormalLog", "RequestTransferKey_TCP", "KeyTrnasfer Failed --> Requested Key :" + KeyFieldRequest);
                        LogWrite("NormalLog", "RequestTransferKey_TCP", "KeyTrnasfer Failed --> From file Key :" + DecryptedKeyString);

                        JSONObject objKIFResponse = new JSONObject();
                        JSONObject objKIFField = new JSONObject();
                        objKIFResponse.put("dataType", "KeyTransfer");
                        objKIFField.put("response", "KIS");
                        objKIFResponse.put("data", objKIFField);
                        socket.close();
                        return objKIFResponse;
                    }

                } else {

                    LogWrite("NormalLog", "RequestTransferKey_TCP", "KeyTrnasfer Failed From terminal --> Found error Terminal :" + KeyTransferStr);
                    JSONObject JsonResponseObject_KeyTransfer = new JSONObject(KeyTransferStr);
                    JsonResponseObject_KeyTransfer.remove("checksum");
                    socket.close();
                    return JsonResponseObject_KeyTransfer;
                }

            } else {
                LogWrite("NormalLog", "RequestTransferKey_TCP", "Terminal Final Response Received data not valid --> Recieved data :" + KeyTransferStr);
                JSONObject ReceiveError = new JSONObject();
                ReceiveError.put("dataType", "NAK");
                ReceiveError.put("error", "13");
                socket.close();
                return ReceiveError;
            }

        } catch (UnknownHostException ex) {
            LogWrite("NormalLog", "RequestTransferKey_TCP", "TCP UnknownHostException Error --> Recieved data :" + ex.getMessage());
            JSONObject ExceptionError = new JSONObject();
            ExceptionError.put("dataType", "NAK");
            ExceptionError.put("error", "14");
            try {
                socket.close();
            } catch (IOException ex1) {
                
            }
            return ExceptionError;

        } catch (IOException ex) {
            LogWrite("NormalLog", "RequestTransferKey_TCP", "TCP IOException Error --> Recieved data :" + ex.getMessage());
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

    //USB serial communication TransferKey
    public JSONObject RequestTransferKey_USB() {

        LogWrite("LogStart", "RequestTransferKey_USB", "Function Starting");

        //fIND PAX COM PORT
        String ComPort = "";

        ComPort = PaxPort.discover();   // shared port owner, port stays open so a cancel can use it

        if (PortMatch.isUsablePortName(ComPort)) {   // POS Testing Platform patch: was ComPort.contains("COM")

        } else {
            LogWrite("NormalLog", "RequestTransferKey_USB", "Pax Com Port Not Found");
            JSONObject PortOpenError = new JSONObject();
            PortOpenError.put("dataType", "NAK");
            PortOpenError.put("error", "15");
            return PortOpenError;
        }

        OutputStream outputStream;

        SerialPort port = PaxPort.acquire(ComPort);   // shared port owner, port stays open so a cancel can use it

        if (port == null) {

                LogWrite("NormalLog", "RequestTransferKey_USB", "Com Port Open Error :" + ComPort);
                JSONObject PortOpenError = new JSONObject();
                PortOpenError.put("dataType", "NAK");
                PortOpenError.put("error", "16");
                return PortOpenError;

        }

        String DataTypeFieldRequest = TransferKeyJsonObjectCommand.getString("dataType");

        if (!DataTypeFieldRequest.equals("KeyTransfer")) {

            LogWrite("NormalLog", "RequestTransferKey_USB", "Requested DataType incorrect --> Client Requested DataType" + DataTypeFieldRequest);
            JSONObject RequestClientDataTypeError = new JSONObject();
            RequestClientDataTypeError.put("dataType", "NAK");
            RequestClientDataTypeError.put("error", "10");
            PaxPort.release();
            return RequestClientDataTypeError;
        }

        JSONObject DataFieldRequest = TransferKeyJsonObjectCommand.getJSONObject("data");
        String KeyFieldRequest = DataFieldRequest.getString("Key");

        if (KeyFieldRequest.equals("") || KeyFieldRequest.length() != 32) {

            LogWrite("NormalLog", "RequestTransferKey_USB", "Client Key length not valid --> Key Requested :" + KeyFieldRequest);
            JSONObject RequestClientKeyError = new JSONObject();
            RequestClientKeyError.put("dataType", "NAK");
            RequestClientKeyError.put("error", "25");
            PaxPort.release();
            return RequestClientKeyError;
        }

        String DataFieldEncoded = sha256(DataFieldRequest.toString());

        if (DataFieldEncoded.length() > 10) {
            TransferKeyJsonObjectCommand.put("checksum", DataFieldEncoded);
        } else {
            LogWrite("NormalLog", "RequestTransferKey_USB", "CheckSum Calculate Error --> Calculated checksum :" + DataFieldEncoded);
            JSONObject CheckSumCalculateError = new JSONObject();
            CheckSumCalculateError.put("dataType", "NAK");
            CheckSumCalculateError.put("error", "11");
            PaxPort.release();
            return CheckSumCalculateError;
        }

        String KeyTransferCmdStr = TransferKeyJsonObjectCommand.toString();

        outputStream = port.getOutputStream();
        port.flushIOBuffers();

        try {
            outputStream.write(KeyTransferCmdStr.getBytes());
        } catch (IOException ex) {
            LogWrite("NormalLog", "RequestTransferKey_USB", "Com Port Write Exception :" + ex.getMessage());
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
            LogWrite("NormalLog", "RequestTransferKey_USB", "ACK Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "18");
            PaxPort.release();
            return TimeOutError;
        }

        String KeyTransferAckStr;
        try {
            KeyTransferAckStr = new String(AckReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {

            LogWrite("NormalLog", "RequestTransferKey_USB", "Byte to String convert error :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");
            PaxPort.release();
            return TimeOutError;
        }

        if (KeyTransferAckStr.length() > 10) {
            JSONObject obj = new JSONObject(KeyTransferAckStr);
            String data = obj.get("dataType").toString();

            if (data.equals("ACK")) {
                LogWrite("NormalLog", "RequestTransferKey_USB", "Sent Command to terminal successfully");
            } else {
                LogWrite("NormalLog", "RequestTransferKey_USB", "Terminal Side error code json found :" + KeyTransferAckStr);
                JSONObject JsonResponseObject = new JSONObject(KeyTransferAckStr);
                PaxPort.release();
                return JsonResponseObject;
            }
        } else {
            LogWrite("NormalLog", "RequestTransferKey_USB", "Terminal Response not valid :" + KeyTransferAckStr);
            JSONObject AckReceiveError = new JSONObject();
            AckReceiveError.put("dataType", "NAK");
            AckReceiveError.put("error", "13");
            PaxPort.release();
            return AckReceiveError;
        }

        port.flushIOBuffers();

        byte[] KeyTransferReadBuffer = new byte[2048];
        boolean check1 = false;
        int y = 0;

        while (check1 == false && y < intTo) {

            int numRead = port.readBytes(KeyTransferReadBuffer,
                    KeyTransferReadBuffer.length);
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
            LogWrite("NormalLog", "RequestTransferKey_USB", "Terminal Response Timeout");
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "20");
            PaxPort.release();
            return TimeOutError;
        }

        String KeyTransferStr;
        try {
            KeyTransferStr = new String(KeyTransferReadBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {

            LogWrite("NormalLog", "RequestTransferKey_USB", "Terminal Response bytes to string convert Exception :" + ex.getMessage());
            JSONObject TimeOutError = new JSONObject();
            TimeOutError.put("dataType", "NAK");
            TimeOutError.put("error", "19");
            PaxPort.release();
            return TimeOutError;
        }

        if (KeyTransferStr.length() > 10) {

            JSONObject JsonResponseObject = new JSONObject(KeyTransferStr);
            String DataTypeFieldResponse = JsonResponseObject.getString("dataType");

            if (!DataTypeFieldResponse.equals("KeyTransfer")) {
                LogWrite("NormalLog", "RequestTransferKey_USB", "Response DataType incorrect --> Terminal Response DataType :" + DataTypeFieldResponse);
                JSONObject ResponseClientDataTypeError = new JSONObject();
                ResponseClientDataTypeError.put("dataType", "NAK");
                ResponseClientDataTypeError.put("error", "12");
                PaxPort.release();
                return ResponseClientDataTypeError;
            }

            String ChecksumFieldResponse = JsonResponseObject.getString("checksum");
            JSONObject DataFieldResponse = JsonResponseObject.getJSONObject("data");

            String DataFieldEncodedResponse = sha256(DataFieldResponse.toString());

            JSONObject KeyTransferDataFieldResponse = JsonResponseObject.getJSONObject("data");
            String KeyTransferResponseFieldResponse = KeyTransferDataFieldResponse.getString("response");

            if (KeyTransferResponseFieldResponse.equals("KIS")) {

                byte[] EnryptedKeyBytes;
                try {
                    EnryptedKeyBytes = Keyencrypt(KeyFieldRequest, KeyEncrypt_Key);
                } catch (Exception ex) {
                    LogWrite("NormalLog", "RequestTransferKey_USB", "Client Key Encryption Error --> Exception Error :" + ex.getMessage());
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "26");
                    PaxPort.release();
                    return KeyEncryptError;
                }

                if (EnryptedKeyBytes == null) {
                    LogWrite("NormalLog", "RequestTransferKey_USB", "Encrypyted Key null Error");
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "26");
                    PaxPort.release();
                    return KeyEncryptError;
                }

                String testData = byte2HexStr(EnryptedKeyBytes);

                if (testData.equals("")) {
                    LogWrite("NormalLog", "RequestTransferKey_USB", "byte2HexStr Calculate Error --> Function parameter :" + testData);
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "27");
                    PaxPort.release();
                    return KeyEncryptError;
                }

                boolean KeyWrite = KeyLogWrite("KeyTransfer", testData);

                if (!KeyWrite) {
                    LogWrite("NormalLog", "RequestTransferKey_USB", "Encrypted Key write error to key file --> Key Write :" + KeyWrite);
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "27");
                    PaxPort.release();
                    return KeyEncryptError;
                }

                String DecryptedKeyString = GetKeyFromLog();

                if (DecryptedKeyString.equals("")) {
                    LogWrite("NormalLog", "RequestTransferKey_USB", "Decryption error log file key");
                    JSONObject KeyEncryptError = new JSONObject();
                    KeyEncryptError.put("dataType", "NAK");
                    KeyEncryptError.put("error", "28");
                    PaxPort.release();
                    return KeyEncryptError;
                }

                if (DecryptedKeyString.equals(KeyFieldRequest)) {

                    JSONObject JsonResponseObject_KeyTransfer = new JSONObject(KeyTransferStr);
                    JsonResponseObject_KeyTransfer.remove("checksum");
                    LogWrite("NormalLog", "RequestTransferKey_USB", "KeyTrnasfer succeed");
                    PaxPort.release();
                    return JsonResponseObject_KeyTransfer;

                } else {

                    LogWrite("NormalLog", "RequestTransferKey_USB", "KeyTrnasfer Failed(Log Write) Key mismatch");
                    LogWrite("NormalLog", "RequestTransferKey_USB", "KeyTrnasfer Failed --> Requested Key :" + KeyFieldRequest);
                    LogWrite("NormalLog", "RequestTransferKey_USB", "KeyTrnasfer Failed --> From file Key :" + DecryptedKeyString);

                    JSONObject objKIFResponse = new JSONObject();
                    JSONObject objKIFField = new JSONObject();
                    objKIFResponse.put("dataType", "KeyTransfer");
                    objKIFField.put("response", "KIS");
                    objKIFResponse.put("data", objKIFField);
                    PaxPort.release();
                    return objKIFResponse;
                }

            } else {

                LogWrite("NormalLog", "RequestTransferKey_USB", "KeyTrnasfer Failed From terminal --> Found error Terminal :" + KeyTransferStr);
                JSONObject JsonResponseObject_KeyTransfer = new JSONObject(KeyTransferStr);
                JsonResponseObject_KeyTransfer.remove("checksum");
                PaxPort.release();
                return JsonResponseObject_KeyTransfer;
            }

        } else {

            LogWrite("NormalLog", "RequestTransferKey_USB", "Terminal Final Response Received data not valid --> Recieved data :" + KeyTransferStr);
            JSONObject ReceiveError = new JSONObject();
            ReceiveError.put("dataType", "NAK");
            ReceiveError.put("error", "13");
            PaxPort.release();
            return ReceiveError;
        }

    }

}
