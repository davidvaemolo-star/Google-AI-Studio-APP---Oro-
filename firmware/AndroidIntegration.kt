/**
 * Oro Haptic Paddle - Android BLE Integration Example
 *
 * This file demonstrates how to integrate the Oro firmware BLE protocol
 * into your Android application's BleManager class.
 *
 * Add this code to: app/src/main/java/com/orotrain/oro/ble/BleManager.kt
 *
 * Note: This is example code showing the key integration points.
 * Adapt to your existing BleManager architecture.
 */

package com.orotrain.oro.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

// ============================================================================
// BLE CONSTANTS
// ============================================================================

object BleConstants {
    // Oro Haptic Service UUIDs
    val ORO_HAPTIC_SERVICE_UUID = UUID.fromString("12340000-1234-5678-1234-56789abcdef0")
    val HAPTIC_CONTROL_UUID = UUID.fromString("12340001-1234-5678-1234-56789abcdef0")
    val ZONE_SETTINGS_UUID = UUID.fromString("12340002-1234-5678-1234-56789abcdef0")
    val DEVICE_STATUS_UUID = UUID.fromString("12340003-1234-5678-1234-56789abcdef0")
    val CONNECTION_STATUS_UUID = UUID.fromString("12340004-1234-5678-1234-56789abcdef0")

    // Battery Service UUIDs (Standard)
    val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    // Client Characteristic Configuration Descriptor (for notifications)
    val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

// ============================================================================
// DATA MODELS
// ============================================================================

/**
 * Device state as reported by firmware
 */
enum class DeviceState(val value: Int) {
    IDLE(0x00),
    READY(0x01),
    TRAINING(0x02),
    PAUSED(0x03),
    COMPLETE(0x04),
    ERROR(0xFF);

    companion object {
        fun fromByte(byte: Byte): DeviceState {
            return values().find { it.value == (byte.toInt() and 0xFF) } ?: ERROR
        }
    }
}

/**
 * Haptic commands to send to firmware
 */
enum class HapticCommand(val value: Byte) {
    STOP(0x00),
    SINGLE_PULSE(0x01),
    START_TRAINING(0x02),
    PAUSE_TRAINING(0x03),
    RESUME_TRAINING(0x04),
    COMPLETE_TRAINING(0x05),
    TEST_PATTERN(0x06)
}

/**
 * Haptic patterns (DRV2605L effect library)
 */
enum class HapticPattern(val value: Byte) {
    STRONG_CLICK(1),
    SHARP_CLICK(2),
    SOFT_CLICK(3),
    DOUBLE_CLICK(12),
    TRIPLE_CLICK(13),
    ALERT_750MS(24),
    PULSING(47),
    TRANSITION(51)
}

/**
 * Training zone configuration
 */
data class TrainingZone(
    val strokes: Int,
    val sets: Int,
    val strokesPerMinute: Int,
    val color: Long  // ARGB color
)

/**
 * Device status from firmware
 */
data class DeviceStatus(
    val state: DeviceState,
    val currentStroke: Int,
    val currentSet: Int,
    val batteryLevel: Int
)

// ============================================================================
// BLE MANAGER
// ============================================================================

class BleManager(private val context: Context) {

    private val TAG = "OroBleMgr"

    // BLE components
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null

    // Characteristics
    private var hapticControlChar: BluetoothGattCharacteristic? = null
    private var zoneSettingsChar: BluetoothGattCharacteristic? = null
    private var deviceStatusChar: BluetoothGattCharacteristic? = null
    private var connectionStatusChar: BluetoothGattCharacteristic? = null
    private var batteryLevelChar: BluetoothGattCharacteristic? = null

    // State flows
    private val _deviceState = MutableStateFlow<DeviceState>(DeviceState.IDLE)
    val deviceState: StateFlow<DeviceState> = _deviceState

    private val _trainingProgress = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0)) // (stroke, set)
    val trainingProgress: StateFlow<Pair<Int, Int>> = _trainingProgress

    private val _batteryLevel = MutableStateFlow<Int>(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    private val _isConnected = MutableStateFlow<Boolean>(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    // ============================================================================
    // SCANNING
    // ============================================================================

    /**
     * Start scanning for Oro devices
     */
    fun startScan() {
        Log.d(TAG, "Starting BLE scan for Oro devices")

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.ORO_HAPTIC_SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Scan failed - missing permissions", e)
        }
    }

    /**
     * Stop BLE scan
     */
    fun stopScan() {
        try {
            bluetoothLeScanner.stopScan(scanCallback)
            Log.d(TAG, "BLE scan stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Stop scan failed", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = try {
                device.name ?: "Unknown"
            } catch (e: SecurityException) {
                "Unknown"
            }

            if (deviceName.startsWith("Oro-")) {
                Log.d(TAG, "Found Oro device: $deviceName (${device.address})")

                // Add to discovered devices if not already present
                val currentDevices = _discoveredDevices.value.toMutableList()
                if (!currentDevices.any { it.address == device.address }) {
                    currentDevices.add(device)
                    _discoveredDevices.value = currentDevices
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }

    // ============================================================================
    // CONNECTION
    // ============================================================================

    /**
     * Connect to Oro device
     */
    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to device: ${device.address}")

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Connection failed - missing permissions", e)
        }
    }

    /**
     * Disconnect from current device
     */
    fun disconnect() {
        bluetoothGatt?.let {
            try {
                it.disconnect()
                it.close()
            } catch (e: SecurityException) {
                Log.e(TAG, "Disconnect failed", e)
            }
        }
        bluetoothGatt = null
        _isConnected.value = false
        Log.d(TAG, "Disconnected")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _isConnected.value = true
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Service discovery failed", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _isConnected.value = false
                    _deviceState.value = DeviceState.IDLE
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")

                // Get Oro Haptic Service characteristics
                val hapticService = gatt.getService(BleConstants.ORO_HAPTIC_SERVICE_UUID)
                if (hapticService != null) {
                    hapticControlChar = hapticService.getCharacteristic(BleConstants.HAPTIC_CONTROL_UUID)
                    zoneSettingsChar = hapticService.getCharacteristic(BleConstants.ZONE_SETTINGS_UUID)
                    deviceStatusChar = hapticService.getCharacteristic(BleConstants.DEVICE_STATUS_UUID)
                    connectionStatusChar = hapticService.getCharacteristic(BleConstants.CONNECTION_STATUS_UUID)

                    // Enable notifications on device status
                    deviceStatusChar?.let { enableNotifications(gatt, it) }

                    Log.d(TAG, "Oro Haptic Service characteristics configured")
                } else {
                    Log.e(TAG, "Oro Haptic Service not found!")
                }

                // Get Battery Service characteristic
                val batteryService = gatt.getService(BleConstants.BATTERY_SERVICE_UUID)
                if (batteryService != null) {
                    batteryLevelChar = batteryService.getCharacteristic(BleConstants.BATTERY_LEVEL_UUID)
                    batteryLevelChar?.let { enableNotifications(gatt, it) }
                    Log.d(TAG, "Battery Service characteristic configured")
                }

                // Read initial battery level
                batteryLevelChar?.let {
                    try {
                        gatt.readCharacteristic(it)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Read battery failed", e)
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicValue(characteristic, value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicValue(characteristic, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "Characteristic write failed: ${characteristic.uuid}, status: $status")
            }
        }
    }

    /**
     * Enable notifications on a characteristic
     */
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG)
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)

            Log.d(TAG, "Enabled notifications for: ${characteristic.uuid}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Enable notifications failed", e)
        }
    }

    /**
     * Handle characteristic value updates
     */
    private fun handleCharacteristicValue(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        when (characteristic.uuid) {
            BleConstants.DEVICE_STATUS_UUID -> {
                // Parse: [state(1)][stroke_lsb(1)][stroke_msb(1)][set(1)][battery(1)]
                if (value.size >= 5) {
                    val state = DeviceState.fromByte(value[0])
                    val currentStroke = (value[1].toInt() and 0xFF) or
                                       ((value[2].toInt() and 0xFF) shl 8)
                    val currentSet = value[3].toInt() and 0xFF
                    val battery = value[4].toInt() and 0xFF

                    _deviceState.value = state
                    _trainingProgress.value = Pair(currentStroke, currentSet)
                    _batteryLevel.value = battery

                    Log.d(TAG, "Device status: state=$state, stroke=$currentStroke, set=$currentSet, battery=$battery%")
                }
            }

            BleConstants.BATTERY_LEVEL_UUID -> {
                // Parse: [battery_percentage(1)]
                if (value.isNotEmpty()) {
                    val battery = value[0].toInt() and 0xFF
                    _batteryLevel.value = battery
                    Log.d(TAG, "Battery level: $battery%")
                }
            }
        }
    }

    // ============================================================================
    // COMMANDS
    // ============================================================================

    /**
     * Send haptic command to device
     */
    fun sendHapticCommand(
        command: HapticCommand,
        intensity: Int = 100,
        pattern: HapticPattern = HapticPattern.STRONG_CLICK
    ) {
        hapticControlChar?.let { char ->
            // Format: [command(1)][intensity(1)][duration_lsb(1)][duration_msb(1)][pattern(1)]
            val data = byteArrayOf(
                command.value,
                intensity.toByte(),
                0,  // Duration LSB (not used)
                0,  // Duration MSB (not used)
                pattern.value
            )

            char.value = data
            try {
                bluetoothGatt?.writeCharacteristic(char)
                Log.d(TAG, "Sent haptic command: $command, intensity: $intensity, pattern: $pattern")
            } catch (e: SecurityException) {
                Log.e(TAG, "Send command failed", e)
            }
        } ?: Log.e(TAG, "Haptic control characteristic not available")
    }

    /**
     * Configure training zone
     */
    fun configureZone(zone: TrainingZone) {
        zoneSettingsChar?.let { char ->
            // Format: [strokes_lsb(1)][strokes_msb(1)][sets(1)][spm_lsb(1)][spm_msb(1)][color(1)]
            val colorCode = getZoneColorCode(zone.color)

            val data = ByteBuffer.allocate(6).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                putShort(zone.strokes.toShort())
                put(zone.sets.toByte())
                putShort(zone.strokesPerMinute.toShort())
                put(colorCode)
            }.array()

            char.value = data
            try {
                bluetoothGatt?.writeCharacteristic(char)
                Log.d(TAG, "Configured zone: strokes=${zone.strokes}, sets=${zone.sets}, spm=${zone.strokesPerMinute}")
            } catch (e: SecurityException) {
                Log.e(TAG, "Configure zone failed", e)
            }
        } ?: Log.e(TAG, "Zone settings characteristic not available")
    }

    /**
     * Start training session
     */
    fun startTraining() {
        sendHapticCommand(HapticCommand.START_TRAINING)
    }

    /**
     * Pause training
     */
    fun pauseTraining() {
        sendHapticCommand(HapticCommand.PAUSE_TRAINING)
    }

    /**
     * Resume training
     */
    fun resumeTraining() {
        sendHapticCommand(HapticCommand.RESUME_TRAINING)
    }

    /**
     * Stop training
     */
    fun stopTraining() {
        sendHapticCommand(HapticCommand.STOP)
    }

    /**
     * Test haptic pattern
     */
    fun testHaptic(pattern: HapticPattern = HapticPattern.STRONG_CLICK, intensity: Int = 100) {
        sendHapticCommand(HapticCommand.TEST_PATTERN, intensity, pattern)
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    /**
     * Map Android ARGB color to firmware zone color code
     */
    private fun getZoneColorCode(color: Long): Byte {
        return when (color) {
            0xFF64B5F6 -> 0x01  // Recovery - Light Blue
            0xFF81C784 -> 0x02  // Endurance - Green
            0xFFFFD54F -> 0x03  // Tempo - Yellow
            0xFFFFB74D -> 0x04  // Threshold - Orange
            0xFFE57373 -> 0x05  // VO2 Max - Red
            0xFFF44336 -> 0x06  // Anaerobic - Dark Red
            else -> 0x02  // Default to Endurance
        }
    }
}

// ============================================================================
// USAGE EXAMPLE
// ============================================================================

/*
class TrainingViewModel(private val bleManager: BleManager) : ViewModel() {

    init {
        // Observe device state
        viewModelScope.launch {
            bleManager.deviceState.collect { state ->
                when (state) {
                    DeviceState.TRAINING -> {
                        // Update UI to show training in progress
                    }
                    DeviceState.COMPLETE -> {
                        // Show completion message
                    }
                    else -> {
                        // Handle other states
                    }
                }
            }
        }

        // Observe training progress
        viewModelScope.launch {
            bleManager.trainingProgress.collect { (stroke, set) ->
                // Update progress UI
                updateProgress(stroke, set)
            }
        }

        // Observe battery level
        viewModelScope.launch {
            bleManager.batteryLevel.collect { level ->
                // Update battery indicator
                updateBattery(level)
            }
        }
    }

    fun onConnectToDevice(device: BluetoothDevice) {
        bleManager.connect(device)
    }

    fun onConfigureZone(strokes: Int, sets: Int, spm: Int, color: Long) {
        val zone = TrainingZone(strokes, sets, spm, color)
        bleManager.configureZone(zone)
    }

    fun onStartTraining() {
        bleManager.startTraining()
    }

    fun onPauseTraining() {
        bleManager.pauseTraining()
    }

    fun onStopTraining() {
        bleManager.stopTraining()
    }

    fun onTestHaptic() {
        bleManager.testHaptic(HapticPattern.DOUBLE_CLICK, 80)
    }
}
*/
