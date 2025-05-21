package com.kimboflash

/**
 * Common interface for communication services.
 */
interface CommService {
    fun send(data: ByteArray)
    fun receiveBytes(): ByteArray
}
