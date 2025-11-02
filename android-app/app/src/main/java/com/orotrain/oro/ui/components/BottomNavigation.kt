package com.orotrain.oro.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.orotrain.oro.model.AppDestination

@Composable
fun BottomNavigationBar(
    current: AppDestination,
    onSelect: (AppDestination) -> Unit,
    connectedCount: Int,
    maxDevices: Int,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        NavigationBarItem(
            selected = current == AppDestination.Connection,
            onClick = { onSelect(AppDestination.Connection) },
            icon = {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = "Devices"
                )
            },
            label = {
                Text(
                    text = "Devices\n$connectedCount of $maxDevices",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )

        NavigationBarItem(
            selected = current == AppDestination.Training,
            onClick = { onSelect(AppDestination.Training) },
            icon = {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.FitnessCenter,
                    contentDescription = "Training"
                )
            },
            label = {
                Text(
                    text = "Training",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )
    }
}
