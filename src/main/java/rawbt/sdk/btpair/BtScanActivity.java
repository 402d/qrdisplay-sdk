package rawbt.sdk.btpair;

import static android.view.View.GONE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import androidx.annotation.RequiresPermission;

import qrbt.sdk.R;


public class BtScanActivity extends Activity {

    public static Intent getRunIntent(Context ctx,String cur_mac){
        Intent runScan = new Intent(ctx,BtScanActivity.class);
        runScan.putExtra(BtScanActivity.PARAMETER_CUR_MAC,cur_mac);
        return runScan;
    }

    // optional argument
    public static final String PARAMETER_CUR_MAC = "cur_mac";

    // RESULT_OK, RESULT_CANCELED and users defined :
    public static final int RESULT_BLUETOOTH_NONE = 20;
    public static final int RESULT_BLUETOOTH_NOT_ENABLED = 21;
    public static final int RESULT_PERMISSION_LOCATION_NOT_GRANTED = 22;
    public static final int RESULT_PERMISSION_BLUETOOTH_SCAN_NOT_GRANTED = 23;
    public static final int RESULT_LOCATION_OFF = 24;

    // Result Ok extra
    public static final String RESULT_OK_EXTRA = "device";

    // -------------------------------------------------------------------------
    private ProgressBar progressBar;
    private BluetoothAdapter mBtAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the window
        setContentView(R.layout.btscan_activity);
        this.setFinishOnTouchOutside(false);

        progressBar = findViewById(R.id.progressBar);

        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBtAdapter == null){
            setResult(RESULT_BLUETOOTH_NONE);
            finish();
        }

        IntentFilter filter = new IntentFilter();
        // Register for broadcasts when a device is discovered
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        // Register for broadcasts when discovery has finished
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        if(!mBtAdapter.isEnabled()){
            setResult(RESULT_BLUETOOTH_NOT_ENABLED);
            finish();
        }

        if(!BtDeviceSelectHelper.checkSearchAvailability(this)){
            setResult(RESULT_LOCATION_OFF);
            finish();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // android 8.0 or above
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    setResult(RESULT_PERMISSION_LOCATION_NOT_GRANTED);
                    finish();
                }
            }else{
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    setResult(RESULT_PERMISSION_LOCATION_NOT_GRANTED);
                    finish();
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    setResult(RESULT_PERMISSION_BLUETOOTH_SCAN_NOT_GRANTED);
                    finish();
                }
            }
        }

        String cur_mac = "00:00:00:00:00";
        Intent intent = getIntent();
        if(intent != null){
            Bundle extra = intent.getExtras();
            if(extra != null){
                cur_mac = extra.getString(PARAMETER_CUR_MAC,"00:00:00:00:00");
            }
        }

        // Find and set up the ListView for newly discovered devices
        mNewDevicesArrayAdapter = new BtDevAdapter(this, cur_mac);
        ListView newDevicesListView = findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);



        findViewById(R.id.btnCancel).setOnClickListener((v)-> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }


    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();
        if (!mBtAdapter.isDiscovering()) {
            mBtAdapter.startDiscovery();
        }
        progressBar.setVisibility(View.VISIBLE);
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onPause() {
        super.onPause();
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }
    }

    @SuppressLint("MissingPermission")
    protected void onDestroy() {
        super.onDestroy();
        // Make sure we're not doing discovery anymore
        if (mBtAdapter != null) {
            if(mBtAdapter.isDiscovering()){
                mBtAdapter.cancelDiscovery();
            }
            this.unregisterReceiver(mReceiver);
        }
    }

    // ===================================================
    // SCAN
    // ===================================================

    private BtDevAdapter mNewDevicesArrayAdapter;

    // The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        @SuppressLint("InlinedApi")
        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null ) {
                    String find = device.getAddress();
                    boolean present = false;
                    for (int i = 0; i < mNewDevicesArrayAdapter.getCount(); i++) {
                        String val = mNewDevicesArrayAdapter.getAddress(i);
                        if (find.equals(val)) {
                            present = true;
                        }
                    }
                    if (!present) {
                        mNewDevicesArrayAdapter.add(device);
                        mNewDevicesArrayAdapter.notifyDataSetChanged();
                    }
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                progressBar.setVisibility(GONE);
            }
        }
    };

    // The on-click listener for all devices in the ListViews
    private final AdapterView.OnItemClickListener mDeviceClickListener = (av, v, position, id) -> {
        BluetoothDevice dev = (BluetoothDevice) av.getItemAtPosition(position);
        Intent intent = new Intent();
        intent.putExtra( RESULT_OK_EXTRA, dev);
        setResult(RESULT_OK,intent);
        finish();
    };

}
