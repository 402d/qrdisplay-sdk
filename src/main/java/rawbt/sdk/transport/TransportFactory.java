package rawbt.sdk.transport;

import static rawbt.sdk.transport.Constant.*;

public class TransportFactory {
    static public Transport get(ConnectParameters settings) throws TransportException {

        Transport transport = null;

        switch (settings.getProtocol()){
            case PROTOCOL_BLE: {
                String mac = settings.get_mac();
                if (!"00:00:00:00:00:00".equals(mac)) {
                    transport = new BLE();
                    ((BLE) transport).setConnectParam(mac);
                }
                break;
            }
            case PROTOCOL_USB:
                transport = new USB();
                ((USB)transport).setConnectParam(settings.get_vid(),settings.get_pid());
                break;
        }

        if(transport==null){
            throw  new TransportException("Device not configured");
        }
        return transport;
    }
}
