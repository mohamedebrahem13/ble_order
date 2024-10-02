package com.example.oneclickorder.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oneclickorder.domain.BLEUseCase
import com.example.oneclickorder.domain.model.Result
import com.example.oneclickorder.domain.state.BLEStateMonad
import com.example.oneclickorder.ui.command.Command
import com.example.oneclickorder.ui.command.scanAndConnectCommand
import com.example.oneclickorder.ui.command.sendOrderCommand
import com.juul.kable.Peripheral
import com.juul.kable.State
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class BLEViewModel @Inject constructor(
    private val bleUseCase: BLEUseCase,
    private val bleStateMonad: BLEStateMonad
) : ViewModel() {

    val bleState = bleStateMonad.getState()


    private val intentChannel = Channel<BLEIntent>(Channel.UNLIMITED)
    private val orderQueue = mutableListOf<String>() // Keep this for processing orders
    private var connectedPeripheral: Peripheral? = null
    private var isProcessingOrders = false

    init {
        processIntents()
        observePeripheralConnectionState()
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
                    is BLEIntent.ScanAndConnect -> executeCommand {
                        val result = scanAndConnectCommand(bleUseCase, bleStateMonad).invoke()

                        if (result is Result.Success) {
                            connectedPeripheral = result.data  // Store the connected peripheral
                        }
                        observePeripheralConnectionState()  // Observe the connection state
                        startObservingNotifications()       // Start observing notifications
                        result  // Return the result for executeCommand to handle properly
                    }
                    is BLEIntent.SendOrderData -> queueOrderData(intent.orderData)
                }
            }
        }
    }

    private fun <T> executeCommand(command: Command<T>) {
        viewModelScope.launch {
            val result = command() // Invoke the command
            if (result is Result.Failure) {
                Log.e("BLEViewModel", "Command execution failed: ${result.error.message}")
            } else if (result is Result.Success && result.data is Peripheral) {
                connectedPeripheral = result.data  // Store the connected peripheral
                // Observations are handled elsewhere, not here.
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
                // Use the command to send the order and invoke it
                executeCommand {
                    sendOrderCommand(
                        bleUseCase,
                        order,
                        connectedPeripheral,
                        bleStateMonad,
                    ).invoke()
                }
            }
            isProcessingOrders = false
        }
    }
    // Attempt reconnection indefinitely with retry
    private fun attemptReconnectionWithRetry() {
        viewModelScope.launch {
            bleStateMonad.updateConnectionState(BLEState.ConnectionState.Reconnecting)

            try {
                retryWithFixedDelay {
                    val result = bleUseCase.scanAndConnect() // Get the result
                    if (result is Result.Success) {
                        connectedPeripheral = result.data // Assign the peripheral if success
                        if (connectedPeripheral != null) {
                            bleStateMonad.updateConnectionState(BLEState.ConnectionState.Connected)
                            observePeripheralConnectionState() // Start observing connection state
                        } else {
                            throw Exception("Failed to reconnect: Peripheral is null")
                        }
                    } else if (result is Result.Failure) {
                        throw Exception("Failed to reconnect: ${result.error.message}")
                    }
                }
            } catch (e: Exception) {
                bleStateMonad.updateConnectionState(BLEState.ConnectionState.Error(e.message ?: "Reconnection failed"))
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
                            bleStateMonad.updateOrderState(BLEState.OrderState.NotificationReceived(fullResponse))
                            responseData.clear()
                        }
                    }
                } else {
                    bleStateMonad.updateOrderState(BLEState.OrderState.Error("Notification characteristic not found"))
                }
            } ?: run {
                bleStateMonad.updateOrderState(BLEState.OrderState.Error("No connected device. Please scan and connect first."))
            }
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
                if (bleState.value.connectionState != BLEState.ConnectionState.Connected) {
                    bleStateMonad.updateConnectionState(BLEState.ConnectionState.Connected)
                    Log.d("BLEViewModel", "Peripheral connected")
                    retryFailedOrders()  // Retry any failed orders after connection is restored
                }
            }
            is State.Disconnected -> {
                if (bleState.value.connectionState != BLEState.ConnectionState.Disconnected) {
                    bleStateMonad.updateConnectionState(BLEState.ConnectionState.Disconnected)
                    Log.d("BLEViewModel", "Peripheral disconnected, attempting to reconnect...")
                    attemptReconnectionWithRetry()
                }
            }
            else -> {
                bleStateMonad.updateConnectionState(BLEState.ConnectionState.Error("Unknown state"))
            }
        }
    }
    private suspend fun <T> retryWithFixedDelay(
        delayDuration: Long = 2000L,
        block: suspend () -> T
    ): T {
        while (true) {
            try {
                return block()  // Attempt the connection or reconnection
            } catch (e: Exception) {
                Log.e("BLECommand", "Reconnection attempt failed: ${e.message}, retrying in $delayDuration ms")
            }
            delay(delayDuration)  // Wait before retrying
        }
    }

    private fun retryFailedOrders() {
        viewModelScope.launch {
            val failedOrders = bleState.value.failedOrders
            if (failedOrders.isNotEmpty()) {
                for (order in failedOrders) {
                    // Use the command to retry sending the order and invoke it
                    Log.d("BLEViewModel", "Retrying failed order: $order")
                    executeCommand {
                        sendOrderCommand(
                            bleUseCase,
                            order,
                            connectedPeripheral,
                            bleStateMonad
                        ).invoke()
                    }
                }
                bleStateMonad.clearFailedOrders()
            }
        }
    }
}