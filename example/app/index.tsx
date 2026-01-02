import React, { useState, useEffect } from 'react';
import {
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  ScrollView,
  Alert,
  Platform,
} from 'react-native';
import {
  BLEPrinter,
  NetPrinter,
  USBPrinter,
  ConnectionState,
} from 'react-native-thermal-receipt-printer-image-qr';

type PrinterType = 'BLE' | 'NET' | 'USB';

interface Device {
  name: string;
  address: string;
}

export default function PrinterScreen() {
  const [printerType, setPrinterType] = useState<PrinterType>('BLE');
  const [devices, setDevices] = useState<Device[]>([]);
  const [connected, setConnected] = useState<string | undefined>();
  const [connectionState, setConnectionState] = useState<ConnectionState>(
    ConnectionState.DISCONNECTED
  );
  const [isPrinting, setIsPrinting] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);

  const log = (message: string) => {
    console.log(message);
    setLogs((prev) => [
      `[${new Date().toLocaleTimeString()}] ${message}`,
      ...prev.slice(0, 20),
    ]);
  };

  useEffect(() => {
    let unsubscribe: (() => void) | undefined;

    const initPrinter = async () => {
      try {
        log(`Initializing ${printerType} printer...`);

        if (printerType === 'BLE') {
          await BLEPrinter.init();
          unsubscribe = BLEPrinter.onConnectionStateChange((state) => {
            setConnectionState(state);
            log(`Connection state: ${ConnectionState[state]}`);
          });
          setConnected(BLEPrinter.isConnected());
        } else if (printerType === 'NET') {
          await NetPrinter.init();
          unsubscribe = NetPrinter.onConnectionStateChange((state) => {
            setConnectionState(state);
            log(`Connection state: ${ConnectionState[state]}`);
          });
          setConnected(NetPrinter.isConnected());
        } else {
          await USBPrinter.init();
          unsubscribe = USBPrinter.onConnectionStateChange((state) => {
            setConnectionState(state);
            log(`Connection state: ${ConnectionState[state]}`);
          });
          setConnected(USBPrinter.isConnected());
        }

        log(`${printerType} printer initialized`);
      } catch (error) {
        log(`Init error: ${error}`);
      }
    };

    initPrinter();

    return () => {
      unsubscribe?.();
    };
  }, [printerType]);

  const scanDevices = async () => {
    try {
      log('Scanning for devices...');
      setDevices([]);

      if (printerType === 'BLE') {
        const result = await BLEPrinter.getDeviceList();
        setDevices(
          result.map((d) => ({
            name: d.device_name,
            address: d.inner_mac_address,
          }))
        );
        log(`Found ${result.length} BLE devices`);
      } else if (printerType === 'NET') {
        const result = await NetPrinter.scanNetwork(5000);
        setDevices(
          result.map((d) => ({
            name: d.deviceName || `${d.host}:${d.port}`,
            address: d.host,
          }))
        );
        log(`Found ${result.length} network printers`);
      } else {
        const result = await USBPrinter.getDeviceList();
        setDevices(
          result.map((d) => ({
            name: d.device_name,
            address: `${d.vendor_id}:${d.product_id}`,
          }))
        );
        log(`Found ${result.length} USB devices`);
      }
    } catch (error) {
      log(`Scan error: ${error}`);
    }
  };

  const connectDevice = async (device: Device) => {
    try {
      log(`Connecting to ${device.name}...`);

      if (printerType === 'BLE') {
        await BLEPrinter.connectPrinter(device.address);
        setConnected(BLEPrinter.isConnected());
      } else if (printerType === 'NET') {
        const [host, port] = device.address.includes(':')
          ? device.address.split(':')
          : [device.address, '9100'];
        await NetPrinter.connectPrinter(host, parseInt(port, 10));
        setConnected(NetPrinter.isConnected());
      } else {
        const [vendorId, productId] = device.address.split(':');
        await USBPrinter.connectPrinter(vendorId, productId);
        setConnected(USBPrinter.isConnected());
      }

      log('Connected!');
    } catch (error) {
      log(`Connect error: ${error}`);
      Alert.alert('Connection Failed', String(error));
    }
  };

  const disconnect = async () => {
    try {
      log('Disconnecting...');

      if (printerType === 'BLE') {
        await BLEPrinter.closeConn();
      } else if (printerType === 'NET') {
        await NetPrinter.closeConn();
      } else {
        await USBPrinter.closeConn();
      }

      setConnected(undefined);
      log('Disconnected');
    } catch (error) {
      log(`Disconnect error: ${error}`);
    }
  };

  const printTest = async () => {
    try {
      setIsPrinting(true);
      log('Printing test...');

      const testText = `
================================
     NITRO THERMAL PRINTER
         TEST PRINT
================================

Date: ${new Date().toLocaleString()}
Printer Type: ${printerType}
Connection: ${connected}

--------------------------------
This is a test print from the
Nitro Modules v3 migration.

Features:
- High-performance JSI bridge
- Auto-reconnection (BLE)
- Print queue management
- Image caching
- New Architecture only

================================
       Thank you!
================================


`;

      let result;
      if (printerType === 'BLE') {
        result = await BLEPrinter.printBill(testText, { cut: true, beep: true });
      } else if (printerType === 'NET') {
        result = await NetPrinter.printBill(testText, { cut: true, beep: true });
      } else {
        result = await USBPrinter.printBill(testText, { cut: true, beep: true });
      }

      log(`Print completed: ${result.jobId} - ${result.status}`);
    } catch (error) {
      log(`Print error: ${error}`);
    } finally {
      setIsPrinting(false);
    }
  };

  const askPermissions = async () => {
    try {
      log('Requesting permissions...');

      let result;
      if (printerType === 'BLE') {
        result = await BLEPrinter.askPermissions();
      } else if (printerType === 'NET') {
        result = await NetPrinter.askPermissions();
      } else {
        result = await USBPrinter.askPermissions();
      }

      log(
        `Permissions: granted=${result.granted}, showSettings=${result.shouldShowSettings}`
      );
    } catch (error) {
      log(`Permission error: ${error}`);
    }
  };

  const getConnectionStateColor = () => {
    switch (connectionState) {
      case ConnectionState.CONNECTED:
        return '#4CAF50';
      case ConnectionState.CONNECTING:
      case ConnectionState.RECONNECTING:
        return '#FFC107';
      default:
        return '#F44336';
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* Status */}
      <View style={styles.statusBar}>
        <View
          style={[
            styles.statusIndicator,
            { backgroundColor: getConnectionStateColor() },
          ]}
        />
        <Text style={styles.statusText}>{ConnectionState[connectionState]}</Text>
        {connected && <Text style={styles.connectedText}>{connected}</Text>}
      </View>

      {/* Printer Type Selector */}
      <View style={styles.typeSelector}>
        {(['BLE', 'NET', 'USB'] as PrinterType[]).map((type) => (
          <TouchableOpacity
            key={type}
            style={[
              styles.typeButton,
              printerType === type && styles.typeButtonActive,
            ]}
            onPress={() => setPrinterType(type)}
          >
            <Text
              style={[
                styles.typeButtonText,
                printerType === type && styles.typeButtonTextActive,
              ]}
            >
              {type}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* Actions */}
      <View style={styles.actions}>
        <TouchableOpacity style={styles.button} onPress={askPermissions}>
          <Text style={styles.buttonText}>Permissions</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.button} onPress={scanDevices}>
          <Text style={styles.buttonText}>Scan</Text>
        </TouchableOpacity>
        {connected && (
          <>
            <TouchableOpacity style={styles.buttonDanger} onPress={disconnect}>
              <Text style={styles.buttonText}>Disconnect</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.buttonSuccess, isPrinting && styles.buttonDisabled]}
              onPress={printTest}
              disabled={isPrinting}
            >
              <Text style={styles.buttonText}>
                {isPrinting ? 'Printing...' : 'Print Test'}
              </Text>
            </TouchableOpacity>
          </>
        )}
      </View>

      {/* Device List */}
      <Text style={styles.sectionTitle}>
        Devices {devices.length > 0 && `(${devices.length})`}
      </Text>
      <ScrollView style={styles.deviceList}>
        {devices.length === 0 ? (
          <Text style={styles.emptyText}>
            No devices found. Tap "Scan" to search.
          </Text>
        ) : (
          devices.map((device, index) => (
            <TouchableOpacity
              key={index}
              style={styles.deviceItem}
              onPress={() => connectDevice(device)}
            >
              <Text style={styles.deviceName}>{device.name}</Text>
              <Text style={styles.deviceAddress}>{device.address}</Text>
            </TouchableOpacity>
          ))
        )}
      </ScrollView>

      {/* Logs */}
      <Text style={styles.sectionTitle}>Logs</Text>
      <ScrollView style={styles.logContainer}>
        {logs.map((msg, index) => (
          <Text key={index} style={styles.logText}>
            {msg}
          </Text>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  statusBar: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  statusIndicator: {
    width: 12,
    height: 12,
    borderRadius: 6,
    marginRight: 8,
  },
  statusText: {
    fontSize: 14,
    fontWeight: '600',
  },
  connectedText: {
    fontSize: 12,
    color: '#666',
    marginLeft: 8,
  },
  typeSelector: {
    flexDirection: 'row',
    padding: 8,
    backgroundColor: 'white',
  },
  typeButton: {
    flex: 1,
    padding: 12,
    alignItems: 'center',
    borderRadius: 8,
    marginHorizontal: 4,
    backgroundColor: '#e0e0e0',
  },
  typeButtonActive: {
    backgroundColor: '#2196F3',
  },
  typeButtonText: {
    fontWeight: 'bold',
    color: '#666',
  },
  typeButtonTextActive: {
    color: 'white',
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    padding: 8,
    backgroundColor: 'white',
    gap: 8,
  },
  button: {
    padding: 10,
    paddingHorizontal: 16,
    backgroundColor: '#2196F3',
    borderRadius: 8,
  },
  buttonSuccess: {
    padding: 10,
    paddingHorizontal: 16,
    backgroundColor: '#4CAF50',
    borderRadius: 8,
  },
  buttonDanger: {
    padding: 10,
    paddingHorizontal: 16,
    backgroundColor: '#F44336',
    borderRadius: 8,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: 'white',
    fontWeight: 'bold',
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    padding: 12,
    backgroundColor: '#e0e0e0',
    color: '#333',
  },
  deviceList: {
    flex: 1,
    backgroundColor: 'white',
  },
  emptyText: {
    padding: 16,
    color: '#666',
    textAlign: 'center',
  },
  deviceItem: {
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  deviceName: {
    fontSize: 16,
    fontWeight: '500',
  },
  deviceAddress: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  logContainer: {
    flex: 1,
    backgroundColor: '#1e1e1e',
    padding: 8,
    maxHeight: 150,
  },
  logText: {
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 10,
    color: '#4CAF50',
    marginBottom: 2,
  },
});
