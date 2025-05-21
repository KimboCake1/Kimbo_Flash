package com.kimboflash

import java.io.File
import kotlin.concurrent.thread

/**
 * Handles ECU communication over Bluetooth or USB and
 * implements a basic DS2/KWP2000 flash sequence.
 */
class EcuFlashService(
    private val btService: CommService,
    private val usbService: CommService
) {
    enum class CommType { BLUETOOTH, USB }
    var currentCommType = CommType.BLUETOOTH
        private set

    fun useUsbCommunication()     { currentCommType = CommType.USB }
    fun useBluetoothCommunication() { currentCommType = CommType.BLUETOOTH }

    /**
     * Flash the .bin file at [path] to the ECU using a KWP2000/DS2 sequence.
     */
    fun writeTuneFile(path: String, callback: (Boolean) -> Unit) {
        thread {
            try {
                val data = File(path).readBytes()
                val svc = if (currentCommType == CommType.BLUETOOTH) btService else usbService

                // 1) Start Diagnostic Session
                svc.send(byteArrayOf(0x10.toByte(), 0x81.toByte()))
                waitForPositive(svc, 0x50)

                // 2) Security Access (seed/key)
                svc.send(byteArrayOf(0x27.toByte(), 0x01.toByte()))
                val seed = waitForSeed(svc)
                val key  = computeBmwKey(seed)
                svc.send(byteArrayOf(0x27.toByte(), 0x02.toByte()) + key)
                waitForPositive(svc, 0x67)

                // 3) Optional: erase routine
                svc.send(byteArrayOf(0x31.toByte(), 0x01.toByte(), 0xFF.toByte()))
                waitForPositive(svc, 0x71)

                // 4) Transfer Data in chunks
                val chunkSize = 128
                var counter: Byte = 1
                var offset = 0
                while (offset < data.size) {
                    val end = (offset + chunkSize).coerceAtMost(data.size)
                    val chunk = data.copyOfRange(offset, end)
                    val msg = byteArrayOf(0x36.toByte(), counter) + chunk
                    svc.send(msg)
                    waitForSpecificResponse(svc, 0x76, counter)
                    counter = (counter + 1).toByte()
                    offset = end
                }

                // 5) Request Transfer Exit
                svc.send(byteArrayOf(0x37.toByte()))
                waitForPositive(svc, 0x77)

                callback(true)
            } catch (_: Exception) {
                callback(false)
            }
        }
    }

    private fun waitForPositive(svc: CommService, serviceId: Int) {
        val resp = svc.receiveBytes()
        if (resp.isEmpty() || resp[0].toInt() != (serviceId + 0x40)) {
            throw RuntimeException("Unexpected positive response")
        }
    }

    private fun waitForSeed(svc: CommService): ByteArray {
        val resp = svc.receiveBytes()
        if (resp.size < 3 || resp[0].toInt() != 0x67 || resp[1].toInt() != 0x01) {
            throw RuntimeException("Invalid seed response")
        }
        return resp.copyOfRange(2, resp.size)
    }

    private fun computeBmwKey(seed: ByteArray): ByteArray {
        // placeholder: bitwise invert each byte
        return ByteArray(seed.size) { i ->
            seed[i].toInt().inv().toByte()
        }
    }

    private fun waitForSpecificResponse(svc: CommService, code: Int, counter: Byte) {
        val resp = svc.receiveBytes()
        if (resp.size < 2 || resp[0].toInt() != code || resp[1] != counter) {
            throw RuntimeException("Unexpected transfer response")
        }
    }
}
