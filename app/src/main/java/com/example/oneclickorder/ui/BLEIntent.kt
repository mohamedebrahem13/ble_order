package com.example.oneclickorder.ui

sealed class BLEIntent {
    data object ScanAndConnect : BLEIntent()
    data class SendOrderData(val orderData: String) : BLEIntent()
    data class DeleteUnsentOrder(val orderId: Int) : BLEIntent()

}