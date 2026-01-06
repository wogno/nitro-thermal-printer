import type { HybridObject } from 'react-native-nitro-modules'
import type {
  NetDevice,
  PrintOptions,
  ImagePrintOptions,
  PrintJobStatus,
  PermissionResult,
  PrintBulkItem,
} from './types'
import { ConnectionState } from './types'

/**
 * HybridNetPrinter - Network (TCP/IP) Printer Interface
 */
export interface NetPrinter
  extends HybridObject<{ ios: 'swift', android: 'kotlin' }> {

  // Lifecycle
  initialize(): Promise<void>
  // Note: dispose() is implemented natively only

  // Device Discovery
  getDeviceList(): Promise<NetDevice[]>
  scanNetwork(timeout: number): Promise<NetDevice[]>

  // Scan progress listener - returns subscription ID
  addScanProgressListener(callback: (progress: number) => void): string
  removeScanProgressListener(subscriptionId: string): void

  // Connection
  connectPrinter(host: string, port: number, timeout: number): Promise<NetDevice>
  closeConnection(): Promise<void>
  isConnected(): string | undefined
  getConnectionState(): ConnectionState

  // Connection state listener - returns subscription ID
  addConnectionStateListener(callback: (state: ConnectionState) => void): string
  removeConnectionStateListener(subscriptionId: string): void

  // Print Status
  isPrinting(): boolean
  getPrintQueue(): PrintJobStatus[]

  // Print Methods (Async - wait for completion)
  printText(text: string, options: PrintOptions): Promise<PrintJobStatus>
  printBill(text: string, options: PrintOptions): Promise<PrintJobStatus>
  printRaw(data: string): Promise<PrintJobStatus>
  printImage(imageUrl: string, options: ImagePrintOptions): Promise<PrintJobStatus>
  printImageBase64(base64: string, options: ImagePrintOptions): Promise<PrintJobStatus>
  printColumnsText(
    texts: string[],
    columnWidths: number[],
    columnAlignments: number[],
    columnStyles: string[],
    options: PrintOptions
  ): Promise<PrintJobStatus>

  // Print Methods (Sync - fire and forget, instant return)
  printTextSync(text: string, options: PrintOptions): string  // Returns jobId
  printBillSync(text: string, options: PrintOptions): string
  printRawSync(data: string): string
  printImageBase64Sync(base64: string, options: ImagePrintOptions): string
  printColumnsTextSync(
    texts: string[],
    columnWidths: number[],
    columnAlignments: number[],
    columnStyles: string[],
    options: PrintOptions
  ): string

  // Get job status by ID
  getJobStatus(jobId: string): PrintJobStatus | undefined

  // Bulk Print (single call for multiple items)
  printBulk(items: PrintBulkItem[]): Promise<PrintJobStatus>

  // Image Caching
  cacheImage(url: string, key: string): Promise<void>
  printCachedImage(key: string, options: ImagePrintOptions): Promise<PrintJobStatus>
  clearImageCache(): void

  // Permissions
  askPermissions(): Promise<PermissionResult>
}
