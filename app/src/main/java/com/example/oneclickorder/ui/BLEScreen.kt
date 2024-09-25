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
    val connectionState by viewModel.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Display current connection status
        Text(text = "Connection Status: $connectionState")

        Spacer(modifier = Modifier.height(16.dp))

        // Button to scan and connect to the BLE device
        Button(onClick = { viewModel.scanAndConnect() }) {
            Text(text = "Scan & Connect")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button to send the order data after the connection
        Button(onClick = { viewModel.sendOrderData("Order #166588") }) {
            Text(text = "Send Order1")
        }
        // Button to send the order data after the connection
        Button(onClick = { viewModel.sendOrderData("Order #15000") }) {
            Text(text = "Send Order2")
        }
    }
}