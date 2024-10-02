package com.example.oneclickorder.ui.command

import com.example.oneclickorder.domain.BLEUseCase
import com.example.oneclickorder.domain.model.Result
import com.example.oneclickorder.domain.state.BLEStateMonad
import com.example.oneclickorder.ui.BLEState
import com.juul.kable.ConnectionLostException
import com.juul.kable.Peripheral


fun sendOrderCommand(
    bleUseCase: BLEUseCase,
    orderData: String,
    peripheral: Peripheral?,
    bleStateMonad: BLEStateMonad
): Command<Boolean> = {
    try {
        peripheral?.let {
            // Update the state to sending order
            bleStateMonad.updateOrderState(BLEState.OrderState.SendingOrder(orderData))

            // Call the use case to send the order data
            when (val result = bleUseCase.sendOrderData(it, orderData)) {
                is Result.Success -> Result.Success(true)
                is Result.Failure -> throw result.error
            }
        } ?: throw Exception("No connected device. Please scan and connect first.")
    } catch (e: ConnectionLostException) {
        requeueFailedOrder(orderData, bleStateMonad)
        disconnectPeripheral(peripheral, bleStateMonad) // Disconnect before reconnecting
        Result.Failure(e)
    } catch (e: Exception) {
        requeueFailedOrder(orderData, bleStateMonad)
        disconnectPeripheral(peripheral, bleStateMonad) // Disconnect before reconnecting
        Result.Failure(e)
    }
}

// Disconnect the peripheral and update the state
private suspend fun disconnectPeripheral(peripheral: Peripheral?, bleStateMonad: BLEStateMonad) {
    peripheral?.let {
        it.disconnect() // Disconnect the peripheral
        bleStateMonad.updateConnectionState(BLEState.ConnectionState.Disconnected) // State updated here only once
    }
}



// Requeue the failed order
private fun requeueFailedOrder(orderData: String, bleStateMonad: BLEStateMonad) {
    bleStateMonad.updateOrderState(BLEState.OrderState.Error(orderData))
    bleStateMonad.addFailedOrder(orderData)
}