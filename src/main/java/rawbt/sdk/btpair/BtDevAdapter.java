package rawbt.sdk.btpair;

import static rawbt.sdk.btpair.BtDeviceSelectHelper.fixDevName;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.List;

import qrbt.sdk.R;


public class BtDevAdapter extends BaseAdapter {
    private List<BluetoothDevice> devices;
    private final LayoutInflater lInflater;
    private final String curMac;

    @SuppressLint("InlinedApi")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public BtDevAdapter(Context context, String curMac) {
        this.devices = new ArrayList<>();
        lInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.curMac = curMac;
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public BluetoothDevice getItem(int position) {
        return devices.get(position);
    }


    public void setList(List<BluetoothDevice> dirs) {
        this.devices = dirs;
    }

    public void add(BluetoothDevice add) {
        this.devices.add(add);
    }

    public void clear() {
        this.devices.clear();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public String getAddress(int position) {
        return devices.get(position).getAddress();
    }


    @Override
    @SuppressLint("InlinedApi")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = lInflater.inflate(R.layout.btdev_item, parent, false);
        }
        BluetoothDevice d = getItem(position);
        if(d == null){
            return null;
        }


        TextView nameView = view.findViewById(R.id.bt_name);
        String devName = fixDevName(d.getName());

        nameView.setText(devName);

        TextView pathView = view.findViewById(R.id.bt_mac);
        String mac = d.getAddress();
        pathView.setText(mac);
        ImageView classIcon = view.findViewById(R.id.bt_class);

        if (d.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.IMAGING
                && ((d.getBluetoothClass().getDeviceClass() & 0x70) == 0) // x70 display,camera,scanner
        ) {

            nameView.setTextColor(view.getResources().getColor(R.color.color_important));
            pathView.setTextColor(view.getResources().getColor(R.color.color_important));
            classIcon.setImageResource(R.drawable.class_printer);

        } else {
            nameView.setTextColor(view.getResources().getColor(R.color.gray));
            pathView.setTextColor(view.getResources().getColor(R.color.gray));
            classIcon.setImageResource(R.drawable.class_unknown);
        }

        TextView typeView = view.findViewById(R.id.bt_type);
        switch (d.getType()) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                typeView.setText(R.string.bt_spp);
                break;
            case BluetoothDevice.DEVICE_TYPE_LE:
                typeView.setText(R.string.bt_ble);
                break;
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                typeView.setText(R.string.bt_dual);
                break;
            default:
                typeView.setText(R.string.bt_unknown);
        }

        if (mac.equals(curMac)) {
            nameView.setTextColor(view.getResources().getColor(R.color.colorAccent));
        }


        return view;
    }
}
