package TCPJAVA_CBA_ClassLibrary.Requests;

import TCPJAVA_CBA_ClassLibrary.Communication.Connector;
import TCPJAVA_CBA_ClassLibrary.Exceptions.DeviceExceptions;
import org.json.JSONObject;

public class CheckStatus extends Request {
    public CheckStatus(Connector connector) {
        super(connector);
    }

    public JSONObject checkStatus(){
        JSONObject obj = new JSONObject();
        obj.put("dataType", "active");
        return request(obj);
    }

    @Override
    public JSONObject request(JSONObject requestData) {
        try{
            JSONObject responseJson = new JSONObject();
            connector.send(requestData);
            JSONObject ackResponseJson = connector.receive();
            String ackResponseString  = ackResponseJson.getString("dataType");
            if(ackResponseString.equals("ACK") || ackResponseString.equals("ack")){
                responseJson.put("dataType", "ACK");
            }else{
                responseJson.put("dataType", "NAK");
                responseJson.put("error", "13");
            }
            return responseJson;
        }catch (DeviceExceptions.SendException se){
            JSONObject responseJson = new JSONObject();
            responseJson.put("dataType", "NAK");
            responseJson.put("error", "17");
            return responseJson;
        } catch (DeviceExceptions deviceException){
            JSONObject responseJson = new JSONObject();
            responseJson.put("dataType", "NAK");
            responseJson.put("error", deviceException.getCode());
            return responseJson;
        }catch (Exception e){
            JSONObject responseJson = new JSONObject();
            responseJson.put("dataType", "NAK");
            responseJson.put("error", "17");
            return responseJson;
        }
    }
}
