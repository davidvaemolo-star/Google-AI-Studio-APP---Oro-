package com.orotrain.oro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orotrain.oro.MainViewModel
import com.orotrain.oro.model.AppDestination
import com.orotrain.oro.model.MAX_DEVICES
import com.orotrain.oro.ui.components.BottomNavigationBar
import com.orotrain.oro.ui.screens.ConnectionScreen
import com.orotrain.oro.ui.screens.TrainingScreen

@Composable
fun OroApp(
    viewModel: MainViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomNavigationBar(
                current = state.destination,
                onSelect = viewModel::setDestination,
                connectedCount = state.connectedDevicesCount,
                maxDevices = MAX_DEVICES
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (state.destination) {
                AppDestination.Connection -> ConnectionScreen(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    onScan = viewModel::startScan,
                    onToggleDevice = viewModel::toggleDeviceConnection,
                    onReorder = viewModel::reorderConnectedDevices
                )

                AppDestination.Training -> TrainingScreen(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    onAddZone = viewModel::addZone,
                    onRemoveZone = viewModel::removeZone,
                    onDuplicateZone = viewModel::duplicateZone,
                    onAddAfterZone = viewModel::addZoneAfter,
                    onAdjustZone = viewModel::adjustZone,
                    onReorderZones = viewModel::reorderZones
                )
            }
        }
    }
}
