package com.nitro.thermalprinter

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import com.nitro.thermalprinter.utils.ImageProcessor
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Network Device data class
 */
data class NetDevice(
    val host: String,
    val port: Int,
    val deviceName: String = "$host:$port"
)

/**
 * HybridNetPrinter - Kotlin implementation of Network thermal printer
 */
class HybridNetPrinter(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionManager = ConnectionManager()
    private val printQueue = PrintQueue(scope)
    private val imageCache = ImageCache(10)
    private val imageProcessor = ImageProcessor()

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var currentDevice: NetDevice? = null

    private val stateListeners = CopyOnWriteArrayList<(ConnectionState) -> Unit>()
    private val scanProgressListeners = CopyOnWriteArrayList<(Int) -> Unit>()

    // ESC/POS commands
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_CUT = byteArrayOf(0x1D, 0x56, 0x00)
    private val ESC_BEEP = byteArrayOf(0x1B, 0x42, 0x03, 0x02)

    private val DEFAULT_PORT = 9100
    private val DEFAULT_TIMEOUT = 4000

    init {
        scope.launch {
            connectionManager.state.collect { state ->
                stateListeners.forEach { it(state) }
            }
        }
    }

    // ============ Lifecycle ============

    suspend fun init(): Unit = withContext(Dispatchers.Main) {
        // Network printer doesn't need special initialization
    }

    fun dispose() {
        scope.cancel()
        closeConnectionInternal()
        printQueue.dispose()
    }

    // ============ Device Discovery ============

    suspend fun getDeviceList(): List<NetDevice> {
        // Return empty list - network printers need to be scanned
        return emptyList()
    }

    suspend fun scanNetwork(timeout: Int = 5000): List<NetDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<NetDevice>()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipAddress = wifiManager?.connectionInfo?.ipAddress ?: return@withContext devices

        val ipString = String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            (ipAddress shr 8) and 0xff,
            (ipAddress shr 16) and 0xff,
            (ipAddress shr 24) and 0xff
        )

        val prefix = ipString.substringBeforeLast('.') + "."
        val selfSuffix = ipString.substringAfterLast('.').toIntOrNull() ?: 0

        val jobs = (1..254).filter { it != selfSuffix }.map { suffix ->
            async {
                val host = prefix + suffix
                if (isPortOpen(host, DEFAULT_PORT, 100)) {
                    NetDevice(host, DEFAULT_PORT)
                } else null
            }
        }

        var completed = 0
        jobs.forEach { job ->
            val result = job.await()
            completed++
            scanProgressListeners.forEach { it((completed * 100) / 254) }
            result?.let { devices.add(it) }
        }

        devices
    }

    private fun isPortOpen(host: String, port: Int, timeout: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeout)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun onScanProgress(callback: (Int) -> Unit): () -> Unit {
        scanProgressListeners.add(callback)
        return { scanProgressListeners.remove(callback) }
    }

    // ============ Connection ============

    suspend fun connectPrinter(host: String, port: Int = DEFAULT_PORT, timeout: Int = DEFAULT_TIMEOUT): NetDevice =
        withContext(Dispatchers.IO) {
            connectionManager.setConnecting()

            try {
                socket = Socket()
                socket?.connect(InetSocketAddress(host, port), timeout)
                outputStream = socket?.getOutputStream()

                val device = NetDevice(host, port)
                currentDevice = device
                connectionManager.setConnected("$host:$port")
                device
            } catch (e: Exception) {
                closeConnectionInternal()
                connectionManager.setDisconnected()
                throw IOException("Failed to connect to $host:$port - ${e.message}")
            }
        }

    suspend fun closeConnection(): Unit = withContext(Dispatchers.IO) {
        closeConnectionInternal()
        connectionManager.setDisconnected()
    }

    private fun closeConnectionInternal() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            // Ignore
        }
        outputStream = null
        socket = null
        currentDevice = null
    }

    // ============ Connection State ============

    fun isConnected(): String? = connectionManager.getConnectedDeviceId()

    fun getConnectionState(): String = connectionManager.state.value.value

    fun onConnectionStateChange(callback: (String) -> Unit): () -> Unit {
        val listener: (ConnectionState) -> Unit = { state -> callback(state.value) }
        stateListeners.add(listener)
        return { stateListeners.remove(listener) }
    }

    // ============ Print Status ============

    fun isPrinting(): Boolean = printQueue.isPrinting.value

    fun getPrintQueue(): List<PrintJobStatus> = printQueue.getQueueStatus()

    // ============ Print Methods ============

    suspend fun printText(text: String, options: Map<String, Any>? = null): PrintJobStatus {
        val job = printQueue.enqueue {
            ensureConnected()
            val data = encodeText(text, options)
            writeToSocket(data)
        }
        return printQueue.awaitJob(job.id)
    }

    suspend fun printBill(text: String, options: Map<String, Any>? = null): PrintJobStatus {
        val billOptions = (options?.toMutableMap() ?: mutableMapOf()).apply {
            putIfAbsent("beep", true)
            putIfAbsent("cut", true)
            putIfAbsent("tailingLine", true)
        }
        return printText(text, billOptions)
    }

    suspend fun printRaw(data: String): PrintJobStatus {
        val job = printQueue.enqueue {
            ensureConnected()
            val bytes = Base64.decode(data, Base64.DEFAULT)
            writeToSocket(bytes)
        }
        return printQueue.awaitJob(job.id)
    }

    suspend fun printImage(imageUrl: String, options: Map<String, Any>? = null): PrintJobStatus {
        val job = printQueue.enqueue {
            ensureConnected()
            val bitmap = imageProcessor.downloadBitmap(imageUrl)
                ?: throw IllegalArgumentException("Failed to download image")

            val width = (options?.get("imageWidth") as? Number)?.toInt() ?: 0
            val height = (options?.get("imageHeight") as? Number)?.toInt() ?: 0
            val escPosData = imageProcessor.bitmapToEscPos(bitmap, width, height)
            writeToSocket(escPosData)

            if (options?.get("cut") == true) writeToSocket(ESC_CUT)
            if (options?.get("beep") == true) writeToSocket(ESC_BEEP)
        }
        return printQueue.awaitJob(job.id)
    }

    suspend fun printImageBase64(base64: String, options: Map<String, Any>? = null): PrintJobStatus {
        val job = printQueue.enqueue {
            ensureConnected()
            val bitmap = imageProcessor.decodeBitmapFromBase64(base64)
                ?: throw IllegalArgumentException("Invalid Base64 image data")

            val width = (options?.get("imageWidth") as? Number)?.toInt() ?: 0
            val height = (options?.get("imageHeight") as? Number)?.toInt() ?: 0
            val escPosData = imageProcessor.bitmapToEscPos(bitmap, width, height)
            writeToSocket(escPosData)

            if (options?.get("cut") == true) writeToSocket(ESC_CUT)
            if (options?.get("beep") == true) writeToSocket(ESC_BEEP)
        }
        return printQueue.awaitJob(job.id)
    }

    // ============ Image Caching ============

    suspend fun cacheImage(url: String, key: String) {
        imageCache.cacheFromUrl(url, key)
    }

    suspend fun printCachedImage(key: String, options: Map<String, Any>? = null): PrintJobStatus {
        val bitmap = imageCache.get(key)
            ?: throw IllegalArgumentException("Image not found in cache: $key")

        val job = printQueue.enqueue {
            ensureConnected()
            val width = (options?.get("imageWidth") as? Number)?.toInt() ?: 0
            val height = (options?.get("imageHeight") as? Number)?.toInt() ?: 0
            val escPosData = imageProcessor.bitmapToEscPos(bitmap, width, height)
            writeToSocket(escPosData)

            if (options?.get("cut") == true) writeToSocket(ESC_CUT)
            if (options?.get("beep") == true) writeToSocket(ESC_BEEP)
        }
        return printQueue.awaitJob(job.id)
    }

    fun clearImageCache() {
        imageCache.clear()
    }

    // ============ Permissions ============

    suspend fun askPermissions(): PermissionResult {
        // Network doesn't require special permissions
        return PermissionResult(granted = true, shouldShowSettings = false)
    }

    // ============ Helper Methods ============

    private fun ensureConnected() {
        if (socket == null || socket?.isConnected != true) {
            throw IllegalStateException("Not connected to printer")
        }
    }

    private suspend fun writeToSocket(data: ByteArray) = withContext(Dispatchers.IO) {
        withTimeout(WRITE_TIMEOUT_MS) {
            outputStream?.apply {
                write(data)
                flush()
            } ?: throw IOException("Socket not connected")
        }
    }

    companion object {
        private const val WRITE_TIMEOUT_MS = 10000L // 10 seconds timeout for write operations
    }

    private fun encodeText(text: String, options: Map<String, Any>?): ByteArray {
        val output = mutableListOf<Byte>()
        output.addAll(ESC_INIT.toList())

        val encoding = options?.get("encoding") as? String ?: "UTF-8"
        output.addAll(text.toByteArray(charset(encoding)).toList())

        if (options?.get("tailingLine") == true) {
            output.addAll("\n\n\n".toByteArray().toList())
        }
        if (options?.get("beep") == true) {
            output.addAll(ESC_BEEP.toList())
        }
        if (options?.get("cut") == true) {
            output.addAll(ESC_CUT.toList())
        }

        return output.toByteArray()
    }
}
