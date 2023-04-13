package rawbt.sdk.transport;

public interface ConnectParameters {
    int getProtocol();

    // BT
    String get_mac();


    // USB
    String get_vid();
    String get_pid();

}
