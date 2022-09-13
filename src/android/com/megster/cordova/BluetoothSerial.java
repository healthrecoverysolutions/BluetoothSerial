package com.megster.cordova;

import android.Manifest;
import android.content.pm.PackageManager;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import java.util.Set;

/**
 * PhoneGap Plugin for Serial Communication over Bluetooth
 */
public class BluetoothSerial extends CordovaPlugin {

    // actions
    private static final String LIST = "list";
    private static final String CONNECT = "connect";
    private static final String CONNECT_INSECURE = "connectInsecure";
    private static final String DISCONNECT = "disconnect";
    private static final String WRITE = "write";
    private static final String AVAILABLE = "available";
    private static final String READ = "read";
    private static final String READ_UNTIL = "readUntil";
    private static final String SUBSCRIBE = "subscribe";
    private static final String UNSUBSCRIBE = "unsubscribe";
    private static final String SUBSCRIBE_RAW = "subscribeRaw";
    private static final String UNSUBSCRIBE_RAW = "unsubscribeRaw";
    private static final String IS_ENABLED = "isEnabled";
    private static final String IS_CONNECTED = "isConnected";
    private static final String CLEAR = "clear";
    private static final String SETTINGS = "showBluetoothSettings";
    private static final String ENABLE = "enable";
    private static final String DISCOVER_UNPAIRED = "discoverUnpaired";
    private static final String SET_DEVICE_DISCOVERED_LISTENER = "setDeviceDiscoveredListener";
    private static final String CLEAR_DEVICE_DISCOVERED_LISTENER = "clearDeviceDiscoveredListener";
    private static final String SET_NAME = "setName";
    private static final String SET_DISCOVERABLE = "setDiscoverable";

    // callbacks
    private CallbackContext connectCallback;
    private CallbackContext dataAvailableCallback;
    private CallbackContext rawDataAvailableCallback;
    private CallbackContext enableBluetoothCallback;
    private CallbackContext deviceDiscoveredCallback;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSerialService bluetoothSerialService;

    // Debugging
    private static final String TAG = "BluetoothSerial";
    private static final boolean D = true;

    // Message types sent from the BluetoothSerialService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_READ_RAW = 6;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    StringBuffer buffer = new StringBuffer();
    private String delimiter;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;

    // Android 23 requires user to explicitly grant permission for location to discover unpaired
    private static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int CHECK_PERMISSIONS_REQ_CODE = 2;
    private CallbackContext permissionCallback;

    // Android 31 permissions
    private static final String BLUETOOTH_SCAN = Manifest.permission.BLUETOOTH_SCAN;
    private static final String BLUETOOTH_CONNECT = Manifest.permission.BLUETOOTH_CONNECT;

    // Android 29 and 30 permission
    private static final String ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;

    //good link
    //https://stackoverflow.com/questions/4715865/how-can-i-programmatically-tell-if-a-bluetooth-device-is-connected
    //https://stackoverflow.com/questions/13626277/how-to-detect-that-an-already-discovered-and-paired-device-is-available

//Device discovery will only find remote devices that are currently discoverable (inquiry scan enabled). Many Bluetooth devices are not discoverable by default, and need to be entered into a special mode.
//https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#startDiscovery()

    //TRY THIS  >> bond state change one
    //https://stackoverflow.com/questions/35239880/find-all-bluetooth-devices-headsets-phones-etc-nearby-without-forcing-the-de

    BluetoothBroadcastReceiver aclConnectEventReceiver;

    public void registerStateChangeReceiver(Context context) {
        if(aclConnectEventReceiver == null) {
            //nehal
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            aclConnectEventReceiver = new BluetoothBroadcastReceiver();
            context.registerReceiver(aclConnectEventReceiver, filter);
        }
    }

    /**
     * Broadcast receiver listening to bluetooth connection and pairing actions
     */
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                Bundle bundle = intent.getExtras();

                /* Logging of actions received for debugging purpose */
                if (BluetoothDevice.ACTION_FOUND.equals(action) && bundle != null) {
                    for (String key : bundle.keySet()) {
                        Log.d(TAG, String.format("======%s :  %s--%s", action, key,
                            bundle.get(key) != null ? Objects.requireNonNull(bundle.get(key)).toString() : "null"));
                    }
                }

                if (action == null) return;

                switch (action) {
//                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
//                        Log.d(TAG, "ACTION_BOND_STATE_CHANGED");
//                        onPairingStateChanged(Objects.requireNonNull(bundle), intent);
//                        break;


                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        if (intent != null) {
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            if (device != null) {
                                Log.d("AB", "Device CONNECTED ACL  " + device.getName());
                            }
                        }
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        //NEHAL FINDINGS :
                        // Even if the device is transmitting the readings and is ON, then also this event gets notfication at a very early time
                        // when we turn on a device, state is not changing from isConnected to CONNECTING..
                        if (intent != null) {
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            if (device != null) {
                                Log.d("AB", "Device Disconnected ACL  " + device.getName());
                                Log.d("AB", "BT service instance " + bluetoothSerialService);
                                Log.d("AB", "## Device " + device.getName() + " ACL Disconnected --empty the queue to start fresh");
                                bluetoothSerialService.setStateNone();
                                if (queuedClassicDevices != null) {
                                    queuedClassicDevices.clear();


                                }
                            }
                        }
                    default:
                        break;
                }
            } catch (Exception e) {
                Log.d(TAG,
                    "Exception onReceive in Device paired with the sensor" + e);
            }
        }
    }

     @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView){
         Log.d("AB", "Initialzie PLUGON & QUEUE");

    }

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "action = " + action);

        if (bluetoothAdapter == null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (bluetoothSerialService == null) {
            bluetoothSerialService = new BluetoothSerialService(mHandler);
            registerStateChangeReceiver(cordova.getContext());
        }
//        else {
//            if( !bluetoothAdapter.isDiscovering()) {
//                bluetoothAdapter.startDiscovery();
//            }
//        }

        boolean validAction = true;

        if (action.equals(LIST)) {

            listBondedDevices(callbackContext);

        } else if (action.equals(CONNECT)) {

            boolean secure = true;
            connect(args, secure, callbackContext);

        } else if (action.equals(CONNECT_INSECURE)) {

            // see Android docs about Insecure RFCOMM http://goo.gl/1mFjZY
            boolean secure = false;
            connect(args, secure, callbackContext);

        } else if (action.equals(DISCONNECT)) {

            connectCallback = null;
            bluetoothSerialService.stop();
            callbackContext.success();

        } else if (action.equals(WRITE)) {

            byte[] data = args.getArrayBuffer(0);
            bluetoothSerialService.write(data);
            callbackContext.success();

        } else if (action.equals(AVAILABLE)) {

            callbackContext.success(available());

        } else if (action.equals(READ)) {

            callbackContext.success(read());

        } else if (action.equals(READ_UNTIL)) {

            String interesting = args.getString(0);
            callbackContext.success(readUntil(interesting));

        } else if (action.equals(SUBSCRIBE)) {

            delimiter = args.getString(0);
            dataAvailableCallback = callbackContext;

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else if (action.equals(UNSUBSCRIBE)) {

            delimiter = null;

            // send no result, so Cordova won't hold onto the data available callback anymore
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            dataAvailableCallback.sendPluginResult(result);
            dataAvailableCallback = null;

            callbackContext.success();

        } else if (action.equals(SUBSCRIBE_RAW)) {
            rawDataAvailableCallback = callbackContext;

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else if (action.equals(UNSUBSCRIBE_RAW)) {

            rawDataAvailableCallback = null;

            callbackContext.success();

        } else if (action.equals(IS_ENABLED)) {

            if (bluetoothAdapter.isEnabled()) {
                callbackContext.success();
            } else {
                callbackContext.error("Bluetooth is disabled.");
            }

        } else if (action.equals(IS_CONNECTED)) {

            callbackContext.error("Not connected.");

//            if (bluetoothSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
//                callbackContext.success();
//            } else {
//                callbackContext.error("Not connected.");
//            }

        } else if (action.equals(CLEAR)) {

            buffer.setLength(0);
            callbackContext.success();

        } else if (action.equals(SETTINGS)) {

            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            cordova.getActivity().startActivity(intent);
            callbackContext.success();

        } else if (action.equals(ENABLE)) {

            enableBluetoothCallback = callbackContext;
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            cordova.startActivityForResult(this, intent, REQUEST_ENABLE_BLUETOOTH);

        } else if (action.equals(DISCOVER_UNPAIRED)) {
          //  this.deviceDiscoveredCallback = null;
            if (hasBluetoothPermissions()) {
                discoverUnpairedDevices(callbackContext);
            } else {
                permissionCallback = callbackContext;
                requestPermissions();
            }

        } else if (action.equals(SET_DEVICE_DISCOVERED_LISTENER)) {

            this.deviceDiscoveredCallback = callbackContext;

        } else if (action.equals(CLEAR_DEVICE_DISCOVERED_LISTENER)) {

            this.deviceDiscoveredCallback = null;

        } else if (action.equals(SET_NAME)) {

            String newName = args.getString(0);
            bluetoothAdapter.setName(newName);
            callbackContext.success();

        } else if (action.equals(SET_DISCOVERABLE)) {

            int discoverableDuration = args.getInt(0);
            Intent discoverIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableDuration);
            cordova.getActivity().startActivity(discoverIntent);

        } else {
            validAction = false;

        }

        return validAction;
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) { // for android 12 check for Nearby devices permission
            return cordova.hasPermission(BLUETOOTH_SCAN) && cordova.hasPermission(BLUETOOTH_CONNECT);
        } else if (Build.VERSION.SDK_INT == 29 || Build.VERSION.SDK_INT == 30) {
            return cordova.hasPermission(ACCESS_FINE_LOCATION);
        } else {
            return cordova.hasPermission(ACCESS_COARSE_LOCATION);
        }
    }

    private void requestPermissions() {
        //Android 12 (API 31) and higher
        // Users MUST accept BLUETOOTH_SCAN and BLUETOOTH_CONNECT [nearby devices]
        // Android 10 (API 29) up to Android 11 (API 30)
        // Users MUST accept ACCESS_FINE_LOCATION
        // Users may accept or reject ACCESS_BACKGROUND_LOCATION
        // Android 9 (API 28) and lower
        // Users MUST accept ACCESS_COARSE_LOCATION

        if (Build.VERSION.SDK_INT >= 31) {
            cordova.requestPermissions(this, CHECK_PERMISSIONS_REQ_CODE, new String[]{BLUETOOTH_SCAN, BLUETOOTH_CONNECT});
        } else if (Build.VERSION.SDK_INT == 29 || Build.VERSION.SDK_INT == 30) {
            cordova.requestPermission(this, CHECK_PERMISSIONS_REQ_CODE, ACCESS_FINE_LOCATION);
        } else {
            cordova.requestPermission(this, CHECK_PERMISSIONS_REQ_CODE, ACCESS_COARSE_LOCATION);
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {

            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "User enabled Bluetooth");
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.success();
                }
            } else {
                Log.d(TAG, "User did *NOT* enable Bluetooth");
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.error("User did not enable Bluetooth");
                }
            }

            enableBluetoothCallback = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothSerialService != null) {
            bluetoothSerialService.stop();
        }
        if(aclConnectEventReceiver!=null) {
            cordova.getActivity().unregisterReceiver(aclConnectEventReceiver);
            aclConnectEventReceiver = null;
        }
    }

    private void listBondedDevices(CallbackContext callbackContext) throws JSONException {
        JSONArray deviceList = new JSONArray();
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : bondedDevices) {
            deviceList.put(deviceToJSON(device));
        }
        callbackContext.success(deviceList);
    }



    private void discoverUnpairedDevices(final CallbackContext callbackContext) throws JSONException {

        final CallbackContext ddc = deviceDiscoveredCallback;


  final BroadcastReceiver discoverReceiver = new BroadcastReceiver() {

        private JSONArray unpairedDevices = new JSONArray();

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //   Log.d("AB", "**************** ACTION ******* " + action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d("AB", "@@@@ Action FOUND " + device.getName());
                try {
                    JSONObject o = deviceToJSON(device);
                    unpairedDevices.put(o);
                    if (ddc != null) {
                        PluginResult res = new PluginResult(PluginResult.Status.OK, o);
                        res.setKeepCallback(true);
                        ddc.sendPluginResult(res);
                    }
                } catch (JSONException e) {
                    // This shouldn't happen, Log and ignore
                    Log.e(TAG, "Problem converting device to JSON", e);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d("AB", "@@@@ FINISHED FOUND " + unpairedDevices.toString());
               callbackContext.success(unpairedDevices);
               cordova.getActivity().unregisterReceiver(this);
            }



            //  bluetoothSerialService.stop();
            }

    };


    Activity activity = cordova.getActivity();
//        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
//        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
//        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
//        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothDevice.ACTION_FOUND);

    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
    activity.registerReceiver(discoverReceiver, filter);

        bluetoothAdapter.startDiscovery();



    }

    private JSONObject deviceToJSON(BluetoothDevice device) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", device.getName());
        Log.d("AB", "************ CLASSIC DEVICE FOUND name " + device.getName());
        json.put("address", device.getAddress());
        Log.d("AB", "************ CLASSIC DEVICE FOUND  address" + device.getAddress());
        json.put("id", device.getAddress());
        if (device.getBluetoothClass() != null) {
            json.put("class", device.getBluetoothClass().getDeviceClass());
        }
        return json;
    }

  static Set<ClassicDevice> queuedClassicDevices = new HashSet<ClassicDevice>();

    class ClassicDevice {
        BluetoothDevice bluetoothDevice;
        boolean secure;
        CallbackContext callbackContext;

        public ClassicDevice(BluetoothDevice bluetoothDevice, boolean secure, CallbackContext callbackContext) {
            this.bluetoothDevice = bluetoothDevice;
            this.secure = secure;
            this.callbackContext = callbackContext;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                + ((bluetoothDevice.getAddress() == null) ? 0 : bluetoothDevice.getAddress().hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final ClassicDevice other = (ClassicDevice) obj;
            if (bluetoothDevice == null) {
                if (other.bluetoothDevice != null)
                    return false;
            } else if (!bluetoothDevice.getAddress().equals(other.bluetoothDevice.getAddress()))
                return false;
            return true;
        }

    }

    private void connect(CordovaArgs args, boolean secure, CallbackContext callbackContext) throws JSONException {
     if (Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
            if(!hasBluetoothPermissions()) {
                return;
            }
        }


        String macAddress = args.getString(0);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        Log.d("AB", "## Processing for device " + device.getName());
        if(queuedClassicDevices.isEmpty()) {
            Log.d("AB", "## QUEUE is empty thus connecting to the obtained device ## ");
            if (device != null) {
                queuedClassicDevices.add(new ClassicDevice(device, secure, callbackContext));
            }
               connectClassic(device, secure, callbackContext);
        } else {

            Log.d("AB", "## QUEUE is NOT ---- empty thus will ADD will wait until last device finishes good or bad ## ");
            if(device!=null) {
                    queuedClassicDevices.add(new ClassicDevice(device, secure, callbackContext));
                Log.d("AB", "## QUEUE SIZE ## " + queuedClassicDevices.size());
            }
        }
    }

    private void connectClassic(BluetoothDevice device, boolean secure, CallbackContext callbackContext) {
        Log.d("AB", "## Connect to classic " + device.getName());
        if (device != null) {

            connectCallback = callbackContext;
            bluetoothSerialService.start(device);
            bluetoothSerialService.connect(device, secure);
            buffer.setLength(0);

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else {
            callbackContext.error("Could not connect to " + device.getName());
        }
    }

    // The Handler that gets information back from the BluetoothSerialService
    // Original code used handler for the because it was talking to the UI.
    // Consider replacing with normal callbacks
    private final Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    String bundle = msg.obj.toString();
                    Log.d(TAG, bundle);

                    if (dataAvailableCallback != null) {
                        sendDataToSubscriber(bundle);
                    }

                    break;
                case MESSAGE_READ_RAW:
                    if (rawDataAvailableCallback != null) {
                        byte[] bytes = (byte[]) msg.obj;
                        sendRawDataToSubscriber(bytes);
                    }
                    break;
                case MESSAGE_STATE_CHANGE:

                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothSerialService.STATE_CONNECTED:
                            Log.i(TAG, "BluetoothSerialService.STATE_CONNECTED");
                            notifyConnectionSuccess();
                            break;
                        case BluetoothSerialService.STATE_CONNECTING:
                            Log.i(TAG, "BluetoothSerialService.STATE_CONNECTING");
                            break;
                        case BluetoothSerialService.STATE_LISTEN:
                            Log.i(TAG, "BluetoothSerialService.STATE_LISTEN");
                            break;
                        case BluetoothSerialService.STATE_NONE:
                            Log.i(TAG, "BluetoothSerialService.STATE_NONE");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    Log.i(TAG, "Wrote: " + writeMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    Log.i(TAG, msg.getData().getString(DEVICE_NAME));
                    break;
                case MESSAGE_TOAST:
                    String message = msg.getData().getString(TOAST);
                    notifyConnectionLost(message);
                    break;
            }
        }
    };

    private void notifyConnectionLost(String error) {
        Log.d("AB", "## Notify Connection Lost ");
        if (connectCallback != null) {
            connectCallback.error(error);
            connectCallback = null;

        }
        processNextEnqueuedClassicDevice();
    }

    private void processNextEnqueuedClassicDevice() {
        if (bluetoothSerialService!=null && (bluetoothSerialService.getState() == BluetoothSerialService.STATE_CONNECTING
         /*|| bluetoothSerialService.getState() == BluetoothSerialService.STATE_CONNECTED*/)) {
            Log.d("AB", "## Attempting to process next enqueued device but annother oneis already in connecting state --- ");
/// Use case failing ::: if first device connects properly then this check is not allowing to connect the next device
            // try :: whenever NONE is done, send a notification so that this code executes
            // or try this check at start of connect method
            // make connect method or other block/method synchronized
        } else {
            Log.d("AB", "## Process next enqueued classic device --- ");
            if (!queuedClassicDevices.isEmpty()) {

                Iterator<ClassicDevice> iterator = queuedClassicDevices.iterator();
                if (iterator.hasNext()) {
                    ClassicDevice element = iterator.next();
                    Log.d("AB", "## Process next enqueued classic device --- " + element.bluetoothDevice.getName());
                    connectClassic(element.bluetoothDevice, element.secure, element.callbackContext);
                    iterator.remove();
                } else {
                    Log.d("AB", "## Not processing any element --- ");
                }

            }
        }
    }

    private void notifyConnectionSuccess() {
        if (connectCallback != null) {
//            PluginResult result = new PluginResult(PluginResult.Status.OK);
//            result.setKeepCallback(true);
//            connectCallback.sendPluginResult(result);

            BluetoothDevice connectedDevice;
            JSONObject o = null;
            PluginResult res;
            //nehal sending device object to which connection has been made
            if (bluetoothSerialService != null) {
                connectedDevice = bluetoothSerialService.getConnectedDevice();
            try {
                o = deviceToJSON(connectedDevice);
                res = new PluginResult(PluginResult.Status.OK, o);
            } catch (JSONException e) {
                e.printStackTrace();
                res = new PluginResult(PluginResult.Status.OK);
            }

            } else {
            res = new PluginResult(PluginResult.Status.OK);
            }

             res.setKeepCallback(true);
             connectCallback.sendPluginResult(res);
             // processNextEnqueuedClassicDevice();
        }

    }

    private void sendRawDataToSubscriber(byte[] data) {
        if (data != null && data.length > 0) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
            result.setKeepCallback(true);
            rawDataAvailableCallback.sendPluginResult(result);
        }
    }

    private void sendDataToSubscriber(String data) {
        if (data != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
            result.setKeepCallback(true);
            dataAvailableCallback.sendPluginResult(result);
        }
    }

    private int available() {
        return buffer.length();
    }

    private String read() {
        int length = buffer.length();
        String data = buffer.substring(0, length);
        buffer.delete(0, length);
        return data;
    }

    private String readUntil(String c) {
        String data = "";
        int index = buffer.indexOf(c, 0);
        if (index > -1) {
            data = buffer.substring(0, index + c.length());
            buffer.delete(0, index + c.length());
        }
        return data;
    }


    private  boolean verifyPermissions(int[] grantResults) {
        // At least one result must be checked.
        if(grantResults.length < 1){
            return false;
        }
        // Verify that each required permission has been granted, otherwise return false.
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {

        if (requestCode == CHECK_PERMISSIONS_REQ_CODE) {
            if (verifyPermissions(grantResults)) {
                discoverUnpairedDevices(permissionCallback);
                this.permissionCallback = null;
            } else {
                // permissions not granted, disable functionality or show message
                if (Build.VERSION.SDK_INT >= 31) {
                    Log.d(TAG, "User did not grant Nearby devices permission");
                } else {
                    Log.d(TAG, "User did not grant location permission");
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }
}

//package com.megster.cordova;
//
//import android.Manifest;
//import android.content.pm.PackageManager;
//
//import android.app.Activity;
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothDevice;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Message;
//import android.provider.Settings;
//import android.util.Log;
//import org.apache.cordova.CordovaArgs;
//import org.apache.cordova.CordovaPlugin;
//import org.apache.cordova.CallbackContext;
//import org.apache.cordova.PermissionHelper;
//import org.apache.cordova.PluginResult;
//import org.apache.cordova.Log;
//import org.json.JSONArray;
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import java.util.concurrent.TimeUnit;
//
//import java.util.Set;
//
///**
// * PhoneGap Plugin for Serial Communication over Bluetooth
// */
//public class BluetoothSerial extends CordovaPlugin {
//
//    // actions
//    private static final String LIST = "list";
//    private static final String CONNECT = "connect";
//    private static final String CONNECT_INSECURE = "connectInsecure";
//    private static final String DISCONNECT = "disconnect";
//    private static final String WRITE = "write";
//    private static final String AVAILABLE = "available";
//    private static final String READ = "read";
//    private static final String READ_UNTIL = "readUntil";
//    private static final String SUBSCRIBE = "subscribe";
//    private static final String UNSUBSCRIBE = "unsubscribe";
//    private static final String SUBSCRIBE_RAW = "subscribeRaw";
//    private static final String UNSUBSCRIBE_RAW = "unsubscribeRaw";
//    private static final String IS_ENABLED = "isEnabled";
//    private static final String IS_CONNECTED = "isConnected";
//    private static final String CLEAR = "clear";
//    private static final String SETTINGS = "showBluetoothSettings";
//    private static final String ENABLE = "enable";
//    private static final String DISCOVER_UNPAIRED = "discoverUnpaired";
//    private static final String SET_DEVICE_DISCOVERED_LISTENER = "setDeviceDiscoveredListener";
//    private static final String CLEAR_DEVICE_DISCOVERED_LISTENER = "clearDeviceDiscoveredListener";
//    private static final String SET_NAME = "setName";
//    private static final String SET_DISCOVERABLE = "setDiscoverable";
//
//    // callbacks
//    private CallbackContext connectCallback;
//    private CallbackContext dataAvailableCallback;
//    private CallbackContext rawDataAvailableCallback;
//    private CallbackContext enableBluetoothCallback;
//    private CallbackContext deviceDiscoveredCallback;
//
//    private BluetoothBroadcastReceiver actionFoundReceiver;
//
//    private BluetoothAdapter bluetoothAdapter;
//    private Map<String,BluetoothSerialService> bluetoothSerialServiceMap = new HashMap<>();
//    private BluetoothSerialService bluetoothSerialService; //
//    private Handler mDeviceWaitingConnectionHandler = new Handler();
//
//    // Debugging
//    private static final String TAG = "AB"; //"BluetoothSerial";
//    private static final boolean D = true;
//
//    // Message types sent from the BluetoothSerialService Handler
//    public static final int MESSAGE_STATE_CHANGE = 1;
//    public static final int MESSAGE_READ = 2;
//    public static final int MESSAGE_WRITE = 3;
//    public static final int MESSAGE_DEVICE_NAME = 4;
//    public static final int MESSAGE_TOAST = 5;
//    public static final int MESSAGE_READ_RAW = 6;
//
//    // Key names received from the BluetoothChatService Handler
//    public static final String DEVICE_NAME = "device_name";
//    public static final String TOAST = "toast";
//
//    StringBuffer buffer = new StringBuffer();
//    private String delimiter;
//    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
//
//    // Android 23 requires user to explicitly grant permission for location to discover unpaired
//    private static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
//    private static final int CHECK_PERMISSIONS_REQ_CODE = 2;
//    private CallbackContext permissionCallback;
//
//    // Android 31 permissions
//    private static final String BLUETOOTH_SCAN = Manifest.permission.BLUETOOTH_SCAN;
//    private static final String BLUETOOTH_CONNECT = Manifest.permission.BLUETOOTH_CONNECT;
//
//    // Android 29 and 30 permission
//    private static final String ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
//
//    @Override
//    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
//
//        Log.d(TAG, "action = " + action+ " BluetoothSerial:"+ this + " bluetoothSerialService:"+ bluetoothSerialService);
//
//        if (bluetoothAdapter == null) {
//            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        }
//
//        if (bluetoothSerialService == null) {
//            bluetoothSerialService = new BluetoothSerialService(mHandler);
//            registerStateChangeReceiver(cordova.getContext());
//            Log.d("AB", "Bluetooth serial service INSTANCE " + bluetoothSerialService);
//            Activity activity = cordova.getActivity();
//        }
//
//        boolean validAction = true;
//
//        if (action.equals(LIST)) {
//
//            listBondedDevices(callbackContext);
//
//        } else if (action.equals(CONNECT)) {
//
//            boolean secure = true;
//            connect(args, secure, callbackContext);
//
//        } else if (action.equals(CONNECT_INSECURE)) {
//
//            // see Android docs about Insecure RFCOMM http://goo.gl/1mFjZY
//            boolean secure = false;
//            connect(args, secure, callbackContext);
//
//        } else if (action.equals(DISCONNECT)) {
//
//            connectCallback = null;
//            bluetoothSerialService.stop();
//            callbackContext. onScaleDataPacket();();
//
//        } else if (action.equals(WRITE)) {
//
//            byte[] data = args.getArrayBuffer(0);
//            bluetoothSerialService.write(data);
//            callbackContext.success();
//
//        } else if (action.equals(AVAILABLE)) {
//
//            callbackContext.success(available());
//
//        } else if (action.equals(READ)) {
//
//            callbackContext.success(read());
//
//        } else if (action.equals(READ_UNTIL)) {
//
//            String interesting = args.getString(0);
//            callbackContext.success(readUntil(interesting));
//
//        } else if (action.equals(SUBSCRIBE)) {
//
//            delimiter = args.getString(0);
//            dataAvailableCallback = callbackContext;
//
//            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
//            result.setKeepCallback(true);
//            callbackContext.sendPluginResult(result);
//
//        } else if (action.equals(UNSUBSCRIBE)) {
//
//            delimiter = null;
//
//            // send no result, so Cordova won't hold onto the data available callback anymore
//            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
//            dataAvailableCallback.sendPluginResult(result);
//            dataAvailableCallback = null;
//
//            callbackContext.success();
//
//        } else if (action.equals(SUBSCRIBE_RAW)) {
//            rawDataAvailableCallback = callbackContext;
//
//            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
//            result.setKeepCallback(true);
//            callbackContext.sendPluginResult(result);
//
//        } else if (action.equals(UNSUBSCRIBE_RAW)) {
//
//            rawDataAvailableCallback = null;
//
//            callbackContext.success();
//
//        } else if (action.equals(IS_ENABLED)) {
//
//            if (bluetoothAdapter.isEnabled()) {
//                callbackContext.success();
//            } else {
//                callbackContext.error("Bluetooth is disabled.");
//            }
//
//        } else if (action.equals(IS_CONNECTED)) {
//        // along with this check.. check that device is initialized...
//            if (bluetoothSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
////                if( pairedDeviceIsConnected) {
////                    Log.d("AB", "PAIRED DEVICE IS ACTUALLY CONNECTED ");
////                    callbackContext.success();
////                } else {
////                    Log.d("AB", "PAIRED DEVICE IS ***NOT**** ACTUALLY CONNECTED ");
////                }
////                BluetoothDevice connectedDevice;
////                JSONObject o = null;
////                PluginResult res;
//                //nehal sending device object to which connection has been made
//                if (bluetoothSerialService != null) {
//                    if (bluetoothSerialService.getConnectedDevice() != null)
//                        Log.d("AB", "######## Is connected " + bluetoothSerialService.getConnectedDevice().getName());
//                }
////                    try {
////                        o = deviceToJSON(connectedDevice);
////                        res = new PluginResult(PluginResult.Status.OK, o);
////                    } catch (JSONException e) {
////                        e.printStackTrace();
////                        res = new PluginResult(PluginResult.Status.OK);
////                    }
////
////                } else {
////                    res = new PluginResult(PluginResult.Status.OK);
////                }
////
////                res.setKeepCallback(true);
////                connectCallback.sendPluginResult(res);
//                callbackContext.success();
//            } else {
//                callbackContext.error("Not connected.");
//            }
//
//        } else if (action.equals(CLEAR)) {
//
//            buffer.setLength(0);
//            callbackContext.success();
//
//        } else if (action.equals(SETTINGS)) {
//
//            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
//            cordova.getActivity().startActivity(intent);
//            callbackContext.success();
//
//        } else if (action.equals(ENABLE)) {
//
//            enableBluetoothCallback = callbackContext;
//            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            cordova.startActivityForResult(this, intent, REQUEST_ENABLE_BLUETOOTH);
//
//        } else if (action.equals(DISCOVER_UNPAIRED)) {
//
//            if (hasBluetoothPermissions()) {
//                discoverUnpairedDevices(callbackContext);
//            } else {
//                permissionCallback = callbackContext;
//                requestPermissions();
//            }
//
//        } else if (action.equals(SET_DEVICE_DISCOVERED_LISTENER)) {
//
//            this.deviceDiscoveredCallback = callbackContext;
//
//        } else if (action.equals(CLEAR_DEVICE_DISCOVERED_LISTENER)) {
//
//            this.deviceDiscoveredCallback = null;
//
//        } else if (action.equals(SET_NAME)) {
//
//            String newName = args.getString(0);
//            bluetoothAdapter.setName(newName);
//            callbackContext.success();
//
//        } else if (action.equals(SET_DISCOVERABLE)) {
//
//            int discoverableDuration = args.getInt(0);
//            Intent discoverIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//            discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableDuration);
//            cordova.getActivity().startActivity(discoverIntent);
//
//        } else {
//            validAction = false;
//
//        }
//
//        return validAction;
//    }
//
//    private boolean hasBluetoothPermissions() {
//        if (Build.VERSION.SDK_INT >= 31) { // for android 12 check for Nearby devices permission
//            return cordova.hasPermission(BLUETOOTH_SCAN) && cordova.hasPermission(BLUETOOTH_CONNECT);
//        } else if (Build.VERSION.SDK_INT == 29 || Build.VERSION.SDK_INT == 30) {
//            return cordova.hasPermission(ACCESS_FINE_LOCATION);
//        } else {
//            return cordova.hasPermission(ACCESS_COARSE_LOCATION);
//        }
//    }
//
//    private void requestPermissions() {
//        //Android 12 (API 31) and higher
//        // Users MUST accept BLUETOOTH_SCAN and BLUETOOTH_CONNECT [nearby devices]
//        // Android 10 (API 29) up to Android 11 (API 30)
//        // Users MUST accept ACCESS_FINE_LOCATION
//        // Users may accept or reject ACCESS_BACKGROUND_LOCATION
//        // Android 9 (API 28) and lower
//        // Users MUST accept ACCESS_COARSE_LOCATION
//
//        if (Build.VERSION.SDK_INT >= 31) {
//            cordova.requestPermissions(this, CHECK_PERMISSIONS_REQ_CODE, new String[]{BLUETOOTH_SCAN, BLUETOOTH_CONNECT});
//        } else if (Build.VERSION.SDK_INT == 29 || Build.VERSION.SDK_INT == 30) {
//            cordova.requestPermission(this, CHECK_PERMISSIONS_REQ_CODE, ACCESS_FINE_LOCATION);
//        } else {
//            cordova.requestPermission(this, CHECK_PERMISSIONS_REQ_CODE, ACCESS_COARSE_LOCATION);
//        }
//
//    }
//
//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//
//        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
//
//            if (resultCode == Activity.RESULT_OK) {
//                Log.d(TAG, "User enabled Bluetooth");
//                if (enableBluetoothCallback != null) {
//                    enableBluetoothCallback.success();
//                }
//            } else {
//                Log.d(TAG, "User did *NOT* enable Bluetooth");
//                if (enableBluetoothCallback != null) {
//                    enableBluetoothCallback.error("User did not enable Bluetooth");
//                }
//            }
//
//            enableBluetoothCallback = null;
//        }
//    }
//
//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        if (bluetoothSerialService != null) {
//            bluetoothSerialService.stop();
//        }
//    }
//
//    private void listBondedDevices(CallbackContext callbackContext) throws JSONException {
//        JSONArray deviceList = new JSONArray();
//        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
//
//        for (BluetoothDevice device : bondedDevices) {
//            deviceList.put(deviceToJSON(device));
//        }
//        callbackContext.success(deviceList);
//    }
//
//    private void discoverUnpairedDevices(final CallbackContext callbackContext) throws JSONException {
//
//        final CallbackContext ddc = deviceDiscoveredCallback;
//
//        final BroadcastReceiver discoverReceiver = new BroadcastReceiver() {
//
//            private JSONArray unpairedDevices = new JSONArray();
//
//            public void onReceive(Context context, Intent intent) {
//                String action = intent.getAction();
//                Log.d("AB", "onReceive STRIMG " + action);
//                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
//                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                   // Log.d("AB", "Device DISCOVERED " + device.getName());
//                    try {
//                        JSONObject o = deviceToJSON(device);
//                        unpairedDevices.put(o);
//                        if (ddc != null) {
//                            PluginResult res = new PluginResult(PluginResult.Status.OK, o);
//                            res.setKeepCallback(true);
//                            ddc.sendPluginResult(res);
//                        }
//                    } catch (JSONException e) {
//                        // This shouldn't happen, Log and ignore
//                        Log.e(TAG, "Problem converting device to JSON", e);
//                    }
//                }
////                else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
////                    if(intent !=null) {
////                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
////                        if(device!=null) {
////                            Log.d("AB", "Device CONNECTED ACL  " + device.getName());
////                        }
////                    }
////                }
////                else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
////
////                    if(intent !=null) {
////                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
////                        if(device!=null) {
////                            Log.d("AB", "Device DISSSSSS CONNECTED ACL  " + device.getName());
////                        }
////                    }
////                }
//                else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
//                    callbackContext.success(unpairedDevices);
//                    cordova.getActivity().unregisterReceiver(this);
//                }
//            }
//        };
//
//
//        Activity activity = cordova.getActivity();
//        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
//        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
//        //activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
//        //activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
//        bluetoothAdapter.startDiscovery();
//    }
//
//    private JSONObject deviceToJSON(BluetoothDevice device) throws JSONException {
//        JSONObject json = new JSONObject();
//        json.put("name", device.getName());
//        json.put("address", device.getAddress());
//        json.put("id", device.getAddress());
//        if (device.getBluetoothClass() != null) {
//            json.put("class", device.getBluetoothClass().getDeviceClass());
//        }
//        return json;
//    }
//
//    private void connect(final CordovaArgs args, final boolean secure, final CallbackContext callbackContext) throws JSONException {
//
//        if (Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
//            if(!hasBluetoothPermissions()) {
//                return;
//            }
//        }
//
//        String macAddress = args.getString(0);
//        BluetoothDevice devicePassedToConnect = bluetoothAdapter.getRemoteDevice(macAddress);
//        Log.d("AB", "---Bluetooth Service initiating to connect device---- ");
//
//        if(bluetoothSerialService != null){
//            BluetoothDevice connectedbluetoothDevice = bluetoothSerialService.getConnectedDevice();
//            Log.d("AB", "---Bluetooth Service initiating to connect NEW device " + macAddress + " name " + devicePassedToConnect.getName() +  " bluetoothSerialService: " + bluetoothSerialService.getState());
//
//            if (connectedbluetoothDevice != null) {
//                Log.d("AB", "---Bluetooth Service Already Running for Device:" + connectedbluetoothDevice.getName()
//                    + " bluetoothSerialService.getState()" + bluetoothSerialService.getState());
//            }
//
//            if(connectedbluetoothDevice != null && !connectedbluetoothDevice.getAddress().equalsIgnoreCase(macAddress)) {
//                if ((bluetoothSerialService.getState() != BluetoothSerialService.STATE_NONE /*BluetoothSerialService.STATE_CONNECTED*/)) {
//                    Log.d("AB", "---Bluetooth Service Already connecting for a different device, so close that");
//
//                    mHandler.removeMessages(0);
//
//                        mHandler.postDelayed(() -> {
//                            try {
//                                BluetoothSerial.this.connect(args, secure, callbackContext);
//                            } catch (JSONException e) {
//                                e.printStackTrace();
//                            }
//                        }, 500);
//
//                    return;
////                   bluetoothSerialService.stop();
////                   bluetoothSerialService = new BluetoothSerialService(mHandler);
//                    // this requires a bit of time... before starting a new service instance the old one needs to be cleared prperly
//
////                try {
////                    //set time in mili
////                    Thread.sleep(3000);
////
////                }catch (Exception e){
////                    e.printStackTrace();
////                }
//                } else {
//                    bluetoothSerialService.stop();
//                    bluetoothSerialService = new BluetoothSerialService(mHandler);
//                }
//            }
//        }
//
//        if (devicePassedToConnect != null) {
//            connectCallback = callbackContext;
//            Log.d("AB", "Calling connect and start service for device " + devicePassedToConnect.getName() + " bluetoothSerialService: " + bluetoothSerialService);
//            bluetoothSerialService.start(devicePassedToConnect);
//            bluetoothSerialService.connect(devicePassedToConnect, secure);
//            buffer.setLength(0);
//
//            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
//            result.setKeepCallback(true);
//            callbackContext.sendPluginResult(result);
//
//        } else {
//            callbackContext.error("Could not connect to " + macAddress);
//        }
//    }
//
//    // The Handler that gets information back from the BluetoothSerialService
//    // Original code used handler for the because it was talking to the UI.
//    // Consider replacing with normal callbacks
//    private final Handler mHandler = new Handler() {
//
//        public void handleMessage(Message msg) {
//            switch (msg.what) {
//                case MESSAGE_READ:
//                    String bundle = msg.obj.toString();
//                    Log.d(TAG, bundle);
//
//                    if (dataAvailableCallback != null) {
//                        sendDataToSubscriber(bundle);
//                    }
//
//                    break;
//                case MESSAGE_READ_RAW:
//                    if (rawDataAvailableCallback != null) {
//                        byte[] bytes = (byte[]) msg.obj;
//                        sendRawDataToSubscriber(bytes);
//                    }
//                    break;
//                case MESSAGE_STATE_CHANGE:
//
//                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
//                    switch (msg.arg1) {
//                        case BluetoothSerialService.STATE_CONNECTED:
//                            Log.i(TAG, "BluetoothSerialService.STATE_CONNECTED");
////                            notifyConnectionSuccess("CONNECTED");
//                            notifyConnectionSuccess();
//                            break;
//                        case BluetoothSerialService.STATE_CONNECTING:
//                            Log.i(TAG, "BluetoothSerialService.STATE_CONNECTING");
//                            break;
//                        case BluetoothSerialService.STATE_LISTEN:
//                            Log.i(TAG, "BluetoothSerialService.STATE_LISTEN");
//                            break;
//                        case BluetoothSerialService.STATE_NONE:
//                            Log.i(TAG, "BluetoothSerialService.STATE_NONE");
//                            break;
//                    }
//                    break;
//                case MESSAGE_WRITE:
//                    byte[] writeBuf = (byte[]) msg.obj;
//                    String writeMessage = new String(writeBuf);
//                    Log.i(TAG, "Wrote: " + writeMessage);
//                    break;
//                case MESSAGE_DEVICE_NAME:
//                    Log.i(TAG, msg.getData().getString(DEVICE_NAME));
//                    break;
//                case MESSAGE_TOAST:
//                    String message = msg.getData().getString(TOAST);
//                    notifyConnectionLost(message);
//                    break;
//            }
//        }
//    };
//
//    private void notifyConnectionLost(String error) {
//        if (connectCallback != null) {
//            connectCallback.error(error);
//            connectCallback = null;
//        }
//    }
//
//    private void notifyConnectionSuccess() {
//        if (connectCallback != null) {
//
////            PluginResult result = new PluginResult(PluginResult.Status.OK); //nehal  if paired -> PAIRED; OK -> connected
////            result.setKeepCallback(true);
////            connectCallback.sendPluginResult(result);
//
//            BluetoothDevice connectedDevice;
//            JSONObject o = null;
//            PluginResult res;
//            //nehal sending device object to which connection has been made
//            if (bluetoothSerialService != null) {
//                connectedDevice = bluetoothSerialService.getConnectedDevice();
//            try {
//                o = deviceToJSON(connectedDevice);
//                res = new PluginResult(PluginResult.Status.OK, o);
//            } catch (JSONException e) {
//                e.printStackTrace();
//                res = new PluginResult(PluginResult.Status.OK);
//            }
//
//            } else {
//            res = new PluginResult(PluginResult.Status.OK);
//            }
//
//             res.setKeepCallback(true);
//             connectCallback.sendPluginResult(res);
//
//        }
//    }
//
////    private void notifyConnectionSuccess(String data) {
////        if (connectCallback != null) {
////            PluginResult result = new PluginResult(PluginResult.Status.OK, data); //nehal  if paired -> PAIRED; OK -> connected
////            result.setKeepCallback(true);
////            connectCallback.sendPluginResult(result);
////        }
////    }
//
//    private void sendRawDataToSubscriber(byte[] data) {
//        if (data != null && data.length > 0) {
//            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
//            result.setKeepCallback(true);
//            rawDataAvailableCallback.sendPluginResult(result);
//        }
//    }
//
//    private void sendDataToSubscriber(String data) {
//        if (data != null) {
//            PluginResult result = new PluginResult(PluginResult.Status.OK, data);
//            result.setKeepCallback(true);
//            dataAvailableCallback.sendPluginResult(result);
//        }
//    }
//
//    private int available() {
//        return buffer.length();
//    }
//
//    private String read() {
//        int length = buffer.length();
//        String data = buffer.substring(0, length);
//        buffer.delete(0, length);
//        return data;
//    }
//
//    private String readUntil(String c) {
//        String data = "";
//        int index = buffer.indexOf(c, 0);
//        if (index > -1) {
//            data = buffer.substring(0, index + c.length());
//            buffer.delete(0, index + c.length());
//        }
//        return data;
//    }
//
//
//    private  boolean verifyPermissions(int[] grantResults) {
//        // At least one result must be checked.
//        if(grantResults.length < 1){
//            return false;
//        }
//        // Verify that each required permission has been granted, otherwise return false.
//        for (int result : grantResults) {
//            if (result != PackageManager.PERMISSION_GRANTED) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    @Override
//    public void onRequestPermissionResult(int requestCode, String[] permissions,
//                                          int[] grantResults) throws JSONException {
//
//        if (requestCode == CHECK_PERMISSIONS_REQ_CODE) {
//            if (verifyPermissions(grantResults)) {
//                discoverUnpairedDevices(permissionCallback);
//                this.permissionCallback = null;
//            } else {
//                // permissions not granted, disable functionality or show message
//                if (Build.VERSION.SDK_INT >= 31) {
//                    Log.d(TAG, "User did not grant Nearby devices permission");
//                } else {
//                    Log.d(TAG, "User did not grant location permission");
//                }
//            }
//        } else {
//            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        }
//
//    }
//
//    public void registerStateChangeReceiver(Context context) {
//        if(actionFoundReceiver == null) {
//            //nehal
//            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
//            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
//            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
//            actionFoundReceiver = new BluetoothBroadcastReceiver();
//            context.registerReceiver(actionFoundReceiver, filter);
//        }
//    }
//
//    /**
//     * Broadcast receiver listening to bluetooth connection and pairing actions
//     */
//    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
//
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            try {
//                String action = intent.getAction();
//                Bundle bundle = intent.getExtras();
//
//                /* Logging of actions received for debugging purpose */
//                if (BluetoothDevice.ACTION_FOUND.equals(action) && bundle != null) {
//                    for (String key : bundle.keySet()) {
//                        Log.d(TAG, String.format("======%s :  %s--%s", action, key,
//                            bundle.get(key) != null ? Objects.requireNonNull(bundle.get(key)).toString() : "null"));
//                    }
//                }
//
//                if (action == null) return;
//
//                switch (action) {
//                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
//                        Log.d(TAG, "ACTION_BOND_STATE_CHANGED");
//                        onPairingStateChanged(Objects.requireNonNull(bundle), intent);
//                        break;
//
//                    case BluetoothDevice.ACTION_FOUND:
//                        break;
//
//                    case BluetoothDevice.ACTION_ACL_CONNECTED:
//                        if(intent !=null) {
//                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                            if(device!=null) {
//                                Log.d("AB", "Device CONNECTED ACL  " + device.getName());
//                            }
//                        }
//                        break;
//                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
//                        //NEHAL FINDINGS :
//                        // Even if the device is transmitting the readings and is ON, then also this event gets notfication at a very early time
//                        // when we turn on a device, state is not changing from isConnected to CONNECTING..
//                        if(intent !=null) {
//                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                            if(device!=null) {
//                                Log.d("AB", "Device Disconnected ACL  " + device.getName());
//                                Log.d("AB", "BT service instance " + bluetoothSerialService);
//                                 bluetoothSerialService.setStateNone();
//                            }
//                        }
//                    default:
//                        break;
//                }
//            } catch (Exception e) {
//                Log.d(TAG,
//                    "Exception onReceive in Device paired with the sensor" + e);
//            }
//        }
//
//        BluetoothDevice mBluetoothDevice;
//        /**
//         * Handling of pairing/bonding state changes action
//         * BOND_NONE       --> BOND_BONDING: Pairing started
//         * BOND_BONDING    --> BOND_NONE   : Pairing error, as pairing was not completed to BOND_BONDED status
//         * BOND_BONDING    --> BOND_BONDED : Pairing completed
//         *
//         * @param bundle broadcast bundle
//         **/
//        private void onPairingStateChanged(Bundle bundle, Intent intent) {
//            if(mBluetoothDevice==null){
//                mBluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//            }
//            int newBondState = bundle.getInt(BluetoothDevice.EXTRA_BOND_STATE);
//            int previousBondState = bundle.getInt(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE);
//            Log.d(TAG, "Previous bond state: " + previousBondState + " ---> New bond state: " + newBondState);
//            String deviceInfoLog =  " | " + mBluetoothDevice.getName() + "| MACId: " + mBluetoothDevice.getAddress();
//            switch (newBondState) {
//                case BluetoothDevice.BOND_NONE:
//                    /* Possible pairing failed case as Pairing state changed from BOND_BONDING to BOND_NONE */
//                    if (previousBondState == BluetoothDevice.BOND_BONDING) {
//                        Log.d(TAG, "********** Possible pairing failed as pairing state changed from BOND_BONDING to BOND_NONE **********");
//                       // cancelDeviceDiscovery();
//                        //  mListener.onPairingError(FailureReason.FAILED);
//                    }
//                    break;
//
//                case BluetoothDevice.BOND_BONDING:
//                    /* Pairing started for device, start a timeout handler */
//                    Log.d(TAG, "********** Pairing Initiated with Device :" + deviceInfoLog + " **********");
//                    // mListener.onPairingStarted();
//                    break;
//
//                case BluetoothDevice.BOND_BONDED:
//                    Log.d(TAG, "********** Device paired successfully :" + deviceInfoLog + "  **********");
//                    //  cancelDeviceDiscovery();
////                    onPairingCompleted();
//                   // notifyConnectionSuccess("PAIRED");
//                    break;
//                default:
//                    break;
//            }
//        }
//
//    }
//}
