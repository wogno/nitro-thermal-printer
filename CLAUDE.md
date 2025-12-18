# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a React Native library for thermal receipt printers supporting USB, Bluetooth (BLE), and Network (TCP/IP) connections. It's a fork of `react-native-thermal-receipt-printer` with added image/QR printing capabilities.

## Build Commands

```bash
# Build TypeScript to dist/
yarn build

# Install dependencies and setup example app
yarn bootstrap

# Run example app
yarn example android
yarn example ios
```

## Architecture

### TypeScript Layer (`src/`)
- **`index.ts`** - Main entry point exporting three printer interfaces: `USBPrinter`, `BLEPrinter`, `NetPrinter`
- **`utils/EPToolkit.ts`** - ESC/POS command encoding and text/image buffer generation
- **`utils/printer-commands.ts`** - ESC/POS command constants (text formatting, alignment, etc.)
- **`utils/print-column.ts`** - Column-based text layout processing
- **`utils/net-connect.ts`** - Network printer connectivity with ping/timeout handling

### Native Modules
Each printer type has a corresponding native module:

**Android (`android/src/main/java/com/pinmi/react/printer/`)**
- `RN[USB|BLE|Net]PrinterModule.java` - React Native bridge modules
- `adapter/[USB|BLE|Net]PrinterAdapter.java` - Platform-specific printer communication
- `adapter/PrinterAdapter.java` - Common interface for all adapters
- `adapter/UtilsImage.java` - Image bitmap processing for thermal printing

**iOS (`ios/`)**
- `RN[USB|BLE|Net]Printer.m` - Objective-C native modules
- Uses CocoaAsyncSocket for network printing

### Printer Width Constants
- 80mm paper: 46 characters per line
- 58mm paper: 30 characters per line

## Key Dependencies

- `react-native-ping` - Required peer dependency for network printer discovery
- `iconv-lite` - Character encoding support
- `buffer` - Binary data handling for ESC/POS commands

## Example App

Located in `example/` - a full React Native app demonstrating all printer types with QR code scanning and Sunmi device integration.
