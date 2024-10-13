package com.example.oneclickorder.data

import android.util.Log
import com.juul.kable.AndroidPeripheral
import com.juul.kable.Characteristic
import com.juul.kable.ConnectionLostException
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.WriteType
import com.juul.kable.peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
        private const val REQUESTED_MTU = 512  // Adjust the requested MTU size
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
                    retryOperation {
                        peripheral.connect()

                        // MTU Request for Android Peripheral
                        if (peripheral is AndroidPeripheral) {
                            val mtuSize = peripheral.requestMtu(REQUESTED_MTU)
                            Log.d("BLEManager", "Requested MTU size: $REQUESTED_MTU, Negotiated MTU size: $mtuSize")
                        }

                        // After connecting and negotiating MTU, send small data first
                        val characteristic = getCharacteristic(peripheral)
                        if (characteristic != null) {
                            // Small data packet (e.g., "ping" message)
                            val smallData = "PING"+"END"
                            peripheral.write(characteristic, smallData.toByteArray(Charsets.UTF_8), WriteType.WithResponse)
                            Log.d("BLEManager", "Small data packet sent to stabilize connection")
                        }
                    }

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
        val orderWithEnd = orderData + "END"  // Append "END" to all orders, small or large
        val orderSize = calculateOrderSizeInBytes(orderWithEnd)
        Log.d("BLEManager", "Order size in bytes: $orderSize")

        val characteristic = getCharacteristic(peripheral)
        if (characteristic == null) {
            Log.e("BLEManager", "Characteristic not found")
            return false
        }

        // Calculate the maximum payload size based on the negotiated MTU
        val maxPayloadSize = REQUESTED_MTU - 3  // Subtract 3 bytes for BLE overhead
        val dataBytes = orderWithEnd.toByteArray(Charsets.UTF_8)  // Convert orderData with "END" appended to bytes

        try {
            // Check if the order data fits within a single packet
            if (dataBytes.size <= maxPayloadSize) {
                // Send the small order with "END" in a single packet
                peripheral.write(characteristic, dataBytes, WriteType.WithResponse)
                Log.d("BLEManager", "Small order with 'END' written successfully")
            } else {
                // Chunk the data if it exceeds the MTU size
                val chunks = dataBytes.toList().chunked(maxPayloadSize)
                for (chunk in chunks) {
                    peripheral.write(characteristic, chunk.toByteArray(), WriteType.WithResponse)
                    Log.d("BLEManager", "Chunk written successfully")
                    delay(100)  // Adjust delay based on performance and server response time
                }
                Log.d("BLEManager", "All chunks written successfully")
            }
            return true
        } catch (e: Exception) {
            Log.e("BLEManager", "Failed to write order data or chunk: ${e.message}")
            return false
        }
    }
    private fun calculateOrderSizeInBytes(orderData: String): Int {
        // Convert the order data to bytes and add "END" to mark the end of the order
        val dataBytes = (orderData + "END").toByteArray(Charsets.UTF_8)

        // Return the size of the byte array, which represents the size of the order in bytes
        return dataBytes.size
    }

    fun startObservingNotifications(peripheral: Peripheral, characteristic: Characteristic): Flow<String> {
        return peripheral.observe(characteristic).map { value ->
            val chunk = value.decodeToString()
            Log.d("BLEManager", "Received chunk: $chunk")
            chunk
        }
    }
}