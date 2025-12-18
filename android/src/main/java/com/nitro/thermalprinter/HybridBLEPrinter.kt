package com.nitro.thermalprinter

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.core.content.ContextCompat
import com.nitro.thermalprinter.utils.ImageProcessor
import kotlinx.coroutines.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * BLE Device data class
 */
data class BLEDevice(
    val deviceName: String,
    val innerMacAddress: String
)

/**
 * Permission result data class
 */
data class PermissionResult(
    val granted: Boolean,
    val shouldShowSettings: Boolean
)

/**
 * HybridBLEPrinter - Kotlin implementation of BLE thermal printer
 * Uses Nitro Modules for high-performance JS bridge
 */
class HybridBLEPrinter(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionManager = ConnectionManager()
    private val printQueue = PrintQueue(scope)
    private val imageCache = ImageCache(10)
    private val imageProcessor = ImageProcessor()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var currentDevice: BluetoothDevice? = null

    // Standard SPP UUID for Bluetooth printers
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    // ESC/POS commands
    private val ESC_INIT = byteArrayOf(0x1B, 0x40) // Initialize printer
    private val ESC_CUT = byteArrayOf(0x1D, 0x56, 0x00) // Full cut
    private val ESC_BEEP = byteArrayOf(0x1B, 0x42, 0x03, 0x02) // Beep

    // Connection state listeners (thread-safe)
    private val stateListeners = CopyOnWriteArrayList<(ConnectionState) -> Unit>()

    init {
        // Observe connection state changes
        scope.launch {
            connectionManager.state.collect { state ->
                stateListeners.forEach { it(state) }

                // Handle auto-reconnection
                if (state == ConnectionState.RECONNECTING && connectionManager.shouldReconnect()) {
                    delay(connectionManager.getReconnectDelay())
                    connectionManager.getReconnectDeviceId()?.let { deviceId ->
                        connectionManager.incrementReconnectAttempts()
                        tryReconnect(deviceId)
                    }
                }
            }
        }
    }

    // ============ Lifecycle ============

    suspend fun init(): Unit = withContext(Dispatchers.Main) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
            ?: throw IllegalStateException("No Bluetooth adapter available")

        if (bluetoothAdapter?.isEnabled != true) {
            throw IllegalStateException("Bluetooth is not enabled")
        }
    }

    fun dispose() {
        scope.cancel()
        closeConnectionInternal()
        printQueue.dispose()
    }

    // ============ Device Discovery ============

    suspend fun getDeviceList(): List<BLEDevice> = withContext(Dispatchers.IO) {
        ensureInitialized()

        if (!hasBluetoothPermissions()) {
            throw SecurityException("Bluetooth permissions not granted")
        }

        try {
            bluetoothAdapter?.bondedDevices?.map { device ->
                BLEDevice(
                    deviceName = device.name ?: "Unknown",
                    innerMacAddress = device.address
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            throw SecurityException("Bluetooth permissions not granted: ${e.message}")
        }
    }

    // ============ Connection ============

    suspend fun connectPrinter(innerMacAddress: String): BLEDevice = withContext(Dispatchers.IO) {
        ensureInitialized()
        connectionManager.setConnecting()

        val device = try {
            bluetoothAdapter?.bondedDevices?.find { it.address == innerMacAddress }
        } catch (e: SecurityException) {
            connectionManager.setDisconnected()
            throw SecurityException("Bluetooth permissions not granted")
        } ?: run {
            connectionManager.setDisconnected()
            throw IllegalArgumentException("Device not found in paired devices: $innerMacAddress")
        }

        try {
            connectToDevice(device)
            connectionManager.setConnected(innerMacAddress)
            BLEDevice(device.name ?: "Unknown", device.address)
        } catch (e: Exception) {
            connectionManager.setDisconnected()
            throw e
        }
    }

    private suspend fun connectToDevice(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        closeConnectionInternal()

        try {
            // Try standard connection first
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            currentDevice = device
        } catch (e: IOException) {
            // Fallback: use reflection method
            try {
                bluetoothSocket?.close()
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                bluetoothSocket = method.invoke(device, 1) as BluetoothSocket
                bluetoothSocket?.connect()
                currentDevice = device
            } catch (e2: Exception) {
                bluetoothSocket?.close()
                bluetoothSocket = null
                throw IOException("Failed to connect to device: ${e2.message}")
            }
        }
    }

    private suspend fun tryReconnect(deviceId: String) {
        try {
            val device = bluetoothAdapter?.bondedDevices?.find { it.address == deviceId }
            if (device != null) {
                connectToDevice(device)
                connectionManager.setConnected(deviceId)
            } else {
                connectionManager.resetReconnection()
            }
        } catch (e: Exception) {
            if (!connectionManager.shouldReconnect()) {
                connectionManager.resetReconnection()
            }
        }
    }

    suspend fun closeConnection(): Unit = withContext(Dispatchers.IO) {
        closeConnectionInternal()
        connectionManager.setDisconnected()
    }

    private fun closeConnectionInternal() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            // Ignore
        }
        bluetoothSocket = null
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

    // ============ Auto-Reconnection ============

    fun enableAutoReconnect(enabled: Boolean) {
        connectionManager.enableAutoReconnect(enabled)
    }

    fun setReconnectAttempts(maxAttempts: Int) {
        connectionManager.setMaxReconnectAttempts(maxAttempts)
    }

    fun setReconnectDelay(delayMs: Long) {
        connectionManager.setReconnectDelay(delayMs)
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
                ?: throw IllegalArgumentException("Failed to download image from: $imageUrl")

            val width = (options?.get("imageWidth") as? Number)?.toInt() ?: 0
            val height = (options?.get("imageHeight") as? Number)?.toInt() ?: 0
            val escPosData = imageProcessor.bitmapToEscPos(bitmap, width, height)
            writeToSocket(escPosData)

            // Handle cut and beep
            if (options?.get("cut") == true) {
                writeToSocket(ESC_CUT)
            }
            if (options?.get("beep") == true) {
                writeToSocket(ESC_BEEP)
            }
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

            // Handle cut and beep
            if (options?.get("cut") == true) {
                writeToSocket(ESC_CUT)
            }
            if (options?.get("beep") == true) {
                writeToSocket(ESC_BEEP)
            }
        }
        return printQueue.awaitJob(job.id)
    }

    suspend fun printColumnsText(
        texts: List<String>,
        columnWidths: List<Int>,
        columnAlignments: List<Int>,
        columnStyles: List<String>? = null,
        options: Map<String, Any>? = null
    ): PrintJobStatus {
        // Process column text using existing utility
        val result = processColumnText(texts, columnWidths, columnAlignments, columnStyles ?: emptyList())
        return printText(result, options)
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

            if (options?.get("cut") == true) {
                writeToSocket(ESC_CUT)
            }
            if (options?.get("beep") == true) {
                writeToSocket(ESC_BEEP)
            }
        }
        return printQueue.awaitJob(job.id)
    }

    fun clearImageCache() {
        imageCache.clear()
    }

    // ============ Permissions ============

    suspend fun askPermissions(): PermissionResult = withContext(Dispatchers.Main) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            // Open app settings to allow user to grant permissions
            PermissionResult(granted = false, shouldShowSettings = true)
        } else {
            PermissionResult(granted = true, shouldShowSettings = false)
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ============ Helper Methods ============

    private fun ensureInitialized() {
        if (bluetoothAdapter == null) {
            throw IllegalStateException("Printer not initialized. Call init() first.")
        }
    }

    private fun ensureConnected() {
        if (bluetoothSocket == null || bluetoothSocket?.isConnected != true) {
            throw IllegalStateException("Not connected to printer. Call connectPrinter() first.")
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private suspend fun writeToSocket(data: ByteArray) = withContext(Dispatchers.IO) {
        withTimeout(WRITE_TIMEOUT_MS) {
            bluetoothSocket?.outputStream?.apply {
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

        // Initialize printer
        output.addAll(ESC_INIT.toList())

        // Add text as bytes (UTF-8)
        val encoding = options?.get("encoding") as? String ?: "UTF-8"
        output.addAll(text.toByteArray(charset(encoding)).toList())

        // Add tailing line if requested
        if (options?.get("tailingLine") == true) {
            output.addAll("\n\n\n".toByteArray().toList())
        }

        // Beep if requested
        if (options?.get("beep") == true) {
            output.addAll(ESC_BEEP.toList())
        }

        // Cut if requested
        if (options?.get("cut") == true) {
            output.addAll(ESC_CUT.toList())
        }

        return output.toByteArray()
    }

    /**
     * Process column text layout (simplified version)
     */
    private fun processColumnText(
        texts: List<String>,
        columnWidths: List<Int>,
        columnAlignments: List<Int>,
        columnStyles: List<String>
    ): String {
        val totalWidth = columnWidths.sum()
        val lines = mutableListOf<String>()
        val remainingTexts = texts.toMutableList()
        var hasMore = true

        while (hasMore) {
            val lineBuilder = StringBuilder()
            hasMore = false

            for (i in texts.indices) {
                val width = columnWidths.getOrElse(i) { 10 }
                val alignment = columnAlignments.getOrElse(i) { 0 }
                var text = remainingTexts.getOrElse(i) { "" }

                // Check if text overflows
                if (text.length > width) {
                    // Find last space within width
                    val lastSpace = text.substring(0, width).lastIndexOf(' ')
                    val breakPoint = if (lastSpace > 0) lastSpace else width

                    remainingTexts[i] = text.substring(breakPoint).trim()
                    text = text.substring(0, breakPoint)
                    hasMore = true
                } else {
                    remainingTexts[i] = ""
                }

                // Apply alignment
                val paddedText = when (alignment) {
                    1 -> text.padStart((width + text.length) / 2).padEnd(width) // CENTER
                    2 -> text.padStart(width) // RIGHT
                    else -> text.padEnd(width) // LEFT
                }

                lineBuilder.append(paddedText)
            }

            lines.add(lineBuilder.toString())
        }

        return lines.joinToString("\n")
    }
}
