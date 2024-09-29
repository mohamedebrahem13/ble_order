package com.example.oneclickorder

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.oneclickorder.ui.BLEScreen
import com.example.oneclickorder.ui.reciver.BluetoothStateReceiver
import com.example.oneclickorder.ui.theme.OneClickOrderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bluetoothAdapter: BluetoothAdapter

    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter.isEnabled

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Handle Bluetooth enable result if needed */ }

    // Initialize BluetoothStateReceiver
    private val bluetoothStateReceiver = BluetoothStateReceiver(
        onBluetoothOff = {
            Toast.makeText(this, "Bluetooth turned off, requesting to turn it back on", Toast.LENGTH_SHORT).show()
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        },
        onBluetoothOn = {
            Toast.makeText(this, "Bluetooth is on", Toast.LENGTH_SHORT).show()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register BluetoothStateReceiver
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionsResult(permissions)
        }

        // Request permissions depending on the Android version
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        // Launch the permission request
        permissionLauncher.launch(permissionsToRequest)

        enableEdgeToEdge()
        setContent {
            OneClickOrderTheme {
                BLEScreen()
            }
        }
    }

    // Handle permission results for both Android 12+ and below
    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ specific permission handling
            val canEnableBluetooth = permissions[android.Manifest.permission.BLUETOOTH_CONNECT] == true
            val canScanBluetooth = permissions[android.Manifest.permission.BLUETOOTH_SCAN] == true
            val canAdvertiseBluetooth = permissions[android.Manifest.permission.BLUETOOTH_ADVERTISE] == true
            val canAccessFineLocation = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
            val canAccessCoarseLocation = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true

            // Check if all essential permissions were granted
            if (canEnableBluetooth && canScanBluetooth && canAdvertiseBluetooth && canAccessFineLocation && canAccessCoarseLocation) {
                requestBluetoothEnableIfNecessary()
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth functionality", Toast.LENGTH_LONG).show()
            }
        } else {
            // Android versions below 12 (API 31) permission handling
            val canUseBluetooth = permissions[android.Manifest.permission.BLUETOOTH] == true
            val canUseBluetoothAdmin = permissions[android.Manifest.permission.BLUETOOTH_ADMIN] == true
            val canAccessFineLocation = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
            val canAccessCoarseLocation = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true

            // Check if essential permissions were granted
            if (canUseBluetooth && canUseBluetoothAdmin && canAccessFineLocation && canAccessCoarseLocation) {
                requestBluetoothEnableIfNecessary()
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth functionality", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Request to enable Bluetooth if not already enabled
    private fun requestBluetoothEnableIfNecessary() {
        if (!isBluetoothEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister BluetoothStateReceiver when the activity is destroyed
        unregisterReceiver(bluetoothStateReceiver)
    }
}