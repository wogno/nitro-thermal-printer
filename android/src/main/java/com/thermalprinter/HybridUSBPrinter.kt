package com.thermalprinter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Base64
import androidx.annotation.Keep
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.core.Promise
import com.margelo.nitro.com.thermalprinter.ConnectionState
import com.margelo.nitro.com.thermalprinter.HybridUSBPrinterSpec
import com.margelo.nitro.com.thermalprinter.ImagePrintOptions
import com.margelo.nitro.com.thermalprinter.PermissionResult
import com.margelo.nitro.com.thermalprinter.PrintJobStatus
import com.margelo.nitro.com.thermalprinter.PrintJobStatusType
import com.margelo.nitro.com.thermalprinter.PrintOptions
import com.margelo.nitro.com.thermalprinter.USBDevice as NitroUSBDevice
import com.thermalprinter.utils.ImageProcessor
import kotlinx.coroutines.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * HybridUSBPrinter - Kotlin implementation of USB thermal printer
 * Extends Nitro-generated HybridUSBPrinterSpec for JSI bridge
 * Note: USB printing is only supported on Android
 */
@DoNotStrip
@Keep
class HybridUSBPrinter : HybridUSBPrinterSpec() {

    private val context: Context
        get() = ApplicationContextProvider.context
            ?: throw IllegalStateException("Application context not available")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val imageProcessor = ImageProcessor()
    private val imageCache = ImageCache(10)

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpoint: UsbEndpoint? = null

    // Connection state management
    private var connectionState = ConnectionState.DISCONNECTED
    private var connectedDeviceId: String? = null

    // Print job queue
    private var isPrintingState = false
    private val printJobs = mutableMapOf<String, PrintJobStatus>()

    // ESC/POS commands
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_CUT = byteArrayOf(0x1D, 0x56, 0x00)
    private val ESC_BEEP = byteArrayOf(0x1B, 0x42, 0x03, 0x02)

    // Listeners
    private val stateListeners = CopyOnWriteArrayList<Pair<String, (ConnectionState) -> Unit>>()
    private val deviceAttachedListeners = CopyOnWriteArrayList<Pair<String, (NitroUSBDevice) -> Unit>>()
    private val deviceDetachedListeners = CopyOnWriteArrayList<Pair<String, () -> Unit>>()

    private var isReceiverRegistered = false

    companion object {
        private const val ACTION_USB_PERMISSION = "com.thermalprinter.USB_PERMISSION"
        private const val WRITE_TIMEOUT_MS = 10000
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let {
                        val nitroDevice = NitroUSBDevice(
                            deviceName = it.deviceName,
                            vendorId = it.vendorId.toDouble(),
                            productId = it.productId.toDouble(),
                            deviceId = it.deviceId.toDouble()
                        )
                        deviceAttachedListeners.forEach { listener -> listener.second(nitroDevice) }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device?.deviceId == usbDevice?.deviceId) {
                        closeConnectionInternal()
                        setConnectionState(ConnectionState.DISCONNECTED)
                        deviceDetachedListeners.forEach { listener -> listener.second() }
                    }
                }
            }
        }
    }

    // ============ Lifecycle ============

    override fun initialize(): Promise<Unit> = Promise.async {
        withContext(Dispatchers.Main) {
            usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: throw IllegalStateException("USB Manager not available")

            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                    addAction(ACTION_USB_PERMISSION)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(usbReceiver, filter)
                }
                isReceiverRegistered = true
            }
        }
    }

    override fun dispose() {
        scope.cancel()
        closeConnectionInternal()
        imageCache.clear()

        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) { }
            isReceiverRegistered = false
        }
    }

    // ============ Device Discovery ============

    override fun getDeviceList(): Promise<Array<NitroUSBDevice>> = Promise.async {
        withContext(Dispatchers.IO) {
            ensureInitialized()

            usbManager?.deviceList?.values
                ?.filter { isPrinterDevice(it) }
                ?.map { device ->
                    NitroUSBDevice(
                        deviceName = device.deviceName,
                        vendorId = device.vendorId.toDouble(),
                        productId = device.productId.toDouble(),
                        deviceId = device.deviceId.toDouble()
                    )
                }?.toTypedArray() ?: emptyArray()
        }
    }

    // ============ Connection ============

    override fun connectPrinter(vendorId: Double, productId: Double): Promise<NitroUSBDevice> = Promise.async {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            setConnectionState(ConnectionState.CONNECTING)

            val device = usbManager?.deviceList?.values?.find {
                it.vendorId == vendorId.toInt() && it.productId == productId.toInt()
            } ?: run {
                setConnectionState(ConnectionState.DISCONNECTED)
                throw IllegalArgumentException("USB device not found: $vendorId:$productId")
            }

            // Check/request permission
            if (usbManager?.hasPermission(device) != true) {
                val permissionIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                usbManager?.requestPermission(device, permissionIntent)

                // Wait for permission
                delay(2000)

                if (usbManager?.hasPermission(device) != true) {
                    setConnectionState(ConnectionState.DISCONNECTED)
                    throw SecurityException("USB permission denied for device")
                }
            }

            try {
                connectToDevice(device)
                connectedDeviceId = "${vendorId.toInt()}:${productId.toInt()}"
                setConnectionState(ConnectionState.CONNECTED)

                NitroUSBDevice(
                    deviceName = device.deviceName,
                    vendorId = device.vendorId.toDouble(),
                    productId = device.productId.toDouble(),
                    deviceId = device.deviceId.toDouble()
                )
            } catch (e: Exception) {
                setConnectionState(ConnectionState.DISCONNECTED)
                throw e
            }
        }
    }

    override fun closeConnection(): Promise<Unit> = Promise.async {
        withContext(Dispatchers.IO) {
            closeConnectionInternal()
            setConnectionState(ConnectionState.DISCONNECTED)
        }
    }

    override fun isConnected(): String? = connectedDeviceId

    override fun getConnectionState(): ConnectionState = connectionState

    override fun addConnectionStateListener(callback: (state: ConnectionState) -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()
        stateListeners.add(Pair(subscriptionId, callback))
        return subscriptionId
    }

    override fun removeConnectionStateListener(subscriptionId: String) {
        stateListeners.removeAll { it.first == subscriptionId }
    }

    // ============ USB Events ============

    override fun addDeviceAttachedListener(callback: (device: NitroUSBDevice) -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()
        deviceAttachedListeners.add(Pair(subscriptionId, callback))
        return subscriptionId
    }

    override fun removeDeviceAttachedListener(subscriptionId: String) {
        deviceAttachedListeners.removeAll { it.first == subscriptionId }
    }

    override fun addDeviceDetachedListener(callback: () -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()
        deviceDetachedListeners.add(Pair(subscriptionId, callback))
        return subscriptionId
    }

    override fun removeDeviceDetachedListener(subscriptionId: String) {
        deviceDetachedListeners.removeAll { it.first == subscriptionId }
    }

    // ============ Print Status ============

    override fun isPrinting(): Boolean = isPrintingState

    override fun getPrintQueue(): Array<PrintJobStatus> = printJobs.values.toTypedArray()

    // ============ Print Methods ============

    override fun printText(text: String, options: PrintOptions): Promise<PrintJobStatus> = Promise.async {
        withContext(Dispatchers.IO) {
            val jobId = UUID.randomUUID().toString()
            val job = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
            printJobs[jobId] = job

            try {
                isPrintingState = true
                printJobs[jobId] = job.copy(status = PrintJobStatusType.PRINTING)

                ensureConnected()
                val data = encodeText(text, options)
                writeToUsb(data)

                val completed = PrintJobStatus(jobId, PrintJobStatusType.COMPLETED, null)
                printJobs[jobId] = completed
                completed
            } catch (e: Exception) {
                val failed = PrintJobStatus(jobId, PrintJobStatusType.FAILED, e.message)
                printJobs[jobId] = failed
                failed
            } finally {
                isPrintingState = false
            }
        }
    }

    override fun printBill(text: String, options: PrintOptions): Promise<PrintJobStatus> {
        val billOptions = PrintOptions(
            beep = true,
            cut = true,
            tailingLine = true,
            encoding = options.encoding
        )
        return printText(text, billOptions)
    }

    override fun printRaw(data: String): Promise<PrintJobStatus> = Promise.async {
        withContext(Dispatchers.IO) {
            val jobId = UUID.randomUUID().toString()
            val job = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
            printJobs[jobId] = job

            try {
                isPrintingState = true
                printJobs[jobId] = job.copy(status = PrintJobStatusType.PRINTING)

                ensureConnected()
                val bytes = Base64.decode(data, Base64.DEFAULT)
                writeToUsb(bytes)

                val completed = PrintJobStatus(jobId, PrintJobStatusType.COMPLETED, null)
                printJobs[jobId] = completed
                completed
            } catch (e: Exception) {
                val failed = PrintJobStatus(jobId, PrintJobStatusType.FAILED, e.message)
                printJobs[jobId] = failed
                failed
            } finally {
                isPrintingState = false
            }
        }
    }

    override fun printImage(imageUrl: String, options: ImagePrintOptions): Promise<PrintJobStatus> = Promise.async {
        withContext(Dispatchers.IO) {
            val jobId = UUID.randomUUID().toString()
            val job = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
            printJobs[jobId] = job

            try {
                isPrintingState = true
                printJobs[jobId] = job.copy(status = PrintJobStatusType.PRINTING)

                ensureConnected()
                val bitmap = imageProcessor.downloadBitmap(imageUrl)
                    ?: throw IllegalArgumentException("Failed to download image: $imageUrl")

                val escPosData = imageProcessor.bitmapToEscPos(
                    bitmap,
                    options.imageWidth.toInt(),
                    options.imageHeight.toInt()
                )
                writeToUsb(escPosData)

                if (options.cut) writeToUsb(ESC_CUT)
                if (options.beep) writeToUsb(ESC_BEEP)

                val completed = PrintJobStatus(jobId, PrintJobStatusType.COMPLETED, null)
                printJobs[jobId] = completed
                completed
            } catch (e: Exception) {
                val failed = PrintJobStatus(jobId, PrintJobStatusType.FAILED, e.message)
                printJobs[jobId] = failed
                failed
            } finally {
                isPrintingState = false
            }
        }
    }

    override fun printImageBase64(base64: String, options: ImagePrintOptions): Promise<PrintJobStatus> = Promise.async {
        withContext(Dispatchers.IO) {
            val jobId = UUID.randomUUID().toString()
            val job = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
            printJobs[jobId] = job

            try {
                isPrintingState = true
                printJobs[jobId] = job.copy(status = PrintJobStatusType.PRINTING)

                ensureConnected()
                val bitmap = imageProcessor.decodeBitmapFromBase64(base64)
                    ?: throw IllegalArgumentException("Invalid Base64 image data")

                val escPosData = imageProcessor.bitmapToEscPos(
                    bitmap,
                    options.imageWidth.toInt(),
                    options.imageHeight.toInt()
                )
                writeToUsb(escPosData)

                if (options.cut) writeToUsb(ESC_CUT)
                if (options.beep) writeToUsb(ESC_BEEP)

                val completed = PrintJobStatus(jobId, PrintJobStatusType.COMPLETED, null)
                printJobs[jobId] = completed
                completed
            } catch (e: Exception) {
                val failed = PrintJobStatus(jobId, PrintJobStatusType.FAILED, e.message)
                printJobs[jobId] = failed
                failed
            } finally {
                isPrintingState = false
            }
        }
    }

    override fun printColumnsText(
        texts: Array<String>,
        columnWidths: DoubleArray,
        columnAlignments: DoubleArray,
        columnStyles: Array<String>,
        options: PrintOptions
    ): Promise<PrintJobStatus> {
        val result = processColumnText(
            texts.toList(),
            columnWidths.map { it.toInt() },
            columnAlignments.map { it.toInt() },
            columnStyles.toList()
        )
        return printText(result, options)
    }

    // ============ Sync Print Methods ============

    override fun printTextSync(text: String, options: PrintOptions): String {
        val jobId = UUID.randomUUID().toString()
        printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
        scope.launch {
            try {
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.PRINTING, null)
                ensureConnected()
                val data = encodeText(text, options)
                writeToUsb(data)
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.COMPLETED, null)
            } catch (e: Exception) {
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.FAILED, e.message)
            }
        }
        return jobId
    }

    override fun printBillSync(text: String, options: PrintOptions): String {
        val billOptions = PrintOptions(
            beep = true,
            cut = true,
            tailingLine = true,
            encoding = options.encoding
        )
        return printTextSync(text, billOptions)
    }

    override fun printRawSync(data: String): String {
        val jobId = UUID.randomUUID().toString()
        printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
        scope.launch {
            try {
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.PRINTING, null)
                ensureConnected()
                val bytes = Base64.decode(data, Base64.DEFAULT)
                writeToUsb(bytes)
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.COMPLETED, null)
            } catch (e: Exception) {
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.FAILED, e.message)
            }
        }
        return jobId
    }

    override fun printImageBase64Sync(base64: String, options: ImagePrintOptions): String {
        val jobId = UUID.randomUUID().toString()
        printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
        scope.launch {
            try {
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.PRINTING, null)
                ensureConnected()
                val bitmap = imageProcessor.decodeBitmapFromBase64(base64)
                    ?: throw IllegalArgumentException("Invalid Base64 image data")
                val escPosData = imageProcessor.bitmapToEscPos(
                    bitmap, options.imageWidth.toInt(), options.imageHeight.toInt()
                )
                writeToUsb(escPosData)
                if (options.cut) writeToUsb(ESC_CUT)
                if (options.beep) writeToUsb(ESC_BEEP)
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.COMPLETED, null)
            } catch (e: Exception) {
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.FAILED, e.message)
            }
        }
        return jobId
    }

    override fun printColumnsTextSync(
        texts: Array<String>,
        columnWidths: DoubleArray,
        columnAlignments: DoubleArray,
        columnStyles: Array<String>,
        options: PrintOptions
    ): String {
        val result = processColumnText(
            texts.toList(),
            columnWidths.map { it.toInt() },
            columnAlignments.map { it.toInt() },
            columnStyles.toList()
        )
        return printTextSync(result, options)
    }

    override fun getJobStatus(jobId: String): PrintJobStatus? = printJobs[jobId]

    // ============ Image Caching ============

    override fun cacheImage(url: String, key: String): Promise<Unit> = Promise.async {
        imageCache.cacheFromUrl(url, key)
    }

    override fun printCachedImage(key: String, options: ImagePrintOptions): Promise<PrintJobStatus> = Promise.async {
        withContext(Dispatchers.IO) {
            val bitmap = imageCache.get(key)
                ?: throw IllegalArgumentException("Image not found in cache: $key")

            val jobId = UUID.randomUUID().toString()
            val job = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
            printJobs[jobId] = job

            try {
                isPrintingState = true
                printJobs[jobId] = job.copy(status = PrintJobStatusType.PRINTING)

                ensureConnected()
                val escPosData = imageProcessor.bitmapToEscPos(
                    bitmap,
                    options.imageWidth.toInt(),
                    options.imageHeight.toInt()
                )
                writeToUsb(escPosData)

                if (options.cut) writeToUsb(ESC_CUT)
                if (options.beep) writeToUsb(ESC_BEEP)

                val completed = PrintJobStatus(jobId, PrintJobStatusType.COMPLETED, null)
                printJobs[jobId] = completed
                completed
            } catch (e: Exception) {
                val failed = PrintJobStatus(jobId, PrintJobStatusType.FAILED, e.message)
                printJobs[jobId] = failed
                failed
            } finally {
                isPrintingState = false
            }
        }
    }

    override fun clearImageCache() {
        imageCache.clear()
    }

    // ============ Permissions ============

    override fun askPermissions(): Promise<PermissionResult> = Promise.async {
        // USB permissions are handled per-device via requestPermission
        PermissionResult(granted = true, shouldShowSettings = false)
    }

    // ============ Helper Methods ============

    private fun setConnectionState(state: ConnectionState) {
        connectionState = state
        if (state == ConnectionState.DISCONNECTED) {
            connectedDeviceId = null
        }
        stateListeners.forEach { it.second(state) }
    }

    private fun ensureInitialized() {
        if (usbManager == null) {
            throw IllegalStateException("USB Printer not initialized. Call init() first.")
        }
    }

    private fun ensureConnected() {
        if (usbConnection == null || usbEndpoint == null) {
            throw IllegalStateException("Not connected to USB printer. Call connectPrinter() first.")
        }
    }

    private fun isPrinterDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return true
            }
        }
        return false
    }

    private fun connectToDevice(device: UsbDevice) {
        closeConnectionInternal()

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (j in 0 until intf.endpointCount) {
                val endpoint = intf.getEndpoint(j)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    endpoint.direction == UsbConstants.USB_DIR_OUT) {
                    usbInterface = intf
                    usbEndpoint = endpoint
                    break
                }
            }
            if (usbEndpoint != null) break
        }

        if (usbEndpoint == null) {
            throw IOException("No suitable USB endpoint found")
        }

        usbConnection = usbManager?.openDevice(device)
            ?: throw IOException("Failed to open USB device")

        if (usbConnection?.claimInterface(usbInterface, true) != true) {
            usbConnection?.close()
            usbConnection = null
            throw IOException("Failed to claim USB interface")
        }

        usbDevice = device
    }

    private fun closeConnectionInternal() {
        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (e: Exception) { }
        usbConnection = null
        usbInterface = null
        usbEndpoint = null
        usbDevice = null
    }

    private suspend fun writeToUsb(data: ByteArray) = withContext(Dispatchers.IO) {
        withTimeout(WRITE_TIMEOUT_MS.toLong()) {
            val connection = usbConnection ?: throw IOException("USB not connected")
            val endpoint = usbEndpoint ?: throw IOException("USB endpoint not available")

            val result = connection.bulkTransfer(endpoint, data, data.size, WRITE_TIMEOUT_MS)
            if (result < 0) {
                throw IOException("USB bulk transfer failed: $result")
            }
        }
    }

    private fun encodeText(text: String, options: PrintOptions): ByteArray {
        val output = mutableListOf<Byte>()
        output.addAll(ESC_INIT.toList())
        output.addAll(text.toByteArray(charset(options.encoding)).toList())
        // Append 0x00 to safely complete any ESC command truncated by JNI bridge
        // (JNI Modified UTF-8 strips \x00 from strings, e.g. TXT_NORMAL \x1b\x21\x00)
        output.add(0x00)

        if (options.tailingLine) {
            output.addAll("\n\n\n".toByteArray().toList())
        }
        if (options.beep) {
            output.addAll(ESC_BEEP.toList())
        }
        if (options.cut) {
            output.addAll(ESC_CUT.toList())
        }

        return output.toByteArray()
    }

    private fun processColumnText(
        texts: List<String>,
        columnWidths: List<Int>,
        columnAlignments: List<Int>,
        columnStyles: List<String>
    ): String {
        val lines = mutableListOf<String>()
        val remainingTexts = texts.toMutableList()
        var hasMore = true

        while (hasMore) {
            val lineBuilder = StringBuilder()
            hasMore = false

            for (i in texts.indices) {
                val width = columnWidths.getOrElse(i) { 10 }
                val alignment = columnAlignments.getOrElse(i) { 0 }
                val style = columnStyles.getOrElse(i) { "" }
                var text = remainingTexts.getOrElse(i) { "" }

                if (text.length > width) {
                    val lastSpace = text.substring(0, width).lastIndexOf(' ')
                    val breakPoint = if (lastSpace > 0) lastSpace else width
                    remainingTexts[i] = text.substring(breakPoint).trim()
                    text = text.substring(0, breakPoint)
                    hasMore = true
                } else {
                    remainingTexts[i] = ""
                }

                val paddedText = when (alignment) {
                    1 -> text.padStart((width + text.length) / 2).padEnd(width)
                    2 -> text.padStart(width)
                    else -> text.padEnd(width)
                }
                // Apply style if provided (e.g. BOLD_ON + TXT_2HEIGHT for totals)
                lineBuilder.append(style + paddedText)
            }
            lines.add(lineBuilder.toString())
        }

        return lines.joinToString("\n")
    }
}
