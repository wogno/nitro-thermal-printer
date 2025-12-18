import type { HybridObject } from 'react-native-nitro-modules';
import type {
  NetDevice,
  PrintOptions,
  ImagePrintOptions,
  ConnectionState,
  PrintJobStatus,
  PermissionResult,
} from './types';

/**
 * HybridNetPrinter - Network (TCP/IP) Printer Interface
 *
 * This is a Nitro Hybrid Object that provides high-performance
 * network thermal printer functionality using JSI.
 */
export interface HybridNetPrinter
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {

  // ============ Lifecycle ============

  /**
   * Initialize the Network printer module
   * Must be called before any other method
   */
  init(): Promise<void>;

  /**
   * Dispose and cleanup resources
   */
  dispose(): void;

  // ============ Device Discovery ============

  /**
   * Get list of previously connected network printers
   */
  getDeviceList(): Promise<NetDevice[]>;

  /**
   * Scan local network for available printers on port 9100
   * @param timeout Scan timeout in milliseconds (default: 5000)
   */
  scanNetwork(timeout?: number): Promise<NetDevice[]>;

  /**
   * Listen to scan progress updates
   * @param callback Called with progress percentage (0-100)
   * @returns Unsubscribe function
   */
  onScanProgress(callback: (progress: number) => void): () => void;

  // ============ Connection ============

  /**
   * Connect to a network printer
   * @param host IP address or hostname
   * @param port Port number (default: 9100)
   * @param timeout Connection timeout in ms (default: 4000)
   */
  connectPrinter(host: string, port?: number, timeout?: number): Promise<NetDevice>;

  /**
   * Close the current connection
   */
  closeConnection(): Promise<void>;

  /**
   * Check if connected to a printer
   * @returns Device host:port if connected, undefined otherwise
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
   * Request network permissions (if needed on platform)
   * @returns Permission result with granted status
   */
  askPermissions(): Promise<PermissionResult>;
}
