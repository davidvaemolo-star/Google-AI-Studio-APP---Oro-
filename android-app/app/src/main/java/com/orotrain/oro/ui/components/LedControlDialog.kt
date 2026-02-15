package com.orotrain.oro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.orotrain.oro.ui.theme.AccentCyan
import com.orotrain.oro.ui.theme.Charcoal
import com.orotrain.oro.ui.theme.SurfaceOverlay

private data class ColorPreset(val name: String, val color: Color, val r: Int, val g: Int, val b: Int)

private val colorPresets = listOf(
    ColorPreset("Red", Color(0xFFFF0000), 255, 0, 0),
    ColorPreset("Green", Color(0xFF00FF00), 0, 255, 0),
    ColorPreset("Blue", Color(0xFF0000FF), 0, 0, 255),
    ColorPreset("Cyan", Color(0xFF00FFFF), 0, 255, 255),
    ColorPreset("Yellow", Color(0xFFFFFF00), 255, 255, 0),
    ColorPreset("White", Color(0xFFFFFFFF), 255, 255, 255)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedControlDialog(
    deviceName: String,
    onDismiss: () -> Unit,
    onSetColor: (r: Int, g: Int, b: Int) -> Unit,
    onSetAutoMode: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Charcoal,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LED Control",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = AccentCyan
                )
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    colorPresets.forEach { preset ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                onSetColor(preset.r, preset.g, preset.b)
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(preset.color)
                                    .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            )
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = onSetAutoMode,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceOverlay
                    )
                ) {
                    Text(
                        text = "Auto Mode",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = AccentCyan
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEE5253)
                    )
                ) {
                    Text("Close")
                }
            }
        }
    }
}
