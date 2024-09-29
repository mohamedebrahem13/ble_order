package com.example.oneclickorder.ui.reciver

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BluetoothStateReceiver(
    private val onBluetoothOff: () -> Unit,
    private val onBluetoothOn: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_OFF -> {
                    // Bluetooth is off, trigger callback
                    onBluetoothOff()
                }
                BluetoothAdapter.STATE_ON -> {
                    // Bluetooth is on, trigger callback
                    onBluetoothOn()
                }
            }
        }
    }
}