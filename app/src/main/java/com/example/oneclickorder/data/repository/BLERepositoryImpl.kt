package com.example.oneclickorder.data.repository

import com.example.oneclickorder.data.BLEManager
import com.example.oneclickorder.data.repository.source.IBLERepository
import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BLERepositoryImpl @Inject constructor(
    private val bleManager: BLEManager,
) : IBLERepository {

    override suspend fun scanAndConnect(): Peripheral? {
        return bleManager.scanAndConnect()
    }

    override suspend fun writeOrderData(peripheral: Peripheral, orderData: String): Boolean {
        return bleManager.writeOrderData(peripheral, orderData)
    }

    override fun startObservingNotifications(peripheral: Peripheral): Flow<String>? {
        val characteristic = bleManager.getCharacteristic(peripheral)
        return characteristic?.let {
            bleManager.startObservingNotifications(peripheral, it)
        }
    }

    override fun getCharacteristic(peripheral: Peripheral): Characteristic? {
        return bleManager.getCharacteristic(peripheral)
    }
}