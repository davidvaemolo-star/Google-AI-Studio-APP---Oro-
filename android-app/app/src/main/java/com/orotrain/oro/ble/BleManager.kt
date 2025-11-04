package com.orotrain.oro.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.orotrain.oro.model.DeviceStatus
import com.orotrain.oro.model.HapticDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_TIMEOUT_MS = 10000L
        private const val CONNECTION_TIMEOUT_MS = 10000L
        private const val CONNECTION_RETRY_DELAY_MS = 2000L
        private const val MAX_CONNECTION_ATTEMPTS = 3

        // Oro Haptic Service UUIDs (from firmware)
        val ORO_HAPTIC_SERVICE_UUID = UUID.fromString("12340000-1234-5678-1234-56789abcdef0")
        val HAPTIC_CONTROL_UUID = UUID.fromString("12340001-1234-5678-1234-56789abcdef0")
        val ZONE_SETTINGS_UUID = UUID.fromString("12340002-1234-5678-1234-56789abcdef0")
        val DEVICE_STATUS_UUID = UUID.fromString("12340003-1234-5678-1234-56789abcdef0")
        val CONNECTION_STATUS_UUID = UUID.fromString("12340004-1234-5678-1234-56789abcdef0")
        val STROKE_EVENT_UUID = UUID.fromString("12340005-1234-5678-1234-56789abcdef0")
        val CALIBRATION_UUID = UUID.fromString("12340006-1234-5678-1234-56789abcdef0")

        // Standard BLE Battery Service UUIDs
        val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

        // BLE Descriptor UUIDs
        val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Haptic Commands
        const val CMD_STOP: Byte = 0x00
        const val CMD_SINGLE_PULSE: Byte = 0x01
        const val CMD_START_TRAINING: Byte = 0x02
        const val CMD_PAUSE_TRAINING: Byte = 0x03
        const val CMD_RESUME_TRAINING: Byte = 0x04
        const val CMD_COMPLETE_TRAINING: Byte = 0x05
        const val CMD_TEST_PATTERN: Byte = 0x06

        // Haptic Patterns
        const val PATTERN_STRONG_CLICK: Byte = 1
        const val PATTERN_SHARP_CLICK: Byte = 2
        const val PATTERN_SOFT_CLICK: Byte = 3
        const val PATTERN_DOUBLE_CLICK: Byte = 12
        const val PATTERN_TRIPLE_CLICK: Byte = 13
        const val PATTERN_LONG_ALERT: Byte = 24

        // Device States
        const val STATE_IDLE: Byte = 0x00
        const val STATE_READY: Byte = 0x01
        const val STATE_TRAINING: Byte = 0x02
        const val STATE_PAUSED: Byte = 0x03
        const val STATE_COMPLETE: Byte = 0x04
        const val STATE_ERROR: Byte = 0xFF.toByte()

        // Zone Color Codes
        const val ZONE_RECOVERY: Byte = 0x01
        const val ZONE_ENDURANCE: Byte = 0x02
        const val ZONE_TEMPO: Byte = 0x03
        const val ZONE_THRESHOLD: Byte = 0x04
        const val ZONE_VO2_MAX: Byte = 0x05
        const val ZONE_ANAEROBIC: Byte = 0x06

        // Stroke Phases
        const val STROKE_PHASE_CATCH: Byte = 0x01
        const val STROKE_PHASE_DRIVE: Byte = 0x02
        const val STROKE_PHASE_FINISH: Byte = 0x03
        const val STROKE_PHASE_RECOVERY: Byte = 0x04

        // Calibration Commands
        const val CAL_CMD_START: Byte = 0x01
        const val CAL_CMD_STOP: Byte = 0x02
        const val CAL_CMD_SET_THRESHOLD: Byte = 0x03
        const val CAL_CMD_GET_STATUS: Byte = 0x04
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var scanJob: Job? = null

    private val _discoveredDevices = MutableStateFlow<List<HapticDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<HapticDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Stroke event data class
    data class StrokeEvent(
        val deviceId: String,
        val phase: Byte,
        val timestamp: Long,
        val accelMagnitude: Float
    )

    private val _strokeEvents = MutableStateFlow<StrokeEvent?>(null)
    val strokeEvents: StateFlow<StrokeEvent?> = _strokeEvents.asStateFlow()

    private val deviceGattMap = ConcurrentHashMap<String, BluetoothGatt>()
    private val deviceStatusMap = ConcurrentHashMap<String, DeviceStatus>()
    private val connectionRetryMap = ConcurrentHashMap<String, Int>()
    private val connectionTimeoutJobs = ConcurrentHashMap<String, Job>()
    private val manualDisconnects = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val deviceReadyMap = ConcurrentHashMap<String, Boolean>()
    private val commandLocks = ConcurrentHashMap<String, ReentrantLock>()
    private var pacerDeviceId: String? = null  // Track which device is Seat 1 (pacer)

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Missing required Bluetooth permissions")
            return
        }

        if (bluetoothLeScanner == null) {
            Log.w(TAG, "Bluetooth LE scanner not available")
            return
        }

        if (_isScanning.value) {
            Log.w(TAG, "Scan already in progress")
            return
        }

        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner.startScan(null, scanSettings, scanCallback)

        // Auto-stop scan after timeout
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }

        Log.d(TAG, "BLE scan started")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return

        scanJob?.cancel()
        scanJob = null

        if (hasRequiredPermissions() && bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(scanCallback)
        }

        _isScanning.value = false
        Log.d(TAG, "BLE scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val deviceAddress = device.address
        val deviceName = device.name

        // Debug logging - log ALL discovered devices
        val serviceUuids = result.scanRecord?.serviceUuids
        Log.d(TAG, "Scan result: name=$deviceName, address=$deviceAddress, serviceUUIDs=$serviceUuids")

        // Filter for Oro devices by checking for the Oro Haptic Service UUID OR device name
        val hasOroService = serviceUuids?.any { it.uuid == ORO_HAPTIC_SERVICE_UUID } == true
        val hasOroName = deviceName?.startsWith("Oro", ignoreCase = true) == true

        if (!hasOroService && !hasOroName) {
            return // Not an Oro device
        }

        val currentDevices = _discoveredDevices.value
        if (currentDevices.any { it.id == deviceAddress }) {
            return // Device already discovered
        }

        // Use the advertised name if available, otherwise use MAC address
        val displayName = deviceName ?: "Oro-${deviceAddress.takeLast(5).replace(":", "")}"

        val hapticDevice = HapticDevice(
            id = deviceAddress,
            name = displayName,
            status = DeviceStatus.Disconnected
        )

        _discoveredDevices.value = currentDevices + hapticDevice
        Log.d(TAG, "Discovered Oro device: $displayName ($deviceAddress), hasService=$hasOroService, hasName=$hasOroName")
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(deviceId: String, preserveRetryCount: Boolean = false) {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Missing required Bluetooth permissions")
            connectionRetryMap.remove(deviceId)
            updateDeviceStatus(deviceId, DeviceStatus.Disconnected, batteryLevel = null)
            return
        }

        if (deviceStatusMap[deviceId] == DeviceStatus.Connected) {
            Log.d(TAG, "Device already connected: $deviceId")
            return
        }

        if (!preserveRetryCount) {
            connectionRetryMap[deviceId] = 0
        }

        manualDisconnects.remove(deviceId)

        connectionTimeoutJobs.remove(deviceId)?.cancel()

        deviceGattMap.remove(deviceId)?.let { existingGatt ->
            try {
                existingGatt.disconnect()
                existingGatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing existing GATT for $deviceId: ${e.message}")
            }
        }
        deviceReadyMap.remove(deviceId)
        commandLocks.remove(deviceId)

        val device = try {
            bluetoothAdapter?.getRemoteDevice(deviceId)
        } catch (e: IllegalArgumentException) {
            null
        } ?: run {
            Log.e(TAG, "Device not found: $deviceId")
            connectionRetryMap.remove(deviceId)
            updateDeviceStatus(deviceId, DeviceStatus.Disconnected, batteryLevel = null)
            return
        }

        deviceStatusMap[deviceId] = DeviceStatus.Connecting
        updateDeviceStatus(deviceId, DeviceStatus.Connecting)

        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }

        if (gatt == null) {
            Log.e(TAG, "connectGatt returned null for $deviceId")
            handleConnectionFailure(deviceId, immediate = true)
            return
        }

        connectionTimeoutJobs[deviceId] = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (deviceStatusMap[deviceId] == DeviceStatus.Connecting) {
                Log.w(TAG, "Connection timeout for $deviceId, retrying")
                try {
                    gatt.disconnect()
                    gatt.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing timed out GATT for $deviceId: ${e.message}")
                }
                handleConnectionFailure(deviceId)
            }
        }

        val attempt = connectionRetryMap[deviceId]?.plus(1) ?: 1
        Log.d(TAG, "Connecting to device: $deviceId (attempt $attempt)")
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice(deviceId: String) {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Missing required Bluetooth permissions")
            return
        }

        manualDisconnects.add(deviceId)
        connectionTimeoutJobs.remove(deviceId)?.cancel()
        connectionRetryMap.remove(deviceId)
        deviceReadyMap.remove(deviceId)
        commandLocks.remove(deviceId)

        deviceGattMap.remove(deviceId)?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting $deviceId: ${e.message}")
            }
        }

        deviceStatusMap.remove(deviceId)
        updateDeviceStatus(deviceId, DeviceStatus.Disconnected, batteryLevel = null)
        Log.d(TAG, "Disconnected from device: $deviceId")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceId = gatt.device.address

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectionTimeoutJobs.remove(deviceId)?.cancel()

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Connected to GATT server: $deviceId")
                        deviceGattMap[deviceId] = gatt
                        deviceStatusMap[deviceId] = DeviceStatus.Connected
                        connectionRetryMap.remove(deviceId)
                        deviceReadyMap[deviceId] = false
                        commandLocks.putIfAbsent(deviceId, ReentrantLock())

                        if (hasRequiredPermissions()) {
                            try {
                                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            } catch (e: SecurityException) {
                                Log.w(TAG, "Unable to set connection priority for $deviceId: ${e.message}")
                            } catch (e: IllegalArgumentException) {
                                Log.w(TAG, "Connection priority call failed for $deviceId: ${e.message}")
                            }
                            gatt.discoverServices()
                        }

                        // Set default battery level immediately when connected
                        // This will be updated if Battery Service is available
                        updateDeviceStatus(deviceId, DeviceStatus.Connected, batteryLevel = 75)
                    } else {
                        Log.w(TAG, "Failed to connect to $deviceId, status=$status")
                        try {
                            gatt.disconnect()
                            gatt.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error closing failed GATT for $deviceId: ${e.message}")
                        }
                        handleConnectionFailure(deviceId, status, immediate = true)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectionTimeoutJobs.remove(deviceId)?.cancel()

                    val wasManual = manualDisconnects.remove(deviceId)
                    Log.d(TAG, "Disconnected from GATT server: $deviceId (status=$status, manual=$wasManual)")

                    deviceGattMap.remove(deviceId)
                    deviceStatusMap.remove(deviceId)

                    try {
                        gatt.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing GATT for $deviceId: ${e.message}")
                    }

                    if (wasManual) {
                        connectionRetryMap.remove(deviceId)
                        updateDeviceStatus(deviceId, DeviceStatus.Disconnected, batteryLevel = null)
                        return
                    }

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        connectionRetryMap.remove(deviceId)
                        updateDeviceStatus(deviceId, DeviceStatus.Disconnected, batteryLevel = null)
                    } else {
                        Log.w(TAG, "Unexpected disconnect from $deviceId with status=$status")
                        handleConnectionFailure(deviceId, status)
                    }
                    deviceReadyMap.remove(deviceId)
                    commandLocks.remove(deviceId)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val deviceId = gatt.device.address
                Log.d(TAG, "Services discovered for $deviceId")

                if (!hasRequiredPermissions()) return

                val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID)
                if (hapticService == null) {
                    Log.w(TAG, "Oro Haptic service not found on $deviceId, retrying discovery")
                    deviceReadyMap[deviceId] = false
                    try {
                        gatt.discoverServices()
                    } catch (e: Exception) {
                        Log.w(TAG, "Unable to restart service discovery for $deviceId: ${e.message}")
                    }
                    return
                } else {
                    deviceReadyMap[deviceId] = true
                }

                // Enable notifications for Device Status
                val deviceStatusChar = hapticService.getCharacteristic(DEVICE_STATUS_UUID)
                if (deviceStatusChar != null) {
                    gatt.setCharacteristicNotification(deviceStatusChar, true)
                    val descriptor = deviceStatusChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    writeDescriptorCompat(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }

                // Enable stroke event notifications if this is the pacer (Seat 1)
                if (gatt.device.address == pacerDeviceId) {
                    val strokeEventChar = hapticService.getCharacteristic(STROKE_EVENT_UUID)
                    if (strokeEventChar != null) {
                        gatt.setCharacteristicNotification(strokeEventChar, true)
                        val descriptor = strokeEventChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                        writeDescriptorCompat(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        Log.d(TAG, "Enabled stroke event notifications for pacer: ${gatt.device.address}")
                    }
                }

                // Read battery level
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                if (batteryChar != null) {
                    gatt.readCharacteristic(batteryChar)
                    // Enable battery notifications
                    gatt.setCharacteristicNotification(batteryChar, true)
                    val descriptor = batteryChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    writeDescriptorCompat(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    // Fallback: Set default battery level if Battery Service not available
                    // This allows testing when devices don't have the standard battery service
                    Log.w(TAG, "Battery Service not found for ${gatt.device.address}, using default battery level")
                    updateDeviceStatus(gatt.device.address, DeviceStatus.Connected, batteryLevel = 75)
                }
            }
        }

        @Deprecated("Deprecated in Android API")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleCharacteristicRead(gatt, characteristic, value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            handleCharacteristicRead(gatt, characteristic, value)
        }

        @Deprecated("Deprecated in Android API")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleCharacteristicChanged(gatt, characteristic, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(gatt, characteristic, value)
        }
    }

    private fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        when (characteristic.uuid) {
            BATTERY_LEVEL_CHAR_UUID -> {
                val batteryLevel = value.firstOrNull()?.toInt()?.and(0xFF) ?: return
                Log.d(TAG, "Battery level for ${gatt.device.address}: $batteryLevel%")
                updateDeviceStatus(
                    gatt.device.address,
                    DeviceStatus.Connected,
                    batteryLevel
                )
            }
        }
    }

    private fun handleCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        when (characteristic.uuid) {
            STROKE_EVENT_UUID -> {
                if (value.size != 7) return
                val phase = value[0]
                val timestamp = ((value[4].toInt() and 0xFF).toLong() shl 24) or
                        ((value[3].toInt() and 0xFF).toLong() shl 16) or
                        ((value[2].toInt() and 0xFF).toLong() shl 8) or
                        (value[1].toInt() and 0xFF).toLong()
                val accelInt = ((value[6].toInt() and 0xFF) shl 8) or
                        (value[5].toInt() and 0xFF)
                val accelMagnitude = accelInt.toShort() / 100.0f

                val strokeEvent = StrokeEvent(
                    deviceId = gatt.device.address,
                    phase = phase,
                    timestamp = timestamp,
                    accelMagnitude = accelMagnitude
                )

                _strokeEvents.value = strokeEvent

                Log.d(TAG, "Stroke event from ${gatt.device.address}: phase=$phase, accel=$accelMagnitude")

                if (phase == STROKE_PHASE_FINISH && gatt.device.address == pacerDeviceId) {
                    triggerFollowerHaptics()
                }
            }
            DEVICE_STATUS_UUID -> {
                // Parse Device Status notification
                // Expected format: [battery_level, status_flags, ...]
                if (value.isNotEmpty()) {
                    val batteryLevel = value[0].toInt() and 0xFF
                    Log.d(TAG, "Device status update for ${gatt.device.address}: battery=$batteryLevel%")
                    updateDeviceStatus(
                        gatt.device.address,
                        DeviceStatus.Connected,
                        batteryLevel
                    )
                }
            }
            BATTERY_LEVEL_CHAR_UUID -> {
                val batteryLevel = value.firstOrNull()?.toInt()?.and(0xFF) ?: return
                Log.d(TAG, "Battery update for ${gatt.device.address}: $batteryLevel%")
                updateDeviceStatus(
                    gatt.device.address,
                    DeviceStatus.Connected,
                    batteryLevel
                )
            }
        }
    }

    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor?,
        value: ByteArray
    ): Boolean {
        if (descriptor == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int
    ): Boolean {
        characteristic.writeType = writeType
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun handleConnectionFailure(
        deviceId: String,
        status: Int? = null,
        immediate: Boolean = false
    ) {
        connectionTimeoutJobs.remove(deviceId)?.cancel()

        deviceGattMap.remove(deviceId)?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up GATT for $deviceId: ${e.message}")
            }
        }
        deviceReadyMap.remove(deviceId)
        commandLocks.remove(deviceId)

        val retryNumber = connectionRetryMap.getOrDefault(deviceId, 0) + 1

        if (retryNumber > MAX_CONNECTION_ATTEMPTS) {
            connectionRetryMap.remove(deviceId)
            manualDisconnects.remove(deviceId)
            deviceStatusMap.remove(deviceId)
            updateDeviceStatus(deviceId, DeviceStatus.Disconnected, batteryLevel = null)
            Log.e(TAG, "Max connection attempts reached for $deviceId (status=$status)")
            return
        }

        connectionRetryMap[deviceId] = retryNumber

        scope.launch {
            if (!immediate) {
                delay(CONNECTION_RETRY_DELAY_MS * retryNumber)
            }
            Log.d(TAG, "Retrying connection to $deviceId (retry $retryNumber/${MAX_CONNECTION_ATTEMPTS})")
            connectDevice(deviceId, preserveRetryCount = true)
        }
    }

    private fun updateDeviceStatus(
        deviceId: String,
        status: DeviceStatus,
        batteryLevel: Int? = null
    ) {
        _discoveredDevices.value = _discoveredDevices.value.map { device ->
            if (device.id == deviceId) {
                device.copy(
                    status = status,
                    batteryLevel = batteryLevel ?: device.batteryLevel
                )
            } else {
                device
            }
        }
    }

    fun getDeviceStatus(deviceId: String): DeviceStatus {
        return deviceStatusMap[deviceId] ?: DeviceStatus.Disconnected
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) ==
                PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    // Haptic control functions

    @SuppressLint("MissingPermission")
    fun configureTrainingZone(deviceId: String, strokes: Int, sets: Int, spm: Int, zoneColor: Byte = ZONE_ENDURANCE): Boolean {
        if (!hasRequiredPermissions()) return false

        val gatt = deviceGattMap[deviceId] ?: return false
        val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: return false
        val zoneSettingsChar = hapticService.getCharacteristic(ZONE_SETTINGS_UUID) ?: return false

        // Format: [strokes LSB, strokes MSB, sets, SPM LSB, SPM MSB, zone color]
        val data = ByteBuffer.allocate(6).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putShort(strokes.toShort())
            put(sets.toByte())
            putShort(spm.toShort())
            put(zoneColor)
        }.array()

        val result = writeCharacteristicCompat(
            gatt,
            zoneSettingsChar,
            data,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
        Log.d(TAG, "Configure zone for $deviceId: strokes=$strokes, sets=$sets, spm=$spm, result=$result")
        return result
    }

    @SuppressLint("MissingPermission")
    fun startTraining(deviceId: String, intensity: Int = 100, pattern: Byte = PATTERN_STRONG_CLICK): Boolean {
        return sendHapticCommand(deviceId, CMD_START_TRAINING, intensity, pattern)
    }

    @SuppressLint("MissingPermission")
    fun pauseTraining(deviceId: String): Boolean {
        return sendHapticCommand(deviceId, CMD_PAUSE_TRAINING)
    }

    @SuppressLint("MissingPermission")
    fun resumeTraining(deviceId: String): Boolean {
        return sendHapticCommand(deviceId, CMD_RESUME_TRAINING)
    }

    @SuppressLint("MissingPermission")
    fun stopTraining(deviceId: String): Boolean {
        return sendHapticCommand(deviceId, CMD_STOP)
    }

    @SuppressLint("MissingPermission")
    fun testHapticPattern(deviceId: String, pattern: Byte, intensity: Int = 80): Boolean {
        return sendHapticCommand(deviceId, CMD_TEST_PATTERN, intensity, pattern)
    }

    @SuppressLint("MissingPermission")
    private fun sendHapticCommand(
        deviceId: String,
        command: Byte,
        intensity: Int = 100,
        pattern: Byte = PATTERN_STRONG_CLICK
    ): Boolean {
        if (!hasRequiredPermissions()) return false

        val gatt = deviceGattMap[deviceId] ?: run {
            Log.w(TAG, "GATT not available for $deviceId; cannot send command")
            return false
        }

        if (deviceReadyMap[deviceId] != true) {
            Log.w(TAG, "Device $deviceId not ready for haptic command, re-discovering services")
            deviceReadyMap[deviceId] = false
            try {
                gatt.discoverServices()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to trigger service discovery on $deviceId: ${e.message}")
            }
            return false
        }

        val lock = commandLocks.computeIfAbsent(deviceId) { ReentrantLock() }
        lock.lock()
        return try {
            val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: run {
                Log.w(TAG, "Oro Haptic service missing for $deviceId; re-running discovery")
                deviceReadyMap[deviceId] = false
                try {
                    gatt.discoverServices()
                } catch (e: Exception) {
                    Log.w(TAG, "Service rediscovery failed for $deviceId: ${e.message}")
                }
                return false
            }

            val hapticControlChar = hapticService.getCharacteristic(HAPTIC_CONTROL_UUID) ?: run {
                Log.w(TAG, "Haptic control characteristic missing for $deviceId")
                deviceReadyMap[deviceId] = false
                try {
                    gatt.discoverServices()
                } catch (e: Exception) {
                    Log.w(TAG, "Characteristic rediscovery failed for $deviceId: ${e.message}")
                }
                return false
            }

            val supportsNoResponse =
                (hapticControlChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            hapticControlChar.writeType = if (supportsNoResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            // Format: [command, intensity, duration LSB, duration MSB, pattern]
            val data = byteArrayOf(
                command,
                intensity.coerceIn(0, 100).toByte(),
                0, // duration LSB (unused)
                0, // duration MSB (unused)
                pattern
            )

            val result = writeCharacteristicCompat(
                gatt,
                hapticControlChar,
                data,
                hapticControlChar.writeType
            )
            if (!result) {
                Log.w(TAG, "Haptic command write failed for $deviceId (cmd=$command)")
            } else {
                Log.d(
                    TAG,
                    "Sent haptic command to $deviceId: cmd=$command, intensity=$intensity, pattern=$pattern"
                )
            }
            result
        } finally {
            lock.unlock()
        }
    }

    // Stroke detection and pacer management

    fun setPacerDevice(deviceId: String) {
        pacerDeviceId = deviceId
        Log.d(TAG, "Pacer device set to: $deviceId")

        // If already connected, enable stroke notifications
        val gatt = deviceGattMap[deviceId]
        if (gatt != null) {
            enableStrokeNotifications(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableStrokeNotifications(gatt: BluetoothGatt) {
        if (!hasRequiredPermissions()) return

        val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: return
        val strokeEventChar = hapticService.getCharacteristic(STROKE_EVENT_UUID) ?: return

        gatt.setCharacteristicNotification(strokeEventChar, true)
        val descriptor = strokeEventChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        writeDescriptorCompat(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)

        Log.d(TAG, "Enabled stroke event notifications for: ${gatt.device.address}")
    }

    fun enableStrokeDetection(deviceId: String): Boolean {
        // Enable stroke detection by starting training mode (firmware will use IMU)
        return startTraining(deviceId)
    }

    fun disableStrokeDetection(deviceId: String): Boolean {
        return stopTraining(deviceId)
    }

    private fun triggerFollowerHaptics() {
        // Send haptic pulse to all connected devices except the pacer
        var attempted = 0
        var succeeded = 0
        deviceGattMap.keys.forEach { deviceId ->
            if (deviceId != pacerDeviceId && deviceStatusMap[deviceId] == DeviceStatus.Connected) {
                attempted++
                val result = sendHapticCommand(
                    deviceId,
                    CMD_SINGLE_PULSE,
                    intensity = 100,
                    pattern = PATTERN_STRONG_CLICK
                )
                if (result) {
                    succeeded++
                }
            }
        }
        if (attempted == 0) {
            Log.d(TAG, "No follower devices available for haptic relay")
        } else if (succeeded != attempted) {
            Log.w(TAG, "Haptic relay partial success: $succeeded / $attempted followers pulsed")
        }
    }

    // Calibration functions

    @SuppressLint("MissingPermission")
    fun startCalibration(deviceId: String): Boolean {
        if (!hasRequiredPermissions()) return false

        val gatt = deviceGattMap[deviceId] ?: return false
        val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: return false
        val calibrationChar = hapticService.getCharacteristic(CALIBRATION_UUID) ?: return false

        val data = byteArrayOf(CAL_CMD_START)
        val result = writeCharacteristicCompat(
            gatt,
            calibrationChar,
            data,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
        Log.d(TAG, "Start calibration for $deviceId: $result")
        return result
    }

    @SuppressLint("MissingPermission")
    fun stopCalibration(deviceId: String): Boolean {
        if (!hasRequiredPermissions()) return false

        val gatt = deviceGattMap[deviceId] ?: return false
        val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: return false
        val calibrationChar = hapticService.getCharacteristic(CALIBRATION_UUID) ?: return false

        val data = byteArrayOf(CAL_CMD_STOP)
        val result = writeCharacteristicCompat(
            gatt,
            calibrationChar,
            data,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
        Log.d(TAG, "Stop calibration for $deviceId: $result")
        return result
    }

    @SuppressLint("MissingPermission")
    fun setStrokeThreshold(deviceId: String, threshold: Float): Boolean {
        if (!hasRequiredPermissions()) return false

        val gatt = deviceGattMap[deviceId] ?: return false
        val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: return false
        val calibrationChar = hapticService.getCharacteristic(CALIBRATION_UUID) ?: return false

        // Convert float to int16 (multiply by 100)
        val thresholdInt = (threshold * 100).toInt().toShort()
        val data = byteArrayOf(
            CAL_CMD_SET_THRESHOLD,
            (thresholdInt.toInt() and 0xFF).toByte(),
            ((thresholdInt.toInt() shr 8) and 0xFF).toByte()
        )

        val result = writeCharacteristicCompat(
            gatt,
            calibrationChar,
            data,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
        Log.d(TAG, "Set stroke threshold for $deviceId to $threshold: $result")
        return result
    }

    fun cleanup() {
        stopScan()
        connectionTimeoutJobs.values.forEach { it.cancel() }
        connectionTimeoutJobs.clear()
        connectionRetryMap.clear()
        manualDisconnects.clear()
        deviceReadyMap.clear()
        commandLocks.clear()
        deviceGattMap.values.forEach { gatt ->
            try {
                @SuppressLint("MissingPermission")
                if (hasRequiredPermissions()) {
                    gatt.disconnect()
                    gatt.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT: ${e.message}")
            }
        }
        deviceGattMap.clear()
        deviceStatusMap.clear()
        pacerDeviceId = null
    }
}
