package com.kimboflash;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Stub Wi-Fi serial service for K-Line adapter.
 */
public class WifiSerialService {
    private static final String TAG = "WifiSerialSvc";
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Wi-Fi connect failed", e);
            return false;
        }
    }

    public void write(byte[] data) throws Exception {
        if (out != null) out.write(data);
    }

    public void read(UsbSerialInterface.UsbReadCallback callback) {
        // stub: implement read handling from in stream
    }

    public void close() {
        try { if (socket != null) socket.close(); } catch (Exception e) { }
    }
}
