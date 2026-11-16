package TCPJAVA_CBA_ClassLibrary.Requests;

import TCPJAVA_CBA_ClassLibrary.Communication.Connector;
import org.json.JSONObject;

public class BinRequest extends Request{

    public BinRequest(Connector connector) {
        super(connector);
    }

    @Override
    public JSONObject request(JSONObject requestData) {
        return null;
    }
}
