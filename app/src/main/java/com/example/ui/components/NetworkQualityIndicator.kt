package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NetworkQualityMetrics

@Composable
fun NetworkQualityIndicator(
    metrics: NetworkQualityMetrics,
    modifier: Modifier = Modifier
) {
    var showDetailsDialog by remember { mutableStateOf(false) }

    val qualityColor = when (metrics.signalBars) {
        4 -> Color(0xFF10B981) // Green
        3 -> Color(0xFF3B82F6) // Blue
        2 -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFFEF4444) // Red
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(qualityColor.copy(alpha = 0.12f))
            .clickable { showDetailsDialog = true }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("network_quality_indicator"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visual 4-Bar Signal Strength Graphic
        SignalStrengthBars(
            activeBars = metrics.signalBars,
            barColor = qualityColor,
            modifier = Modifier.size(width = 16.dp, height = 12.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Latency and Jitter display
        Text(
            text = "${metrics.latencyMs} ms",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = qualityColor,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "(±${metrics.jitterMs}ms)",
            fontSize = 10.sp,
            color = qualityColor.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace
        )
    }

    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = qualityColor,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Network Latency & Jitter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Real-time streaming transmission quality on local Wi-Fi:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            MetricItem("Round-Trip Latency (RTT)", "${metrics.latencyMs} ms", qualityColor)
                            MetricItem("Packet Jitter Variance", "±${metrics.jitterMs} ms", qualityColor)
                            MetricItem("Overall Quality", metrics.qualityText, qualityColor)
                            if (metrics.wifiSsid != null) {
                                MetricItem("Wi-Fi SSID", metrics.wifiSsid, MaterialTheme.colorScheme.onSurface)
                            }
                            val bandText = if (metrics.wifiFrequencyMhz > 4000) "5.0 GHz (Fast)" else "2.4 GHz"
                            MetricItem("Wi-Fi Frequency", bandText, MaterialTheme.colorScheme.onSurface)
                            MetricItem("Wi-Fi Link Speed", "${metrics.wifiLinkSpeedMbps} Mbps", MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Low latency (<25ms) and low jitter (<6ms) ensure stutter-free CD-quality audio playback.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SignalStrengthBars(
    activeBars: Int,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(4.dp, 6.5.dp, 9.5.dp, 12.dp)
        for (i in 0 until 4) {
            val isActive = i < activeBars
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(heights[i])
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isActive) barColor else barColor.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
