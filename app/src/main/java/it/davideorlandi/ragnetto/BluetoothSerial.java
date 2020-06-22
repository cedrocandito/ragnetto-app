package it.davideorlandi.ragnetto;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;

import java.util.List;

public class BluetoothSerial
{
    // UUID for CC2540 BLE services ??????????????
    public static final String BTLE_READ_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private Context context;


    public BluetoothSerial(Context context)
    {
        this.context = context;
    }


    public void antani()
    {
        adapter = BluetoothAdapter.getDefaultAdapter();

        Log.v("scan", "Scanning");
        scanner = adapter.getBluetoothLeScanner();
        scanner.startScan(new ScanCallback()
        {
            @Override
            public void onScanResult(int callbackType, ScanResult result)
            {
                Log.v("scan", "scan result " + result.toString());
                BluetoothDevice device = result.getDevice();
                Log.v("scan", "device: " + device);
                BluetoothGatt gatt = device.connectGatt(context, true, new BluetoothGattCallback()
                {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
                    {
                        Log.v("gatt", "conn.state change; status=" + status + ", newState=" + newState);
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status)
                    {
                        Log.v("gatt", "services discovered; status=" + status);
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
                    {
                        Log.v("gatt", "char.read " + characteristic + ", status=" + status);
                    }

                    @Override
                    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
                    {
                        Log.v("gatt", "char.write " + characteristic + ", status=" + status);
                    }

                    @Override
                    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
                    {
                        Log.v("gatt", "char.changed " + characteristic);
                    }

                    @Override
                    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
                    {
                        Log.v("gatt", "desc.read " + descriptor + ", status=" + status);
                    }

                    @Override
                    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
                    {
                        Log.v("gatt", "desc.write " + descriptor + ", status=" + status);
                    }
                });
                gatt.discoverServices();

            }

            @Override
            public void onScanFailed(int errorCode)
            {
                Log.v("scan", "Error " + errorCode);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results)
            {
                Log.v("batch scan", "scan result " + results.toString());
            }
        });
    }

}
