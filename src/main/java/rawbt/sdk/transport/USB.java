package rawbt.sdk.transport;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;





public class USB implements Transport {
    CallBack driver = null;
    @Override
    public void injectDriver(CallBack driver) {
        this.driver = driver;
    }


    private int _vendorId;
    private int _productId;


    private boolean isConnect = false;

    @Override
    public boolean isConnected() {
        return isConnect;
    }

    public void setConnectParam(String vendor, String product) {
        _vendorId = Integer.parseInt(vendor);
        _productId = Integer.parseInt(product);
    }



    @Override
    public String write(byte[] data) {
        String str = "ok";
        try {
            if (!isConnect) {
                String st = _connect();
                if (!"ok".equals(st)) {
                    return st;
                }
            }

            port.setDTR(false);
            port.setRTS(true);
            port.write(data,5000);

            if (!isConnect) {
                disconnect();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return e.getLocalizedMessage();
        }
        return str;

    }

    UsbSerialPort port = null;

    private String _connect() {
        isConnect = false;
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) driver.getContext().getSystemService(Context.USB_SERVICE);

        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(_vendorId, _productId, CdcAcmSerialDriver.class);

        UsbSerialProber prober = new UsbSerialProber(customTable);
        List<UsbSerialDriver> availableDrivers = prober.findAllDrivers(manager);

        if (availableDrivers.isEmpty()) {
            return "not connected";
        }

        // Open a connection to the first available driver.
        UsbSerialDriver serialDriver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(serialDriver.getDevice());
        if (connection == null) {
            // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            return "error";
        }


        try {
            port = serialDriver.getPorts().get(0); // Most devices have just one port (port 0)
            port.open(connection);
            // port.setParameters(57600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            if(port.isOpen()){
                Log.w("aaa","port is open");
                // reading thread start
                StartReadingThread();

                isConnect = true;
                driver.onTransportConnect();
                return "ok";
            }
            return "port not open";
        } catch (IOException e) {
            e.printStackTrace();
            return e.getLocalizedMessage();
        }
    }

    public String connect() {
        return _connect();
    }

    public void disconnect() {
        isConnect = false;
        if (port != null) {
            try {
                port.close();
            } catch (IOException ignored) {}
        }
        driver.onTransportDisconnect();
    }

    // ==============================================
    /**
     * Starts the thread that continuously reads the data from the device.
     * Should be called in order to be able to talk with the device.
     */
    public void StartReadingThread() {
        if (_readingThread == null) {
            isReadCancel = false;
            _readingThread = new Thread(readerReceiver);
            _readingThread.start();
        }
    }
    // Locker object that is responsible for locking read/write thread.
    private final Object _locker = new Object();
    private Thread _readingThread = null;
    boolean isReadCancel = true;
    // The thread that continuously receives data from the dongle and put it to the queue.
    private final Runnable readerReceiver = () -> {
        if (port == null) {
            return;
        }
        try {
            while (!isReadCancel) {
                // Lock that is common for read/write methods.
                synchronized (_locker) {
                    byte[] read = new byte[1024];
                    int len = port.read(read,50);
                    if (len > 0) {
                        byte[] truncatedBytes = new byte[len]; // Truncate bytes
                        System.arraycopy(read, 0, truncatedBytes, 0, len);
                        // Log.i("BT_INPUT",bytesToHexFormatted(truncatedBytes,len));
                        driver.receiveFromDevice(truncatedBytes);
                    }
                }
                // Sleep for 10 ms to pause, so other thread can write data or anything.
                // As both read and write data methods lock each other - they cannot be run in parallel.
                // Looks like Android is not so smart in planning the threads, so we need to give it a small time
                // to switch the thread context.
                Sleep(10);
            }

        }catch (Exception e){
            e.printStackTrace();
            isReadCancel = true;
        }
    };

    private void Sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
