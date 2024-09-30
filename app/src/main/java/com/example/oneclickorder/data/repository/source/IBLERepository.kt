package com.example.oneclickorder.data.repository.source

import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import kotlinx.coroutines.flow.Flow

interface IBLERepository {
    suspend fun scanAndConnect(): Peripheral?
    suspend fun writeOrderData(peripheral: Peripheral, orderData: String): Boolean
    fun startObservingNotifications(peripheral: Peripheral): Flow<String>?
    fun getCharacteristic(peripheral: Peripheral): Characteristic?
}