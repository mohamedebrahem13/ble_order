package com.example.oneclickorder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oneclickorder.data.BLEManager
import com.juul.kable.Peripheral
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BLEViewModel @Inject constructor(
    private val bleManager: BLEManager
) : ViewModel() {

    // Expose connection state to the UI
    private val _connectionState = MutableStateFlow("Idle")
    val connectionState: StateFlow<String> = _connectionState

    // Hold the connected peripheral
    private var connectedPeripheral: Peripheral? = null

    // Function to start the BLE process of scanning and connecting to the Cashier
    fun scanAndConnect() {
        viewModelScope.launch {
            _connectionState.value = "Scanning and connecting..."
            connectedPeripheral = bleManager.scanAndConnect()  // Scan and connect to peripheral
            if (connectedPeripheral != null) {
                _connectionState.value = "Connected to Cashier"
            } else {
                _connectionState.value = "Failed to connect"
            }
        }
    }

    // Function to send order data and subscribe to notifications
    fun sendOrderData(orderData: String) {
        viewModelScope.launch {
            connectedPeripheral?.let { peripheral ->
                _connectionState.value = "Sending order data..."
                val response = bleManager.writeOrderDataAndSubscribe(peripheral, orderData)  // Write data and subscribe to notifications
                _connectionState.value = response ?: "Failed to receive response"
            } ?: run {
                _connectionState.value = "No connected device. Please scan and connect first."
            }
        }
    }
}