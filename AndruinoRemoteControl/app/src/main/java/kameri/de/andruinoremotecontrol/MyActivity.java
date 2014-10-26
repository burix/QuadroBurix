package kameri.de.andruinoremotecontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import kameri.de.andruinoremotecontrol.service.commstack.RcCommStack;
import kameri.de.andruinoremotecontrol.service.UsbWifiAdapterService;


public class MyActivity extends Activity implements View.OnClickListener, RcCommStack.RcCommunicationStackListener {

    private static final String TAG = MyActivity.class.getSimpleName();

    private static final String PREFERENCES_IP_ADRESS_KEY = "de.kameri.andruinoremotecontrol.pref.IP_ADDRESS";

    private TextView mTextViewState;
    private TextView mTextViewThrottle;
    private TextView mTextViewYaw;
    private TextView mTextViewPitch;
    private TextView mTextViewRoll;
    private ProgressBar mProgressThrottle;
    private ProgressBar mProgressYaw;
    private ProgressBar mProgressPitch;
    private ProgressBar mProgressRoll;
    private Dialog mShowingDialog;
    private Button mButtonConnectUsb;
    private Button mButtonConnectWifi;
    private View mContainerProgressBar;

    private UsbWifiAdapterService mService;
    private boolean mBound = false;

    private UsbWifiAdapterService.ConnectionState mServiceState = UsbWifiAdapterService.ConnectionState.STATE_IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        mTextViewState = (TextView) findViewById(R.id.textView4);
        mTextViewThrottle = (TextView) findViewById(R.id.textView5);
        mTextViewYaw = (TextView) findViewById(R.id.textView7);
        mTextViewPitch = (TextView) findViewById(R.id.textView9);
        mTextViewRoll = (TextView) findViewById(R.id.textView11);

        mProgressThrottle = (ProgressBar) findViewById(R.id.progressBarThrottle);
        mProgressYaw = (ProgressBar) findViewById(R.id.progressBarYaw);
        mProgressPitch = (ProgressBar) findViewById(R.id.progressBarPitch);
        mProgressRoll = (ProgressBar) findViewById(R.id.progressBarRoll);

        mButtonConnectUsb = (Button) findViewById(R.id.buttonConnectUsb);
        mButtonConnectUsb.setOnClickListener(this);
        mButtonConnectWifi = (Button) findViewById(R.id.buttonConnectWifi);
        mButtonConnectWifi.setOnClickListener(this);

        mContainerProgressBar = (View) findViewById(R.id.containerProgressBar);

        onNewIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbWifiAdapterService.ACTION_STATE_CHANGED);
        registerReceiver(mStateChangedReceiver, intentFilter);

        Intent intent = new Intent(this, UsbWifiAdapterService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mStateChangedReceiver);

        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }

        if (mShowingDialog != null) {
            mShowingDialog.dismiss();
            mShowingDialog = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            showNetworkInputDialog("Save", null);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private BroadcastReceiver mStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                if (intent.getAction().equals(UsbWifiAdapterService.ACTION_STATE_CHANGED)) {

                    mServiceState = (UsbWifiAdapterService.ConnectionState) intent.getSerializableExtra(UsbWifiAdapterService.EXTRA_STATE);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            switch (mServiceState) {
                                case STATE_IDLE:
                                    mTextViewState.setText(getString(R.string.state_idle));
                                    mTextViewState.setTextColor(Color.LTGRAY);
                                    mContainerProgressBar.setVisibility(View.INVISIBLE);
                                    mButtonConnectUsb.setText(getString(R.string.btn_connect_usb));
                                    break;
                                case STATE_ESTABLISH_USB_CONNECTION:
                                    if (mService != null) {
                                        mService.addRCDataListener(MyActivity.this);
                                    }
                                    mTextViewState.setText(getString(R.string.state_connect_usb));
                                    mTextViewState.setTextColor(Color.BLUE);
                                    mContainerProgressBar.setVisibility(View.INVISIBLE);
                                    break;
                                case STATE_ESTABLISH_WIFI_CONNECTION:
                                    mTextViewState.setText(getString(R.string.state_connect_wifi));
                                    mTextViewState.setTextColor(Color.BLUE);
                                    mContainerProgressBar.setVisibility(View.VISIBLE);
                                    mButtonConnectUsb.setText(getString(R.string.btn_disconnect_usb));
                                    break;
                                case STATE_CONNECTED:
                                    mTextViewState.setText(getString(R.string.state_connected));
                                    mTextViewState.setTextColor(Color.GREEN);
                                    mContainerProgressBar.setVisibility(View.VISIBLE);
                                    break;
                            }
                        }
                    });
                }
            }
        }
    };

    private void onIpAddressEnterSuccessful(String ipAddress) {
        UsbWifiAdapterService.serviceTryConnect(this, ipAddress);
    }

    private void showNetworkInputDialog(String confirmButtonText, final DialogInterface.OnClickListener listener) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Network connection");
        alert.setMessage("Type in IP address");

        // Set an EditText view to get user input
        final EditText input = new EditText(this);

        String prefIpAddress = readIpAddressPreference();
        input.setText(prefIpAddress);

        alert.setView(input);

        alert.setPositiveButton(confirmButtonText, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();

                storeIpAddressPreference(value);

                if (listener != null) {
                    listener.onClick(dialog, whichButton);
                }
                mShowingDialog = null;
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mShowingDialog = null;
            }
        });

        mShowingDialog = alert.show();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.buttonConnectUsb) {
            switch (mServiceState) {
                case STATE_IDLE:
                    String ipAddress = readIpAddressPreference();

                    if (ipAddress != null && !ipAddress.isEmpty()) {
                        onIpAddressEnterSuccessful(ipAddress);
                    } else {
                        showNetworkInputDialog("Connect", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String ipAddress = readIpAddressPreference();
                                if (ipAddress != null && !ipAddress.isEmpty()) {
                                    onIpAddressEnterSuccessful(ipAddress);
                                }
                            }
                        });
                    }
                    break;
                default:
                    UsbWifiAdapterService.serviceCloseConnect(this);
                    break;
            }


        } else if (v.getId() == R.id.buttonConnectWifi) {
        }
    }

    private String readIpAddressPreference() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getString(PREFERENCES_IP_ADRESS_KEY, "192.168.178.1");
    }

    private void storeIpAddressPreference(String ipAddress) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PREFERENCES_IP_ADRESS_KEY, ipAddress);
        editor.commit();
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            UsbWifiAdapterService.LocalBinder binder = (UsbWifiAdapterService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mService = null;
        }
    };

    @Override
    public void onControlCommandReceived(final int throttleValue, final int yawValue, final int pitchValue, final int rollValue) {
//        Log.d(TAG, "onControlCommandReceived");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mTextViewThrottle.setText(String.valueOf(throttleValue));
                mProgressThrottle.setProgress(throttleValue);

                mTextViewYaw.setText(String.valueOf(yawValue));
                mProgressYaw.setProgress(yawValue);

                mTextViewPitch.setText(String.valueOf(pitchValue));
                mProgressPitch.setProgress(pitchValue);

                mTextViewRoll.setText(String.valueOf(rollValue));
                mProgressRoll.setProgress(rollValue);
            }
        });

    }
}
