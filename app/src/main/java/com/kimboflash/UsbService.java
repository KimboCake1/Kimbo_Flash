// src/main/java/com/kimboflash/UsbService.java
package com.kimboflash;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

// Removed explicit import for java.io.IOException as it's not directly caught for open/close
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UsbService extends Service {
    /**
     * Returns the current UsbSerialDevice port.
     */
    public UsbSerialDevice getSerialPort() {
        return this.serialPort; // adjust field name if needed
    }

    public static final String ACTION_USB_PERMISSION = "com.kimboflash.USB_PERMISSION";

    public static final int MESSAGE_FROM_SERVICE = 0;
    public static final int MESSAGE_FROM_SERIAL_PORT = 1;
    private static final String TAG = "UsbService";
    private static final int DEFAULT_BAUD = 10400; // Standard K-Line baud rate

    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialPort;
    private boolean serialPortConnected = false;

    private final IBinder binder = new UsbBinder();
    private volatile Handler mHandler;

    private final ConcurrentLinkedQueue<byte[]> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isProcessingCommands = false;

    private KLineManager kLineManager;

    @Override
    public void onCreate() {
        super.onCreate();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        kLineManager = new KLineManager(this);
        Log.i(TAG, "UsbService created.");
    }

    public class UsbBinder extends Binder {
        public UsbService getService() {
            return UsbService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind called");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind called");
        return super.onUnbind(intent);
    }

    public void setHandler(Handler handler) {
        this.mHandler = handler;
        if (handler == null) {
            Log.d(TAG, "UI Handler has been unset.");
        } else {
            Log.d(TAG, "UI Handler has been set.");
        }
    }

    public void findSerialPortDevice() {
        if (usbManager == null) {
            sendServiceMessage("UsbManager not available.");
            Log.w(TAG, "UsbManager is null in findSerialPortDevice.");
            return;
        }
        sendServiceMessage("Searching for USB OBD adapter...");
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (usbDevices.isEmpty()) {
            sendServiceMessage("No USB devices found.");
            return;
        }

        boolean foundAdapter = false;
        for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
            UsbDevice d = entry.getValue();
            int vid = d.getVendorId();
            if (vid == 0x0403 || vid == 0x10C4 || vid == 0x1A86) {
                this.device = d;
                sendServiceMessage("Compatible adapter found: VID=0x" + Integer.toHexString(vid) +
                        ", PID=0x" + Integer.toHexString(d.getProductId()));
                requestUserPermission();
                foundAdapter = true;
                break;
            }
        }
        if (!foundAdapter) {
            sendServiceMessage("No compatible OBD adapter found. (Supported VIDs: FTDI, SiLabs, CH340)");
        }
    }

    private void requestUserPermission() {
        if (device == null) {
            sendServiceMessage("No device selected to request permission for.");
            return;
        }
        if (usbManager == null) {
            sendServiceMessage("UsbManager not available for permission request.");
            Log.e(TAG, "UsbManager null in requestUserPermission");
            return;
        }

        if (usbManager.hasPermission(device)) {
            sendServiceMessage("USB permission already granted.");
            connectToDevice();
        } else {
            sendServiceMessage("Requesting USB permission...");
            PendingIntent pi = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_MUTABLE
            );
            usbManager.requestPermission(device, pi);
        }
    }

    public synchronized boolean connectToDevice() {
        if (device == null) {
            sendServiceMessage("Connection failed: No USB device specified.");
            Log.w(TAG, "connectToDevice: No USB device specified.");
            return false;
        }
        if (serialPortConnected && serialPort != null) {
            sendServiceMessage("Already connected to serial port.");
            Log.i(TAG, "connectToDevice: Already connected to " + device.getDeviceName());
            return true;
        }
        if (usbManager == null) {
            sendServiceMessage("Connection failed: UsbManager not available.");
            Log.e(TAG, "connectToDevice: UsbManager is null.");
            return false;
        }

        Log.d(TAG, "connectToDevice: Attempting new connection. Ensuring old one is closed first.");
        disconnectFromDeviceInternal();

        sendServiceMessage("Attempting to open USB device: " + device.getDeviceName());
        Log.i(TAG, "Opening USB device: " + device.getDeviceName());
        connection = usbManager.openDevice(device);
        if (connection == null) {
            sendServiceMessage("Connection failed: Could not open USB device. Permission issue or device busy?");
            Log.w(TAG, "connectToDevice: usbManager.openDevice() returned null for " + device.getDeviceName());
            return false;
        }
        Log.i(TAG, "USB device opened successfully. Creating serial port...");

        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection, -1);
        if (serialPort == null) {
            sendServiceMessage("Connection failed: No serial driver for this device or device not supported by library.");
            Log.w(TAG, "connectToDevice: UsbSerialDevice.createUsbSerialDevice() returned null.");
            disconnectFromDeviceInternal();
            return false;
        }
        Log.i(TAG, "UsbSerialDevice created for " + device.getDeviceName());

        sendServiceMessage("Serial port object created. Opening physical port...");
        if (!serialPort.open()) { // Check return value
            sendServiceMessage("Connection failed: Could not open serial port (serialPort.open() returned false).");
            Log.w(TAG, "connectToDevice: serialPort.open() returned false.");
            disconnectFromDeviceInternal();
            return false;
        }
        // No try-catch for IOException as serialPort.open() does not declare it.

        Log.i(TAG, "Serial port opened. Configuring parameters...");
        serialPort.setBaudRate(DEFAULT_BAUD);
        serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
        serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
        serialPort.setParity(UsbSerialInterface.PARITY_NONE);
        serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
        serialPort.read(mCallback);

        serialPortConnected = true;
        sendServiceMessage("Serial port connected and configured successfully.");
        Log.i(TAG, "Serial port fully connected and configured for " + device.getDeviceName());

        if (!isProcessingCommands && !commandQueue.isEmpty()) {
            isProcessingCommands = true;
            processNextCommand();
        }
        return true;
    }

    public synchronized void disconnectFromDevice() {
        sendServiceMessage("Disconnecting from device...");
        Log.i(TAG, "disconnectFromDevice() called publicly.");
        disconnectFromDeviceInternal();
        device = null;
        sendServiceMessage("Disconnected.");
        Log.i(TAG, "Finished public disconnectFromDevice().");
    }

    private synchronized void disconnectFromDeviceInternal() {
        Log.d(TAG, "disconnectFromDeviceInternal() called.");
        isProcessingCommands = false;
        commandQueue.clear();

        if (serialPort != null) {
            try {
                Log.d(TAG, "Closing serial port object...");
                serialPort.close();
                Log.i(TAG, "Serial port object closed.");
            } catch (Exception e) { // Catch generic Exception
                Log.e(TAG, "Error closing serial port object", e);
            } finally {
                serialPort = null;
            }
        }
        serialPortConnected = false;

        if (connection != null) {
            try {
                Log.d(TAG, "Closing UsbDeviceConnection object...");
                connection.close();
                Log.i(TAG, "UsbDeviceConnection object closed.");
            } catch (Exception e) {
                Log.e(TAG, "Error closing USB device connection object", e);
            } finally {
                connection = null;
            }
        }
        Log.d(TAG, "Finished disconnectFromDeviceInternal(). serialPortConnected=" + serialPortConnected);
    }

    public synchronized void write(byte[] data) {
        if (!isConnected()) {
            sendServiceMessage("Cannot write: Serial port not connected.");
            Log.w(TAG, "Attempted to write when serial port not connected.");
            return;
        }
        Log.d(TAG, "Writing data: " + bytesToHex(data));
        serialPort.write(data);
    }

    public void queueCommand(byte[] cmd) {
        if (cmd == null || cmd.length == 0) {
            Log.w(TAG, "Attempted to queue null or empty command.");
            return;
        }
        commandQueue.add(cmd);
        Log.d(TAG, "Command queued: " + bytesToHex(cmd) + " (Queue size: " + commandQueue.size() + ")");
        if (!isProcessingCommands && isConnected()) {
            isProcessingCommands = true;
            processNextCommand();
        } else if (!isConnected()) {
            sendServiceMessage("Command queued, but port not connected. Will send upon connection.");
        }
    }

    private synchronized void processNextCommand() {
        if (!isConnected()) {
            isProcessingCommands = false;
            Log.w(TAG, "Cannot process command: Serial port not connected.");
            return;
        }
        byte[] cmd = commandQueue.poll();
        if (cmd != null) {
            Log.i(TAG, "Processing next command: " + bytesToHex(cmd));
            write(cmd);
        } else {
            isProcessingCommands = false;
            Log.d(TAG, "Command queue empty.");
        }
    }

    private final UsbSerialInterface.UsbReadCallback mCallback = data -> {
        if (data != null && data.length > 0) {
            Log.d(TAG, "Data received: " + bytesToHex(data) + " (Length: " + data.length + ")");
            if (mHandler != null) {
                Message msg = mHandler.obtainMessage(MESSAGE_FROM_SERIAL_PORT);
                Bundle b = new Bundle();
                b.putByteArray("data", data);
                msg.setData(b);
                mHandler.sendMessage(msg);
            } else {
                Log.w(TAG, "mHandler is null, cannot send received data to UI.");
            }
        } else {
            Log.d(TAG, "Empty or null data received from serial port's read callback.");
        }
        if (isProcessingCommands) {
            processNextCommand();
        }
    };

    public synchronized boolean setBaudRate(int baudRate) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot set baud rate: Serial port not connected.");
            sendServiceMessage("Cannot set baud rate: Serial port not connected.");
            return false;
        }
        try {
            serialPort.setBaudRate(baudRate);
            Log.i(TAG, "Attempted to set baud rate to " + baudRate);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting baud rate to " + baudRate, e);
            sendServiceMessage("Error setting baud rate: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean lineStateLow() {
        if (!isConnected()) {
            Log.w(TAG, "Cannot set line state: Serial port not connected.");
            return false;
        }
        try {
            // TODO: serialPort.setDTR(true_or_false); or serialPort.setRTS(true_or_false);
            Log.i(TAG, "K-Line state set to LOW (Implement actual hardware control).");
            sendServiceMessage("K-Line state set to LOW (Implement actual hardware control).");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting K-Line state to LOW", e);
            return false;
        }
    }

    public synchronized boolean lineStateHigh() {
        if (!isConnected()) {
            Log.w(TAG, "Cannot set line state: Serial port not connected.");
            return false;
        }
        try {
            // TODO: serialPort.setDTR(true_or_false); or serialPort.setRTS(true_or_false);
            Log.i(TAG, "K-Line state set to HIGH (Implement actual hardware control).");
            sendServiceMessage("K-Line state set to HIGH (Implement actual hardware control).");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error setting K-Line state to HIGH", e);
            return false;
        }
    }

    public void perform5BaudInit(byte address) {
        if (kLineManager != null) {
            kLineManager.perform5BaudInit(address);
        } else {
            sendServiceMessage("KLineManager not initialized. Cannot perform 5-baud init.");
            Log.w(TAG, "perform5BaudInit: KLineManager is null.");
        }
    }

    public void performFastInit() {
        if (kLineManager != null) {
            kLineManager.performFastInit();
        } else {
            sendServiceMessage("KLineManager not initialized. Cannot perform fast init.");
            Log.w(TAG, "performFastInit: KLineManager is null.");
        }
    }

    public boolean isConnected() {
        return connection != null && serialPort != null && serialPortConnected;
    }

    @Deprecated
    public boolean isSerialPortOpen() {
        return serialPort != null && serialPortConnected;
    }

    public void sendServiceMessage(String message) {
        Log.d(TAG, "Service Message to UI: " + message);
        if (mHandler != null) {
            Message msg = mHandler.obtainMessage(MESSAGE_FROM_SERVICE);
            Bundle b = new Bundle();
            b.putString("msg", message);
            msg.setData(b);
            mHandler.sendMessage(msg);
        } else {
            Log.w(TAG, "Cannot send service message: mHandler is null. Message was: " + message);
        }
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        if (bytes.length == 0) return "";
        char[] hexChars = new char[bytes.length * 3 - 1];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = HEX_ARRAY[v >>> 4];
            hexChars[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
            if (j < bytes.length - 1) {
                hexChars[j * 3 + 2] = ' ';
            }
        }
        return new String(hexChars);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "UsbService destroying...");
        disconnectFromDeviceInternal();
        if (kLineManager != null) {
            // TODO: Implement kLineManager.destroy() or kLineManager.cleanup()
            // kLineManager.destroy();
            Log.d(TAG, "KLineManager cleanup would happen here if implemented.");
        }
        mHandler = null;
        super.onDestroy();
        Log.i(TAG, "UsbService destroyed.");
    }
}