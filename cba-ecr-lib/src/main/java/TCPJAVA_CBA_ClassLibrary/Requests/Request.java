package TCPJAVA_CBA_ClassLibrary.Requests;

import TCPJAVA_CBA_ClassLibrary.Communication.Connector;
import TCPJAVA_CBA_ClassLibrary.Exceptions.DeviceExceptions;
import org.json.JSONObject;

public abstract class Request {
    public Connector connector;

    public Request(Connector connector) {
        this.connector = connector;
    }

    public abstract JSONObject request(JSONObject requestData);
}
