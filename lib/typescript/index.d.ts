/**
 * React Native Thermal Receipt Printer v3
 *
 * High-performance thermal printer library using Nitro Modules.
 * Supports USB, BLE, and Network printers.
 */
import type { BLEPrinter as HybridBLEPrinter } from './specs/BLEPrinter.nitro';
import type { NetPrinter as HybridNetPrinter } from './specs/NetPrinter.nitro';
import type { USBPrinter as HybridUSBPrinter } from './specs/USBPrinter.nitro';
import type { PrintJobStatus as NitroPrintJobStatus, PrintBulkItem } from './specs/types';
import { PrinterWidthType, ConnectionState } from './specs/types';
export * from './specs/types';
export type { HybridBLEPrinter, HybridNetPrinter, HybridUSBPrinter };
export { COMMANDS } from './utils/printer-commands';
export { processColumnText } from './utils/print-column';
/**
 * Normalize a PrintBulkItem to ensure all required fields are present for JSI bridge.
 * This is automatically called by printBulk(), but you can use it manually if needed.
 *
 * @param item The PrintBulkItem to normalize
 * @returns A normalized PrintBulkItem with all required fields
 */
export declare function normalizePrintBulkItem(item: PrintBulkItem): any;
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
declare function getBLEPrinter(): HybridBLEPrinter;
declare function getNetPrinter(): HybridNetPrinter;
declare function getUSBPrinter(): HybridUSBPrinter;
/**
 * BLE Printer - Backward compatible API with new features
 */
export declare const BLEPrinter: {
    /**
     * Get the underlying Hybrid Object for advanced usage
     */
    getInstance: typeof getBLEPrinter;
    /**
     * Initialize the BLE printer module
     */
    init(): Promise<void>;
    /**
     * Get list of paired BLE devices
     */
    getDeviceList(): Promise<IBLEPrinter[]>;
    /**
     * Connect to a BLE printer
     */
    connectPrinter(inner_mac_address: string): Promise<IBLEPrinter>;
    /**
     * Close current connection
     */
    closeConn(): Promise<void>;
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
     * @returns Unsubscribe function
     */
    onConnectionStateChange(callback: (state: ConnectionState) => void): () => void;
    /**
     * Enable/disable auto-reconnection
     */
    enableAutoReconnect(enabled: boolean): void;
    setReconnectAttempts(maxAttempts: number): void;
    setReconnectDelay(delayMs: number): void;
    /**
     * Check if printer is currently printing
     */
    isPrinting(): boolean;
    /**
     * Get print queue status
     */
    getPrintQueue(): NitroPrintJobStatus[];
    /**
     * Print text
     */
    printText(text: string, opts?: PrinterOptions): Promise<NitroPrintJobStatus>;
    /**
     * Print bill with cut and beep
     */
    printBill(text: string, opts?: PrinterOptions): Promise<NitroPrintJobStatus>;
    /**
     * Print raw Base64 data
     */
    printRaw(data: string): Promise<NitroPrintJobStatus>;
    /**
     * Print image from URL
     */
    printImage(imgUrl: string, opts?: PrinterImageOptions): Promise<NitroPrintJobStatus>;
    /**
     * Print image from Base64
     */
    printImageBase64(base64: string, opts?: PrinterImageOptions): Promise<NitroPrintJobStatus>;
    /**
     * Print text in columns
     */
    printColumnsText(texts: string[], columnWidth: number[], columnAlignment: number[], columnStyle?: string[], opts?: PrinterOptions): Promise<NitroPrintJobStatus>;
    /**
     * Cache an image for faster future printing
     */
    cacheImage(url: string, key: string): Promise<void>;
    /**
     * Print a cached image
     */
    printCachedImage(key: string, opts?: PrinterImageOptions): Promise<NitroPrintJobStatus>;
    /**
     * Clear image cache
     */
    clearImageCache(): void;
    /**
     * Request Bluetooth permissions
     */
    askPermissions(): Promise<import(".").PermissionResult>;
    /**
     * Print text instantly - returns jobId immediately
     */
    printTextSync(text: string, opts?: PrinterOptions): string;
    /**
     * Print bill instantly - returns jobId immediately
     */
    printBillSync(text: string, opts?: PrinterOptions): string;
    /**
     * Print columns text instantly - returns jobId immediately
     */
    printColumnsTextSync(texts: string[], columnWidth: number[], columnAlignment: number[], columnStyle?: string[], opts?: PrinterOptions): string;
    /**
     * Print raw data instantly - returns jobId immediately
     */
    printRawSync(data: string): string;
    /**
     * Print image base64 instantly - returns jobId immediately
     */
    printImageBase64Sync(base64: string, opts?: PrinterImageOptions): string;
    /**
     * Get job status by ID
     */
    getJobStatus(jobId: string): NitroPrintJobStatus | undefined;
    /**
     * Print multiple items in a single call for maximum performance
     * @param items Array of print items (text, columns, images, separators)
     * @returns Promise with job status
     */
    printBulk(items: PrintBulkItem[]): Promise<NitroPrintJobStatus>;
};
/**
 * Network Printer - Backward compatible API with new features
 */
export declare const NetPrinter: {
    getInstance: typeof getNetPrinter;
    init(): Promise<void>;
    getDeviceList(): Promise<INetPrinter[]>;
    /**
     * Connect to a network printer
     */
    connectPrinter(host: string, port?: number, timeout?: number): Promise<INetPrinter>;
    closeConn(): Promise<void>;
    scanNetwork(timeout?: number): Promise<import(".").NetDevice[]>;
    onScanProgress(callback: (progress: number) => void): () => void;
    isConnected(): string | undefined;
    getConnectionState(): ConnectionState;
    onConnectionStateChange(callback: (state: ConnectionState) => void): () => void;
    isPrinting(): boolean;
    getPrintQueue(): NitroPrintJobStatus[];
    printText(text: string, opts?: PrinterOptions): Promise<NitroPrintJobStatus>;
    printBill(text: string, opts?: PrinterOptions): Promise<NitroPrintJobStatus>;
    printRaw(data: string): Promise<NitroPrintJobStatus>;
    printImage(imgUrl: string, opts?: PrinterImageOptions): Promise<NitroPrintJobStatus>;
    printImageBase64(base64: string, opts?: PrinterImageOptions): Promise<NitroPrintJobStatus>;
    printColumnsText(texts: string[], columnWidth: number[], columnAlignment: number[], columnStyle?: string[], opts?: PrinterOptions): Promise<NitroPrintJobStatus>;
    cacheImage(url: string, key: string): Promise<void>;
    printCachedImage(key: string, opts?: PrinterImageOptions): Promise<NitroPrintJobStatus>;
    clearImageCache(): void;
    askPermissions(): Promise<import(".").PermissionResult>;
    /**
     * Print multiple items in a single call for maximum performance
     * @param items Array of print items (text, columns, images, separators)
     * @returns Promise with job status
     */
    printBulk(items: PrintBulkItem[]): Promise<NitroPrintJobStatus>;
};
/**
 * USB Printer - Backward compatible API with new features (Android only)
 */
export declare const USBPrinter: {
    getInstance: typeof getUSBPrinter;
    init(): Promise<void>;
    getDeviceList(): Promise<IUSBPrinter[]>;
    connectPrinter(vendorId: string, productId: string): Promise<IUSBPrinter>;
    closeConn(): Promise<void>;
    onDeviceAttached(callback: (device: IUSBPrinter) => void): () => void;
    onDeviceDetached(callback: () => void): () => void;
    isConnected(): string | undefined;
    getConnectionState(): ConnectionState;
    onConnectionStateChange(callback: (state: ConnectionState) => void): () => void;
    isPrinting(): boolean;
    getPrintQueue(): NitroPrintJobStatus[];
    printText(text: string, opts?: PrinterOptions): Promise<NitroPrintJobStatus>;
    printBill(text: string, opts?: PrinterOptions): Promise<NitroPrintJobStatus>;
    printRaw(data: string): Promise<NitroPrintJobStatus>;
    printImage(imgUrl: string, opts?: PrinterImageOptions): Promise<NitroPrintJobStatus>;
    printImageBase64(base64: string, opts?: PrinterImageOptions): Promise<NitroPrintJobStatus>;
    printColumnsText(texts: string[], columnWidth: number[], columnAlignment: number[], columnStyle?: string[], opts?: PrinterOptions): Promise<NitroPrintJobStatus>;
    cacheImage(url: string, key: string): Promise<void>;
    printCachedImage(key: string, opts?: PrinterImageOptions): Promise<NitroPrintJobStatus>;
    clearImageCache(): void;
    askPermissions(): Promise<import(".").PermissionResult>;
};
export declare const NetPrinterEventEmitter: {
    addListener: (event: string, _callback: (...args: unknown[]) => void) => {
        remove: () => void;
    };
};
export declare enum RN_THERMAL_RECEIPT_PRINTER_EVENTS {
    EVENT_NET_PRINTER_SCANNED_SUCCESS = "scannerResolved",
    EVENT_NET_PRINTER_SCANNING = "scannerRunning",
    EVENT_NET_PRINTER_SCANNED_ERROR = "registerError"
}
//# sourceMappingURL=index.d.ts.map