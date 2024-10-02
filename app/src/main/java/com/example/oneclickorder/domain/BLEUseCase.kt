package com.example.oneclickorder.domain

import com.example.oneclickorder.data.repository.source.IBLERepository
import com.juul.kable.Peripheral
import kotlinx.coroutines.flow.Flow
import com.example.oneclickorder.domain.model.Result

import javax.inject.Inject

class BLEUseCase @Inject constructor(
    private val repository: IBLERepository
) {

    suspend fun scanAndConnect(): Result<Peripheral?> {
        return try {
            val peripheral = repository.scanAndConnect()
            if (peripheral != null) {
                Result.Success(peripheral)
            } else {
                Result.Failure(Exception("Failed to connect to device"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    suspend fun sendOrderData(peripheral: Peripheral, orderData: String): Result<Boolean> {
        return try {
            val success = repository.writeOrderData(peripheral, orderData)
            if (success) {
                Result.Success(true)
            } else {
                Result.Failure(Exception("Failed to send order data"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    fun observeNotifications(peripheral: Peripheral): Flow<String>? {
        return repository.startObservingNotifications(peripheral)
    }

    fun getPeripheralCharacteristic(peripheral: Peripheral) =
        repository.getCharacteristic(peripheral)
}