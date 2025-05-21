package com.kimboflash

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.kimboflash.CommService
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothService(private val context: Context) : CommService {
    companion object {
        private const val TAG     = "BluetoothService"
        private val     SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private var socket: BluetoothSocket? = null
    private var inStream: InputStream?  = null
    private var outStream: OutputStream? = null

    fun setupBluetooth() {
        Log.d(TAG, "setupBluetooth(): adapter enabled=${adapter?.isEnabled}")
    }

    fun hasRequiredPermissions(): Boolean = 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun isReady(): Boolean = socket?.isConnected == true

    override fun send(data: ByteArray) {
        if (!isReady()) return
        outStream?.write(data)
    }

    override fun receiveBytes(): ByteArray {
        if (!isReady()) return ByteArray(0)
        val buf = ByteArray(1024)
        val len = inStream?.read(buf) ?: 0
        return buf.copyOfRange(0, len)
    }

    fun findElmDevice(): BluetoothDevice? =
        adapter?.bondedDevices?.firstOrNull { it.name.contains("ELM", true) }

    fun connect(
        device: BluetoothDevice,
        onConnected: (() -> Unit)? = null,
        onError:     ((Exception) -> Unit)? = null
    ) {
        thread {
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter?.cancelDiscovery()
                socket!!.connect()
                inStream  = socket!!.inputStream
                outStream = socket!!.outputStream
                Log.i(TAG, "Connected to ${device.name}")
                onConnected?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "connect failed", e)
                onError?.invoke(e)
                socket = null
            }
        }
    }
}
