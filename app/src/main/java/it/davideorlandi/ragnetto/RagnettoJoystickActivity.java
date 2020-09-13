package it.davideorlandi.ragnetto;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.Set;

import it.davideorlandi.ragnetto.service.BluetoothSerialService;


public class RagnettoJoystickActivity extends AppCompatActivity implements Handler.Callback
{
    private static final String TAG = "RagnettoJoy.Activity";
    private static final String BUNDLE_ID_SENSOR_ACTIVE = "sensorActive";
    private static final int JOYSTICK_POLLING_INTERVAL = 100;
    private static final String SWAP_CONTROLS_PREFERENCE_KEY = "invert_rotation_sidestep";

    private Menu menu;
    private JoystickView primaryJoystick;
    private JoystickView secondaryJoystick;
    private TextView txtForward;
    private TextView txtSidestep;
    private TextView txtRotation;
    private TextView terminal;
    private Spinner mode;
    private boolean swapControls;
    private boolean sensorActive = false;
    private Handler handler;
    private Handler serviceCommunicationHandler;
    private BluetoothSerialService btService;
    private ServiceConnection connection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            Log.v(TAG, "onServiceConnected");
            BluetoothSerialService.RagnettoBinder binder = (BluetoothSerialService.RagnettoBinder) service;
            btService = binder.getService(serviceCommunicationHandler);
            updateConnectMenuStatus();

            // request configuration when service bound
            requestConfiguration();
        }

        @Override
        public void onServiceDisconnected(ComponentName className)
        {
            Log.v(TAG, "onServiceDisconnected");
            btService = null;
        }

        @Override
        public void onBindingDied(ComponentName name)
        {
            Log.v(TAG, "onBindingDied");
            btService = null;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        Log.d(TAG, "Creating menu");
        this.menu = menu;
        /* (Nota: se non trova menu in R bisogna selezionare
           File->Invalidate caches/restart. Android studio è una
           vera merda! */
        getMenuInflater().inflate(R.menu.menu_joystick, menu);

        if (primaryJoystick.isSensorAvailable())
        {
            updateSensorMenuItemStatus();
        }
        else
        {
            menu.findItem(R.id.mi_sensor).setVisible(false);
            sensorActive = false;
        }

        updateConnectMenuStatus();

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(TAG, "Creating activity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ragnetto_joystick_activity);
        primaryJoystick = findViewById(R.id.primaryJoystick);
        secondaryJoystick = findViewById(R.id.secondaryJoystick);
        txtForward = findViewById(R.id.txt_speed_forward);
        txtRotation = findViewById(R.id.txt_speed_rotation);
        txtSidestep = findViewById(R.id.txt_speed_side);
        terminal = findViewById(R.id.terminal);
        mode = findViewById(R.id.mode);

        /* apparently this is needed to enable scrolling, even if the textview
        has scrollbars="vertical"
         */
        terminal.setMovementMethod(new ScrollingMovementMethod());


        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.modes, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(adapter);
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
            {
                Log.v(TAG, "mode: onItemSelected (pos.=" + position + ", id=" + id + ")");
                if (btService != null && btService.isConnected())
                {
                    btService.sendCommand(RagnettoConstants.COMMAND_MODE + position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
                Log.v(TAG, "mode: onNothingSelected");
            }
        });

        serviceCommunicationHandler = new Handler(Looper.getMainLooper(), this);

        if (savedInstanceState != null)
        {
            sensorActive = savedInstanceState.getBoolean(BUNDLE_ID_SENSOR_ACTIVE, false);

            Log.v(TAG, "Saved sensor state: " + (sensorActive ? "active" : "stopped"));
        }


    }

    @Override
    protected void onStop()
    {
        Log.v(TAG, "onStop");
        super.onStop();
        unbindService(connection);
    }

    @Override
    protected void onStart()
    {
        Log.v(TAG, "onStart");
        super.onStart();
        Intent intent = new Intent(this, BluetoothSerialService.class);
        /* start the service manually so it wont be destroyed when unbinding (which happens when the rotation changes) */
        startService(intent);
        // bind to the service
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
    }

    @Override
    protected void onResume()
    {
        Log.v(TAG, "onResume");
        super.onResume();
        resumeSensor();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        swapControls = prefs.getBoolean(SWAP_CONTROLS_PREFERENCE_KEY, false);
        handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    final float y = primaryJoystick.getStickY();
                    final float xpri = primaryJoystick.getStickX();
                    final float xsec = secondaryJoystick.getStickX();
                    final float x, r;

                    if (swapControls)
                    {
                        x = xpri;
                        r = xsec;
                    }
                    else
                    {
                        x = xsec;
                        r = xpri;
                    }

                    txtForward.setText(String.format(Locale.getDefault(), "%.0f %%", y * 100));
                    txtSidestep.setText(String.format(Locale.getDefault(), "%.0f %%", x * 100));
                    txtRotation.setText(String.format(Locale.getDefault(), "%.0f %%", r * 100));

                    if (btService != null && btService.isConnected())
                    {
                        String command = String.format(Locale.getDefault(), "K%d;%d;%d", (int) (y * 100f), (int) (x * 100f), (int) (-r * 100f));
                        btService.sendCommand(command);
                    }
                } finally
                {
                    handler.postDelayed(this, JOYSTICK_POLLING_INTERVAL);
                }
            }
        }, JOYSTICK_POLLING_INTERVAL);

        updateConnectMenuStatus();

        // request configuration when resuming
        requestConfiguration();
    }

    @Override
    protected void onPause()
    {
        Log.v(TAG, "onPause");
        super.onPause();
        pauseSensor();
        handler.removeCallbacksAndMessages(null);
    }

    /**
     * Save state. Necessary because the activity will be recreated by the system when changing
     * orientation.
     *
     * @param outState bundle to save to.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState)
    {
        Log.d(TAG, "Saving state");
        super.onSaveInstanceState(outState);
        outState.putBoolean(BUNDLE_ID_SENSOR_ACTIVE, sensorActive);
        Log.v(TAG, "Saved sensor state: " + (sensorActive ? "active" : "stopped"));
    }

    public void onMenuClickSettings(MenuItem item)
    {
        Log.d(TAG, "Settings menu clicked");
        Intent intent = new Intent(this, RagnettoSettingsActivity.class);
        startActivity(intent);
    }

    public void onMenuClickSensor(MenuItem item)
    {
        Log.d(TAG, "Sensor menu clicked - toggling sensor status");
        toggleSensor();
    }

    public void onMenuClickTuning(MenuItem item)
    {
        Log.d(TAG, "Tuning menu clicked");
        Intent intent = new Intent(this, RagnettoConfigActivity.class);
        startActivity(intent);
    }

    public void onMenuClickConnect(MenuItem item)
    {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
        {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.bt_not_available_title)
                    .setMessage(R.string.bt_not_available_text)
                    .setCancelable(false)
                    .setPositiveButton(R.string.bt_not_available_ok, null)
                    .show();
        }
        else
        {
            final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || adapter.getBluetoothLeScanner() == null)
            {
                Log.wtf(TAG, "BluetoothAdapter or BluetoothLeScanner null");
            }
            else
            {
                if (adapter.isEnabled())
                {
                    // BT available and enabled
                    Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
                    if (pairedDevices.isEmpty())
                    {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.bt_no_paired_device_title)
                                .setMessage(R.string.bt_no_paired_device_text)
                                .setCancelable(false)
                                .setPositiveButton(R.string.bt_no_paired_device_ok, null)
                                .show();
                    }

                    // arrays to contain device labels (needed by AlertDialog) and full data
                    final String[] deviceNames = new String[pairedDevices.size()];
                    final BluetoothDevice[] deviceObjects = new BluetoothDevice[pairedDevices.size()];

                    int i = 0;
                    for (BluetoothDevice device : pairedDevices)
                    {
                        deviceNames[i] = device.getName() + " " + device.getAddress();
                        deviceObjects[i] = device;
                        i++;
                    }

                    if (btService != null)
                    {
                        new AlertDialog.Builder(this).setTitle(R.string.bt_select_title).setItems(deviceNames, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                Log.v(TAG, "selected object " + which + ": " + deviceObjects[which]);
                                btService.connect(deviceObjects[which]);
                            }
                        }).show();
                    }
                    else
                    {
                        Log.wtf(TAG, "Service not connected!");
                    }
                }
                else
                {
                    // bluetooth not enabled; ask to enable it
                    Intent bton = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(bton, 1);
                }
            }
        }
    }

    public void onMenuClickDisconnect(MenuItem item)
    {
        if (btService != null)
        {
            btService.disconnect();
        }
    }

    private void updateConnectMenuStatus()
    {
        if (btService != null)
        {
            boolean connected = btService.isConnected();
            Log.d(TAG, "Updating conected status (connected=" + connected + ")");
            findViewById(R.id.mode).setEnabled(connected);
            if (menu != null)
            {
                menu.findItem(R.id.mi_disconnect).setVisible(connected);
                menu.findItem(R.id.mi_connect).setVisible(!connected);
            }
            else
            {
                Log.d(TAG, "(But skipping menu update because menu is null)");
            }
        }
        else
        {
            Log.d(TAG, "Update connected status skipped because service is not bound");
        }
    }

    /**
     * Start listening to sensor and update its icon.
     */
    private void startSensor()
    {
        if (primaryJoystick.isSensorAvailable())
        {
            primaryJoystick.startSensor();
            sensorActive = true;
            updateSensorMenuItemStatus();
        }
    }

    /**
     * Stop listening to sensor and update its icon.
     */
    private void stopSensor()
    {
        primaryJoystick.stopSensor();
        sensorActive = false;
        updateSensorMenuItemStatus();
    }

    /**
     * Toggle sensor statuc (and icon).
     */
    private void toggleSensor()
    {
        Log.v(TAG, "Sensor is currently " + (sensorActive ? "active" : "stopped") + " - toggling");
        if (sensorActive)
        {
            stopSensor();
        }
        else
        {
            startSensor();
        }
    }

    /**
     * Resume listening to sensor if the toggle was enabled.
     */
    private void resumeSensor()
    {
        if (sensorActive)
        {
            primaryJoystick.startSensor();
        }
    }

    /**
     * Stop listening to sensor but leave the toggle as it is.
     */
    private void pauseSensor()
    {
        primaryJoystick.stopSensor();
    }

    @Override
    public boolean handleMessage(@NonNull Message msg)
    {
        switch (msg.what)
        {
            case BluetoothSerialService.MESSAGE_TYPE_CONNECTED:
                Toast.makeText(this, R.string.toast_connected, Toast.LENGTH_SHORT).show();
                updateConnectMenuStatus();
                requestConfiguration();
                break;
            case BluetoothSerialService.MESSAGE_TYPE_DISCONNECTED:
                Toast.makeText(this, R.string.toast_disconnected, Toast.LENGTH_SHORT).show();
                updateConnectMenuStatus();
                break;
            case BluetoothSerialService.MESSAGE_TYPE_UNABLE_TO_CONNECT:
                Toast.makeText(this, R.string.toast_unable_to_connected, Toast.LENGTH_LONG).show();
                updateConnectMenuStatus();
                break;
            case BluetoothSerialService.MESSAGE_TYPE_VALID_STRING_RECEIVED:
                String line = (String) msg.obj;
                terminal.append(line + "\n");
                RagnettoConfiguration conf = RagnettoConfiguration.parseLine(line);
                if (conf != null)
                {
                    // update command mode control
                    mode.setSelection(conf.commandMode, true);
                }
                break;
            case BluetoothSerialService.MESSAGE_TYPE_INVALID_STRING_RECEIVED:
                terminal.append("[CHECKSUM FAILED] " + msg.obj + "\n");
                Toast.makeText(this, getResources().getString(R.string.toast_communication_error, msg.obj), Toast.LENGTH_SHORT).show();
                break;
            default:
        }

        return true;
    }

    private void updateSensorMenuItemStatus()
    {
        MenuItem item = menu.findItem(R.id.mi_sensor);
        Drawable icon = item.getIcon();

        if (sensorActive)
        {
            icon.setColorFilter(ContextCompat.getColor(this, R.color.activeIcon), PorterDuff.Mode.SRC_IN);
        }
        else
        {
            icon.clearColorFilter();
        }

    }

    /**
     * Request a configuration dump.
     */
    private void requestConfiguration()
    {
        if (btService != null && btService.isConnected())
        {
            btService.sendCommand(RagnettoConstants.COMMAND_REQUEST_CONFIGURATION);
        }
    }

}
