package rawbt.sdk.transport;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@SuppressLint("MissingPermission")
public class BLE implements Transport {
    private final Object _locker = new Object();

    final String UUID_COMMON_PROFILE_SERVICE = "00001800-0000-1000-8000-00805f9b34fb";

    private final BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothGatt mBluetoothGatt;


    BluetoothGattCharacteristic wGatt = null;
    volatile int BUF_SIZE = 20;

    CallBack driver;
    @Override
    public void injectDriver(CallBack callback) {
        this.driver = callback;
    }


    @Override
    public String write(byte[] d) {
         if(wGatt == null) {
             return "output gatt not found";
         }
        synchronized (_locker) {
            int ln = d.length;

            byte[] buffer = new byte[BUF_SIZE];
            int offset = 0;
            int r = 0;
            int retry = 0;
            try {
                // Write the data as a bulk transfer with defined data length.
                while (ln > 0 && offset < ln) {
                    int ost = Math.min(BUF_SIZE, (ln - offset));

                    if (ost < BUF_SIZE) {
                        buffer = new byte[ost];
                    }

                    System.arraycopy(d, offset, buffer, 0, buffer.length);
                    wGatt.setValue(buffer);
                    if (mBluetoothGatt.writeCharacteristic(wGatt)) {
                        r = buffer.length;
                        offset += r;
                        retry = 0;
                    } else {
                        if(retry>500) {
                            return "wrc error";
                        }
                        Thread.sleep(10);  // sum = 500*(10+2) = 6c
                        retry ++ ;
                    }

                    // local echo
                    if (r > 0) {
                        driver.sentToDevice(buffer);
                    }
                    Thread.sleep(2);
                }
            } catch (Exception e) {
                e.printStackTrace();
                String err = e.getLocalizedMessage();
                if (err == null) {
                    err = e.getClass().getSimpleName();
                }
                return err;
            }
            SystemClock.sleep(4);
        }
        return "ok";
    }

    BluetoothDevice remoteDevice;

    private void btOn() {
        driver.transportMessage("request bt on");
        defaultAdapter.enable();
        int n = 0;
        // wait 5 sec step 10 ms
        do {
            SystemClock.sleep(10);
            n++;
        } while (defaultAdapter.getState() != BluetoothAdapter.STATE_ON && n < 500);
    }



    @Override
    public String connect() {
        Log.e("BLETRANSPORT","connect");
        isConnect = false;
        try {
            driver.transportMessage("BLE connect is start");
            if (defaultAdapter == null) {
                return "error no bluetooth";
            }

            if (defaultAdapter.getState() != BluetoothAdapter.STATE_ON) {
                btOn();
            }

            if (defaultAdapter.getState() != BluetoothAdapter.STATE_ON) {
                return "error bluetooth off";
            }


            try {
                remoteDevice = defaultAdapter.getRemoteDevice(getMacAddress());
            } catch (Exception e) {
                return e.getLocalizedMessage();
            }
            if (remoteDevice == null) {
                return "Device not found.  Unable to connect.";
            }



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mBluetoothGatt = remoteDevice.connectGatt(driver.getContext(), false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                mBluetoothGatt = remoteDevice.connectGatt(driver.getContext(), false, mGattCallback);
            }

            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);

            int n = 0;
            while (!isConnect && n < 1500) { // 15 sec. step 10 ms
                sleep(10);
                n++;
            }

            if (!isConnect) {
                return "not connected";
            }

            mBluetoothGatt.discoverServices();
            n = 0;
            while (services == null && n < 1000) { // 10 sec. step 10ms
                sleep(10);
                n++;
            }

            if (services == null) {
                disconnect();
                return "no gatt services";
            }

            if (!findOutputGatt()) {
                disconnect();
                return "output gatt not found";
            }

            sleep(100);
            listenerNotify(true);

            driver.transportMessage("BLE connect done");
            sleep(400);

            return "ok";
        }catch (SecurityException s){
            return "BLUETOOTH_CONNECT permission not granted";
        }catch (Exception e){
            e.printStackTrace();
        }
        return "exception";
    }

    public void setMtu(int s){
        // mTU
        if(s>22){
            this.mBluetoothGatt.requestMtu(s);
            waitMtu();
        }
    }

    boolean findOutputGatt(){
        if(driver instanceof BleDeviceInterface){
            SystemClock.sleep(250);
            List<UUID> need = Arrays.asList(((BleDeviceInterface) driver).getWriteUuid());
            for (Map.Entry<String, BluetoothGattCharacteristic> c : writeCharacteristics.entrySet()) {
                if(need.contains(UUID.fromString(c.getKey()))){
                    wGatt = c.getValue();
                    wGatt.setWriteType(1);
                    return true;
                }
            }
            return false;
        }
        // пока не буду рисковать с авто назначением.
        return writeCharacteristics.size()>0;
    }

    private void listenerNotify(boolean onOff){
        // прослушиваем нотифи
        UUID[] need;
        if(driver instanceof BleDeviceInterface){
            need = ((BleDeviceInterface) driver).getNotifyUuid();
        }else{
            need = new UUID[0];
        }

        if (notifyCharacteristics.size() > 0) {
            for (Map.Entry<String, BluetoothGattCharacteristic> c : notifyCharacteristics.entrySet()) {
                if (checkNeed(c.getKey(), need)) {
                    BluetoothGattCharacteristic characteristicData = c.getValue();
                    if(onOff) {
                        for (BluetoothGattDescriptor descriptor : characteristicData.getDescriptors()) {
                            if ((characteristicData.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) == BluetoothGattCharacteristic.PROPERTY_INDICATE) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                            } else {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            }
                            mBluetoothGatt.writeDescriptor(descriptor);
                        }
                    }
                    mBluetoothGatt.setCharacteristicNotification(characteristicData, onOff);
                }
            }
        }
    }

    private boolean checkNeed(String k,UUID[] a){
        if(a.length < 1) return true;
        UUID u = UUID.fromString(k);
        for (UUID i:a){
            if(u.equals(i)) return true;
        }
        return false;
    }

    @Override
    public void disconnect() {
        Log.e("BLETRANSPORT","disconnect");
        if(isConnect) {
            isConnect = false;
            if (defaultAdapter == null || mBluetoothGatt == null) {
                return;
            }
            listenerNotify(false);
            mBluetoothGatt.close();
            mBluetoothGatt.disconnect();
        }
    }

    // -----------------------------------------

    private String macAddress;
    public void setConnectParam(String param1) {
        macAddress = param1;
    }

    public String getMacAddress() {
        return macAddress;
    }

    // ----------------------------------------
    private void requestReadStringCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (defaultAdapter == null || mBluetoothGatt == null) {
            return;
        }
        if(mBluetoothGatt.readCharacteristic(characteristic)){
            waitRead();
        }
    }


    // --------------- common profile --------------
    boolean isCommonProfileFound = false;


    final String UUID_DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb";
    private String deviceName = null;
    public String getDeviceName(){
        if(deviceName == null) {
            BluetoothGattCharacteristic ch = readCharacteristics.get(UUID_DEVICE_NAME);
            if (ch != null) {
                requestReadStringCharacteristic(ch);
            }
            if(deviceName == null){
                deviceName = remoteDevice.getName();
            }
        }
        return deviceName;
    }



    // -----------------------------------------
    private boolean isConnect = false;
    public boolean isConnected() {
        return isConnect;
    }

    private List<BluetoothGattService> services = null;
    private final HashMap<String,BluetoothGattCharacteristic> readCharacteristics = new HashMap<>();
    private final HashMap<String,BluetoothGattCharacteristic> writeCharacteristics = new HashMap<>();
    private final HashMap<String,BluetoothGattCharacteristic> notifyCharacteristics = new HashMap<>();

    public HashMap<String, BluetoothGattCharacteristic> getReadCharacteristics() {
        return readCharacteristics;
    }

    public HashMap<String, BluetoothGattCharacteristic> getWriteCharacteristics() {
        return writeCharacteristics;
    }

    public HashMap<String, BluetoothGattCharacteristic> getNotifyCharacteristics() {
        return notifyCharacteristics;
    }

    synchronized void fillCharacteristic(){
        for (BluetoothGattService gattService : services) {
            if (UUID_COMMON_PROFILE_SERVICE.equalsIgnoreCase(gattService.getUuid().toString())){
                isCommonProfileFound = true;
            }
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                int property = gattCharacteristic.getProperties();
                if((property & BluetoothGattCharacteristic.PROPERTY_READ) == BluetoothGattCharacteristic.PROPERTY_READ) {
                    readCharacteristics.put(gattCharacteristic.getUuid().toString().toLowerCase(), gattCharacteristic);
                }
                if(
                        (property & BluetoothGattCharacteristic.PROPERTY_WRITE) == BluetoothGattCharacteristic.PROPERTY_WRITE
                     || (property & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
                {
                    writeCharacteristics.put(gattCharacteristic.getUuid().toString().toLowerCase(), gattCharacteristic);
                }
                if(
                        (property & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == BluetoothGattCharacteristic.PROPERTY_NOTIFY
                     || (property & BluetoothGattCharacteristic.PROPERTY_INDICATE) == BluetoothGattCharacteristic.PROPERTY_INDICATE
                ) {
                    notifyCharacteristics.put(gattCharacteristic.getUuid().toString().toLowerCase(), gattCharacteristic);
                }

            }
        }
    }

    volatile boolean mtuWaitEnd = false;
    void waitMtu(){
        mtuWaitEnd = false;
        int n = 0;
        while (!mtuWaitEnd && n < 1000 ) { // 1 sec . step 1ms
            sleep(1);
            n++;
        }
    }

    volatile private boolean isReadDone = false;
    private void waitRead(){
        isReadDone = false;
        int n = 0;
        while (!isReadDone && n < 400 ) { // 2 sec . step 5 ms
            sleep(5);
            n++;
        }
    }

    private void onRead(BluetoothGattCharacteristic characteristic){
        isReadDone = true;


        // разбираем

        if (UUID_DEVICE_NAME.equals(characteristic.getUuid().toString())) {
            deviceName = str_from_bytes(characteristic.getValue());
        }

        // отдаем драйверу принтера
        int len = characteristic.getValue().length;
        if(len>0) {
            final byte[] value = new byte[len];
            System.arraycopy(
                    characteristic.getValue(),
                    0, value, 0,
                    len);
            driver.receiveFromDevice(value);
        }

    }

    // =====================================


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt,status,newState);
            Log.e("BLE TRANSPORT","onConnectionStateChange "+newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // driver.transportMessage("onConnectionStateChange STATE_CONNECTED");
                isConnect = true;
                driver.onTransportConnect();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //  нужно закрыть гат
                disconnect();
                driver.onTransportDisconnect();
                // driver.transportMessage("onConnectionStateChange STATE_DISCONNECTED");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // driver.transportMessage("onServicesDiscovered SUCCESS");
                services = mBluetoothGatt.getServices();
                fillCharacteristic();
            }else{
                driver.transportMessage("onServicesDiscovered FAIL");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if(status ==  BluetoothGatt.GATT_SUCCESS ) {
                onRead(characteristic);
            }else{
                driver.transportMessage("onCharacteristicRead FAIL");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            onRead(characteristic);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if(status ==  BluetoothGatt.GATT_SUCCESS ) {
                driver.transportMessage("onMtuChanged "+mtu);
                BUF_SIZE = mtu-3;
            }else{
                driver.transportMessage("onMtuChanged FAIL");
            }
            mtuWaitEnd = true;
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.e("BLE","onCharacteristicWrite "+ status);
        }
    };



    // =======================================

    private String str_from_bytes(byte[] b){
        try {
            return new String(b, Charset.defaultCharset());
        }catch (Exception ignored){}
        return  null;
    }

    private void sleep(int s){
        try {
            Thread.sleep(s);
        } catch (InterruptedException ignored) {
        }
    }
}
