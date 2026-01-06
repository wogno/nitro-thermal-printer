"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
var _exportNames = {
  BLEPrinter: true,
  NetPrinter: true,
  USBPrinter: true,
  NetPrinterEventEmitter: true,
  RN_THERMAL_RECEIPT_PRINTER_EVENTS: true,
  COMMANDS: true,
  processColumnText: true
};
exports.BLEPrinter = void 0;
Object.defineProperty(exports, "COMMANDS", {
  enumerable: true,
  get: function () {
    return _printerCommands.COMMANDS;
  }
});
exports.USBPrinter = exports.RN_THERMAL_RECEIPT_PRINTER_EVENTS = exports.NetPrinterEventEmitter = exports.NetPrinter = void 0;
Object.defineProperty(exports, "processColumnText", {
  enumerable: true,
  get: function () {
    return _printColumn.processColumnText;
  }
});
var _reactNativeNitroModules = require("react-native-nitro-modules");
var _types = require("./specs/types");
Object.keys(_types).forEach(function (key) {
  if (key === "default" || key === "__esModule") return;
  if (Object.prototype.hasOwnProperty.call(_exportNames, key)) return;
  if (key in exports && exports[key] === _types[key]) return;
  Object.defineProperty(exports, key, {
    enumerable: true,
    get: function () {
      return _types[key];
    }
  });
});
var _printerCommands = require("./utils/printer-commands");
var _printColumn = require("./utils/print-column");
/**
 * React Native Thermal Receipt Printer v3
 *
 * High-performance thermal printer library using Nitro Modules.
 * Supports USB, BLE, and Network printers.
 */

// Re-export types

// Export utilities

// Legacy type exports for backward compatibility

// ============ Helper Functions ============

function toNitroPrintOptions(opts = {}) {
  return {
    beep: opts.beep ?? false,
    cut: opts.cut ?? false,
    tailingLine: opts.tailingLine ?? false,
    encoding: opts.encoding ?? 'UTF-8'
  };
}
function toNitroImageOptions(opts = {}) {
  return {
    beep: opts.beep ?? false,
    cut: opts.cut ?? false,
    tailingLine: opts.tailingLine ?? false,
    encoding: opts.encoding ?? 'UTF-8',
    imageWidth: opts.imageWidth ?? 0,
    imageHeight: opts.imageHeight ?? 0,
    printerWidthType: opts.printerWidthType ?? _types.PrinterWidthType.MM_80,
    paddingX: opts.paddingX ?? 0
  };
}

// ============ Hybrid Object Instances ============

let _blePrinter = null;
let _netPrinter = null;
let _usbPrinter = null;
function getBLEPrinter() {
  if (!_blePrinter) {
    _blePrinter = _reactNativeNitroModules.NitroModules.createHybridObject('BLEPrinter');
  }
  return _blePrinter;
}
function getNetPrinter() {
  if (!_netPrinter) {
    _netPrinter = _reactNativeNitroModules.NitroModules.createHybridObject('NetPrinter');
  }
  return _netPrinter;
}
function getUSBPrinter() {
  if (!_usbPrinter) {
    _usbPrinter = _reactNativeNitroModules.NitroModules.createHybridObject('USBPrinter');
  }
  return _usbPrinter;
}

// ============ BLE Printer API ============

/**
 * BLE Printer - Backward compatible API with new features
 */
const BLEPrinter = exports.BLEPrinter = {
  /**
   * Get the underlying Hybrid Object for advanced usage
   */
  getInstance: getBLEPrinter,
  /**
   * Initialize the BLE printer module
   */
  async init() {
    return getBLEPrinter().initialize();
  },
  /**
   * Get list of paired BLE devices
   */
  async getDeviceList() {
    const devices = await getBLEPrinter().getDeviceList();
    return devices.map(d => ({
      device_name: d.deviceName,
      inner_mac_address: d.innerMacAddress
    }));
  },
  /**
   * Connect to a BLE printer
   */
  async connectPrinter(inner_mac_address) {
    const device = await getBLEPrinter().connectPrinter(inner_mac_address);
    return {
      device_name: device.deviceName,
      inner_mac_address: device.innerMacAddress
    };
  },
  /**
   * Close current connection
   */
  async closeConn() {
    return getBLEPrinter().closeConnection();
  },
  // NEW: Connection state methods
  /**
   * Check if connected to a printer
   * @returns Device MAC address if connected, undefined otherwise
   */
  isConnected() {
    return getBLEPrinter().isConnected() ?? undefined;
  },
  /**
   * Get current connection state
   */
  getConnectionState() {
    return getBLEPrinter().getConnectionState();
  },
  /**
   * Listen to connection state changes
   * @returns Unsubscribe function
   */
  onConnectionStateChange(callback) {
    const subscriptionId = getBLEPrinter().addConnectionStateListener(callback);
    return () => {
      getBLEPrinter().removeConnectionStateListener(subscriptionId);
    };
  },
  // NEW: Auto-reconnection
  /**
   * Enable/disable auto-reconnection
   */
  enableAutoReconnect(enabled) {
    getBLEPrinter().enableAutoReconnect(enabled);
  },
  setReconnectAttempts(maxAttempts) {
    getBLEPrinter().setReconnectAttempts(maxAttempts);
  },
  setReconnectDelay(delayMs) {
    getBLEPrinter().setReconnectDelay(delayMs);
  },
  // NEW: Print status
  /**
   * Check if printer is currently printing
   */
  isPrinting() {
    return getBLEPrinter().isPrinting();
  },
  /**
   * Get print queue status
   */
  getPrintQueue() {
    return getBLEPrinter().getPrintQueue();
  },
  // Print methods - now return Promises
  /**
   * Print text
   */
  async printText(text, opts = {}) {
    return getBLEPrinter().printText(text, toNitroPrintOptions(opts));
  },
  /**
   * Print bill with cut and beep
   */
  async printBill(text, opts = {}) {
    return getBLEPrinter().printBill(text, toNitroPrintOptions(opts));
  },
  /**
   * Print raw Base64 data
   */
  async printRaw(data) {
    return getBLEPrinter().printRaw(data);
  },
  /**
   * Print image from URL
   */
  async printImage(imgUrl, opts = {}) {
    return getBLEPrinter().printImage(imgUrl, toNitroImageOptions(opts));
  },
  /**
   * Print image from Base64
   */
  async printImageBase64(base64, opts = {}) {
    return getBLEPrinter().printImageBase64(base64, toNitroImageOptions(opts));
  },
  /**
   * Print text in columns
   */
  async printColumnsText(texts, columnWidth, columnAlignment, columnStyle = [], opts = {}) {
    return getBLEPrinter().printColumnsText(texts, columnWidth, columnAlignment, columnStyle, toNitroPrintOptions(opts));
  },
  // NEW: Image caching
  /**
   * Cache an image for faster future printing
   */
  async cacheImage(url, key) {
    return getBLEPrinter().cacheImage(url, key);
  },
  /**
   * Print a cached image
   */
  async printCachedImage(key, opts = {}) {
    return getBLEPrinter().printCachedImage(key, toNitroImageOptions(opts));
  },
  /**
   * Clear image cache
   */
  clearImageCache() {
    getBLEPrinter().clearImageCache();
  },
  // NEW: Permissions
  /**
   * Request Bluetooth permissions
   */
  async askPermissions() {
    return getBLEPrinter().askPermissions();
  },
  // ============ SYNC METHODS (Instant - Fire & Forget) ============
  /**
   * Print text instantly - returns jobId immediately
   */
  printTextSync(text, opts = {}) {
    return getBLEPrinter().printTextSync(text, toNitroPrintOptions(opts));
  },
  /**
   * Print bill instantly - returns jobId immediately
   */
  printBillSync(text, opts = {}) {
    return getBLEPrinter().printBillSync(text, toNitroPrintOptions(opts));
  },
  /**
   * Print columns text instantly - returns jobId immediately
   */
  printColumnsTextSync(texts, columnWidth, columnAlignment, columnStyle = [], opts = {}) {
    return getBLEPrinter().printColumnsTextSync(texts, columnWidth, columnAlignment, columnStyle, toNitroPrintOptions(opts));
  },
  /**
   * Print raw data instantly - returns jobId immediately
   */
  printRawSync(data) {
    return getBLEPrinter().printRawSync(data);
  },
  /**
   * Print image base64 instantly - returns jobId immediately
   */
  printImageBase64Sync(base64, opts = {}) {
    return getBLEPrinter().printImageBase64Sync(base64, toNitroImageOptions(opts));
  },
  /**
   * Get job status by ID
   */
  getJobStatus(jobId) {
    return getBLEPrinter().getJobStatus(jobId) ?? undefined;
  },
  // ============ BULK PRINT (Optimized - Single Call) ============
  /**
   * Print multiple items in a single call for maximum performance
   * @param items Array of print items (text, columns, images, separators)
   * @returns Promise with job status
   */
  async printBulk(items) {
    try {
      // Validate and log items before passing to native
      console.log('[BLEPrinter.printBulk] ========== START ==========');
      console.log('[BLEPrinter.printBulk] Called with', items.length, 'items');

      // Validate each item
      const validatedItems = [];
      items.forEach((item, index) => {
        console.log(`[BLEPrinter.printBulk] Item ${index + 1}:`, {
          type: item.type,
          typeValue: typeof item.type === 'number' ? item.type : 'INVALID',
          hasContent: item.content !== undefined && item.content !== null,
          hasOptions: item.options !== undefined && item.options !== null,
          hasTexts: item.texts !== undefined && item.texts !== null,
          textsLength: item.texts?.length ?? 0,
          hasColumnWidths: item.columnWidths !== undefined && item.columnWidths !== null,
          columnWidthsLength: item.columnWidths?.length ?? 0,
          hasColumnAlignments: item.columnAlignments !== undefined && item.columnAlignments !== null,
          columnAlignmentsLength: item.columnAlignments?.length ?? 0,
          hasColumnStyles: item.columnStyles !== undefined && item.columnStyles !== null,
          columnStylesLength: item.columnStyles?.length ?? 0,
          hasBase64: item.base64 !== undefined && item.base64 !== null,
          hasImageOptions: item.imageOptions !== undefined && item.imageOptions !== null
        });

        // Validate type is a number
        if (typeof item.type !== 'number') {
          console.error(`[BLEPrinter.printBulk] ❌ Item ${index + 1} has invalid type:`, item.type);
          throw new Error(`Invalid PrintBulkItemType: expected number, got ${typeof item.type}`);
        }

        // Create clean item without undefined values
        const cleanItem = {
          type: item.type
        };
        if (item.content !== undefined && item.content !== null) cleanItem.content = item.content;

        // PrintOptions requires all fields (beep, cut, tailingLine, encoding)
        if (item.options !== undefined && item.options !== null) {
          cleanItem.options = {
            beep: item.options.beep ?? false,
            cut: item.options.cut ?? false,
            tailingLine: item.options.tailingLine ?? false,
            encoding: item.options.encoding ?? 'UTF-8'
          };
        }
        if (item.texts !== undefined && item.texts !== null) cleanItem.texts = item.texts;
        if (item.columnWidths !== undefined && item.columnWidths !== null) cleanItem.columnWidths = item.columnWidths;
        if (item.columnAlignments !== undefined && item.columnAlignments !== null) cleanItem.columnAlignments = item.columnAlignments;
        if (item.columnStyles !== undefined && item.columnStyles !== null) cleanItem.columnStyles = item.columnStyles;
        if (item.base64 !== undefined && item.base64 !== null) cleanItem.base64 = item.base64;

        // ImagePrintOptions requires all fields
        if (item.imageOptions !== undefined && item.imageOptions !== null) {
          cleanItem.imageOptions = {
            beep: item.imageOptions.beep ?? false,
            cut: item.imageOptions.cut ?? false,
            tailingLine: item.imageOptions.tailingLine ?? false,
            encoding: item.imageOptions.encoding ?? 'UTF-8',
            imageWidth: item.imageOptions.imageWidth ?? 0,
            imageHeight: item.imageOptions.imageHeight ?? 0,
            printerWidthType: item.imageOptions.printerWidthType ?? 80,
            paddingX: item.imageOptions.paddingX ?? 0
          };
        }
        validatedItems.push(cleanItem);
      });
      console.log('[BLEPrinter.printBulk] Calling native printBulk with', validatedItems.length, 'validated items');
      const result = await getBLEPrinter().printBulk(validatedItems);
      console.log('[BLEPrinter.printBulk] Native call completed:', result);
      console.log('[BLEPrinter.printBulk] ========== END ==========');
      return result;
    } catch (error) {
      console.error('[BLEPrinter.printBulk] ❌ ERROR:', error);
      console.error('[BLEPrinter.printBulk] Error stack:', error instanceof Error ? error.stack : 'No stack');
      throw error;
    }
  }
};

// ============ Network Printer API ============

/**
 * Network Printer - Backward compatible API with new features
 */
const NetPrinter = exports.NetPrinter = {
  getInstance: getNetPrinter,
  async init() {
    return getNetPrinter().initialize();
  },
  async getDeviceList() {
    const devices = await getNetPrinter().getDeviceList();
    return devices.map(d => ({
      host: d.host,
      port: d.port
    }));
  },
  /**
   * Connect to a network printer
   */
  async connectPrinter(host, port = 9100, timeout = 4000) {
    const device = await getNetPrinter().connectPrinter(host, port, timeout);
    return {
      host: device.host,
      port: device.port
    };
  },
  async closeConn() {
    return getNetPrinter().closeConnection();
  },
  // NEW: Network scan
  async scanNetwork(timeout = 5000) {
    return getNetPrinter().scanNetwork(timeout);
  },
  onScanProgress(callback) {
    const subscriptionId = getNetPrinter().addScanProgressListener(callback);
    return () => {
      getNetPrinter().removeScanProgressListener(subscriptionId);
    };
  },
  // Connection state
  isConnected() {
    return getNetPrinter().isConnected() ?? undefined;
  },
  getConnectionState() {
    return getNetPrinter().getConnectionState();
  },
  onConnectionStateChange(callback) {
    const subscriptionId = getNetPrinter().addConnectionStateListener(callback);
    return () => {
      getNetPrinter().removeConnectionStateListener(subscriptionId);
    };
  },
  // Print status
  isPrinting() {
    return getNetPrinter().isPrinting();
  },
  getPrintQueue() {
    return getNetPrinter().getPrintQueue();
  },
  // Print methods
  async printText(text, opts = {}) {
    return getNetPrinter().printText(text, toNitroPrintOptions(opts));
  },
  async printBill(text, opts = {}) {
    return getNetPrinter().printBill(text, toNitroPrintOptions(opts));
  },
  async printRaw(data) {
    return getNetPrinter().printRaw(data);
  },
  async printImage(imgUrl, opts = {}) {
    return getNetPrinter().printImage(imgUrl, toNitroImageOptions(opts));
  },
  async printImageBase64(base64, opts = {}) {
    return getNetPrinter().printImageBase64(base64, toNitroImageOptions(opts));
  },
  async printColumnsText(texts, columnWidth, columnAlignment, columnStyle = [], opts = {}) {
    return getNetPrinter().printColumnsText(texts, columnWidth, columnAlignment, columnStyle, toNitroPrintOptions(opts));
  },
  // Image caching
  async cacheImage(url, key) {
    return getNetPrinter().cacheImage(url, key);
  },
  async printCachedImage(key, opts = {}) {
    return getNetPrinter().printCachedImage(key, toNitroImageOptions(opts));
  },
  clearImageCache() {
    getNetPrinter().clearImageCache();
  },
  // Permissions
  async askPermissions() {
    return getNetPrinter().askPermissions();
  },
  // ============ BULK PRINT (Optimized - Single Call) ============
  /**
   * Print multiple items in a single call for maximum performance
   * @param items Array of print items (text, columns, images, separators)
   * @returns Promise with job status
   */
  async printBulk(items) {
    return getNetPrinter().printBulk(items);
  }
};

// ============ USB Printer API ============

/**
 * USB Printer - Backward compatible API with new features (Android only)
 */
const USBPrinter = exports.USBPrinter = {
  getInstance: getUSBPrinter,
  async init() {
    return getUSBPrinter().initialize();
  },
  async getDeviceList() {
    const devices = await getUSBPrinter().getDeviceList();
    return devices.map(d => ({
      device_name: d.deviceName,
      vendor_id: String(d.vendorId),
      product_id: String(d.productId)
    }));
  },
  async connectPrinter(vendorId, productId) {
    const device = await getUSBPrinter().connectPrinter(parseInt(vendorId, 10), parseInt(productId, 10));
    return {
      device_name: device.deviceName,
      vendor_id: String(device.vendorId),
      product_id: String(device.productId)
    };
  },
  async closeConn() {
    return getUSBPrinter().closeConnection();
  },
  // NEW: USB events
  onDeviceAttached(callback) {
    const subscriptionId = getUSBPrinter().addDeviceAttachedListener(d => {
      callback({
        device_name: d.deviceName,
        vendor_id: String(d.vendorId),
        product_id: String(d.productId)
      });
    });
    return () => {
      getUSBPrinter().removeDeviceAttachedListener(subscriptionId);
    };
  },
  onDeviceDetached(callback) {
    const subscriptionId = getUSBPrinter().addDeviceDetachedListener(callback);
    return () => {
      getUSBPrinter().removeDeviceDetachedListener(subscriptionId);
    };
  },
  // Connection state
  isConnected() {
    return getUSBPrinter().isConnected() ?? undefined;
  },
  getConnectionState() {
    return getUSBPrinter().getConnectionState();
  },
  onConnectionStateChange(callback) {
    const subscriptionId = getUSBPrinter().addConnectionStateListener(callback);
    return () => {
      getUSBPrinter().removeConnectionStateListener(subscriptionId);
    };
  },
  // Print status
  isPrinting() {
    return getUSBPrinter().isPrinting();
  },
  getPrintQueue() {
    return getUSBPrinter().getPrintQueue();
  },
  // Print methods
  async printText(text, opts = {}) {
    return getUSBPrinter().printText(text, toNitroPrintOptions(opts));
  },
  async printBill(text, opts = {}) {
    return getUSBPrinter().printBill(text, toNitroPrintOptions(opts));
  },
  async printRaw(data) {
    return getUSBPrinter().printRaw(data);
  },
  async printImage(imgUrl, opts = {}) {
    return getUSBPrinter().printImage(imgUrl, toNitroImageOptions(opts));
  },
  async printImageBase64(base64, opts = {}) {
    return getUSBPrinter().printImageBase64(base64, toNitroImageOptions(opts));
  },
  async printColumnsText(texts, columnWidth, columnAlignment, columnStyle = [], opts = {}) {
    return getUSBPrinter().printColumnsText(texts, columnWidth, columnAlignment, columnStyle, toNitroPrintOptions(opts));
  },
  // Image caching
  async cacheImage(url, key) {
    return getUSBPrinter().cacheImage(url, key);
  },
  async printCachedImage(key, opts = {}) {
    return getUSBPrinter().printCachedImage(key, toNitroImageOptions(opts));
  },
  clearImageCache() {
    getUSBPrinter().clearImageCache();
  },
  // Permissions
  async askPermissions() {
    return getUSBPrinter().askPermissions();
  }
};

// ============ Event Emitter (Legacy) ============

// Note: Events are now handled through onConnectionStateChange callbacks
// This is kept for backward compatibility
const NetPrinterEventEmitter = exports.NetPrinterEventEmitter = {
  addListener: (event, _callback) => {
    if (event === 'scannerResolved') {
      // Map to new scan API
      console.warn('NetPrinterEventEmitter is deprecated. Use NetPrinter.scanNetwork() instead.');
    }
    return {
      remove: () => {}
    };
  }
};
let RN_THERMAL_RECEIPT_PRINTER_EVENTS = exports.RN_THERMAL_RECEIPT_PRINTER_EVENTS = /*#__PURE__*/function (RN_THERMAL_RECEIPT_PRINTER_EVENTS) {
  RN_THERMAL_RECEIPT_PRINTER_EVENTS["EVENT_NET_PRINTER_SCANNED_SUCCESS"] = "scannerResolved";
  RN_THERMAL_RECEIPT_PRINTER_EVENTS["EVENT_NET_PRINTER_SCANNING"] = "scannerRunning";
  RN_THERMAL_RECEIPT_PRINTER_EVENTS["EVENT_NET_PRINTER_SCANNED_ERROR"] = "registerError";
  return RN_THERMAL_RECEIPT_PRINTER_EVENTS;
}({});
//# sourceMappingURL=index.js.map