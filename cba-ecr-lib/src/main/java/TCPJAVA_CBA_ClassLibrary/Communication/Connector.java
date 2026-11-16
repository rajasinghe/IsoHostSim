package TCPJAVA_CBA_ClassLibrary.Communication;

import TCPJAVA_CBA_ClassLibrary.Exceptions.DeviceExceptions;
import org.json.JSONObject;

public interface Connector {
    public void send(JSONObject data) throws DeviceExceptions;
    public JSONObject receive() throws DeviceExceptions;
}
