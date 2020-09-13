package it.davideorlandi.ragnetto;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import it.davideorlandi.ragnetto.service.BluetoothSerialService;


public class RagnettoConfigActivity extends AppCompatActivity implements Handler.Callback
{
    private static final String TAG = "RagnetoConfActivity";

    private SeekBarAndValueView height_offset;
    private SeekBarAndValueView lift_height;
    private SeekBarAndValueView max_phase_duration;
    private SeekBarAndValueView leg_lift_duration;
    private SeekBarAndValueView leg_drop_duration;
    private SeekBarAndValueView[][] trims;

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
            // request configuration as soon as the service is bound
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
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(TAG, "Creating activity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ragnetto_config_activity);

        height_offset = findViewById(R.id.sb_height_offset);
        lift_height = findViewById(R.id.sb_lift_height);
        max_phase_duration = findViewById(R.id.sb_max_phase_duration);
        leg_lift_duration = findViewById(R.id.sb_leg_lift_duration);
        leg_drop_duration = findViewById(R.id.sb_leg_drop_duration);

        trims = new SeekBarAndValueView[RagnettoConstants.NUM_LEGS][RagnettoConstants.NUM_JOINTS];

        LinearLayout trimsLayout = findViewById(R.id.trims);
        for (int l = 0; l < RagnettoConstants.NUM_LEGS; l++)
        {
            for (int j = 0; j < RagnettoConstants.NUM_JOINTS; j++)
            {
                SeekBarAndValueView trim = new SeekBarAndValueView(this);
                trim.setId(View.generateViewId());
                trim.setMin(-40);
                trim.setMax(40);
                trim.setProgress(0);
                trim.setTitle(getResources().getString(R.string.config_trim_joint_title, l + 1, j + 1));
                trimsLayout.addView(trim);
                trims[l][j] = trim;
            }
        }

        serviceCommunicationHandler = new Handler(Looper.getMainLooper(), this);
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

        // request configuration when resuming
        requestConfiguration();
    }

    @Override
    protected void onPause()
    {
        Log.v(TAG, "onPause");
        super.onPause();
    }


    @Override
    public boolean handleMessage(@NonNull Message msg)
    {
        switch (msg.what)
        {
            case BluetoothSerialService.MESSAGE_TYPE_CONNECTED:
                Toast.makeText(this, R.string.toast_connected, Toast.LENGTH_SHORT).show();
                requestConfiguration();
                break;
            case BluetoothSerialService.MESSAGE_TYPE_DISCONNECTED:
                Toast.makeText(this, R.string.toast_disconnected, Toast.LENGTH_SHORT).show();
                finish();
                break;
            case BluetoothSerialService.MESSAGE_TYPE_UNABLE_TO_CONNECT:
                Toast.makeText(this, R.string.toast_unable_to_connected, Toast.LENGTH_LONG).show();
                break;
            case BluetoothSerialService.MESSAGE_TYPE_VALID_STRING_RECEIVED:
                String line = (String) msg.obj;
                RagnettoConfiguration conf = RagnettoConfiguration.parseLine(line);
                if (conf != null)
                {
                    // update controls
                    height_offset.setProgress(conf.heightOffset);
                    lift_height.setProgress(conf.liftHeight);
                    max_phase_duration.setProgress(conf.maxPhaseDuration);
                    leg_lift_duration.setProgress(conf.legLiftDurationPercent);
                    leg_drop_duration.setProgress(conf.legDropDurationPercent);
                    for (int l = 0; l < RagnettoConstants.NUM_LEGS; l++)
                    {
                        for (int j = 0; j < RagnettoConstants.NUM_JOINTS; j++)
                        {
                            trims[l][j].setProgress(conf.trim[l][j]);
                        }
                    }
                }
                break;
            case BluetoothSerialService.MESSAGE_TYPE_INVALID_STRING_RECEIVED:
                Toast.makeText(this, getResources().getString(R.string.toast_communication_error, msg.obj), Toast.LENGTH_SHORT).show();
            default:
                Log.w(TAG, "Received unknown message type: " + msg.what);
        }

        return true;
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
