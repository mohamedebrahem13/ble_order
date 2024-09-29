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
    private val _orderState = MutableStateFlow("Idle")
    val orderState: StateFlow<String> = _orderState

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
            _uiConnectionDetailState.value = "Scanning and connecting..."
            connectedPeripheral = bleManager.scanAndConnect()  // Scan and connect to peripheral
            if (connectedPeripheral != null) {
                _uiConnectionDetailState.value = "Connected to Cashier"
                observePeripheralConnectionState()  // Start observing the peripheral state once connected
            } else {
                _uiConnectionDetailState.value = "Failed to connect"
            }
        }
    }

    // Function to send order data only (separated from observing notifications)
    fun sendOrderData(orderData: String) {
        viewModelScope.launch {
            connectedPeripheral?.let { peripheral ->
                _orderState.value = "Sending order data..."
                val success = bleManager.writeOrderData(peripheral, orderData)  // Write data to the peripheral
                if (success) {
                    startObservingNotifications()
                } else {
                    _orderState.value = "Failed to send order data"
                }
            } ?: run {
                _orderState.value = "No connected device. Please scan and connect first."
            }
        }
    }
    // Function to start observing notifications
    private fun startObservingNotifications() {
        viewModelScope.launch {
            connectedPeripheral?.let { peripheral ->
                val characteristic = bleManager.getCharacteristic(peripheral)
                if (characteristic != null) {
                    val responseData = StringBuilder()  // Accumulate chunks here
                    bleManager.startObservingNotifications(peripheral, characteristic)
                        .collect { dataChunk ->
                            // Append each chunk to the accumulated data
                            responseData.append(dataChunk)

                            // Log each chunk received for debugging
                            Log.d("BLEViewModel", "Received notification chunk: $dataChunk")

                            // Check for the "END" marker indicating the full message is received
                            if (responseData.contains("END")) {
                                val fullResponse = responseData.toString().replace("END", "")  // Clean the message
                                Log.d("BLEViewModel", "Full notification received: $fullResponse")

                                // Update the state with the full response
                                _orderState.value = fullResponse

                                // Clear the responseData StringBuilder for future notifications if necessary
                                responseData.clear()
                            }
                        }
                } else {
                    _orderState.value = "Notification characteristic not found"
                }
            } ?: run {
                _orderState.value = "No connected device. Please scan and connect first."
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
                            _uiConnectionDetailState.value = "Connected" // High-level and detailed states are in sync
                        }
                        is State.Disconnected -> {
                            _uiConnectionDetailState.value = "Disconnected"
                            Log.d("BLEViewModel", "Peripheral disconnected, attempting to reconnect...")
                            attemptReconnectionWithRetry()  // Try to reconnect with retry logic
                        }
                        is State.Disconnecting -> {
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
            _orderState.value = "Attempting to reconnect..."
            try {
                retryWithFixedDelay {
                    connectedPeripheral = bleManager.scanAndConnect()  // Scan and reconnect
                    if (connectedPeripheral != null) {
                        _uiConnectionDetailState.value = "Reconnected to Cashier"
                        observePeripheralConnectionState()  // Re-observe the peripheral state after reconnection
                    } else {
                        throw Exception("Failed to reconnect")
                    }
                }
            } catch (e: Exception) {
                Log.e("BLEViewModel", "Reconnection failed: ${e.message}")
                _uiConnectionDetailState.value = "Reconnection failed"
            }
        }
    }

    // Retry logic with a fixed 2-second delay for infinite retries until success
    private suspend fun <T> retryWithFixedDelay(
        delayDuration: Long = 2000L,  // Fixed 2 second delay
        block: suspend () -> T
    ): T {
        while (true) {  // Infinite loop for retrying until success
            try {
                return block()  // Try to execute the block
            } catch (e: Exception) {
                Log.e("BLEViewModel", "Attempt failed: ${e.message}, retrying in $delayDuration ms")
            }
            delay(delayDuration)  // Fixed delay between retries
        }
    }

}