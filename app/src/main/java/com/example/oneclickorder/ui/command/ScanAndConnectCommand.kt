package com.example.oneclickorder.ui.command

import android.util.Log
import com.example.oneclickorder.domain.BLEUseCase
import com.example.oneclickorder.domain.model.Result
import com.example.oneclickorder.domain.state.BLEStateMonad
import com.example.oneclickorder.ui.BLEState
import com.juul.kable.Peripheral
import kotlinx.coroutines.delay

typealias Command<T> = suspend () -> Result<T>

fun scanAndConnectCommand(
    bleUseCase: BLEUseCase,
    bleStateMonad: BLEStateMonad
): Command<Peripheral?> = {
    try {
        // Set the state to "Reconnecting" first if the connection is being retried
        bleStateMonad.updateConnectionState(BLEState.ConnectionState.Reconnecting)

        // Try to scan and connect with retries
        retryWithFixedDelay {
            // Call the use case to scan and connect
            when (val result = bleUseCase.scanAndConnect()) {
                is Result.Success -> {
                    // Connection successful, update state to Connected
                    bleStateMonad.updateConnectionState(BLEState.ConnectionState.Connected)
                    Result.Success(result.data)
                }
                is Result.Failure -> {
                    Log.e("BLECommand", "Connection failed, attempting reconnection...")
                    throw result.error  // Trigger retry
                }
            }
        }
    } catch (e: Exception) {
        // If an error occurs during the connection, update the state to Error
        bleStateMonad.updateConnectionState(BLEState.ConnectionState.Error(e.message ?: "Unknown error during connection"))
        Result.Failure(e)  // Handle unexpected errors
    }
}

suspend fun <T> retryWithFixedDelay(
    delayDuration: Long = 2000L,
    block: suspend () -> T
): T {
    while (true) {
        try {
            return block()  // Attempt the connection or reconnection
        } catch (e: Exception) {
            Log.e("BLECommand", "Reconnection attempt failed: ${e.message}, retrying in $delayDuration ms")
        }
        delay(delayDuration)  // Wait before retrying
    }
}