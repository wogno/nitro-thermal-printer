import Foundation
import UIKit
import CoreBluetooth

/// BLE Device data structure
public struct BLEDevice: Codable {
    public let deviceName: String
    public let innerMacAddress: String

    public init(deviceName: String, innerMacAddress: String) {
        self.deviceName = deviceName
        self.innerMacAddress = innerMacAddress
    }
}

/// Permission result data structure
public struct PermissionResultData: Codable {
    public let granted: Bool
    public let shouldShowSettings: Bool

    public init(granted: Bool, shouldShowSettings: Bool) {
        self.granted = granted
        self.shouldShowSettings = shouldShowSettings
    }
}

/// Print options
public struct PrintOptions {
    public var beep: Bool?
    public var cut: Bool?
    public var tailingLine: Bool?
    public var encoding: String?

    public init(beep: Bool? = nil, cut: Bool? = nil, tailingLine: Bool? = nil, encoding: String? = nil) {
        self.beep = beep
        self.cut = cut
        self.tailingLine = tailingLine
        self.encoding = encoding
    }
}

/// Image print options
public struct ImagePrintOptions: Codable {
    public var beep: Bool?
    public var cut: Bool?
    public var tailingLine: Bool?
    public var encoding: String?
    public var imageWidth: Int?
    public var imageHeight: Int?
    public var printerWidthType: Int?
    public var paddingX: Int?

    public init() {}
}

/// HybridBLEPrinter - Swift implementation of BLE thermal printer
/// Uses Nitro Modules for high-performance JS bridge
public class HybridBLEPrinter {

    private let printerSDK = PrinterSDKBridge.shared
    private let connectionManager = ConnectionManager()
    private let printQueue = PrintQueue()
    private let imageCache = ImageCache(maxSize: 10)

    private var printerArray: [Printer] = []
    private var currentPrinter: Printer?
    private var scanCompletion: (([BLEDevice]) -> Void)?

    private var stateListeners: [UUID: (ConnectionState) -> Void] = [:]

    public init() {
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

    public func initialize() async throws {
        printerArray = []
        // PrinterSDK initializes automatically
    }

    public func dispose() {
        printerSDK.disconnect()
        printerArray = []
        currentPrinter = nil
    }

    // MARK: - Device Discovery

    public func getDeviceList() async throws -> [BLEDevice] {
        return await withCheckedContinuation { continuation in
            var devices: [BLEDevice] = []
            var timeoutTask: Task<Void, Never>?
            var hasResumed = false
            let resumeLock = NSLock()

            printerArray = []

            printerSDK.scanPrinters { [weak self] printer in
                guard let printer = printer else {
                    // Scan complete
                    timeoutTask?.cancel()
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
                let device = BLEDevice(deviceName: printer.name, innerMacAddress: printer.uuidString)

                if !devices.contains(where: { $0.innerMacAddress == device.innerMacAddress }) {
                    devices.append(device)
                }
            }

            // Timeout after 5 seconds
            timeoutTask = Task {
                try? await Task.sleep(nanoseconds: 5_000_000_000)
                if !Task.isCancelled {
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

    public func connectPrinter(innerMacAddress: String) async throws -> BLEDevice {
        connectionManager.setConnecting()

        guard let printer = printerArray.first(where: { $0.uuidString == innerMacAddress }) else {
            connectionManager.setDisconnected()
            throw PrinterError.deviceNotFound
        }

        printerSDK.connectBT(printer)
        currentPrinter = printer

        // Wait for connection notification
        try await Task.sleep(nanoseconds: 1_000_000_000) // 1 second

        connectionManager.setConnected(deviceId: innerMacAddress)
        return BLEDevice(deviceName: printer.name, innerMacAddress: printer.uuidString)
    }

    public func closeConnection() async throws {
        printerSDK.disconnect()
        currentPrinter = nil
        connectionManager.setDisconnected()
    }

    // MARK: - Connection State

    public func isConnected() -> String? {
        return connectionManager.getConnectedDeviceId()
    }

    public func getConnectionState() -> String {
        return connectionManager.state.rawValue
    }

    public func onConnectionStateChange(_ callback: @escaping (String) -> Void) -> () -> Void {
        let id = UUID()
        let listener: (ConnectionState) -> Void = { state in
            callback(state.rawValue)
        }
        stateListeners[id] = listener
        return { [weak self] in
            self?.stateListeners.removeValue(forKey: id)
        }
    }

    // MARK: - Auto-Reconnection

    public func enableAutoReconnect(_ enabled: Bool) {
        connectionManager.enableAutoReconnect(enabled)
    }

    public func setReconnectAttempts(_ maxAttempts: Int) {
        connectionManager.setMaxReconnectAttempts(maxAttempts)
    }

    public func setReconnectDelay(_ delayMs: Int) {
        connectionManager.setReconnectDelay(delayMs)
    }

    // MARK: - Print Status

    public func isPrinting() async -> Bool {
        return await printQueue.isPrinting
    }

    public func getPrintQueue() async -> [PrintJobStatus] {
        return await printQueue.getQueueStatus()
    }

    // MARK: - Print Methods

    public func printText(_ text: String, options: PrintOptions? = nil) async throws -> PrintJobStatus {
        let job = await printQueue.enqueue { [weak self] in
            guard self?.currentPrinter != nil else {
                throw PrinterError.notConnected
            }

            self?.printerSDK.printText(text)

            if options?.beep == true {
                self?.printerSDK.beep()
            }
            if options?.cut == true {
                self?.printerSDK.cutPaper()
            }
        }

        return await printQueue.awaitJob(id: job.id)
    }

    public func printBill(_ text: String, options: PrintOptions? = nil) async throws -> PrintJobStatus {
        var billOptions = options ?? PrintOptions()
        billOptions.beep = billOptions.beep ?? true
        billOptions.cut = billOptions.cut ?? true
        billOptions.tailingLine = billOptions.tailingLine ?? true

        return try await printText(text, options: billOptions)
    }

    public func printRaw(_ data: String) async throws -> PrintJobStatus {
        let job = await printQueue.enqueue { [weak self] in
            guard self?.currentPrinter != nil else {
                throw PrinterError.notConnected
            }

            guard let decodedData = Data(base64Encoded: data),
                  let text = String(data: decodedData, encoding: .utf8) else {
                throw PrinterError.invalidData
            }

            self?.printerSDK.printText(text)
        }

        return await printQueue.awaitJob(id: job.id)
    }

    public func printImage(_ imageUrl: String, options: ImagePrintOptions? = nil) async throws -> PrintJobStatus {
        let job = await printQueue.enqueue { [weak self] in
            guard self?.currentPrinter != nil else {
                throw PrinterError.notConnected
            }

            let image = try await self?.imageCache.downloadImage(from: imageUrl)
            guard let image = image else {
                throw PrinterError.imageLoadFailed
            }

            let processedImage = self?.processImage(image, options: options) ?? image
            let printerWidth = options?.printerWidthType == 58 ? 384 : 576

            self?.printerSDK.setPrintWidth(printerWidth)
            self?.printerSDK.printImage(processedImage)

            if options?.beep == true {
                self?.printerSDK.beep()
            }
            if options?.cut == true {
                self?.printerSDK.cutPaper()
            }
        }

        return await printQueue.awaitJob(id: job.id)
    }

    public func printImageBase64(_ base64: String, options: ImagePrintOptions? = nil) async throws -> PrintJobStatus {
        let job = await printQueue.enqueue { [weak self] in
            guard self?.currentPrinter != nil else {
                throw PrinterError.notConnected
            }

            guard let data = Data(base64Encoded: base64),
                  let image = UIImage(data: data) else {
                throw PrinterError.invalidData
            }

            let processedImage = self?.processImage(image, options: options) ?? image
            let printerWidth = options?.printerWidthType == 58 ? 384 : 576

            self?.printerSDK.setPrintWidth(printerWidth)
            self?.printerSDK.printImage(processedImage)

            if options?.beep == true {
                self?.printerSDK.beep()
            }
            if options?.cut == true {
                self?.printerSDK.cutPaper()
            }
        }

        return await printQueue.awaitJob(id: job.id)
    }

    public func printColumnsText(
        _ texts: [String],
        columnWidths: [Int],
        columnAlignments: [Int],
        columnStyles: [String]? = nil,
        options: PrintOptions? = nil
    ) async throws -> PrintJobStatus {
        let result = processColumnText(texts, columnWidths: columnWidths, columnAlignments: columnAlignments)
        return try await printText(result, options: options)
    }

    // MARK: - Image Caching

    public func cacheImage(_ url: String, key: String) async throws {
        try await imageCache.cacheFromUrl(url, key: key)
    }

    public func printCachedImage(_ key: String, options: ImagePrintOptions? = nil) async throws -> PrintJobStatus {
        guard let image = imageCache.get(key: key) else {
            throw PrinterError.imageNotCached
        }

        let job = await printQueue.enqueue { [weak self] in
            guard self?.currentPrinter != nil else {
                throw PrinterError.notConnected
            }

            let processedImage = self?.processImage(image, options: options) ?? image
            let printerWidth = options?.printerWidthType == 58 ? 384 : 576

            self?.printerSDK.setPrintWidth(printerWidth)
            self?.printerSDK.printImage(processedImage)

            if options?.beep == true {
                self?.printerSDK.beep()
            }
            if options?.cut == true {
                self?.printerSDK.cutPaper()
            }
        }

        return await printQueue.awaitJob(id: job.id)
    }

    public func clearImageCache() {
        imageCache.clear()
    }

    // MARK: - Permissions

    public func askPermissions() async -> PermissionResultData {
        // iOS handles Bluetooth permissions through Info.plist
        // The system will prompt when needed
        return PermissionResultData(granted: true, shouldShowSettings: false)
    }

    // MARK: - Image Processing

    private func processImage(_ image: UIImage, options: ImagePrintOptions?) -> UIImage {
        let newWidth = CGFloat(options?.imageWidth ?? 150)
        let aspectRatio = image.size.height / image.size.width
        let newHeight = options?.imageHeight != nil ? CGFloat(options!.imageHeight!) : newWidth * aspectRatio
        let paddingX = CGFloat(options?.paddingX ?? 0)

        // Resize image
        let newSize = CGSize(width: newWidth, height: newHeight)
        UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
        defer { UIGraphicsEndImageContext() }

        guard let context = UIGraphicsGetCurrentContext() else { return image }
        context.interpolationQuality = .high
        image.draw(in: CGRect(origin: .zero, size: newSize))

        guard let resizedImage = UIGraphicsGetImageFromCurrentImageContext() else { return image }

        // Add padding if needed
        if paddingX > 0 {
            return addPadding(to: resizedImage, paddingX: paddingX)
        }

        return resizedImage
    }

    private func addPadding(to image: UIImage, paddingX: CGFloat) -> UIImage {
        let newSize = CGSize(width: image.size.width + paddingX, height: image.size.height)

        UIGraphicsBeginImageContextWithOptions(newSize, true, 1.0)
        defer { UIGraphicsEndImageContext() }

        guard let context = UIGraphicsGetCurrentContext() else { return image }
        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(origin: .zero, size: newSize))

        let origin = CGPoint(x: paddingX / 2, y: 0)
        image.draw(at: origin)

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
                    let breakPoint = text.prefix(width).lastIndex(of: " ").map { text.distance(from: text.startIndex, to: $0) } ?? width
                    remainingTexts[i] = String(text.dropFirst(breakPoint)).trimmingCharacters(in: .whitespaces)
                    text = String(text.prefix(breakPoint))
                    hasMore = true
                } else {
                    remainingTexts[i] = ""
                }

                // Apply alignment
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
            connectionManager.setConnected(deviceId: deviceId)
            stateListeners.values.forEach { $0(.connected) }
        }
    }

    @objc private func handlePrinterDisconnected() {
        connectionManager.setDisconnected()
        stateListeners.values.forEach { $0(connectionManager.state) }

        // Handle auto-reconnection
        if connectionManager.shouldReconnect() {
            Task {
                try? await Task.sleep(nanoseconds: UInt64(connectionManager.getReconnectDelay()) * 1_000_000)
                if let deviceId = connectionManager.getReconnectDeviceId(),
                   let printer = printerArray.first(where: { $0.uuidString == deviceId }) {
                    connectionManager.incrementReconnectAttempts()
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
        case .notInitialized:
            return "Printer not initialized. Call init() first."
        case .notConnected:
            return "Not connected to printer. Call connectPrinter() first."
        case .deviceNotFound:
            return "Device not found in discovered printers."
        case .connectionFailed:
            return "Failed to connect to printer."
        case .invalidData:
            return "Invalid data format."
        case .imageLoadFailed:
            return "Failed to load image."
        case .imageNotCached:
            return "Image not found in cache."
        case .permissionDenied:
            return "Bluetooth permission denied."
        }
    }
}
