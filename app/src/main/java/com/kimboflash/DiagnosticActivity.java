package com.kimboflash;
// AndroidX and Android framework classes
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import android.widget.Button;
import android.widget.TextView;
// Your app classes
import com.kimboflash.UsbService;
import com.kimboflash.KLineManager;
import com.kimboflash.KWP2000Manager;
import com.kimboflash.DTCAdapter;
import com.kimboflash.DTC;
// Java standard library
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class DiagnosticActivity extends AppCompatActivity {

    private enum ConnectionType {USB, BLUETOOTH, WIFI, NONE}

    private static final String TAG = "DiagnosticActivity";

    // Define a constant for K-Line initialization success message
    // Ensure KLineManager (or UsbService) uses this exact string.
    private static final String KLINE_INIT_SUCCESS_MSG = "K-Line initialization successful";
    // Define a constant for the action to perform after K-Line init
    private static final int POST_KLINE_INIT_ACTION_NONE = 0;
    private static final int POST_KLINE_INIT_ACTION_READ_DTCS = 1;
    private static final int POST_KLINE_INIT_ACTION_CLEAR_DTCS = 2;

    private UsbService usbService;
    private KLineManager kLine;
    private KWP2000Manager kwp;
    private boolean bound = false;
    private boolean kLineInitialized = false;
    private int postKLineInitAction = POST_KLINE_INIT_ACTION_NONE; // For UX improvement

    private TextView status;
    private Button btnConnect, btnRead, btnClear;
    private DTCAdapter adapter;
    private List<DTC> dtcList = new ArrayList<>();

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            usbService = ((UsbService.UsbBinder) binder).getService();
            usbService.setHandler(mHandler); // Set the handler for UsbService to communicate back

            // Initialize KLineManager and KWP2000Manager with the UsbService instance
            kLine = new KLineManager(usbService);
            kwp = new KWP2000Manager(usbService);

            bound = true;
            updateStatus("Service connected. Ready.");
            updateButtonStates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            kLineInitialized = false; // Reset K-Line status on disconnect
            updateStatus("Service disconnected.");
            updateButtonStates();
        }
    };

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERVICE:
                    String serviceMsg = msg.getData().getString("msg");
                    updateStatus(serviceMsg);
                    if (KLINE_INIT_SUCCESS_MSG.equals(serviceMsg)) {
                        kLineInitialized = true;
                        updateStatus("K-Line initialized. Ready for commands.");
                        updateButtonStates();
                        // Optional: Automatically perform action after K-Line init
                        performPostKLineInitAction();
                    } else if (serviceMsg != null && serviceMsg.contains("K-Line initialization failed")) {
                        kLineInitialized = false;
                        postKLineInitAction = POST_KLINE_INIT_ACTION_NONE; // Reset pending action
                        updateStatus("K-Line initialization failed. Please retry.");
                        updateButtonStates();
                    }
                    break;
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                    byte[] data = msg.getData().getByteArray("data");
                    processReceivedData(data);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Adapter detection
        ConnectionType detected = detectAdapter();
        if (detected == ConnectionType.NONE) {
            // …
        }

        if (conn == ConnectionType.NONE) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("No Adapter Found")
                    .setMessage("Connect USB, Bluetooth, or Wi-Fi adapter.")
                    .setPositiveButton("OK", (d, w) -> finish())
                    .show();
            return;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostic);

        status = findViewById(R.id.status);
        btnConnect = findViewById(R.id.btnConnect);
        btnRead = findViewById(R.id.btnRead);
        btnClear = findViewById(R.id.btnClear);

        RecyclerView rv = findViewById(R.id.recycler);
        adapter = new DTCAdapter(dtcList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnConnect.setOnClickListener(v -> {
            if (bound && usbService != null) {
                if (usbService.isConnected()) {
                    // Optionally disconnect or show status
                    updateStatus("Already connected. Use Reconnect if needed or handle as appropriate.");
                    // usbService.disconnect(); // Example if you want connect to also act as disconnect
                } else {
                    updateStatus("Searching for USB device...");
                    usbService.findSerialPortDevice();
                }
            } else if (!bound) {
                updateStatus("Service not bound. Please wait or restart app.");
            }
        });

        btnRead.setOnClickListener(v -> {
            if (bound && usbService != null && usbService.isConnected()) {
                if (!kLineInitialized) {
                    updateStatus("Initializing K-Line for Read DTCs...");
                    // Store the action to perform after successful init
                    postKLineInitAction = POST_KLINE_INIT_ACTION_READ_DTCS;
                    // Ensure KWP2000Manager.ECU_ADDRESS is defined and correct
                    // e.g., public static final byte ECU_ADDRESS = (byte) 0x12; in KWP2000Manager
                    kLine.perform5BaudInit(KWP2000Manager.ECU_ADDRESS);
                } else {
                    updateStatus("Reading DTCs...");
                    try {
                        kwp.readDTCs();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                updateStatus("Not connected to USB device or service unbound.");
            }
        });

        btnClear.setOnClickListener(v -> {
            if (bound && usbService != null && usbService.isConnected()) {
                if (!kLineInitialized) {
                    updateStatus("Initializing K-Line for Clear DTCs...");
                    // Store the action to perform after successful init
                    postKLineInitAction = POST_KLINE_INIT_ACTION_CLEAR_DTCS;
                    kLine.perform5BaudInit(KWP2000Manager.ECU_ADDRESS);
                } else {
                    showClearDtcConfirmationDialog();
                }
            } else {
                updateStatus("Not connected to USB device or service unbound.");
            }
        });

        updateButtonStates();
        // Bind to UsbService
        Intent intent = new Intent(this, UsbService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
    }

    private void performPostKLineInitAction() {
        switch (postKLineInitAction) {
            case POST_KLINE_INIT_ACTION_READ_DTCS:
                updateStatus("K-Line Ready. Reading DTCs...");
                try {
                    kwp.readDTCs();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case POST_KLINE_INIT_ACTION_CLEAR_DTCS:
                updateStatus("K-Line Ready. Clearing DTCs...");
                showClearDtcConfirmationDialog();
                break;
        }
        postKLineInitAction = POST_KLINE_INIT_ACTION_NONE; // Reset after performing
    }

    private void showClearDtcConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear DTCs")
                .setMessage("Are you sure you want to clear Diagnostic Trouble Codes?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    updateStatus("Clearing DTCs...");
                    try {
                        kwp.clearDTCs();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            unbindService(conn);
            bound = false;
        }
    }

    private void updateStatus(String message) {
        if (message != null) {
            Log.d(TAG, message);
            if (status != null) {
                status.setText(message);
            }
        }
    }

    private void updateButtonStates() {
        boolean serviceConnectedAndDeviceReady = bound && usbService != null && usbService.isConnected();

        btnConnect.setEnabled(bound); // Can always try to connect/reconnect if service is bound
        btnConnect.setText(serviceConnectedAndDeviceReady ? "Reconnect" : "Connect");

        // Read and Clear buttons should only be enabled if connected and K-Line can be/is initialized
        btnRead.setEnabled(serviceConnectedAndDeviceReady);
        btnClear.setEnabled(serviceConnectedAndDeviceReady && kLineInitialized); // Can only clear if K-Line is already confirmed
    }

    private void processReceivedData(byte[] data) {
        if (data == null || data.length < 1) { // Basic check
            Log.w(TAG, "Received null or empty data from serial port.");
            return;
        }

        // Log raw data for debugging
        // Log.d(TAG, "Raw Data Received: " + UsbService.bytesToHex(data)); // Assuming UsbService has bytesToHex

        // More robust check for KWP2000 DTC Response (ISO 14230-3)
        // A typical positive response to "Read DTCs By Status" (SID 0x18) is SID 0x58.
        // Format: [FMT] [TGT] [SRC] [LEN] [SID_RESP] [DTC_COUNT_OR_OTHER_PARAMS...] [DTCs...] [CS]
        // Assuming your KWP2000Manager constants are correct.
        // The SID is usually the 4th or 5th byte depending on header format.
        // If data[3] is length, then data[4] is SID.
        // Let's assume a common structure where SID is at index 4 (after FMT, TGT, SRC, LEN)
        // This needs to align with your KWP2000Manager.makeMsg() and actual ECU response.

        // This check needs to be specific to your ECU's KWP2000 response format.
        // For example, if your makeMsg produces a header of 4 bytes before SID:
        // Byte 0: Format (e.g. 0x80)
        // Byte 1: Target
        // Byte 2: Source
        // Byte 3: Length of (SID + Data)
        // Then, in the response, assuming similar header:
        // Byte 0: Format (e.g. 0x80 or echo of request's source becoming target)
        // Byte 1: Target (Tester)
        // Byte 2: Source (ECU)
        // Byte 3: Length
        // Byte 4: Response SID (e.g., 0x18 + 0x40 = 0x58)

        // The original check data[3] might be if there's no explicit format/target/source in the response prefix being checked
        // or if it refers to a specific known offset. Let's be cautious.
        // If KWP2000Manager.POSITIVE_RESPONSE_OFFSET is 0x40, and READ_DIAGNOSTIC_TROUBLE_CODES is 0x18, then expected SID is 0x58.

        // Find the actual SID in the response. This depends heavily on your KWP2000 message structure.
        // For now, let's assume the previous logic for data[3] was based on a simplified structure
        // or a known offset for the response SID after some initial bytes.
        // This is a critical part to get right based on your ECU.

        byte responseSid = -1;
        if (data.length >= 5) { // Assuming at least Header (4 bytes) + SID (1 byte)
            // This is an assumption that the SID is at index 4 of the KWP2000 frame
            responseSid = data[4];
        } else if (data.length >= 4 && (data[0] & 0xC0) == 0x80) {
            // Alternative check if data[3] was SID for some reason (e.g. no target/source in simple response)
            // This is less likely for full KWP2000 responses. The original check was on data[3].
            // Let's stick to the previous assumption for now, but highlight it needs verification.
            // responseSid = data[3]; // Previous logic used this
        }


        // Let's use a more explicit check for the positive response SID
        if (kwp != null && responseSid == (KWP2000Manager.READ_DIAGNOSTIC_TROUBLE_CODES + KWP2000Manager.POSITIVE_RESPONSE_OFFSET)) {
            updateStatus("DTC Response Received. Parsing...");
            List<String> codes = kwp.parseDTCResponse(data); // kwp.parseDTCResponse should handle the full frame
            if (codes != null && !codes.isEmpty()) {
                dtcList.clear(); // Clear previous DTCs
                for (String c : codes) {
                    dtcList.add(new DTC(c, getDTCDescription(c), new Date()));
                }
                runOnUiThread(() -> {
                    adapter.setItems(new ArrayList<>(dtcList)); // Update adapter with new list
                    updateStatus("Found " + dtcList.size() + " DTC(s).");
                });
            } else if (codes != null) { // codes is not null but empty
                runOnUiThread(() -> {
                    dtcList.clear();
                    adapter.setItems(new ArrayList<>(dtcList));
                    updateStatus("No DTCs reported by ECU.");
                });
            } else { // codes is null (parsing failed)
                updateStatus("Failed to parse DTC response.");
            }
        } else if (responseSid != -1) {
            // Handle other KWP2000 responses or negative responses
            updateStatus("Received KWP2000 response (SID: " + String.format("%02X", responseSid) + "). Not a DTC list or unexpected.");
            // Add more logic here to parse negative responses (e.g., if SID is 0x7F)
        } else if (data.length > 0) {
            updateStatus("Received data, but could not identify as DTC response. Length: " + data.length);
        }
    }

    private String getDTCDescription(String code) {
        // In a real app, look up the code in a database or resource file.
        // Example: P0100 -> Mass or Volume Air Flow Circuit Malfunction
        return "Description for " + code;
    }

    private ConnectionType detectAdapter() {
        // 1) USB
        if (usbService != null && usbService.getSerialPort() != null && usbService.isConnected()) {
            return ConnectionType.USB;
        }

        // 2) Bluetooth
        BluetoothAdapter blue = BluetoothAdapter.getDefaultAdapter();
        if (blue != null && blue.isEnabled()) {
            for (BluetoothDevice dev : blue.getBondedDevices()) {
                // Consider making "MyKwpAdapter" a constant or more robust check
                if ("MyKwpAdapter".equals(dev.getName())) { // Example name
                    // You might need to attempt a connection here to be sure
                    return ConnectionType.BLUETOOTH;
                }
            }
        }

        // 3) Wi-Fi
        try (Socket s = new Socket()) {
            // Make IP and Port configurable or constants
            s.connect(new InetSocketAddress("192.168.0.10", 35000), 200); // 200ms timeout
            return ConnectionType.WIFI;
        } catch (IOException ignored) {
            // Log the exception for debugging if needed
        }

        return ConnectionType.NONE;
    }
}