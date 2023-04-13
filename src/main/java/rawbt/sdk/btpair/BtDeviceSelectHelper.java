package rawbt.sdk.btpair;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

import qrbt.sdk.R;


public class BtDeviceSelectHelper {
    // =================================================
    // HELPERS
    // =================================================

    static public void openBtSettings(Context ctx){
        Intent intentOpenBluetoothSettings = new Intent();
        intentOpenBluetoothSettings.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        ctx.startActivity(Intent.createChooser(intentOpenBluetoothSettings,ctx.getResources().getString(R.string.open_bt_settings)));
    }

    static public boolean checkSearchAvailability(Context ctx){
        LocationManager lm = (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);
        boolean network_enabled = false;

        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch(Exception ex) {
            ex.printStackTrace();
        }

        boolean gps_enabled = false;

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return network_enabled || gps_enabled;
    }

    public static String fixDevName(String devName) {
        if (devName == null) {
            devName = "unknown";
        }
        if (devName.endsWith("=")) {
            try {
                devName = new String(Base64.decode(devName, Base64.DEFAULT), StandardCharsets.UTF_8);
            } catch (Exception e) {
                devName = "Name in Base64";
            }
        }

        return devName;
    }

}
