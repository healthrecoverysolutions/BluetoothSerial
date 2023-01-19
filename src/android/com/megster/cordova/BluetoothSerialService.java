package com.megster.cordova;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 *
 * This code was based on the Android SDK BluetoothChat Sample
 * $ANDROID_SDK/samples/android-17/BluetoothChat
 */
public class BluetoothSerialService {

    // Debugging
    private static final String TAG = "BluetoothSerialService";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "PhoneGapBluetoothSerialServiceSecure";
    private static final String NAME_INSECURE = "PhoneGapBluetoothSerialServiceInSecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE = UUID.fromString("7A9C3B55-78D0-44A7-A94E-A93E3FE118CE");
    private static final UUID MY_UUID_INSECURE = UUID.fromString("23F18142-B389-4772-93BD-52BDBB2C03E9");

    // Well known SPP UUID
    private static final UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private /*final*/ BluetoothSocket mmSocket;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    private Timer bluetooth_timer = null;
    private int bluetooth_timeout=2*1000*60;
    private InputStream in=null;
    private OutputStream out=null;
    private android.bluetooth.BluetoothDevice bluetoothDevice;
    private int plen = 0;
    private int[] PATIENTINFO_PACKET_HEADER = new int[]{80, 87, 67, 65, 80, 73};  //PWCAPI
    private int dataBPPacketSize=10; // BP
    private int dataWeightPacketSize=14; // Weight
    private Date connectionTime=null;
    private BluetoothDevice connectedBlueToothDevice;
    // private static SPPAcceptThread sppAcceptThread;

    public int getDataPacketLength(){return plen;}

    public void resetConnectedBTDevice() {
        connectedBlueToothDevice = null;
    }

    /**
     * Constructor. Prepares a new BluetoothSerial session.
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothSerialService(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(BluetoothSerial.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public static void safeClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                Log.d( TAG, "Error closing closeable:" + e.getMessage());
            }
        }
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }





    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start(BluetoothDevice device) {
        if (D) Log.d(TAG, "start");

//        if (
//            // this is specifically for the A&D devices for the SPP listener mode
//            device != null && (device.getName().contains("UA-767") || device.getName().contains("UC-355") || device.getName().contains("UC-351"))
//        ) {
//            resetConnectedBTDevice();
//
//            if (sppAcceptThread!=null && sppAcceptThread.isAlive()) {
//                Log.d(TAG, "## SPP thread is alive!!!");
//                sppAcceptThread.resetDevice(device);
//            } else {
//                Log.d(TAG, "## New SPP thread !!!");
//                sppAcceptThread = new SPPAcceptThread(device);
//                sppAcceptThread.start();
//            }
//        }

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

      //  setState(STATE_NONE);


        //        // Start the thread to listen on a BluetoothServerSocket
//        if (mSecureAcceptThread == null) {
//            mSecureAcceptThread = new AcceptThread(true);
//            mSecureAcceptThread.start();
//        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();

        }
        setState(STATE_LISTEN);

    }

    public void setStateNone() {
        setState(STATE_NONE);
    }

//    private class SPPAcceptThread extends Thread {
//        private BluetoothServerSocket mmServerSocket;
//        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
//        BluetoothDevice device;
//
//        public void resetDevice(BluetoothDevice device) {
//            Log.d(TAG, "## Resetting the device from " +  this.device.getName());
//            this.device = device;
//            Log.d(TAG, "## Resetting the device to " + device.getName());
//        }
//
//        public SPPAcceptThread(BluetoothDevice device) {
//            mmServerSocket = null;
//            this.device = device;
//            Log.d(TAG, "New SPP Accept thread for device " + device.getName());
//        }
//
//        public void run() {
//            BluetoothSocket socket = null;
//            // Keep listening until exception occurs or a socket is returned
//            try {
//                Log.i(TAG, "getting socket from adapter");
//                mmServerSocket = adapter.listenUsingInsecureRfcommWithServiceRecord("PWAccessP", UUID_SPP);
//
//
//                while (true/* mState != STATE_CONNECTED*/) {
//                    try {
//                        Log.d(TAG, "----- > SPP accept thread ---> ");
//
//                        socket = mmServerSocket.accept();
//                    } catch (IOException e) {
//                        Log.d(TAG, "IO EXCEPTION -----------SPP ACCEPT--------- " + e.getMessage());
//                    }
//
//                    // If a connection was accepted
//                    if (socket != null) {
//                        //synchronized (BluetoothSerialService.this) {
//                        // Do work to manage the connection (in a separate thread)
//                        try {
//                            Log.d(TAG, " -- > SPP thread --> connection accepted -- > ");
//                            BluetoothDevice btDevice = this.device; //device;
//                            String deviceType = "";
//                            try {
//                                String deviceAddr = btDevice.getAddress();
//                                //found connected device profile, get results
//                                Log.d(TAG, "Bluetooth device found, processing data..." + btDevice.getName());
//                                if (btDevice.getName().contains("UA-767")) {
//                                    deviceType = "bloodpressure";
//                                } else if (btDevice.getName().contains("UC-355") || btDevice.getName().contains("UC-351")) {
//                                    deviceType = "scale";
//                                }
//                                onConnection(socket);
//                                readData(socket, deviceType);
//                            } catch (Exception e) {
//                                Log.d(TAG, "Error finding bluetooth device" + e);
//                            }
//                        } catch (Exception e) {
//                            Log.d(TAG, "Bluetooth Socket Error", e);
//                        }
//                        try {
//                            if(mmServerSocket!=null) {
//                                mmServerSocket.close();
//                            }
//                        } catch (IOException exception) {
//                            exception.printStackTrace();
//                        }  //nehal trail
//                        break;
//                    }
//
//                }
//
//
//            }
//            catch (IOException ex) {
//                Log.e(TAG, "error while initializing");
//            }
//        }
//
//        /** Will cancel the listening socket, and cause the thread to finish */
//        public void cancel() {
//            try {
//                if(mmServerSocket!=null) {
//                    mmServerSocket.close();
//                }
//            } catch (IOException e) { }
//        }
//    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        if (D) Log.d(TAG, "connect to: " + device);
        bluetoothDevice = device;

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);


    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, final String socketType) {
        setState(STATE_CONNECTED);
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }


        // Start the thread to manage the connection and perform transmissions
//        if (
//            !device.getName().contains("UA-767") &&
//                !device.getName().contains("UC-355") &&
//                !device.getName().contains("UC-351")
//        ) {
            mConnectedThread = new ConnectedThread(socket, socketType, device);
            mConnectedThread.start();

            // Send the name of the connected device back to the UI Activity
            Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_DEVICE_NAME);
            Bundle bundle = new Bundle();
            bundle.putString(BluetoothSerial.DEVICE_NAME, device.getName());
            msg.setData(bundle);
            mHandler.sendMessage(msg);

  //     }
  //    setState(STATE_CONNECTED); //TODO COMMENTED code to be reverted

    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        connectedBlueToothDevice = null;
        Log.d("AB", "On service stop --- Made connected bluetooth device as null ");
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothSerial.TOAST, "Unable to connect to device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothSerialService.this.start(null);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothSerial.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothSerialService.this.start(null);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private /*final*/ BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure":"Insecure";
            Log.d(TAG, "Accept thread" + mSocketType);
//            // Create a new listening server socket
            /// this should work based on device type TBD
            try {
//                if (secure) {
//                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
//                } else {
//                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
//                }
                tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord("PWAccessP", UUID_SPP);
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;

        }

        public void run() {
            if (D) Log.d(TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket;


            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothSerialService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                Log.d(TAG, "Calling accept thread connect ----------");
                                connected(socket, socket.getRemoteDevice(),
                                    mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED: Log.d(TAG, "Calling accept thread CLOSE SOCKET ----------");
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private /*final*/ BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";
            try {
                if(mmSocket!=null) {
                    mmSocket.close();
                    mmSocket = null;
                }

            } catch (IOException e3) {
                Log.e(TAG, "unable to close() " + mSocketType + " socket during connection failure", e3);
            }

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
//            if (
//                !device.getName().contains("UA-767") &&
//                    !device.getName().contains("UC-355") &&
//                    !device.getName().contains("UC-351")
//            ) {
                Log.d(TAG, "Creating Socket connection 111 ");
                // Get a BluetoothSocket for a connection with the given BluetoothDevice
                try {
//                    if (secure) {
//                        tmp = device.createRfcommSocketToServiceRecord(UUID_SPP);
//                    } else {
                        tmp = device.createInsecureRfcommSocketToServiceRecord(UUID_SPP);
                   // }
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
                }
                mmSocket = tmp;
//            } else {
//                Log.d(TAG, "Creating Socket connection 222 ");
//                try {
//                    tmp = device.createInsecureRfcommSocketToServiceRecord(UUID_SPP);
//                } catch (IOException e) {
//                    Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
//                }
//                mmSocket = tmp;
//            }
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mmDevice);
            setName("ConnectThread" + mSocketType);

//            if (
//                !mmDevice.getName().contains("UA-767") &&
//                    !mmDevice.getName().contains("UC-355") &&
//                    !mmDevice.getName().contains("UC-351")
//            ) {
                // Always cancel discovery because it will slow down a connection
                mAdapter.cancelDiscovery();

                // Make a connection to the BluetoothSocket
                try {
                    // This is a blocking call and will only return on a successful connection or an exception
                    Log.i(TAG, "Connecting to socket...");
                    mmSocket.connect();
                    Log.i(TAG, "Connected");
                    connectedBlueToothDevice = mmSocket.getRemoteDevice();
                } catch (IOException e) {
                    Log.e(TAG, e.toString());

                    // Some 4.1 devices have problems, try an alternative way to connect
                    // See https://github.com/don/BluetoothSerial/issues/89
                    try {
                        Log.i(TAG, "Trying fallback...");
                        mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mmDevice, 1);

                        mmSocket.connect();
                        Log.i(TAG, "Connected");
                        connectedBlueToothDevice = mmSocket.getRemoteDevice();
                    } catch (Exception e2) {
                        Log.e(TAG, "Couldn't establish a Bluetooth connection.");
                        try {
                            mmSocket.close();
                        } catch (IOException e3) {
                            Log.e(TAG, "unable to close() " + mSocketType + " socket during connection failure", e3);
                        }
                        connectionFailed();
                        return;
                    }
                }
//            } else {
//                // for SPP..
//                // Make a connection to the BluetoothSocket
//                try {
//                    // This is a blocking call and will only return on a successful connection or an exception
//                    Log.i(TAG, "Connecting to socket...");
//                    mmSocket.connect();
//                    Log.i(TAG, "Connected");
//                    connectedBlueToothDevice = mmSocket.getRemoteDevice();
//                } catch (IOException e) {
//                    Log.e(TAG, e.toString());
//
//                    // Some 4.1 devices have problems, try an alternative way to connect
//                    // See https://github.com/don/BluetoothSerial/issues/89
//                    try {
//                        Log.i(TAG, "Trying fallback...");
//                        mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mmDevice, 1);
//
//                        mmSocket.connect();
//                        Log.i(TAG, "Connected");
//                        connectedBlueToothDevice = mmSocket.getRemoteDevice();
//                    } catch (Exception e2) {
//                        Log.e(TAG, "Couldn't establish a Bluetooth connection.");
//                        try {
//                            if(mmSocket!=null) {
//                                mmSocket.close();
//                            }
//
//                        } catch (IOException e3) {
//                            Log.e(TAG, "unable to close() " + mSocketType + " socket during connection failure", e3);
//                        }
//
//                        // Send the name of the connected device back to the UI Activity
//                        // Send a failure message back to the Activity
//                        Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_TOAST);
//                        Bundle bundle = new Bundle();
//                        bundle.putString(BluetoothSerial.TOAST, "Unable to connect to device");
//                        msg.setData(bundle);
//                        mHandler.sendMessage(msg);
//
//                    }
//                }

 //          }
            synchronized (BluetoothSerialService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }
        public void cancel() {
            try {
                if(mmSocket!=null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    private void connectToDevice(BluetoothSocket socket) throws IOException {
        mmSocket = socket;
        connectedBlueToothDevice = socket.getRemoteDevice();
        Log.d(TAG, "bluetoothDevice CONNECTED OR NOT: " + socket.isConnected());
        in = socket.getInputStream();
        out = socket.getOutputStream();
        Log.d(TAG, "out: " + out);
    }
//    private void readPatientInfoPacket() throws IOException{
//        // Check the packet type...
//        int pktType1 = in.read();
//        int pktType2 = in.read();
//        int pktType = pktType1 + shift(pktType2,8);
//        Log.d(TAG,"packet type: " + pktType);
//
//
//        //if packet type is not the data packet, then read in and discard header packet
//        if (pktType != 2) {
//            Log.d(TAG,"skipping patient info packet...");
//            //this is a patient info packet, skip the next 28 bytes
//            boolean packetend = false;
//            int lookfor = 0;
//            while (!packetend) {
//                int b = in.read();
//                if (b == PATIENTINFO_PACKET_HEADER[lookfor]) {
//                    lookfor++;
//                }
//                else if (lookfor > 0) {
//                    lookfor = 0;
//                }
//
//                if (lookfor == PATIENTINFO_PACKET_HEADER.length) {
//                    packetend = true;
//                }
//            }
//            Log.d(TAG,"end of patient info packet found...");
//
//            //now start the packet read over again
//            pktType1 = in.read();
//            pktType2 = in.read();
//            pktType = pktType1 + shift(pktType2,8);
//
//        }
//    }

    public static int shift(int val, int shift){
        return (val << shift);
    }

//    private int readPatientPacket() throws IOException{
//        // Check the packet length...
//        plen = in.read() + shift(in.read(),8) + shift(in.read(),16) + shift(in.read(),24);
//        Log.d(TAG,"packet len: " + plen);
//
//        //read devtype
//        int devType = in.read() + shift(in.read(),8);
//        Log.d(TAG,"devtype: " + devType);
//
//        //get flag
//        int flag = in.read();
//        Log.d(TAG,"flag: " + flag);
//
//        //get time of measurement
//        int myear = in.read() + shift(in.read(),8);
//        int mmonth = in.read();
//        int mday = in.read();
//        int mhour = in.read();
//        int mmin = in.read();
//        int msec = in.read();
//        Log.d(TAG,"measurement time: " + mday + "/" + mmonth + "/" + myear + " " + mmin + ":" + msec);
//
//        //get time of transmission
//        int tyear = in.read() + shift(in.read(),8);
//        int tmonth = in.read();
//        int tday = in.read();
//        int thour = in.read();
//        int tmin = in.read();
//        int tsec = in.read();
//        Log.d(TAG,"transmission time: " + tday + "/" + tmonth + "/" + tyear + " " + tmin + ":" + tsec);
//
//        Date measurementDate=new Date();
//        Date transmissionDate=(Date)measurementDate.clone();
//        measurementDate.setYear(myear-1900);
//        measurementDate.setMonth(mmonth-1);
//        measurementDate.setDate(mday);
//        measurementDate.setHours(mhour);
//        measurementDate.setMinutes(mmin);
//        measurementDate.setSeconds(msec);
//        transmissionDate.setYear(tyear-1900);
//        transmissionDate.setMonth(tmonth-1);
//        transmissionDate.setDate(tday);
//        transmissionDate.setHours(thour);
//        transmissionDate.setMinutes(tmin);
//        transmissionDate.setSeconds(tsec);
//
//        //get bluetooth id of device
//        int id0 = in.read();
//        int id1 = in.read();
//        int id2 = in.read();
//        int id3 = in.read();
//        int id4 = in.read();
//        int id5 = in.read();
//        Log.d(TAG,"bluetooth id: " + id0 + ":" + id1 + ":" + id2 + ":" + id3 + ":" + id4 + ":" + id5);
//
//        //get device name and serial number
//        String devnameupper = in.read() + "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read();
//        String devserial = in.read() + "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read() +
//            "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read();
//        String devnamelower = in.read() + "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read() +
//            "" + in.read() + "" + in.read() + "" + in.read() + "" + in.read();
//
//        //device battery status
//        int batterystatus = in.read();
//        Log.d(TAG,"battery status: " + batterystatus);
//
//        //skip reserved byte
//        in.skip(1);
//
//        //get device firmware/hardware
//        int devfirmwarehardware = in.read();
//
//        return devType;
//    }


    public void onConnection(BluetoothSocket socket) throws IOException {
        Log.d(TAG, "onConnection" + socket);
        connectionTime = new Date();
        if (socket!=null) {
            in = socket.getInputStream();
            out = socket.getOutputStream();
        }
    }

//    private void readData(BluetoothSocket socket, String deviceType) throws IOException{
//        connectToDevice(socket);
//        setState(STATE_CONNECTED);
//        readPatientInfoPacket();
//        int devType = readPatientPacket();
//        Log.d(TAG, "## device type " + deviceType);
//
//        if(socket!=null) {
//            BluetoothDevice connectedBTDevice = socket.getRemoteDevice(); //gives the currently connected device
//            if(connectedBTDevice!=null) {
//                Log.d(TAG, "## connected BT device " + connectedBTDevice.getName());
//                connectedBlueToothDevice = connectedBTDevice;
//            }
//        }
//        Log.d(TAG, " Connected device type " + connectedBlueToothDevice.getName());
//        if(devType == 766 || connectedBlueToothDevice.getName().contains("UA-767") ) { //device keeps on getting changed, recheck device frm data obtained
//            onBPDataPacket();
//        } else {
//            onScaleDataPacket();
//        }
//
//        sendAcknowledgement();
//         closeConnection();  //nehal trial
//    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        BluetoothDevice btDevice;

        public ConnectedThread(BluetoothSocket socket, String socketType, BluetoothDevice device) {
            Log.d(TAG, "create ConnectedThread: " + socketType + " thread " + this);
            btDevice = device;
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
                connectedBlueToothDevice = socket.getRemoteDevice();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            Log.d(TAG, "mmInStream " + mmInStream);
            Log.d(TAG, "mmInStream " + mmInStream);


        }

        public void run() {
            Log.d(TAG, "ConnectedThread RUN reading bytes");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
//                    try {
//                        Log.d(TAG, "Sleeping this thread for  a while " + this);
//                        sleep(10000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }

                    bytes = mmInStream.read(buffer);
                    String data = new String(buffer, 0, bytes);

                    // Send the new data String to the UI Activity
                    mHandler.obtainMessage(BluetoothSerial.MESSAGE_READ, data).sendToTarget();

                    // Send the raw bytestream to the UI Activity.
                    // We make a copy because the full array can have extra data at the end
                    // when / if we read less than its size.
                    Log.d(TAG, "ConnectedThread RUN reading bytes SIZE " + bytes);
                    if (bytes > 0) {
                        byte[] rawdata = Arrays.copyOf(buffer, bytes);
                        mHandler.obtainMessage(BluetoothSerial.MESSAGE_READ_RAW, rawdata).sendToTarget();
                    }



                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothSerialService.this.start(btDevice);

                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(BluetoothSerial.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                if(mmSocket!=null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public void onScaleDataPacket() throws IOException {
        //now get the scale data packet information
        int i = 0;
        String datastr = "";
        while (i < dataWeightPacketSize) {
            datastr = datastr + (char) in.read();
            i++;
        }
        Log.d(TAG,"scale data: " + datastr);

        //if the reported length was more than 14, skip the rest of the data
        if (getDataPacketLength() > dataWeightPacketSize) {
            in.skip(getDataPacketLength() - dataWeightPacketSize);
        }

        //parse reading
        double w = 0;
        String readingval = datastr.substring(4, 10);
        String readingtype = datastr.substring(10, 12);
        Log.d(TAG,"parsing: " + readingval);
        Log.d(TAG,"reading type: " + readingtype);
        //force lb as reading type
        if (readingtype.equals("lb")) {
            w = Double.valueOf(readingval);
            Log.d(TAG, "weight weight" + w);
            Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_READ);
            msg.obj = w;
            mHandler.sendMessage(msg);
        } else if (readingtype.equals("kg")){
            //convert kg to lbs
            w = Double.valueOf(readingval);
            w = kgTolbs(w);
            Log.d(TAG,"converted to lbs: " + w);
            Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_READ);
            msg.obj = w;
            mHandler.sendMessage(msg);
        }

    }

    //functions
    public static double kgTolbs(double kg){
        kg = kg * 2.20462;
        int kgi = (int)(kg*10);
        double lbs = (double)kgi/10.0;
        return lbs;
    }

    public void onBPDataPacket() throws IOException {
        int i = 0;
        String datastr = "";
        while (i < dataBPPacketSize) {
            datastr = datastr + (char) in.read();
            i++;
        }

        Log.d(TAG,"Datastring for blood pressure packet " + datastr);

        Message msg = mHandler.obtainMessage(BluetoothSerial.MESSAGE_READ);
        msg.obj = datastr;
        mHandler.sendMessage(msg);
    }

    private void sendAcknowledgement(){
        //now send response to acknowledge
        try{
            String msg = "PWA4"; // "PWA1"; //PWA4 to send data accpet without disconnet
            byte[] send = msg.getBytes();
            out.write(send);
            out.flush();
        }
        catch(Exception e){e.printStackTrace();}
    }

    private void closeConnection() throws IOException{
        closeBluetoothConnection();
    }

    public synchronized BluetoothDevice getConnectedDevice() {
        if (connectedBlueToothDevice!=null) {
            return connectedBlueToothDevice;
        }
        else {
            return bluetoothDevice;
        }
    }

    protected void closeBluetoothConnection()  {
        //try catching NPE for all before closing
        try{if(in!=null) {in.close();}}catch(Exception e){e.printStackTrace();}
        try{if(out!=null) {out.close();}}catch(Exception e){e.printStackTrace();}
        try{if(mmSocket!=null) {mmSocket.close();}}catch(Exception e){e.printStackTrace();}
        setState(STATE_NONE);
    }
}
