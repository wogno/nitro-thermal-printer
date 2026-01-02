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
        return Promise.async { [weak self] in
            self?.printerArray = []
        }
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
        return Promise.async { [weak self] in
            guard let self = self else { throw PrinterError.notInitialized }

            self.setConnectionState(.connecting)

            guard let printer = self.printerArray.first(where: { $0.uuidString == innerMacAddress }) else {
                self.setConnectionState(.disconnected)
                throw PrinterError.deviceNotFound
            }

            self.printerSDK.connectBT(printer)
            self.currentPrinter = printer

            // Wait for connection
            try await Task.sleep(nanoseconds: 1_000_000_000)

            self.connectedDeviceId = innerMacAddress
            self.setConnectionState(.connected)

            return BLEDevice(
                deviceName: printer.name,
                innerMacAddress: printer.uuidString
            )
        }
    }

    public func closeConnection() throws -> Promise<Void> {
        return Promise.async { [weak self] in
            self?.printerSDK.disconnect()
            self?.currentPrinter = nil
            self?.connectedDeviceId = nil
            self?.setConnectionState(.disconnected)
        }
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

    // MARK: - Print Methods

    public func printText(text: String, options: PrintOptions) throws -> Promise<PrintJobStatus> {
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

                self.printerSDK.printText(text)

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

                // Decode base64 and convert to hex string for sendHex
                guard let decodedData = Data(base64Encoded: data) else {
                    throw PrinterError.invalidData
                }

                let hexString = decodedData.map { String(format: "%02X", $0) }.joined()
                self.printerSDK.sendHex(hexString)

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

                guard let data = Data(base64Encoded: base64),
                      let image = UIImage(data: data) else {
                    throw PrinterError.invalidData
                }

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
            columnAlignments: columnAlignments.map { Int($0) }
        )
        return try printText(text: result, options: options)
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

            guard let image = self.imageCache.get(key: key) else {
                throw PrinterError.imageNotCached
            }

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

    public func clearImageCache() throws {
        imageCache.clear()
    }

    // MARK: - Permissions

    public func askPermissions() throws -> Promise<PermissionResult> {
        return Promise.async {
            // iOS handles Bluetooth permissions through Info.plist
            return PermissionResult(granted: true, shouldShowSettings: false)
        }
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

        // Calculate target width - use imageWidth if specified, otherwise fit to printer width
        var targetWidth = CGFloat(options.imageWidth)
        if targetWidth <= 0 {
            // Default: fit image to printer width minus padding
            let padding = CGFloat(options.paddingX)
            targetWidth = CGFloat(printerWidth) - padding
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

    private func processColumnText(_ texts: [String], columnWidths: [Int], columnAlignments: [Int]) -> String {
        var lines: [String] = []
        var remainingTexts = texts
        var hasMore = true

        while hasMore {
            var line = ""
            hasMore = false

            for i in 0..<texts.count {
                let width = i < columnWidths.count ? columnWidths[i] : 10
                let alignment = i < columnAlignments.count ? columnAlignments[i] : 0
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

                line += paddedText
            }

            lines.append(line)
        }

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
