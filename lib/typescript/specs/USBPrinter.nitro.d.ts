import type { HybridObject } from 'react-native-nitro-modules';
import type { USBDevice, PrintOptions, ImagePrintOptions, PrintJobStatus, PermissionResult } from './types';
import { ConnectionState } from './types';
/**
 * HybridUSBPrinter - USB Printer Interface (Android only)
 * Note: USB printing is only supported on Android.
 */
export interface USBPrinter extends HybridObject<{
    ios: 'swift';
    android: 'kotlin';
}> {
    initialize(): Promise<void>;
    getDeviceList(): Promise<USBDevice[]>;
    connectPrinter(vendorId: number, productId: number): Promise<USBDevice>;
    closeConnection(): Promise<void>;
    isConnected(): string | undefined;
    getConnectionState(): ConnectionState;
    addConnectionStateListener(callback: (state: ConnectionState) => void): string;
    removeConnectionStateListener(subscriptionId: string): void;
    addDeviceAttachedListener(callback: (device: USBDevice) => void): string;
    removeDeviceAttachedListener(subscriptionId: string): void;
    addDeviceDetachedListener(callback: () => void): string;
    removeDeviceDetachedListener(subscriptionId: string): void;
    isPrinting(): boolean;
    getPrintQueue(): PrintJobStatus[];
    printText(text: string, options: PrintOptions): Promise<PrintJobStatus>;
    printBill(text: string, options: PrintOptions): Promise<PrintJobStatus>;
    printRaw(data: string): Promise<PrintJobStatus>;
    printImage(imageUrl: string, options: ImagePrintOptions): Promise<PrintJobStatus>;
    printImageBase64(base64: string, options: ImagePrintOptions): Promise<PrintJobStatus>;
    printColumnsText(texts: string[], columnWidths: number[], columnAlignments: number[], columnStyles: string[], options: PrintOptions): Promise<PrintJobStatus>;
    printTextSync(text: string, options: PrintOptions): string;
    printBillSync(text: string, options: PrintOptions): string;
    printRawSync(data: string): string;
    printImageBase64Sync(base64: string, options: ImagePrintOptions): string;
    printColumnsTextSync(texts: string[], columnWidths: number[], columnAlignments: number[], columnStyles: string[], options: PrintOptions): string;
    getJobStatus(jobId: string): PrintJobStatus | undefined;
    cacheImage(url: string, key: string): Promise<void>;
    printCachedImage(key: string, options: ImagePrintOptions): Promise<PrintJobStatus>;
    clearImageCache(): void;
    askPermissions(): Promise<PermissionResult>;
}
//# sourceMappingURL=USBPrinter.nitro.d.ts.map