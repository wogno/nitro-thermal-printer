import type { HybridObject } from 'react-native-nitro-modules';
import type {
  USBDevice,
  PrintOptions,
  ImagePrintOptions,
  ConnectionState,
  PrintJobStatus,
  PermissionResult,
} from './types';

/**
 * HybridUSBPrinter - USB Printer Interface (Android only)
 *
 * This is a Nitro Hybrid Object that provides high-performance
 * USB thermal printer functionality using JSI.
 *
 * Note: USB printing is only supported on Android.
 */
export interface HybridUSBPrinter
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {

  // ============ Lifecycle ============

  /**
   * Initialize the USB printer module
   * Must be called before any other method
   */
  init(): Promise<void>;

  /**
   * Dispose and cleanup resources
   */
  dispose(): void;

  // ============ Device Discovery ============

  /**
   * Get list of connected USB printer devices
   */
  getDeviceList(): Promise<USBDevice[]>;

  // ============ Connection ============

  /**
   * Connect to a USB printer device
   * @param vendorId USB vendor ID
   * @param productId USB product ID
   */
  connectPrinter(vendorId: number, productId: number): Promise<USBDevice>;

  /**
   * Close the current connection
   */
  closeConnection(): Promise<void>;

  /**
   * Check if connected to a printer
   * @returns Device vendorId:productId if connected, undefined otherwise
   */
  isConnected(): string | undefined;

  /**
   * Get current connection state
   */
  getConnectionState(): ConnectionState;

  /**
   * Listen to connection state changes
   * @param callback Called when connection state changes
   * @returns Unsubscribe function
   */
  onConnectionStateChange(callback: (state: ConnectionState) => void): () => void;

  // ============ USB Events ============

  /**
   * Listen for USB device attach events
   * @param callback Called when a USB device is attached
   * @returns Unsubscribe function
   */
  onDeviceAttached(callback: (device: USBDevice) => void): () => void;

  /**
   * Listen for USB device detach events
   * @param callback Called when the connected device is detached
   * @returns Unsubscribe function
   */
  onDeviceDetached(callback: () => void): () => void;

  // ============ Print Status ============

  /**
   * Check if printer is currently printing
   */
  isPrinting(): boolean;

  /**
   * Get status of all print jobs in queue
   */
  getPrintQueue(): PrintJobStatus[];

  // ============ Print Methods ============

  /**
   * Print text without cutting
   */
  printText(text: string, options?: PrintOptions): Promise<PrintJobStatus>;

  /**
   * Print text with automatic cut and beep
   */
  printBill(text: string, options?: PrintOptions): Promise<PrintJobStatus>;

  /**
   * Print raw Base64 encoded ESC/POS data
   */
  printRaw(data: string): Promise<PrintJobStatus>;

  /**
   * Print image from URL
   */
  printImage(imageUrl: string, options?: ImagePrintOptions): Promise<PrintJobStatus>;

  /**
   * Print image from Base64 string
   */
  printImageBase64(base64: string, options?: ImagePrintOptions): Promise<PrintJobStatus>;

  /**
   * Print text in columns
   */
  printColumnsText(
    texts: string[],
    columnWidths: number[],
    columnAlignments: number[],
    columnStyles?: string[],
    options?: PrintOptions
  ): Promise<PrintJobStatus>;

  // ============ Image Caching ============

  /**
   * Cache an image from URL for faster future printing
   * @param url Image URL to cache
   * @param key Unique key to reference the cached image
   */
  cacheImage(url: string, key: string): Promise<void>;

  /**
   * Print a previously cached image
   * @param key Key used when caching the image
   */
  printCachedImage(key: string, options?: ImagePrintOptions): Promise<PrintJobStatus>;

  /**
   * Clear all cached images
   */
  clearImageCache(): void;

  // ============ Permissions ============

  /**
   * Request USB permissions
   * @returns Permission result with granted status
   */
  askPermissions(): Promise<PermissionResult>;
}
