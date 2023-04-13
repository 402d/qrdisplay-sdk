package qrbt.sdk;


import android.content.Context;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import rawbt.sdk.drivers.AbstractDriverWithTransport;
import rawbt.sdk.transport.BLE;
import rawbt.sdk.transport.BleDeviceInterface;
import rawbt.sdk.transport.ConnectParameters;

public class MertechQrDisplay extends AbstractDriverWithTransport implements BleDeviceInterface {
    String deviceName = "unknown";

    public MertechQrDisplay(Context context) {
        setContext(context);
    }

    @Override
    public String connectDevice(ConnectParameters connectParameters) {
        resetError();
        String rez = super.connectDevice(connectParameters);
        if ("ok".equals(rez)) {
            getVersion();
            if (isError()) {
                Log.e("BTDISPLAY","Error "+getErrorMessage());
                disconnectDevice();
                return getErrorMessage();
            }
            cls();

            if (transport instanceof BLE) {
                deviceName = ((BLE) transport).getDeviceName();
            }

        }
        return rez;
    }

    @Override
    public final void disconnectDevice() {
        if (transport == null) return;
        cls();
        try{
            Thread.sleep(100);
        }catch (Exception ignored){}

        this.transport.disconnect();
    }


    // ==============================

    public void getVersion() {
        transport_write(new byte[]{0x02, (byte) 0xF0, 0x03, '0', 'D', '1', '3', '0', '2', 0x03});
    }

    // =====================================
    //   COMMAND
    // =====================================

    public void symbolOk() {
        transport_write(new byte[]{0x02, (byte) 0xF0, 0x03, 'c', 'o', 'r', 'r', 'e', 'c', 't', 0x03});
    }

    public void symbolFail() {
        transport_write(new byte[]{0x02, (byte) 0xF0, 0x03, 'm', 'i', 's', 't', 'a', 'k', 'e', 0x03});
    }

    public void cls() {
        transport_write(new byte[]{0x02, (byte) 0xF0, 0x03, 'C', 'L', 'S', 0x03});
    }


    // =====================================
    //  SEND DATA
    // =====================================

    public void sendBytes(byte[] bytes){
        transport_write(bytes);
    }

    public void qrCode(String qr) {  // 02 F2
        byte[] content = qr.getBytes(StandardCharsets.UTF_8);
        try {
            // исходная длина массива
            int len = content.length;
            // создаем массив байтов исходный + 3 (префикс) + 2(длина)+ 3(суффикс) = 8
            byte[] data = new byte[len + 8];
            data[0] = 0x02;
            data[1] = (byte) 0xF2;
            data[2] = 0x02;
            data[3] = (byte) (len >>> 8 & 0xFF);
            data[4] = (byte) (len & 0xFF);
            int pos = 5;
            for (byte b : content) {
                data[pos] = b;
                pos++;
            }
            data[pos] = 0x02;
            data[pos + 1] = (byte) 0xF2;
            data[pos + 2] = 0x03;

            // и в буфер вывода
            transport_write(data);
        } catch (Exception e) {
            e.printStackTrace();
            transportError(e.getLocalizedMessage());
        }
    }


    // ================================================================
    @Override
    public UUID[] getWriteUuid() {
        return new UUID[]{
                UUID.fromString("6d400002-b5a3-f393-e0a9-e50e24dcca9e"),
                UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        };
    }

    @Override
    public UUID[] getNotifyUuid() {
        return new UUID[]{
                UUID.fromString("6d400003-b5a3-f393-e0a9-e50e24dcca9e"),
                UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        };
    }


    // ---------------

    @Override
    public void receiveFromDevice(byte[] b) {
        if (jobTask != null && b.length > 0) jobTask.receiveFromDevice(b);
    }

    @Override
    public void onTransportConnect() {
        if (jobTask != null) jobTask.onTransportConnect();
    }

    @Override
    public void onTransportDisconnect() {
        if (jobTask != null) jobTask.onTransportDisconnect();
    }


}
