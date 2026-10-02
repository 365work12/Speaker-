package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectionStatus
import com.example.data.SpeakerMode
import com.example.data.SpeakerUiState

/**
 * Visual Connection Status Indicator displaying:
 * 1. Protocol badge (Wi-Fi / Bluetooth) with live status pulse
 * 2. Current audio transmission Bitrate (e.g. 1,411 kbps)
 * 3. Ping time (round-trip latency) to the PC source
 */
@Composable
fun ConnectionStatusIndicator(
    uiState: SpeakerUiState,
    modifier: Modifier = Modifier,
    onRefreshPing: (() -> Unit)? = null
) {
    var showTelemetryDialog by remember { mutableStateOf(false) }

    val isConnected = uiState.status == ConnectionStatus.CONNECTED
    val isStreaming = isConnected && !uiState.isAudioPaused
    val isReconnecting = uiState.status == ConnectionStatus.RECONNECTING
    val isBluetooth = uiState.mode == SpeakerMode.BLUETOOTH_CHECK || uiState.activeProtocol.contains("Bluetooth", ignoreCase = true)

    // Status colors
    val statusColor = when {
        isStreaming -> Color(0xFF10B981) // Emerald Green
        isConnected -> Color(0xFF38BDF8) // Cyan Connected
        isReconnecting -> Color(0xFFF59E0B) // Amber
        else -> MaterialTheme.colorScheme.outline
    }

    val pingColor = when {
        uiState.pingMs < 25 -> Color(0xFF10B981) // Ultra-low
        uiState.pingMs < 60 -> Color(0xFF38BDF8) // Optimal
        else -> Color(0xFFF59E0B) // Fair
    }

    // Pulse animation for the connection indicator dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (isStreaming) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isStreaming) 650 else 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = statusColor.copy(alpha = if (isConnected) 0.35f else 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { showTelemetryDialog = true }
            .testTag("connection_status_indicator"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Top Row: Source header & quick status summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .scale(pulseScale)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (isConnected) {
                            uiState.connectedDeviceName?.let { "Connected to $it" } ?: "Stream Active"
                        } else if (isReconnecting) {
                            "Reconnecting to PC..."
                        } else {
                            "Ready to Receive from PC"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Telemetry",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "View telemetry",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Status Row: [Protocol] | [Bitrate] | [PC Ping Time]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Protocol Badge (Wi-Fi / Bluetooth)
                StatusTelemetryPill(
                    icon = if (isBluetooth) Icons.Default.Bluetooth else Icons.Default.Wifi,
                    label = "Protocol",
                    value = if (isBluetooth) "Bluetooth" else "Wi-Fi",
                    accentColor = if (isBluetooth) Color(0xFFA855F7) else Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f),
                    testTag = "telemetry_protocol"
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Current Bitrate
                val bitrateText = if (uiState.currentBitrateKbps >= 1000) {
                    "%.1f Mbps".format(uiState.currentBitrateKbps / 1000f)
                } else {
                    "${uiState.currentBitrateKbps} kbps"
                }

                StatusTelemetryPill(
                    icon = Icons.Default.GraphicEq,
                    label = "Bitrate",
                    value = if (isConnected && !uiState.isAudioPaused) bitrateText else "Idle",
                    accentColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                    testTag = "telemetry_bitrate"
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 3. Ping Time to PC Source
                StatusTelemetryPill(
                    icon = Icons.Default.Speed,
                    label = "PC Ping",
                    value = "${uiState.pingMs} ms",
                    accentColor = pingColor,
                    modifier = Modifier.weight(1f),
                    testTag = "telemetry_ping"
                )
            }
        }
    }

    // Telemetry & Diagnostic Detail Dialog
    if (showTelemetryDialog) {
        AlertDialog(
            onDismissRequest = { showTelemetryDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Connection Telemetry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Real-time PC audio stream telemetry and transmission quality metrics:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            TelemetryDetailRow(
                                label = "Protocol",
                                value = if (isBluetooth) "Bluetooth A2DP Sink" else "Wi-Fi (TCP / HTTP)",
                                valueColor = if (isBluetooth) Color(0xFFA855F7) else Color(0xFF38BDF8)
                            )
                            TelemetryDetailRow(
                                label = "Audio Transmission Bitrate",
                                value = "${uiState.currentBitrateKbps} kbps (CD Quality PCM)",
                                valueColor = Color(0xFF10B981)
                            )
                            TelemetryDetailRow(
                                label = "Ping Latency to PC",
                                value = "${uiState.pingMs} ms",
                                valueColor = pingColor
                            )
                            TelemetryDetailRow(
                                label = "Packet Jitter Variance",
                                value = "±${uiState.networkQuality.jitterMs} ms",
                                valueColor = MaterialTheme.colorScheme.onSurface
                            )
                            TelemetryDetailRow(
                                label = "Audio Sample Rate",
                                value = "${uiState.sampleRate} Hz • 16-bit Stereo",
                                valueColor = MaterialTheme.colorScheme.onSurface
                            )
                            TelemetryDetailRow(
                                label = "Target Buffer Latency",
                                value = "${uiState.bufferLatencyMs} ms",
                                valueColor = MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.connectedDeviceIp != null) {
                                TelemetryDetailRow(
                                    label = "PC Source IP",
                                    value = uiState.connectedDeviceIp,
                                    valueColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (uiState.networkQuality.wifiSsid != null) {
                                TelemetryDetailRow(
                                    label = "Connected Wi-Fi SSID",
                                    value = uiState.networkQuality.wifiSsid,
                                    valueColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "💡 SoundLink uses uncompressed 44.1 kHz PCM audio over local low-jitter Wi-Fi for lossless, lag-free sound from your PC.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTelemetryDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                if (onRefreshPing != null) {
                    TextButton(onClick = { onRefreshPing() }) {
                        Text("Refresh Ping")
                    }
                }
            }
        )
    }
}

/**
 * Individual Telemetry Pill inside the Connection Status Bar.
 */
@Composable
private fun StatusTelemetryPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .testTag(testTag)
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TelemetryDetailRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}
