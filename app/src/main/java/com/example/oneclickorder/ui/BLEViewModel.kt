package com.example.oneclickorder.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oneclickorder.domain.BLEUseCase
import com.example.oneclickorder.domain.LocalOrderUseCase
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
    private val bleUseCase: BLEUseCase,
    private val localOrderUseCase: LocalOrderUseCase  // Inject LocalOrderUseCase

) : ViewModel() {

    private val _bleState = MutableStateFlow(BLEState())
    val bleState: StateFlow<BLEState> = _bleState

    private val intentChannel = Channel<BLEIntent>(Channel.UNLIMITED)
    private val orderQueue = mutableListOf<String>() // Keep this for processing orders
    private var connectedPeripheral: Peripheral? = null
    private var isProcessingOrders = false

    init {
        processIntents()
        fetchUnsentOrders()
    }
    /**
     * Fetch unsent orders and update the state.
     */
    private fun fetchUnsentOrders() {
        viewModelScope.launch {
            localOrderUseCase.getUnsentOrders().collect { orders ->
                _bleState.value = _bleState.value.copy(unsentOrders = orders)
            }
        }
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
                    is BLEIntent.DeleteUnsentOrder -> removeUnsentOrder(intent.orderId)  // Handle delete order intent

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
                // Save the order in Room before attempting to send it
                val currentTime = System.currentTimeMillis().toString()
                val orderId = localOrderUseCase.saveOrder(orderData, currentTime)  // Save order to Room and get ID

                val orderWithId = "$orderId|$orderData"  // Prepend orderId to the orderData
                connectedPeripheral?.let { peripheral ->
                    _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.SendingOrder(orderWithId))
                    val success = bleUseCase.sendOrderData(peripheral, orderWithId)
                    if (success) {
                        startObservingNotifications()
                        localOrderUseCase.updateOrderStatus(orderId.toInt(), true)  // Update isSent to true

                    } else {
                        throw ConnectionLostException("Failed to send order data")
                    }
                } ?: throw NotReadyException("No connected device. Please scan and connect first.")
            } catch (e: ConnectionLostException) {
                _bleState.value = _bleState.value.copy(
                    orderState = BLEState.OrderState.Error(e.message ?: "Connection lost while sending data")
                )
                // Force disconnect and reconnect
                disconnectAndReconnect()
            } catch (e: NotReadyException) {
                _bleState.value = _bleState.value.copy(
                    orderState = BLEState.OrderState.Error(e.message ?: "Peripheral not ready for data transmission")
                )
                // Force disconnect and reconnect
                disconnectAndReconnect()
            } catch (e: Exception) {
                _bleState.value = _bleState.value.copy(
                    orderState = BLEState.OrderState.Error(e.message ?: "Unknown error occurred while sending data")
                )
                Log.e("BLEViewModel", "Error sending order data: ${e.message}")
                // Force disconnect and reconnect
                disconnectAndReconnect()
            }
        }
    }
    // Method to send unsent orders without saving them again
    private fun sendUnsentOrder(orderId: Int, orderData: String) {
        viewModelScope.launch {
            try {
                connectedPeripheral?.let { peripheral ->
                    // Concatenate orderId before the orderData
                    val orderWithId = "$orderId|$orderData"

                    _bleState.value = _bleState.value.copy(orderState = BLEState.OrderState.SendingOrder(orderWithId))

                    // Send the order with the concatenated orderId and orderData
                    val success = bleUseCase.sendOrderData(peripheral, orderWithId)
                    if (success) {
                        startObservingNotifications()
                        localOrderUseCase.updateOrderStatus(orderId, true)  // Mark the order as sent in the database
                    } else {
                        throw ConnectionLostException("Failed to send unsent order")
                    }
                } ?: throw NotReadyException("No connected device. Please scan and connect first.")
            } catch (e: ConnectionLostException) {
                _bleState.value = _bleState.value.copy(
                    orderState = BLEState.OrderState.Error(e.message ?: "Connection lost while sending unsent order")
                )
                disconnectAndReconnect()
            } catch (e: NotReadyException) {
                _bleState.value = _bleState.value.copy(
                    orderState = BLEState.OrderState.Error(e.message ?: "Peripheral not ready for transmission")
                )
                disconnectAndReconnect()
            } catch (e: Exception) {
                _bleState.value = _bleState.value.copy(
                    orderState = BLEState.OrderState.Error(e.message ?: "Unknown error occurred while sending unsent order")
                )
                Log.e("BLEViewModel", "Error sending unsent order: ${e.message}")
                disconnectAndReconnect()
            }
        }
    }
    private fun removeUnsentOrder(orderId: Int) {
        viewModelScope.launch {
            try {
                localOrderUseCase.deleteOrder(orderId)
            } catch (e: Exception) {
                Log.e("BLEViewModel", "Error deleting order: ${e.message}")
            }
        }
    }

    private fun disconnectAndReconnect() {
        viewModelScope.launch {
            connectedPeripheral?.let {
                Log.d("BLEViewModel", "Forcefully disconnecting peripheral")
                it.disconnect()  // Forcefully disconnect the peripheral
                _bleState.value = _bleState.value.copy(connectionState = BLEState.ConnectionState.Disconnected)
            }

            // Wait a moment to ensure the disconnection happens
            delay(1000)

        }
    }



    // Automatically retry failed orders when the connection is re-established
    private fun retryFailedOrders() {
        viewModelScope.launch {
            val unsentOrders = _bleState.value.unsentOrders
            if (unsentOrders.isNotEmpty()) {
                for (order in unsentOrders) {
                    sendUnsentOrder(order.orderId, order.orderString)
                }
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