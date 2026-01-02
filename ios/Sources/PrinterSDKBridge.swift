import Foundation
import UIKit
#if !targetEnvironment(simulator)
import PrinterSDK
#endif

/// Bridge to the Objective-C PrinterSDK library
/// This class wraps the PrinterSDK calls and provides a Swift-friendly interface
@objc public class PrinterSDKBridge: NSObject {

    /// Shared singleton instance
    public static let shared = PrinterSDKBridge()

    #if !targetEnvironment(simulator)
    /// Reference to the PrinterSDK singleton
    private var sdk: PrinterSDK {
        return PrinterSDK.default()
    }
    #endif

    private override init() {
        super.init()
    }

    // MARK: - BLE Methods

    /// Scan for BLE printers
    /// - Parameter completion: Called with each discovered printer, nil when scan completes
    public func scanPrinters(completion: @escaping (Any?) -> Void) {
        #if !targetEnvironment(simulator)
        sdk.scanPrinters { printer in
            completion(printer)
        }
        #else
        print("[PrinterSDK] Simulator: scanPrinters not available")
        completion(nil)
        #endif
    }

    /// Stop scanning for printers
    public func stopScan() {
        #if !targetEnvironment(simulator)
        sdk.stopScanPrinters()
        #else
        print("[PrinterSDK] Simulator: stopScan not available")
        #endif
    }

    /// Connect to a BLE printer
    /// - Parameter printer: The printer to connect to
    public func connectBT(_ printer: Any) {
        #if !targetEnvironment(simulator)
        if let p = printer as? Printer {
            sdk.connectBT(p)
        }
        #else
        print("[PrinterSDK] Simulator: connectBT not available")
        #endif
    }

    // MARK: - Network Methods

    /// Connect to a network printer
    /// - Parameter host: IP address of the printer
    /// - Returns: true if connection was initiated successfully
    @discardableResult
    public func connectIP(_ host: String) -> Bool {
        #if !targetEnvironment(simulator)
        return sdk.connectIP(host)
        #else
        print("[PrinterSDK] Simulator: connectIP not available")
        return false
        #endif
    }

    // MARK: - Connection Management

    /// Disconnect from the current printer
    public func disconnect() {
        #if !targetEnvironment(simulator)
        sdk.disconnect()
        #else
        print("[PrinterSDK] Simulator: disconnect not available")
        #endif
    }

    // MARK: - Print Methods

    /// Print text
    /// - Parameter text: Text to print
    public func printText(_ text: String) {
        #if !targetEnvironment(simulator)
        sdk.printText(text)
        #else
        print("[PrinterSDK] Simulator: printText not available - \(text)")
        #endif
    }

    /// Print text as image (for complex formatting)
    /// - Parameter text: Text to print as image
    public func printTextImage(_ text: String) {
        #if !targetEnvironment(simulator)
        sdk.printTextImage(text)
        #else
        print("[PrinterSDK] Simulator: printTextImage not available")
        #endif
    }

    /// Print an image
    /// - Parameter image: UIImage to print
    public func printImage(_ image: UIImage) {
        #if !targetEnvironment(simulator)
        sdk.print(image)
        #else
        print("[PrinterSDK] Simulator: printImage not available")
        #endif
    }

    /// Send raw hex commands
    /// - Parameter hex: Hex string to send
    public func sendHex(_ hex: String) {
        #if !targetEnvironment(simulator)
        sdk.sendHex(hex)
        #else
        print("[PrinterSDK] Simulator: sendHex not available")
        #endif
    }

    // MARK: - Printer Commands

    /// Cut the paper
    public func cutPaper() {
        #if !targetEnvironment(simulator)
        sdk.cutPaper()
        #else
        print("[PrinterSDK] Simulator: cutPaper not available")
        #endif
    }

    /// Beep the printer
    public func beep() {
        #if !targetEnvironment(simulator)
        sdk.beep()
        #else
        print("[PrinterSDK] Simulator: beep not available")
        #endif
    }

    /// Open the cash drawer
    public func openCashDrawer() {
        #if !targetEnvironment(simulator)
        sdk.openCasher()
        #else
        print("[PrinterSDK] Simulator: openCashDrawer not available")
        #endif
    }

    /// Set the print width
    /// - Parameter width: Width in pixels (576 for 80mm, 384 for 58mm)
    public func setPrintWidth(_ width: Int) {
        #if !targetEnvironment(simulator)
        sdk.setPrintWidth(width)
        #else
        print("[PrinterSDK] Simulator: setPrintWidth not available")
        #endif
    }

    /// Set font size multiplier
    /// - Parameter multiple: Font size multiplier
    public func setFontSizeMultiple(_ multiple: Int) {
        #if !targetEnvironment(simulator)
        sdk.setFontSizeMultiple(multiple)
        #else
        print("[PrinterSDK] Simulator: setFontSizeMultiple not available")
        #endif
    }

    // MARK: - Barcode & QR

    /// Print a barcode
    /// - Parameters:
    ///   - text: Barcode content
    ///   - type: Barcode type
    public func printCodeBar(_ text: String, type: Int) {
        #if !targetEnvironment(simulator)
        sdk.printCodeBar(text, type: CodeBarType(UInt32(type)))
        #else
        print("[PrinterSDK] Simulator: printCodeBar not available")
        #endif
    }

    /// Print a QR code
    /// - Parameter text: QR code content
    public func printQrCode(_ text: String) {
        #if !targetEnvironment(simulator)
        sdk.printQrCode(text)
        #else
        print("[PrinterSDK] Simulator: printQrCode not available")
        #endif
    }

    // MARK: - Test

    /// Print a test page
    public func printTestPaper() {
        #if !targetEnvironment(simulator)
        sdk.printTestPaper()
        #else
        print("[PrinterSDK] Simulator: printTestPaper not available")
        #endif
    }

    /// Perform self-test
    public func selfTest() {
        #if !targetEnvironment(simulator)
        sdk.selfTest()
        #else
        print("[PrinterSDK] Simulator: selfTest not available")
        #endif
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let printerConnected = Notification.Name("PrinterConnectedNotification")
    static let printerDisconnected = Notification.Name("PrinterDisconnectedNotification")
}
