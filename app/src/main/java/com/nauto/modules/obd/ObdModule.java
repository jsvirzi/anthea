package com.nauto.modules.obd;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * Created by jsvirzi on 7/9/16.
 */

/*
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 * http://blog.lemberg.co.uk/how-guide-obdii-reader-app-development
 * https://github.com/pires/android-obd-reader
 * https://en.wikipedia.org/wiki/OBD-II_PIDs
 * http://stackoverflow.com/questions/15117475/android-elm327-obd2-protocol
 */
public class ObdModule {

    private static final String TAG = "ObdModule";
    Context context;
    String deviceAddress;
    final String serialUUID = "00001101-0000-1000-8000-00805F9B34FB";
    HandlerThread thread;
    Handler handler;

    public ObdModule(Context context) {
        this.context = context;
        thread = new HandlerThread(TAG);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    private Runnable connectRunnable = new Runnable() {
        @Override
        public void run() {
            ArrayList deviceStrs = new ArrayList();
            final ArrayList<String> devices = new ArrayList<>();

            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    deviceStrs.add(device.getName() + "\n" + device.getAddress());
                    devices.add(device.getAddress());
                }
            }

            // show list
//        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
//
//        ArrayAdapter adapter = new ArrayAdapter(context, android.R.layout.select_dialog_singlechoice,
//                deviceStrs.toArray(new String[deviceStrs.size()]));
//
//        alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
//                int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
//                deviceAddress = devices.get(position);
//                 TODO save deviceAddress
//            }
//        });
//
//        alertDialog.setTitle("Choose Bluetooth device");
//        alertDialog.show();

            deviceAddress = devices.get(0);
            BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);
            UUID uuid = UUID.fromString(serialUUID);
            BluetoothSocket socket = null;
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
//                socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(device, 2);
                socket.connect();
            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(device, 1);
                    socket.connect();
                } catch (Exception ex2) {
                    ex2.printStackTrace();
                    Log.e(TAG, "unable to establish connection");
                    return;
                }
            }
            try {
                InputStream inputStream = socket.getInputStream();
                OutputStream outputStream = socket.getOutputStream();
                String msg;
                msg = "ATZ";
                outputStream.write(msg.getBytes());
//                msg = "AT E0";
//                outputStream.write(msg.getBytes());
                byte[] bytes = new byte[384];
                int n = inputStream.read(bytes);
                if (n > 0) {
                    msg = String.format("read %d bytes = %s", n, bytes.toString());
                } else {
                    msg = "no message for you. nobody likes you";
                }
                Log.d(TAG, msg);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    };

    public void connect() {
        handler.post(connectRunnable);
    }

}
