package com.example.oneclickorder.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oneclickorder.domain.BLEUseCase
import com.juul.kable.Peripheral
import com.juul.kable.State
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BLEViewModel @Inject constructor(
    private val bleUseCase: BLEUseCase  // Injecting BLEUseCase instead of BLEManager
) : ViewModel() {
    private val orderChannel = Channel<String>(Channel.UNLIMITED)

    // High-level connection state (Idle, Connected, Disconnected, etc.)
    private val _orderState = MutableStateFlow("Idle")
    val orderState: StateFlow<String> = _orderState

    // Detailed connection state (Connecting.Bluetooth, Connecting.Services, etc.)
    private val _uiConnectionDetailState = MutableStateFlow("Idle")
    val uiConnectionDetailState: StateFlow<String> = _uiConnectionDetailState

    // Hold the connected peripheral
    private var connectedPeripheral: Peripheral? = null

    // Debounce interval for state updates (in milliseconds)
    private val delay = 300L

    init {
        observePeripheralConnectionState()
        processOrderQueue()  // Start processing orders from the queue
    }

    // Process the orders from the queue one by one
    private fun processOrderQueue() {
        viewModelScope.launch {
            for (order in orderChannel) {
                sendOrderData(order)
            }
        }
    }

    // Function to queue the order data
    fun queueOrderData(orderData: String) {
        viewModelScope.launch {
            orderChannel.send(orderData)  // Send the order to the queue
        }
    }

    // Function to start the BLE process of scanning and connecting to the Cashier
    fun scanAndConnect() {
        viewModelScope.launch {
            _uiConnectionDetailState.value = "Scanning and connecting..."
            connectedPeripheral = bleUseCase.scanAndConnect()  // Use BLEUseCase to scan and connect
            if (connectedPeripheral != null) {
                _uiConnectionDetailState.value = "Connected"
                observePeripheralConnectionState()  // Start observing the peripheral state once connected
            } else {
                _uiConnectionDetailState.value = "Failed to connect"
            }
        }
    }

    // Function to send order data only (separated from observing notifications)
    private fun sendOrderData(orderData: String) {
        viewModelScope.launch {
            connectedPeripheral?.let { peripheral ->
                _orderState.value = "Sending order data..."
                val success = bleUseCase.sendOrderData(peripheral, orderData)  // Use BLEUseCase to send order data
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
                val characteristic = bleUseCase.getPeripheralCharacteristic(peripheral)  // Use BLEUseCase to get characteristic
                if (characteristic != null) {
                    val responseData = StringBuilder()  // Accumulate chunks here
                    bleUseCase.observeNotifications(peripheral)?.collect { dataChunk ->
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
    @OptIn(FlowPreview::class)
    private fun observePeripheralConnectionState() {
        connectedPeripheral?.let { peripheral ->
            viewModelScope.launch {
                // Debounce state updates to avoid rapid updates
                peripheral.state
                    // Only emit if the state actually changes
                    .debounce(delay)  // Debounce to avoid frequent updates
                    .collect { state ->
                        handleConnectionState(state)
                    }
            }
        }
    }

    // Function to handle connection state changes
    private fun handleConnectionState(state: State) {
        when (state) {
            is State.Connected -> {
                if (_uiConnectionDetailState.value != "Connected") {
                    _uiConnectionDetailState.value = "Connected"
                    Log.d("BLEViewModel", "Peripheral connected")
                }
            }
            is State.Disconnected -> {
                if (_uiConnectionDetailState.value != "Disconnected") {
                    _uiConnectionDetailState.value = "Disconnected"
                    Log.d("BLEViewModel", "Peripheral disconnected, attempting to reconnect...")
                    attemptReconnectionWithRetry()  // Try to reconnect with retry logic
                }
            }
            is State.Disconnecting -> {
                if (_uiConnectionDetailState.value != "Disconnecting...") {
                    _uiConnectionDetailState.value = "Disconnecting..."
                }
            }
            is State.Connecting.Bluetooth -> {
                if (_uiConnectionDetailState.value != "Connecting: Bluetooth...") {
                    _uiConnectionDetailState.value = "Connecting: Bluetooth..."
                }
            }
            is State.Connecting.Services -> {
                if (_uiConnectionDetailState.value != "Connecting: Discovering Services...") {
                    _uiConnectionDetailState.value = "Connecting: Discovering Services..."
                }
            }
            is State.Connecting.Observes -> {
                if (_uiConnectionDetailState.value != "Connecting: Setting Up Observes...") {
                    _uiConnectionDetailState.value = "Connecting: Setting Up Observes..."
                }
            }
            else -> {
                _uiConnectionDetailState.value = state::class.simpleName ?: "Unknown State"
            }
        }
    }

    // Retry logic for reconnection with delay
    private fun attemptReconnectionWithRetry() {
        viewModelScope.launch {
            if (_uiConnectionDetailState.value != "Attempting to reconnect...") {
                _uiConnectionDetailState.value = "Attempting to reconnect..."
            }
            try {
                retryWithFixedDelay {
                    connectedPeripheral = bleUseCase.scanAndConnect()  // Use BLEUseCase to scan and reconnect
                    if (connectedPeripheral != null) {
                        _uiConnectionDetailState.value = "Connected"
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