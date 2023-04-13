package rawbt.sdk.drivers;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.Arrays;

import rawbt.sdk.transport.ConnectParameters;
import rawbt.sdk.transport.Transport;
import rawbt.sdk.transport.TransportFactory;

abstract public class AbstractDriverWithTransport implements Transport.CallBack{
    private Context context;
    @Override
    public final Context getContext() {
        return context;
    }

    public final void setContext(Context context) {
        this.context = context;
    }
    protected Transport transport = null;

    public String connectDevice(ConnectParameters connectParameters){
        if(Looper.getMainLooper().getThread() == Thread.currentThread()){
            throw new IllegalStateException("run on main thread not allowed");
        }

        try {
            transport = TransportFactory.get(connectParameters);
            transport.injectDriver(this);
        } catch (Exception e) {
            setErrorMessage(e.getLocalizedMessage());
            return  e.getLocalizedMessage();
        }

        if (this.transport == null) {
            setErrorMessage("transport is null");
            return "transport is null";
        }

        String rez = this.transport.connect();
        if(!"ok".equals(rez)){
            setErrorMessage(rez);
        }
        return rez;
    }


    public void disconnectDevice() {
        if (transport == null) return;
        this.transport.disconnect();
    }


    public final void transport_write(byte[] b) {
        if (isMainThread()) {
            throw new IllegalStateException("Cannot access printer driver on the main thread since"
                    + " it may potentially lock the UI for a long period of time.");
        }
        if (transport != null) {
            if (!isCancelled()) {
                if(!transport.isConnected()){
                    setErrorMessage("not connected");
                    return;
                }
                String res = transport.write(b);
                if (!"ok".equals(res)) {
                    setErrorMessage(res);
                }
            }
        }
    }



    /** Returns true if the calling thread is the main thread. */
    private static boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    // ----------------------------------------
    private boolean errorPresent = false;
    private String errMsg = null;


    public boolean isError() {
        return errorPresent;
    }

    public void resetError(){
        errorPresent = false;
        errMsg = null;
    }

    public final String getErrorMessage() {
        return errMsg;
    }

    public final void setErrorMessage(String errMsg){
        errorPresent = true;
        this.errMsg = errMsg;
    }

    // -----------------
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes, int length) {
        char[] hexChars = new char[length * 3 - 1];

        Arrays.fill(hexChars, ' ');

        for (int j = 0; j < length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = HEX_ARRAY[v >>> 4];
            hexChars[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    // ----------------------------------------
    protected PosDriverInterface.TaskForTransport jobTask = null;

    public final boolean isCancelled() {
        if (jobTask == null) {
            return false;
        }
        return jobTask.isCancelled();
    }


    public  final void interactiveTask(@NonNull PosDriverInterface.TaskForTransport jobTask) {
        this.jobTask = jobTask;
    }

    @Override
    public  final void transportMessage(String msg) {
        if (jobTask != null) jobTask.deviceProgress(msg);
    }
    @Override
    public final void transportError(String msg) {
        errorPresent = true;
        errMsg = msg;
        if (jobTask != null) jobTask.deviceError(msg);
    }

    @Override
    public void sentToDevice(byte[] b) {
        if (jobTask != null) jobTask.sentToDevice(b);
    }
}
