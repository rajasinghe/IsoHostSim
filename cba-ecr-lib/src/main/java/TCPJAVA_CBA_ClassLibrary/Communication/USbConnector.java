package TCPJAVA_CBA_ClassLibrary.Communication;

import TCPJAVA_CBA_ClassLibrary.Exceptions.DeviceExceptions;
import TCPJAVA_CBA_ClassLibrary.Utility;
import com.fazecast.jSerialComm.SerialPort;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class USbConnector extends Utility implements Connector {

    private static USbConnector instance;
    private SerialPort serialPort;

    public static USbConnector getInstance(){
        if(instance == null){
            instance = new USbConnector();
        }
        return instance;
    }

    private USbConnector(){
        //private constructor to prevent instantiation
    }

    private String getPaxDevicePort() throws DeviceExceptions {
        // POS Testing Platform patch: discovery (PAX description, forced port, Linux ttyACM/ttyUSB
        // fallback) and the "COM" check both go through PaxPort/PortMatch.
        String comPort = PaxPort.discover();
        if(!PortMatch.isUsablePortName(comPort)){
            LogWrite("NormalLog", "CheckStatus_USB", "Pax Com Port Not Found");
            throw new DeviceExceptions("","15");
        }
        return comPort;
    }

    private void establishConnection(String paxComPort) throws DeviceExceptions{
        serialPort = SerialPort.getCommPort(paxComPort);
        serialPort.setComPortParameters(9600, 8, 1, 0);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, (int) 0,(int) 3000);
        if(!serialPort.openPort()){
           serialPort.closePort();
           if(!serialPort.openPort()){
               LogWrite("NormalLog", "RequestLast_USB", "Com Port Open Error :" + paxComPort);
               throw new DeviceExceptions("Com Port Open Error","16");
           }
        }
        serialPort.flushIOBuffers();
    }

    public void send(JSONObject jsonObject) throws DeviceExceptions {
        if(!serialPort.isOpen()){
            String serialPortName = getPaxDevicePort();
            establishConnection(serialPortName);
        }
        OutputStream outputStream=serialPort.getOutputStream();
        serialPort.flushIOBuffers();

        String requestString = jsonObject.toString() + "\n";
        try{
            outputStream.write(requestString.getBytes());
        }catch (Exception e){
            LogWrite("NormalLog", "sendCmd", "Com Port Write Exception :" + e.getMessage());
            throw new DeviceExceptions.SendException("Com Port Write Exception");
        }
    }

    public JSONObject receive() throws DeviceExceptions{
        if(!serialPort.isOpen()){
            String serialPortName = getPaxDevicePort();
            establishConnection(serialPortName);
        }
        byte[] readBuffer = new byte[2048];
        boolean check = false;
        int x = 0;
        while (check == false && x < 50) {
            int numRead = serialPort.readBytes(readBuffer,
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
            LogWrite("NormalLog", "recieveResponse", "ACK Timeout");
            throw new DeviceExceptions("timeout","18");
        }
        String responseString = "";
        try {
            responseString = new String(readBuffer, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            LogWrite("NormalLog", "recieveResponse", "Byte to String convert error :" + ex.getMessage());
            throw new DeviceExceptions("convert error","19");
        }

        if(responseString.length()<10){
            LogWrite("NormalLog", "recieveResponse", "response length is less than 10 - invalid terminal Response:" + responseString);
            throw new DeviceExceptions("invalid terminal response","13");

        }
        return new JSONObject(responseString);
    }
}
