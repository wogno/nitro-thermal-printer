package com.thermalprinter

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Base64
import androidx.annotation.Keep
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.core.Promise
import com.margelo.nitro.com.thermalprinter.ConnectionState
import com.margelo.nitro.com.thermalprinter.HybridNetPrinterSpec
import com.margelo.nitro.com.thermalprinter.ImagePrintOptions
import com.margelo.nitro.com.thermalprinter.NetDevice
import com.margelo.nitro.com.thermalprinter.PermissionResult
import com.margelo.nitro.com.thermalprinter.PrintBulkItem
import com.margelo.nitro.com.thermalprinter.PrintBulkItemType
import com.margelo.nitro.com.thermalprinter.PrintJobStatus
import com.margelo.nitro.com.thermalprinter.PrintJobStatusType
import com.margelo.nitro.com.thermalprinter.PrintOptions
import com.thermalprinter.utils.ImageProcessor
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * HybridNetPrinter - Kotlin implementation of Network thermal printer
 * Extends Nitro-generated HybridNetPrinterSpec for JSI bridge
 */
@DoNotStrip
@Keep
class HybridNetPrinter : HybridNetPrinterSpec() {

    private val context: Context
        get() = ApplicationContextProvider.context
            ?: throw IllegalStateException("Application context not available")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val imageProcessor = ImageProcessor()
    private val imageCache = ImageCache(10)

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var currentDevice: NetDevice? = null

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

    private val DEFAULT_PORT = 9100.0
    private val DEFAULT_TIMEOUT = 4000

    // Listeners
    private val stateListeners = CopyOnWriteArrayList<Pair<String, (ConnectionState) -> Unit>>()
    private val scanProgressListeners = CopyOnWriteArrayList<Pair<String, (Double) -> Unit>>()

    // Known devices cache
    private val knownDevices = mutableListOf<NetDevice>()

    companion object {
        private const val WRITE_TIMEOUT_MS = 15000L
    }

    // ============ Lifecycle ============

    override fun initialize(): Promise<Unit> = Promise.async {
        // Network printer doesn't need special initialization
    }

    override fun dispose() {
        scope.cancel()
        closeConnectionInternal()
        imageCache.clear()
    }

    // ============ Device Discovery ============

    override fun getDeviceList(): Promise<Array<NetDevice>> = Promise.async {
        knownDevices.toTypedArray()
    }

    override fun scanNetwork(timeout: Double): Promise<Array<NetDevice>> = Promise.async {
        withContext(Dispatchers.IO) {
            val foundDevices = mutableListOf<NetDevice>()
            val localIp = getLocalIPAddress() ?: throw IOException("No network connection")
            val subnet = localIp.substringBeforeLast(".")

            val totalHosts = 254
            var scannedHosts = 0

            // Scan common printer ports
            val jobs = (1..254).map { i ->
                async {
                    val host = "$subnet.$i"
                    if (isPortOpen(host, DEFAULT_PORT.toInt(), 100)) {
                        synchronized(foundDevices) {
                            foundDevices.add(NetDevice(host, DEFAULT_PORT, null))
                        }
                    }
                    synchronized(this@HybridNetPrinter) {
                        scannedHosts++
                        val progress = (scannedHosts * 100.0) / totalHosts
                        scanProgressListeners.forEach { it.second(progress) }
                    }
                }
            }

            withTimeout(timeout.toLong()) {
                jobs.awaitAll()
            }

            // Add to known devices
            knownDevices.clear()
            knownDevices.addAll(foundDevices)

            foundDevices.toTypedArray()
        }
    }

    override fun addScanProgressListener(callback: (progress: Double) -> Unit): String {
        val subscriptionId = UUID.randomUUID().toString()
        scanProgressListeners.add(Pair(subscriptionId, callback))
        return subscriptionId
    }

    override fun removeScanProgressListener(subscriptionId: String) {
        scanProgressListeners.removeAll { it.first == subscriptionId }
    }

    // ============ Connection ============

    override fun connectPrinter(host: String, port: Double, timeout: Double): Promise<NetDevice> = Promise.async {
        withContext(Dispatchers.IO) {
            setConnectionState(ConnectionState.CONNECTING)

            try {
                socket = Socket()
                socket?.connect(
                    InetSocketAddress(host, port.toInt()),
                    timeout.toInt()
                )
                outputStream = socket?.getOutputStream()

                val device = NetDevice(host, port, "$host:${port.toInt()}")
                currentDevice = device
                connectedDeviceId = "$host:${port.toInt()}"

                // Add to known devices if not exists
                if (knownDevices.none { it.host == host && it.port == port }) {
                    knownDevices.add(device)
                }

                setConnectionState(ConnectionState.CONNECTED)
                device
            } catch (e: Exception) {
                setConnectionState(ConnectionState.DISCONNECTED)
                throw IOException("Failed to connect to $host:${port.toInt()}: ${e.message}")
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
        // Network printing doesn't require special permissions on most Android versions
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

    private fun ensureConnected() {
        if (socket == null || socket?.isConnected != true || outputStream == null) {
            throw IllegalStateException("Not connected to printer. Call connectPrinter() first.")
        }
    }

    private fun closeConnectionInternal() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) { }
        outputStream = null
        socket = null
        currentDevice = null
    }

    private suspend fun writeToSocket(data: ByteArray) = withContext(Dispatchers.IO) {
        withTimeout(WRITE_TIMEOUT_MS) {
            outputStream?.apply {
                write(data)
                flush()
            } ?: throw IOException("Socket not connected")
        }
    }

    private fun getLocalIPAddress(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val ip = wifiInfo?.ipAddress ?: return null

        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
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

    private fun encodeText(text: String, options: PrintOptions): ByteArray {
        val output = mutableListOf<Byte>()
        output.addAll(ESC_INIT.toList())
        output.addAll(text.toByteArray(charset(options.encoding)).toList())

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
