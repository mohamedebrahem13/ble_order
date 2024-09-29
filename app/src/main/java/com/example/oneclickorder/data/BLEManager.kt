package com.example.oneclickorder.data

import android.util.Log
import com.juul.kable.Characteristic
import com.juul.kable.ConnectionLostException
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.WriteType
import com.juul.kable.peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

class BLEManager @Inject constructor(private val scope: CoroutineScope) {

    private val scanner = Scanner()
    private val serviceUUID = UUID.fromString("00002222-0000-1000-8000-00805f9b34fb")
    private val characteristicUUID = UUID.fromString("00001111-0000-1000-8000-00805f9b34fb")

    companion object {
        private const val DEVICE_NAME = "Galaxy A14"  // Update with correct device name
        private const val TIMEOUT_DURATION = 10000L  // 10 seconds timeout
    }

    // Retrieve the characteristic from the connected peripheral
    fun getCharacteristic(peripheral: Peripheral): Characteristic? {
        val services = peripheral.services ?: return null
        val service = services.firstOrNull { it.serviceUuid == serviceUUID }
        return service?.characteristics?.firstOrNull { it.characteristicUuid == characteristicUUID }
    }

    // Retry mechanism for connection or other BLE operations
    private suspend fun retryOperation(retries: Int = 3, operation: suspend () -> Unit) {
        repeat(retries) {
            try {
                operation()
                return
            } catch (e: Exception) {
                Log.e("BLEManager", "Operation failed: ${e.message}, retrying...")
            }
        }
    }


    /**
     * Scan for BLE devices and connect to the peripheral with retries and timeout.
     */
    suspend fun scanAndConnect(): Peripheral? {
        return withTimeoutOrNull(TIMEOUT_DURATION) {
            try {
                // Scan for devices by name (Cashier device)
                val advertisement = scanner.advertisements
                    .filter { it.name == DEVICE_NAME }
                    .firstOrNull()

                // Create a Peripheral and connect
                advertisement?.let {
                    val peripheral = scope.peripheral(it)
                    retryOperation { peripheral.connect() }
                    peripheral  // Return the connected peripheral
                }
            } catch (e: ConnectionLostException) {
                Log.e("BLEManager", "Connection lost: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e("BLEManager", "Failed to connect: ${e.message}")
                null
            }
        }
    }
    // Existing writeOrderData and startObservingNotifications methods
    suspend fun writeOrderData(peripheral: Peripheral, orderData: String): Boolean {
        val characteristic = getCharacteristic(peripheral)
        return if (characteristic != null) {
            try {
                peripheral.write(characteristic, orderData.toByteArray(), WriteType.WithResponse)
                Log.d("BLEManager", "Order data written successfully")
                true
            } catch (e: Exception) {
                Log.e("BLEManager", "Failed to write order data: ${e.message}")
                false
            }
        } else {
            Log.e("BLEManager", "Characteristic not found")
            false
        }
    }

    fun startObservingNotifications(peripheral: Peripheral, characteristic: Characteristic): Flow<String> {
        return peripheral.observe(characteristic).map { value ->
            val chunk = value.decodeToString()
            Log.d("BLEManager", "Received chunk: $chunk")
            chunk
        }
    }
}