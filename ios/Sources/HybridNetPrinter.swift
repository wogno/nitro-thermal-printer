import Foundation
import UIKit
import Network
import NitroModules

/// HybridNetPrinter - Swift implementation of Network thermal printer
/// Implements Nitro-generated HybridNetPrinterSpec protocol
public class HybridNetPrinter: HybridNetPrinterSpec {

    // MARK: - HybridObject Requirements
    public var memorySize: Int {
        return MemoryLayout<HybridNetPrinter>.size
    }

    // MARK: - Private Properties

    private let printerSDK = PrinterSDKBridge.shared
    private let imageCache = ImageCache(maxSize: 10)

    private var currentDevice: NetDevice?
    private var connectionState: ConnectionState = .disconnected
    private var connectedDeviceId: String?
    private var isPrintingState = false
    private var printJobs: [String: PrintJobStatus] = [:]

    private var stateListeners: [String: (ConnectionState) -> Void] = [:]
    private var scanProgressListeners: [String: (Double) -> Void] = [:]

    // MARK: - Initialization

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
        return Promise.resolved(withResult: ())
    }

    // MARK: - Device Discovery

    public func getDeviceList() throws -> Promise<[NetDevice]> {
        return Promise.resolved(withResult: [])
    }

    public func scanNetwork(timeout: Double) throws -> Promise<[NetDevice]> {
        return Promise.async { [weak self] in
            guard let self = self else { return [] }

            var devices: [NetDevice] = []

            guard let localIP = self.getLocalIPAddress() else {
                return devices
            }

            let prefix = localIP.components(separatedBy: ".").dropLast().joined(separator: ".") + "."
            let selfSuffix = Int(localIP.components(separatedBy: ".").last ?? "0") ?? 0

            var completed = 0
            let total = 254

            for i in 1...254 where i != selfSuffix {
                let host = prefix + String(i)

                let isOpen = await self.isPortOpen(host: host, port: 9100, timeout: 100)
                if isOpen {
                    devices.append(NetDevice(host: host, port: 9100, deviceName: nil))
                }

                completed += 1
                let progress = (Double(completed) * 100.0) / Double(total)
                self.scanProgressListeners.values.forEach { $0(progress) }
            }

            return devices
        }
    }

    public func addScanProgressListener(callback: @escaping (_ progress: Double) -> Void) throws -> String {
        let subscriptionId = UUID().uuidString
        scanProgressListeners[subscriptionId] = callback
        return subscriptionId
    }

    public func removeScanProgressListener(subscriptionId: String) throws {
        scanProgressListeners.removeValue(forKey: subscriptionId)
    }

    private func isPortOpen(host: String, port: Int, timeout: Int) async -> Bool {
        return await withCheckedContinuation { continuation in
            var hasResumed = false
            let resumeLock = NSLock()

            let connection = NWConnection(
                host: NWEndpoint.Host(host),
                port: NWEndpoint.Port(integerLiteral: UInt16(port)),
                using: .tcp
            )

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    connection.cancel()
                    resumeLock.lock()
                    if !hasResumed {
                        hasResumed = true
                        resumeLock.unlock()
                        continuation.resume(returning: true)
                    } else {
                        resumeLock.unlock()
                    }
                case .failed, .cancelled:
                    resumeLock.lock()
                    if !hasResumed {
                        hasResumed = true
                        resumeLock.unlock()
                        continuation.resume(returning: false)
                    } else {
                        resumeLock.unlock()
                    }
                default:
                    break
                }
            }

            connection.start(queue: .global())

            DispatchQueue.global().asyncAfter(deadline: .now() + .milliseconds(timeout)) {
                connection.cancel()
            }
        }
    }

    private func getLocalIPAddress() -> String? {
        var address: String?
        var ifaddr: UnsafeMutablePointer<ifaddrs>?

        if getifaddrs(&ifaddr) == 0 {
            var ptr = ifaddr
            while ptr != nil {
                defer { ptr = ptr?.pointee.ifa_next }

                guard let interface = ptr?.pointee,
                      let addr = interface.ifa_addr else { continue }

                let addrFamily = addr.pointee.sa_family
                if addrFamily == UInt8(AF_INET) {
                    let name = String(cString: interface.ifa_name)
                    if name == "en0" {
                        var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                        getnameinfo(addr, socklen_t(addr.pointee.sa_len),
                                   &hostname, socklen_t(hostname.count), nil, 0, NI_NUMERICHOST)
                        address = String(cString: hostname)
                    }
                }
            }
            freeifaddrs(ifaddr)
        }

        return address
    }

    // MARK: - Connection

    public func connectPrinter(host: String, port: Double, timeout: Double) throws -> Promise<NetDevice> {
        return Promise.async { [weak self] in
            guard let self = self else {
                throw PrinterError.notConnected
            }

            self.setConnectionState(.connecting)

            let success = self.printerSDK.connectIP(host)

            if success {
                let device = NetDevice(host: host, port: port, deviceName: "\(host):\(Int(port))")
                self.currentDevice = device
                self.connectedDeviceId = "\(host):\(Int(port))"
                self.setConnectionState(.connected)
                return device
            } else {
                self.setConnectionState(.disconnected)
                throw PrinterError.connectionFailed
            }
        }
    }

    public func closeConnection() throws -> Promise<Void> {
        return Promise.async { [weak self] in
            self?.printerSDK.disconnect()
            self?.currentDevice = nil
            self?.setConnectionState(.disconnected)
        }
    }

    // MARK: - Connection State

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

    private func setConnectionState(_ state: ConnectionState) {
        connectionState = state
        if state == .disconnected {
            connectedDeviceId = nil
        }
        stateListeners.values.forEach { $0(state) }
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

                guard self.currentDevice != nil else {
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

                guard self.currentDevice != nil else {
                    throw PrinterError.notConnected
                }

                guard let decodedData = Data(base64Encoded: data),
                      let text = String(data: decodedData, encoding: .utf8) else {
                    throw PrinterError.invalidData
                }

                self.printerSDK.printText(text)

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

                guard self.currentDevice != nil else {
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

                guard self.currentDevice != nil else {
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
        let columnText = processColumnText(
            texts: texts,
            columnWidths: columnWidths.map { Int($0) },
            columnAlignments: columnAlignments.map { Int($0) },
            columnStyles: columnStyles
        )
        return try printText(text: columnText, options: options)
    }

    private func processColumnText(
        texts: [String],
        columnWidths: [Int],
        columnAlignments: [Int],
        columnStyles: [String]
    ) -> String {
        var lines: [String] = []
        var remainingTexts = texts

        var hasMore = true
        while hasMore {
            var lineBuilder = ""
            hasMore = false

            for i in 0..<texts.count {
                let width = i < columnWidths.count ? columnWidths[i] : 10
                let alignment = i < columnAlignments.count ? columnAlignments[i] : 0
                var text = i < remainingTexts.count ? remainingTexts[i] : ""

                if text.count > width {
                    let breakIndex = text.index(text.startIndex, offsetBy: width)
                    let substring = String(text[..<breakIndex])
                    if let lastSpace = substring.lastIndex(of: " ") {
                        remainingTexts[i] = String(text[text.index(after: lastSpace)...]).trimmingCharacters(in: .whitespaces)
                        text = String(text[..<lastSpace])
                    } else {
                        remainingTexts[i] = String(text[breakIndex...])
                        text = substring
                    }
                    hasMore = true
                } else {
                    remainingTexts[i] = ""
                }

                let paddedText: String
                switch alignment {
                case 1: // Center
                    let padding = (width - text.count) / 2
                    paddedText = String(repeating: " ", count: padding) + text + String(repeating: " ", count: width - text.count - padding)
                case 2: // Right
                    paddedText = String(repeating: " ", count: width - text.count) + text
                default: // Left
                    paddedText = text + String(repeating: " ", count: width - text.count)
                }
                lineBuilder += paddedText
            }
            lines.append(lineBuilder)
        }

        return lines.joined(separator: "\n")
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

                guard self.currentDevice != nil else {
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
        return Promise.resolved(withResult: PermissionResult(granted: true, shouldShowSettings: false))
    }

    // MARK: - Notification Handlers

    @objc private func handlePrinterConnected() {
        if let device = currentDevice {
            connectedDeviceId = "\(device.host):\(Int(device.port))"
            setConnectionState(.connected)
        }
    }

    @objc private func handlePrinterDisconnected() {
        setConnectionState(.disconnected)
    }

    // MARK: - Image Processing

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
}
