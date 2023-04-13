package rawbt.sdk.btpair;

import static android.view.View.GONE;

import static rawbt.sdk.btpair.BtDeviceSelectHelper.checkSearchAvailability;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import qrbt.sdk.R;


public abstract class BtPairActivity extends AppCompatActivity {

    public int getImgId() {
        return R.drawable.bt_device;
    }

    // Member fields
    private BluetoothAdapter mBtAdapter;
    Context ctx;

    private Button scanButton;
    private Button permButton;
    private ProgressBar progressBar;
    private TextView hint ;

    private Timer mTimer;
    private final int WaitBeforeReScan = 15;
    // =============== activity life  ===========

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctx = this;
        // Setup the window
        setContentView(R.layout.device_list);
        setTitle(R.string.select_device);

        // add back arrow to toolbar
        findViewById(R.id.topAppBar).setOnClickListener(v -> myclose());

        // add back arrow to toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (mBtAdapter == null) {
            showError(getResources().getString(R.string.error_bluetooth_not_available));
            myclose();
        }

        progressBar = findViewById(R.id.progressBar);
        ((ImageView)findViewById(R.id.btDeviceImg)).setImageResource(getImgId());


        hint = findViewById(R.id.txt_hint);


        // Initialize the button to perform device discovery
        scanButton = findViewById(R.id.button_scan);
        scanButton.setOnClickListener(v -> {

            if(!mBtAdapter.isEnabled()){
                refresh();
                return;
            }
            if(!checkSearchAvailability(ctx)){
                refresh();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                doComplain();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        hint.setText(R.string.near_location);
                        hint.setVisibility(View.VISIBLE);
                        permButton.setVisibility(View.VISIBLE);
                        scanButton.setVisibility(GONE);
                        return;
                    }
                }
                runBtScanActivity.launch(BtScanActivity.getRunIntent(ctx,getCurrentMac()));
            }
        });

        permButton = findViewById(R.id.button_grant);
        permButton.setOnClickListener(v -> {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mRequestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
            }else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mRequestPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
            }

        });



    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        progressBar.setVisibility(GONE);
        complainWait = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mTimer != null){
            mTimer.cancel();
        }
    }


    @SuppressLint("NewApi")
    private void refresh(){
        progressBar.setVisibility(GONE);
        hint.setVisibility(GONE);
        scanButton.setVisibility(View.VISIBLE);
        permButton.setVisibility(GONE);

        // флаг что не упадет из-за пермишина
        boolean isNotCrashed = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ) {
                isNotCrashed = false;
            }
        }
        if(isNotCrashed) {

            try {
                if (!mBtAdapter.isEnabled()) {
                    if(isBtOnDismiss){
                        hint.setText(R.string.bluetooth_must_be_on);
                        hint.setVisibility(View.VISIBLE);
                        scanButton.setVisibility(GONE);
                    }else {
                        btOnLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                        isBtOnDismiss = true;
                    }
                } else {

                    if (!checkSearchAvailability(ctx)) {

                        hint.setText(R.string.hint_location_service_is_off);
                        hint.setVisibility(View.VISIBLE);
                        scanButton.setVisibility(GONE);

                        if (mTimer != null) {
                            mTimer.cancel();
                            mTimer = null;
                        }
                        mTimer = new Timer();
                        mTimer.schedule(new CheckLocationOnTask(), 1000,500);

                    }

                }

            } catch (NullPointerException e) {
                showError(getResources().getString(R.string.error_bluetooth_not_available));
                myclose();
            }
        }else{
            hint.setText(R.string.faq_BT_12);
            hint.setVisibility(View.VISIBLE);
            permButton.setVisibility(View.VISIBLE);
            scanButton.setVisibility(GONE);
        }
    }


    // ===========================================================
    //  PERMISSION
    // ===========================================================

    private final ActivityResultLauncher<String> mRequestPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> refresh());




    // ==================================================
    //  BT ON
    // ==================================================
    private boolean isBtOnDismiss = false;
    private final ActivityResultLauncher<Intent> btOnLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    scanButton.setVisibility(View.VISIBLE);
                }
            });


    // ==================================================
    //  RESULT
    // ==================================================


    abstract protected void done(BluetoothDevice dev);

    abstract protected void myclose();

    // ===================================================
    // SCAN
    // ===================================================
    private final ActivityResultLauncher<Intent> runBtScanActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // There are no request codes
                    Intent data = result.getData();
                    if (data != null) {
                        Bundle extra = data.getExtras();
                        if(extra != null){
                            BluetoothDevice dev = extra.getParcelable(BtScanActivity.RESULT_OK_EXTRA);
                            done(dev);
                        }
                    }

                }else{
                    waitSec = WaitBeforeReScan;
                    mTimer = new Timer();
                    mTimer.schedule(new WaitBeforeScanTask(), 100,1000);
                }
            });

    // ===================================================
    // COMPANION
    // ===================================================

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void doComplain(){
        CompanionDeviceManager deviceManager = getSystemService(CompanionDeviceManager.class);

        List<String> mInitialAssociations = deviceManager.getAssociations();
        String curMac = getCurrentMac();
        for(String mac: mInitialAssociations) {
            if(!curMac.equals(mac)) {
                deviceManager.disassociate(mac);
            }
        }

        // To skip filtering based on name and supported feature flags (UUIDs),
        // don't include calls to setNamePattern() and addServiceUuid(),
        // respectively. This example uses Bluetooth.
        BluetoothDeviceFilter deviceFilter = new BluetoothDeviceFilter.Builder().build();

        // The argument provided in setSingleDevice() determines whether a single
        // device name or a list of device names is presented to the user as
        // pairing options.
        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(false)
                .build();

        scanButton.setVisibility(GONE);
        progressBar.setVisibility(View.VISIBLE);
        complainWait = true;


        // When the app tries to pair with the Bluetooth device, show the
        // appropriate pairing request dialog to the user.
        deviceManager.associate(pairingRequest,
                new CompanionDeviceManager.Callback() {
                    @Override
                    public void onDeviceFound(IntentSender chooserLauncher) {

                        IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(chooserLauncher)
                                .build();
                        btCompanionLauncher.launch(intentSenderRequest);

                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        if (mTimer != null) {
                            mTimer.cancel();
                            mTimer = null;
                        }
                        waitSec = WaitBeforeReScan;
                        mTimer = new Timer();
                        mTimer.schedule(new WaitBeforeScanTask(), 100,1000);
                    }
                }, null);

        // так как нет метода узнать запустился ли диалог , то буду проверять
        // по таймеру
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        mTimer = new Timer();
        mTimer.schedule(new CheckComplainDialog(), 10000);
    }

    private final ActivityResultLauncher<IntentSenderRequest> btCompanionLauncher =
            registerForActivityResult(new ActivityResultContracts
                    .StartIntentSenderForResult(), new ActivityResultCallback<ActivityResult>() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void onActivityResult(ActivityResult result) {
                    scanButton.setVisibility(View.VISIBLE);
                    try {
                        BluetoothDevice device;
                        device = Objects.requireNonNull(result.getData()).getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
                        if (device != null) {
//                    device.createBond();
                            done(device);
                        }
                    }catch (Exception ignored){}
                }

            });


    // ==========================================================
    // TIMER TASK
    // ==========================================================


    private boolean complainWait = false;
    // Задача для таймера. Проверка запуска комплайн диалога блютуз
    private class CheckComplainDialog extends TimerTask {

        @Override
        public void run() {
            runOnUiThread(() -> {
                scanButton.setVisibility(View.VISIBLE);
                progressBar.setVisibility(GONE);
                if (complainWait) {
                    complainWait = false;
                }
            });
        }

    }

    private class CheckLocationOnTask extends TimerTask {
        @Override
        public void run() {
            if(checkSearchAvailability(ctx)){
                runOnUiThread(() -> {
                    mTimer.cancel();
                    mTimer = null;
                    refresh();
                });
            }
        }
    }

    private int waitSec = 0;

    private class WaitBeforeScanTask extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(() -> {
                if(waitSec<1) {
                    mTimer.cancel();
                    mTimer = null;
                    scanButton.setText(R.string.button_scan);
                    scanButton.setEnabled(true);
                }else{
                    scanButton.setText(String.valueOf(waitSec));
                    scanButton.setEnabled(false);
                }
                waitSec --;
            });

        }
    }

    abstract protected void showError(String error);
    protected abstract String getCurrentMac();
}

