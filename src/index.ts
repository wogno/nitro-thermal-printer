/**
 * React Native Thermal Receipt Printer v3
 *
 * High-performance thermal printer library using Nitro Modules.
 * Supports USB, BLE, and Network printers.
 */

import { NitroModules } from 'react-native-nitro-modules';
import type { BLEPrinter as HybridBLEPrinter } from './specs/BLEPrinter.nitro';
import type { NetPrinter as HybridNetPrinter } from './specs/NetPrinter.nitro';
import type { USBPrinter as HybridUSBPrinter } from './specs/USBPrinter.nitro';
import type {
  PrintOptions as NitroPrintOptions,
  ImagePrintOptions as NitroImagePrintOptions,
  PrintJobStatus as NitroPrintJobStatus,
  USBDevice as NitroUSBDevice,
} from './specs/types';
import { PrinterWidthType, ConnectionState } from './specs/types';

// Re-export types
export * from './specs/types';
export type { HybridBLEPrinter, HybridNetPrinter, HybridUSBPrinter };

// Export utilities
export { COMMANDS } from './utils/printer-commands';
export { processColumnText } from './utils/print-column';

// Legacy type exports for backward compatibility
export interface PrinterOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
}

export interface PrinterImageOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
  imageWidth?: number;
  imageHeight?: number;
  printerWidthType?: PrinterWidthType;
  paddingX?: number;
}

export interface IUSBPrinter {
  device_name: string;
  vendor_id: string;
  product_id: string;
}

export interface IBLEPrinter {
  device_name: string;
  inner_mac_address: string;
}

export interface INetPrinter {
  host: string;
  port: number;
}

// ============ Helper Functions ============

function toNitroPrintOptions(opts: PrinterOptions = {}): NitroPrintOptions {
  return {
    beep: opts.beep ?? false,
    cut: opts.cut ?? false,
    tailingLine: opts.tailingLine ?? false,
    encoding: opts.encoding ?? 'UTF-8',
  };
}

function toNitroImageOptions(opts: PrinterImageOptions = {}): NitroImagePrintOptions {
  return {
    beep: opts.beep ?? false,
    cut: opts.cut ?? false,
    tailingLine: opts.tailingLine ?? false,
    encoding: opts.encoding ?? 'UTF-8',
    imageWidth: opts.imageWidth ?? 0,
    imageHeight: opts.imageHeight ?? 0,
    printerWidthType: opts.printerWidthType ?? PrinterWidthType.MM_80,
    paddingX: opts.paddingX ?? 0,
  };
}

// ============ Hybrid Object Instances ============

let _blePrinter: HybridBLEPrinter | null = null;
let _netPrinter: HybridNetPrinter | null = null;
let _usbPrinter: HybridUSBPrinter | null = null;

function getBLEPrinter(): HybridBLEPrinter {
  if (!_blePrinter) {
    _blePrinter = NitroModules.createHybridObject<HybridBLEPrinter>('BLEPrinter');
  }
  return _blePrinter;
}

function getNetPrinter(): HybridNetPrinter {
  if (!_netPrinter) {
    _netPrinter = NitroModules.createHybridObject<HybridNetPrinter>('NetPrinter');
  }
  return _netPrinter;
}

function getUSBPrinter(): HybridUSBPrinter {
  if (!_usbPrinter) {
    _usbPrinter = NitroModules.createHybridObject<HybridUSBPrinter>('USBPrinter');
  }
  return _usbPrinter;
}

// ============ BLE Printer API ============

/**
 * BLE Printer - Backward compatible API with new features
 */
export const BLEPrinter = {
  /**
   * Get the underlying Hybrid Object for advanced usage
   */
  getInstance: getBLEPrinter,

  /**
   * Initialize the BLE printer module
   */
  async init(): Promise<void> {
    return getBLEPrinter().initialize();
  },

  /**
   * Get list of paired BLE devices
   */
  async getDeviceList(): Promise<IBLEPrinter[]> {
    const devices = await getBLEPrinter().getDeviceList();
    return devices.map((d) => ({
      device_name: d.deviceName,
      inner_mac_address: d.innerMacAddress,
    }));
  },

  /**
   * Connect to a BLE printer
   */
  async connectPrinter(inner_mac_address: string): Promise<IBLEPrinter> {
    const device = await getBLEPrinter().connectPrinter(inner_mac_address);
    return {
      device_name: device.deviceName,
      inner_mac_address: device.innerMacAddress,
    };
  },

  /**
   * Close current connection
   */
  async closeConn(): Promise<void> {
    return getBLEPrinter().closeConnection();
  },

  // NEW: Connection state methods
  /**
   * Check if connected to a printer
   * @returns Device MAC address if connected, undefined otherwise
   */
  isConnected(): string | undefined {
    return getBLEPrinter().isConnected() ?? undefined;
  },

  /**
   * Get current connection state
   */
  getConnectionState(): ConnectionState {
    return getBLEPrinter().getConnectionState();
  },

  /**
   * Listen to connection state changes
   * @returns Unsubscribe function
   */
  onConnectionStateChange(callback: (state: ConnectionState) => void): () => void {
    const subscriptionId = getBLEPrinter().addConnectionStateListener(callback);
    return () => {
      getBLEPrinter().removeConnectionStateListener(subscriptionId);
    };
  },

  // NEW: Auto-reconnection
  /**
   * Enable/disable auto-reconnection
   */
  enableAutoReconnect(enabled: boolean): void {
    getBLEPrinter().enableAutoReconnect(enabled);
  },

  setReconnectAttempts(maxAttempts: number): void {
    getBLEPrinter().setReconnectAttempts(maxAttempts);
  },

  setReconnectDelay(delayMs: number): void {
    getBLEPrinter().setReconnectDelay(delayMs);
  },

  // NEW: Print status
  /**
   * Check if printer is currently printing
   */
  isPrinting(): boolean {
    return getBLEPrinter().isPrinting();
  },

  /**
   * Get print queue status
   */
  getPrintQueue(): NitroPrintJobStatus[] {
    return getBLEPrinter().getPrintQueue();
  },

  // Print methods - now return Promises
  /**
   * Print text
   */
  async printText(text: string, opts: PrinterOptions = {}): Promise<NitroPrintJobStatus> {
    return getBLEPrinter().printText(text, toNitroPrintOptions(opts));
  },

  /**
   * Print bill with cut and beep
   */
  async printBill(text: string, opts: PrinterOptions = {}): Promise<NitroPrintJobStatus> {
    return getBLEPrinter().printBill(text, toNitroPrintOptions(opts));
  },

  /**
   * Print raw Base64 data
   */
  async printRaw(data: string): Promise<NitroPrintJobStatus> {
    return getBLEPrinter().printRaw(data);
  },

  /**
   * Print image from URL
   */
  async printImage(imgUrl: string, opts: PrinterImageOptions = {}): Promise<NitroPrintJobStatus> {
    return getBLEPrinter().printImage(imgUrl, toNitroImageOptions(opts));
  },

  /**
   * Print image from Base64
   */
  async printImageBase64(base64: string, opts: PrinterImageOptions = {}): Promise<NitroPrintJobStatus> {
    return getBLEPrinter().printImageBase64(base64, toNitroImageOptions(opts));
  },

  /**
   * Print text in columns
   */
  async printColumnsText(
    texts: string[],
    columnWidth: number[],
    columnAlignment: number[],
    columnStyle: string[] = [],
    opts: PrinterOptions = {}
  ): Promise<NitroPrintJobStatus> {
    return getBLEPrinter().printColumnsText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle,
      toNitroPrintOptions(opts)
    );
  },

  // NEW: Image caching
  /**
   * Cache an image for faster future printing
   */
  async cacheImage(url: string, key: string): Promise<void> {
    return getBLEPrinter().cacheImage(url, key);
  },

  /**
   * Print a cached image
   */
  async printCachedImage(key: string, opts: PrinterImageOptions = {}): Promise<NitroPrintJobStatus> {
    return getBLEPrinter().printCachedImage(key, toNitroImageOptions(opts));
  },

  /**
   * Clear image cache
   */
  clearImageCache(): void {
    getBLEPrinter().clearImageCache();
  },

  // NEW: Permissions
  /**
   * Request Bluetooth permissions
   */
  async askPermissions() {
    return getBLEPrinter().askPermissions();
  },
};

// ============ Network Printer API ============

/**
 * Network Printer - Backward compatible API with new features
 */
export const NetPrinter = {
  getInstance: getNetPrinter,

  async init(): Promise<void> {
    return getNetPrinter().initialize();
  },

  async getDeviceList(): Promise<INetPrinter[]> {
    const devices = await getNetPrinter().getDeviceList();
    return devices.map((d) => ({
      host: d.host,
      port: d.port,
    }));
  },

  /**
   * Connect to a network printer
   */
  async connectPrinter(
    host: string,
    port: number = 9100,
    timeout: number = 4000
  ): Promise<INetPrinter> {
    const device = await getNetPrinter().connectPrinter(host, port, timeout);
    return {
      host: device.host,
      port: device.port,
    };
  },

  async closeConn(): Promise<void> {
    return getNetPrinter().closeConnection();
  },

  // NEW: Network scan
  async scanNetwork(timeout: number = 5000) {
    return getNetPrinter().scanNetwork(timeout);
  },

  onScanProgress(callback: (progress: number) => void): () => void {
    const subscriptionId = getNetPrinter().addScanProgressListener(callback);
    return () => {
      getNetPrinter().removeScanProgressListener(subscriptionId);
    };
  },

  // Connection state
  isConnected(): string | undefined {
    return getNetPrinter().isConnected() ?? undefined;
  },

  getConnectionState(): ConnectionState {
    return getNetPrinter().getConnectionState();
  },

  onConnectionStateChange(callback: (state: ConnectionState) => void): () => void {
    const subscriptionId = getNetPrinter().addConnectionStateListener(callback);
    return () => {
      getNetPrinter().removeConnectionStateListener(subscriptionId);
    };
  },

  // Print status
  isPrinting(): boolean {
    return getNetPrinter().isPrinting();
  },

  getPrintQueue(): NitroPrintJobStatus[] {
    return getNetPrinter().getPrintQueue();
  },

  // Print methods
  async printText(text: string, opts: PrinterOptions = {}): Promise<NitroPrintJobStatus> {
    return getNetPrinter().printText(text, toNitroPrintOptions(opts));
  },

  async printBill(text: string, opts: PrinterOptions = {}): Promise<NitroPrintJobStatus> {
    return getNetPrinter().printBill(text, toNitroPrintOptions(opts));
  },

  async printRaw(data: string): Promise<NitroPrintJobStatus> {
    return getNetPrinter().printRaw(data);
  },

  async printImage(imgUrl: string, opts: PrinterImageOptions = {}): Promise<NitroPrintJobStatus> {
    return getNetPrinter().printImage(imgUrl, toNitroImageOptions(opts));
  },

  async printImageBase64(base64: string, opts: PrinterImageOptions = {}): Promise<NitroPrintJobStatus> {
    return getNetPrinter().printImageBase64(base64, toNitroImageOptions(opts));
  },

  async printColumnsText(
    texts: string[],
    columnWidth: number[],
    columnAlignment: number[],
    columnStyle: string[] = [],
    opts: PrinterOptions = {}
  ): Promise<NitroPrintJobStatus> {
    return getNetPrinter().printColumnsText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle,
      toNitroPrintOptions(opts)
    );
  },

  // Image caching
  async cacheImage(url: string, key: string): Promise<void> {
    return getNetPrinter().cacheImage(url, key);
  },

  async printCachedImage(key: string, opts: PrinterImageOptions = {}): Promise<NitroPrintJobStatus> {
    return getNetPrinter().printCachedImage(key, toNitroImageOptions(opts));
  },

  clearImageCache(): void {
    getNetPrinter().clearImageCache();
  },

  // Permissions
  async askPermissions() {
    return getNetPrinter().askPermissions();
  },
};

// ============ USB Printer API ============

/**
 * USB Printer - Backward compatible API with new features (Android only)
 */
export const USBPrinter = {
  getInstance: getUSBPrinter,

  async init(): Promise<void> {
    return getUSBPrinter().initialize();
  },

  async getDeviceList(): Promise<IUSBPrinter[]> {
    const devices = await getUSBPrinter().getDeviceList();
    return devices.map((d) => ({
      device_name: d.deviceName,
      vendor_id: String(d.vendorId),
      product_id: String(d.productId),
    }));
  },

  async connectPrinter(vendorId: string, productId: string): Promise<IUSBPrinter> {
    const device = await getUSBPrinter().connectPrinter(
      parseInt(vendorId, 10),
      parseInt(productId, 10)
    );
    return {
      device_name: device.deviceName,
      vendor_id: String(device.vendorId),
      product_id: String(device.productId),
    };
  },

  async closeConn(): Promise<void> {
    return getUSBPrinter().closeConnection();
  },

  // NEW: USB events
  onDeviceAttached(callback: (device: IUSBPrinter) => void): () => void {
    const subscriptionId = getUSBPrinter().addDeviceAttachedListener((d: NitroUSBDevice) => {
      callback({
        device_name: d.deviceName,
        vendor_id: String(d.vendorId),
        product_id: String(d.productId),
      });
    });
    return () => {
      getUSBPrinter().removeDeviceAttachedListener(subscriptionId);
    };
  },

  onDeviceDetached(callback: () => void): () => void {
    const subscriptionId = getUSBPrinter().addDeviceDetachedListener(callback);
    return () => {
      getUSBPrinter().removeDeviceDetachedListener(subscriptionId);
    };
  },

  // Connection state
  isConnected(): string | undefined {
    return getUSBPrinter().isConnected() ?? undefined;
  },

  getConnectionState(): ConnectionState {
    return getUSBPrinter().getConnectionState();
  },

  onConnectionStateChange(callback: (state: ConnectionState) => void): () => void {
    const subscriptionId = getUSBPrinter().addConnectionStateListener(callback);
    return () => {
      getUSBPrinter().removeConnectionStateListener(subscriptionId);
    };
  },

  // Print status
  isPrinting(): boolean {
    return getUSBPrinter().isPrinting();
  },

  getPrintQueue(): NitroPrintJobStatus[] {
    return getUSBPrinter().getPrintQueue();
  },

  // Print methods
  async printText(text: string, opts: PrinterOptions = {}): Promise<NitroPrintJobStatus> {
    return getUSBPrinter().printText(text, toNitroPrintOptions(opts));
  },

  async printBill(text: string, opts: PrinterOptions = {}): Promise<NitroPrintJobStatus> {
    return getUSBPrinter().printBill(text, toNitroPrintOptions(opts));
  },

  async printRaw(data: string): Promise<NitroPrintJobStatus> {
    return getUSBPrinter().printRaw(data);
  },

  async printImage(imgUrl: string, opts: PrinterImageOptions = {}): Promise<NitroPrintJobStatus> {
    return getUSBPrinter().printImage(imgUrl, toNitroImageOptions(opts));
  },

  async printImageBase64(base64: string, opts: PrinterImageOptions = {}): Promise<NitroPrintJobStatus> {
    return getUSBPrinter().printImageBase64(base64, toNitroImageOptions(opts));
  },

  async printColumnsText(
    texts: string[],
    columnWidth: number[],
    columnAlignment: number[],
    columnStyle: string[] = [],
    opts: PrinterOptions = {}
  ): Promise<NitroPrintJobStatus> {
    return getUSBPrinter().printColumnsText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle,
      toNitroPrintOptions(opts)
    );
  },

  // Image caching
  async cacheImage(url: string, key: string): Promise<void> {
    return getUSBPrinter().cacheImage(url, key);
  },

  async printCachedImage(key: string, opts: PrinterImageOptions = {}): Promise<NitroPrintJobStatus> {
    return getUSBPrinter().printCachedImage(key, toNitroImageOptions(opts));
  },

  clearImageCache(): void {
    getUSBPrinter().clearImageCache();
  },

  // Permissions
  async askPermissions() {
    return getUSBPrinter().askPermissions();
  },
};

// ============ Event Emitter (Legacy) ============

// Note: Events are now handled through onConnectionStateChange callbacks
// This is kept for backward compatibility
export const NetPrinterEventEmitter = {
  addListener: (event: string, _callback: (...args: unknown[]) => void) => {
    if (event === 'scannerResolved') {
      // Map to new scan API
      console.warn(
        'NetPrinterEventEmitter is deprecated. Use NetPrinter.scanNetwork() instead.'
      );
    }
    return { remove: () => {} };
  },
};

export enum RN_THERMAL_RECEIPT_PRINTER_EVENTS {
  EVENT_NET_PRINTER_SCANNED_SUCCESS = 'scannerResolved',
  EVENT_NET_PRINTER_SCANNING = 'scannerRunning',
  EVENT_NET_PRINTER_SCANNED_ERROR = 'registerError',
}
