package com.example.oneclickorder.data

import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import com.juul.kable.Characteristic
import com.juul.kable.ConnectionLostException
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.WriteType
import com.juul.kable.peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

class BLEManager @Inject constructor(private val scope: CoroutineScope) {

    private val scanner = Scanner()
    private val serviceUUID = UUID.fromString("00002222-0000-1000-8000-00805f9b34fb")
    private val characteristicUUID = UUID.fromString("00001111-0000-1000-8000-00805f9b34fb")
    private val CCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    companion object {
        private const val DEVICE_NAME = "Galaxy A14"  // Update with correct device name
        private const val TIMEOUT_DURATION = 10000L  // 10 seconds timeout
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

    private suspend fun resetBluetooth() {
        try {
            // Optional: Delay before attempting a reconnection
            delay(1000L)

            // Scan and reconnect (fresh connection)
            scanAndConnect()
            Log.d("BLEManager", "Bluetooth reset: successfully reconnected")
        } catch (e: Exception) {
            Log.e("BLEManager", "Error resetting Bluetooth: ${e.message}")
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

    suspend fun writeOrderDataAndSubscribe(peripheral: Peripheral, orderData: String): String? {
        val services = peripheral.services ?: return null
        val service = services.firstOrNull { it.serviceUuid == serviceUUID }
        val characteristic = service?.characteristics?.firstOrNull { it.characteristicUuid == characteristicUUID }

        return if (characteristic != null) {
            try {
                // Write the order data to the characteristic
                peripheral.write(characteristic, orderData.toByteArray(), WriteType.WithResponse)

                // Enable notifications by writing to the CCD (Client Characteristic Configuration Descriptor)
                val ccd = characteristic.descriptors.firstOrNull { it.descriptorUuid == CCD_UUID }
                if (ccd != null) {
                    Log.d("BLEManager", "CCD descriptor found${ccd.descriptorUuid}")
                    retryOperation {

                        peripheral.write(ccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    }
                } else {
                    Log.e("BLEManager", "CCD descriptor not found")
                    return null
                }

                // Subscribe to notifications and get the acknowledgment
                val acknowledgment = observeNotifications(peripheral, characteristic)
                Log.d("BLEManager", "data from observer : $acknowledgment")
                acknowledgment
            } catch (e: Exception) {
                Log.e("BLEManager", "Failed to write/read data: ${e.message}")

                // Check if GATT error 133 occurred and reset Bluetooth
                if (e.message?.contains("133") == true) {
                    Log.e("BLEManager", "GATT error 133 detected. Resetting Bluetooth...")
                    resetBluetooth()
                }

                null
            }
        } else {
            Log.e("BLEManager", "Characteristic not found")
            null
        }
    }

    private suspend fun observeNotifications(peripheral: Peripheral, characteristic: Characteristic): String {
        val responseData = StringBuilder()
        try {
            peripheral.observe(characteristic).collectLatest { value ->
                val chunk = value.decodeToString()
                // Append the chunk to the full response
                responseData.append(chunk)

                // Check for the end of the message
                if (responseData.contains("END")) {
                    // Remove the delimiter "END" before returning the data
                    val fullResponse = responseData.toString().replace("END", "")
                    Log.d("BLEManager", "Full response received: $fullResponse")
                    return@collectLatest
                }
            }
        } catch (e: Exception) {
            Log.e("BLEManager", "Error observing notifications: ${e.message}")
        }
        return responseData.toString()
    }

}