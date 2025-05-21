package com.kimboflash.patch

import java.io.FileInputStream
import java.io.FileOutputStream

class PatchManager {
    enum class EcuType { MS42, MS43, UNKNOWN }

    private lateinit var data: ByteArray
    private var ecuType: EcuType = EcuType.UNKNOWN

    fun loadBin(input: FileInputStream) {
        data = input.readBytes()
        ecuType = identifyEcu(data)
    }

    private fun identifyEcu(data: ByteArray): EcuType {
        val versionByte = data[0x1234]  // adjust offset
        return when (versionByte.toInt()) {
            in 0x10..0x1F -> EcuType.MS42
            in 0x20..0x2F -> EcuType.MS43
            else -> EcuType.UNKNOWN
        }
    }

    fun applyPopsBangs(enabled: Boolean) {
        if (!enabled || ecuType != EcuType.MS42) return
        val rpmOffset = 0x2345
        val igOffset = 0x3456
        data[rpmOffset] = 0xFF.toByte()
        data[igOffset] = 0xF0.toByte()
    }

    fun applyIgnitionCutMs42(enabled: Boolean) {
        if (!enabled || ecuType != EcuType.MS42) return
        val offset = 0x4567
        data[offset] = 0x01
    }

    fun applyIgnitionCutMs43(enabled: Boolean) {
        if (!enabled || ecuType != EcuType.MS43) return
        val offset = 0x4567
        data[offset] = 0x01
    }

    fun applyLaunchControl(enabled: Boolean) {
        if (!enabled) return
        val offset = 0x5678
        data[offset] = 0x01
    }

    fun applyNoLiftShift(enabled: Boolean) {
        if (!enabled) return
        val offset = 0x6789
        data[offset] = 0x01
    }

    fun applyRollingAntiLag(enabled: Boolean) {
        if (!enabled) return
        val offset = 0x789A
        data[offset] = 0x01
    }

    fun applyIgnitionAdvance(value: Int) {
        val offset = 0x8901
        data[offset] = value.toByte()
    }

    fun applyFuelMixture(value: Int) {
        val offset = 0x9012
        data[offset] = value.toByte()
    }

    fun saveBin(output: FileOutputStream) {
        recalculateChecksum(data)
        output.write(data)
    }

    private fun recalculateChecksum(data: ByteArray) {
        var sum = 0
        for (i in 0 until data.size - 2) {
            sum = (sum + data[i].toUByte().toInt()) and 0xFFFF
        }
        data[data.size - 2] = ((sum shr 8) and 0xFF).toByte()
        data[data.size - 1] = (sum and 0xFF).toByte()
    }
}