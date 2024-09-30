package com.example.oneclickorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BLEScreen(viewModel: BLEViewModel = hiltViewModel()) {
    // Collect the unified BLE state from the ViewModel
    val bleState by viewModel.bleState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Display current order status based on the state
        Text(text = "Order Status: ${bleState.orderState}")

        Spacer(modifier = Modifier.height(16.dp))

        // Display current connection status based on the state
        Text(text = "Connection Status: ${bleState.connectionState}")

        Spacer(modifier = Modifier.height(16.dp))

        // Button to scan and connect to the BLE device
        Button(onClick = { viewModel.sendIntent(BLEIntent.ScanAndConnect) }) {
            Text(text = "Scan & Connect")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button to send the first order data after the connection
        Button(onClick = { viewModel.sendIntent(BLEIntent.SendOrderData("Order #166588")) }) {
            Text(text = "Send Order 1")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button to send the second order data after the connection
        Button(onClick = { viewModel.sendIntent(BLEIntent.SendOrderData("Order #15000")) }) {
            Text(text = "Send Order 2")
        }
    }
}