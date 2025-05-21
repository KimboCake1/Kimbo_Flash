package com.kimboflash;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

// Added import for UsbSerialInterface
import com.felhr.usbserial.UsbSerialInterface;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Stub Bluetooth SPP serial service for K-Line adapter.
 */
public class BluetoothSerialService {
    private static final String TAG = "BluetoothSerialSvc";
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;

    public boolean connect(String deviceName) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            // It's good practice to check if adapter is null (Bluetooth not supported/enabled)
            if (adapter == null) {
                Log.w(TAG, "BluetoothAdapter is null. Bluetooth might not be supported or enabled.");
                return false;
            }
            // It's also good practice to check if Bluetooth is enabled
            if (!adapter.isEnabled()) {
                Log.w(TAG, "Bluetooth is not enabled.");
                // Consider prompting the user to enable Bluetooth or handle this case appropriately.
                return false;
            }

            for (BluetoothDevice dev : adapter.getBondedDevices()) {
                // To avoid NullPointerException if dev.getName() is null
                if (dev.getName() != null && dev.getName().equals(deviceName)) {
                    socket = dev.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                    socket.connect(); // This is a blocking call, consider doing it on a background thread
                    in = socket.getInputStream();
                    out = socket.getOutputStream();
                    Log.i(TAG, "Connected to " + deviceName);
                    return true;
                }
            }
            Log.w(TAG, "Device not found or not bonded: " + deviceName);
        } catch (SecurityException se) {
            // Catch SecurityException separately if targeting Android 12+ due to new Bluetooth permissions
            Log.e(TAG, "BT connect failed - SecurityException. Check BLUETOOTH_CONNECT permission.", se);
        }
        catch (Exception e) { // Catch more specific exceptions like IOException
            Log.e(TAG, "BT connect failed for " + deviceName, e);
        }
        // Ensure resources are cleaned up if connection fails partially
        close(); // Close socket if connection failed after socket was created
        return false;
    }

    public void write(byte[] data) throws Exception { // Consider more specific Exception like IOException
        if (out != null) {
            out.write(data);
        } else {
            // Consider throwing an IllegalStateException or IOException if not connected
            Log.w(TAG, "Output stream is null. Cannot write data. Not connected?");
            throw new IOException("Bluetooth output stream is null. Not connected.");
        }
    }

    /**
     * Reads data from the Bluetooth input stream.
     * This method needs to be implemented to actually read data and pass it to the callback.
     * Reading from an InputStream is typically a blocking operation and should be done
     * on a separate thread.
     *
     * @param callback The callback to be invoked when data is received.
     */
    public void read(UsbSerialInterface.UsbReadCallback callback) {
        // stub: implement read handling from in stream
        // Example of how you might start a reading thread (simplified):
        if (in == null || callback == null) {
            Log.w(TAG, "InputStream or callback is null. Cannot start reading.");
            return;
        }

        new Thread(() -> {
            byte[] buffer = new byte[1024]; // Or a more appropriate buffer size
            int bytes;
            Log.d(TAG, "Bluetooth read thread started.");
            try {
                while (socket != null && socket.isConnected() && in != null) {
                    bytes = in.read(buffer); // Blocking call
                    if (bytes > 0) {
                        final byte[] dataReceived = new byte[bytes];
                        System.arraycopy(buffer, 0, dataReceived, 0, bytes);
                        // The UsbReadCallback is likely designed for USB data,
                        // but can be repurposed here if the signature matches the need.
                        // Ensure this callback is appropriate for Bluetooth data handling.
                        callback.onReceivedData(dataReceived);
                    } else if (bytes == -1) {
                        // End of stream, connection closed by remote device
                        Log.i(TAG, "Bluetooth input stream ended (read -1). Connection closed by remote.");
                        break;
                    }
                }
            } catch (IOException e) {
                // Check if the exception is due to socket being closed intentionally
                if ("socket closed".equalsIgnoreCase(e.getMessage()) || "Software caused connection abort".equalsIgnoreCase(e.getMessage())) {
                    Log.i(TAG, "Bluetooth read thread exiting, socket closed: " + e.getMessage());
                } else {
                    Log.e(TAG, "Bluetooth read failed", e);
                }
                // Optionally, you might want to notify about the error via the callback or another mechanism
            } finally {
                Log.d(TAG, "Bluetooth read thread finished.");
                // Consider closing the connection or notifying the main component if the read loop exits unexpectedly
            }
        }).start();
        Log.i(TAG, "Placeholder for read implementation. Data should be read from 'in' and passed to callback.");
        // Note: The UsbSerialInterface.UsbReadCallback might not be the most semantically correct
        // callback for a Bluetooth service. You might consider defining your own callback interface
        // specific to Bluetooth data if this class is meant to be a generic Bluetooth serial service.
        // However, if it's specifically for interfacing with parts of your app that expect UsbReadCallback,
        // then using it is fine, just be mindful of the context.
    }

    public void close() {
        Log.d(TAG, "Closing Bluetooth connection.");
        try {
            if (in != null) {
                in.close();
                in = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to close input stream", e);
        }
        try {
            if (out != null) {
                out.close();
                out = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to close output stream", e);
        }
        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to close socket", e);
        }
        Log.i(TAG, "Bluetooth connection closed.");
    }
}