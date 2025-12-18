import type { HybridObject } from 'react-native-nitro-modules';
import type {
  BLEDevice,
  PrintOptions,
  ImagePrintOptions,
  ConnectionState,
  PrintJobStatus,
  PermissionResult,
} from './types';

/**
 * HybridBLEPrinter - Bluetooth Low Energy Printer Interface
 *
 * This is a Nitro Hybrid Object that provides high-performance
 * BLE thermal printer functionality using JSI.
 */
export interface HybridBLEPrinter
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {

  // ============ Lifecycle ============

  /**
   * Initialize the BLE printer module
   * Must be called before any other method
   */
  init(): Promise<void>;

  /**
   * Dispose and cleanup resources
   */
  dispose(): void;

  // ============ Device Discovery ============

  /**
   * Get list of paired/bonded BLE devices
   */
  getDeviceList(): Promise<BLEDevice[]>;

  // ============ Connection ============

  /**
   * Connect to a BLE printer device
   * @param innerMacAddress MAC address of the device
   */
  connectPrinter(innerMacAddress: string): Promise<BLEDevice>;

  /**
   * Close the current connection
   */
  closeConnection(): Promise<void>;

  /**
   * Check if connected to a printer
   * @returns Device MAC address if connected, undefined otherwise
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

  // ============ Auto-Reconnection ============

  /**
   * Enable/disable auto-reconnection when connection drops
   */
  enableAutoReconnect(enabled: boolean): void;

  /**
   * Set maximum reconnection attempts before giving up
   */
  setReconnectAttempts(maxAttempts: number): void;

  /**
   * Set delay between reconnection attempts (in ms)
   */
  setReconnectDelay(delayMs: number): void;

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
   * Request Bluetooth permissions
   * @returns Permission result with granted status and whether to show settings
   */
  askPermissions(): Promise<PermissionResult>;
}
