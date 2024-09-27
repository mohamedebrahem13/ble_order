package com.example.oneclickorder.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oneclickorder.data.BLEManager
import com.juul.kable.Peripheral
import com.juul.kable.State
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BLEViewModel @Inject constructor(
    private val bleManager: BLEManager
) : ViewModel() {

    // High-level connection state (Idle, Connected, Disconnected, etc.)
    private val _uiConnectionState = MutableStateFlow("Idle")
    val uiConnectionState: StateFlow<String> = _uiConnectionState

    // Detailed connection state (Connecting.Bluetooth, Connecting.Services, etc.)
    private val _uiConnectionDetailState = MutableStateFlow("Idle")
    val uiConnectionDetailState: StateFlow<String> = _uiConnectionDetailState

    // Hold the connected peripheral
    private var connectedPeripheral: Peripheral? = null

    // Initialize the connection observer
    init {
        observePeripheralConnectionState()
    }

    // Function to start the BLE process of scanning and connecting to the Cashier
    fun scanAndConnect() {
        viewModelScope.launch {
            _uiConnectionState.value = "Scanning and connecting..."
            connectedPeripheral = bleManager.scanAndConnect()  // Scan and connect to peripheral
            if (connectedPeripheral != null) {
                _uiConnectionState.value = "Connected to Cashier"
                observePeripheralConnectionState()  // Start observing the peripheral state once connected
            } else {
                _uiConnectionState.value = "Failed to connect"
            }
        }
    }

    // Function to send order data and subscribe to notifications
    fun sendOrderData(orderData: String) {
        viewModelScope.launch {
            connectedPeripheral?.let { peripheral ->
                _uiConnectionState.value = "Sending order data..."
                val response = bleManager.writeOrderDataAndSubscribe(peripheral, orderData)  // Write data and subscribe to notifications
                Log.d("BLEViewModel", "Response from server: $response")
                _uiConnectionState.value = response ?: "Failed to receive response"
            } ?: run {
                _uiConnectionState.value = "No connected device. Please scan and connect first."
            }
        }
    }

    // Observe the peripheral's state and attempt reconnection if disconnected
    private fun observePeripheralConnectionState() {
        connectedPeripheral?.let { peripheral ->
            viewModelScope.launch {
                peripheral.state.collect { state ->
                    when (state) {
                        is State.Connected -> {
                            _uiConnectionState.value = "Connected"
                            _uiConnectionDetailState.value = "Connected" // High-level and detailed states are in sync
                        }
                        is State.Disconnected -> {
                            _uiConnectionState.value = "Disconnected"
                            _uiConnectionDetailState.value = "Disconnected"
                            Log.d("BLEViewModel", "Peripheral disconnected, attempting to reconnect...")
                            attemptReconnectionWithRetry()  // Try to reconnect with retry logic
                        }
                        is State.Disconnecting -> {
                            _uiConnectionState.value = "Disconnecting..."
                            _uiConnectionDetailState.value = "Disconnecting..."
                        }
                        is State.Connecting.Bluetooth -> {
                            _uiConnectionDetailState.value = "Connecting: Bluetooth..."
                        }
                        is State.Connecting.Services -> {
                            _uiConnectionDetailState.value = "Connecting: Discovering Services..."
                        }
                        is State.Connecting.Observes -> {
                            _uiConnectionDetailState.value = "Connecting: Setting Up Observes..."
                        }
                        else -> {
                            _uiConnectionDetailState.value = state::class.simpleName ?: "Unknown State"
                        }
                    }
                }
            }
        }
    }

    private fun attemptReconnectionWithRetry() {
        viewModelScope.launch {
            _uiConnectionState.value = "Attempting to reconnect..."
            try {
                retryWithBackoff(initialDelay = 2000L, factor = 2.5) {
                    connectedPeripheral = bleManager.scanAndConnect()  // Scan and reconnect
                    if (connectedPeripheral != null) {
                        _uiConnectionState.value = "Reconnected to Cashier"
                        _uiConnectionDetailState.value = "Reconnected to Cashier"
                        observePeripheralConnectionState()  // Re-observe the peripheral state after reconnection
                    } else {
                        throw Exception("Failed to reconnect")
                    }
                }
            } catch (e: Exception) {
                Log.e("BLEViewModel", "Reconnection failed: ${e.message}")
                _uiConnectionState.value = "Reconnection failed"
                _uiConnectionDetailState.value = "Reconnection failed"
            }
        }
    }

    // Retry logic with exponential backoff for infinite retries until success
    private suspend fun <T> retryWithBackoff(
        initialDelay: Long = 2000L,  // Start with a 2 second delay
        factor: Double = 2.5,  // Exponential backoff factor increased to 2.5
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        while (true) {  // Infinite loop for retrying until success
            try {
                return block()  // Try to execute the block
            } catch (e: Exception) {
                Log.e("BLEViewModel", "Attempt failed: ${e.message}, retrying in $currentDelay ms")
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()  // Exponential backoff delay
        }
    }
}