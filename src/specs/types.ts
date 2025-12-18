/**
 * Shared types for Nitro Thermal Printer
 */

// Device types
export interface BLEDevice {
  deviceName: string;
  innerMacAddress: string;
}

export interface NetDevice {
  host: string;
  port: number;
  deviceName?: string;
}

export interface USBDevice {
  deviceName: string;
  vendorId: number;
  productId: number;
  deviceId?: number;
}

// Print options
export interface PrintOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
}

export interface ImagePrintOptions extends PrintOptions {
  imageWidth?: number;
  imageHeight?: number;
  printerWidthType?: 58 | 80;
  paddingX?: number; // iOS only
}

// Connection state
export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

// Print job status
export interface PrintJobStatus {
  jobId: string;
  status: 'queued' | 'printing' | 'completed' | 'failed';
  error?: string;
}

// Permission result
export interface PermissionResult {
  granted: boolean;
  shouldShowSettings: boolean;
}

// Column alignment
export enum ColumnAlignment {
  LEFT = 0,
  CENTER = 1,
  RIGHT = 2,
}

// Printer width
export enum PrinterWidth {
  '58mm' = 58,
  '80mm' = 80,
}
