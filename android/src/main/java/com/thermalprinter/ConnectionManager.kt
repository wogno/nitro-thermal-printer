package com.thermalprinter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connection state enum matching TypeScript spec
 */
enum class ConnectionState(val value: String) {
    DISCONNECTED("disconnected"),
    CONNECTING("connecting"),
    CONNECTED("connected"),
    RECONNECTING("reconnecting");

    companion object {
        fun fromString(value: String): ConnectionState {
            return entries.find { it.value == value } ?: DISCONNECTED
        }
    }
}

/**
 * Manages connection state for printer adapters.
 * Provides auto-reconnection functionality.
 */
class ConnectionManager {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var currentDeviceId: String? = null
    private var autoReconnectEnabled = false
    private var maxReconnectAttempts = 3
    private var reconnectDelayMs = 1000L
    private var reconnectAttempts = 0

    /**
     * Set connection as connected
     */
    fun setConnected(deviceId: String) {
        currentDeviceId = deviceId
        reconnectAttempts = 0
        _state.value = ConnectionState.CONNECTED
    }

    /**
     * Set connection as disconnected
     * Will trigger reconnection if auto-reconnect is enabled
     */
    fun setDisconnected() {
        if (autoReconnectEnabled && currentDeviceId != null &&
            reconnectAttempts < maxReconnectAttempts) {
            _state.value = ConnectionState.RECONNECTING
        } else {
            currentDeviceId = null
            reconnectAttempts = 0
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    /**
     * Set connection as connecting
     */
    fun setConnecting() {
        _state.value = ConnectionState.CONNECTING
    }

    /**
     * Get currently connected device ID
     * @returns Device ID if connected, null otherwise
     */
    fun getConnectedDeviceId(): String? {
        return if (_state.value == ConnectionState.CONNECTED) currentDeviceId else null
    }

    /**
     * Check if should attempt reconnection
     */
    fun shouldReconnect(): Boolean {
        return _state.value == ConnectionState.RECONNECTING &&
                reconnectAttempts < maxReconnectAttempts
    }

    /**
     * Increment reconnect attempts counter
     * @returns Current attempt number
     */
    fun incrementReconnectAttempts(): Int {
        return ++reconnectAttempts
    }

    /**
     * Get the reconnect delay in milliseconds
     */
    fun getReconnectDelay(): Long = reconnectDelayMs

    /**
     * Get the device ID for reconnection
     */
    fun getReconnectDeviceId(): String? = currentDeviceId

    /**
     * Reset reconnection state
     */
    fun resetReconnection() {
        reconnectAttempts = 0
        if (_state.value == ConnectionState.RECONNECTING) {
            _state.value = ConnectionState.DISCONNECTED
        }
    }

    // Configuration methods
    fun enableAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
    }

    fun setMaxReconnectAttempts(attempts: Int) {
        maxReconnectAttempts = attempts
    }

    fun setReconnectDelay(delayMs: Long) {
        reconnectDelayMs = delayMs
    }

    fun isAutoReconnectEnabled(): Boolean = autoReconnectEnabled
}
