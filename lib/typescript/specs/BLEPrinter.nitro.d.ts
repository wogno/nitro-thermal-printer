import type { HybridObject } from 'react-native-nitro-modules';
import type { BLEDevice, PrintOptions, ImagePrintOptions, PrintJobStatus, PermissionResult } from './types';
import { ConnectionState } from './types';
/**
 * HybridBLEPrinter - Bluetooth Low Energy Printer Interface
 */
export interface BLEPrinter extends HybridObject<{
    ios: 'swift';
    android: 'kotlin';
}> {
    initialize(): Promise<void>;
    getDeviceList(): Promise<BLEDevice[]>;
    connectPrinter(innerMacAddress: string): Promise<BLEDevice>;
    closeConnection(): Promise<void>;
    isConnected(): string | undefined;
    getConnectionState(): ConnectionState;
    addConnectionStateListener(callback: (state: ConnectionState) => void): string;
    removeConnectionStateListener(subscriptionId: string): void;
    enableAutoReconnect(enabled: boolean): void;
    setReconnectAttempts(maxAttempts: number): void;
    setReconnectDelay(delayMs: number): void;
    isPrinting(): boolean;
    getPrintQueue(): PrintJobStatus[];
    printText(text: string, options: PrintOptions): Promise<PrintJobStatus>;
    printBill(text: string, options: PrintOptions): Promise<PrintJobStatus>;
    printRaw(data: string): Promise<PrintJobStatus>;
    printImage(imageUrl: string, options: ImagePrintOptions): Promise<PrintJobStatus>;
    printImageBase64(base64: string, options: ImagePrintOptions): Promise<PrintJobStatus>;
    printColumnsText(texts: string[], columnWidths: number[], columnAlignments: number[], columnStyles: string[], options: PrintOptions): Promise<PrintJobStatus>;
    cacheImage(url: string, key: string): Promise<void>;
    printCachedImage(key: string, options: ImagePrintOptions): Promise<PrintJobStatus>;
    clearImageCache(): void;
    askPermissions(): Promise<PermissionResult>;
}
//# sourceMappingURL=BLEPrinter.nitro.d.ts.map