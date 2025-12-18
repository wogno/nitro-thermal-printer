package com.nitro.thermalprinter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.util.Base64
import com.nitro.thermalprinter.utils.ImageProcessor
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * USB Device data class
 */
data class USBDevice(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceId: Int = 0
)

/**
 * HybridUSBPrinter - Kotlin implementation of USB thermal printer (Android only)
 */
class HybridUSBPrinter(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionManager = ConnectionManager()
    private val printQueue = PrintQueue(scope)
    private val imageCache = ImageCache(10)
    private val imageProcessor = ImageProcessor()

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpoint: UsbEndpoint? = null
    private var permissionIntent: PendingIntent? = null

    private val stateListeners = CopyOnWriteArrayList<(ConnectionState) -> Unit>()
    private val deviceAttachedListeners = CopyOnWriteArrayList<(USBDevice) -> Unit>()
    private val deviceDetachedListeners = CopyOnWriteArrayList<() -> Unit>()
    private var isReceiverRegistered = false

    // ESC/POS commands
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_CUT = byteArrayOf(0x1D, 0x56, 0x00)
    private val ESC_BEEP = byteArrayOf(0x1B, 0x42, 0x03, 0x02)

    private val ACTION_USB_PERMISSION = "com.nitro.thermalprinter.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { usbDevice = it }
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        val usbDev = USBDevice(
                            deviceName = it.deviceName,
                            vendorId = it.vendorId,
                            productId = it.productId,
                            deviceId = it.deviceId
                        )
                        deviceAttachedListeners.forEach { listener -> listener(usbDev) }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    closeConnectionInternal()
                    connectionManager.setDisconnected()
                    deviceDetachedListeners.forEach { it() }
                }
            }
        }
    }

    init {
        scope.launch {
            connectionManager.state.collect { state ->
                stateListeners.forEach { it(state) }
            }
        }
    }

    // ============ Lifecycle ============

    suspend fun init(): Unit = withContext(Dispatchers.Main) {
        usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: throw IllegalStateException("USB Manager not available")

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        permissionIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            context.registerReceiver(usbReceiver, filter)
            isReceiverRegistered = true
        }
    }

    fun dispose() {
        scope.cancel()
        closeConnectionInternal()
        printQueue.dispose()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                // Already unregistered
            }
        }
    }

    // ============ Device Discovery ============

    suspend fun getDeviceList(): List<USBDevice> = withContext(Dispatchers.IO) {
        val manager = usbManager ?: return@withContext emptyList()

        manager.deviceList.values.map { device ->
            USBDevice(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                deviceId = device.deviceId
            )
        }
    }

    // ============ Connection ============

    suspend fun connectPrinter(vendorId: Int, productId: Int): USBDevice = withContext(Dispatchers.IO) {
        connectionManager.setConnecting()

        val manager = usbManager ?: run {
            connectionManager.setDisconnected()
            throw IllegalStateException("USB Manager not initialized")
        }

        val device = manager.deviceList.values.find {
            it.vendorId == vendorId && it.productId == productId
        } ?: run {
            connectionManager.setDisconnected()
            throw IllegalArgumentException("USB device not found: $vendorId:$productId")
        }

        // Request permission if not granted
        if (!manager.hasPermission(device)) {
            manager.requestPermission(device, permissionIntent)
            delay(1000) // Wait for permission dialog
        }

        if (!manager.hasPermission(device)) {
            connectionManager.setDisconnected()
            throw SecurityException("USB permission not granted")
        }

        // Open connection
        val connection = manager.openDevice(device) ?: run {
            connectionManager.setDisconnected()
            throw IOException("Failed to open USB connection")
        }

        // Find bulk transfer endpoint
        val intf = device.getInterface(0)
        var endpoint: UsbEndpoint? = null

        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                ep.direction == UsbConstants.USB_DIR_OUT) {
                endpoint = ep
                break
            }
        }

        if (endpoint == null) {
            connection.close()
            connectionManager.setDisconnected()
            throw IOException("No bulk transfer endpoint found")
        }

        if (!connection.claimInterface(intf, true)) {
            connection.close()
            connectionManager.setDisconnected()
            throw IOException("Failed to claim USB interface")
        }

        usbDevice = device
        usbConnection = connection
        usbInterface = intf
        usbEndpoint = endpoint

        val deviceId = "$vendorId:$productId"
        connectionManager.setConnected(deviceId)

        USBDevice(
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            deviceId = device.deviceId
        )
    }

    suspend fun closeConnection(): Unit = withContext(Dispatchers.IO) {
        closeConnectionInternal()
        connectionManager.setDisconnected()
    }

    private fun closeConnectionInternal() {
        usbInterface?.let { usbConnection?.releaseInterface(it) }
        usbConnection?.close()
        usbDevice = null
        usbConnection = null
        usbInterface = null
        usbEndpoint = null
    }

    // ============ Connection State ============

    fun isConnected(): String? = connectionManager.getConnectedDeviceId()

    fun getConnectionState(): String = connectionManager.state.value.value

    fun onConnectionStateChange(callback: (String) -> Unit): () -> Unit {
        val listener: (ConnectionState) -> Unit = { state -> callback(state.value) }
        stateListeners.add(listener)
        return { stateListeners.remove(listener) }
    }

    // ============ USB Events ============

    fun onDeviceAttached(callback: (USBDevice) -> Unit): () -> Unit {
        deviceAttachedListeners.add(callback)
        return { deviceAttachedListeners.remove(callback) }
    }

    fun onDeviceDetached(callback: () -> Unit): () -> Unit {
        deviceDetachedListeners.add(callback)
        return { deviceDetachedListeners.remove(callback) }
    }

    // ============ Print Status ============

    fun isPrinting(): Boolean = printQueue.isPrinting.value

    fun getPrintQueue(): List<PrintJobStatus> = printQueue.getQueueStatus()

    // ============ Print Methods ============

    suspend fun printText(text: String, options: Map<String, Any>? = null): PrintJobStatus {
        val job = printQueue.enqueue {
            ensureConnected()
            val data = encodeText(text, options)
            writeToUsb(data)
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
            writeToUsb(bytes)
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
            writeToUsb(escPosData)

            if (options?.get("cut") == true) writeToUsb(ESC_CUT)
            if (options?.get("beep") == true) writeToUsb(ESC_BEEP)
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
            writeToUsb(escPosData)

            if (options?.get("cut") == true) writeToUsb(ESC_CUT)
            if (options?.get("beep") == true) writeToUsb(ESC_BEEP)
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
            writeToUsb(escPosData)

            if (options?.get("cut") == true) writeToUsb(ESC_CUT)
            if (options?.get("beep") == true) writeToUsb(ESC_BEEP)
        }
        return printQueue.awaitJob(job.id)
    }

    fun clearImageCache() {
        imageCache.clear()
    }

    // ============ Permissions ============

    suspend fun askPermissions(): PermissionResult {
        return PermissionResult(granted = true, shouldShowSettings = false)
    }

    // ============ Helper Methods ============

    private fun ensureConnected() {
        if (usbConnection == null || usbEndpoint == null) {
            throw IllegalStateException("Not connected to USB printer")
        }
    }

    private suspend fun writeToUsb(data: ByteArray) = withContext(Dispatchers.IO) {
        withTimeout(WRITE_TIMEOUT_MS) {
            val connection = usbConnection ?: throw IOException("USB not connected")
            val endpoint = usbEndpoint ?: throw IOException("USB endpoint not found")

            val result = connection.bulkTransfer(endpoint, data, data.size, USB_TRANSFER_TIMEOUT_MS)
            if (result < 0) {
                throw IOException("USB bulk transfer failed: $result")
            }
        }
    }

    companion object {
        private const val WRITE_TIMEOUT_MS = 15000L // 15 seconds overall timeout
        private const val USB_TRANSFER_TIMEOUT_MS = 10000 // 10 seconds for USB transfer
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
