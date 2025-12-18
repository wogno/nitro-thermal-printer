import Foundation
import Combine

/// Connection state enum matching TypeScript spec
public enum ConnectionState: String {
    case disconnected
    case connecting
    case connected
    case reconnecting
}

/// Manages connection state for printer adapters.
/// Provides auto-reconnection functionality.
public class ConnectionManager: ObservableObject {
    @Published public private(set) var state: ConnectionState = .disconnected

    private var currentDeviceId: String?
    private var autoReconnectEnabled = false
    private var maxReconnectAttempts = 3
    private var reconnectDelayMs: Int = 1000
    private var reconnectAttempts = 0

    private var stateChangedCallbacks: [UUID: (ConnectionState) -> Void] = [:]

    public init() {}

    /// Set connection as connected
    public func setConnected(deviceId: String) {
        currentDeviceId = deviceId
        reconnectAttempts = 0
        updateState(.connected)
    }

    /// Set connection as disconnected
    /// Will trigger reconnection if auto-reconnect is enabled
    public func setDisconnected() {
        if autoReconnectEnabled && currentDeviceId != nil &&
            reconnectAttempts < maxReconnectAttempts {
            updateState(.reconnecting)
        } else {
            currentDeviceId = nil
            reconnectAttempts = 0
            updateState(.disconnected)
        }
    }

    /// Set connection as connecting
    public func setConnecting() {
        updateState(.connecting)
    }

    /// Get currently connected device ID
    public func getConnectedDeviceId() -> String? {
        return state == .connected ? currentDeviceId : nil
    }

    /// Check if should attempt reconnection
    public func shouldReconnect() -> Bool {
        return state == .reconnecting && reconnectAttempts < maxReconnectAttempts
    }

    /// Increment reconnect attempts counter
    @discardableResult
    public func incrementReconnectAttempts() -> Int {
        reconnectAttempts += 1
        return reconnectAttempts
    }

    /// Get the reconnect delay in milliseconds
    public func getReconnectDelay() -> Int {
        return reconnectDelayMs
    }

    /// Get the device ID for reconnection
    public func getReconnectDeviceId() -> String? {
        return currentDeviceId
    }

    /// Reset reconnection state
    public func resetReconnection() {
        reconnectAttempts = 0
        if state == .reconnecting {
            updateState(.disconnected)
        }
    }

    // MARK: - Configuration

    public func enableAutoReconnect(_ enabled: Bool) {
        autoReconnectEnabled = enabled
    }

    public func setMaxReconnectAttempts(_ attempts: Int) {
        maxReconnectAttempts = attempts
    }

    public func setReconnectDelay(_ delayMs: Int) {
        reconnectDelayMs = delayMs
    }

    public func isAutoReconnectEnabled() -> Bool {
        return autoReconnectEnabled
    }

    // MARK: - State Change Callbacks

    public func onStateChange(_ callback: @escaping (ConnectionState) -> Void) -> () -> Void {
        let id = UUID()
        stateChangedCallbacks[id] = callback
        return { [weak self] in
            self?.stateChangedCallbacks.removeValue(forKey: id)
        }
    }

    private func updateState(_ newState: ConnectionState) {
        state = newState
        stateChangedCallbacks.values.forEach { $0(newState) }
    }
}
