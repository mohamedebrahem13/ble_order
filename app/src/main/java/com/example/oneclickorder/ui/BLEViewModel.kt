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
    private val bleUseCase: BLEUseCase
) : ViewModel() {

    // Single state object to manage both connection and order state
    private val _bleState = MutableStateFlow(BLEState())
    val bleState: StateFlow<BLEState> = _bleState

    private val intentChannel = Channel<BLEIntent>(Channel.UNLIMITED)
    private val orderChannel = Channel<String>(Channel.UNLIMITED)
    private var connectedPeripheral: Peripheral? = null

    init {
        processIntents()
        processOrderQueue()
    }

    // Public method for dispatching intents
    fun sendIntent(intent: BLEIntent) {
        viewModelScope.launch {
            intentChannel.send(intent)
        }
    }

    // Process intents one by one
    private fun processIntents() {
        viewModelScope.launch {
            for (intent in intentChannel) {
                when (intent) {
                    is BLEIntent.ScanAndConnect -> handleScanAndConnect()
                    is BLEIntent.SendOrderData -> queueOrderData(intent.orderData)
                }
            }
        }
    }

    // Queue the order data when SendOrderData intent is received
    private fun queueOrderData(orderData: String) {
        viewModelScope.launch {
            orderChannel.send(orderData)
        }
    }

    // Process the orders from the queue one by one
    private fun processOrderQueue() {
        viewModelScope.launch {
            for (order in orderChannel) {
                sendOrderData(order)
            }
        }
    }

    // Handle Scan and Connect intent
    private fun handleScanAndConnect() {
        viewModelScope.launch {
            // Update the connection state to scanning
            _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Scanning)

            connectedPeripheral = bleUseCase.scanAndConnect()
            if (connectedPeripheral != null) {
                // Update the connection state to connected
                _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Connected)
                observePeripheralConnectionState()
            } else {
                // Update the connection state to error
                _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Error("Failed to connect to device"))
                attemptReconnectionWithRetry()
            }
        }
    }

    // Handle sending order data
    private fun sendOrderData(orderData: String) {
        viewModelScope.launch {
            connectedPeripheral?.let { peripheral ->
                // Update the order state to sending order
                _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.SendingOrder(orderData))

                val success = bleUseCase.sendOrderData(peripheral, orderData)
                if (success) {
                    startObservingNotifications()
                } else {
                    // Update the order state to error
                    _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.Error("Failed to send order data"))
                }
            } ?: run {
                // Update the order state to error
                _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.Error("No connected device. Please scan and connect first."))
            }
        }
    }

    // Function to start observing notifications
    private fun startObservingNotifications() {
        viewModelScope.launch {
            connectedPeripheral?.let { peripheral ->
                val characteristic = bleUseCase.getPeripheralCharacteristic(peripheral)
                if (characteristic != null) {
                    val responseData = StringBuilder()
                    bleUseCase.observeNotifications(peripheral)?.collect { dataChunk ->
                        responseData.append(dataChunk)
                        if (responseData.contains("END")) {
                            val fullResponse = responseData.toString().replace("END", "")
                            // Update the order state to notification received
                            _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.NotificationReceived(fullResponse))
                            responseData.clear()
                        }
                    }
                } else {
                    // Update the order state to error
                    _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.Error("Notification characteristic not found"))
                }
            } ?: run {
                // Update the order state to error
                _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.Error("No connected device. Please scan and connect first."))
            }
        }
    }

    // Retry logic for reconnection with delay
    private fun attemptReconnectionWithRetry() {
        viewModelScope.launch {
            // Update the connection state to reconnecting
            _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Reconnecting)

            try {
                retryWithFixedDelay {
                    connectedPeripheral = bleUseCase.scanAndConnect()
                    if (connectedPeripheral != null) {
                        // Update the connection state to connected
                        _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Connected)
                        observePeripheralConnectionState()
                    } else {
                        throw Exception("Failed to reconnect")
                    }
                }
            } catch (e: Exception) {
                // Update the connection state to error
                _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Error(e.message ?: "Reconnection failed"))
            }
        }
    }

    // Retry logic with a fixed delay
    private suspend fun <T> retryWithFixedDelay(
        delayDuration: Long = 2000L,
        block: suspend () -> T
    ): T {
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                Log.e("BLEViewModel", "Reconnection attempt failed: ${e.message}, retrying in $delayDuration ms")
            }
            delay(delayDuration)
        }
    }

    // Observe the peripheral's state and attempt reconnection if disconnected
    @OptIn(FlowPreview::class)
    private fun observePeripheralConnectionState() {
        connectedPeripheral?.let { peripheral ->
            viewModelScope.launch {
                peripheral.state.debounce(300L).collect { state ->
                    handleConnectionState(state)
                }
            }
        }
    }

    // Handle connection state changes
    private fun handleConnectionState(state: State) {
        when (state) {
            is State.Connected -> {
                _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Connected)
                Log.d("BLEViewModel", "Peripheral connected")
            }
            is State.Disconnected -> {
                _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Disconnected)
                Log.d("BLEViewModel", "Peripheral disconnected, attempting to reconnect...")
                attemptReconnectionWithRetry()
            }
            else -> {
                _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Error("Unknown state"))
            }
        }
    }
}