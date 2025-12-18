import Foundation
import UIKit

/// Bridge to the Objective-C PrinterSDK library
/// This class wraps the PrinterSDK calls and provides a Swift-friendly interface
@objc public class PrinterSDKBridge: NSObject {

    /// Shared singleton instance
    public static let shared = PrinterSDKBridge()

    /// Reference to the PrinterSDK singleton
    private var sdk: PrinterSDK {
        return PrinterSDK.defaultPrinterSDK()
    }

    private override init() {
        super.init()
    }

    // MARK: - BLE Methods

    /// Scan for BLE printers
    /// - Parameter completion: Called with each discovered printer, nil when scan completes
    public func scanPrinters(completion: @escaping (Printer?) -> Void) {
        sdk.scanPrinters { printer in
            completion(printer)
        }
    }

    /// Stop scanning for printers
    public func stopScan() {
        sdk.stopScanPrinters()
    }

    /// Connect to a BLE printer
    /// - Parameter printer: The printer to connect to
    public func connectBT(_ printer: Printer) {
        sdk.connectBT(printer)
    }

    // MARK: - Network Methods

    /// Connect to a network printer
    /// - Parameter host: IP address of the printer
    /// - Returns: true if connection was initiated successfully
    @discardableResult
    public func connectIP(_ host: String) -> Bool {
        return sdk.connectIP(host)
    }

    // MARK: - Connection Management

    /// Disconnect from the current printer
    public func disconnect() {
        sdk.disconnect()
    }

    // MARK: - Print Methods

    /// Print text
    /// - Parameter text: Text to print
    public func printText(_ text: String) {
        sdk.printText(text)
    }

    /// Print text as image (for complex formatting)
    /// - Parameter text: Text to print as image
    public func printTextImage(_ text: String) {
        sdk.printTextImage(text)
    }

    /// Print an image
    /// - Parameter image: UIImage to print
    public func printImage(_ image: UIImage) {
        sdk.printImage(image)
    }

    /// Send raw hex commands
    /// - Parameter hex: Hex string to send
    public func sendHex(_ hex: String) {
        sdk.sendHex(hex)
    }

    // MARK: - Printer Commands

    /// Cut the paper
    public func cutPaper() {
        sdk.cutPaper()
    }

    /// Beep the printer
    public func beep() {
        sdk.beep()
    }

    /// Open the cash drawer
    public func openCashDrawer() {
        sdk.openCasher()
    }

    /// Set the print width
    /// - Parameter width: Width in pixels (576 for 80mm, 384 for 58mm)
    public func setPrintWidth(_ width: Int) {
        sdk.setPrintWidth(width)
    }

    /// Set font size multiplier
    /// - Parameter multiple: Font size multiplier
    public func setFontSizeMultiple(_ multiple: Int) {
        sdk.setFontSizeMultiple(multiple)
    }

    // MARK: - Barcode & QR

    /// Print a barcode
    /// - Parameters:
    ///   - text: Barcode content
    ///   - type: Barcode type
    public func printCodeBar(_ text: String, type: CodeBarType) {
        sdk.printCodeBar(text, type: type)
    }

    /// Print a QR code
    /// - Parameter text: QR code content
    public func printQrCode(_ text: String) {
        sdk.printQrCode(text)
    }

    // MARK: - Test

    /// Print a test page
    public func printTestPaper() {
        sdk.printTestPaper()
    }

    /// Perform self-test
    public func selfTest() {
        sdk.selfTest()
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let printerConnected = Notification.Name("PrinterConnectedNotification")
    static let printerDisconnected = Notification.Name("PrinterDisconnectedNotification")
}
