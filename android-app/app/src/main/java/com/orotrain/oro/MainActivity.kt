package com.orotrain.oro

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.orotrain.oro.ble.BleManager
import com.orotrain.oro.ui.OroApp
import com.orotrain.oro.ui.theme.OroTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager
    private lateinit var audioManager: com.orotrain.oro.audio.AudioManager
    private lateinit var viewModel: MainViewModel

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required for device scanning", Toast.LENGTH_LONG).show()
        }
    }

    // Bluetooth enable launcher
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth is required for this app", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BLE manager and Audio manager
        bleManager = BleManager(applicationContext)
        audioManager = com.orotrain.oro.audio.AudioManager(applicationContext)

        // Create ViewModel with BleManager and AudioManager
        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(bleManager, audioManager)
        )[MainViewModel::class.java]

        // Check and request permissions
        checkAndRequestPermissions()

        enableEdgeToEdge()
        setContent {
            OroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OroApp(viewModel = viewModel)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // Check if Bluetooth is supported
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        }

        // Check and request runtime permissions
        val requiredPermissions = getRequiredPermissions()
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.cleanup()
        audioManager.cleanup()
    }
}

// ViewModelFactory to inject BleManager and AudioManager
class MainViewModelFactory(
    private val bleManager: BleManager,
    private val audioManager: com.orotrain.oro.audio.AudioManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(bleManager, audioManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

