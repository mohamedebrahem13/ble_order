package com.example.oneclickorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    // Remember a scroll state
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState), // Make the column scrollable
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top // Changed from Center to allow scroll from top
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
        Button(onClick = {
            viewModel.sendIntent(
                BLEIntent.SendOrderData(
                    "\"Steak1\", \"Fish1\", \"Chicken1\", \"Rice1\", \"Noodles1\",\n" +
                            "\"Coke1\", \"Lemonade1\", \"Taco1\", \"Burrito1\", \"Cake1\" " +
                            "\"Coke2\", \"Lemonade2\", \"Taco2\", \"Burrito2\", \"Cake2\" " +
                            "\"Coke3\", \"Lemonade3\", \"Taco3\", \"Burrito3\", \"Cake3\" " +
                            "\"Coke4\", \"Lemonade4\", \"Taco4\", \"Burrito4\", \"Cake4\" " +
                            "\"Steak2\", \"Fish2\", \"Chicken2\", \"Rice2\", \"Noodles2\",\n" +
                            "\"Coke5\", \"Lemonade5\", \"Taco5\", \"Burrito5\", \"Cake5\" " +
                            "\"Coke6\", \"Lemonade6\", \"Taco6\", \"Burrito6\", \"Cake6\" " +
                            "\"Coke7\", \"Lemonade7\", \"Taco7\", \"Burrito7\", \"Cake7\" " +
                            "\"Coke8\", \"Lemonade8\", \"Taco8\", \"Burrito8\", \"Cake8\" " +
                            "\"Steak3\", \"Fish3\", \"Chicken3\", \"Rice3\", \"Noodles3\",\n" +
                            "\"Coke9\", \"Lemonade9\", \"Taco9\", \"Burrito9\", \"Cake9\" " +
                            "\"Coke10\", \"Lemonade10\", \"Taco10\", \"Burrito10\", \"Cake10\" " +
                            "\"Coke11\", \"Lemonade11\", \"Taco11\", \"Burrito11\", \"Cake11\" " +
                            "\"Coke12\", \"Lemonade12\", \"Taco12\", \"Burrito12\", \"Cake12\" " +
                            "\"Steak4\", \"Fish4\", \"Chicken4\", \"Rice4\", \"Noodles4\",\n" +
                            "\"Coke13\", \"Lemonade13\", \"Taco13\", \"Burrito13\", \"Cake13\" " +
                            "\"Coke14\", \"Lemonade14\", \"Taco14\", \"Burrito14\", \"Cake14\" " +
                            "\"Coke15\", \"Lemonade15\", \"Taco15\", \"Burrito15\", \"Cake15\" " +
                            "\"Coke16\", \"Lemonade16\", \"Taco16\", \"Burrito16\", \"Cake16\" 213131" +
                            "\"Steak5\", \"Fish5\", \"Chicken5\", \"Rice5\", \"Noodles5\",\n" +
                            "\"Coke17\", \"Lemonade17\", \"Taco17\", \"Burrito17\", \"Cake17\" " +
                            "\"Coke18\", \"Lemonade18\", \"Taco18\", \"Burrito18\", \"Cake18\" " +
                            "\"Coke19\", \"Lemonade19\", \"Taco19\", \"Burrito19\", \"Cake19\" " +
                            "\"Coke20\", \"Lemonade20\", \"Taco20\", \"Burrito20\", \"Cake20\" " +
                            "\"Steak6\", \"Fish6\", \"Chicken6\", \"Rice6\", \"Noodles6\",\n" +
                            "\"Coke21\", \"Lemonade21\", \"Taco21\", \"Burrito21\", \"Cake21\" " +
                            "\"Coke22\", \"Lemonade22\", \"Taco22\", \"Burrito22\", \"Cake22\" " +
                            "\"Coke23\", \"Lemonade23\", \"Taco23\", \"Burrito23\", \"Cake23\" " +
                            "\"Coke24\", \"Lemonade24\", \"Taco24\", \"Burrito24\", \"Cake24\" 2124578"
                )
            )
        }) {
            Text(text = "Send Order 1")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button to send the second order data after the connection
        Button(onClick = { viewModel.sendIntent(BLEIntent.SendOrderData("Order #15000")) }) {
            Text(text = "Send Order 2")
        }

        Spacer(modifier = Modifier.height(16.dp))


        Spacer(modifier = Modifier.height(16.dp))

        // Display the list of unsent orders (isSent = false)
        if (bleState.unsentOrders.isNotEmpty()) {
            Text(text = "Unsent Orders:", modifier = Modifier.padding(top = 16.dp))

            Column(modifier = Modifier.padding(8.dp)) {
                bleState.unsentOrders.forEach { unsentOrder ->
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically // Align items in the center vertically
                    ) {
                        // Order ID
                        Text(
                            text = "Order ID: ${unsentOrder.orderId}",
                            modifier = Modifier.weight(1f).padding(end = 16.dp) // Take up available space and add padding
                        )

                        // Button to remove the unsent order by orderId
                        Button(onClick = {
                            viewModel.sendIntent(BLEIntent.DeleteUnsentOrder(unsentOrder.orderId))
                        }) {
                            Text(text = "Remove")
                        }

                    }
                }
            }
        }

    }
}