package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectionStatus

@Composable
fun SpeakerHeroCard(
    status: ConnectionStatus,
    audioLevel: Float,
    connectedDeviceName: String?,
    audioRouteName: String,
    modifier: Modifier = Modifier
) {
    val isStreaming = status == ConnectionStatus.CONNECTED
    val isListening = status == ConnectionStatus.LISTENING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isStreaming) 1.14f else if (isListening) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isStreaming) 450 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waves"
    )

    val statusColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.CONNECTED -> Color(0xFF10B981) // Emerald Green
            ConnectionStatus.LISTENING -> Color(0xFF3B82F6) // Electric Blue
            ConnectionStatus.CONNECTING -> Color(0xFFF59E0B) // Amber
            ConnectionStatus.PAUSED -> Color(0xFFEAB308) // Yellow
            ConnectionStatus.ERROR -> Color(0xFFEF4444) // Red
            else -> MaterialTheme.colorScheme.outline
        },
        label = "statusColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("speaker_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (status) {
                        ConnectionStatus.CONNECTED -> "STREAMING ACTIVE"
                        ConnectionStatus.LISTENING -> "READY & LISTENING"
                        ConnectionStatus.CONNECTING -> "CONNECTING..."
                        ConnectionStatus.PAUSED -> "PLAYBACK PAUSED"
                        ConnectionStatus.ERROR -> "CONNECTION ISSUE"
                        else -> "SPEAKER IDLE"
                    },
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Animated Speaker Cone Graphic with Sound Waves
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .testTag("speaker_cone_box"),
                contentAlignment = Alignment.Center
            ) {
                // Expanding radiating acoustic wave rings
                if (isStreaming || isListening) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val maxRadius = size.minDimension / 2
                        val primaryColor = statusColor

                        for (ring in 1..3) {
                            val ringOffset = (wavePhase + ring * 0.33f) % 1.0f
                            val currentRadius = maxRadius * (0.55f + ringOffset * 0.45f)
                            val alpha = (1.0f - ringOffset) * (if (isStreaming) 0.6f else 0.3f)

                            drawCircle(
                                color = primaryColor.copy(alpha = alpha),
                                radius = currentRadius,
                                center = center,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    }
                }

                // Speaker Body Circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .border(
                            width = 3.dp,
                            brush = Brush.linearGradient(
                                listOf(statusColor, MaterialTheme.colorScheme.primary)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.GraphicEq else Icons.Default.VolumeUp,
                        contentDescription = "Speaker",
                        modifier = Modifier.size(52.dp),
                        tint = if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connected Device or Output Info
            if (connectedDeviceName != null) {
                Text(
                    text = connectedDeviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Connected Source Device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (isListening) "Waiting for sender phone to connect..." else "Phone Speaker Idle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Output Route Badge
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Output: $audioRouteName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
