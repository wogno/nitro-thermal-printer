import Foundation
import UIKit
import Network

/// Network Device data structure
public struct NetDevice: Codable {
    public let host: String
    public let port: Int
    public var deviceName: String

    public init(host: String, port: Int, deviceName: String? = nil) {
        self.host = host
        self.port = port
        self.deviceName = deviceName ?? "\(host):\(port)"
    }
}

/// HybridNetPrinter - Swift implementation of Network thermal printer
public class HybridNetPrinter {

    private let printerSDK = PrinterSDKBridge.shared
    private let connectionManager = ConnectionManager()
    private let printQueue = PrintQueue()
    private let imageCache = ImageCache(maxSize: 10)

    private var currentDevice: NetDevice?
    private var stateListeners: [UUID: (ConnectionState) -> Void] = [:]
    private var scanProgressListeners: [UUID: (Int) -> Void] = [:]

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
        // Network printer doesn't need special initialization
    }

    public func dispose() {
        printerSDK.disconnect()
        currentDevice = nil
    }

    // MARK: - Device Discovery

    public func getDeviceList() async -> [NetDevice] {
        return []
    }

    public func scanNetwork(timeout: Int = 5000) async -> [NetDevice] {
        var devices: [NetDevice] = []

        guard let localIP = getLocalIPAddress() else {
            return devices
        }

        let prefix = localIP.components(separatedBy: ".").dropLast().joined(separator: ".") + "."
        let selfSuffix = Int(localIP.components(separatedBy: ".").last ?? "0") ?? 0

        var completed = 0
        let total = 254

        for i in 1...254 where i != selfSuffix {
            let host = prefix + String(i)

            if await isPortOpen(host: host, port: 9100, timeout: 100) {
                devices.append(NetDevice(host: host, port: 9100))
            }

            completed += 1
            let progress = (completed * 100) / total
            scanProgressListeners.values.forEach { $0(progress) }
        }

        return devices
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

    public func onScanProgress(_ callback: @escaping (Int) -> Void) -> () -> Void {
        let id = UUID()
        scanProgressListeners[id] = callback
        return { [weak self] in
            self?.scanProgressListeners.removeValue(forKey: id)
        }
    }

    // MARK: - Connection

    public func connectPrinter(host: String, port: Int = 9100, timeout: Int = 4000) async throws -> NetDevice {
        connectionManager.setConnecting()

        let success = printerSDK.connectIP(host)

        if success {
            let device = NetDevice(host: host, port: port)
            currentDevice = device
            connectionManager.setConnected(deviceId: "\(host):\(port)")
            return device
        } else {
            connectionManager.setDisconnected()
            throw PrinterError.connectionFailed
        }
    }

    public func closeConnection() async throws {
        printerSDK.disconnect()
        currentDevice = nil
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
            guard self?.currentDevice != nil else {
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
            guard self?.currentDevice != nil else {
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
            guard self?.currentDevice != nil else {
                throw PrinterError.notConnected
            }

            let image = try await self?.imageCache.downloadImage(from: imageUrl)
            guard let image = image else {
                throw PrinterError.imageLoadFailed
            }

            let printerWidth = options?.printerWidthType == 58 ? 384 : 576
            self?.printerSDK.setPrintWidth(printerWidth)
            self?.printerSDK.printImage(image)

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
            guard self?.currentDevice != nil else {
                throw PrinterError.notConnected
            }

            guard let data = Data(base64Encoded: base64),
                  let image = UIImage(data: data) else {
                throw PrinterError.invalidData
            }

            let printerWidth = options?.printerWidthType == 58 ? 384 : 576
            self?.printerSDK.setPrintWidth(printerWidth)
            self?.printerSDK.printImage(image)

            if options?.beep == true {
                self?.printerSDK.beep()
            }
            if options?.cut == true {
                self?.printerSDK.cutPaper()
            }
        }

        return await printQueue.awaitJob(id: job.id)
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
            guard self?.currentDevice != nil else {
                throw PrinterError.notConnected
            }

            let printerWidth = options?.printerWidthType == 58 ? 384 : 576
            self?.printerSDK.setPrintWidth(printerWidth)
            self?.printerSDK.printImage(image)

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
        return PermissionResultData(granted: true, shouldShowSettings: false)
    }

    // MARK: - Notification Handlers

    @objc private func handlePrinterConnected() {
        if let device = currentDevice {
            connectionManager.setConnected(deviceId: "\(device.host):\(device.port)")
            stateListeners.values.forEach { $0(.connected) }
        }
    }

    @objc private func handlePrinterDisconnected() {
        connectionManager.setDisconnected()
        stateListeners.values.forEach { $0(connectionManager.state) }
    }
}
