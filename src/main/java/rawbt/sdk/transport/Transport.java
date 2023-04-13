package rawbt.sdk.transport;

import android.content.Context;


public interface Transport{
    interface CallBack {
        void transportMessage(String msg);
        void transportError(String msg);
        void receiveFromDevice(byte[] b);
        void sentToDevice(byte[] b);
        boolean isCancelled();
        boolean isError();
        Context getContext();
        void onTransportConnect();
        void onTransportDisconnect();
    }

    void injectDriver(CallBack callback);
    String write(byte[] d);
    String connect();
    void disconnect();
    boolean isConnected();
}

