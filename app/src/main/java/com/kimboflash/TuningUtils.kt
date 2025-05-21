
package com.kimboflash

object TuningUtils {

    fun getByteValue(data: ByteArray, offset: Int): Int = data[offset].toInt() and 0xFF

    fun setByteValue(data: ByteArray, offset: Int, value: Int) {
        data[offset] = value.toByte()
    }

    fun applyChecksums(data: ByteArray) {
        // Simpele MS4X-style checksum logica (voorbeeld)
        val checksumOffset = 0x1F800
        val rangeStart = 0x0000
        val rangeEnd = 0x1F7FF
        var sum = 0

        for (i in rangeStart..rangeEnd) {
            sum = (sum + (data[i].toInt() and 0xFF)) and 0xFFFF
        }

        data[checksumOffset] = (sum shr 8).toByte()
        data[checksumOffset + 1] = (sum and 0xFF).toByte()
    }
}
