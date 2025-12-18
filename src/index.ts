/**
 * React Native Thermal Receipt Printer v3
 *
 * High-performance thermal printer library using Nitro Modules.
 * Supports USB, BLE, and Network printers.
 */

import { NitroModules } from 'react-native-nitro-modules';
import type { HybridBLEPrinter } from './specs/BLEPrinter.nitro';
import type { HybridNetPrinter } from './specs/NetPrinter.nitro';
import type { HybridUSBPrinter } from './specs/USBPrinter.nitro';

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

// PrinterWidth is exported from ./specs/types

export interface PrinterImageOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
  imageWidth?: number;
  imageHeight?: number;
  printerWidthType?: PrinterWidth;
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

export { ColumnAlignment } from './specs/types';

// ============ Hybrid Object Instances ============

let _blePrinter: HybridBLEPrinter | null = null;
let _netPrinter: HybridNetPrinter | null = null;
let _usbPrinter: HybridUSBPrinter | null = null;

function getBLEPrinter(): HybridBLEPrinter {
  if (!_blePrinter) {
    _blePrinter = NitroModules.createHybridObject<HybridBLEPrinter>('HybridBLEPrinter');
  }
  return _blePrinter;
}

function getNetPrinter(): HybridNetPrinter {
  if (!_netPrinter) {
    _netPrinter = NitroModules.createHybridObject<HybridNetPrinter>('HybridNetPrinter');
  }
  return _netPrinter;
}

function getUSBPrinter(): HybridUSBPrinter {
  if (!_usbPrinter) {
    _usbPrinter = NitroModules.createHybridObject<HybridUSBPrinter>('HybridUSBPrinter');
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
    return getBLEPrinter().init();
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
    return getBLEPrinter().isConnected();
  },

  /**
   * Get current connection state
   */
  getConnectionState() {
    return getBLEPrinter().getConnectionState();
  },

  /**
   * Listen to connection state changes
   */
  onConnectionStateChange(callback: (state: string) => void): () => void {
    return getBLEPrinter().onConnectionStateChange(callback);
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
  getPrintQueue() {
    return getBLEPrinter().getPrintQueue();
  },

  // Print methods - now return Promises
  /**
   * Print text
   */
  async printText(text: string, opts: PrinterOptions = {}) {
    return getBLEPrinter().printText(text, opts);
  },

  /**
   * Print bill with cut and beep
   */
  async printBill(text: string, opts: PrinterOptions = {}) {
    return getBLEPrinter().printBill(text, opts);
  },

  /**
   * Print raw Base64 data
   */
  async printRaw(data: string) {
    return getBLEPrinter().printRaw(data);
  },

  /**
   * Print image from URL
   */
  async printImage(imgUrl: string, opts: PrinterImageOptions = {}) {
    return getBLEPrinter().printImage(imgUrl, opts);
  },

  /**
   * Print image from Base64
   */
  async printImageBase64(base64: string, opts: PrinterImageOptions = {}) {
    return getBLEPrinter().printImageBase64(base64, opts);
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
  ) {
    return getBLEPrinter().printColumnsText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle,
      opts
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
  async printCachedImage(key: string, opts: PrinterImageOptions = {}) {
    return getBLEPrinter().printCachedImage(key, opts);
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
    return getNetPrinter().init();
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
    timeout?: number
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
  async scanNetwork(timeout?: number) {
    return getNetPrinter().scanNetwork(timeout);
  },

  onScanProgress(callback: (progress: number) => void): () => void {
    return getNetPrinter().onScanProgress(callback);
  },

  // Connection state
  isConnected(): string | undefined {
    return getNetPrinter().isConnected();
  },

  getConnectionState() {
    return getNetPrinter().getConnectionState();
  },

  onConnectionStateChange(callback: (state: string) => void): () => void {
    return getNetPrinter().onConnectionStateChange(callback);
  },

  // Print status
  isPrinting(): boolean {
    return getNetPrinter().isPrinting();
  },

  getPrintQueue() {
    return getNetPrinter().getPrintQueue();
  },

  // Print methods
  async printText(text: string, opts: PrinterOptions = {}) {
    return getNetPrinter().printText(text, opts);
  },

  async printBill(text: string, opts: PrinterOptions = {}) {
    return getNetPrinter().printBill(text, opts);
  },

  async printRaw(data: string) {
    return getNetPrinter().printRaw(data);
  },

  async printImage(imgUrl: string, opts: PrinterImageOptions = {}) {
    return getNetPrinter().printImage(imgUrl, opts);
  },

  async printImageBase64(base64: string, opts: PrinterImageOptions = {}) {
    return getNetPrinter().printImageBase64(base64, opts);
  },

  async printColumnsText(
    texts: string[],
    columnWidth: number[],
    columnAlignment: number[],
    columnStyle: string[] = [],
    opts: PrinterOptions = {}
  ) {
    return getNetPrinter().printColumnsText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle,
      opts
    );
  },

  // Image caching
  async cacheImage(url: string, key: string): Promise<void> {
    return getNetPrinter().cacheImage(url, key);
  },

  async printCachedImage(key: string, opts: PrinterImageOptions = {}) {
    return getNetPrinter().printCachedImage(key, opts);
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
    return getUSBPrinter().init();
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
    return getUSBPrinter().onDeviceAttached((d) => {
      callback({
        device_name: d.deviceName,
        vendor_id: String(d.vendorId),
        product_id: String(d.productId),
      });
    });
  },

  onDeviceDetached(callback: () => void): () => void {
    return getUSBPrinter().onDeviceDetached(callback);
  },

  // Connection state
  isConnected(): string | undefined {
    return getUSBPrinter().isConnected();
  },

  getConnectionState() {
    return getUSBPrinter().getConnectionState();
  },

  onConnectionStateChange(callback: (state: string) => void): () => void {
    return getUSBPrinter().onConnectionStateChange(callback);
  },

  // Print status
  isPrinting(): boolean {
    return getUSBPrinter().isPrinting();
  },

  getPrintQueue() {
    return getUSBPrinter().getPrintQueue();
  },

  // Print methods
  async printText(text: string, opts: PrinterOptions = {}) {
    return getUSBPrinter().printText(text, opts);
  },

  async printBill(text: string, opts: PrinterOptions = {}) {
    return getUSBPrinter().printBill(text, opts);
  },

  async printRaw(data: string) {
    return getUSBPrinter().printRaw(data);
  },

  async printImage(imgUrl: string, opts: PrinterImageOptions = {}) {
    return getUSBPrinter().printImage(imgUrl, opts);
  },

  async printImageBase64(base64: string, opts: PrinterImageOptions = {}) {
    return getUSBPrinter().printImageBase64(base64, opts);
  },

  async printColumnsText(
    texts: string[],
    columnWidth: number[],
    columnAlignment: number[],
    columnStyle: string[] = [],
    opts: PrinterOptions = {}
  ) {
    return getUSBPrinter().printColumnsText(
      texts,
      columnWidth,
      columnAlignment,
      columnStyle,
      opts
    );
  },

  // Image caching
  async cacheImage(url: string, key: string): Promise<void> {
    return getUSBPrinter().cacheImage(url, key);
  },

  async printCachedImage(key: string, opts: PrinterImageOptions = {}) {
    return getUSBPrinter().printCachedImage(key, opts);
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
  addListener: (event: string, callback: (...args: unknown[]) => void) => {
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
