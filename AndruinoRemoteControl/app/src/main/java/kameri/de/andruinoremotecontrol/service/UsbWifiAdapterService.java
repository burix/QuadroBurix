package kameri.de.andruinoremotecontrol.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kameri.de.andruinoremotecontrol.service.commstack.RcCommStack;

public class UsbWifiAdapterService extends Service {

    private static final String TAG = "UsbWifiAdapterService";
    public static final String ACTION_TRY_CONNECTION = "de.kameri.andruinoremotecontrol.action.tryconnection";
    public static final String ACTION_CLOSE_CONNECTION = "de.kameri.andruinoremotecontrol.action.closeconnection";
    //    public static final String ACTION_DISBAND_CONNECTION = "de.kameri.andruinoremotecontrol.action.disbandconnection";
    public static final String NETWORK_EXTRA_IPADDRESS = "de.kameri.andruinoremotecontrol.extra.NETWORK_IPADDRESS";

    public static final String ACTION_STATE_CHANGED = "de.kameri.andruinoremotecontrol.action.STATE_CHANGED";
    public static final String EXTRA_STATE = "de.kameri.andruinoremotecontrol.extra.STATE";

//    public static final String ACTION_USB_DATA_RECEIVED = "de.kameri.andruinoremotecontrol.action.USB_DATA_RECEIVED";
//    public static final String EXTRA_USB_DATA = "de.kameri.andruinoremotecontrol.extra.USB_DATA";

    public static final String ACTION_RC_CONTROL_RECEIVED = "de.kameri.andruinoremotecontrol.action.RC_CONTROL_RECEIVED";
    public static final String EXTRA_RC_CONTROL_ID = "de.kameri.andruinoremotecontrol.extra.RC_CONTROL_ID";
    public static final String EXTRA_RC_CONTROL_VALUE = "de.kameri.andruinoremotecontrol.extra.RC_CONTROL_VALUE";

    public enum ConnectionState {
        STATE_IDLE,
        STATE_ESTABLISH_USB_CONNECTION,
        STATE_ESTABLISH_WIFI_CONNECTION,
        STATE_CONNECTED
    }

    private ConnectionState mConnectionState = ConnectionState.STATE_IDLE;

    // USB attributes
    private UsbManager mUsbManager;
    private UsbSerialPort mSerialPort;
    private SerialInputOutputManager mSerialIoManager;

    // WIFI attribubtes
    private String mIpAdress = "";

    private RcCommStack mRCDataInterpreter;
    private RCDataDispatcher mRCDataDisptacher = new RCDataDispatcher();

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    mServiceMainThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onUsbDataReceived(data);
                        }
                    });
                }
            };

    private final BroadcastReceiver mUsbDeviceDetachedReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent != null && intent.getAction() != null && intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                        handleUsbDetached();
                    }
                }
            };

    private Handler mServiceMainThreadHandler = new Handler();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService mSocketExecuter = Executors.newCachedThreadPool();

    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerReceiver(mUsbDeviceDetachedReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));

        changeToState(ConnectionState.STATE_IDLE);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public UsbWifiAdapterService getService() {
            // Return this instance of UsbWifiAdapterService so clients can call public methods
            return UsbWifiAdapterService.this;
        }
    }

    public void addRCDataListener(RcCommStack.RcCommunicationStackListener listener)
    {
        Log.d(TAG,"addRCDataListener");
        mRCDataDisptacher.addListener(listener);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void changeToState(ConnectionState newState) {
        mConnectionState = newState;
        Intent broadcastIntent = new Intent(UsbWifiAdapterService.ACTION_STATE_CHANGED);
        broadcastIntent.putExtra(UsbWifiAdapterService.EXTRA_STATE, mConnectionState);
        sendBroadcast(broadcastIntent);

        switch (mConnectionState) {
            case STATE_IDLE:
                mRCDataDisptacher.removeAllListeners();
                break;
            case STATE_ESTABLISH_USB_CONNECTION:
                break;
            case STATE_ESTABLISH_WIFI_CONNECTION:
                break;
            case STATE_CONNECTED:
                break;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && intent.getAction() != null) {
            if (ACTION_TRY_CONNECTION.equals(intent.getAction())) {

                mIpAdress = intent.getStringExtra(NETWORK_EXTRA_IPADDRESS);

                if (mIpAdress != null && !mIpAdress.equals("")) {
                    handleUsbConnectionRequest();
                }
            }else if (ACTION_CLOSE_CONNECTION.equals(intent.getAction())) {
                handleUsbDetached();
            }
        }

        return Service.START_STICKY;
    }

    private void handleUsbConnectionRequest() {
        switch (mConnectionState) {
            case STATE_IDLE:
                changeToState(ConnectionState.STATE_ESTABLISH_USB_CONNECTION);

                // initialize rc data interpreter and dispatcher
                mRCDataInterpreter = new RcCommStack();
                mRCDataInterpreter.setStackListener(mRCDataDisptacher);

//                onUsbDeviceFound(new UsbSerialPort() {
//                    @Override
//                    public UsbSerialDriver getDriver() {
//                        return null;
//                    }
//
//                    @Override
//                    public int getPortNumber() {
//                        return 0;
//                    }
//
//                    @Override
//                    public String getSerial() {
//                        return null;
//                    }
//
//                    @Override
//                    public void open(UsbDeviceConnection connection) throws IOException {
//
//                    }
//
//                    @Override
//                    public void close() throws IOException {
//
//                    }
//
//                    @Override
//                    public int read(byte[] dest, int timeoutMillis) throws IOException {
//                        return 0;
//                    }
//
//                    @Override
//                    public int write(byte[] src, int timeoutMillis) throws IOException {
//                        return 0;
//                    }
//
//                    @Override
//                    public void setParameters(int baudRate, int dataBits, int stopBits, int parity) throws IOException {
//
//                    }
//
//                    @Override
//                    public boolean getCD() throws IOException {
//                        return false;
//                    }
//
//                    @Override
//                    public boolean getCTS() throws IOException {
//                        return false;
//                    }
//
//                    @Override
//                    public boolean getDSR() throws IOException {
//                        return false;
//                    }
//
//                    @Override
//                    public boolean getDTR() throws IOException {
//                        return false;
//                    }
//
//                    @Override
//                    public void setDTR(boolean value) throws IOException {
//
//                    }
//
//                    @Override
//                    public boolean getRI() throws IOException {
//                        return false;
//                    }
//
//                    @Override
//                    public boolean getRTS() throws IOException {
//                        return false;
//                    }
//
//                    @Override
//                    public void setRTS(boolean value) throws IOException {
//
//                    }
//
//                    @Override
//                    public boolean purgeHwBuffers(boolean flushRX, boolean flushTX) throws IOException {
//                        return false;
//                    }
//                });

                new AsyncTask<Void, Void, UsbSerialPort>() {
                    @Override
                    protected UsbSerialPort doInBackground(Void... params) {
                        Log.d(TAG, "Refreshing device list ...");
                        SystemClock.sleep(1000);

                        final List<UsbSerialDriver> drivers =
                                UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

                        final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
                        for (final UsbSerialDriver driver : drivers) {
                            final List<UsbSerialPort> ports = driver.getPorts();
                            Log.d(TAG, String.format("+ %s: %s port%s",
                                    driver, ports.size(), ports.size() == 1 ? "" : "s"));
                            result.addAll(ports);
                        }

                        for (UsbSerialPort port : result) {
                            if (port.getDriver().getDevice().getProductId() == 67 && port.getDriver().getDevice().getVendorId() == 9025) {
                                return port;
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(UsbSerialPort result) {
                        onUsbDeviceFound(result);
                    }

                }.execute((Void) null);
                break;
            case STATE_ESTABLISH_USB_CONNECTION:
            case STATE_ESTABLISH_WIFI_CONNECTION:
            case STATE_CONNECTED:
            default:
                Log.w(TAG, "handleUsbConnectionRequest in wrong state");
                break;
        }
    }

    private void handleUsbDetached() {
        switch (mConnectionState) {
            case STATE_ESTABLISH_USB_CONNECTION:
            case STATE_ESTABLISH_WIFI_CONNECTION:
            case STATE_CONNECTED:
                stopIoManager();
//                if(dummyTimer!=null)
//                {
//                    dummyTimer.cancel();
//                    dummyTimer = null;
//                }

                changeToState(ConnectionState.STATE_IDLE);
                break;
            case STATE_IDLE:
            default:
                Log.w(TAG, "handleUsbDetached in wrong state");
                break;
        }
    }

    private void onUsbDeviceFound(UsbSerialPort result) {
        switch (mConnectionState) {
            case STATE_ESTABLISH_USB_CONNECTION:
                // save new connected serial port
                mSerialPort = result;

                if (result != null) {
                    changeToState(ConnectionState.STATE_ESTABLISH_WIFI_CONNECTION);

                    // open the usb connection for data transfer
                    openUsbConnection();

                    // connect wifi to server (raspberry pi)
                    mSocketExecuter.execute(new ClientThread());
                } else {
                    changeToState(ConnectionState.STATE_IDLE);
                }
                break;
            case STATE_ESTABLISH_WIFI_CONNECTION:
            case STATE_CONNECTED:
            case STATE_IDLE:
            default:
                Log.w(TAG, "onUsbConnected in wrong state");
                break;
        }
    }


//    byte[] dummyBytes1 = {91,33,41,42};
//    byte[] dummyBytes2 = {43,44,93};
//    byte[] dummyBytes3 = {91,33,41,42,43,44,93,91};
//    byte[] dummyBytes4 = {33,41,42,43,44,93};
//    int byteRotataionIndex = 0;
//    Timer dummyTimer;
    private void openUsbConnection() {
//        if(dummyTimer!=null)
//        {
//            dummyTimer.cancel();
//            dummyTimer = null;
//        }
//        dummyTimer = new Timer();
//        dummyTimer.scheduleAtFixedRate(new TimerTask() {
//            @Override
//            public void run() {
//                switch(byteRotataionIndex)
//                {
//                    case 0:
//                        mListener.onNewData(dummyBytes1);
//                        break;
//                    case 1:
//                        mListener.onNewData(dummyBytes2);
//                        break;
//                    case 2:
//                        mListener.onNewData(dummyBytes3);
//                        break;
//                    case 3:
//                        mListener.onNewData(dummyBytes4);
//                        break;
//                }
//                byteRotataionIndex = (byteRotataionIndex+1)%4;
//            }
//        },1000,1000);

        final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        UsbDeviceConnection connection = usbManager.openDevice(mSerialPort.getDriver().getDevice());
        if (connection == null) {
            Log.w(TAG, "Opening device failed");
            return;
        }

        try {
            mSerialPort.open(connection);
            mSerialPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
            try {
                mSerialPort.close();
            } catch (IOException e2) {
                // Ignore.
            }
            mSerialPort = null;
            return;
        }
        Log.w(TAG, "Serial device: " + mSerialPort.getClass().getSimpleName());

        stopIoManager();
        startIoManager();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mSerialPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(mSerialPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

//    private void closeUsbPort() {
//        stopIoManager();
//        if (mSerialPort != null) {
//            try {
//                mSerialPort.close();
//            } catch (IOException e) {
//                // Ignore.
//            }
//            mSerialPort = null;
//        }
//    }

    private void onUsbDataReceived(byte[] data) {
//        String receivedMessageStr = new String(data);
        //Log.d(TAG, "Usb data received: [" + receivedMessageStr + "]");

        switch (mConnectionState) {
            case STATE_CONNECTED:
            case STATE_ESTABLISH_WIFI_CONNECTION:
                try {
                    mRCDataInterpreter.appendNewData(data);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage());
                }
//                Intent broadcastIntent = new Intent(UsbWifiAdapterService.ACTION_USB_DATA_RECEIVED);
//                broadcastIntent.putExtra(UsbWifiAdapterService.EXTRA_USB_DATA, receivedMessageStr);
//                sendBroadcast(broadcastIntent);
                break;
            case STATE_ESTABLISH_USB_CONNECTION:
            case STATE_IDLE:
            default:
                //Log.w(TAG, "onUsbDataReceived in wrong state => ignoring data bytes");
                break;
        }
    }


    private void onWiFiConnected(BufferedOutputStream buffOutStream) {
        switch (mConnectionState) {
            case STATE_ESTABLISH_WIFI_CONNECTION:
                mRCDataDisptacher.addListener(new RCDataToWifiDispatcher(buffOutStream));
                changeToState(ConnectionState.STATE_CONNECTED);
                break;
            case STATE_ESTABLISH_USB_CONNECTION:
            case STATE_CONNECTED:
            case STATE_IDLE:
            default:
                Log.w(TAG, "onWiFiConnected in wrong state");
                break;
        }
    }

    // -------
    // Wifi part

    private class ClientThread implements Runnable {

        private Socket clientSocket;
        private static final int SERVERPORT = 6000;

        @Override
        public void run() {

            try {

                InetAddress serverAddr = InetAddress.getByName(mIpAdress);

                Log.i(TAG, "Connecting to ip Address: " + serverAddr.getHostAddress());

                clientSocket = new Socket(serverAddr, SERVERPORT);

                Log.i(TAG, "Connected to ip Address: " + serverAddr.getHostAddress());

                final BufferedOutputStream clientSocketOs = new BufferedOutputStream(clientSocket.getOutputStream());


//                clientSocketWriter = new PrintWriter(new BufferedWriter(
//                        new OutputStreamWriter(clientSocket.getOutputStream())),
//                        true);

                mServiceMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onWiFiConnected(clientSocketOs);
                    }
                });

                Log.i(TAG, "Connected with wifi address: " + serverAddr.getHostAddress());
            } catch (UnknownHostException e1) {
                e1.printStackTrace();
                // TODO broadcast that wifi connection was not successfull
            } catch (IOException e1) {
                e1.printStackTrace();
                // TODO broadcast that wifi connection was not successfull
            }
        }
    }

    public static void serviceTryConnect(Context ctx, String ipAddress) {
        Intent serviceIntent = new Intent(ctx, UsbWifiAdapterService.class);
        serviceIntent.setAction(UsbWifiAdapterService.ACTION_TRY_CONNECTION);
        serviceIntent.putExtra(UsbWifiAdapterService.NETWORK_EXTRA_IPADDRESS, ipAddress);

        ctx.startService(serviceIntent);
    }

    public static void serviceCloseConnect(Context ctx) {
        Intent serviceIntent = new Intent(ctx, UsbWifiAdapterService.class);
        serviceIntent.setAction(UsbWifiAdapterService.ACTION_CLOSE_CONNECTION);

        ctx.startService(serviceIntent);
    }
}
