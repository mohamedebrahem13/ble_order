package com.example.oneclickorder.domain.state

import com.example.oneclickorder.ui.BLEState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class BLEStateMonad @Inject constructor(private val stateFlow: MutableStateFlow<BLEState>) {

    // Update the connection state
    fun updateConnectionState(newState: BLEState.ConnectionState): BLEState {
        val updatedState = stateFlow.value.copy(connectionState = newState)
        stateFlow.value = updatedState  // Update StateFlow with the new state
        return updatedState
    }

    // Update the order state
    fun updateOrderState(newState: BLEState.OrderState): BLEState {
        val updatedState = stateFlow.value.copy(orderState = newState)
        stateFlow.value = updatedState  // Update StateFlow with the new state
        return updatedState
    }

    // Add a failed order
    fun addFailedOrder(orderData: String): BLEState {
        val updatedFailedOrders = stateFlow.value.failedOrders + orderData
        val updatedState = stateFlow.value.copy(failedOrders = updatedFailedOrders)
        stateFlow.value = updatedState  // Update StateFlow with the new state
        return updatedState
    }

    // Clear all failed orders
    fun clearFailedOrders(): BLEState {
        val updatedState = stateFlow.value.copy(failedOrders = emptyList())
        stateFlow.value = updatedState  // Update StateFlow with the new state
        return updatedState
    }

    // Get the current state as StateFlow
    fun getState(): StateFlow<BLEState> {
        return stateFlow
    }
}