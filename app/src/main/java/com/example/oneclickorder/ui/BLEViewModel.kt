package com.example.oneclickorder.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oneclickorder.domain.BLEUseCase
import com.juul.kable.ConnectionLostException
import com.juul.kable.NotReadyException
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

    private val _bleState = MutableStateFlow(BLEState())
    val bleState: StateFlow<BLEState> = _bleState

    private val intentChannel = Channel<BLEIntent>(Channel.UNLIMITED)
    private val orderQueue = mutableListOf<String>() // Keep this for processing orders
    private var connectedPeripheral: Peripheral? = null
    private var isProcessingOrders = false

    init {
        processIntents()
    }

    fun sendIntent(intent: BLEIntent) {
        viewModelScope.launch {
            intentChannel.send(intent)
        }
    }

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

    private fun queueOrderData(orderData: String) {
        viewModelScope.launch {
            orderQueue.add(orderData)
            processOrderQueue()
        }
    }

    private fun processOrderQueue() {
        if (isProcessingOrders) return

        isProcessingOrders = true

        viewModelScope.launch {
            while (orderQueue.isNotEmpty()) {
                val order = orderQueue.removeAt(0)
                sendOrderData(order)
            }
            isProcessingOrders = false
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

    private fun sendOrderData(orderData: String) {
        viewModelScope.launch {
            try {
                connectedPeripheral?.let { peripheral ->
                    _bleState.value =
                        _bleState.value.copy(orderState = BLEState.OrderState.SendingOrder(orderData))

                    val success = bleUseCase.sendOrderData(peripheral, orderData)
                    if (success) {
                        startObservingNotifications()
                    } else {
                        throw ConnectionLostException("Failed to send order data")
                    }
                } ?: throw NotReadyException("No connected device. Please scan and connect first.")
            } catch (e: ConnectionLostException) {
                requeueFailedOrder(orderData) // Now this updates state
                _bleState.value = _bleState.value.copy(
                    orderState = BLEState.OrderState.Error(e.message ?: "Connection lost while sending data")
                )
            } catch (e: NotReadyException) {
                requeueFailedOrder(orderData) // Now this updates state
                _bleState.value = _bleState.value.copy(
                    orderState = BLEState.OrderState.Error(e.message ?: "Peripheral not ready for data transmission")
                )
            } catch (e: Exception) {
                requeueFailedOrder(orderData) // Now this updates state
                _bleState.value = _bleState.value.copy(
                    orderState = BLEState.OrderState.Error(e.message ?: "Unknown error occurred while sending data")
                )
                Log.e("BLEViewModel", "Error sending order data: ${e.message}")
            }
        }
    }

    // Requeue failed orders by updating the state
    private fun requeueFailedOrder(orderData: String) {
        val updatedFailedOrders = _bleState.value.failedOrders + orderData
        _bleState.value = _bleState.value.copy(failedOrders = updatedFailedOrders) // Update failedOrders in state
    }

    // Automatically retry failed orders when the connection is re-established
    private fun retryFailedOrders() {
        viewModelScope.launch {
            val failedOrders = _bleState.value.failedOrders
            if (failedOrders.isNotEmpty()) {
                for (order in failedOrders) {
                    sendOrderData(order)
                }
                // Clear the failed orders after retry
                _bleState.value = _bleState.value.copy(failedOrders = emptyList())
            }
        }
    }

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
                            _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.NotificationReceived(fullResponse))
                            responseData.clear()
                        }
                    }
                } else {
                    _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.Error("Notification characteristic not found"))
                }
            } ?: run {
                _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.Error("No connected device. Please scan and connect first."))
            }
        }
    }

    private fun attemptReconnectionWithRetry() {
        viewModelScope.launch {
            _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Reconnecting)

            try {
                retryWithFixedDelay {
                    connectedPeripheral = bleUseCase.scanAndConnect()
                    if (connectedPeripheral != null) {
                        _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Connected)
                        observePeripheralConnectionState()
                    } else {
                        throw Exception("Failed to reconnect")
                    }
                }
            } catch (e: Exception) {
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

    private fun handleConnectionState(state: State) {
        when (state) {
            is State.Connected -> {
                _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Connected)
                retryFailedOrders()
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