package com.example.bluetoothdata;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothService {
    private static final String TAG = "BluetoothService";

    private static final String appName = "MYAPP";

    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private AcceptThread mInsecureAcceptThread;

    private ConnectThread mConnectThread;
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;
    ProgressDialog mProgressDialog;

    private ConnectedThread mConnectedThread;

    private final BluetoothAdapter mBluetoothAdapter;
    Context mContext;

    public BluetoothService(Context context ) {
        mContext=context;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        start();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        private AcceptThread() {
            BluetoothServerSocket tmp = null;

            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID_INSECURE);
                Log.d(TAG, "AcceptThread: Setting up Server using " + MY_UUID_INSECURE);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "AcceptThread: IOException : "+e.getMessage());
            }
            mmServerSocket = tmp;
        }
        public  void  run()
        {
            Log.d(TAG, "run: Accept Threading running");

            BluetoothSocket socket = null;

            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Log.d(TAG, "run: RFCOM Server socket start");

                socket = mmServerSocket.accept();
                Log.d(TAG, "run: RFCOM Server socket accpeted connection");
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "AcceptThread: IOException : "+e.getMessage());
            }
            //3
            if (socket!=null)
            {
                connected(socket,mmDevice);
            }
            Log.d(TAG, "run: END Accept Thread");
        }

        public  void  cancel() {
            Log.d(TAG, "cancel: Cancelling AcceptThread");
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Close AcceptThread: IOException : "+e.getMessage());
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG, "ConnectThread: started");
            mmDevice= device;
            deviceUUID = uuid;
        }
        public void run()
        {
            BluetoothSocket tmp = null;
            Log.d(TAG, "run: ConnectThread:");

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                Log.d(TAG, "run: ConnectThread trying to create InsecureRFcomSocket using UUID"+MY_UUID_INSECURE);
                tmp = mmDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "run: ConnectThread IOException: Could not create InsecureRfcomSocket"+e.getMessage());
            }
            mmSocket = tmp;
            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Log.d(TAG, "run: ConnectThread connecting");
                mmSocket.connect();
                Log.d(TAG, "run: ConnectThread connected");
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    //Close the socket
                    mmSocket.close();
                    Log.d(TAG, "run: Closed Socket");
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                    Log.d(TAG, "run: ConnectThread IOException: Could not close socket"+e.getMessage());
                }
                Log.d(TAG, "run: ConnectThread IOException: Could not connect to UUID"+MY_UUID_INSECURE);
            }
            //3
            connected(mmSocket,mmDevice);
        }
        public void cancel() {
            Log.d(TAG, "cancel: Closing Client Socket ConnectThread");
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Close of socket ConnectThread: IOException : "+e.getMessage());
            }
        }
    }



    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized  void start() {
        Log.d(TAG, "start: ");

        // Cancel any thread attempting to make a connection
        if (mConnectThread !=null)
        {
            Log.d(TAG, "start: mConnectThread is not null");
            mConnectThread.cancel();
            Log.d(TAG, "start: mConnectThread cancel");
            mConnectThread=null;
        }
        if (mInsecureAcceptThread == null)
        {
            Log.d(TAG, "start: mInsecureAcceptThread is null");
            mInsecureAcceptThread =  new AcceptThread();
            mInsecureAcceptThread.start();
            Log.d(TAG, "start: mInsecureAcceptThread start");
        }
    }

    /**
     * Accept Thread starts & sits waiting for a connection.
     * Then ConnectThread starts & attempts to make a connection with the other devices AcceptThread.
     */
    public void startClient(BluetoothDevice device,UUID uuid)
    {
        Log.d(TAG, "startClient: Started");

        //init Progress dialog
//        mProgressDialog = ProgressDialog.show(mContext,"Connecting Bluetooth"
//        ,"Please wait...",true);

        mConnectThread = new ConnectThread(device,uuid);
        mConnectThread.start();
        Log.d(TAG, "startClient: ConnectThread started");

    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInputStream;
        private final OutputStream mmOutputStream;


        private ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread: ");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            //dismiss progressDialog when connection is established
//            mProgressDialog.dismiss();
            Log.d(TAG, "ConnectedThread:  progress dialog dismiss");

            try {
                tmpIn = mmSocket.getInputStream();
                Log.d(TAG, "ConnectedThread: getInputStream");
                tmpOut = mmSocket.getOutputStream();
                Log.d(TAG, "ConnectedThread: getOutputStream");
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "ConnectedThread : IOException "+e.getMessage());
            }
            mmInputStream = tmpIn;
            mmOutputStream = tmpOut;
        }

        public void run() {
            Log.d(TAG, "run: start");
            byte[] buffer = new byte[1024];     //buffer store for the stream
            int bytes;      //bytes returned from read()
            while (true)
            {
                try {
                    bytes = mmInputStream.read(buffer);
                    String incomingMessage = new String(buffer,0,bytes);
                    Log.d(TAG, "run: readBuffer & InputStream :"+incomingMessage);

                    Intent incomingMessageIntent = new Intent("incomingMessage");
                    incomingMessageIntent.putExtra("TheMessage",incomingMessage);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(incomingMessageIntent);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "ConnectedThread :run: IOException "+e.getMessage());
                    break;
                }
            }
        }
        //call this from main activity to shutdown the connection
        public void cancel() {
            Log.d(TAG, "cancel: Closing Client Socket ConnectedThread");
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Close of socket ConnectedThread: IOException : "+e.getMessage());
            }
        }
        //cal this from main activity to send data to remote deivce
        public void write(byte[] bytes)
        {
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "write: writing to outputstream");
            try {
                mmOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "ConnectedThread: IOException writing outputstream : "+e.getMessage());
            }
        }

    }

    private void connected(BluetoothSocket mmSocket, BluetoothDevice mmDevice) {
        Log.d(TAG, "connected: starting");

        //start the thread to manage the connection & perform transmissions
        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectedThread.start();
        Log.d(TAG, "connected: ConnectedThread start");
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        Log.d(TAG, "write:  write Called");
        //perform write
        mConnectedThread.write(out);
        Log.d(TAG, "write: output");
    }

}






