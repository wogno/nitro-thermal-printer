import Foundation
import UIKit
import CoreBluetooth
import NitroModules

#if !targetEnvironment(simulator)
import PrinterSDK
typealias PrinterType = Printer
#else
// Stub class for simulator - Printer type is not available
class PrinterType {
    var name: String = ""
    var uuidString: String = ""
}
#endif

/// HybridBLEPrinter - Swift implementation of BLE thermal printer
/// Implements Nitro-generated HybridBLEPrinterSpec protocol for JSI bridge
public class HybridBLEPrinter: HybridBLEPrinterSpec {

    // MARK: - HybridObject Requirements
    public var memorySize: Int {
        return MemoryLayout<HybridBLEPrinter>.size
    }

    // MARK: - Private Properties
    private let printerSDK = PrinterSDKBridge.shared
    private let imageCache = ImageCache(maxSize: 10)

    private var printerArray: [PrinterType] = []
    private var currentPrinter: PrinterType?

    // Connection state
    private var connectionState: ConnectionState = .disconnected
    private var connectedDeviceId: String?

    // Auto-reconnection
    private var autoReconnectEnabled = false
    private var maxReconnectAttempts = 3
    private var reconnectDelayMs = 2000
    private var reconnectAttempts = 0

    // Print state
    private var isPrintingState = false
    private var printJobs: [String: PrintJobStatus] = [:]
    private let printQueue = DispatchQueue(label: "com.thermalprinter.printqueue", qos: .userInitiated)

    // Listeners
    private var stateListeners: [String: (ConnectionState) -> Void] = [:]

    public override init() {
        super.init()
        setupNotifications()
    }

    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handlePrinterConnected),
            name: .printerConnected,
            object: nil
        )

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handlePrinterDisconnected),
            name: .printerDisconnected,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Lifecycle

    public func initialize() throws -> Promise<Void> {
        self.printerArray = []
        return Promise.resolved(withResult: ())
    }

    // MARK: - Device Discovery

    public func getDeviceList() throws -> Promise<[BLEDevice]> {
        return Promise.async { [weak self] in
            guard let self = self else { return [] }

            return await withCheckedContinuation { continuation in
                var devices: [BLEDevice] = []
                var hasResumed = false
                let resumeLock = NSLock()

                self.printerArray = []

                self.printerSDK.scanPrinters { [weak self] printerAny in
                    guard let printerAny = printerAny,
                          let printer = printerAny as? PrinterType else {
                        resumeLock.lock()
                        if !hasResumed {
                            hasResumed = true
                            resumeLock.unlock()
                            continuation.resume(returning: devices)
                        } else {
                            resumeLock.unlock()
                        }
                        return
                    }

                    self?.printerArray.append(printer)
                    let device = BLEDevice(
                        deviceName: printer.name,
                        innerMacAddress: printer.uuidString
                    )

                    if !devices.contains(where: { $0.innerMacAddress == device.innerMacAddress }) {
                        devices.append(device)
                    }
                }

                // Timeout after 5 seconds
                Task {
                    try? await Task.sleep(nanoseconds: 5_000_000_000)
                    self.printerSDK.stopScan()
                    resumeLock.lock()
                    if !hasResumed {
                        hasResumed = true
                        resumeLock.unlock()
                        continuation.resume(returning: devices)
                    } else {
                        resumeLock.unlock()
                    }
                }
            }
        }
    }

    // MARK: - Connection

    public func connectPrinter(innerMacAddress: String) throws -> Promise<BLEDevice> {
        guard let printer = self.printerArray.first(where: { $0.uuidString == innerMacAddress }) else {
            self.setConnectionState(.disconnected)
            throw PrinterError.deviceNotFound
        }

        self.setConnectionState(.connecting)
        self.printerSDK.connectBT(printer)
        self.currentPrinter = printer

        // Create device immediately
        let device = BLEDevice(
            deviceName: printer.name,
            innerMacAddress: printer.uuidString
        )

        // Set connection state after a small delay for BLE to establish
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.connectedDeviceId = innerMacAddress
            self?.setConnectionState(.connected)
        }

        // Return device immediately (connection happens in background)
        return Promise.resolved(withResult: device)
    }

    public func closeConnection() throws -> Promise<Void> {
        self.printerSDK.disconnect()
        self.currentPrinter = nil
        self.connectedDeviceId = nil
        self.setConnectionState(.disconnected)
        return Promise.resolved(withResult: ())
    }

    public func isConnected()  -> String? {
        return connectedDeviceId
    }

    public func getConnectionState()  -> ConnectionState {
        return connectionState
    }

    public func addConnectionStateListener(callback: @escaping (_ state: ConnectionState) -> Void) throws -> String {
        let subscriptionId = UUID().uuidString
        stateListeners[subscriptionId] = callback
        return subscriptionId
    }

    public func removeConnectionStateListener(subscriptionId: String) throws {
        stateListeners.removeValue(forKey: subscriptionId)
    }

    // MARK: - Auto-Reconnection

    public func enableAutoReconnect(enabled: Bool) throws {
        autoReconnectEnabled = enabled
    }

    public func setReconnectAttempts(maxAttempts: Double) throws {
        self.maxReconnectAttempts = Int(maxAttempts)
    }

    public func setReconnectDelay(delayMs: Double) throws {
        self.reconnectDelayMs = Int(delayMs)
    }

    // MARK: - Print Status

    public func isPrinting()  -> Bool {
        return isPrintingState
    }

    public func getPrintQueue()  -> [PrintJobStatus] {
        return Array(printJobs.values)
    }

    public func getJobStatus(jobId: String) -> PrintJobStatus? {
        return printJobs[jobId]
    }

    // MARK: - Raw Print Helpers
    
    /// Convert a string to hex representation
    private func stringToHex(_ string: String) -> String {
        return string.data(using: .utf8)?.map { String(format: "%02x", $0) }.joined() ?? ""
    }
    
    /// Print text using raw sendHex to bypass PrinterSDK's text formatting
    /// This gives us full control over ESC/POS commands including line spacing
    private func printTextRaw(_ text: String) {
        // ESC @ - Initialize printer
        let initCommand = "1b40"
        
        // ESC 3 n - Set line spacing to n dots (24 = 0x18 for comfortable spacing)
        let lineSpacingCommand = "1b3318"
        
        // Send init and line spacing
        printerSDK.sendHex(initCommand)
        printerSDK.sendHex(lineSpacingCommand)
        
        // Convert text to hex and send
        let textHex = stringToHex(text)
        printerSDK.sendHex(textHex)
        
        // Line feed at end
        let lineFeedHex = "0a" // \n
        printerSDK.sendHex(lineFeedHex)
    }
    
    // MARK: - Print Methods

    public func printText(text: String, options: PrintOptions) throws -> Promise<PrintJobStatus> {
        let jobId = UUID().uuidString
        let job = PrintJobStatus(jobId: jobId, status: .queued, error: nil)
        printJobs[jobId] = job

        // Check connection synchronously
        guard currentPrinter != nil else {
            let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Printer not connected")
            printJobs[jobId] = failed
            return Promise.resolved(withResult: failed)
        }

        // Queue work in background (serial queue maintains order)
        printQueue.async { [weak self] in
            guard let self = self else { return }

            self.isPrintingState = true
            self.printJobs[jobId] = PrintJobStatus(jobId: jobId, status: .printing, error: nil)

            // Use sendHex for everything to have full control over ESC/POS commands
            // printText() ignores our ESC commands, so we bypass it completely
            self.printTextRaw(text)

            if options.beep {
                self.printerSDK.beep()
            }
            if options.cut {
                self.printerSDK.cutPaper()
            }

            let completed = PrintJobStatus(jobId: jobId, status: .completed, error: nil)
            self.printJobs[jobId] = completed
            self.isPrintingState = false
        }

        // Return immediately - work happens in background
        return Promise.resolved(withResult: job)
    }

    public func printBill(text: String, options: PrintOptions) throws -> Promise<PrintJobStatus> {
        let billOptions = PrintOptions(
            beep: true,
            cut: true,
            tailingLine: true,
            encoding: options.encoding
        )
        return try printText(text: text, options: billOptions)
    }

    public func printRaw(data: String) throws -> Promise<PrintJobStatus> {
        let jobId = UUID().uuidString
        let job = PrintJobStatus(jobId: jobId, status: .queued, error: nil)
        printJobs[jobId] = job

        guard currentPrinter != nil else {
            let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Printer not connected")
            printJobs[jobId] = failed
            return Promise.resolved(withResult: failed)
        }

        guard let decodedData = Data(base64Encoded: data) else {
            let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Invalid data")
            printJobs[jobId] = failed
            return Promise.resolved(withResult: failed)
        }

        printQueue.async { [weak self] in
            guard let self = self else { return }

            self.isPrintingState = true
            self.printJobs[jobId] = PrintJobStatus(jobId: jobId, status: .printing, error: nil)

            let hexString = decodedData.map { String(format: "%02X", $0) }.joined()
            self.printerSDK.sendHex(hexString)

            let completed = PrintJobStatus(jobId: jobId, status: .completed, error: nil)
            self.printJobs[jobId] = completed
            self.isPrintingState = false
        }

        return Promise.resolved(withResult: job)
    }

    public func printImage(imageUrl: String, options: ImagePrintOptions) throws -> Promise<PrintJobStatus> {
        return Promise.async { [weak self] in
            guard let self = self else { throw PrinterError.notInitialized }

            let jobId = UUID().uuidString
            var job = PrintJobStatus(jobId: jobId, status: .queued, error: nil)
            self.printJobs[jobId] = job

            do {
                self.isPrintingState = true
                job = PrintJobStatus(jobId: jobId, status: .printing, error: nil)
                self.printJobs[jobId] = job

                guard self.currentPrinter != nil else {
                    throw PrinterError.notConnected
                }

                let image = try await self.imageCache.downloadImage(from: imageUrl)
                let processedImage = self.processImage(image, options: options)
                let printerWidth = options.printerWidthType == .mm58 ? 384 : 576

                self.printerSDK.setPrintWidth(printerWidth)
                self.printerSDK.printImage(processedImage)

                if options.beep {
                    self.printerSDK.beep()
                }
                if options.cut {
                    self.printerSDK.cutPaper()
                }

                let completed = PrintJobStatus(jobId: jobId, status: .completed, error: nil)
                self.printJobs[jobId] = completed
                self.isPrintingState = false
                return completed
            } catch {
                let failed = PrintJobStatus(jobId: jobId, status: .failed, error: error.localizedDescription)
                self.printJobs[jobId] = failed
                self.isPrintingState = false
                return failed
            }
        }
    }

    public func printImageBase64(base64: String, options: ImagePrintOptions) throws -> Promise<PrintJobStatus> {
        let jobId = UUID().uuidString
        let job = PrintJobStatus(jobId: jobId, status: .queued, error: nil)
        printJobs[jobId] = job

        guard currentPrinter != nil else {
            let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Printer not connected")
            printJobs[jobId] = failed
            return Promise.resolved(withResult: failed)
        }

        guard let data = Data(base64Encoded: base64),
              let image = UIImage(data: data) else {
            let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Invalid image data")
            printJobs[jobId] = failed
            return Promise.resolved(withResult: failed)
        }

        printQueue.async { [weak self] in
            guard let self = self else { return }

            self.isPrintingState = true
            self.printJobs[jobId] = PrintJobStatus(jobId: jobId, status: .printing, error: nil)

            let processedImage = self.processImage(image, options: options)
            let printerWidth = options.printerWidthType == .mm58 ? 384 : 576

            self.printerSDK.setPrintWidth(printerWidth)
            self.printerSDK.printImage(processedImage)

            if options.beep {
                self.printerSDK.beep()
            }
            if options.cut {
                self.printerSDK.cutPaper()
            }

            let completed = PrintJobStatus(jobId: jobId, status: .completed, error: nil)
            self.printJobs[jobId] = completed
            self.isPrintingState = false
        }

        return Promise.resolved(withResult: job)
    }

    public func printColumnsText(
        texts: [String],
        columnWidths: [Double],
        columnAlignments: [Double],
        columnStyles: [String],
        options: PrintOptions
    ) throws -> Promise<PrintJobStatus> {
        let result = processColumnText(
            texts,
            columnWidths: columnWidths.map { Int($0) },
            columnAlignments: columnAlignments.map { Int($0) },
            columnStyles: columnStyles
        )
        return try printText(text: result, options: options)
    }

    public func printBulk(items: [PrintBulkItem]) throws -> Promise<PrintJobStatus> {
        NSLog("========== [PrintBulk] START ==========")
        NSLog("[PrintBulk] Called with \(items.count) items")
        
        // Log each item structure
        for (index, item) in items.enumerated() {
            NSLog("[PrintBulk] Item \(index + 1): type=\(item.type)")
            NSLog("  - content: \(item.content ?? "nil")")
            NSLog("  - options: \(item.options != nil ? "present" : "nil")")
            NSLog("  - texts: \(item.texts?.count ?? 0) items")
            NSLog("  - columnWidths: \(item.columnWidths?.count ?? 0) items")
            NSLog("  - columnAlignments: \(item.columnAlignments?.count ?? 0) items")
            NSLog("  - columnStyles: \(item.columnStyles?.count ?? 0) items")
            NSLog("  - base64: \(item.base64 != nil ? "present" : "nil")")
            NSLog("  - imageOptions: \(item.imageOptions != nil ? "present" : "nil")")
        }
        
        do {
            let jobId = UUID().uuidString
            let job = PrintJobStatus(jobId: jobId, status: .queued, error: nil)
            printJobs[jobId] = job

            guard currentPrinter != nil else {
                NSLog("[PrintBulk] Printer not connected")
                let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Printer not connected")
                printJobs[jobId] = failed
                return Promise.resolved(withResult: failed)
            }

            NSLog("[PrintBulk] Starting print queue")

            printQueue.async { [weak self] in
                guard let self = self else {
                    NSLog("[PrintBulk] Self is nil")
                    return
                }

                do {
                    self.isPrintingState = true
                    self.printJobs[jobId] = PrintJobStatus(jobId: jobId, status: .printing, error: nil)

                    // Process each item in the bulk
                    for (index, item) in items.enumerated() {
                        NSLog("[PrintBulk] Processing item \(index + 1)/\(items.count), type: \(item.type)")
                        
                        switch item.type {
                case .text:
                    if let content = item.content {
                        // Use raw sendHex to bypass PrinterSDK's text formatting
                        self.printTextRaw(content)
                        if let options = item.options {
                            if options.beep { self.printerSDK.beep() }
                            if options.cut { self.printerSDK.cutPaper() }
                        }
                    }

                case .columns:
                    if let texts = item.texts,
                       let widths = item.columnWidths,
                       let alignments = item.columnAlignments {
                        NSLog("[PrintBulk] Processing columns: texts=\(texts.count), widths=\(widths.count), alignments=\(alignments.count)")
                        let styles = item.columnStyles ?? []
                        let result = self.processColumnText(
                            texts,
                            columnWidths: widths.map { Int($0) },
                            columnAlignments: alignments.map { Int($0) },
                            columnStyles: styles
                        )
                        // Use raw sendHex to bypass PrinterSDK's text formatting
                        self.printTextRaw(result)
                        if let options = item.options {
                            if options.beep { self.printerSDK.beep() }
                            if options.cut { self.printerSDK.cutPaper() }
                        }
                    } else {
                        NSLog("[PrintBulk] Warning: Missing required fields for columns item")
                    }

                case .imageBase64:
                    if let base64 = item.base64,
                       let data = Data(base64Encoded: base64),
                       let image = UIImage(data: data),
                       let imageOptions = item.imageOptions {
                        let processedImage = self.processImage(image, options: imageOptions)
                        let printerWidth = imageOptions.printerWidthType == .mm58 ? 384 : 576
                        self.printerSDK.setPrintWidth(printerWidth)
                        self.printerSDK.printImage(processedImage)
                        if imageOptions.beep { self.printerSDK.beep() }
                        if imageOptions.cut { self.printerSDK.cutPaper() }
                    }

                case .separator:
                    self.printTextRaw(" ------ ------ ------ ------")

                @unknown default:
                    NSLog("[PrintBulk] Warning: Unknown item type")
                    break
                }
                    }
                    
                    let completed = PrintJobStatus(jobId: jobId, status: .completed, error: nil)
                    self.printJobs[jobId] = completed
                    self.isPrintingState = false
                    NSLog("[PrintBulk] Completed successfully")
                } catch {
                    NSLog("[PrintBulk] Error processing items: \(error.localizedDescription)")
                    let failed = PrintJobStatus(jobId: jobId, status: .failed, error: error.localizedDescription)
                    self.printJobs[jobId] = failed
                    self.isPrintingState = false
                }
            }

            NSLog("[PrintBulk] Promise created, returning job")
            return Promise.resolved(withResult: job)
        } catch {
            NSLog("[PrintBulk] ❌ ERROR in printBulk: \(error)")
            NSLog("[PrintBulk] Error type: \(type(of: error))")
            NSLog("[PrintBulk] Error description: \(error.localizedDescription)")
            if let nsError = error as NSError? {
                NSLog("[PrintBulk] NSError domain: \(nsError.domain)")
                NSLog("[PrintBulk] NSError code: \(nsError.code)")
                NSLog("[PrintBulk] NSError userInfo: \(nsError.userInfo)")
            }
            let jobId = UUID().uuidString
            let failed = PrintJobStatus(jobId: jobId, status: .failed, error: error.localizedDescription)
            printJobs[jobId] = failed
            return Promise.resolved(withResult: failed)
        }
    }

    // MARK: - Image Caching

    public func cacheImage(url: String, key: String) throws -> Promise<Void> {
        return Promise.async { [weak self] in
            try await self?.imageCache.cacheFromUrl(url, key: key)
        }
    }

    public func printCachedImage(key: String, options: ImagePrintOptions) throws -> Promise<PrintJobStatus> {
        return Promise.async { [weak self] in
            guard let self = self else { throw PrinterError.notInitialized }

            return try await withCheckedThrowingContinuation { continuation in
                self.printQueue.async {
                    let jobId = UUID().uuidString
                    var job = PrintJobStatus(jobId: jobId, status: .queued, error: nil)
                    self.printJobs[jobId] = job

                    guard let image = self.imageCache.get(key: key) else {
                        let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Image not cached")
                        self.printJobs[jobId] = failed
                        continuation.resume(returning: failed)
                        return
                    }

                    guard self.currentPrinter != nil else {
                        let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Printer not connected")
                        self.printJobs[jobId] = failed
                        continuation.resume(returning: failed)
                        return
                    }

                    self.isPrintingState = true
                    job = PrintJobStatus(jobId: jobId, status: .printing, error: nil)
                    self.printJobs[jobId] = job

                    let processedImage = self.processImage(image, options: options)
                    let printerWidth = options.printerWidthType == .mm58 ? 384 : 576

                    self.printerSDK.setPrintWidth(printerWidth)
                    self.printerSDK.printImage(processedImage)

                    if options.beep {
                        self.printerSDK.beep()
                    }
                    if options.cut {
                        self.printerSDK.cutPaper()
                    }

                    let completed = PrintJobStatus(jobId: jobId, status: .completed, error: nil)
                    self.printJobs[jobId] = completed
                    self.isPrintingState = false
                    continuation.resume(returning: completed)
                }
            }
        }
    }

    public func clearImageCache() throws {
        imageCache.clear()
    }

    // MARK: - Sync Print Methods (Fire and Forget - Instant Return)

    public func printTextSync(text: String, options: PrintOptions) -> String {
        let jobId = UUID().uuidString
        let job = PrintJobStatus(jobId: jobId, status: .queued, error: nil)
        printJobs[jobId] = job

        printQueue.async { [weak self] in
            guard let self = self else { return }

            guard self.currentPrinter != nil else {
                let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Printer not connected")
                self.printJobs[jobId] = failed
                return
            }

            self.isPrintingState = true
            self.printJobs[jobId] = PrintJobStatus(jobId: jobId, status: .printing, error: nil)

            // Use raw sendHex to bypass PrinterSDK's text formatting
            self.printTextRaw(text)

            if options.beep {
                self.printerSDK.beep()
            }
            if options.cut {
                self.printerSDK.cutPaper()
            }

            let completed = PrintJobStatus(jobId: jobId, status: .completed, error: nil)
            self.printJobs[jobId] = completed
            self.isPrintingState = false
        }

        return jobId
    }

    public func printRawSync(data: String) -> String {
        let jobId = UUID().uuidString
        let job = PrintJobStatus(jobId: jobId, status: .queued, error: nil)
        printJobs[jobId] = job

        printQueue.async { [weak self] in
            guard let self = self else { return }

            guard self.currentPrinter != nil else {
                let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Printer not connected")
                self.printJobs[jobId] = failed
                return
            }

            guard let decodedData = Data(base64Encoded: data) else {
                let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Invalid data")
                self.printJobs[jobId] = failed
                return
            }

            self.isPrintingState = true
            self.printJobs[jobId] = PrintJobStatus(jobId: jobId, status: .printing, error: nil)

            let hexString = decodedData.map { String(format: "%02X", $0) }.joined()
            self.printerSDK.sendHex(hexString)

            let completed = PrintJobStatus(jobId: jobId, status: .completed, error: nil)
            self.printJobs[jobId] = completed
            self.isPrintingState = false
        }

        return jobId
    }

    public func printImageBase64Sync(base64: String, options: ImagePrintOptions) -> String {
        let jobId = UUID().uuidString
        let job = PrintJobStatus(jobId: jobId, status: .queued, error: nil)
        printJobs[jobId] = job

        printQueue.async { [weak self] in
            guard let self = self else { return }

            guard self.currentPrinter != nil else {
                let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Printer not connected")
                self.printJobs[jobId] = failed
                return
            }

            guard let data = Data(base64Encoded: base64),
                  let image = UIImage(data: data) else {
                let failed = PrintJobStatus(jobId: jobId, status: .failed, error: "Invalid image data")
                self.printJobs[jobId] = failed
                return
            }

            self.isPrintingState = true
            self.printJobs[jobId] = PrintJobStatus(jobId: jobId, status: .printing, error: nil)

            let processedImage = self.processImage(image, options: options)
            let printerWidth = options.printerWidthType == .mm58 ? 384 : 576

            self.printerSDK.setPrintWidth(printerWidth)
            self.printerSDK.printImage(processedImage)

            if options.beep {
                self.printerSDK.beep()
            }
            if options.cut {
                self.printerSDK.cutPaper()
            }

            let completed = PrintJobStatus(jobId: jobId, status: .completed, error: nil)
            self.printJobs[jobId] = completed
            self.isPrintingState = false
        }

        return jobId
    }

    public func printBillSync(text: String, options: PrintOptions) -> String {
        // Bill always uses beep, cut, and tailing line
        let billOptions = PrintOptions(
            beep: true,
            cut: true,
            tailingLine: true,
            encoding: options.encoding
        )
        return printTextSync(text: text, options: billOptions)
    }

    public func printColumnsTextSync(
        texts: [String],
        columnWidths: [Double],
        columnAlignments: [Double],
        columnStyles: [String],
        options: PrintOptions
    ) -> String {
        let result = processColumnText(
            texts,
            columnWidths: columnWidths.map { Int($0) },
            columnAlignments: columnAlignments.map { Int($0) },
            columnStyles: columnStyles
        )
        return printTextSync(text: result, options: options)
    }

    // MARK: - Permissions

    public func askPermissions() throws -> Promise<PermissionResult> {
        // iOS handles Bluetooth permissions through Info.plist
        return Promise.resolved(withResult: PermissionResult(granted: true, shouldShowSettings: false))
    }

    // MARK: - Private Helpers

    private func setConnectionState(_ state: ConnectionState) {
        connectionState = state
        if state == .disconnected {
            connectedDeviceId = nil
        }
        stateListeners.values.forEach { $0(state) }
    }

    private func processImage(_ image: UIImage, options: ImagePrintOptions) -> UIImage {
        // Determine printer width in pixels (576 for 80mm, 384 for 58mm)
        let printerWidth = options.printerWidthType == .mm58 ? 384.0 : 576.0

        // Calculate target width - use imageWidth if specified, otherwise use 90% of printer width
        var targetWidth = CGFloat(options.imageWidth)
        if targetWidth <= 0 {
            // Default: use 90% of printer width for larger images
            targetWidth = CGFloat(printerWidth) * 0.9
        } else if targetWidth < 100 {
            // If width is small (< 100px), treat it as percentage of printer width
            // e.g., 38 = 38% of printer width, 40 = 40% of printer width
            let percentage = targetWidth / 100.0
            targetWidth = CGFloat(printerWidth) * percentage
        }

        // Limit to printer width
        targetWidth = min(targetWidth, CGFloat(printerWidth))

        // Calculate height maintaining aspect ratio
        let aspectRatio = image.size.height / image.size.width
        var targetHeight = CGFloat(options.imageHeight)
        if targetHeight <= 0 {
            targetHeight = targetWidth * aspectRatio
        }

        // Resize image
        let targetSize = CGSize(width: targetWidth, height: targetHeight)
        UIGraphicsBeginImageContextWithOptions(targetSize, true, 1.0)
        defer { UIGraphicsEndImageContext() }

        guard let context = UIGraphicsGetCurrentContext() else { return image }

        // White background
        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(origin: .zero, size: targetSize))

        context.interpolationQuality = .high
        image.draw(in: CGRect(origin: .zero, size: targetSize))

        guard let resizedImage = UIGraphicsGetImageFromCurrentImageContext() else { return image }

        // Center image on printer paper
        return centerImageForPrinter(resizedImage, printerWidth: CGFloat(printerWidth))
    }

    private func centerImageForPrinter(_ image: UIImage, printerWidth: CGFloat) -> UIImage {
        // If image is already printer width, no centering needed
        if image.size.width >= printerWidth {
            return image
        }

        // Create new image with printer width, centered
        let newSize = CGSize(width: printerWidth, height: image.size.height)

        UIGraphicsBeginImageContextWithOptions(newSize, true, 1.0)
        defer { UIGraphicsEndImageContext() }

        guard let context = UIGraphicsGetCurrentContext() else { return image }

        // White background
        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(origin: .zero, size: newSize))

        // Center the image horizontally
        let xOffset = (printerWidth - image.size.width) / 2
        image.draw(at: CGPoint(x: xOffset, y: 0))

        return UIGraphicsGetImageFromCurrentImageContext() ?? image
    }

    private func processColumnText(_ texts: [String], columnWidths: [Int], columnAlignments: [Int], columnStyles: [String] = []) -> String {
        var lines: [String] = []
        var remainingTexts = texts
        var hasMore = true

        while hasMore {
            var line = ""
            hasMore = false

            for i in 0..<texts.count {
                let width = i < columnWidths.count ? columnWidths[i] : 10
                let alignment = i < columnAlignments.count ? columnAlignments[i] : 0
                let style = i < columnStyles.count ? columnStyles[i] : ""
                var text = i < remainingTexts.count ? remainingTexts[i] : ""

                if text.count > width {
                    let breakPoint = text.prefix(width).lastIndex(of: " ")
                        .map { text.distance(from: text.startIndex, to: $0) } ?? width
                    remainingTexts[i] = String(text.dropFirst(breakPoint)).trimmingCharacters(in: .whitespaces)
                    text = String(text.prefix(breakPoint))
                    hasMore = true
                } else {
                    remainingTexts[i] = ""
                }

                let paddedText: String
                switch alignment {
                case 1: // CENTER
                    let padding = width - text.count
                    let leftPad = padding / 2
                    paddedText = String(repeating: " ", count: leftPad) + text + String(repeating: " ", count: width - text.count - leftPad)
                case 2: // RIGHT
                    paddedText = String(repeating: " ", count: width - text.count) + text
                default: // LEFT
                    paddedText = text + String(repeating: " ", count: width - text.count)
                }

                // Apply style if provided
                line += style + paddedText
            }

            lines.append(line)
        }

        // Join lines - printTextRaw will handle line spacing
        return lines.joined(separator: "\n")
    }

    // MARK: - Notification Handlers

    @objc private func handlePrinterConnected() {
        if let deviceId = currentPrinter?.uuidString {
            connectedDeviceId = deviceId
            setConnectionState(.connected)
        }
    }

    @objc private func handlePrinterDisconnected() {
        setConnectionState(.disconnected)

        // Handle auto-reconnection
        if autoReconnectEnabled && reconnectAttempts < maxReconnectAttempts {
            Task {
                try? await Task.sleep(nanoseconds: UInt64(reconnectDelayMs) * 1_000_000)
                if let deviceId = connectedDeviceId,
                   let printer = printerArray.first(where: { $0.uuidString == deviceId }) {
                    reconnectAttempts += 1
                    setConnectionState(.reconnecting)
                    printerSDK.connectBT(printer)
                }
            }
        }
    }
}

// MARK: - Errors

public enum PrinterError: Error, LocalizedError {
    case notInitialized
    case notConnected
    case deviceNotFound
    case connectionFailed
    case invalidData
    case imageLoadFailed
    case imageNotCached
    case permissionDenied

    public var errorDescription: String? {
        switch self {
        case .notInitialized: return "Printer not initialized"
        case .notConnected: return "Not connected to printer"
        case .deviceNotFound: return "Device not found"
        case .connectionFailed: return "Connection failed"
        case .invalidData: return "Invalid data format"
        case .imageLoadFailed: return "Failed to load image"
        case .imageNotCached: return "Image not in cache"
        case .permissionDenied: return "Permission denied"
        }
    }
}
