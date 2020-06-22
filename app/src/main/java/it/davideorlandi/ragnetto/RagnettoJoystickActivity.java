package it.davideorlandi.ragnetto;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


public class RagnettoJoystickActivity extends AppCompatActivity
{
    private static final String TAG = "RJA";
    private static final String BUNDLE_ID_SENSOR_ACTIVE = "sensorActive";
    private static final int JOYSTICK_POLLING_INTERVAL = 50;
    private static final String SWAP_CONTROLS_PREFERENCE_KEY = "invert_rotation_sidestep";

    private Menu menu;
    private JoystickView primaryJoystick;
    private JoystickView secondaryJoystick;
    private TextView txtForward;
    private TextView txtSidestep;
    private TextView txtRotation;
    private boolean swapControls;
    private boolean sensorActive = false;
    private Handler handler;

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

        if (savedInstanceState != null)
        {
            sensorActive = savedInstanceState.getBoolean(BUNDLE_ID_SENSOR_ACTIVE, false);

            Log.v(TAG, "Saved sensor state: " + (sensorActive ? "active" : "stopped"));
        }
    }

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

        return true;
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

                    txtForward.setText(String.format("%.0f %%", y * 100));
                    txtSidestep.setText(String.format("%.0f %%", x * 100));
                    txtRotation.setText(String.format("%.0f %%", r * 100));
                } finally
                {
                    handler.postDelayed(this, JOYSTICK_POLLING_INTERVAL);
                }


            }
        }, JOYSTICK_POLLING_INTERVAL);
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
     * Save state. Necessary because the activity will be recreated by the system when changing orientation.
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
        // ???????? TODO
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

    public void onMenuClickConnect(MenuItem item)
    {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.bt_not_available_title)
                    .setMessage(R.string.bt_not_available_text)
                    .setNeutralButton(R.string.bt_not_available_ok, null)
                    .show();
        }
        else
        {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || adapter.getBluetoothLeScanner() == null)
            {
                Log.wtf(TAG, "BluetoothAdapter or BluetoothLeScanner null");
            }
            else
            {
                if (!adapter.isEnabled())
                {
                    // bluetooth not enabled; ask to enable it
                    Intent bton = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(bton, 1);
                }
                else
                {
                    // BT present and enabled
                    BluetoothSerial bts = new BluetoothSerial(this);
                    bts.antani();

                    //?????? TODO
                    //updateConnectedStatus(true);
                }
            }
        }
    }

    public void onMenuClickDisconnect(MenuItem item)
    {
        //?????????? TODO
        //updateConnectedStatus(false);
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


    private void updateConnectedStatus(boolean connected)
    {
        //??????????????? TODO
        menu.findItem(R.id.mi_connect).setVisible(!connected);
    }
}
