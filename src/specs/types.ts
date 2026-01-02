/**
 * Shared types for Nitro Thermal Printer
 */

// Device types
export interface BLEDevice {
  deviceName: string
  innerMacAddress: string
}

export interface NetDevice {
  host: string
  port: number
  deviceName: string | undefined
}

export interface USBDevice {
  deviceName: string
  vendorId: number
  productId: number
  deviceId: number | undefined
}

// Print options
export interface PrintOptions {
  beep: boolean
  cut: boolean
  tailingLine: boolean
  encoding: string
}

export interface ImagePrintOptions {
  beep: boolean
  cut: boolean
  tailingLine: boolean
  encoding: string
  imageWidth: number
  imageHeight: number
  printerWidthType: PrinterWidthType
  paddingX: number
}

// Connection state enum
export enum ConnectionState {
  DISCONNECTED = 0,
  CONNECTING = 1,
  CONNECTED = 2,
  RECONNECTING = 3
}

// Print job status enum
export enum PrintJobStatusType {
  QUEUED = 0,
  PRINTING = 1,
  COMPLETED = 2,
  FAILED = 3
}

// Print job status
export interface PrintJobStatus {
  jobId: string
  status: PrintJobStatusType
  error: string | undefined
}

// Permission result
export interface PermissionResult {
  granted: boolean
  shouldShowSettings: boolean
}

// Column alignment
export enum ColumnAlignment {
  LEFT = 0,
  CENTER = 1,
  RIGHT = 2
}

// Printer width type
export enum PrinterWidthType {
  MM_58 = 58,
  MM_80 = 80
}

// Legacy alias for backward compatibility
export type PrinterWidth = PrinterWidthType
