package com.example.oneclickorder.domain

import com.example.oneclickorder.data.repository.source.IBLERepository
import com.juul.kable.Peripheral
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BLEUseCase @Inject constructor(
    private val repository: IBLERepository  // Interact with the interface, not the implementation
) {
    suspend fun scanAndConnect(): Peripheral? {
        return repository.scanAndConnect()
    }

    suspend fun sendOrderData(peripheral: Peripheral, orderData: String): Boolean {
        return repository.writeOrderData(peripheral, orderData)
    }

    fun observeNotifications(peripheral: Peripheral): Flow<String>? {
        return repository.startObservingNotifications(peripheral)
    }

    fun getPeripheralCharacteristic(peripheral: Peripheral) =
        repository.getCharacteristic(peripheral)
}