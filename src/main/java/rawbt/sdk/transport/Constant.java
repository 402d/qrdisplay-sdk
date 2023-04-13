package rawbt.sdk.transport;


public class Constant {
    public static final int PROTOCOL_NOT_SET = 0;
    public static final int PROTOCOL_USB = 3;
    public static final int PROTOCOL_BLE = 6;



    static public String getProtocolName(int tr_type){
        String r="Unknown";
        switch(tr_type){
            case PROTOCOL_BLE:
                r = "Bluetooth Low Energy";
                break;
            case PROTOCOL_USB:
                r = "USB cable";
                break;
        }
        return r;
    }

}
