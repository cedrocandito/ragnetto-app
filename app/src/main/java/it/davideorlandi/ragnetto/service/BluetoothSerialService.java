package it.davideorlandi.ragnetto.service;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.UUID;

import it.davideorlandi.ragnetto.R;
import it.davideorlandi.ragnetto.RagnettoJoystickActivity;

public class BluetoothSerialService extends Service implements Runnable
{
    /**
     * Mesage (from service to activity) type: bluetooth connected.
     */
    public static final int MESSAGE_TYPE_CONNECTED = 1;

    /**
     * Mesage (from service to activity) type: bluetooth disconnected.
     */
    public static final int MESSAGE_TYPE_DISCONNECTED = 2;

    /**
     * Mesage (from service to activity) type: error while trying to connect.
     */
    public static final int MESSAGE_TYPE_UNABLE_TO_CONNECT = 3;

    /**
     * Mesage (from service to activity) type: valid (checksum ok) string received.
     */
    public static final int MESSAGE_TYPE_VALID_STRING_RECEIVED = 10;

    /**
     * Mesage (from service to activity) type: invalid (checksum failed) string received.
     */
    public static final int MESSAGE_TYPE_INVALID_STRING_RECEIVED = 11;


    // standard bluetooth serial port UUID
    private static final UUID BT_SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final String CHARSET = "iso-8859-1";

    private static final String NOTIFICATION_CHANNEL_ID = "RagnettoConnected";

    private static final int NOTIFICATION_ID = 5390663; // no real meaning

    private static final String TAG = "BluetoothSerialService";


    private BluetoothAdapter adapter;
    private BluetoothSocket socket = null;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;
    private PrintWriter writer = null;
    private BufferedReader reader = null;
    private boolean connected = false;
    private BluetoothDevice device;
    private Thread thread;
    private Handler communicationHandler;

    private RagnettoBinder binder = new RagnettoBinder();

    public BluetoothSerialService()
    {
        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.v(TAG, "onBind");
        return binder;
    }

    @Override
    public void onRebind(Intent intent)
    {
        Log.v(TAG, "onRebind");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        Log.v(TAG, "onUnbind");
        this.communicationHandler = null;
        return super.onUnbind(intent);
    }


    @Override
    public void onCreate()
    {
        Log.v(TAG, "onCreate");
        super.onCreate();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent)
    {
        Log.v(TAG, "onTaskRemoved; rootIntent=" + rootIntent);
        super.onTaskRemoved(rootIntent);
        // TODO ?????????
    }

    @Override
    public void onDestroy()
    {
        Log.v(TAG, "onDestroy");
        super.onDestroy();
        disconnect();
    }


    @Override
    public void run()
    {
        Log.d(TAG, "Running background thread");
        try
        {
            // if discovery is in progress it will slow down the connection
            adapter.cancelDiscovery();

            Log.d(TAG, "Creating socket");
            socket = device.createRfcommSocketToServiceRecord(BT_SERIAL_UUID);

            Log.d(TAG, "Connecting");
            socket.connect();
            Log.d(TAG, "Getting streams");
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(outputStream, CHARSET));
            reader = new BufferedReader(new InputStreamReader(inputStream, CHARSET));
            Log.d(TAG, "Connected = true");
            connected = true;

            Intent intent = new Intent(this, RagnettoJoystickActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            /* apparently, CATEGORY_LANCHER is needed to allow the notification to just bring up
            the activity instead of creating a new one over the first. */
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.notification_title))
                    .setContentText(getString(R.string.notification_text))
                    .setLocalOnly(true)
                    .setContentIntent(pendingIntent);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.notify(NOTIFICATION_ID, builder.build());

            sendMessageToActivity(MESSAGE_TYPE_CONNECTED, null);

        } catch (IOException ioe)
        {
            Log.e(TAG, "Error connecting to bluetooth device", ioe);
            if (socket != null)
            {
                disconnect();
            }
            sendMessageToActivity(MESSAGE_TYPE_UNABLE_TO_CONNECT, ioe.getMessage());

            return;
        }

        try
        {
            Log.d(TAG, "Starting read loop");
            while (true)
            {
                String line = reader.readLine();
                String checked = verifyChecksum(line);
                if (checked != null)
                {
                    Log.d(TAG, "Received verified line: " + checked);
                    sendMessageToActivity(MESSAGE_TYPE_INVALID_STRING_RECEIVED, checked);
                }
                else
                {
                    sendMessageToActivity(MESSAGE_TYPE_INVALID_STRING_RECEIVED, line);
                    Log.d(TAG, "Received NOT verified line: " + line);
                }
            }
        } catch (IOException ioe)
        {
            Log.i(TAG, "Exception caught, service thread is terminating", ioe);
            disconnect();
            Log.d(TAG, "Service stopping itself");
            stopSelf();
        }

        Log.v(TAG, "Canceling notification");

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    public void connect(BluetoothDevice device)
    {
        Log.v(TAG, "connect: " + device);

        if (connected)
            throw new IllegalStateException("Already connected");

        this.device = device;
        thread = new Thread(this);
        thread.start();
    }

    public void disconnect()
    {
        Log.v(TAG, "disconnect: current connected state = " + connected);
        if (connected)
        {
            closeForcefully(socket, "the bluetooth socket");
            closeForcefully(writer, "the writer and the outputstream");
            closeForcefully(reader, "the reader and the inputstream");
            socket = null;
            inputStream = null;
            outputStream = null;
            writer = null;
            reader = null;
            connected = false;

            sendMessageToActivity(MESSAGE_TYPE_DISCONNECTED, null);
        }
    }

    public void sendCommand(String command)
    {
        writer.println(command);
        writer.flush();
    }

    public boolean isConnected()
    {
        return connected;
    }

    private void closeForcefully(Closeable closeable, String what)
    {
        try
        {
            if (closeable != null)
            {
                closeable.close();
            }
        } catch (IOException ioe)
        {
            Log.wtf(TAG, "Unable to close " + what, ioe);
        }
    }

    /**
     * Send a message to the acticity.
     *
     * @param type    type of message. See constants.
     * @param content content of the message (string). Only for some types.
     */
    private void sendMessageToActivity(int type, String content)
    {
        if (communicationHandler == null)
        {
            Log.w(TAG, String.format("Cannot send message %d, string \"%s\" to activity: communication handler is null", type, content));
        }
        else
        {
            Message msg = communicationHandler.obtainMessage(type, content);
            communicationHandler.sendMessage(msg);
        }
    }

    /**
     * f the string ends with "#xxxx#" (where xxxx is a 4-digit hex number) extracts this xxxx number and compares
     * it with the checksum of the previous characters (the part before #xxxx#). If they match, the
     * string up to the first "#" (excluded) is returned. If the don't match of if the string
     * didn't end with "#xxxx#", null is returned.
     *
     * @param s source string.
     * @return verified string or null if no checksum or invalid checksum.
     */
    private String verifyChecksum(String s)
    {
        Long foundChecksum = extractChecksum(s);
        if (foundChecksum == null)
        {
            return null;
        }

        String effectiveString = s.substring(0, s.length() - 6);
        if (foundChecksum == calculateChecksum(effectiveString))
        {
            return effectiveString;
        }
        else
        {
            return null;
        }

    }

    /**
     * Calculates the checksum of a string.
     *
     * @param s source string.
     * @return calculated checksum.
     */
    private long calculateChecksum(String s)
    {
        try
        {
            byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
            long checksum = 0;
            for (byte b : bytes)
            {
                int unsignedb = ((int) b) & 0xFF;
                checksum += unsignedb;
            }
            return checksum;
        } catch (UnsupportedCharsetException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * If the string ends with "#xxxx#" (where xxxx is a 4-digit hex number) returns the content of xxxx as a long.
     * If it doesn't it returns null.
     *
     * @param s source string.
     * @return extracted checksum or null.
     */
    private Long extractChecksum(String s)
    {
        if (s.matches(".*#[0-9a-fA-F]{4}#$"))
        {
            int l = s.length();
            String hexString = s.substring(l - 5, l - 1);
            return Long.parseLong(hexString, 16);
        }
        else
        {
            return null;
        }
    }

    public class RagnettoBinder extends Binder
    {
        public BluetoothSerialService getService(Handler handlerForCommunication)
        {
            // set handler for communicating back to the activity
            BluetoothSerialService.this.communicationHandler = handlerForCommunication;
            // Return this instance of LocalService so clients can call public methods
            return BluetoothSerialService.this;
        }
    }

}
