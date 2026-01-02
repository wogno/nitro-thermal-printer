"use strict";

/**
 * Shared types for Nitro Thermal Printer
 */

// Device types

// Print options

// Connection state enum
export let ConnectionState = /*#__PURE__*/function (ConnectionState) {
  ConnectionState[ConnectionState["DISCONNECTED"] = 0] = "DISCONNECTED";
  ConnectionState[ConnectionState["CONNECTING"] = 1] = "CONNECTING";
  ConnectionState[ConnectionState["CONNECTED"] = 2] = "CONNECTED";
  ConnectionState[ConnectionState["RECONNECTING"] = 3] = "RECONNECTING";
  return ConnectionState;
}({});

// Print job status enum
export let PrintJobStatusType = /*#__PURE__*/function (PrintJobStatusType) {
  PrintJobStatusType[PrintJobStatusType["QUEUED"] = 0] = "QUEUED";
  PrintJobStatusType[PrintJobStatusType["PRINTING"] = 1] = "PRINTING";
  PrintJobStatusType[PrintJobStatusType["COMPLETED"] = 2] = "COMPLETED";
  PrintJobStatusType[PrintJobStatusType["FAILED"] = 3] = "FAILED";
  return PrintJobStatusType;
}({});

// Print job status

// Permission result

// Column alignment
export let ColumnAlignment = /*#__PURE__*/function (ColumnAlignment) {
  ColumnAlignment[ColumnAlignment["LEFT"] = 0] = "LEFT";
  ColumnAlignment[ColumnAlignment["CENTER"] = 1] = "CENTER";
  ColumnAlignment[ColumnAlignment["RIGHT"] = 2] = "RIGHT";
  return ColumnAlignment;
}({});

// Printer width type
export let PrinterWidthType = /*#__PURE__*/function (PrinterWidthType) {
  PrinterWidthType[PrinterWidthType["MM_58"] = 58] = "MM_58";
  PrinterWidthType[PrinterWidthType["MM_80"] = 80] = "MM_80";
  return PrinterWidthType;
}({});

// Legacy alias for backward compatibility
//# sourceMappingURL=types.js.map