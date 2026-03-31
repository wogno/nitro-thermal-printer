package com.thermalprinter

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
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.core.Promise
import com.margelo.nitro.com.thermalprinter.BLEDevice
import com.margelo.nitro.com.thermalprinter.ConnectionState
import com.margelo.nitro.com.thermalprinter.HybridBLEPrinterSpec
import com.margelo.nitro.com.thermalprinter.ImagePrintOptions
import com.margelo.nitro.com.thermalprinter.PermissionResult
import com.margelo.nitro.com.thermalprinter.PrintBulkItem
import com.margelo.nitro.com.thermalprinter.PrintBulkItemType
import com.margelo.nitro.com.thermalprinter.PrintJobStatus
import com.margelo.nitro.com.thermalprinter.PrintJobStatusType
import com.margelo.nitro.com.thermalprinter.PrintOptions
import com.thermalprinter.utils.ImageProcessor
import kotlinx.coroutines.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * HybridBLEPrinter - Kotlin implementation of BLE thermal printer
 * Extends Nitro-generated HybridBLEPrinterSpec for JSI bridge
 */
@DoNotStrip
@Keep
class HybridBLEPrinter : HybridBLEPrinterSpec() {

    private val context: Context
        get() = ApplicationContextProvider.context
            ?: throw IllegalStateException("Application context not available")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val imageProcessor = ImageProcessor()
    private val imageCache = ImageCache(10)

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var currentDevice: BluetoothDevice? = null

    // Connection state management
    private var connectionState = ConnectionState.DISCONNECTED
    private var connectedDeviceId: String? = null

    // Auto-reconnection settings
    private var autoReconnectEnabled = false
    private var maxReconnectAttempts = 3
    private var reconnectDelayMs: Long = 2000
    private var reconnectAttempts = 0

    // Print job queue
    private var isPrintingState = false
    private val printJobs = mutableMapOf<String, PrintJobStatus>()

    // Standard SPP UUID for Bluetooth printers
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    // ESC/POS commands
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_CUT = byteArrayOf(0x1D, 0x56, 0x00)
    private val ESC_BEEP = byteArrayOf(0x1B, 0x42, 0x03, 0x02)

    // Connection state listeners
    private val stateListeners = CopyOnWriteArrayList<Pair<String, (ConnectionState) -> Unit>>()

    companion object {
        private const val WRITE_TIMEOUT_MS = 10000L
    }

    // ============ Lifecycle ============

    override fun initialize(): Promise<Unit> = Promise.async {
        withContext(Dispatchers.Main) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter

            // Allow initialization on emulators without Bluetooth - operations will fail gracefully
            if (bluetoothAdapter == null) {
                android.util.Log.w("HybridBLEPrinter", "No Bluetooth adapter available (emulator?)")
                return@withContext
            }

            if (bluetoothAdapter?.isEnabled != true) {
                android.util.Log.w("HybridBLEPrinter", "Bluetooth is not enabled")
            }
        }
    }

    override fun dispose() {
        scope.cancel()
        closeConnectionInternal()
        imageCache.clear()
    }

    // ============ Device Discovery ============

    override fun getDeviceList(): Promise<Array<BLEDevice>> = Promise.async {
        withContext(Dispatchers.IO) {
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
                }?.toTypedArray() ?: emptyArray()
            } catch (e: SecurityException) {
                throw SecurityException("Bluetooth permissions not granted: ${e.message}")
            }
        }
    }

    // ============ Connection ============

    override fun connectPrinter(innerMacAddress: String): Promise<BLEDevice> = Promise.async {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            setConnectionState(ConnectionState.CONNECTING)

            val device = try {
                bluetoothAdapter?.bondedDevices?.find { it.address == innerMacAddress }
            } catch (e: SecurityException) {
                setConnectionState(ConnectionState.DISCONNECTED)
                throw SecurityException("Bluetooth permissions not granted")
            } ?: run {
                setConnectionState(ConnectionState.DISCONNECTED)
                throw IllegalArgumentException("Device not found: $innerMacAddress")
            }

            try {
                connectToDevice(device)
                connectedDeviceId = innerMacAddress
                setConnectionState(ConnectionState.CONNECTED)
                BLEDevice(device.name ?: "Unknown", device.address)
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

    // ============ Auto-Reconnection ============

    override fun enableAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
    }

    override fun setReconnectAttempts(maxAttempts: Double) {
        this.maxReconnectAttempts = maxAttempts.toInt()
    }

    override fun setReconnectDelay(delayMs: Double) {
        this.reconnectDelayMs = delayMs.toLong()
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
                writeToSocket(data)

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
                writeToSocket(bytes)

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
                writeToSocket(escPosData)

                if (options.cut) writeToSocket(ESC_CUT)
                if (options.beep) writeToSocket(ESC_BEEP)

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
                writeToSocket(escPosData)

                if (options.cut) writeToSocket(ESC_CUT)
                if (options.beep) writeToSocket(ESC_BEEP)

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

    override fun printBulk(items: Array<PrintBulkItem>): Promise<PrintJobStatus> = Promise.async {
        withContext(Dispatchers.IO) {
            val jobId = UUID.randomUUID().toString()
            val job = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
            printJobs[jobId] = job

            try {
                isPrintingState = true
                printJobs[jobId] = job.copy(status = PrintJobStatusType.PRINTING)

                ensureConnected()

                // Process each item in the bulk
                // NOTE: We avoid using encodeText() here because ESC/POS commands
                // containing \x00 (like TXT_NORMAL = \x1b\x21\x00) get truncated
                // by the JNI bridge (Modified UTF-8 treats \x00 as string terminator).
                // Instead, we write ESC_INIT separately and append \x00 after text
                // to safely complete any truncated ESC command.
                for (item in items) {
                    when (item.type) {
                        PrintBulkItemType.TEXT -> {
                            item.content?.let { content ->
                                // Send ESC @ (init) as raw bytes to reset printer state
                                writeToSocket(ESC_INIT)
                                // Send text content bytes
                                val textBytes = content.toByteArray(charset(item.options?.encoding ?: "UTF-8"))
                                writeToSocket(textBytes)
                                // Append 0x00 to complete any truncated ESC command
                                // (e.g. TXT_NORMAL \x1b\x21\x00 loses its \x00 in JNI)
                                // then append newline
                                writeToSocket(byteArrayOf(0x00, 0x0A))
                                // Handle options
                                val options = item.options
                                if (options != null) {
                                    if (options.tailingLine) writeToSocket("\n\n\n".toByteArray())
                                    if (options.beep) writeToSocket(ESC_BEEP)
                                    if (options.cut) writeToSocket(ESC_CUT)
                                }
                            }
                        }

                        PrintBulkItemType.COLUMNS -> {
                            item.texts?.let { texts ->
                                item.columnWidths?.let { widths ->
                                    item.columnAlignments?.let { alignments ->
                                        val styles = item.columnStyles ?: arrayOf()
                                        val result = processColumnText(
                                            texts.toList(),
                                            widths.map { it.toInt() },
                                            alignments.map { it.toInt() },
                                            styles.toList()
                                        )
                                        // Send ESC @ (init) as raw bytes
                                        writeToSocket(ESC_INIT)
                                        val encoding = item.options?.encoding ?: "UTF-8"
                                        val textBytes = result.toByteArray(charset(encoding))
                                        writeToSocket(textBytes)
                                        // Append 0x00 + newline
                                        writeToSocket(byteArrayOf(0x00, 0x0A))
                                        // Handle options
                                        val options = item.options
                                        if (options != null) {
                                            if (options.beep) writeToSocket(ESC_BEEP)
                                            if (options.cut) writeToSocket(ESC_CUT)
                                        }
                                    }
                                }
                            }
                        }

                        PrintBulkItemType.IMAGE_BASE64 -> {
                            item.base64?.let { base64 ->
                                item.imageOptions?.let { imageOptions ->
                                    val bitmap = imageProcessor.decodeBitmapFromBase64(base64)
                                        ?: throw IllegalArgumentException("Invalid Base64 image data")

                                    val escPosData = imageProcessor.bitmapToEscPos(
                                        bitmap,
                                        imageOptions.imageWidth.toInt(),
                                        imageOptions.imageHeight.toInt()
                                    )
                                    writeToSocket(escPosData)

                                    if (imageOptions.cut) writeToSocket(ESC_CUT)
                                    if (imageOptions.beep) writeToSocket(ESC_BEEP)
                                }
                            }
                        }

                        PrintBulkItemType.SEPARATOR -> {
                            val separator = " ------ ------ ------ ------\n"
                            val data = separator.toByteArray()
                            writeToSocket(data)
                        }
                    }
                }

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

    // ============ Sync Print Methods ============

    override fun printTextSync(text: String, options: PrintOptions): String {
        val jobId = UUID.randomUUID().toString()
        printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.QUEUED, null)
        scope.launch {
            try {
                printJobs[jobId] = PrintJobStatus(jobId, PrintJobStatusType.PRINTING, null)
                ensureConnected()
                val data = encodeText(text, options)
                writeToSocket(data)
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
                writeToSocket(bytes)
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
                writeToSocket(escPosData)
                if (options.cut) writeToSocket(ESC_CUT)
                if (options.beep) writeToSocket(ESC_BEEP)
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
                writeToSocket(escPosData)

                if (options.cut) writeToSocket(ESC_CUT)
                if (options.beep) writeToSocket(ESC_BEEP)

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
        withContext(Dispatchers.Main) {
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

            PermissionResult(granted = allGranted, shouldShowSettings = !allGranted)
        }
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

    private suspend fun connectToDevice(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        closeConnectionInternal()

        try {
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            currentDevice = device
        } catch (e: IOException) {
            try {
                bluetoothSocket?.close()
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                bluetoothSocket = method.invoke(device, 1) as BluetoothSocket
                bluetoothSocket?.connect()
                currentDevice = device
            } catch (e2: Exception) {
                bluetoothSocket?.close()
                bluetoothSocket = null
                throw IOException("Failed to connect: ${e2.message}")
            }
        }
    }

    private fun closeConnectionInternal() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) { }
        bluetoothSocket = null
        currentDevice = null
    }

    private suspend fun writeToSocket(data: ByteArray) = withContext(Dispatchers.IO) {
        withTimeout(WRITE_TIMEOUT_MS) {
            bluetoothSocket?.outputStream?.apply {
                write(data)
                flush()
            } ?: throw IOException("Socket not connected")
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
