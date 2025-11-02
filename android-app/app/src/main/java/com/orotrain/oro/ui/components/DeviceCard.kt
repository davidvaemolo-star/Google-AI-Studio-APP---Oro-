package com.orotrain.oro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orotrain.oro.model.DeviceStatus
import com.orotrain.oro.model.HapticDevice
import com.orotrain.oro.ui.theme.AccentCyan
import com.orotrain.oro.ui.theme.AccentCyanDark
import com.orotrain.oro.ui.theme.Charcoal
import com.orotrain.oro.ui.theme.SurfaceOverlay

@Composable
fun DeviceCard(
    device: HapticDevice,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    seatNumber: Int? = null,
    seatRole: String? = null,
    isDragging: Boolean = false
) {
    val (statusText, buttonLabel, statusColor) = when (device.status) {
        DeviceStatus.Disconnected -> Triple("Disconnected", "Connect", Color.White.copy(alpha = 0.6f))
        DeviceStatus.Connecting -> Triple("Connecting...", "Connecting", Color.Yellow.copy(alpha = 0.8f))
        DeviceStatus.Connected -> Triple("Connected", "Disconnect", Color(0xFF3ADE8A))
    }

    val batteryTint = when (device.batteryLevel ?: 0) {
        in 0..20 -> Color(0xFFFF6E6E)
        in 21..50 -> Color(0xFFFFC24B)
        else -> Color(0xFF3ADE8A)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isDragging) SurfaceOverlay.copy(alpha = 0.8f) else SurfaceOverlay
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (seatNumber != null) {
            SeatBadge(
                number = seatNumber,
                role = seatRole
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (device.status == DeviceStatus.Connected && device.batteryLevel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryChargingFull,
                            contentDescription = "Battery",
                            tint = batteryTint
                        )
                        Text(
                            text = "${device.batteryLevel}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = batteryTint,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onToggle(device.id) },
            enabled = device.status != DeviceStatus.Connecting,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (device.status == DeviceStatus.Connected) Color(0xFFEE5253) else AccentCyan,
                disabledContainerColor = AccentCyanDark.copy(alpha = 0.4f)
            )
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun SeatBadge(
    number: Int,
    role: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Charcoal.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
            color = AccentCyan
        )
        if (!role.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = role.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = AccentCyan
            )
        }
    }
}
