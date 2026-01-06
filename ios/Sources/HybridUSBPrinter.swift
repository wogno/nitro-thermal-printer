import Foundation
import NitroModules

/// HybridUSBPrinter - Swift stub for USB thermal printer
/// USB printing is NOT supported on iOS - this is a stub implementation
/// Implements Nitro-generated HybridUSBPrinterSpec protocol
public class HybridUSBPrinter: HybridUSBPrinterSpec {

    // MARK: - HybridObject Requirements
    public var memorySize: Int {
        return MemoryLayout<HybridUSBPrinter>.size
    }

    // MARK: - Error

    private enum USBError: Error, LocalizedError {
        case notSupported

        var errorDescription: String? {
            switch self {
            case .notSupported:
                return "USB printing is not supported on iOS"
            }
        }
    }

    // MARK: - Initialization

    public override init() {
        super.init()
    }

    // MARK: - Lifecycle

    public func initialize() throws -> Promise<Void> {
        return Promise.resolved(withResult: ())
    }

    // MARK: - Device Discovery

    public func getDeviceList() throws -> Promise<[USBDevice]> {
        // USB not supported on iOS - return empty list
        return Promise.resolved(withResult: [])
    }

    // MARK: - Connection

    public func connectPrinter(vendorId: Double, productId: Double) throws -> Promise<USBDevice> {
        return Promise.async {
            throw USBError.notSupported
        }
    }

    public func closeConnection() throws -> Promise<Void> {
        return Promise.resolved(withResult: ())
    }

    public func isConnected()  -> String? {
        return nil
    }

    public func getConnectionState()  -> ConnectionState {
        return .disconnected
    }

    public func addConnectionStateListener(callback: @escaping (_ state: ConnectionState) -> Void) throws -> String {
        // No-op, return empty subscription ID
        return UUID().uuidString
    }

    public func removeConnectionStateListener(subscriptionId: String) throws {
        // No-op
    }

    // MARK: - Device Event Listeners

    public func addDeviceAttachedListener(callback: @escaping (_ device: USBDevice) -> Void) throws -> String {
        // No-op on iOS
        return UUID().uuidString
    }

    public func removeDeviceAttachedListener(subscriptionId: String) throws {
        // No-op
    }

    public func addDeviceDetachedListener(callback: @escaping () -> Void) throws -> String {
        // No-op on iOS
        return UUID().uuidString
    }

    public func removeDeviceDetachedListener(subscriptionId: String) throws {
        // No-op
    }

    // MARK: - Print Status

    public func isPrinting()  -> Bool {
        return false
    }

    public func getPrintQueue()  -> [PrintJobStatus] {
        return []
    }

    // MARK: - Print Methods

    public func printText(text: String, options: PrintOptions) throws -> Promise<PrintJobStatus> {
        return Promise.async {
            throw USBError.notSupported
        }
    }

    public func printBill(text: String, options: PrintOptions) throws -> Promise<PrintJobStatus> {
        return Promise.async {
            throw USBError.notSupported
        }
    }

    public func printRaw(data: String) throws -> Promise<PrintJobStatus> {
        return Promise.async {
            throw USBError.notSupported
        }
    }

    public func printImage(imageUrl: String, options: ImagePrintOptions) throws -> Promise<PrintJobStatus> {
        return Promise.async {
            throw USBError.notSupported
        }
    }

    public func printImageBase64(base64: String, options: ImagePrintOptions) throws -> Promise<PrintJobStatus> {
        return Promise.async {
            throw USBError.notSupported
        }
    }

    public func printColumnsText(
        texts: [String],
        columnWidths: [Double],
        columnAlignments: [Double],
        columnStyles: [String],
        options: PrintOptions
    ) throws -> Promise<PrintJobStatus> {
        return Promise.async {
            throw USBError.notSupported
        }
    }

    // MARK: - Sync Print Methods (Stubs - USB not supported on iOS)

    public func printTextSync(text: String, options: PrintOptions) -> String {
        // Return empty jobId - USB not supported
        return ""
    }

    public func printBillSync(text: String, options: PrintOptions) -> String {
        // Return empty jobId - USB not supported
        return ""
    }

    public func printRawSync(data: String) -> String {
        // Return empty jobId - USB not supported
        return ""
    }

    public func printImageBase64Sync(base64: String, options: ImagePrintOptions) -> String {
        // Return empty jobId - USB not supported
        return ""
    }

    public func printColumnsTextSync(
        texts: [String],
        columnWidths: [Double],
        columnAlignments: [Double],
        columnStyles: [String],
        options: PrintOptions
    ) -> String {
        // Return empty jobId - USB not supported
        return ""
    }

    public func getJobStatus(jobId: String) -> PrintJobStatus? {
        // Return nil - USB not supported
        return nil
    }

    // MARK: - Image Caching

    public func cacheImage(url: String, key: String) throws -> Promise<Void> {
        return Promise.async {
            throw USBError.notSupported
        }
    }

    public func printCachedImage(key: String, options: ImagePrintOptions) throws -> Promise<PrintJobStatus> {
        return Promise.async {
            throw USBError.notSupported
        }
    }

    public func clearImageCache() throws {
        // No-op
    }

    // MARK: - Permissions

    public func askPermissions() throws -> Promise<PermissionResult> {
        // USB not supported on iOS, but return granted since there's nothing to request
        return Promise.resolved(withResult: PermissionResult(granted: false, shouldShowSettings: false))
    }
}
