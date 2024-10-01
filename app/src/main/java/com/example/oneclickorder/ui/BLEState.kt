package com.example.oneclickorder.ui

data class BLEState(
    val connectionState: ConnectionState = ConnectionState.Idle,
    val orderState: OrderState = OrderState.Idle,
    val failedOrders: List<String> = emptyList()  // Add failedOrders here

) {
    // Separate state for connection
    sealed class ConnectionState {
        data object Idle : ConnectionState()

        object Scanning : ConnectionState() {
            override fun toString(): String = "Scanning for devices"
        }

        object Connected : ConnectionState() {
            override fun toString(): String = "Connected to device"
        }

        object Disconnected : ConnectionState() {
            override fun toString(): String = "Disconnected from device"
        }

        data object Reconnecting : ConnectionState()

        data class Error(val error: String) : ConnectionState() {
            override fun toString(): String = "Connection Error: $error"
        }
    }

    // Separate state for order
    sealed class OrderState {
        data object Idle : OrderState()

        data class SendingOrder(val orderInfo: String) : OrderState() {
            override fun toString(): String = "Sending order: $orderInfo"
        }

        data class NotificationReceived(val data: String) : OrderState() {
            override fun toString(): String = "Notification received: $data"
        }

        data class Error(val error: String) : OrderState() {
            override fun toString(): String = "Order Error: $error"
        }
    }
}