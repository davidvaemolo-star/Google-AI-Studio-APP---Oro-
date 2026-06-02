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
        val AUDIO_CONTROL_UUID = UUID.fromString("12340007-1234-5678-1234-56789abcdef0")
        val FSR_DATA_UUID = UUID.fromString("12340008-1234-5678-1234-56789abcdef0")
        // 12340009 reserved (LED control removed — ADR-0009)
        val ROLLCALL_CONTROL_UUID = UUID.fromString("1234000A-1234-5678-1234-56789abcdef0")

        /** Target lead time (ms) the phone aims to have all devices speak the roll-call together. */
        const val ROLLCALL_PLAY_TARGET_MS = 300

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
        const val PATTERN_ALERT_750MS: Byte = PATTERN_LONG_ALERT
        const val PATTERN_TRANSITION: Byte = 51

        // Audio Events
        // Tones (firmware-generated)
        const val AUDIO_POWER_ON: Byte            = 0x01
        // 0x02 was the Countdown (AUDIO_SESSION_START_BEEP) — RETIRED (ADR-0017). The session now
        // begins on the Pacer's first Catch, announced by AUDIO_STANDBY. ID reserved; do not reuse.
        const val AUDIO_SET_CHANGEOVER_BEEP: Byte = 0x03

        // Zone voice prompts
        const val AUDIO_LAST_SET: Byte            = 0x04
        const val AUDIO_NEXT_SET_LOW: Byte        = 0x05
        const val AUDIO_NEXT_SET_MEDIUM: Byte     = 0x06
        const val AUDIO_NEXT_SET_HIGH: Byte       = 0x07

        // Session start/end voice prompts (ADR-0017)
        const val AUDIO_STANDBY: Byte             = 0x14  // "stand by" — armed, awaiting Pacer's first Catch
        const val AUDIO_SESSION_COMPLETE: Byte    = 0x15  // "session complete" — at session end
        const val AUDIO_STANDBY_FOR_RESULTS: Byte = 0x16  // "stand by for results" — before the Crew Roll-Call

        // Session summary (RETIRED — ADR-0016, replaced by the Crew Roll-Call; IDs reserved, do not reuse)
        // Session summary: Poor sync
        const val AUDIO_SUMMARY_POOR_LIGHT: Byte      = 0x08
        const val AUDIO_SUMMARY_POOR_MODERATE: Byte   = 0x09
        const val AUDIO_SUMMARY_POOR_STRONG: Byte     = 0x0A
        const val AUDIO_SUMMARY_POOR_MAXIMUM: Byte    = 0x0B

        // Session summary: Good sync
        const val AUDIO_SUMMARY_GOOD_LIGHT: Byte      = 0x0C
        const val AUDIO_SUMMARY_GOOD_MODERATE: Byte   = 0x0D
        const val AUDIO_SUMMARY_GOOD_STRONG: Byte     = 0x0E
        const val AUDIO_SUMMARY_GOOD_MAXIMUM: Byte    = 0x0F

        // Session summary: Excellent sync
        const val AUDIO_SUMMARY_EXCELLENT_LIGHT: Byte      = 0x10
        const val AUDIO_SUMMARY_EXCELLENT_MODERATE: Byte   = 0x11
        const val AUDIO_SUMMARY_EXCELLENT_STRONG: Byte     = 0x12
        const val AUDIO_SUMMARY_EXCELLENT_MAXIMUM: Byte    = 0x13

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

    // Stroke event data class (enriched 16-byte format, backward compatible with 7-byte)
    data class StrokeEvent(
        val deviceId: String,
        val phase: Byte,
        val timestamp: Long,
        val accelMagnitude: Float,
        val peakAccel: Float = 0f,        // Peak acceleration during this stroke (g)
        val minAccel: Float = 0f,         // Min acceleration during recovery (g)
        val phaseDurationMs: Int = 0,     // Duration of current phase (ms)
        val topHandPressurePercent: Int = 0,     // FSR reading at this moment (0-100)
        val strokeFlags: Int = 0          // Bit flags: bit0=fsr_threshold_triggered
    )

    data class BroadcastResult(
        val attempted: Int,
        val succeeded: Int
    )

    data class CalibrationUpdate(
        val deviceId: String,
        val strokeCount: Int,
        val maxAccel: Float,
        val minAccel: Float,
        val suggestedThreshold: Float,
        val isComplete: Boolean,
        val isCapturingBaseline: Boolean = false,  // firmware is in the resting-baseline (tare) window; hold still (ADR-0012)
        val baselineRejected: Boolean = false       // baseline rejected because the paddle wasn't still; retry (ADR-0012)
    )

    private val _strokeEvents = MutableStateFlow<StrokeEvent?>(null)
    val strokeEvents: StateFlow<StrokeEvent?> = _strokeEvents.asStateFlow()

    private val _calibrationUpdates = MutableStateFlow<CalibrationUpdate?>(null)
    val calibrationUpdates: StateFlow<CalibrationUpdate?> = _calibrationUpdates.asStateFlow()

    data class FsrUpdate(val deviceId: String, val forcePercent: Int, val rawAdc: Int, val topHandPressureThresholdTriggered: Boolean)
    private val _fsrUpdates = MutableStateFlow<FsrUpdate?>(null)
    val fsrUpdates: StateFlow<FsrUpdate?> = _fsrUpdates.asStateFlow()

    // Raw firmware DeviceState byte (STATE_IDLE/READY/CALIBRATING/...) reported via the Device
    // Status characteristic. The ViewModel uses this to resync calibration state after a reconnect,
    // so a Bluetooth blip no longer wipes a completed calibration. See ADR-0003 / device-state sync.
    data class DeviceStateUpdate(val deviceId: String, val firmwareState: Int)
    private val _deviceStateUpdates = MutableStateFlow<DeviceStateUpdate?>(null)
    val deviceStateUpdates: StateFlow<DeviceStateUpdate?> = _deviceStateUpdates.asStateFlow()

    private val deviceGattMap = ConcurrentHashMap<String, BluetoothGatt>()
    private val deviceStatusMap = ConcurrentHashMap<String, DeviceStatus>()
    private val connectionRetryMap = ConcurrentHashMap<String, Int>()
    private val connectionTimeoutJobs = ConcurrentHashMap<String, Job>()
    private val manualDisconnects = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val deviceReadyMap = ConcurrentHashMap<String, Boolean>()
    private val commandLocks = ConcurrentHashMap<String, ReentrantLock>()
    private var pacerDeviceId: String? = null  // Track which device is Seat 1 (pacer)

    private data class DeviceStatusFrame(
        val state: Byte,
        val strokeCount: Int,
        val currentSet: Int,
        val batteryLevel: Int
    )

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
        connectionRetryMap.remove("${deviceId}_service_discovery")  // Clean up service discovery retries
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

    // GATT operations must be serialized: Android's BluetoothGatt only supports one in-flight
    // operation at a time across ALL characteristics and descriptors. Firing writes back-to-back
    // (e.g., 5 CCCD subscribes in onServicesDiscovered, or configureZone+startTraining at session
    // start) causes all but the first to be silently dropped. Symptom: notifications never arrive,
    // or commands like CMD_START_TRAINING never reach firmware.
    //
    // Single unified queue per device, drained by either onDescriptorWrite or onCharacteristicWrite.
    private sealed class PendingGattOp {
        abstract val label: String
        data class DescriptorWrite(
            val descriptor: BluetoothGattDescriptor,
            val value: ByteArray,
            override val label: String
        ) : PendingGattOp()
        data class CharacteristicWrite(
            val characteristic: BluetoothGattCharacteristic,
            val value: ByteArray,
            val writeType: Int,
            override val label: String
        ) : PendingGattOp()
    }
    private val gattOpQueues = mutableMapOf<String, ArrayDeque<PendingGattOp>>()
    private val gattOpInFlight = mutableMapOf<String, Boolean>()

    private fun enqueueDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor?,
        value: ByteArray,
        label: String
    ) {
        if (descriptor == null) {
            Log.w(TAG, "  ✗ Cannot enqueue CCCD write [$label]: descriptor is null")
            return
        }
        enqueueGattOp(gatt, PendingGattOp.DescriptorWrite(descriptor, value, label))
    }

    private fun enqueueCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
        label: String
    ): Boolean {
        enqueueGattOp(gatt, PendingGattOp.CharacteristicWrite(characteristic, value, writeType, label))
        return true
    }

    private fun enqueueGattOp(gatt: BluetoothGatt, op: PendingGattOp) {
        val deviceId = gatt.device.address
        synchronized(gattOpQueues) {
            val queue = gattOpQueues.getOrPut(deviceId) { ArrayDeque() }
            queue.addLast(op)
            if (gattOpInFlight[deviceId] != true) {
                drainGattOpQueue(gatt)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun drainGattOpQueue(gatt: BluetoothGatt) {
        val deviceId = gatt.device.address
        synchronized(gattOpQueues) {
            val queue = gattOpQueues[deviceId] ?: return
            val next = queue.removeFirstOrNull()
            if (next == null) {
                gattOpInFlight[deviceId] = false
                return
            }
            gattOpInFlight[deviceId] = true
            val ok = when (next) {
                is PendingGattOp.DescriptorWrite ->
                    writeDescriptorCompat(gatt, next.descriptor, next.value)
                is PendingGattOp.CharacteristicWrite ->
                    writeCharacteristicCompat(gatt, next.characteristic, next.value, next.writeType)
            }
            if (!ok) {
                Log.w(TAG, "  ✗ GATT op [${next.label}] failed to queue for $deviceId")
                gattOpInFlight[deviceId] = false
                drainGattOpQueue(gatt)
            }
        }
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

                    // Clear any pending GATT ops so a fresh reconnect starts clean
                    synchronized(gattOpQueues) {
                        gattOpQueues.remove(deviceId)
                        gattOpInFlight.remove(deviceId)
                    }

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
            val deviceId = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered for $deviceId")

                if (!hasRequiredPermissions()) return

                // Log all discovered services for debugging
                val services = gatt.services
                Log.d(TAG, "  Total services found: ${services.size}")
                services.forEach { service ->
                    Log.d(TAG, "    Service: ${service.uuid}")
                }

                val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID)
                if (hapticService == null) {
                    Log.w(TAG, "Oro Haptic service NOT FOUND on $deviceId")
                    deviceReadyMap[deviceId] = false

                    // Retry discovery with a delay (max 3 attempts)
                    val retryCount = connectionRetryMap.getOrDefault("${deviceId}_service_discovery", 0)
                    if (retryCount < 3) {
                        connectionRetryMap["${deviceId}_service_discovery"] = retryCount + 1
                        Log.d(TAG, "  Scheduling service rediscovery attempt ${retryCount + 1}/3 for $deviceId")
                        scope.launch {
                            delay(500L * (retryCount + 1))  // Increasing delay: 500ms, 1000ms, 1500ms
                            if (deviceGattMap[deviceId] != null && hasRequiredPermissions()) {
                                Log.d(TAG, "  Retrying service discovery for $deviceId (attempt ${retryCount + 1})")
                                try {
                                    gatt.discoverServices()
                                } catch (e: Exception) {
                                    Log.e(TAG, "  Service rediscovery failed for $deviceId: ${e.message}")
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "  Max service discovery attempts reached for $deviceId - device may not be advertising service correctly")
                    }
                    return
                } else {
                    // Service found successfully
                    connectionRetryMap.remove("${deviceId}_service_discovery")
                    deviceReadyMap[deviceId] = true
                    Log.d(TAG, "  Oro Haptic service FOUND on $deviceId - device is ready")
                }

                // Enable notifications for Device Status
                val deviceStatusChar = hapticService.getCharacteristic(DEVICE_STATUS_UUID)
                if (deviceStatusChar != null) {
                    gatt.setCharacteristicNotification(deviceStatusChar, true)
                    val descriptor = deviceStatusChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    enqueueDescriptorWrite(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, "device-status")
                    Log.d(TAG, "  ✓ Device Status characteristic enabled for $deviceId")
                } else {
                    Log.w(TAG, "  ✗ Device Status characteristic NOT FOUND for $deviceId")
                }

                // Verify Audio Control characteristic is available
                val audioControlChar = hapticService.getCharacteristic(AUDIO_CONTROL_UUID)
                if (audioControlChar != null) {
                    Log.d(TAG, "  ✓ Audio Control characteristic FOUND for $deviceId")
                } else {
                    Log.w(TAG, "  ✗ Audio Control characteristic NOT FOUND for $deviceId - audio prompts will not work!")
                }

                // Enable stroke event notifications if this is the pacer (Seat 1)
                if (gatt.device.address == pacerDeviceId) {
                    val strokeEventChar = hapticService.getCharacteristic(STROKE_EVENT_UUID)
                    if (strokeEventChar != null) {
                        gatt.setCharacteristicNotification(strokeEventChar, true)
                        val descriptor = strokeEventChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                        enqueueDescriptorWrite(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, "stroke-event")
                        Log.d(TAG, "  ✓ Enabled stroke event notifications for pacer: ${gatt.device.address}")
                    } else {
                        Log.w(TAG, "  ✗ Stroke Event characteristic NOT FOUND for pacer: ${gatt.device.address}")
                    }
                }

                // Enable calibration notifications for all devices
                val calibrationChar = hapticService.getCharacteristic(CALIBRATION_UUID)
                if (calibrationChar != null) {
                    gatt.setCharacteristicNotification(calibrationChar, true)
                    val descriptor = calibrationChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    enqueueDescriptorWrite(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, "calibration")
                    Log.d(TAG, "  ✓ Calibration notifications enabled for $deviceId")
                } else {
                    Log.w(TAG, "  ✗ Calibration characteristic NOT FOUND for $deviceId")
                }

                // Enable FSR data notifications for all devices
                val fsrDataChar = hapticService.getCharacteristic(FSR_DATA_UUID)
                if (fsrDataChar != null) {
                    gatt.setCharacteristicNotification(fsrDataChar, true)
                    val descriptor = fsrDataChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    enqueueDescriptorWrite(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, "fsr-data")
                    Log.d(TAG, "  ✓ FSR data notifications enabled for $deviceId")
                } else {
                    Log.w(TAG, "  ✗ FSR Data characteristic NOT FOUND for $deviceId")
                }

                // Read battery level
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                if (batteryChar != null) {
                    gatt.readCharacteristic(batteryChar)
                    // Enable battery notifications
                    gatt.setCharacteristicNotification(batteryChar, true)
                    val descriptor = batteryChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    enqueueDescriptorWrite(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, "battery-level")
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

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val deviceId = gatt.device.address
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "  ✗ CCCD write failed for $deviceId (status=$status, uuid=${descriptor.characteristic.uuid})")
            }
            synchronized(gattOpQueues) {
                gattOpInFlight[deviceId] = false
            }
            drainGattOpQueue(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val deviceId = gatt.device.address
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "  ✗ Characteristic write failed for $deviceId (status=$status, uuid=${characteristic.uuid})")
            }
            synchronized(gattOpQueues) {
                gattOpInFlight[deviceId] = false
            }
            drainGattOpQueue(gatt)
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
                // Support both 7-byte (legacy) and 16-byte (enriched) packets
                if (value.size < 7) return
                val phase = value[0]
                val timestamp = ((value[4].toInt() and 0xFF).toLong() shl 24) or
                        ((value[3].toInt() and 0xFF).toLong() shl 16) or
                        ((value[2].toInt() and 0xFF).toLong() shl 8) or
                        (value[1].toInt() and 0xFF).toLong()
                val accelInt = ((value[6].toInt() and 0xFF) shl 8) or
                        (value[5].toInt() and 0xFF)
                val accelMagnitude = accelInt.toShort() / 100.0f

                // Extended fields from 16-byte enriched packet
                val peakAccel = if (value.size >= 16) {
                    (((value[8].toInt() and 0xFF) shl 8) or (value[7].toInt() and 0xFF)).toShort() / 100.0f
                } else 0f
                val minAccel = if (value.size >= 16) {
                    (((value[10].toInt() and 0xFF) shl 8) or (value[9].toInt() and 0xFF)).toShort() / 100.0f
                } else 0f
                val phaseDurationMs = if (value.size >= 16) {
                    (value[11].toInt() and 0xFF) or ((value[12].toInt() and 0xFF) shl 8)
                } else 0
                val topHandPressurePercent = if (value.size >= 16) value[13].toInt() and 0xFF else 0
                val strokeFlags = if (value.size >= 16) value[14].toInt() and 0xFF else 0

                val strokeEvent = StrokeEvent(
                    deviceId = gatt.device.address,
                    phase = phase,
                    timestamp = timestamp,
                    accelMagnitude = accelMagnitude,
                    peakAccel = peakAccel,
                    minAccel = minAccel,
                    phaseDurationMs = phaseDurationMs,
                    topHandPressurePercent = topHandPressurePercent,
                    strokeFlags = strokeFlags
                )

                _strokeEvents.value = strokeEvent

                Log.d(TAG, "Stroke event from ${gatt.device.address}: phase=$phase, accel=$accelMagnitude, " +
                    "peak=$peakAccel, min=$minAccel, phaseDur=${phaseDurationMs}ms, fsr=$topHandPressurePercent%")

            }
            DEVICE_STATUS_UUID -> {
                val frame = parseDeviceStatusFrame(value)
                if (frame != null) {
                    val deviceId = gatt.device.address
                    Log.d(
                        TAG,
                        "Device status update for $deviceId: state=0x${String.format("%02X", frame.state)} " +
                            "stroke=${frame.strokeCount} set=${frame.currentSet} battery=${frame.batteryLevel}%"
                    )
                    updateDeviceStatus(
                        deviceId,
                        DeviceStatus.Connected,
                        batteryLevel = frame.batteryLevel
                    )
                    // Surface the firmware's own state so the ViewModel can resync calibration
                    // across reconnects (ADR-0003 / device-state sync).
                    _deviceStateUpdates.value = DeviceStateUpdate(deviceId, frame.state.toInt() and 0xFF)
                } else {
                    Log.w(TAG, "Malformed device status payload from ${gatt.device.address}: ${value.size} bytes")
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
            FSR_DATA_UUID -> {
                if (value.size >= 4) {
                    val forcePercent = value[0].toInt() and 0xFF
                    val rawAdc = ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                    val topHandPressureThresholdTriggered = value[3].toInt() != 0

                    _fsrUpdates.value = FsrUpdate(
                        deviceId = gatt.device.address,
                        forcePercent = forcePercent,
                        rawAdc = rawAdc,
                        topHandPressureThresholdTriggered = topHandPressureThresholdTriggered
                    )

                    Log.d(TAG, "FSR data from ${gatt.device.address}: force=$forcePercent%, rawAdc=$rawAdc, threshold=$topHandPressureThresholdTriggered")
                }
            }
            CALIBRATION_UUID -> {
                Log.d(TAG, "Calibration notification received from ${gatt.device.address}, size=${value.size}")

                // Parse calibration status frame
                // Format: [command(1) | strokeCount(1) | maxAccel(2) | minAccel(2) | taring(1) | baselineRejected(1)]
                if (value.size >= 4) {
                    val command = value[0]
                    val strokeCount = value[1].toInt() and 0xFF

                    Log.d(TAG, "Calibration command=0x${String.format("%02X", command)}, strokeCount=$strokeCount, size=${value.size}")

                    // Check if this is a calibration status update (CAL_CMD_GET_STATUS = 0x04)
                    if (command == CAL_CMD_GET_STATUS && value.size >= 8) {
                        val maxAccelInt = ((value[3].toInt() and 0xFF) shl 8) or (value[2].toInt() and 0xFF)
                        val minAccelInt = ((value[5].toInt() and 0xFF) shl 8) or (value[4].toInt() and 0xFF)

                        val maxAccel = maxAccelInt.toShort() / 100.0f
                        val minAccel = minAccelInt.toShort() / 100.0f
                        val suggestedThreshold = maxAccel * 0.55f  // 55% of max
                        val isComplete = strokeCount >= 50
                        // Bytes 6–7 added in ADR-0012; older firmware sent 0 here.
                        val isCapturingBaseline = value[6].toInt() != 0
                        val baselineRejected = value[7].toInt() != 0

                        val calibrationUpdate = CalibrationUpdate(
                            deviceId = gatt.device.address,
                            strokeCount = strokeCount,
                            maxAccel = maxAccel,
                            minAccel = minAccel,
                            suggestedThreshold = suggestedThreshold,
                            isComplete = isComplete,
                            isCapturingBaseline = isCapturingBaseline,
                            baselineRejected = baselineRejected
                        )

                        _calibrationUpdates.value = calibrationUpdate

                        Log.d(TAG, "Calibration update from ${gatt.device.address}: " +
                                "strokes=$strokeCount/50, maxAccel=$maxAccel, minAccel=$minAccel, " +
                                "threshold=$suggestedThreshold, complete=$isComplete")
                    }
                }
            }
        }
    }

    private fun parseDeviceStatusFrame(value: ByteArray): DeviceStatusFrame? {
        if (value.size < 5) return null
        val state = value[0]
        val strokeCount = (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        val currentSet = value[3].toInt() and 0xFF
        val batteryLevel = value[4].toInt() and 0xFF
        return DeviceStatusFrame(state, strokeCount, currentSet, batteryLevel)
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
        connectionRetryMap.remove("${deviceId}_service_discovery")  // Clean up service discovery retries
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
        deviceStatusMap[deviceId] = status
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
    fun configureTrainingZone(deviceId: String, strokes: Int, sets: Int, spm: Int, zoneColor: Byte = ZONE_ENDURANCE, isPacer: Boolean = false): Boolean {
        if (!hasRequiredPermissions()) return false

        val gatt = deviceGattMap[deviceId] ?: return false
        val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: return false
        val zoneSettingsChar = hapticService.getCharacteristic(ZONE_SETTINGS_UUID) ?: return false

        // Format: [strokes LSB, strokes MSB, sets, SPM LSB, SPM MSB, zone color, role]
        // role: 0x00 = Follower, 0x01 = Pacer
        val data = ByteBuffer.allocate(7).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putShort(strokes.toShort())
            put(sets.toByte())
            putShort(spm.toShort())
            put(zoneColor)
            put(if (isPacer) 0x01.toByte() else 0x00.toByte())
        }.array()

        val result = enqueueCharacteristicWrite(
            gatt,
            zoneSettingsChar,
            data,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            "zone-settings"
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

        // Check if device is ready - if not, trigger service rediscovery but still attempt to send
        if (deviceReadyMap[deviceId] != true) {
            Log.w(TAG, "Device $deviceId not ready for haptic command, triggering service rediscovery")
            try {
                scope.launch {
                    delay(100)  // Small delay before triggering rediscovery
                    if (hasRequiredPermissions() && deviceGattMap[deviceId] != null) {
                        gatt.discoverServices()
                        Log.d(TAG, "Triggered service rediscovery for $deviceId")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to trigger service discovery on $deviceId: ${e.message}")
            }
            // Don't return false immediately - try to send anyway in case service is available
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

            val result = enqueueCharacteristicWrite(
                gatt,
                hapticControlChar,
                data,
                hapticControlChar.writeType,
                "haptic-cmd-0x${"%02X".format(command)}"
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
        enqueueDescriptorWrite(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, "stroke-event-late")

        Log.d(TAG, "Enabled stroke event notifications for: ${gatt.device.address}")
    }

    /**
     * Subscribe to stroke-event notifications on every connected device, so the phone hears all
     * Catches — not just the pacer's — for Sync Score measurement. Every device already detects
     * strokes in firmware; this just opens the notification channel. Called at session start. (ADR-0015)
     */
    fun enableStrokeNotificationsForAllConnected() {
        deviceGattMap.forEach { (deviceId, gatt) ->
            if (deviceStatusMap[deviceId] == DeviceStatus.Connected) {
                enableStrokeNotifications(gatt)
            }
        }
    }

    fun enableStrokeDetection(deviceId: String): Boolean {
        // Enable stroke detection by starting training mode (firmware will use IMU)
        return startTraining(deviceId)
    }

    fun disableStrokeDetection(deviceId: String): Boolean {
        return stopTraining(deviceId)
    }

    /**
     * Sends a command to every eligible connected device, returning attempted/succeeded counts.
     *
     * A device is eligible when it is connected, ready, and either [includePacer] is true or it
     * is not the pacer. This is the single place the broadcast eligibility rule lives. [send]
     * performs the per-device write and reports whether it succeeded.
     */
    private fun broadcast(
        label: String,
        includePacer: Boolean,
        send: (deviceId: String) -> Boolean
    ): BroadcastResult {
        var attempted = 0
        var succeeded = 0

        Log.d(TAG, "=== $label START ===")
        Log.d(TAG, "  includePacer: $includePacer, totalDevices: ${deviceGattMap.size}, pacer: $pacerDeviceId")

        deviceGattMap.keys.forEach { deviceId ->
            val isPacer = deviceId == pacerDeviceId
            val isConnected = deviceStatusMap[deviceId] == DeviceStatus.Connected
            val isReady = deviceReadyMap[deviceId] == true
            val shouldSend = isConnected && isReady && (includePacer || !isPacer)

            Log.d(TAG, "  Device $deviceId: isPacer=$isPacer, connected=$isConnected, ready=$isReady, shouldSend=$shouldSend")

            if (shouldSend) {
                attempted++
                if (send(deviceId)) {
                    succeeded++
                    Log.d(TAG, "    ✓ SUCCESS sending to $deviceId")
                } else {
                    Log.w(TAG, "    ✗ FAILED sending to $deviceId")
                }
            }
        }

        when {
            attempted == 0 -> Log.w(TAG, "$label: NO ELIGIBLE DEVICES (includePacer=$includePacer, totalDevices=${deviceGattMap.size})")
            succeeded != attempted -> Log.w(TAG, "$label PARTIAL SUCCESS: $succeeded / $attempted succeeded")
            else -> Log.d(TAG, "$label COMPLETE SUCCESS: $succeeded / $attempted devices")
        }
        Log.d(TAG, "=== $label END ===")

        return BroadcastResult(attempted, succeeded)
    }

    fun broadcastHaptic(
        command: Byte = CMD_SINGLE_PULSE,
        pattern: Byte = PATTERN_STRONG_CLICK,
        intensity: Int = 100,
        includePacer: Boolean = false
    ): BroadcastResult {
        Log.d(TAG, "broadcastHaptic: command=0x${String.format("%02X", command)}, pattern=$pattern, intensity=$intensity")
        return broadcast("broadcastHaptic", includePacer) { deviceId ->
            sendHapticCommand(deviceId, command, intensity = intensity, pattern = pattern)
        }
    }

    fun broadcastAudio(
        audioEvent: Byte,
        volume: Int = 80,
        includePacer: Boolean = true
    ): BroadcastResult {
        Log.d(TAG, "broadcastAudio: event=0x${String.format("%02X", audioEvent)}, volume=$volume")
        return broadcast("broadcastAudio", includePacer) { deviceId ->
            sendAudioCommand(deviceId, audioEvent, volume)
        }
    }

    /**
     * Loads the Crew Roll-Call roster onto every device (Roll-Call Control LOAD — BLE_PROTOCOL §1.10).
     * Pair with [broadcastRollCallPlay] once loaded; the per-device write queue keeps LOAD before PLAY.
     */
    fun broadcastRollCallLoad(rollCall: com.orotrain.oro.model.CrewRollCall): BroadcastResult {
        val payload = RollCallCodec.encodeLoad(rollCall)
        Log.d(TAG, "broadcastRollCallLoad: ${rollCall.seats.size} seats, ${payload.size} bytes")
        return broadcast("rollCallLoad", includePacer = true) { deviceId ->
            sendRollCallWrite(deviceId, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, "rollcall-load")
        }
    }

    /**
     * Triggers every device to speak the loaded roster in unison (Roll-Call Control PLAY). Each device
     * gets a *decreasing* start delay so devices reached earlier wait longer and all begin together;
     * sent Write Without Response to minimize per-write latency (BLE_PROTOCOL §1.10, ADR-0016).
     */
    fun broadcastRollCallPlay(volume: Int = 100, targetDelayMs: Int = ROLLCALL_PLAY_TARGET_MS): BroadcastResult {
        val t0 = System.currentTimeMillis()
        Log.d(TAG, "broadcastRollCallPlay: volume=$volume, target=${targetDelayMs}ms")
        return broadcast("rollCallPlay", includePacer = true) { deviceId ->
            val elapsed = (System.currentTimeMillis() - t0).toInt()
            val payload = RollCallCodec.encodePlay(startDelayMs = targetDelayMs - elapsed, volume = volume)
            sendRollCallWrite(deviceId, payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE, "rollcall-play")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendRollCallWrite(deviceId: String, payload: ByteArray, writeType: Int, label: String): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "$label failed for $deviceId: Missing Bluetooth permissions")
            return false
        }
        val gatt = deviceGattMap[deviceId] ?: run {
            Log.w(TAG, "$label failed for $deviceId: GATT connection not found")
            return false
        }
        val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: run {
            Log.w(TAG, "$label failed for $deviceId: Oro Haptic Service not found")
            return false
        }
        val rollCallChar = hapticService.getCharacteristic(ROLLCALL_CONTROL_UUID) ?: run {
            Log.w(TAG, "$label failed for $deviceId: Roll-Call Control characteristic not found (firmware may need update)")
            return false
        }
        return enqueueCharacteristicWrite(gatt, rollCallChar, payload, writeType, label)
    }

    @SuppressLint("MissingPermission")
    private fun sendAudioCommand(deviceId: String, audioEvent: Byte, volume: Int): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "sendAudioCommand failed for $deviceId: Missing Bluetooth permissions")
            return false
        }

        val gatt = deviceGattMap[deviceId] ?: run {
            Log.w(TAG, "sendAudioCommand failed for $deviceId: GATT connection not found")
            return false
        }

        val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: run {
            Log.w(TAG, "sendAudioCommand failed for $deviceId: Oro Haptic Service not found")
            return false
        }

        val audioChar = hapticService.getCharacteristic(AUDIO_CONTROL_UUID) ?: run {
            Log.w(TAG, "sendAudioCommand failed for $deviceId: Audio Control characteristic not found (firmware may need update)")
            return false
        }

        // Clamp volume to 0-100
        val clampedVolume = volume.coerceIn(0, 100)

        val data = byteArrayOf(audioEvent, clampedVolume.toByte())
        val result = enqueueCharacteristicWrite(gatt, audioChar, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, "audio-event-0x${"%02X".format(audioEvent)}")

        if (result) {
            Log.d(TAG, "Audio command sent successfully to $deviceId: event=0x${String.format("%02X", audioEvent)}, volume=$clampedVolume")
        } else {
            Log.w(TAG, "Audio command write failed for $deviceId")
        }

        return result
    }

    // Calibration functions

    @SuppressLint("MissingPermission")
    fun startCalibration(deviceId: String): Boolean {
        if (!hasRequiredPermissions()) return false

        val gatt = deviceGattMap[deviceId] ?: return false
        val hapticService = gatt.getService(ORO_HAPTIC_SERVICE_UUID) ?: return false
        val calibrationChar = hapticService.getCharacteristic(CALIBRATION_UUID) ?: return false

        val data = byteArrayOf(CAL_CMD_START)
        val result = enqueueCharacteristicWrite(
            gatt,
            calibrationChar,
            data,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            "cal-start"
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
        val result = enqueueCharacteristicWrite(
            gatt,
            calibrationChar,
            data,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            "cal-stop"
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

        val result = enqueueCharacteristicWrite(
            gatt,
            calibrationChar,
            data,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            "cal-set-threshold"
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
