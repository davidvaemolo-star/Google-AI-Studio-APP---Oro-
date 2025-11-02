package com.orotrain.oro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orotrain.oro.model.AppDestination
import com.orotrain.oro.model.DeviceStatus
import com.orotrain.oro.model.HapticDevice
import com.orotrain.oro.model.MAX_DEVICES
import com.orotrain.oro.model.MAX_SETS
import com.orotrain.oro.model.MAX_SPM
import com.orotrain.oro.model.MAX_STROKES
import com.orotrain.oro.model.MAX_ZONES
import com.orotrain.oro.model.MIN_SPM
import com.orotrain.oro.model.MIN_VALUE
import com.orotrain.oro.model.OroUiState
import com.orotrain.oro.model.Zone
import com.orotrain.oro.model.ZoneField
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel : ViewModel() {

    private val deviceNames = listOf(
        "Kai", "Moana", "Nalu", "Koa", "Hoku", "Makai", "Aukai", "Lani",
        "Triton", "Poseidon", "Mako", "Stingray", "Barracuda", "Marlin",
        "Voyager", "Navigator", "Compass", "Anchor", "Tsunami", "Coral"
    )

    private val _uiState = MutableStateFlow(OroUiState())
    val uiState: StateFlow<OroUiState> = _uiState.asStateFlow()

    fun setDestination(destination: AppDestination) {
        _uiState.update { it.copy(destination = destination) }
    }

    fun addZone() {
        _uiState.update { state ->
            if (state.zones.size >= MAX_ZONES) state
            else state.copy(zones = state.zones + Zone())
        }
    }

    fun addZoneAfter(zoneId: String) {
        _uiState.update { state ->
            if (state.zones.size >= MAX_ZONES) return@update state
            val index = state.zones.indexOfFirst { it.id == zoneId }
            if (index == -1) state
            else {
                val zones = state.zones.toMutableList()
                zones.add(index + 1, Zone())
                state.copy(zones = zones)
            }
        }
    }

    fun duplicateZone(zoneId: String) {
        _uiState.update { state ->
            if (state.zones.size >= MAX_ZONES) return@update state
            val index = state.zones.indexOfFirst { it.id == zoneId }
            if (index == -1) state
            else {
                val copy = state.zones[index].copy(id = Zone().id)
                val zones = state.zones.toMutableList()
                zones.add(index + 1, copy)
                state.copy(zones = zones)
            }
        }
    }

    fun removeZone(zoneId: String) {
        _uiState.update { state ->
            state.copy(zones = state.zones.filterNot { it.id == zoneId })
        }
    }

    fun adjustZone(zoneId: String, field: ZoneField, delta: Int) {
        _uiState.update { state ->
            val zones = state.zones.map { zone ->
                if (zone.id != zoneId) return@map zone

                when (field) {
                    ZoneField.Strokes -> zone.copy(
                        strokes = (zone.strokes + delta).coerceIn(MIN_VALUE, MAX_STROKES)
                    )

                    ZoneField.Sets -> zone.copy(
                        sets = (zone.sets + delta).coerceIn(MIN_VALUE, MAX_SETS)
                    )

                    ZoneField.Spm -> zone.copy(
                        spm = (zone.spm + delta).coerceIn(MIN_SPM, MAX_SPM)
                    )
                }
            }
            state.copy(zones = zones)
        }
    }

    fun reorderZones(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        _uiState.update { state ->
            if (fromIndex !in state.zones.indices || toIndex !in state.zones.indices) return@update state
            val zones = state.zones.toMutableList()
            val zone = zones.removeAt(fromIndex)
            zones.add(toIndex, zone)
            state.copy(zones = zones)
        }
    }

    fun startScan() {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, devices = emptyList()) }
            delay(2000)
            val foundDevices = deviceNames
                .shuffled()
                .take(MAX_DEVICES)
                .map { name -> HapticDevice(name = name) }
            _uiState.update { it.copy(isScanning = false, devices = foundDevices) }
        }
    }

    fun toggleDeviceConnection(deviceId: String) {
        val device = _uiState.value.devices.find { it.id == deviceId } ?: return
        when (device.status) {
            DeviceStatus.Connected -> disconnect(deviceId)
            DeviceStatus.Disconnected -> connect(deviceId)
            DeviceStatus.Connecting -> {
                // Ignore taps while we're simulating a connection handshake.
            }
        }
    }

    private fun connect(deviceId: String) {
        _uiState.update { state ->
            val updated = state.devices.map { device ->
                if (device.id == deviceId) device.copy(status = DeviceStatus.Connecting)
                else device
            }
            state.copy(devices = updated)
        }

        viewModelScope.launch {
            delay(1500)
            val batteryLevel = Random.nextInt(from = 20, until = 101)
            _uiState.update { state ->
                val updated = state.devices.map { device ->
                    if (device.id == deviceId) {
                        device.copy(
                            status = DeviceStatus.Connected,
                            batteryLevel = batteryLevel
                        )
                    } else device
                }
                state.copy(devices = renumberSeats(updated))
            }
        }
    }

    private fun disconnect(deviceId: String) {
        _uiState.update { state ->
            val updated = state.devices.map { device ->
                if (device.id == deviceId) {
                    device.copy(
                        status = DeviceStatus.Disconnected,
                        batteryLevel = null,
                        seat = null
                    )
                } else device
            }
            state.copy(devices = renumberSeats(updated))
        }
    }

    fun reorderConnectedDevices(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        _uiState.update { state ->
            val connected = state.devices.filter { it.status == DeviceStatus.Connected }
            if (fromIndex !in connected.indices || toIndex !in connected.indices) return@update state
            val reordered = connected.toMutableList().apply {
                val moved = removeAt(fromIndex)
                add(toIndex, moved)
            }
            val reassigned = reordered.mapIndexed { index, device ->
                device.copy(seat = index + 1)
            }
            val connectedIds = reassigned.map { it.id }.toSet()
            val others = state.devices.filter { it.id !in connectedIds }.map {
                if (it.status == DeviceStatus.Connected) it.copy(seat = null) else it
            }
            state.copy(devices = reassigned + others)
        }
    }

    private fun renumberSeats(devices: List<HapticDevice>): List<HapticDevice> {
        val connected = devices
            .filter { it.status == DeviceStatus.Connected }
            .sortedBy { it.seat ?: Int.MAX_VALUE }

        val reassigned = connected.mapIndexed { index, device ->
            device.copy(seat = index + 1)
        }
        val connectedIds = reassigned.map { it.id }.toSet()
        val others = devices.filter { it.id !in connectedIds }.map {
            if (it.status == DeviceStatus.Connected) it.copy(seat = null) else it
        }
        return reassigned + others
    }
}

