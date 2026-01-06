# Nitro Thermal Printer

High-performance React Native thermal receipt printer library using **Nitro Modules** (JSI).

Supports USB, Bluetooth (BLE), and Network printers with near-native performance.

## Features

| Feature | Android | iOS |
|---------|---------|-----|
| USB Printer | ✅ | ❌ |
| BLE Printer | ✅ | ✅ |
| Network Printer | ✅ | ✅ |
| Print Text | ✅ | ✅ |
| Print Image (URL) | ✅ | ✅ |
| Print Image (Base64) | ✅ | ✅ |
| Print Columns | ✅ | ✅ |
| Auto-Reconnection | ✅ | ✅ |
| Connection State | ✅ | ✅ |
| Print Queue | ✅ | ✅ |
| Image Caching | ✅ | ✅ |
| Network Scan | ✅ | ✅ |

## What's New in v3.0

- **Nitro Modules (JSI)** - Near-native performance (~0.1ms vs ~10ms bridge overhead)
- **Kotlin** - Complete Android rewrite in Kotlin with Coroutines
- **Swift** - Complete iOS rewrite in Swift with async/await
- **Auto-reconnection** - Automatic Bluetooth reconnection on disconnect
- **Print Queue** - Prevents race conditions, ensures print order
- **Connection State** - Real-time connection monitoring
- **Image Caching** - LRU cache for faster image printing
- **Async/Await** - All methods return Promises

## Requirements

- React Native >= 0.76.0
- New Architecture enabled
- iOS 13.0+
- Android API 24+

## Installation

```bash
# npm
npm install react-native-thermal-receipt-printer-image-qr react-native-nitro-modules

# yarn
yarn add react-native-thermal-receipt-printer-image-qr react-native-nitro-modules
```

### iOS Setup

```bash
cd ios && pod install
```

### Android Setup

No additional setup required.

## Quick Start

### BLE Printer

```typescript
import { BLEPrinter } from 'react-native-thermal-receipt-printer-image-qr';

async function printWithBLE() {
  // Initialize
  await BLEPrinter.init();

  // Get paired devices
  const devices = await BLEPrinter.getDeviceList();
  console.log('Devices:', devices);

  // Connect to printer
  await BLEPrinter.connectPrinter(devices[0].inner_mac_address);

  // Check connection
  const connectedDevice = BLEPrinter.isConnected();
  console.log('Connected to:', connectedDevice);

  // Print
  await BLEPrinter.printBill('Hello World!\n');

  // Disconnect
  await BLEPrinter.closeConn();
}
```

### Network Printer

```typescript
import { NetPrinter } from 'react-native-thermal-receipt-printer-image-qr';

async function printWithNetwork() {
  await NetPrinter.init();

  // Scan network for printers
  const devices = await NetPrinter.scanNetwork();
  console.log('Found printers:', devices);

  // Or connect directly
  await NetPrinter.connectPrinter('192.168.1.100', 9100);

  // Print
  await NetPrinter.printBill('Invoice #12345\n');

  await NetPrinter.closeConn();
}
```

### USB Printer (Android only)

```typescript
import { USBPrinter } from 'react-native-thermal-receipt-printer-image-qr';

async function printWithUSB() {
  await USBPrinter.init();

  const devices = await USBPrinter.getDeviceList();

  await USBPrinter.connectPrinter(devices[0].vendor_id, devices[0].product_id);

  await USBPrinter.printBill('Receipt\n');

  await USBPrinter.closeConn();
}
```

## API Reference

### Common Methods (All Printers)

```typescript
// Lifecycle
init(): Promise<void>
closeConn(): Promise<void>

// Discovery
getDeviceList(): Promise<Device[]>

// Connection State
isConnected(): string | undefined
getConnectionState(): ConnectionState
onConnectionStateChange(callback: (state: string) => void): () => void

// Print Status
isPrinting(): boolean
getPrintQueue(): PrintJobStatus[]

// Print Methods
printText(text: string, options?: PrinterOptions): Promise<PrintJobStatus>
printBill(text: string, options?: PrinterOptions): Promise<PrintJobStatus>
printRaw(data: string): Promise<PrintJobStatus>
printImage(imageUrl: string, options?: PrinterImageOptions): Promise<PrintJobStatus>
printImageBase64(base64: string, options?: PrinterImageOptions): Promise<PrintJobStatus>
printColumnsText(texts: string[], columnWidth: number[], columnAlignment: number[], columnStyle?: string[], options?: PrinterOptions): Promise<PrintJobStatus>

// Image Caching
cacheImage(url: string, key: string): Promise<void>
printCachedImage(key: string, options?: PrinterImageOptions): Promise<PrintJobStatus>
clearImageCache(): void

// Permissions
askPermissions(): Promise<PermissionResult>
```

### BLE Specific Methods

```typescript
// Auto-reconnection
enableAutoReconnect(enabled: boolean): void
setReconnectAttempts(maxAttempts: number): void
setReconnectDelay(delayMs: number): void
```

### Network Specific Methods

```typescript
// Network scan
scanNetwork(timeout?: number): Promise<NetDevice[]>
onScanProgress(callback: (progress: number) => void): () => void
```

### USB Specific Methods (Android)

```typescript
// Device events
onDeviceAttached(callback: (device: USBDevice) => void): () => void
onDeviceDetached(callback: () => void): () => void
```

## Print Options

```typescript
interface PrinterOptions {
  beep?: boolean;      // Beep after print (default: false)
  cut?: boolean;       // Cut paper after print (default: false)
  tailingLine?: boolean; // Add trailing lines (default: false)
  encoding?: string;   // Text encoding (default: UTF-8)
}

interface PrinterImageOptions extends PrinterOptions {
  imageWidth?: number;  // Image width (default: 0)
  imageHeight?: number; // Image height (default: 0)
  printerWidthType?: 58 | 80; // Printer width in mm (default: 80)
  paddingX?: number;    // Horizontal padding (default: 0)
}
```

## Bulk Print

The `printBulk()` method automatically normalizes all items to ensure JSI bridge compatibility. You don't need to manually normalize items - just pass them as-is:

```typescript
await BLEPrinter.printBulk([
  {
    type: PrintBulkItemType.TEXT,
    content: "Hello",
    options: { cut: true, beep: true } // Missing fields are auto-filled with defaults
  },
  {
    type: PrintBulkItemType.COLUMNS,
    texts: ["Item", "Price"],
    columnWidths: [10, 20],
    columnAlignments: [ColumnAlignment.LEFT, ColumnAlignment.RIGHT]
    // columnStyles is optional
  }
]);
```

**Note:** The package automatically:
- Completes missing `PrintOptions` fields with defaults
- Completes missing `ImagePrintOptions` fields with defaults
- Omits `undefined`/`null` values for better JSI compatibility
- Validates item types

If you need manual normalization, you can import `normalizePrintBulkItem`:
```typescript
import { normalizePrintBulkItem } from 'react-native-thermal-receipt-printer-image-qr';
```

## Styling with ESC/POS Commands

```typescript
import { COMMANDS } from 'react-native-thermal-receipt-printer-image-qr';

const { TEXT_FORMAT } = COMMANDS;

const text = `
${TEXT_FORMAT.TXT_BOLD_ON}Bold Text${TEXT_FORMAT.TXT_BOLD_OFF}
${TEXT_FORMAT.TXT_UNDERL_ON}Underlined${TEXT_FORMAT.TXT_UNDERL_OFF}
${TEXT_FORMAT.TXT_ALIGN_CT}Centered${TEXT_FORMAT.TXT_ALIGN_LT}
`;

await BLEPrinter.printText(text);
```

## Print Columns Example

```typescript
import { COMMANDS, ColumnAlignment } from 'react-native-thermal-receipt-printer-image-qr';

const BOLD_ON = COMMANDS.TEXT_FORMAT.TXT_BOLD_ON;
const BOLD_OFF = COMMANDS.TEXT_FORMAT.TXT_BOLD_OFF;

// 80mm printer = 46 chars, 58mm printer = 30 chars
const columnWidth = [27, 7, 12]; // Total: 46
const columnAlignment = [
  ColumnAlignment.LEFT,
  ColumnAlignment.CENTER,
  ColumnAlignment.RIGHT,
];

// Print header
await BLEPrinter.printColumnsText(
  ['Product', 'Qty', 'Price'],
  columnWidth,
  columnAlignment,
  [BOLD_ON, '', '']
);

// Print items
const items = [
  ['Coffee Latte', 'x2', '$8.00'],
  ['Croissant', 'x1', '$4.50'],
  ['Orange Juice', 'x3', '$12.00'],
];

for (const item of items) {
  await BLEPrinter.printColumnsText(
    item,
    columnWidth,
    columnAlignment,
    [BOLD_OFF, '', '']
  );
}

// Print total
await BLEPrinter.printBill('\n--------------\nTotal: $24.50\n');
```

## Connection State Monitoring

```typescript
// Listen to connection changes
const unsubscribe = BLEPrinter.onConnectionStateChange((state) => {
  console.log('Connection state:', state);
  // 'disconnected' | 'connecting' | 'connected' | 'reconnecting'
});

// Enable auto-reconnection
BLEPrinter.enableAutoReconnect(true);
BLEPrinter.setReconnectAttempts(3);
BLEPrinter.setReconnectDelay(2000);

// Cleanup
unsubscribe();
```

## Image Caching

```typescript
// Cache images for faster printing
await BLEPrinter.cacheImage('https://example.com/logo.png', 'logo');

// Print cached image (much faster)
await BLEPrinter.printCachedImage('logo', { imageWidth: 200 });

// Clear cache when done
BLEPrinter.clearImageCache();
```

## Permissions

### Android

Add to `AndroidManifest.xml`:

```xml
<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

<!-- Location (required for BLE scanning on Android < 12) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- USB -->
<uses-feature android:name="android.hardware.usb.host" />
```

### iOS

Add to `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>We need Bluetooth to connect to thermal printers</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>We need Bluetooth to connect to thermal printers</string>
<key>NSLocalNetworkUsageDescription</key>
<string>We need local network access to find network printers</string>
```

## Troubleshooting

### App freezes after print

This was fixed in v3.0 by implementing a proper print queue. All print operations are now async and non-blocking.

### Bluetooth connection fails on Android 12+

Make sure you have `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions:

```typescript
const result = await BLEPrinter.askPermissions();
if (!result.granted) {
  // Open settings
  console.log('Please grant Bluetooth permissions');
}
```

### Network printer not found

1. Ensure printer is on the same network
2. Check if port 9100 is open
3. Try direct connection: `NetPrinter.connectPrinter('IP', 9100)`

## Migration from v2.x

```typescript
// v2.x (callbacks, sync)
BLEPrinter.printText('Hello'); // fire-and-forget

// v3.x (promises, async)
await BLEPrinter.printText('Hello'); // wait for completion
const status = await BLEPrinter.printBill('Hello'); // get status
console.log(status); // { id, status: 'completed' }
```

## License

MIT
