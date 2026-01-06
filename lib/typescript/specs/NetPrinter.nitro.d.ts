import type { HybridObject } from 'react-native-nitro-modules';
import type { NetDevice, PrintOptions, ImagePrintOptions, PrintJobStatus, PermissionResult, PrintBulkItem } from './types';
import { ConnectionState } from './types';
/**
 * HybridNetPrinter - Network (TCP/IP) Printer Interface
 */
export interface NetPrinter extends HybridObject<{
    ios: 'swift';
    android: 'kotlin';
}> {
    initialize(): Promise<void>;
    getDeviceList(): Promise<NetDevice[]>;
    scanNetwork(timeout: number): Promise<NetDevice[]>;
    addScanProgressListener(callback: (progress: number) => void): string;
    removeScanProgressListener(subscriptionId: string): void;
    connectPrinter(host: string, port: number, timeout: number): Promise<NetDevice>;
    closeConnection(): Promise<void>;
    isConnected(): string | undefined;
    getConnectionState(): ConnectionState;
    addConnectionStateListener(callback: (state: ConnectionState) => void): string;
    removeConnectionStateListener(subscriptionId: string): void;
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
    printBulk(items: PrintBulkItem[]): Promise<PrintJobStatus>;
    cacheImage(url: string, key: string): Promise<void>;
    printCachedImage(key: string, options: ImagePrintOptions): Promise<PrintJobStatus>;
    clearImageCache(): void;
    askPermissions(): Promise<PermissionResult>;
}
//# sourceMappingURL=NetPrinter.nitro.d.ts.map