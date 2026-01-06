/**
 * Shared types for Nitro Thermal Printer
 */
export interface BLEDevice {
    deviceName: string;
    innerMacAddress: string;
}
export interface NetDevice {
    host: string;
    port: number;
    deviceName: string | undefined;
}
export interface USBDevice {
    deviceName: string;
    vendorId: number;
    productId: number;
    deviceId: number | undefined;
}
export interface PrintOptions {
    beep: boolean;
    cut: boolean;
    tailingLine: boolean;
    encoding: string;
}
export interface ImagePrintOptions {
    beep: boolean;
    cut: boolean;
    tailingLine: boolean;
    encoding: string;
    imageWidth: number;
    imageHeight: number;
    printerWidthType: PrinterWidthType;
    paddingX: number;
}
export declare enum ConnectionState {
    DISCONNECTED = 0,
    CONNECTING = 1,
    CONNECTED = 2,
    RECONNECTING = 3
}
export declare enum PrintJobStatusType {
    QUEUED = 0,
    PRINTING = 1,
    COMPLETED = 2,
    FAILED = 3
}
export interface PrintJobStatus {
    jobId: string;
    status: PrintJobStatusType;
    error: string | undefined;
}
export interface PermissionResult {
    granted: boolean;
    shouldShowSettings: boolean;
}
export declare enum ColumnAlignment {
    LEFT = 0,
    CENTER = 1,
    RIGHT = 2
}
export declare enum PrinterWidthType {
    MM_58 = 58,
    MM_80 = 80
}
export type PrinterWidth = PrinterWidthType;
export declare enum PrintBulkItemType {
    TEXT = 0,
    COLUMNS = 1,
    IMAGE_BASE64 = 2,
    SEPARATOR = 3
}
export interface PrintBulkItem {
    type: PrintBulkItemType;
    content?: string;
    options?: PrintOptions;
    texts?: string[];
    columnWidths?: number[];
    columnAlignments?: number[];
    columnStyles?: string[];
    base64?: string;
    imageOptions?: ImagePrintOptions;
}
//# sourceMappingURL=types.d.ts.map